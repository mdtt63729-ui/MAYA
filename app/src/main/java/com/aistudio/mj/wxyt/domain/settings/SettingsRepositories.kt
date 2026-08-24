package com.aistudio.mj.wxyt.domain.settings

import android.content.Context
import android.content.SharedPreferences

class VoiceSettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("voice_settings", Context.MODE_PRIVATE)
    
    var activeVoice: String
        get() = prefs.getString("active_voice", "Natural Female") ?: "Natural Female"
        set(value) = prefs.edit().putString("active_voice", value).apply()
        
    var speakingSpeed: Float
        get() = prefs.getFloat("speaking_speed", 1.0f)
        set(value) = prefs.edit().putFloat("speaking_speed", value).apply()
        
    var voiceEnabled: Boolean
        get() = prefs.getBoolean("voice_enabled", true)
        set(value) = prefs.edit().putBoolean("voice_enabled", value).apply()
}

/**
 * ApiSecretsRepository — DEPRECATED.
 *
 * Gemini API key storage has been moved to SecureCredentialRepository
 * (EncryptedSharedPreferences). This class is retained only for migration
 * purposes — SecureCredentialRepository reads old values from "api_secrets"
 * prefs and migrates them to encrypted storage on first access.
 *
 * Do NOT write new Gemini API key storage here.
 */
@Deprecated("Use SecureCredentialRepository for all API key storage")
class ApiSecretsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("api_secrets", Context.MODE_PRIVATE)
    
    @Deprecated("Use SecureCredentialRepository.geminiApiKey instead")
    var geminiApiKey: String
        get() = ""  // Always returns empty — migrated to SecureCredentialRepository
        set(value) { /* No-op — use SecureCredentialRepository */ }
}

class AISettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
    var activeModel: String
        get() = prefs.getString("active_model", "") ?: ""
        set(value) = prefs.edit().putString("active_model", value).apply()
}

class BackgroundAssistantSettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("bg_settings", Context.MODE_PRIVATE)
    var isBackgroundEnabled: Boolean
        get() = prefs.getBoolean("bg_enabled", false)
        set(value) = prefs.edit().putBoolean("bg_enabled", value).apply()
}

class WakeWordSettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("wakeword_settings", Context.MODE_PRIVATE)
    var isWakeWordEnabled: Boolean
        get() = prefs.getBoolean("wakeword_enabled", false)
        set(value) = prefs.edit().putBoolean("wakeword_enabled", value).apply()
    
    var wakeWord: String
        get() = prefs.getString("wake_word", "Hey MAYA") ?: "Hey MAYA"
        set(value) = prefs.edit().putString("wake_word", value).apply()
}

class AppearanceSettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("appearance_settings", Context.MODE_PRIVATE)
    var theme: String
        get() = prefs.getString("theme", "System") ?: "System"
        set(value) = prefs.edit().putString("theme", value).apply()
}

class SecuritySettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("security_settings", Context.MODE_PRIVATE)
    var requireConfirmation: Boolean
        get() = prefs.getBoolean("require_confirmation", false)
        set(value) = prefs.edit().putBoolean("require_confirmation", value).apply()
}

class PrivacySettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("privacy_settings", Context.MODE_PRIVATE)
    var saveHistory: Boolean
        get() = prefs.getBoolean("save_history", true)
        set(value) = prefs.edit().putBoolean("save_history", value).apply()
}
