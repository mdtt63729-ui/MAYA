package com.aistudio.mj.wxyt.domain.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import com.aistudio.mj.wxyt.accessibility.ORBAccessibilityService
import com.aistudio.mj.wxyt.domain.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Tool Execution Engine — ported from Maya.
 * Handles all AI function-calling tools for Android device control.
 *
 * Tools:
 *   openApp, searchAndCallContact, sendWhatsAppMessage, sendGmail,
 *   searchYouTube, adjustVolume, setVolumePercent, getSimCardInfo,
 *   openQuickSettings, clickTextOnScreen, openNotificationPanel,
 *   toggleTorch, setBrightness, playMedia
 */
class ToolExecutionEngine(private val context: Context) {
    private val settingsRepo = SettingsRepository.get(context)

    suspend fun execute(name: String, args: JsonObject): String = withContext(Dispatchers.IO) {
        try {
            when (name) {
                "openApp" -> {
                    val appName = args["packageName"]?.jsonPrimitive?.content
                        ?: return@withContext "Error: Missing packageName"
                    openAppGeneric(appName)
                }

                "searchAndCallContact" -> {
                    val contactName = args["contactName"]?.jsonPrimitive?.content
                        ?: return@withContext "Error: Missing contactName"
                    val useDialer = args["useDialer"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
                    val simSlot = args["simSlot"]?.jsonPrimitive?.content?.toIntOrNull()
                    callContact(contactName, useDialer, simSlot)
                }

                "sendWhatsAppMessage" -> {
                    val contactName = args["contactName"]?.jsonPrimitive?.content
                        ?: return@withContext "Error: Missing contactName"
                    val message = args["message"]?.jsonPrimitive?.content ?: ""
                    sendWhatsApp(contactName, message)
                }

                "sendGmail" -> {
                    val recipient = args["recipientEmail"]?.jsonPrimitive?.content ?: ""
                    val subject = args["subject"]?.jsonPrimitive?.content ?: ""
                    val body = args["body"]?.jsonPrimitive?.content ?: ""
                    sendEmail(recipient, subject, body)
                }

                "searchYouTube" -> {
                    val query = args["query"]?.jsonPrimitive?.content
                        ?: return@withContext "Error: Missing query"
                    searchYouTube(query)
                }

                "adjustVolume" -> {
                    val direction = args["direction"]?.jsonPrimitive?.content
                        ?: return@withContext "Error: Missing direction (up/down/mute/unmute/max)"
                    adjustSystemVolume(direction)
                }

                "toggleTorch" -> {
                    val state = args["state"]?.jsonPrimitive?.content
                        ?: return@withContext "Error: Missing state (on/off)"
                    toggleTorch(state)
                }

                "setBrightness" -> {
                    val levelStr = args["level"]?.jsonPrimitive?.content
                        ?: return@withContext "Error: Missing level"
                    val level = levelStr.toIntOrNull() ?: 50
                    setBrightness(level)
                }

                "setVolumePercent" -> {
                    val percentStr = args["percent"]?.jsonPrimitive?.content
                        ?: return@withContext "Error: Missing percent"
                    val percent = percentStr.toIntOrNull() ?: 50
                    setVolumePercent(percent)
                }

                "openNotificationPanel" -> {
                    if (!settingsRepo.settings.value.notificationActions) {
                        return@withContext "Notification actions are disabled in MAYA Settings."
                    }
                    openNotificationPanel()
                }

                "openQuickSettings" -> {
                    if (!settingsRepo.settings.value.allowAccessibilityAutomation) {
                        return@withContext "Accessibility automation is disabled in MAYA Settings."
                    }
                    val service = ORBAccessibilityService.instance
                    if (service != null && service.performGlobalAction(
                            android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
                        )
                    ) {
                        "Opened quick settings panel."
                    } else "Failed to open."
                }

                "clickTextOnScreen" -> {
                    if (!settingsRepo.settings.value.allowAccessibilityAutomation || !settingsRepo.settings.value.screenAutomation) {
                        return@withContext "Screen automation is disabled in MAYA Settings."
                    }
                    val text = args["text"]?.jsonPrimitive?.content
                        ?: return@withContext "Error: Missing text"
                    val success = ORBAccessibilityService.clickTextOnScreen(text)
                    if (success) "Clicked on '$text'." else "Failed to click on '$text'."
                }

                "getSimCardInfo" -> {
                    getSimCardInfo()
                }

                "playMedia" -> {
                    val query = args["query"]?.jsonPrimitive?.content
                        ?: return@withContext "Error: Missing query"
                    playMedia(query)
                }

                else -> "Error: Tool $name not found."
            }
        } catch (e: Exception) {
            "Error executing $name: ${e.message}"
        }
    }

    // ==================== Tool Implementations ====================

    private fun openAppGeneric(appName: String): String {
        val lowerName = appName.lowercase()

        if (lowerName == "camera" || lowerName == "kamera") {
            val intent = Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return "Camera opened"
            } catch (e: Exception) {
                // Fallback to searching packages
            }
        }

        val pm = context.packageManager
        val packages = pm.getInstalledApplications(0)

        var targetPackage: String? = null
        for (app in packages) {
            val name = pm.getApplicationLabel(app).toString().lowercase()
            if (name.contains(lowerName)) {
                targetPackage = app.packageName
                break
            }
        }

        if (targetPackage == null) {
            return "Could not find an installed app matching '$appName'"
        }

        val launchIntent = pm.getLaunchIntentForPackage(targetPackage)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            return "App '$appName' launched successfully."
        }
        return "Could not launch app '$appName'"
    }

    private fun callContact(nameOrNumber: String, useDialer: Boolean = false, simSlot: Int? = null): String {
        if (nameOrNumber == "121" || nameOrNumber == "*121#") {
            return "ERROR: You tried to call 121 instead of using the contact name. DO NOT invent numbers. Use the contact name provided by the user (e.g. 'Rohit')."
        }

        val isNumber = nameOrNumber.count { it.isDigit() } >= 7 ||
                nameOrNumber.matches(Regex("^[0-9+\\-*#]+$"))

        val number = if (isNumber) {
            nameOrNumber.replace(Regex("[^0-9+*#]"), "")
        } else {
            val matches = findContacts(nameOrNumber)
            if (matches.isEmpty()) return "Could not find a phone number for '$nameOrNumber'. Please ask the user for the correct name."
            matches.first().second
        }

        val action = if (useDialer) Intent.ACTION_DIAL else Intent.ACTION_CALL
        val callIntent = Intent(action)
        callIntent.data = Uri.parse("tel:$number")
        callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (!useDialer && simSlot != null) {
            try {
                val slotIndex = simSlot - 1
                callIntent.putExtra("com.android.phone.force.slot", true)
                callIntent.putExtra("com.android.phone.extra.slot", slotIndex)
                callIntent.putExtra("simSlot", slotIndex)

                if (context.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as android.telecom.TelecomManager
                    val phoneAccounts = telecomManager.callCapablePhoneAccounts
                    if (slotIndex in 0 until phoneAccounts.size) {
                        callIntent.putExtra(
                            android.telecom.TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE,
                            phoneAccounts[slotIndex]
                        )
                    }
                }
            } catch (e: Exception) {
                // Ignore exceptions with telecom manager
            }
        }

        return try {
            context.startActivity(callIntent)
            if (useDialer) "Opened dialer for $nameOrNumber ($number)"
            else "Calling $nameOrNumber ($number) via SIM $simSlot..."
        } catch (e: SecurityException) {
            "Missing CALL_PHONE permission."
        }
    }

    private fun sendWhatsApp(nameOrNumber: String, message: String): String {
        if (nameOrNumber == "121" || nameOrNumber == "*121#") {
            return "ERROR: You tried to use 121 instead of the contact name. DO NOT invent numbers. Use the exact contact name provided by the user."
        }

        val isNumber = nameOrNumber.count { it.isDigit() } >= 7 ||
                nameOrNumber.matches(Regex("^[0-9+\\-*#]+$"))

        val number = if (isNumber) {
            nameOrNumber
        } else {
            val matches = findContacts(nameOrNumber)
            if (matches.isEmpty()) return "Could not find a phone number for '$nameOrNumber'. Please ask the user for the correct name."
            matches.first().second
        }

        val cleanNumber = number.replace(Regex("[^0-9+]"), "")

        val url = "https://api.whatsapp.com/send?phone=$cleanNumber&text=${Uri.encode(message)}"
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse(url)
        intent.setPackage("com.whatsapp")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            ORBAccessibilityService.shouldAutoClick = true
            context.startActivity(intent)
            "I am automatically sending the WhatsApp message to $nameOrNumber."
        } catch (e: Exception) {
            ORBAccessibilityService.shouldAutoClick = false
            "WhatsApp may not be installed."
        }
    }

    private fun sendEmail(recipient: String, subject: String, body: String): String {
        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(emailIntent)
            "Opened email client with draft."
        } catch (e: Exception) {
            "No email client found."
        }
    }

    private fun searchYouTube(query: String): String {
        val intent = Intent(Intent.ACTION_SEARCH)
        intent.setPackage("com.google.android.youtube")
        intent.putExtra("query", query)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            "Opened YouTube with search query: $query"
        } catch (e: Exception) {
            "YouTube app not found on device."
        }
    }

    private fun levenshtein(lhs: CharSequence, rhs: CharSequence): Int {
        val lhsLength = lhs.length
        val rhsLength = rhs.length

        var cost = IntArray(lhsLength + 1) { it }
        var newCost = IntArray(lhsLength + 1)

        for (i in 1..rhsLength) {
            newCost[0] = i
            for (j in 1..lhsLength) {
                val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                val costReplace = cost[j - 1] + match
                val costInsert = cost[j] + 1
                val costDelete = newCost[j - 1] + 1
                newCost[j] = minOf(costInsert, costDelete, costReplace)
            }
            val swap = cost
            cost = newCost
            newCost = swap
        }
        return cost[lhsLength]
    }

    private fun findContacts(namePattern: String): List<Pair<String, String>> {
        if (context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return emptyList()
        }

        try {
            val fallbackUri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val fbProjection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            context.contentResolver.query(fallbackUri, fbProjection, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                val exactMatches = mutableListOf<Pair<String, String>>()
                val startsWithMatches = mutableListOf<Pair<String, String>>()
                val containsMatches = mutableListOf<Pair<String, String>>()
                val fuzzyMatches = mutableListOf<Pair<Int, Pair<String, String>>>()

                val cleanPattern = namePattern.lowercase().replace(Regex("[^a-z0-9 ]"), "").trim()
                val searchWords = cleanPattern.split(" ").filter { it.isNotEmpty() }

                while (cursor.moveToNext()) {
                    val contactName = cursor.getString(nameIdx) ?: continue
                    val contactNum = cursor.getString(numIdx) ?: continue

                    val cleanContactName = contactName.lowercase().replace(Regex("[^a-z0-9 ]"), "").trim()
                    if (cleanContactName.isEmpty()) continue

                    val contactNameNoSpace = cleanContactName.replace(" ", "")
                    val patternNoSpace = cleanPattern.replace(" ", "")

                    if (contactNameNoSpace == patternNoSpace || cleanContactName == cleanPattern) {
                        exactMatches.add(Pair(contactName, contactNum))
                    } else if (contactNameNoSpace.startsWith(patternNoSpace) || cleanContactName.startsWith(cleanPattern)) {
                        startsWithMatches.add(Pair(contactName, contactNum))
                    } else if (searchWords.isNotEmpty() && searchWords.all { cleanContactName.contains(it) }) {
                        containsMatches.add(Pair(contactName, contactNum))
                    } else if (contactNameNoSpace.contains(patternNoSpace) && patternNoSpace.length > 2) {
                        containsMatches.add(Pair(contactName, contactNum))
                    }

                    val distance = levenshtein(contactNameNoSpace, patternNoSpace)
                    if (distance <= 2 && patternNoSpace.length > 3) {
                        fuzzyMatches.add(Pair(distance, Pair(contactName, contactNum)))
                    }
                }

                if (exactMatches.isNotEmpty()) return exactMatches.distinctBy { it.second }
                if (startsWithMatches.isNotEmpty()) return startsWithMatches.distinctBy { it.second }
                if (containsMatches.isNotEmpty()) return containsMatches.distinctBy { it.second }
                if (fuzzyMatches.isNotEmpty()) {
                    return fuzzyMatches.sortedBy { it.first }.map { it.second }.distinctBy { it.second }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ORBTools", "Error finding contact", e)
        }
        return emptyList()
    }

    private fun adjustSystemVolume(direction: String): String {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val streamType = android.media.AudioManager.STREAM_MUSIC
        return try {
            when (direction.lowercase()) {
                "up" -> {
                    audioManager.adjustStreamVolume(streamType, android.media.AudioManager.ADJUST_RAISE, android.media.AudioManager.FLAG_SHOW_UI)
                    "Volume increased"
                }
                "down" -> {
                    audioManager.adjustStreamVolume(streamType, android.media.AudioManager.ADJUST_LOWER, android.media.AudioManager.FLAG_SHOW_UI)
                    "Volume decreased"
                }
                "mute" -> {
                    audioManager.adjustStreamVolume(streamType, android.media.AudioManager.ADJUST_MUTE, android.media.AudioManager.FLAG_SHOW_UI)
                    "Volume muted"
                }
                "unmute" -> {
                    audioManager.adjustStreamVolume(streamType, android.media.AudioManager.ADJUST_UNMUTE, android.media.AudioManager.FLAG_SHOW_UI)
                    "Volume unmuted"
                }
                "max" -> {
                    val maxVol = audioManager.getStreamMaxVolume(streamType)
                    audioManager.setStreamVolume(streamType, maxVol, android.media.AudioManager.FLAG_SHOW_UI)
                    "Volume set to maximum"
                }
                else -> "Unknown volume direction. Use up, down, mute, or max."
            }
        } catch (e: Exception) {
            "Failed to adjust volume: ${e.message}"
        }
    }

    private fun toggleTorch(state: String): String {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
            val cameraId = cameraManager.cameraIdList[0]
            if (state.lowercase() == "on") {
                cameraManager.setTorchMode(cameraId, true)
                "Torch turned on"
            } else {
                cameraManager.setTorchMode(cameraId, false)
                "Torch turned off"
            }
        } catch (e: Exception) {
            "Failed to toggle torch: ${e.message}"
        }
    }

    private fun setBrightness(level: Int): String {
        return try {
            if (!android.provider.Settings.System.canWrite(context)) {
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS)
                intent.data = android.net.Uri.parse("package:" + context.packageName)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return "Prompted user for write settings permission to change brightness. Please try again after permission is granted."
            }

            val brightness = (level * 255) / 100
            android.provider.Settings.System.putInt(
                context.contentResolver,
                android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE,
                android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            android.provider.Settings.System.putInt(
                context.contentResolver,
                android.provider.Settings.System.SCREEN_BRIGHTNESS,
                brightness
            )
            "Brightness set to $level%"
        } catch (e: Exception) {
            "Failed to set brightness: ${e.message}"
        }
    }

    private fun playMedia(query: String): String {
        return try {
            val intent = Intent(android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            intent.putExtra(android.app.SearchManager.QUERY, query)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "Started playing media for query: $query"
        } catch (e: Exception) {
            "Failed to play media (no suitable app found): ${e.message}"
        }
    }

    private fun setVolumePercent(percent: Int): String {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            val streamType = android.media.AudioManager.STREAM_MUSIC
            val maxVol = audioManager.getStreamMaxVolume(streamType)
            val targetVol = (maxVol * Math.max(0, Math.min(100, percent))) / 100
            audioManager.setStreamVolume(streamType, targetVol, android.media.AudioManager.FLAG_SHOW_UI)
            "Volume set to $percent%"
        } catch (e: Exception) {
            "Failed to set volume: ${e.message}"
        }
    }

    private fun openNotificationPanel(): String {
        return try {
            val service = ORBAccessibilityService.instance
            if (service != null) {
                val success = service.performGlobalAction(
                    android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
                )
                if (success) {
                    "Opened notification panel."
                } else {
                    "Accessibility service failed to open notification panel."
                }
            } else {
                "Accessibility service not running. Enable ORB Automation in Settings > Accessibility."
            }
        } catch (e: Exception) {
            "Error opening notification panel: ${e.message}"
        }
    }

    private fun getSimCardInfo(): String {
        if (context.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return "Unable to determine SIM cards because READ_PHONE_STATE permission is lacking. Proceed assuming 1 SIM."
        }
        return try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as android.telecom.TelecomManager
            val phoneAccounts = telecomManager.callCapablePhoneAccounts
            "The device has ${phoneAccounts.size} active calling SIM cards."
        } catch (e: Exception) {
            "Error determining SIM cards: ${e.message}. Proceed assuming 1 SIM."
        }
    }
}
