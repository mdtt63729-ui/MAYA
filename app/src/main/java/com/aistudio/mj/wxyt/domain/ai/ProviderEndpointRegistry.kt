package com.aistudio.mj.wxyt.domain.ai

/**
 * ProviderEndpointRegistry — centralized endpoint configuration for all AI providers.
 * No endpoint string should be hard-coded inside individual provider classes.
 *
 * Gemini:
 *   REST endpoint:    https://generativelanguage.googleapis.com/v1beta
 *   Models endpoint:  https://generativelanguage.googleapis.com/v1beta/models
 *   Live endpoint:    wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent
 *
 * OpenRouter:
 *   Base URL:    https://openrouter.ai/api/v1
 *   Models:      /models
 *   Chat:        /chat/completions
 *
 * OpenCode:
 *   Base URL:    https://api.opencode.com/v1
 *   Models:      /models
 *   Chat:        /chat/completions
 *
 * NVIDIA NIM:
 *   Base URL:    https://integrate.api.nvidia.com/v1
 *   Models:      /models
 *   Chat:        /chat/completions
 *
 * Custom:
 *   User-configurable base URL
 */
object ProviderEndpointRegistry {

    // ---- Gemini ----
    const val GEMINI_API_BASE = "https://generativelanguage.googleapis.com/v1beta"
    const val GEMINI_MODELS_ENDPOINT = "$GEMINI_API_BASE/models"
    const val GEMINI_LIVE_ENDPOINT = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
    const val GEMINI_API_VERSION = "v1beta"

    // Fallback / default models (used when model discovery fails)
    // gemini-flash-latest is Google's auto-updating alias that always points to the
    // current GA Flash model (currently gemini-3.5-flash). Works on free tier.
    // gemini-2.0-flash is shut down (June 2026).
    const val GEMINI_DEFAULT_TEXT_MODEL = "gemini-flash-latest"
    const val GEMINI_DEFAULT_LIVE_MODEL = "gemini-3.1-flash-live-preview"
    const val GEMINI_FALLBACK_LIVE_MODEL = "gemini-2.5-flash-native-audio-preview-12-2025"

    // ---- OpenRouter ----
    const val OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1"

    // ---- OpenCode ----
    const val OPENCODE_BASE_URL = "https://api.opencode.com/v1"

    // ---- NVIDIA NIM ----
    const val NVIDIA_BASE_URL = "https://integrate.api.nvidia.com/v1"

    // ---- Custom (default fallback) ----
    const val CUSTOM_DEFAULT_BASE_URL = "https://api.openai.com/v1"

    /**
     * Build the Gemini generateContent URL for a specific model.
     * Used for REST text/multimodal generation.
     */
    fun geminiGenerateContentUrl(model: String): String {
        return "$GEMINI_API_BASE/models/$model:generateContent"
    }

    /**
     * Build the Gemini streamGenerateContent URL for a specific model.
     * Used for SSE streaming.
     */
    fun geminiStreamGenerateContentUrl(model: String): String {
        return "$GEMINI_API_BASE/models/$model:streamGenerateContent?alt=sse"
    }

    /**
     * Build the Gemini models list URL for model discovery.
     */
    fun geminiModelsUrl(): String {
        return GEMINI_MODELS_ENDPOINT
    }

    /**
     * Build the Gemini Live WebSocket URL with API key as parameter.
     * Note: Live API requires API key as query parameter (WebSocket limitation).
     */
    fun geminiLiveUrl(apiKey: String): String {
        return "$GEMINI_LIVE_ENDPOINT?key=$apiKey"
    }

    /**
     * Get the predefined base URL for a provider by its ID.
     */
    fun getBaseUrl(providerId: String): String = when (providerId) {
        "gemini" -> GEMINI_API_BASE
        "openrouter" -> OPENROUTER_BASE_URL
        "opencode" -> OPENCODE_BASE_URL
        "nvidia" -> NVIDIA_BASE_URL
        "custom" -> CUSTOM_DEFAULT_BASE_URL
        else -> CUSTOM_DEFAULT_BASE_URL
    }
}
