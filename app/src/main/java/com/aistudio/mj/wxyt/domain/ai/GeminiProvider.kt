package com.aistudio.mj.wxyt.domain.ai

import com.aistudio.mj.wxyt.domain.security.SecureCredentialRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

/**
 * GeminiProvider — REST text/multimodal generation via Gemini API.
 *
 * Authentication: x-goog-api-key header (NOT URL query parameter)
 * Endpoint: centralized in ProviderEndpointRegistry
 * Model: resolved from request, settings, or model discovery
 *
 * Separation of concerns:
 *   - GeminiProvider handles REST generateContent + streamGenerateContent
 *   - GeminiLiveRepository handles Live WebSocket (separate class)
 *
 * Model discovery:
 *   - testConnection() calls /v1beta/models to discover available models
 *   - Falls back to GEMINI_DEFAULT_TEXT_MODEL if discovery fails
 *
 * Streaming:
 *   - Uses streamGenerateContent endpoint with SSE parsing
 */
class GeminiProvider(
    private val secureRepo: SecureCredentialRepository,
    private val client: OkHttpClient,
    private val settingsRepo: com.aistudio.mj.wxyt.domain.settings.SettingsRepository? = null
) : AIProvider {
    override val providerId: String = "gemini"
    override val displayName: String = "Google Gemini"

    /**
     * Resolve the model to use for a request.
     * Priority: request.model > settings.activeModel > default
     */
    private fun resolveModel(request: AIRequest): String {
        if (request.model.isNotEmpty()) return request.model
        val settingsModel = settingsRepo?.settings?.value?.activeModel
        if (!settingsModel.isNullOrEmpty()) {
            return settingsModel
        }
        return ProviderEndpointRegistry.GEMINI_DEFAULT_TEXT_MODEL
    }

    override suspend fun testConnection(): ProviderTestResult {
        val apiKey = secureRepo.geminiApiKey
        if (apiKey.isEmpty()) return ProviderTestResult.Failure(
            ProviderTestResult.ErrorType.INVALID_KEY,
            message = "API key is not configured"
        )

        // Step 1: Try model discovery
        val discoveredModel = tryDiscoverModels(apiKey)

        // Step 2: Build list of models to try (discovered + all free-tier fallbacks)
        val modelsToTry = mutableListOf<String>()
        if (discoveredModel != null) {
            modelsToTry.add(discoveredModel)
        }
        // Add all free-tier models as fallback (in priority order)
        val freeTierModels = listOf(
            "gemini-flash-latest",
            "gemini-2.5-flash",
            "gemini-2.5-flash-lite",
            "gemini-3.0-flash-preview",
            "gemini-3.1-flash-lite-preview",
            "gemini-3.5-flash",
            "gemini-3.6-flash"
        )
        for (m in freeTierModels) {
            if (!modelsToTry.contains(m)) {
                modelsToTry.add(m)
            }
        }

        // Step 3: Try each model with a minimal generateContent request
        // Stop at the first one that works
        var lastError: ProviderTestResult.Failure? = null

        for (testModel in modelsToTry) {
            val jsonBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", "Hi") })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("maxOutputTokens", 10)
                })
            }

            val httpRequest = Request.Builder()
                .url(ProviderEndpointRegistry.geminiGenerateContentUrl(testModel))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            try {
                val response = withContext(Dispatchers.IO) { client.newCall(httpRequest).execute() }
                val responseBody = response.body?.string().orEmpty()

                if (response.isSuccessful) {
                    // Success! Save this model as the active model
                    settingsRepo?.let { repo ->
                        val current = repo.settings.value
                        if (current.activeModel.isEmpty() || current.activeModel != testModel) {
                            repo.updateSettings(current.copy(activeModel = testModel))
                        }
                    }
                    return ProviderTestResult.Success
                } else {
                    val errorMsg = parseGeminiError(responseBody, response.code)
                    val errorType = classifyHttpError(response.code)
                    lastError = ProviderTestResult.Failure(errorType, response.code, errorMsg, responseBody)
                    // Try next model
                }
            } catch (e: Exception) {
                lastError = when (val result = classifyException(e)) {
                    is ProviderTestResult.Failure -> result
                    else -> null
                }
                // Try next model
            }
        }

        // All models failed
        return lastError ?: ProviderTestResult.Failure(
            ProviderTestResult.ErrorType.MODEL_NOT_FOUND,
            message = "No available Gemini models found for this API key. Tried: ${modelsToTry.joinToString()}"
        )
    }

    /**
     * Discover available Gemini models via GET /v1beta/models.
     * Returns the first text-capable model, or null on failure.
     */
    private suspend fun tryDiscoverModels(apiKey: String): String? {
        return try {
            val request = Request.Builder()
                .url(ProviderEndpointRegistry.geminiModelsUrl())
                .header("x-goog-api-key", apiKey)
                .get()
                .build()

            val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
            val body = response.body?.string().orEmpty()

            if (response.isSuccessful && body.isNotEmpty()) {
                val json = JSONObject(body)
                val models = json.optJSONArray("models")
                if (models != null && models.length() > 0) {
                    // Collect all model IDs that support generateContent
                    val availableModels = mutableSetOf<String>()
                    for (i in 0 until models.length()) {
                        val model = models.optJSONObject(i) ?: continue
                        val name = model.optString("name")
                        val modelId = name.removePrefix("models/")
                        val supportedMethods = model.optJSONArray("supportedGenerationMethods")
                        if (supportedMethods != null) {
                            for (j in 0 until supportedMethods.length()) {
                                if (supportedMethods.optString(j) == "generateContent") {
                                    availableModels.add(modelId)
                                    break
                                }
                            }
                        }
                    }

                    // Try preferred free-tier models in priority order
                    // gemini-flash-latest always points to the current GA Flash model
                    val preferredModels = listOf(
                        "gemini-flash-latest",
                        "gemini-2.5-flash",
                        "gemini-2.5-flash-lite",
                        "gemini-3.0-flash-preview",
                        "gemini-3.1-flash-lite-preview",
                        "gemini-3.5-flash",
                        "gemini-3.6-flash"
                    )
                    for (preferred in preferredModels) {
                        if (availableModels.contains(preferred)) {
                            return preferred
                        }
                    }

                    // Fallback: return first available model that supports generateContent
                    // and looks like a text model (skip embedding, image, tts models)
                    for (modelId in availableModels) {
                        if (!modelId.contains("embedding") &&
                            !modelId.contains("image") &&
                            !modelId.contains("tts") &&
                            !modelId.contains("aqa")) {
                            return modelId
                        }
                    }

                    // Last resort: any model with generateContent
                    if (availableModels.isNotEmpty()) {
                        return availableModels.first()
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Fetch all available Gemini models as ModelInfo list.
     */
    suspend fun discoverModels(): List<ModelInfo> {
        val apiKey = secureRepo.geminiApiKey
        if (apiKey.isEmpty()) return emptyList()

        return try {
            val request = Request.Builder()
                .url(ProviderEndpointRegistry.geminiModelsUrl())
                .header("x-goog-api-key", apiKey)
                .get()
                .build()

            val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
            val body = response.body?.string().orEmpty()

            if (response.isSuccessful && body.isNotEmpty()) {
                val json = JSONObject(body)
                val models = json.optJSONArray("models")
                val result = mutableListOf<ModelInfo>()
                if (models != null) {
                    for (i in 0 until models.length()) {
                        val model = models.optJSONObject(i) ?: continue
                        val name = model.optString("name").removePrefix("models/")
                        val displayName = model.optString("displayName", name)
                        val methods = model.optJSONArray("supportedGenerationMethods")
                        val supportsText = methods?.toString()?.contains("generateContent") == true
                        val supportsStreaming = methods?.toString()?.contains("streamGenerateContent") == true
                        val supportsLive = name.contains("live")
                        result.add(ModelInfo(
                            id = name,
                            displayName = displayName,
                            provider = "gemini",
                            supportsText = supportsText,
                            supportsStreaming = supportsStreaming,
                            supportsLive = supportsLive,
                            supportsAudioOutput = name.contains("live") || name.contains("audio"),
                            supportsTools = supportsText
                        ))
                    }
                }
                result
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun generate(request: AIRequest): AIResponse {
        val apiKey = secureRepo.geminiApiKey
        if (apiKey.isEmpty()) throw IllegalStateException("Gemini API key is not configured")

        val model = resolveModel(request)

        val jsonBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", request.prompt) })
                    })
                })
            })
            if (request.systemInstruction.isNotEmpty()) {
                put("systemInstruction", JSONObject().apply {
                    put("role", "system")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", request.systemInstruction) })
                    })
                })
            }
            val genConfig = JSONObject().apply {
                put("temperature", request.temperature)
                put("maxOutputTokens", request.maxTokens)
            }
            put("generationConfig", genConfig)
        }

        val httpRequest = Request.Builder()
            .url(ProviderEndpointRegistry.geminiGenerateContentUrl(model))
            .header("Content-Type", "application/json")
            .header("x-goog-api-key", apiKey)  // Header-based auth
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = withContext(Dispatchers.IO) { client.newCall(httpRequest).execute() }
        val responseBody = response.body?.string().orEmpty()

        if (response.isSuccessful && responseBody.isNotEmpty()) {
            val json = JSONObject(responseBody)
            val candidates = json.optJSONArray("candidates")
            val parts = candidates?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")

            var responseText = ""
            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val part = parts.optJSONObject(i)
                    if (part?.has("text") == true) {
                        responseText += part.optString("text")
                    }
                }
            }

            if (responseText.isNotEmpty()) {
                return AIResponse(responseText, providerId, model)
            } else {
                throw Exception("Failed to parse Gemini response")
            }
        } else {
            val errorMsg = parseGeminiError(responseBody, response.code)
            throw Exception(errorMsg)
        }
    }

    /**
     * True SSE streaming via streamGenerateContent endpoint.
     */
    override suspend fun stream(request: AIRequest): Flow<AIStreamChunk> = flow {
        val apiKey = secureRepo.geminiApiKey
        if (apiKey.isEmpty()) throw IllegalStateException("Gemini API key is not configured")

        val model = resolveModel(request)

        val jsonBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", request.prompt) })
                    })
                })
            })
            if (request.systemInstruction.isNotEmpty()) {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", request.systemInstruction) })
                    })
                })
            }
            put("generationConfig", JSONObject().apply {
                put("temperature", request.temperature)
                put("maxOutputTokens", request.maxTokens)
            })
        }

        val httpRequest = Request.Builder()
            .url(ProviderEndpointRegistry.geminiStreamGenerateContentUrl(model))
            .header("Content-Type", "application/json")
            .header("x-goog-api-key", apiKey)
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        try {
            val response = withContext(Dispatchers.IO) { client.newCall(httpRequest).execute() }
            if (!response.isSuccessful) {
                val errorBody = response.body?.string().orEmpty()
                throw Exception(parseGeminiError(errorBody, response.code))
            }

            // Gemini SSE emits one JSON object per `data:` event.
            response.body?.byteStream()?.use { input ->
                BufferedReader(InputStreamReader(input)).use { reader ->
                    while (true) {
                        val rawLine = reader.readLine() ?: break
                        val line = rawLine.trim()
                        if (line.isEmpty() || line.startsWith(":")) continue

                        val payload = when {
                            line.startsWith("data:") -> line.removePrefix("data:").trim()
                            line.startsWith("{") -> line
                            else -> continue
                        }
                        if (payload.isEmpty() || payload == "[DONE]") continue

                        try {
                            val chunk = JSONObject(payload)
                            val candidates = chunk.optJSONArray("candidates")
                            val parts = candidates?.optJSONObject(0)
                                ?.optJSONObject("content")
                                ?.optJSONArray("parts")
                            if (parts != null) {
                                for (j in 0 until parts.length()) {
                                    val text = parts.optJSONObject(j)?.optString("text")
                                    if (!text.isNullOrEmpty()) emit(AIStreamChunk(text))
                                }
                            }
                        } catch (_: Exception) {
                            // Ignore malformed events and continue reading the stream.
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (e is IOException) throw e
            throw e
        }
    }

    // ---- Error handling helpers ----

    private fun parseGeminiError(responseBody: String, statusCode: Int): String {
        return try {
            val json = JSONObject(responseBody)
            val errorObj = json.optJSONObject("error")
            if (errorObj != null) {
                val message = errorObj.optString("message")
                when (statusCode) {
                    400 -> "Invalid request: $message"
                    401 -> "Authentication failed. The API key is invalid."
                    403 -> "Access forbidden. The API key may not have permission for this model."
                    404 -> "Model not found. The selected model may be unavailable for this API key."
                    429 -> "Rate limit reached. Please try again shortly."
                    in 500..599 -> "Server error: $message"
                    else -> message.ifEmpty { "HTTP $statusCode" }
                }
            } else {
                "HTTP $statusCode"
            }
        } catch (e: Exception) {
            "HTTP $statusCode"
        }
    }

    private fun classifyHttpError(code: Int): ProviderTestResult.ErrorType = when (code) {
        400 -> ProviderTestResult.ErrorType.INVALID_ENDPOINT
        401 -> ProviderTestResult.ErrorType.UNAUTHORIZED
        403 -> ProviderTestResult.ErrorType.INVALID_KEY
        404 -> ProviderTestResult.ErrorType.MODEL_NOT_FOUND
        408 -> ProviderTestResult.ErrorType.TIMEOUT
        429 -> ProviderTestResult.ErrorType.RATE_LIMITED
        in 500..599 -> ProviderTestResult.ErrorType.SERVER_ERROR
        else -> ProviderTestResult.ErrorType.UNKNOWN_ERROR
    }

    private fun classifyException(e: Exception): ProviderTestResult {
        return when (e) {
            is java.net.UnknownHostException -> ProviderTestResult.Failure(
                ProviderTestResult.ErrorType.NETWORK_ERROR, null,
                "Unable to reach Google's server. Check your internet connection.",
                e.stackTraceToString()
            )
            is java.net.ConnectException -> ProviderTestResult.Failure(
                ProviderTestResult.ErrorType.NETWORK_ERROR, null,
                "No internet connection. Please check your network and try again.",
                e.stackTraceToString()
            )
            is java.net.SocketTimeoutException -> ProviderTestResult.Failure(
                ProviderTestResult.ErrorType.TIMEOUT, null,
                "Connection timed out while contacting Gemini.",
                e.stackTraceToString()
            )
            is javax.net.ssl.SSLException -> ProviderTestResult.Failure(
                ProviderTestResult.ErrorType.NETWORK_ERROR, null,
                "A secure connection could not be established.",
                e.stackTraceToString()
            )
            is IOException -> ProviderTestResult.Failure(
                ProviderTestResult.ErrorType.NETWORK_ERROR, null,
                "Network error: ${e.message ?: "Unknown"}",
                e.stackTraceToString()
            )
            else -> ProviderTestResult.Failure(
                ProviderTestResult.ErrorType.UNKNOWN_ERROR, null,
                "Unknown error: ${e.message ?: ""}",
                e.stackTraceToString()
            )
        }
    }
}
