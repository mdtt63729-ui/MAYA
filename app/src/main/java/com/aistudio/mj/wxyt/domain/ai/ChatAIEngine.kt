package com.aistudio.mj.wxyt.domain.ai

import com.aistudio.mj.wxyt.domain.security.SecureCredentialRepository
import com.aistudio.mj.wxyt.domain.settings.SettingsRepository

/**
 * Dedicated text-chat routing layer.
 *
 * Gemini is intentionally excluded from this engine. Gemini remains reserved
 * for MAYA's realtime voice path. Text chat can use OpenRouter, OpenCode,
 * NVIDIA NIM, or a custom OpenAI-compatible provider.
 */
class ChatAIEngine(
    private val settingsRepo: SettingsRepository,
    private val secureRepo: SecureCredentialRepository,
    providers: Map<String, AIProvider>
) {
    private val chatProviders = providers.filterKeys { it != "gemini" }

    suspend fun generate(request: AIRequest): AIResponse {
        if (chatProviders.isEmpty()) {
            throw IllegalStateException("No non-Gemini chat provider is available.")
        }

        val settings = settingsRepo.settings.value
        val requestedProvider = settings.chatProvider.takeUnless { it == "gemini" }.orEmpty()
        val preferred = if (requestedProvider == "auto" && settings.adaptiveModelRouting) {
            when {
                looksLikeCode(request.prompt) -> listOf("opencode", "openrouter", "nvidia", "custom")
                else -> listOf("openrouter", "nvidia", "opencode", "custom")
            }
        } else listOf(requestedProvider)
        val primary = preferred.asSequence()
            .mapNotNull { chatProviders[it] }
            .firstOrNull { secureRepo.hasApiKey(it.providerId) }
            ?: chatProviders.values.firstOrNull { secureRepo.hasApiKey(it.providerId) }
            ?: throw IllegalStateException(
                "Configure a non-Gemini Chat API in Settings → AI & Models. Voice continues to use Gemini."
            )

        val model = resolveModel(primary, settings.chatModel)
        val finalRequest = request.copy(model = model, requireAudio = false)

        return try {
            primary.generate(finalRequest)
        } catch (primaryError: Exception) {
            val fallback = chatProviders.values.firstOrNull {
                it.providerId != primary.providerId && secureRepo.hasApiKey(it.providerId)
            } ?: throw primaryError

            fallback.generate(finalRequest.copy(model = resolveModel(fallback, "")))
        }
    }

    suspend fun stream(request: AIRequest): kotlinx.coroutines.flow.Flow<AIStreamChunk> {
        if (chatProviders.isEmpty()) throw IllegalStateException("No non-Gemini chat provider is available.")
        val settings = settingsRepo.settings.value
        val requestedProvider = settings.chatProvider.takeUnless { it == "gemini" }.orEmpty()
        val candidates = if (requestedProvider == "auto" && settings.adaptiveModelRouting) {
            if (looksLikeCode(request.prompt)) listOf("opencode", "openrouter", "nvidia", "custom")
            else listOf("openrouter", "nvidia", "opencode", "custom")
        } else listOf(requestedProvider)
        val primary = candidates.asSequence().mapNotNull { chatProviders[it] }.firstOrNull { secureRepo.hasApiKey(it.providerId) }
            ?: chatProviders.values.firstOrNull { secureRepo.hasApiKey(it.providerId) }
            ?: throw IllegalStateException("Configure a non-Gemini Chat API. Voice remains Gemini-only.")
        return primary.stream(request.copy(model = resolveModel(primary, settings.chatModel), requireAudio = false))
    }

    private fun looksLikeCode(text: String): Boolean {
        val q = text.lowercase()
        return listOf("code", "kotlin", "java", "gradle", "android", "api", "bug", "error", "compile", "কোড", "এরর").count(q::contains) >= 1
    }

    private suspend fun resolveModel(provider: AIProvider, configuredModel: String): String {
        if (configuredModel.isNotBlank()) return configuredModel
        if (provider.providerId == "custom" && secureRepo.customModelId.isNotBlank()) return secureRepo.customModelId

        return when (provider.providerId) {
            // OpenRouter's automatic router is a stable chat fallback and avoids
            // shipping a stale hard-coded model name in the app.
            "openrouter" -> "openrouter/auto"
            else -> {
                if (provider is OpenAICompatibleProvider) {
                    provider.discoverModels().firstOrNull()?.id
                        ?: throw IllegalStateException(
                            "No chat model is configured for ${provider.displayName}. Enter a model ID in Chat AI settings."
                        )
                } else {
                    throw IllegalStateException(
                        "No chat model is configured for ${provider.displayName}."
                    )
                }
            }
        }
    }
}
