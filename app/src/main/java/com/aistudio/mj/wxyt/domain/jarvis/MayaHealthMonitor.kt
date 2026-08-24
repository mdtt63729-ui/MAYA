package com.aistudio.mj.wxyt.domain.jarvis

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import com.aistudio.mj.wxyt.domain.security.SecureCredentialRepository
import com.aistudio.mj.wxyt.domain.settings.SettingsRepository

data class MayaHealth(
    val voiceReady: Boolean,
    val chatReady: Boolean,
    val memoryReady: Boolean,
    val accessibilityReady: Boolean,
    val microphoneReady: Boolean,
    val batteryPercent: Int,
    val heapMb: Long,
    val androidVersion: String
)

class MayaHealthMonitor(private val context: Context) {
    private val app = context.applicationContext
    fun snapshot(): MayaHealth {
        val battery = (app.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val credentials = SecureCredentialRepository(app)
        val settings = SettingsRepository.get(app).settings.value
        return MayaHealth(
            voiceReady = settings.voiceEnabled && credentials.geminiApiKey.isNotBlank(),
            chatReady = if (settings.chatProvider == "auto") listOf("openrouter", "opencode", "nvidia", "custom").any(credentials::hasApiKey) else credentials.hasApiKey(settings.chatProvider.takeUnless { it == "gemini" } ?: "openrouter"),
            memoryReady = settings.longTermMemoryEnabled,
            accessibilityReady = com.aistudio.mj.wxyt.accessibility.ORBAccessibilityService.instance != null,
            microphoneReady = app.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_MICROPHONE),
            batteryPercent = battery,
            heapMb = Debug.getNativeHeapAllocatedSize() / (1024 * 1024),
            androidVersion = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
        )
    }
}
