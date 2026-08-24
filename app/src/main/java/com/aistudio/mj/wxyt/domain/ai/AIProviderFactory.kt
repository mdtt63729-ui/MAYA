package com.aistudio.mj.wxyt.domain.ai

import android.content.Context
import com.aistudio.mj.wxyt.domain.security.SecureCredentialRepository
import com.aistudio.mj.wxyt.domain.settings.SettingsRepository

/**
 * AIProviderFactory — centralized creation of AI providers.
 *
 * Replaces hard-coded provider construction in ChatViewModel/SettingsViewModel.
 * All provider configuration is defined here using AIProviderConfig and
 * ProviderEndpointRegistry.
 *
 * Usage:
 *   val factory = AIProviderFactory(context)
 *   val gemini = factory.createGemini()
 *   val allProviders = factory.createAll()
 */
class AIProviderFactory(
    private val context: Context
) {
    private val secureRepo: SecureCredentialRepository = SecureCredentialRepository(context)
    private val settingsRepo: SettingsRepository = SettingsRepository.get(context)
    private val client: okhttp3.OkHttpClient = ApiClientProvider.client

    // ---- Provider configs ----

    val geminiConfig = AIProviderConfig(
        id = "gemini",
        displayName = "Google Gemini",
        type = AIProviderConfig.ProviderType.GEMINI,
        baseUrl = ProviderEndpointRegistry.GEMINI_API_BASE,
        apiKeyProvider = { secureRepo.geminiApiKey },
        supportsStreaming = true,
        supportsModelsEndpoint = true,
        supportsChatCompletions = false,
        authType = AIProviderConfig.AuthType.X_GOOG_API_KEY
    )

    val openRouterConfig = AIProviderConfig(
        id = "openrouter",
        displayName = "OpenRouter",
        type = AIProviderConfig.ProviderType.OPENAI_COMPATIBLE,
        baseUrl = ProviderEndpointRegistry.OPENROUTER_BASE_URL,
        apiKeyProvider = { secureRepo.openRouterApiKey },
        supportsStreaming = true,
        supportsModelsEndpoint = true,
        supportsChatCompletions = true,
        authType = AIProviderConfig.AuthType.BEARER
    )

    val openCodeConfig = AIProviderConfig(
        id = "opencode",
        displayName = "OpenCode",
        type = AIProviderConfig.ProviderType.OPENAI_COMPATIBLE,
        baseUrl = ProviderEndpointRegistry.OPENCODE_BASE_URL,
        apiKeyProvider = { secureRepo.openCodeApiKey },
        supportsStreaming = true,
        supportsModelsEndpoint = true,
        supportsChatCompletions = true,
        authType = AIProviderConfig.AuthType.BEARER
    )

    val nvidiaConfig = AIProviderConfig(
        id = "nvidia",
        displayName = "NVIDIA NIM",
        type = AIProviderConfig.ProviderType.OPENAI_COMPATIBLE,
        baseUrl = ProviderEndpointRegistry.NVIDIA_BASE_URL,
        apiKeyProvider = { secureRepo.nvidiaApiKey },
        supportsStreaming = true,
        supportsModelsEndpoint = true,
        supportsChatCompletions = true,
        authType = AIProviderConfig.AuthType.BEARER
    )

    val customConfig = AIProviderConfig(
        id = "custom",
        displayName = "Custom Provider",
        type = AIProviderConfig.ProviderType.CUSTOM,
        baseUrl = if (secureRepo.customBaseUrl.isNotEmpty()) secureRepo.customBaseUrl else ProviderEndpointRegistry.CUSTOM_DEFAULT_BASE_URL,
        apiKeyProvider = { secureRepo.customProviderApiKey },
        supportsStreaming = true,
        supportsModelsEndpoint = true,
        supportsChatCompletions = true,
        authType = AIProviderConfig.AuthType.BEARER
    )

    // ---- Factory methods ----

    fun createGemini(): GeminiProvider = GeminiProvider(secureRepo, client, settingsRepo)

    fun createOpenRouter(): OpenAICompatibleProvider = OpenAICompatibleProvider(
        providerId = "openrouter",
        displayName = "OpenRouter",
        baseUrl = ProviderEndpointRegistry.OPENROUTER_BASE_URL,
        apiKeyProvider = { secureRepo.openRouterApiKey },
        client = client,
        config = openRouterConfig
    )

    fun createOpenCode(): OpenAICompatibleProvider = OpenAICompatibleProvider(
        providerId = "opencode",
        displayName = "OpenCode",
        baseUrl = ProviderEndpointRegistry.OPENCODE_BASE_URL,
        apiKeyProvider = { secureRepo.openCodeApiKey },
        client = client,
        config = openCodeConfig
    )

    fun createNvidia(): OpenAICompatibleProvider = OpenAICompatibleProvider(
        providerId = "nvidia",
        displayName = "NVIDIA NIM",
        baseUrl = ProviderEndpointRegistry.NVIDIA_BASE_URL,
        apiKeyProvider = { secureRepo.nvidiaApiKey },
        client = client,
        config = nvidiaConfig
    )

    fun createCustom(): OpenAICompatibleProvider = OpenAICompatibleProvider(
        providerId = "custom",
        displayName = "Custom Provider",
        baseUrl = if (secureRepo.customBaseUrl.isNotEmpty()) secureRepo.customBaseUrl else ProviderEndpointRegistry.CUSTOM_DEFAULT_BASE_URL,
        apiKeyProvider = { secureRepo.customProviderApiKey },
        client = client,
        config = customConfig
    )

    /**
     * Create all providers as a map.
     */
    fun createAll(): Map<String, AIProvider> = mapOf(
        "gemini" to createGemini(),
        "openrouter" to createOpenRouter(),
        "opencode" to createOpenCode(),
        "nvidia" to createNvidia(),
        "custom" to createCustom()
    )

    /**
     * Create a single provider by ID.
     */
    fun createProvider(providerId: String): AIProvider? = when (providerId) {
        "gemini" -> createGemini()
        "openrouter" -> createOpenRouter()
        "opencode" -> createOpenCode()
        "nvidia" -> createNvidia()
        "custom" -> createCustom()
        else -> null
    }

    /**
     * Get all provider configs.
     */
    fun getAllConfigs(): List<AIProviderConfig> = listOf(
        geminiConfig, openRouterConfig, openCodeConfig, nvidiaConfig, customConfig
    )

    /**
     * Expose secureRepo and settingsRepo for consumers that need them.
     */
    fun getSecureRepo(): SecureCredentialRepository = secureRepo
    fun getSettingsRepo(): SettingsRepository = settingsRepo
    fun getClient(): okhttp3.OkHttpClient = client
}
