package com.aistudio.mj.wxyt.domain.brain

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.aistudio.mj.wxyt.domain.chat.LongTermMemoryRepository
import com.aistudio.mj.wxyt.domain.settings.MJSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Local cognition layer: memory + device context + deterministic learning. */
class MayaBrain(context: Context) {
    private val appContext = context.applicationContext
    private val memory = LongTermMemoryRepository(appContext)
    private val cognitive = JarvisCognitiveCore(appContext)
    private val knowledgeGraph = MayaKnowledgeGraph(appContext)

    suspend fun buildContext(userText: String, settings: MJSettings): String {
        val sections = mutableListOf<String>()
        if (settings.useDeviceContext) sections += deviceContext()
        if (settings.temporalIntelligence) sections += "TEMPORAL_CONTEXT: ${cognitive.buildContextSignals(settings).localTime}"
        if (settings.knowledgeGraph) {
            val graph = knowledgeGraph.related("user").take(12)
            if (graph.isNotEmpty()) sections += "KNOWLEDGE_GRAPH:\n" + graph.joinToString("\n") { "${it.first} --${it.second}--> ${it.third}" }
        }
        if (settings.longTermMemoryEnabled) {
            val remembered = memory.relevantContext(userText, settings.memoryDepth)
            if (remembered.isNotBlank()) sections += "LONG_TERM_MEMORY:\n$remembered"
        }
        return sections.joinToString("\n\n")
    }

    suspend fun learnFromUserText(text: String, settings: MJSettings) {
        if (!settings.longTermMemoryEnabled || !settings.memoryAutoLearn || settings.privateMode) return
        val normalized = text.trim()
        if (normalized.length < 6) return
        val patterns = listOf(
            Regex("(?i)\\bremember that\\s+(.+)"),
            Regex("(?i)\\bmy name is\\s+(.+)"),
            Regex("(?i)\\bi live in\\s+(.+)"),
            Regex("(?i)\\bi like\\s+(.+)"),
            Regex("(?i)\\bi love\\s+(.+)"),
            Regex("আমার নাম\\s+(.+)"),
            Regex("আমি থাকি\\s+(.+)"),
            Regex("আমার পছন্দ\\s+(.+)"),
            Regex("মনে রেখো\\s+(.+)")
        )
        patterns.firstNotNullOfOrNull { it.find(normalized) }?.let { match ->
            val value = match.groupValues.last().trim().trimEnd('.', '!', '?')
            if (value.isBlank()) return
            val key = when {
                normalized.contains("name", true) || normalized.contains("নাম") -> "user_name"
                normalized.contains("live", true) || normalized.contains("থাকি") -> "home_location"
                normalized.contains("like", true) || normalized.contains("পছন্দ") -> "preference"
                normalized.contains("love", true) -> "favorite"
                else -> "explicit_memory"
            }
            // With approval enabled, only an explicit "remember" request is persisted.
            // With approval disabled, the other explicit preference/fact patterns are allowed.
            val explicitlyApproved = Regex("(?i)\\bremember that\\b|মনে রেখো|মনে রাখুন|মনে রাখ(?:ো|বে)").containsMatchIn(normalized)
            if (settings.memoryApproval && !explicitlyApproved) return@let
            memory.remember(key, value, category = "personal", importance = if (key == "user_name") 10 else 8)
            if (settings.knowledgeGraph) knowledgeGraph.link("user", key, value)
        }
    }


    suspend fun clearMemory() {
        memory.clear()
        knowledgeGraph.clear()
    }

    private fun deviceContext(): String {
        val batteryManager = appContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val battery = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val power = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val interactive = if (Build.VERSION.SDK_INT >= 20) power.isInteractive else true
        val now = SimpleDateFormat("EEEE, dd MMMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
        val airplane = try {
            Settings.Global.getInt(appContext.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
        } catch (_: Exception) { false }
        return "DEVICE_CONTEXT:\nTime: $now\nBattery: $battery%\nScreenInteractive: $interactive\nAirplaneMode: $airplane\nAndroid: ${Build.VERSION.RELEASE}"
    }
}
