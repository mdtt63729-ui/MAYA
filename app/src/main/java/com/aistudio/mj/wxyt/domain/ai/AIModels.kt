package com.aistudio.mj.wxyt.domain.ai

import kotlinx.coroutines.flow.Flow

data class AIRequest(
    val prompt: String,
    val context: String = "",
    val model: String = "",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 4096,
    val systemInstruction: String = "",
    val requireAudio: Boolean = false
)

data class AIResponse(
    val text: String,
    val providerId: String,
    val modelId: String,
    val audioData: ByteArray? = null
)

data class AIStreamChunk(
    val text: String
)

sealed class ProviderTestResult {
    object Success : ProviderTestResult()
    data class Failure(
        val errorType: ErrorType,
        val statusCode: Int? = null,
        val message: String,
        val rawError: String? = null
    ) : ProviderTestResult()

    enum class ErrorType {
        INVALID_KEY,
        UNAUTHORIZED,
        FORBIDDEN,
        RATE_LIMITED,
        NETWORK_ERROR,
        TIMEOUT,
        SERVER_ERROR,
        MODEL_NOT_FOUND,
        INVALID_ENDPOINT,
        UNKNOWN_ERROR
    }
}

interface AIProvider {
    val providerId: String
    val displayName: String

    suspend fun testConnection(): ProviderTestResult

    suspend fun generate(request: AIRequest): AIResponse

    suspend fun stream(request: AIRequest): Flow<AIStreamChunk>
}
