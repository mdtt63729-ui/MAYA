package com.aistudio.mj.wxyt.domain.ai

import com.aistudio.mj.wxyt.domain.security.SecureCredentialRepository
import com.aistudio.mj.wxyt.domain.settings.SettingsRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * AIOrchestrator — orchestrates multi-model synthesis.
 *
 * Removed: hard-coded OpenRouter models (liquid/lfm-2.5-2.6b:free, etc.)
 * Now: uses AIRouter to resolve provider and model from configuration.
 *
 * Synthesis flow:
 *   1. Use the active provider to generate initial response(s)
 *   2. If multiple models are configured, fire parallel requests
 *   3. Synthesize responses into one coherent answer
 *   4. If synthesis fails, return first successful response
 *
 * Fallback:
 *   - Only configured and healthy providers are used for fallback
 *   - Primary provider fails → try fallback provider
 */
class AIOrchestrator(
    private val settingsRepo: SettingsRepository,
    private val secureRepo: SecureCredentialRepository,
    private val providers: Map<String, AIProvider>
) {
    private val router = AIRouter(settingsRepo, secureRepo, providers)

    /**
     * Generate with optional multi-model synthesis.
     * Uses the active provider. If OpenRouter is configured with models,
     * fires parallel requests and synthesizes.
     */
    suspend fun generateWithSynthesis(request: AIRequest): AIResponse = coroutineScope {
        val settings = settingsRepo.settings.value
        val activeProviderId = settings.activeProvider

        // If the active provider is OpenRouter, try multi-model synthesis
        if (activeProviderId == "openrouter" && secureRepo.hasApiKey("openrouter")) {
            try {
                return@coroutineScope multiModelSynthesis(request)
            } catch (e: Exception) {
                // Fall through to single-provider
            }
        }

        // Single-provider generation via router
        router.generate(request)
    }

    /**
     * Multi-model synthesis using OpenRouter.
     * Models are discovered dynamically, not hard-coded.
     */
    private suspend fun multiModelSynthesis(request: AIRequest): AIResponse = coroutineScope {
        val openRouterProvider = providers["openrouter"]
            ?: throw IllegalStateException("OpenRouter provider not found")

        // Discover available models
        val availableModels = if (openRouterProvider is OpenAICompatibleProvider) {
            openRouterProvider.discoverModels()
        } else {
            emptyList()
        }

        // Select up to 3 models for synthesis (dynamic, not hard-coded)
        val synthesisModels = availableModels.take(3).map { it.id }

        if (synthesisModels.isEmpty()) {
            // No models discovered, use single generation with settings model
            val settings = settingsRepo.settings.value
            val modelRequest = request.copy(model = settings.activeModel)
            return@coroutineScope openRouterProvider.generate(modelRequest)
        }

        // Fire parallel requests
        val deferredResponses = synthesisModels.map { modelId ->
            async {
                try {
                    val modelRequest = request.copy(
                        model = modelId,
                        systemInstruction = "You are MAYA, a helpful AI assistant and warm adult female companion."
                    )
                    val response = openRouterProvider.generate(modelRequest)
                    Result.success(response)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }

        val results = deferredResponses.awaitAll()
        val successfulResponses = results.mapNotNull { it.getOrNull() }

        if (successfulResponses.isEmpty()) {
            val firstError = results.firstNotNullOfOrNull { it.exceptionOrNull() }
            throw firstError ?: Exception("All models failed to respond")
        }

        // If only one response, return it directly
        if (successfulResponses.size == 1) {
            return@coroutineScope successfulResponses.first()
        }

        // Hidden Synthesis using the active provider
        val activeProviderId = settingsRepo.settings.value.activeProvider
        val synthesisProvider = providers[activeProviderId] ?: openRouterProvider

        val synthesisPrompt = buildSynthesisPrompt(request.prompt, successfulResponses)

        val synthesisInstruction = "You are MAYA, an AI assistant and warm adult female companion. You will be provided with several answers to a user query. Compare them, identify correct information, remove contradictions/duplicates, and combine useful details into ONE coherent final answer. Do NOT mention the existence of multiple models, summaries, managers, or synthesizers. Answer directly to the user as MAYA."

        val synthesisModel = if (synthesisProvider.providerId == "openrouter") {
            synthesisModels.firstOrNull() ?: ""
        } else {
            request.model
        }

        val synthesisRequest = AIRequest(
            prompt = synthesisPrompt,
            model = synthesisModel,
            systemInstruction = synthesisInstruction,
            requireAudio = request.requireAudio
        )

        try {
            val finalResponse = synthesisProvider.generate(synthesisRequest)
            AIResponse(
                text = finalResponse.text,
                providerId = "orchestrator",
                modelId = "synthesis"
            )
        } catch (e: Exception) {
            // If synthesis fails, return the first successful response
            successfulResponses.first()
        }
    }

    private fun buildSynthesisPrompt(originalPrompt: String, responses: List<AIResponse>): String {
        val sb = StringBuilder()
        sb.append("User Query: $originalPrompt\n\nModel Responses:\n")
        responses.forEachIndexed { index, response ->
            sb.append("---\nResponse ${index + 1}:\n${response.text}\n")
        }
        return sb.toString()
    }

    /**
     * Single-provider generation (no synthesis).
     */
    suspend fun generateSingle(request: AIRequest): AIResponse {
        return router.generate(request)
    }
}
