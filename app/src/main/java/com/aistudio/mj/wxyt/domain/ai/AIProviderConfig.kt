package com.aistudio.mj.wxyt.domain.ai

/**
 * AIProviderConfig — configuration for a single AI provider.
 *
 * Replaces hard-coded provider maps. Each provider's endpoint, auth type,
 * and capabilities are defined here.
 */
data class AIProviderConfig(
    val id: String,
    val displayName: String,
    val type: ProviderType,
    val baseUrl: String,
    val apiKeyProvider: () -> String,
    val enabled: Boolean = true,
    val supportsStreaming: Boolean = true,
    val supportsModelsEndpoint: Boolean = true,
    val supportsChatCompletions: Boolean = true,
    val authType: AuthType = AuthType.BEARER
) {
    enum class ProviderType {
        GEMINI, OPENAI_COMPATIBLE, CUSTOM
    }

    enum class AuthType {
        BEARER,          // Authorization: Bearer <key>
        X_GOOG_API_KEY,  // x-goog-api-key: <key>  (Gemini)
        QUERY_PARAM      // ?key=<key>             (WebSocket only)
    }
}

/**
 * ProviderState — runtime health state for a provider.
 */
data class ProviderState(
    val apiKeyConfigured: Boolean = false,
    val endpointConfigured: Boolean = true,
    val modelConfigured: Boolean = false,
    val enabled: Boolean = true,
    val connectionState: ProviderHealth = ProviderHealth.UNKNOWN,
    val lastError: String? = null,
    val resolvedModel: String? = null,
    val latencyMs: Long? = null
)

enum class ProviderHealth {
    UNKNOWN,
    CHECKING,
    HEALTHY,
    AUTH_ERROR,
    ENDPOINT_ERROR,
    MODEL_ERROR,
    RATE_LIMITED,
    NETWORK_ERROR
}
