package com.aistudio.mj.wxyt.domain.ai

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
 * OpenAICompatibleProvider — handles all OpenAI-compatible providers.
 *
 * Supports: OpenRouter, OpenCode, NVIDIA NIM, Custom providers
 *
 * Features:
 *   - URL normalization via ApiUrlNormalizer
 *   - Model discovery via /models endpoint (if supported)
 *   - True SSE streaming via stream: true
 *   - Configurable authentication (Bearer token by default)
 *   - Capability-based behavior (supportsModelsEndpoint, supportsChatCompletions)
 */
class OpenAICompatibleProvider(
    override val providerId: String,
    override val displayName: String,
    private val baseUrl: String,
    private val apiKeyProvider: () -> String,
    private val client: OkHttpClient,
    private val config: AIProviderConfig? = null
) : AIProvider {

    /**
     * Normalized base URL — always ends with /v1/
     */
    private val normalizedBaseUrl: String = ApiUrlNormalizer.normalizeBaseUrl(baseUrl)

    /**
     * Whether this provider supports /models endpoint.
     */
    private val supportsModels: Boolean = config?.supportsModelsEndpoint ?: true

    /**
     * Whether this provider supports chat completions.
     */
    private val supportsChat: Boolean = config?.supportsChatCompletions ?: true

    /**
     * Authentication type.
     */
    private val authType: AIProviderConfig.AuthType = config?.authType ?: AIProviderConfig.AuthType.BEARER

    override suspend fun testConnection(): ProviderTestResult {
        val apiKey = apiKeyProvider()
        if (apiKey.isEmpty()) return ProviderTestResult.Failure(
            ProviderTestResult.ErrorType.INVALID_KEY,
            message = "API key is not configured"
        )

        // If provider supports /models, use that for connection test
        if (supportsModels) {
            return testModelsEndpoint(apiKey)
        }

        // Otherwise, do a minimal chat completions request
        return testChatEndpoint(apiKey)
    }

    private suspend fun testModelsEndpoint(apiKey: String): ProviderTestResult {
        val url = ApiUrlNormalizer.buildUrl(normalizedBaseUrl, "models")
        val httpRequest = Request.Builder()
            .url(url)
            .apply { applyAuth(apiKey) }
            .get()
            .build()

        return try {
            val response = withContext(Dispatchers.IO) { client.newCall(httpRequest).execute() }
            val responseBody = response.body?.string().orEmpty()

            if (response.isSuccessful) {
                ProviderTestResult.Success
            } else {
                val errorMsg = parseOpenAIError(responseBody, response.code)
                ProviderTestResult.Failure(classifyHttpError(response.code), response.code, errorMsg, responseBody)
            }
        } catch (e: Exception) {
            classifyException(e)
        }
    }

    private suspend fun testChatEndpoint(apiKey: String): ProviderTestResult {
        val jsonBody = JSONObject().apply {
            put("model", "test")
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "Hi")
                })
            })
            put("max_tokens", 5)
        }

        val url = ApiUrlNormalizer.buildUrl(normalizedBaseUrl, "chat/completions")
        val httpRequest = Request.Builder()
            .url(url)
            .apply { applyAuth(apiKey) }
            .header("Content-Type", "application/json")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            val response = withContext(Dispatchers.IO) { client.newCall(httpRequest).execute() }
            val responseBody = response.body?.string().orEmpty()

            if (response.isSuccessful) {
                ProviderTestResult.Success
            } else {
                val errorMsg = parseOpenAIError(responseBody, response.code)
                ProviderTestResult.Failure(classifyHttpError(response.code), response.code, errorMsg, responseBody)
            }
        } catch (e: Exception) {
            classifyException(e)
        }
    }

    /**
     * Discover available models via GET /models.
     */
    suspend fun discoverModels(): List<ModelInfo> {
        val apiKey = apiKeyProvider()
        if (apiKey.isEmpty()) return emptyList()
        if (!supportsModels) return emptyList()

        return try {
            val url = ApiUrlNormalizer.buildUrl(normalizedBaseUrl, "models")
            val request = Request.Builder()
                .url(url)
                .apply { applyAuth(apiKey) }
                .get()
                .build()

            val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
            val body = response.body?.string().orEmpty()

            if (response.isSuccessful && body.isNotEmpty()) {
                val json = JSONObject(body)
                val models = json.optJSONArray("data") ?: json.optJSONArray("models")
                val result = mutableListOf<ModelInfo>()
                if (models != null) {
                    for (i in 0 until models.length()) {
                        val model = models.optJSONObject(i) ?: continue
                        val id = model.optString("id")
                        if (id.isNotEmpty()) {
                            result.add(ModelInfo(
                                id = id,
                                displayName = id,
                                provider = providerId,
                                supportsText = true,
                                supportsStreaming = true
                            ))
                        }
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
        val apiKey = apiKeyProvider()
        if (apiKey.isEmpty()) throw IllegalStateException("$displayName API key is not configured")

        val jsonBody = JSONObject().apply {
            put("model", request.model)
            val messages = JSONArray()
            if (request.systemInstruction.isNotEmpty()) {
                messages.put(JSONObject().apply {
                    put("role", "system")
                    put("content", request.systemInstruction)
                })
            }
            messages.put(JSONObject().apply {
                put("role", "user")
                put("content", request.prompt)
            })
            put("messages", messages)
            put("temperature", request.temperature)
            put("max_tokens", request.maxTokens)
        }

        val url = ApiUrlNormalizer.buildUrl(normalizedBaseUrl, "chat/completions")
        val httpRequest = Request.Builder()
            .url(url)
            .apply { applyAuth(apiKey) }
            .header("Content-Type", "application/json")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = withContext(Dispatchers.IO) { client.newCall(httpRequest).execute() }
        val responseBody = response.body?.string().orEmpty()

        if (response.isSuccessful && responseBody.isNotEmpty()) {
            val json = JSONObject(responseBody)
            val choices = json.optJSONArray("choices")
            val responseText = choices?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")

            if (!responseText.isNullOrEmpty()) {
                return AIResponse(responseText, providerId, request.model)
            } else {
                throw Exception("Failed to parse $displayName response")
            }
        } else {
            val errorMsg = parseOpenAIError(responseBody, response.code)
            throw Exception(errorMsg)
        }
    }

    /**
     * True SSE streaming via stream: true.
     */
    override suspend fun stream(request: AIRequest): Flow<AIStreamChunk> = flow {
        val apiKey = apiKeyProvider()
        if (apiKey.isEmpty()) throw IllegalStateException("$displayName API key is not configured")

        val jsonBody = JSONObject().apply {
            put("model", request.model)
            val messages = JSONArray()
            if (request.systemInstruction.isNotEmpty()) {
                messages.put(JSONObject().apply {
                    put("role", "system")
                    put("content", request.systemInstruction)
                })
            }
            messages.put(JSONObject().apply {
                put("role", "user")
                put("content", request.prompt)
            })
            put("messages", messages)
            put("temperature", request.temperature)
            put("max_tokens", request.maxTokens)
            put("stream", true)  // Enable SSE streaming
        }

        val url = ApiUrlNormalizer.buildUrl(normalizedBaseUrl, "chat/completions")
        val httpRequest = Request.Builder()
            .url(url)
            .apply { applyAuth(apiKey) }
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        try {
            val response = withContext(Dispatchers.IO) { client.newCall(httpRequest).execute() }
            if (!response.isSuccessful) {
                val errorBody = response.body?.string().orEmpty()
                throw Exception(parseOpenAIError(errorBody, response.code))
            }

            val reader = BufferedReader(InputStreamReader(response.body?.byteStream() ?: return@flow))
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                val data = line ?: continue
                // SSE format: "data: {...}"
                if (!data.startsWith("data:")) continue
                val jsonStr = data.removePrefix("data:").trim()

                if (jsonStr == "[DONE]") break

                try {
                    val json = JSONObject(jsonStr)
                    val choices = json.optJSONArray("choices")
                    val delta = choices?.optJSONObject(0)?.optJSONObject("delta")
                    val content = delta?.optString("content")
                    if (!content.isNullOrEmpty()) {
                        emit(AIStreamChunk(content))
                    }
                } catch (e: Exception) {
                    // Skip malformed chunks
                }
            }
            reader.close()
        } catch (e: Exception) {
            if (e is IOException) throw e
            throw e
        }
    }

    // ---- Auth helper ----

    private fun Request.Builder.applyAuth(apiKey: String) {
        when (authType) {
            AIProviderConfig.AuthType.BEARER -> header("Authorization", "Bearer $apiKey")
            AIProviderConfig.AuthType.X_GOOG_API_KEY -> header("x-goog-api-key", apiKey)
            AIProviderConfig.AuthType.QUERY_PARAM -> { /* handled by URL */ }
        }
    }

    // ---- Error handling ----

    private fun parseOpenAIError(responseBody: String, statusCode: Int): String {
        return try {
            val json = JSONObject(responseBody)
            val errorObj = json.optJSONObject("error")
            if (errorObj != null) {
                val message = errorObj.optString("message")
                when (statusCode) {
                    400 -> "Invalid request: $message"
                    401 -> "Authentication failed. The API key is invalid."
                    403 -> "Access forbidden. Check your API key permissions."
                    404 -> "Endpoint or model not found."
                    429 -> "Provider rate limit reached. Please try again shortly."
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
        403 -> ProviderTestResult.ErrorType.FORBIDDEN
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
                "Unable to reach the provider server.",
                e.stackTraceToString()
            )
            is java.net.ConnectException -> ProviderTestResult.Failure(
                ProviderTestResult.ErrorType.NETWORK_ERROR, null,
                "No internet connection.",
                e.stackTraceToString()
            )
            is java.net.SocketTimeoutException -> ProviderTestResult.Failure(
                ProviderTestResult.ErrorType.TIMEOUT, null,
                "Connection timed out.",
                e.stackTraceToString()
            )
            is javax.net.ssl.SSLException -> ProviderTestResult.Failure(
                ProviderTestResult.ErrorType.NETWORK_ERROR, null,
                "Secure connection failed.",
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
