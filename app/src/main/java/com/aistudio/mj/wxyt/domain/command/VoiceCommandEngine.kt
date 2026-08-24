package com.aistudio.mj.wxyt.domain.command

import android.util.Log
import com.aistudio.mj.wxyt.domain.ai.AIOrchestrator
import com.aistudio.mj.wxyt.domain.ai.AIRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Deterministic-first voice command parser.
 *
 * The parser intentionally handles the common assistant actions locally so
 * simple device commands do not depend on an LLM being reachable.
 */
class VoiceCommandEngine(
    private val aiOrchestrator: AIOrchestrator
) {
    private val validator = CommandValidator()
    private var lastAction: CommandAction? = null
    private var lastTarget: String? = null
    private var pendingClarification: CommandAction? = null

    private val commandKeywords = listOf(
        "open", "launch", "খোলো", "খুলে", "চালু কর",
        "search", "google", "খুঁজে", "সার্চ", "খোঁজ",
        "play", "চালান", "চালাও", "বাজাও",
        "send", "message", "পাঠাও", "পাঠান", "মেসেজ",
        "call", "phone", "ফোন", "কল", "ডায়াল",
        "click", "tap", "চাপ", "ট্যাপ",
        "type", "write", "লেখো", "লিখো", "লেখ", "টাইপ",
        "back", "পেছনে", "পিছনে", "ফিরে যা",
        "করো", "দাও", "করুন", "দিন"
    )

    fun isLikelyCommand(text: String): Boolean = commandKeywords.any { text.contains(it, ignoreCase = true) }

    suspend fun processCommand(rawText: String): VoiceCommand? {
        if (pendingClarification == null && !isLikelyCommand(rawText)) return null

        val normalizedText = normalize(rawText)
        var command = validator.validate(parseDeterministically(rawText, normalizedText))

        if (command.action == CommandAction.UNKNOWN || command.confidence < 0.60f) {
            Log.d("VoiceCommandEngine", "Deterministic parser uncertain; using AI fallback")
            command = parseWithAI(rawText, normalizedText)?.let(validator::validate) ?: command
        }

        if (command.requiresClarification) {
            pendingClarification = command.action
        } else if (command.confidence >= 0.80f) {
            lastAction = command.action
            command.target?.let { lastTarget = it }
            pendingClarification = null
        } else {
            pendingClarification = null
        }

        Log.d("VoiceCommandEngine", "Final Command: $command")
        return command
    }

    private fun normalize(text: String): String = text.lowercase()
        .replace(Regex("[.,!?;:]"), " ")
        .replace(Regex("\\s+"), " ")
        .replace("একটু ", "")
        .trim()

    private fun parseDeterministically(rawText: String, text: String): VoiceCommand {
        val tokens = text.split(' ').filter { it.isNotBlank() }
        var action = CommandAction.UNKNOWN
        var target: String? = null
        var query: String? = null
        var confidence = 0f

        // CALL: "call Rahul", "Rahul কে call করো", "ফোন কর Rahul-কে"
        if (Regex("(?i)(\\bcall\\b|\\bphone\\b|\\bকল\\b|\\bফোন\\b|\\bডায়াল\\b)").containsMatchIn(text)) {
            action = CommandAction.CALL_CONTACT
            target = extractContactTarget(rawText)
            confidence = if (!target.isNullOrBlank()) 0.97f else 0.88f
        }

        // WhatsApp/message: supports both "send X to Y" and "Y-কে X message পাঠাও".
        val messageVerb = listOf("send", "message", "পাঠাও", "পাঠান", "মেসেজ", "লিখে দাও")
            .any { text.contains(it, ignoreCase = true) }
        if (messageVerb && (text.contains("whatsapp") || text.contains("হোয়াটসঅ্যাপ") || text.contains("message") || text.contains("মেসেজ") || text.contains("পাঠাও") || text.contains("পাঠান"))) {
            val parsed = parseMessageCommand(rawText)
            if (parsed.first != null && parsed.second != null) {
                action = CommandAction.SEND_MESSAGE
                target = parsed.first
                query = parsed.second
                confidence = 0.98f
            }
        }

        // YouTube search must be SEARCH_APP, not generic OPEN_APP.
        val youtube = text.contains("youtube") || text.contains("ইউটিউব")
        val searchVerb = text.contains("search") || text.contains("খুঁজে") || text.contains("সার্চ") || text.contains("খোঁজ")
        if (youtube && searchVerb) {
            action = CommandAction.SEARCH_APP
            target = "youtube"
            query = extractSearchQuery(text, setOf("youtube", "ইউটিউব", "search", "খুঁজে", "সার্চ", "খোঁজ", "করো", "কর", "দাও", "এ", "তে", "এর"))
            confidence = if (!query.isNullOrBlank()) 0.98f else 0.88f
        }

        // Google/web search: direct Google URL is used by the executor, so this
        // never depends on ACTION_WEB_SEARCH being handled by a specific app.
        val googleSearch = text.contains("google") && (searchVerb || tokens.size > 1)
        if (action == CommandAction.UNKNOWN && (googleSearch || searchVerb)) {
            action = CommandAction.SEARCH_WEB
            query = extractSearchQuery(text, setOf("google", "search", "খুঁজে", "সার্চ", "খোঁজ", "করো", "কর", "দাও", "এ", "তে", "এর"))
            confidence = if (!query.isNullOrBlank()) 0.97f else 0.85f
        }

        // Explicit media request.
        if (action == CommandAction.UNKNOWN && (text.contains("play ") || text.contains("চালাও") || text.contains("বাজাও"))) {
            action = CommandAction.PLAY_MEDIA
            query = extractSearchQuery(text, setOf("play", "চালাও", "বাজাও", "করো", "দাও"))
            confidence = if (!query.isNullOrBlank()) 0.94f else 0.82f
        }

        // Open app, but only after search/call/message have had a chance to claim the request.
        if (action == CommandAction.UNKNOWN && (text.contains("open") || text.contains("launch") || text.contains("খোলো") || text.contains("খুলে") || text.contains("চালু কর"))) {
            action = CommandAction.OPEN_APP
            val appNames = listOf("youtube", "ইউটিউব", "whatsapp", "হোয়াটসঅ্যাপ", "facebook", "chrome", "google", "play store", "প্লে স্টোর", "settings", "সেটিংস")
            target = appNames.firstOrNull { text.contains(it) }
            if (target == "ইউটিউব") target = "youtube"
            if (target == "হোয়াটসঅ্যাপ") target = "whatsapp"
            if (target == "প্লে স্টোর") target = "play store"
            if (target == "সেটিংস") target = "settings"
            if (target == null && pendingClarification == CommandAction.OPEN_APP) target = rawText.trim()
            confidence = if (!target.isNullOrBlank()) 0.96f else 0.84f
        }

        // Play Store commands.
        if (text.contains("play store") || text.contains("প্লে স্টোর")) {
            val install = text.contains("install") || text.contains("download") || text.contains("ইনস্টল") || text.contains("ডাউনলোড")
            action = if (install) CommandAction.INSTALL_APP else CommandAction.OPEN_PLAY_STORE
            target = extractSearchQuery(text, setOf("play", "store", "প্লে", "স্টোর", "install", "download", "ইনস্টল", "ডাউনলোড", "app", "অ্যাপ", "থেকে"))
            confidence = 0.96f
        }

        // GO_BACK: "go back", "পেছনে যাও", "back", "পিছনে", "ফিরে যাও"
        if (action == CommandAction.UNKNOWN && (
            text.contains("go back") || text.contains("back") || text.contains("পেছনে") ||
            text.contains("পিছনে") || text.contains("ফিরে যা") || text.contains("ফিরে যাও") ||
            text.contains("পেছনে যাও") || text.contains("পিছনে যাও")
        )) {
            action = CommandAction.GO_BACK
            confidence = 0.95f
        }

        // CLICK_TEXT: "click on <text>", "<text>-এ চাপ", "<text> চাপো", "tap <text>"
        if (action == CommandAction.UNKNOWN && (
            text.contains("click") || text.contains("tap") || text.contains("চাপ") || text.contains("ট্যাপ")
        )) {
            val clickTarget = extractClickTarget(rawText)
            if (!clickTarget.isNullOrBlank()) {
                action = CommandAction.CLICK_TEXT
                target = clickTarget
                confidence = 0.94f
            }
        }

        // TYPE_TEXT: "type <text>", "<text> লেখো", "লিখো <text>"
        if (action == CommandAction.UNKNOWN && (
            text.contains("type ") || text.contains("লেখো") || text.contains("লিখো") ||
            text.contains("লেখ") || text.contains("টাইপ")
        )) {
            val typeTarget = extractTypeTarget(rawText)
            if (!typeTarget.isNullOrBlank()) {
                action = CommandAction.TYPE_TEXT
                target = typeTarget
                confidence = 0.93f
            }
        }

        return VoiceCommand(rawText = rawText, normalizedText = text, action = action, target = target, query = query, confidence = confidence)
    }

    private fun extractClickTarget(raw: String): String? {
        val patterns = listOf(
            Regex("(?i)\\b(?:click|tap)\\s+(?:on\\s+)?(.+)$"),
            Regex("(?i)(.+)\\s*(?:-এ?ে?া?)\\s*চাপ(?:ো|ুন)?"),
            Regex("(?i)(.+)\\s*(?:-এ?ে?া?)\\s*ট্যাপ\\s*(?:কর(?:ো|ুন)?)?"),
            Regex("(?i)(.+)\\s*চাপ(?:ো|ুন)?")
        )
        return patterns.firstNotNullOfOrNull { it.find(raw)?.groupValues?.getOrNull(1)?.trim() }
            ?.trim(' ', ',', '.', '?', '!')
            ?.takeIf { it.isNotBlank() }
    }

    private fun extractTypeTarget(raw: String): String? {
        val patterns = listOf(
            Regex("(?i)\\b(?:type|write)\\s+(.+)$"),
            Regex("(?i)(?:লেখো|লিখো|লেখ|টাইপ\\s*কর(?:ো|ুন)?)\\s+(.+)$"),
            Regex("(?i)^(.+?)\\s*(?:লেখো|লিখো|লেখ)$")
        )
        return patterns.firstNotNullOfOrNull { it.find(raw)?.groupValues?.getOrNull(1)?.trim() }
            ?.trim(' ', ',', '.', '?', '!')
            ?.takeIf { it.isNotBlank() }
    }

    private fun extractContactTarget(raw: String): String? {
        val patterns = listOf(
            Regex("(?i)\\b(?:call|phone)\\s+(.+)$"),
            Regex("(?i)(?:ফোন|কল|ডায়াল)\\s+(?:কর(?:ো|ুন)?\\s*)?(.+?)(?:\\s*(?:কে|-কে))?$"),
            Regex("(?i)^(.+?)\\s*(?:কে|-কে)\\s+(?:call|phone|কল|ফোন)\\b.*$"),
            Regex("(?i)\\bcall\\s+(.+?)\\s*$")
        )
        return patterns.firstNotNullOfOrNull { it.find(raw)?.groupValues?.getOrNull(1)?.trim() }
            ?.trim(' ', ',', '.', '?', '!')
    }

    private fun parseMessageCommand(raw: String): Pair<String?, String?> {
        val patterns = listOf(
            Regex("(?is)(?:send|message|পাঠাও|পাঠান|মেসেজ|লিখে দাও)\\s+(.+?)\\s+(?:to|কে|কে)\\s+(.+?)(?:\\s+(?:on whatsapp|whatsapp|এ whatsapp|তে whatsapp))?$"),
            Regex("(?is)^(.+?)\\s*(?:কে|-কে)\\s+(.+?)\\s+(?:message|মেসেজ|পাঠাও|পাঠান)(?:\\s+(?:on whatsapp|whatsapp|এ whatsapp|তে whatsapp))?$")
        )
        val first = patterns.firstNotNullOfOrNull { it.find(raw) }
        if (first != null) {
            val a = first.groupValues.getOrNull(1)?.trim()
            val b = first.groupValues.getOrNull(2)?.trim()
            return if (raw.contains(Regex("(?i)(?:to|কে)"))) Pair(b, a) else Pair(a, b)
        }

        val toMatch = Regex("(?is)(.+?)\\s+(?:to|কে)\\s+(.+)$").find(raw)
        if (toMatch != null) {
            val before = toMatch.groupValues[1].replace(Regex("(?is)^(?:send|message|পাঠাও|পাঠান|মেসেজ)\\s*"), "").trim()
            val target = toMatch.groupValues[2].replace(Regex("(?is)\\s+(?:on whatsapp|whatsapp).*$"), "").trim()
            return Pair(target, before)
        }
        return Pair(null, null)
    }

    private fun extractSearchQuery(text: String, stopWords: Set<String>): String? {
        return text.split(' ')
            .filter { it.isNotBlank() && it !in stopWords }
            .joinToString(" ")
            .trim()
            .takeIf { it.isNotBlank() }
    }

    private suspend fun parseWithAI(rawText: String, normalizedText: String): VoiceCommand? {
        val prompt = """
            You are MAYA's Android action parser. Return ONLY JSON.
            User command: "$rawText"
            Supported actions:
            OPEN_APP, OPEN_PLAY_STORE, INSTALL_APP, SEARCH_WEB, SEARCH_APP,
            SEND_MESSAGE, CALL_CONTACT, PLAY_MEDIA, TYPE_TEXT, CLICK_TEXT, GO_BACK, UNKNOWN.
            Rules:
            - "call/contact/phone" means CALL_CONTACT; target is the exact person name or phone number.
            - "send/message/WhatsApp" means SEND_MESSAGE; target is the recipient and query is the exact message.
            - "YouTube search/find" means SEARCH_APP with target "youtube" and query as the search text.
            - "Google search/find" or a generic web search means SEARCH_WEB with query as the search text.
            - "open YouTube/WhatsApp/Google" means OPEN_APP.
            Schema: {"action":"...","target":"...","query":"...","confidence":0.0}
        """.trimIndent()
        return try {
            val response = aiOrchestrator.generateSingle(AIRequest(prompt = prompt, systemInstruction = "JSON only", temperature = 0f))
            var jsonStr = response.text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val obj = Json.parseToJsonElement(jsonStr).jsonObject
            val action = runCatching { CommandAction.valueOf(obj["action"]?.jsonPrimitive?.content ?: "UNKNOWN") }.getOrDefault(CommandAction.UNKNOWN)
            VoiceCommand(
                rawText = rawText,
                normalizedText = normalizedText,
                action = action,
                target = obj["target"]?.jsonPrimitive?.content?.takeIf { it != "null" && it.isNotBlank() },
                query = obj["query"]?.jsonPrimitive?.content?.takeIf { it != "null" && it.isNotBlank() },
                confidence = obj["confidence"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0.5f
            )
        } catch (e: Exception) {
            Log.e("VoiceCommandEngine", "AI command parsing failed", e)
            null
        }
    }
}
