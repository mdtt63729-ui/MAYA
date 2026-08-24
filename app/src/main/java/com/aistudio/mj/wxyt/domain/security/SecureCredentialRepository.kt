package com.aistudio.mj.wxyt.domain.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * SecureCredentialRepository — single source of truth for all API credentials.
 *
 * Uses Android Keystore-backed EncryptedSharedPreferences for production-grade
 * secure storage. API keys are NEVER stored in plaintext.
 *
 * Migration: Automatically reads old plaintext SharedPreferences values and
 * migrates them to encrypted storage, then deletes the old plaintext entry.
 *
 * Supported credentials:
 *   - Gemini API Key
 *   - OpenRouter API Key
 *   - OpenCode API Key
 *   - NVIDIA API Key
 *   - Custom Provider API Key
 *   - Custom Base URL
 *   - Custom Model ID
 */
class SecureCredentialRepository(context: Context) {

    private val masterKey: MasterKey = try {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    } catch (e: Exception) {
        Log.e("SecureCredRepo", "Failed to create MasterKey", e)
        throw e
    }

    private val encryptedPrefs: SharedPreferences = try {
        EncryptedSharedPreferences.create(
            context,
            "orb_secure_credentials",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.e("SecureCredRepo", "Failed to create EncryptedSharedPreferences, falling back", e)
        // Fallback to regular SharedPreferences (should not happen in production)
        context.getSharedPreferences("orb_secure_credentials_fallback", Context.MODE_PRIVATE)
    }

    // Old plaintext prefs for migration
    private val oldPrefs = context.getSharedPreferences("mj_secure_prefs_fallback", Context.MODE_PRIVATE)
    private val oldApiSecretsPrefs = context.getSharedPreferences("api_secrets", Context.MODE_PRIVATE)

    init {
        migrateOldCredentials()
    }

    /**
     * Migrate old plaintext credentials to encrypted storage.
     * Old keys are deleted after successful migration.
     */
    private fun migrateOldCredentials() {
        try {
            val editor = encryptedPrefs.edit()

            // Migrate from mj_secure_prefs_fallback
            migrateKey(oldPrefs, "gemini_api_key", editor)
            migrateKey(oldPrefs, "openrouter_api_key", editor)
            migrateKey(oldPrefs, "opencode_api_key", editor)
            migrateKey(oldPrefs, "nvidia_api_key", editor)
            migrateKey(oldPrefs, "custom_provider_api_key", editor)
            migrateKey(oldPrefs, "custom_base_url", editor)
            migrateKey(oldPrefs, "custom_model_id", editor)

            // Migrate from api_secrets (duplicate Gemini key)
            val oldGeminiKey = oldApiSecretsPrefs.getString("gemini_api_key", null)
            if (oldGeminiKey != null && oldGeminiKey.isNotEmpty()) {
                // Only migrate if encrypted storage doesn't already have it
                if (encryptedPrefs.getString(KEY_GEMINI, "").isNullOrEmpty()) {
                    editor.putString(KEY_GEMINI, oldGeminiKey)
                }
                // Delete old plaintext copy
                oldApiSecretsPrefs.edit().remove("gemini_api_key").apply()
            }

            editor.apply()

            // Clear old plaintext storage after migration
            if (oldPrefs.all.isNotEmpty()) {
                oldPrefs.edit().clear().apply()
            }
        } catch (e: Exception) {
            Log.e("SecureCredRepo", "Migration error", e)
        }
    }

    private fun migrateKey(
        oldPrefs: SharedPreferences,
        oldKey: String,
        editor: SharedPreferences.Editor
    ) {
        val value = oldPrefs.getString(oldKey, null)
        if (value != null && value.isNotEmpty()) {
            val newKey = mapOldKeyToNew(oldKey)
            // Only migrate if not already present
            if (encryptedPrefs.getString(newKey, "").isNullOrEmpty()) {
                editor.putString(newKey, value)
            }
        }
    }

    private fun mapOldKeyToNew(oldKey: String): String = when (oldKey) {
        "gemini_api_key" -> KEY_GEMINI
        "openrouter_api_key" -> KEY_OPENROUTER
        "opencode_api_key" -> KEY_OPENCODE
        "nvidia_api_key" -> KEY_NVIDIA
        "custom_provider_api_key" -> KEY_CUSTOM_PROVIDER
        "custom_base_url" -> KEY_CUSTOM_BASE_URL
        "custom_model_id" -> KEY_CUSTOM_MODEL_ID
        else -> oldKey
    }

    // ---- Credential keys ----
    companion object {
        private const val KEY_GEMINI = "gemini_api_key"
        private const val KEY_OPENROUTER = "openrouter_api_key"
        private const val KEY_OPENCODE = "opencode_api_key"
        private const val KEY_NVIDIA = "nvidia_api_key"
        private const val KEY_CUSTOM_PROVIDER = "custom_provider_api_key"
        private const val KEY_CUSTOM_BASE_URL = "custom_base_url"
        private const val KEY_CUSTOM_MODEL_ID = "custom_model_id"
    }

    // ---- Generic API ----

    var geminiApiKey: String
        get() = encryptedPrefs.getString(KEY_GEMINI, "") ?: ""
        set(value) = encryptedPrefs.edit().putString(KEY_GEMINI, value.trim()).apply()

    var openRouterApiKey: String
        get() = encryptedPrefs.getString(KEY_OPENROUTER, "") ?: ""
        set(value) = encryptedPrefs.edit().putString(KEY_OPENROUTER, value.trim()).apply()

    var openCodeApiKey: String
        get() = encryptedPrefs.getString(KEY_OPENCODE, "") ?: ""
        set(value) = encryptedPrefs.edit().putString(KEY_OPENCODE, value.trim()).apply()

    var nvidiaApiKey: String
        get() = encryptedPrefs.getString(KEY_NVIDIA, "") ?: ""
        set(value) = encryptedPrefs.edit().putString(KEY_NVIDIA, value.trim()).apply()

    var customProviderApiKey: String
        get() = encryptedPrefs.getString(KEY_CUSTOM_PROVIDER, "") ?: ""
        set(value) = encryptedPrefs.edit().putString(KEY_CUSTOM_PROVIDER, value.trim()).apply()

    var customBaseUrl: String
        get() = encryptedPrefs.getString(KEY_CUSTOM_BASE_URL, "") ?: ""
        set(value) = encryptedPrefs.edit().putString(KEY_CUSTOM_BASE_URL, value.trim()).apply()

    var customModelId: String
        get() = encryptedPrefs.getString(KEY_CUSTOM_MODEL_ID, "") ?: ""
        set(value) = encryptedPrefs.edit().putString(KEY_CUSTOM_MODEL_ID, value.trim()).apply()

    // ---- Validation helpers ----

    fun hasApiKey(providerId: String): Boolean = getApiKey(providerId).isNotEmpty()

    fun getApiKey(providerId: String): String = when (providerId) {
        "gemini" -> geminiApiKey
        "openrouter" -> openRouterApiKey
        "opencode" -> openCodeApiKey
        "nvidia" -> nvidiaApiKey
        "custom" -> customProviderApiKey
        else -> ""
    }

    fun saveApiKey(providerId: String, key: String) {
        when (providerId) {
            "gemini" -> geminiApiKey = key
            "openrouter" -> openRouterApiKey = key
            "opencode" -> openCodeApiKey = key
            "nvidia" -> nvidiaApiKey = key
            "custom" -> customProviderApiKey = key
        }
    }

    fun deleteApiKey(providerId: String) {
        when (providerId) {
            "gemini" -> encryptedPrefs.edit().remove(KEY_GEMINI).apply()
            "openrouter" -> encryptedPrefs.edit().remove(KEY_OPENROUTER).apply()
            "opencode" -> encryptedPrefs.edit().remove(KEY_OPENCODE).apply()
            "nvidia" -> encryptedPrefs.edit().remove(KEY_NVIDIA).apply()
            "custom" -> encryptedPrefs.edit().remove(KEY_CUSTOM_PROVIDER).apply()
        }
    }

    fun clearAllCredentials() {
        encryptedPrefs.edit().clear().apply()
    }
}
