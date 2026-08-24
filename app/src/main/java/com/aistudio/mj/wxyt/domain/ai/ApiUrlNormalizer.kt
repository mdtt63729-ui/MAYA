package com.aistudio.mj.wxyt.domain.ai

/**
 * ApiUrlNormalizer — utility for normalizing provider base URLs.
 *
 * Handles all variations of trailing/leading slashes, missing "v1" path, etc.
 *
 * Examples:
 *   "https://example.com"       → "https://example.com/v1/"
 *   "https://example.com/"      → "https://example.com/v1/"
 *   "https://example.com/v1"    → "https://example.com/v1/"
 *   "https://example.com/v1/"   → "https://example.com/v1/"
 */
object ApiUrlNormalizer {

    /**
     * Normalize a base URL to always end with a single trailing slash
     * and include the /v1 path if it looks like an OpenAI-compatible API.
     */
    fun normalizeBaseUrl(url: String): String {
        if (url.isBlank()) return ""

        var normalized = url.trim()

        // Remove trailing slashes
        while (normalized.endsWith("/")) {
            normalized = normalized.dropLast(1)
        }

        // If the URL doesn't contain a path segment like /v1, append it
        // (only for OpenAI-compatible providers; Gemini handles its own URL construction)
        if (!normalized.endsWith("/v1") && !normalized.endsWith("/v2")) {
            // Check if this looks like a base API URL (no specific endpoint path)
            val pathPart = normalized.substringAfter("://", "").substringAfter("/", "")
            if (pathPart.isEmpty() || !pathPart.contains("/")) {
                normalized = "$normalized/v1"
            }
        }

        return "$normalized/"
    }

    /**
     * Build a full URL by appending a path to a normalized base URL.
     */
    fun buildUrl(baseUrl: String, path: String): String {
        val normalized = normalizeBaseUrl(baseUrl)
        val cleanPath = path.trim('/')
        return "$normalized$cleanPath"
    }

    /**
     * Validate a URL for basic correctness.
     * Returns null if valid, or an error message if invalid.
     */
    fun validateUrl(url: String): String? {
        if (url.isBlank()) return "URL is empty"

        val trimmed = url.trim()
        if (!trimmed.startsWith("https://") && !trimmed.startsWith("http://")) {
            return "URL must start with http:// or https://"
        }

        try {
            val uri = java.net.URI(trimmed)
            if (uri.host.isNullOrBlank()) return "URL has no valid host"
        } catch (e: Exception) {
            return "URL is malformed: ${e.message}"
        }

        return null
    }
}
