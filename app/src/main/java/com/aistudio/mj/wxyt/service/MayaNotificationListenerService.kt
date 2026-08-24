package com.aistudio.mj.wxyt.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import com.aistudio.mj.wxyt.domain.settings.IndianLanguages
import com.aistudio.mj.wxyt.domain.settings.SettingsRepository
import java.util.Locale

/**
 * Optional notification bridge. It is completely permission-gated by Android's
 * notification-listener access screen and obeys MAYA's privacy settings.
 */
class MayaNotificationListenerService : NotificationListenerService() {
    private lateinit var settingsRepo: SettingsRepository
    private var tts: TextToSpeech? = null

    override fun onCreate() {
        super.onCreate()
        settingsRepo = SettingsRepository.get(this)
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val lang = IndianLanguages.findByName(settingsRepo.settings.value.responseLanguage)
                tts?.language = Locale.forLanguageTag(lang.localeTag)
            }
        }
        instance = this
    }

    override fun onDestroy() {
        instance = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val settings = settingsRepo.settings.value
        if (!settings.notificationReading || settings.privateMode) return
        if (sbn.packageName == packageName) return
        if (!isAllowedPackage(sbn.packageName, settings.notificationWhitelist, settings.notificationBlacklist)) return
        if (settings.importantNotificationFilter && !isImportant(sbn.notification)) return

        val extras = sbn.notification.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        if (title.isBlank() && text.isBlank()) return

        val summary = when {
            settings.notificationPrivacyStrict || settings.notificationPrivacy == "Private" ->
                "New notification from ${appLabel(sbn.packageName)}."
            settings.notificationSummaries ->
                listOf(appLabel(sbn.packageName), title).filter { it.isNotBlank() }.joinToString(": ")
            else ->
                listOf(title, text).filter { it.isNotBlank() }.joinToString(": ")
        }

        if (settings.readNotificationsAloud && settings.voiceEnabled) {
            tts?.speak(summary, TextToSpeech.QUEUE_FLUSH, null, "MAYA_NOTIFICATION_${System.currentTimeMillis()}")
        }

        lastNotification = summary
    }

    private fun isImportant(notification: Notification): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val importance = notification.channelId?.let { id ->
                getSystemService(android.app.NotificationManager::class.java)?.getNotificationChannel(id)?.importance
            } ?: android.app.NotificationManager.IMPORTANCE_DEFAULT
            return importance >= android.app.NotificationManager.IMPORTANCE_DEFAULT
        }
        return notification.priority >= Notification.PRIORITY_DEFAULT
    }

    private fun isAllowedPackage(pkg: String, whitelistCsv: String, blacklistCsv: String): Boolean {
        val whitelist = whitelistCsv.split(',').map { it.trim() }.filter { it.isNotBlank() }
        val blacklist = blacklistCsv.split(',').map { it.trim() }.filter { it.isNotBlank() }
        if (blacklist.any { pkg == it || pkg.startsWith("$it.") }) return false
        return whitelist.isEmpty() || whitelist.any { pkg == it || pkg.startsWith("$it.") }
    }

    private fun appLabel(pkg: String): String = try {
        val info = packageManager.getApplicationInfo(pkg, 0)
        packageManager.getApplicationLabel(info).toString()
    } catch (_: Exception) { pkg }

    companion object {
        @Volatile var instance: MayaNotificationListenerService? = null
            private set
        @Volatile var lastNotification: String? = null
            private set
    }
}
