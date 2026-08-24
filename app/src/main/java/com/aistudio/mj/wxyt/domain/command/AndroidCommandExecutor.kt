package com.aistudio.mj.wxyt.domain.command

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import java.text.Normalizer
import java.util.Locale
import kotlin.math.min

/**
 * Native Android action executor.
 *
 * Important: this class reports success only after startActivity/Accessibility
 * dispatch succeeds. It never claims that an external app action completed
 * merely because an Intent was constructed.
 *
 * PRD 1 §3.1-3.2: Integrates IntentDispatcher for deep-link routing and
 * MediaSessionController for background media playback control.
 */
class AndroidCommandExecutor(private val context: Context) {
    private val policy = CommandPolicy()
    private val appResolver = AppResolver(context)
    private val intentDispatcher = IntentDispatcher(context)
    private val mediaSessionController: MediaSessionController? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) MediaSessionController(context) else null

    fun execute(command: VoiceCommand): ExecutionResult {
        if (!policy.isAllowed(command.action)) return fail(command, "এই command-টা এখন execute করা যাবে না।", ExecutionStatus.BLOCKED)
        return when (command.action) {
            CommandAction.OPEN_APP -> executeOpenApp(command)
            CommandAction.SEARCH_WEB -> executeSearchWeb(command)
            CommandAction.OPEN_SETTINGS -> executeOpenSettings(command)
            CommandAction.CALL_CONTACT -> executeCallContact(command)
            CommandAction.SEND_MESSAGE -> executeSendMessage(command)
            CommandAction.PLAY_MEDIA -> executePlayMedia(command)
            CommandAction.SEARCH_APP -> executeSearchApp(command)
            CommandAction.OPEN_PLAY_STORE -> executePlayStore(command, false)
            CommandAction.INSTALL_APP -> executePlayStore(command, true)
            CommandAction.GO_BACK -> executeGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK, command, "পেছনে যাচ্ছি।")
            // PRD 1 §3.2: Media control via MediaSession API
            CommandAction.PAUSE_MEDIA -> executeMediaControl(command, "pause")
            CommandAction.NEXT_TRACK -> executeMediaControl(command, "next")
            CommandAction.PREVIOUS_TRACK -> executeMediaControl(command, "previous")
            CommandAction.VOLUME_UP -> executeMediaControl(command, "volume_up")
            CommandAction.VOLUME_DOWN -> executeMediaControl(command, "volume_down")
            CommandAction.SET_VOLUME -> executeMediaControl(command, "set_volume", command.parameters["percent"]?.toIntOrNull())
            // PRD 1 §3.1: Deep-link media play
            CommandAction.PLAY_MEDIA_DEEP_LINK -> executeDeepLinkPlay(command)
            CommandAction.CLICK_TEXT -> {
                val text = command.target ?: command.query.orEmpty()
                val ok = com.aistudio.mj.wxyt.accessibility.ORBAccessibilityService.clickTextOnScreen(text)
                result(command, ok, text, if (ok) "\"$text\" চাপছি।" else "\"$text\" খুঁজে পাইনি।")
            }
            CommandAction.TYPE_TEXT -> {
                val text = command.target ?: command.query.orEmpty()
                if (text.isBlank()) {
                    fail(command, "কী লিখব বলো।")
                } else {
                    val ok = com.aistudio.mj.wxyt.accessibility.ORBAccessibilityService.setTextOnFocusedField(text)
                    result(command, ok, text, if (ok) "\"$text\" লিখে দিলাম।" else "কোনো text field-এ focus পাইনি, আগে কোথায় লিখব সেটা চুমুক দাও।")
                }
            }
            else -> fail(command, "এই command-টা execute করার implementation নেই।", ExecutionStatus.BLOCKED)
        }
    }

    private fun executeOpenSettings(command: VoiceCommand): ExecutionResult {
        val target = command.target.orEmpty().lowercase(Locale.getDefault())
        val intent = when {
            "wifi" in target || "ওয়াইফাই" in target -> Intent(Settings.ACTION_WIFI_SETTINGS)
            "bluetooth" in target || "ব্লুটুথ" in target -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            else -> Intent(Settings.ACTION_SETTINGS)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return start(command, intent, command.target ?: "Settings", "Settings খুলে দিলাম।")
    }

    private fun executeOpenApp(command: VoiceCommand): ExecutionResult {
        val target = command.target?.trim()
        if (target.isNullOrBlank()) return fail(command, "কোন অ্যাপটা খুলব বুঝতে পারিনি।")
        val canonical = canonicalAppName(target)
        if (canonical == "settings") return executeOpenSettings(command.copy(target = "settings", action = CommandAction.OPEN_SETTINGS))
        if (canonical == "camera") {
            val intent = Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return start(command, intent, "Camera", "ক্যামেরা খুলে দিলাম।")
        }

        // PRD 1 §3.1: Use IntentDispatcher for three-tier deep-link fallback routing
        val dispatchResult = intentDispatcher.launchApp(canonical)
        return if (dispatchResult.success) {
            result(command, true, dispatchResult.targetPackage ?: canonical,
                when (dispatchResult.method) {
                    IntentDispatcher.DispatchMethod.DEEP_LINK -> "ঠিক আছে, $canonical খুলে দিলাম (deep-link)।"
                    IntentDispatcher.DispatchMethod.PACKAGE_LAUNCH -> "ঠিক আছে, $canonical খুলে দিলাম।"
                    IntentDispatcher.DispatchMethod.WEB_FALLBACK -> "$canonical ইনস্টল নেই, browser-এ খুলে দিলাম।"
                    IntentDispatcher.DispatchMethod.FAILED -> "খুলতে পারিনি।"
                }
            )
        } else {
            val status = if (dispatchResult.message.contains("not installed") || dispatchResult.message.contains("ইনস্টল"))
                ExecutionStatus.APP_NOT_INSTALLED else ExecutionStatus.FAILED
            fail(command, dispatchResult.message, status)
        }
    }

    private fun executeSearchWeb(command: VoiceCommand): ExecutionResult {
        val query = command.query?.trim().orEmpty()
        if (query.isBlank()) return fail(command, "কী search করব বুঝতে পারিনি।")

        // ACTION_WEB_SEARCH is inconsistently handled by OEMs. Prefer an explicit
        // Google search URL, with a Google app fallback when it is installed.
        val googleUrl = Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
        val browser = Intent(Intent.ACTION_VIEW, googleUrl).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val googleApp = Intent(Intent.ACTION_VIEW, googleUrl).apply {
            setPackage("com.google.android.googlequicksearchbox")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            val pm = context.packageManager
            if (googleApp.resolveActivity(pm) != null) {
                context.startActivity(googleApp)
            } else if (browser.resolveActivity(pm) != null) {
                context.startActivity(browser)
            } else {
                val webSearch = Intent(Intent.ACTION_WEB_SEARCH).apply {
                    putExtra(SearchManager.QUERY, query)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webSearch)
            }
            result(command, true, "Google", "Google-এ $query search করছি।")
        } catch (e: Exception) {
            Log.e("MayaExecutor", "Web search failed", e)
            fail(command, "Google search চালু করতে পারলাম না।")
        }
    }

    private fun executeCallContact(command: VoiceCommand): ExecutionResult {
        val target = command.target?.trim()
        if (target.isNullOrBlank()) return fail(command, "কাকে কল করব বলো।")

        val number = resolvePhoneNumber(target)
        val hasCallPermission = context.checkSelfPermission(android.Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (number != null) {
            val uri = Uri.parse("tel:${Uri.encode(number)}")
            val intent = if (hasCallPermission) {
                Intent(Intent.ACTION_CALL, uri)
            } else {
                Intent(Intent.ACTION_DIAL, uri)
            }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return try {
                context.startActivity(intent)
                result(command, true, target, if (hasCallPermission) "$target-কে কল করছি।" else "$target-এর নম্বরে ডায়াল করছি, কল বাটনে চাপ দাও।")
            } catch (e: SecurityException) {
                Log.e("MayaExecutor", "Call permission rejected", e)
                val dialIntent = Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(dialIntent)
                    result(command, true, target, "$target-এর নম্বরে ডায়াল করছি।")
                } catch (e2: Exception) {
                    fail(command, "$target-কে কল শুরু করতে পারলাম না।")
                }
            } catch (e: Exception) {
                Log.e("MayaExecutor", "Call failed", e)
                fail(command, "$target-কে কল শুরু করতে পারলাম না।")
            }
        }

        // No number resolved — try dialer with raw target
        val dialUri = Uri.parse("tel:${Uri.encode(target)}")
        val dialIntent = Intent(Intent.ACTION_DIAL, dialUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(dialIntent)
            result(command, true, target, "$target-এর জন্য ডায়ালার খুলে দিলাম।")
        } catch (e: Exception) {
            Log.e("MayaExecutor", "Dialer fallback failed", e)
            fail(command, "$target নামে কোনো কন্টাক্ট/ফোন নম্বর পাইনি। Contacts permission দেওয়া আছে কিনা দেখো।")
        }
    }

    private fun executeSendMessage(command: VoiceCommand): ExecutionResult {
        val target = command.target?.trim()
        val message = command.query?.trim().orEmpty()
        if (target.isNullOrBlank()) return fail(command, "কাকে WhatsApp message পাঠাব?")
        if (message.isBlank()) return fail(command, "কী message পাঠাব সেটাও বলো।")

        val number = resolvePhoneNumber(target)
            ?: return fail(command, "$target নামে কোনো কন্টাক্ট/ফোন নম্বর পাইনি।")
        val whatsappNumber = normalizeWhatsAppNumber(number)

        // Preferred route: WhatsApp's native SENDTO handler with the message
        // prefilled. The accessibility service then presses Send when the UI is
        // actually visible, rather than pretending that opening a URL is sending.
        val accessibility = com.aistudio.mj.wxyt.accessibility.ORBAccessibilityService.instance
        if (accessibility == null) {
            return fail(command, "WhatsApp message auto-send করতে Accessibility Automation চালু করতে হবে।")
        }

        val native = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:${Uri.encode(whatsappNumber)}")
            setPackage("com.whatsapp")
            putExtra("sms_body", message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val webFallback = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$whatsappNumber?text=${Uri.encode(message)}")).apply {
            setPackage("com.whatsapp")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            com.aistudio.mj.wxyt.accessibility.ORBAccessibilityService.shouldAutoClick = true
            if (native.resolveActivity(context.packageManager) != null) {
                context.startActivity(native)
            } else {
                context.startActivity(webFallback)
            }
            result(command, true, target, "$target-কে WhatsApp message পাঠানোর জন্য খুলেছি।")
        } catch (e: Exception) {
            com.aistudio.mj.wxyt.accessibility.ORBAccessibilityService.shouldAutoClick = false
            Log.e("MayaExecutor", "WhatsApp send flow failed", e)
            fail(command, "WhatsApp message flow শুরু করতে পারলাম না।")
        }
    }

    private fun executePlayMedia(command: VoiceCommand): ExecutionResult {
        val query = (command.query ?: command.target).orEmpty().trim()
        if (query.isBlank()) return fail(command, "কী play করব বলো।")
        if (command.target?.let { canonicalAppName(it) } == "youtube" || command.normalizedText.contains("youtube") || command.normalizedText.contains("ইউটিউব")) {
            return executeYouTubeSearch(command, query)
        }
        val intent = Intent(android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            putExtra(SearchManager.QUERY, query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return start(command, intent, query, "$query play করছি।")
    }

    private fun executeSearchApp(command: VoiceCommand): ExecutionResult {
        val target = canonicalAppName(command.target.orEmpty())
        val query = command.query?.trim().orEmpty()
        if (target == "youtube") return executeYouTubeSearch(command, query)
        if (target == "google" || target.isBlank()) return executeSearchWeb(command)
        return fail(command, "${command.target} অ্যাপের ভিতরে search implementation নেই।")
    }

    private fun executeYouTubeSearch(command: VoiceCommand, query: String): ExecutionResult {
        if (query.isBlank()) return executeOpenApp(command.copy(action = CommandAction.OPEN_APP, target = "youtube"))
        val pm = context.packageManager
        val appIntent = Intent(Intent.ACTION_SEARCH).apply {
            setPackage("com.google.android.youtube")
            putExtra(SearchManager.QUERY, query)
            putExtra("query", query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            if (appIntent.resolveActivity(pm) != null) context.startActivity(appIntent) else context.startActivity(webIntent)
            result(command, true, "YouTube", "YouTube-এ $query search করছি।")
        } catch (e: Exception) {
            Log.e("MayaExecutor", "YouTube search failed", e)
            fail(command, "YouTube search চালু করতে পারলাম না।")
        }
    }

    private fun executePlayStore(command: VoiceCommand, install: Boolean): ExecutionResult {
        val query = command.target ?: command.query
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = if (query.isNullOrBlank()) Uri.parse("market://search?q=&c=apps") else Uri.parse("market://search?q=${Uri.encode(query)}&c=apps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            result(command, true, query ?: "Play Store", if (install) "Play Store-এ $query দেখাচ্ছি।" else "Play Store খুলে দিলাম।")
        } catch (_: Exception) {
            val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=${Uri.encode(query.orEmpty())}&c=apps")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return start(command, web, query ?: "Play Store", "Play Store খুলে দিলাম।")
        }
    }

    private fun executeGlobalAction(action: Int, command: VoiceCommand, message: String): ExecutionResult {
        val service = com.aistudio.mj.wxyt.accessibility.ORBAccessibilityService.instance
        val ok = service?.performGlobalAction(action) == true
        return result(command, ok, command.target, if (ok) message else "Automation service চালু নেই।")
    }

    // PRD 1 §3.2: Media session control implementation
    private fun executeMediaControl(command: VoiceCommand, action: String, param: Int? = null): ExecutionResult {
        val controller = mediaSessionController ?: return fail(command, "Media session control requires Android 5.0+.")
        val ok = when (action) {
            "pause" -> controller.playPause()
            "next" -> controller.next()
            "previous" -> controller.previous()
            "volume_up" -> controller.volumeUp()
            "volume_down" -> controller.volumeDown()
            "set_volume" -> {
                val pct = param ?: return fail(command, "Volume percentage not specified.")
                controller.setVolume(pct)
            }
            else -> false
        }
        val message = when (action) {
            "pause" -> if (ok) "Media play/pause করেছি।" else "Media control করতে পারিনি।"
            "next" -> if (ok) "পরের গান বাজাচ্ছি।" else "Next track করতে পারিনি।"
            "previous" -> if (ok) "আগের গান বাজাচ্ছি।" else "Previous track করতে পারিনি।"
            "volume_up" -> if (ok) "ভলিউম বাড়িয়েছি।" else "ভলিউম বাড়ানো যায়নি।"
            "volume_down" -> if (ok) "ভলিউম কমিয়েছি।" else "ভলিউম কমানো যায়নি।"
            "set_volume" -> if (ok) "ভলিউম $param% করেছি।" else "ভলিউম সেট করা যায়নি।"
            else -> "Unknown media action."
        }
        return result(command, ok, action, message)
    }

    // PRD 1 §3.1: Deep-link media play implementation
    private fun executeDeepLinkPlay(command: VoiceCommand): ExecutionResult {
        val appName = command.target ?: "youtube"
        val query = command.query.orEmpty().trim()
        val videoId = command.parameters["videoId"]
        val dispatchResult = intentDispatcher.playMedia(appName, query, videoId)
        return if (dispatchResult.success) {
            result(command, true, dispatchResult.targetPackage, dispatchResult.message)
        } else {
            fail(command, dispatchResult.message)
        }
    }

    private fun start(command: VoiceCommand, intent: Intent, target: String, successMessage: String): ExecutionResult {
        return try {
            if (intent.resolveActivity(context.packageManager) == null) return fail(command, "$target-এর জন্য কোনো compatible app পাওয়া যায়নি।")
            context.startActivity(intent)
            result(command, true, target, successMessage)
        } catch (e: Exception) {
            Log.e("MayaExecutor", "Intent execution failed: $intent", e)
            fail(command, "$target চালু করতে পারলাম না।")
        }
    }

    private fun resolvePhoneNumber(target: String): String? {
        val digits = target.filter { it.isDigit() }
        if (digits.length >= 7 && target.count { it.isDigit() } >= 7 && target.filter { !it.isDigit() && it !in "+- ()" }.isEmpty()) {
            return target.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        }
        if (context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return null
        val contacts = findContacts(target)
        return contacts.firstOrNull()?.second
    }

    private fun findContacts(pattern: String): List<Pair<String, String>> {
        val result = mutableListOf<Triple<Int, String, String>>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val queryCursor = runCatching { context.contentResolver.query(uri, projection, null, null, null) }.getOrNull() ?: return emptyList()
        queryCursor.use { cursor ->
            val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            if (nameIdx < 0 || numberIdx < 0) return emptyList()
            val needle = normalizeName(pattern)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIdx) ?: continue
                val number = cursor.getString(numberIdx) ?: continue
                val normalized = normalizeName(name)
                if (normalized.isBlank() || needle.isBlank()) continue
                val score = when {
                    normalized == needle -> 0
                    normalized.replace(" ", "") == needle.replace(" ", "") -> 1
                    normalized.startsWith(needle) -> 2
                    normalized.split(' ').any { it.startsWith(needle) } -> 3
                    normalized.contains(needle) -> 4
                    levenshtein(normalized.replace(" ", ""), needle.replace(" ", "")) <= maxOf(2, needle.length / 4) -> 5
                    else -> 99
                }
                if (score < 99) result += Triple(score, name, number)
            }
        }
        return result.sortedBy { it.first }.map { it.second to it.third }
    }

    private fun normalizeName(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.getDefault())
        .replace(Regex("[\\u200B-\\u200D\\uFEFF]"), "")
        .map { ch -> if (ch.isLetterOrDigit()) ch else ' ' }
        .joinToString("")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            val curr = IntArray(b.length + 1)
            curr[0] = i + 1
            for (j in b.indices) {
                curr[j + 1] = min(prev[j + 1] + 1, min(curr[j] + 1, prev[j] + if (a[i] == b[j]) 0 else 1))
            }
            prev = curr
        }
        return prev[b.length]
    }

    private fun normalizeWhatsAppNumber(number: String): String {
        var digits = number.filter { it.isDigit() }
        if (digits.startsWith("00")) digits = digits.removePrefix("00")
        if (digits.length == 10) {
            val country = detectCountryCallingCode()
            if (country.isNotBlank()) digits = country + digits
        }
        return digits
    }

    private fun detectCountryCallingCode(): String {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val iso = listOfNotNull(tm?.simCountryIso, tm?.networkCountryIso, Locale.getDefault().country)
            .firstOrNull { it.isNotBlank() }
            ?.uppercase(Locale.getDefault()) ?: return ""
        return when (iso) {
            "IN" -> "91"
            "BD" -> "880"
            "US", "CA" -> "1"
            "GB" -> "44"
            "AE" -> "971"
            "NP" -> "977"
            else -> ""
        }
    }

    private fun canonicalAppName(name: String): String = when (name.trim().lowercase(Locale.getDefault())) {
        "ইউটিউব" -> "youtube"
        "হোয়াটসঅ্যাপ", "হোয়াটসাপ" -> "whatsapp"
        "ক্রোম" -> "chrome"
        "গুগল" -> "google"
        "ফেসবুক" -> "facebook"
        "প্লে স্টোর" -> "play store"
        "সেটিংস" -> "settings"
        "ক্যামেরা" -> "camera"
        else -> name.trim().lowercase(Locale.getDefault())
    }

    private fun result(command: VoiceCommand, success: Boolean, target: String?, message: String): ExecutionResult =
        ExecutionResult(command.id, success, command.action, target, if (success) ExecutionStatus.COMPLETED else ExecutionStatus.FAILED, message)

    private fun fail(command: VoiceCommand, message: String, status: ExecutionStatus = ExecutionStatus.FAILED): ExecutionResult =
        ExecutionResult(command.id, false, command.action, command.target, status, message)
}
