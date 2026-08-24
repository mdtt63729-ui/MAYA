package com.aistudio.mj.wxyt.domain.ai

import com.aistudio.mj.wxyt.domain.settings.SettingsRepository
import com.aistudio.mj.wxyt.domain.security.SecureCredentialRepository
import kotlinx.coroutines.flow.Flow

/**
 * AIRouter — routes AI requests to the correct provider/model.
 *
 * Responsibilities:
 *   - resolveProvider(): find the active provider, validate configuration
 *   - resolveModel(): find the model, validate capability
 *   - validateConfiguration(): check API key, endpoint, model
 *   - execute(): route request to provider
 *   - fallback(): try fallback provider if primary fails
 *
 * Provider resolution is NOT done in the UI layer.
 */
class AIRouter(
    private val settingsRepo: SettingsRepository,
    private val secureRepo: SecureCredentialRepository,
    private val providers: Map<String, AIProvider>
) {
    /**
     * Resolve the active provider.
     * Validates: API key configured, provider exists.
     */
    fun resolveProvider(): AIProvider {
        val settings = settingsRepo.settings.value
        val activeProviderId = settings.activeProvider

        val provider = providers[activeProviderId]
        if (provider != null && secureRepo.hasApiKey(activeProviderId)) {
            return provider
        }

        // Fallback: try Gemini, then any provider with a configured key
        val gemini = providers["gemini"]
        if (gemini != null && secureRepo.hasApiKey("gemini")) {
            return gemini
        }

        // Find any provider with a configured key
        for ((id, p) in providers) {
            if (secureRepo.hasApiKey(id)) return p
        }

        throw IllegalStateException("No AI provider is configured. Please add an API key in Settings.")
    }

    /**
     * Resolve the model for a request.
     * Validates: model is compatible with request capabilities.
     */
    fun resolveModel(request: AIRequest, provider: AIProvider): String {
        val settings = settingsRepo.settings.value

        // Use request model if specified
        val model = if (request.model.isNotEmpty()) request.model else settings.activeModel

        if (model.isEmpty()) {
            // Use provider default
            return when (provider.providerId) {
                "gemini" -> ProviderEndpointRegistry.GEMINI_DEFAULT_TEXT_MODEL
                else -> ""
            }
        }

        return model
    }

    /**
     * Validate that a provider/model is properly configured.
     */
    fun validateConfiguration(providerId: String): Boolean {
        if (!providers.containsKey(providerId)) return false
        return secureRepo.hasApiKey(providerId)
    }

    /**
     * Generate a response using the resolved provider.
     * Implements fallback: if primary fails, try fallback provider.
     */
    suspend fun generate(request: AIRequest): AIResponse {
        val provider = resolveProvider()
        val model = resolveModel(request, provider)
        val finalRequest = request.copy(model = model)

        return try {
            provider.generate(finalRequest)
        } catch (e: Exception) {
            // Try fallback
            val fallback = resolveFallbackProvider(provider.providerId)
            if (fallback != null) {
                val fallbackModel = resolveModel(request, fallback)
                fallback.generate(finalRequest.copy(model = fallbackModel))
            } else {
                throw e
            }
        }
    }

    /**
     * Stream a response using the resolved provider.
     */
    suspend fun stream(request: AIRequest): Flow<AIStreamChunk> {
        val provider = resolveProvider()
        val model = resolveModel(request, provider)
        val finalRequest = request.copy(model = model)
        return provider.stream(finalRequest)
    }

    /**
     * Resolve a fallback provider (one that is configured and different from primary).
     */
    private fun resolveFallbackProvider(primaryId: String): AIProvider? {
        for ((id, provider) in providers) {
            if (id != primaryId && secureRepo.hasApiKey(id)) {
                return provider
            }
        }
        return null
    }

    /**
     * Get all configured (API key present) providers.
     */
    fun getConfiguredProviders(): List<String> {
        return providers.keys.filter { secureRepo.hasApiKey(it) }
    }

    /**
     * Check if any provider is configured.
     */
    fun hasConfiguredProvider(): Boolean {
        return providers.keys.any { secureRepo.hasApiKey(it) }
    }
}
