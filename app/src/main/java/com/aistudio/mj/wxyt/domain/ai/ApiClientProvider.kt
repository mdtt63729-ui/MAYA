package com.aistudio.mj.wxyt.domain.ai

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * ApiClientProvider — shared OkHttpClient instance with proper configuration.
 *
 * Prevents creating a new OkHttpClient() per request (which causes thread pool
 * exhaustion and connection leaks).
 *
 * Configuration:
 *   - connectTimeout: 15s
 *   - readTimeout: 60s (for AI generation which can be slow)
 *   - writeTimeout: 15s
 *   - callTimeout: 90s (total request timeout)
 *   - retryOnConnectionFailure: true
 */
object ApiClientProvider {

    @Volatile
    private var clientInstance: OkHttpClient? = null

    /**
     * Shared REST client for all providers.
     * Used for generateContent, /models, /chat/completions, etc.
     */
    val client: OkHttpClient
        get() {
            return clientInstance ?: synchronized(this) {
                clientInstance ?: createClient().also { clientInstance = it }
            }
        }

    /**
     * Client optimized for WebSocket connections (Gemini Live).
     * No read timeout (long-lived connection).
     */
    val webSocketClient: OkHttpClient
        get() {
            return OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)  // No timeout for WebSocket
                .pingInterval(15, TimeUnit.SECONDS)
                .build()
        }

    private fun createClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
