package com.aistudio.mj.wxyt.domain.command

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import java.util.Locale

/**
 * Intent Dispatcher — PRD 1 §3.1.
 *
 * System-level module that parses user intents and resolves them using explicit
 * Android Intent commands. Implements the three-tier deep-linking fallback:
 *
 * 1. Direct URI Deep-Linking (e.g., vnd.youtube://...)
 * 2. Package Manager Direct Launch (getLaunchIntentForPackage)
 * 3. Browser Web Fallback if the app package is not installed
 */
class IntentDispatcher(private val context: Context) {

    private val appResolver = AppResolver(context)

    /**
     * Deep-link registry — maps app canonical names to their deep-link URI schemes.
     * This enables direct content navigation (e.g., play a specific video on YouTube).
     */
    private val deepLinkSchemes = mapOf(
        "youtube" to DeepLinkConfig(
            scheme = "vnd.youtube://",
            webFallback = "https://www.youtube.com",
            searchUrl = "https://www.youtube.com/results?search_query=%s",
            packageSearchIntent = true
        ),
        "spotify" to DeepLinkConfig(
            scheme = "spotify:",
            webFallback = "https://open.spotify.com",
            searchUrl = "https://open.spotify.com/search/%s",
            packageSearchIntent = false
        ),
        "netflix" to DeepLinkConfig(
            scheme = "nflx://",
            webFallback = "https://www.netflix.com",
            searchUrl = "https://www.netflix.com/search?q=%s",
            packageSearchIntent = false
        ),
        "whatsapp" to DeepLinkConfig(
            scheme = "whatsapp://",
            webFallback = "https://wa.me",
            searchUrl = null,
            packageSearchIntent = false
        ),
        "maps" to DeepLinkConfig(
            scheme = "geo:",
            webFallback = "https://maps.google.com",
            searchUrl = "https://www.google.com/maps/search/%s",
            packageSearchIntent = false
        )
    )

    data class DeepLinkConfig(
        val scheme: String,
        val webFallback: String,
        val searchUrl: String?,
        val packageSearchIntent: Boolean
    )

    data class DispatchResult(
        val success: Boolean,
        val method: DispatchMethod,
        val targetPackage: String?,
        val message: String
    )

    enum class DispatchMethod {
        DEEP_LINK, PACKAGE_LAUNCH, WEB_FALLBACK, FAILED
    }

    /**
     * Launches a third-party app using the three-tier fallback strategy.
     *
     * @param appName Human-readable app name (e.g., "YouTube", "Spotify")
     * @param deepLinkPath Optional deep-link path for direct content navigation
     * @return DispatchResult describing which method succeeded
     */
    fun launchApp(appName: String, deepLinkPath: String? = null): DispatchResult {
        val canonical = canonicalAppName(appName)
        Log.d("IntentDispatcher", "launchApp: canonical=$canonical, deepLinkPath=$deepLinkPath")

        // Tier 1: Direct URI Deep-Linking
        if (deepLinkPath != null) {
            val deepLinkResult = tryDeepLink(canonical, deepLinkPath)
            if (deepLinkResult.success) return deepLinkResult
        }

        // Tier 2: Package Manager Direct Launch
        val packageResult = tryPackageLaunch(canonical)
        if (packageResult.success) return packageResult

        // Tier 3: Browser Web Fallback
        return tryWebFallback(canonical)
    }

    /**
     * Searches within an app using its native search capability or web fallback.
     */
    fun searchInApp(appName: String, query: String): DispatchResult {
        val canonical = canonicalAppName(appName)
        val config = deepLinkSchemes[canonical]
        val resolved = appResolver.resolve(canonical)

        // If the app is installed, use native search intent
        if (resolved.app != null && !resolved.isAmbiguous) {
            if (config?.packageSearchIntent == true && canonical == "youtube") {
                val searchIntent = Intent(Intent.ACTION_SEARCH).apply {
                    setPackage(resolved.app.packageName)
                    putExtra(android.app.SearchManager.QUERY, query)
                    putExtra("query", query)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                return try {
                    context.startActivity(searchIntent)
                    DispatchResult(true, DispatchMethod.PACKAGE_LAUNCH, resolved.app.packageName,
                        "Searching '$query' in $canonical via native intent")
                } catch (e: Exception) {
                    Log.e("IntentDispatcher", "Native search failed for $canonical", e)
                    tryWebSearch(canonical, query)
                }
            }
        }

        // Web fallback for search
        return tryWebSearch(canonical, query)
    }

    /**
     * Plays media on a specified app with deep-link support.
     * E.g., "Play Despacito on YouTube" → deep-links directly to the video.
     */
    fun playMedia(appName: String, query: String, videoId: String? = null): DispatchResult {
        val canonical = canonicalAppName(appName)

        // If we have a direct video/content ID, use deep-link
        if (videoId != null && canonical == "youtube") {
            val deepLinkUri = Uri.parse("vnd.youtube://$videoId")
            val intent = Intent(Intent.ACTION_VIEW, deepLinkUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return try {
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    DispatchResult(true, DispatchMethod.DEEP_LINK, "com.google.android.youtube",
                        "Playing video $videoId on YouTube via deep-link")
                } else {
                    // Fall through to search
                    searchInApp(canonical, query)
                }
            } catch (e: Exception) {
                Log.e("IntentDispatcher", "Deep-link play failed", e)
                searchInApp(canonical, query)
            }
        }

        // No direct ID — search within the app
        return searchInApp(canonical, query)
    }

    // --- Tier implementations ---

    private fun tryDeepLink(canonical: String, path: String): DispatchResult {
        val config = deepLinkSchemes[canonical] ?: return DispatchResult(
            false, DispatchMethod.DEEP_LINK, null, "No deep-link scheme for $canonical"
        )
        val uri = if (path.startsWith(config.scheme)) path else "${config.scheme}$path"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                DispatchResult(true, DispatchMethod.DEEP_LINK, null,
                    "Opened $canonical via deep-link: $uri")
            } else {
                DispatchResult(false, DispatchMethod.DEEP_LINK, null,
                    "No activity handles deep-link for $canonical")
            }
        } catch (e: Exception) {
            Log.e("IntentDispatcher", "Deep-link failed: $uri", e)
            DispatchResult(false, DispatchMethod.DEEP_LINK, null, "Deep-link failed: ${e.message}")
        }
    }

    private fun tryPackageLaunch(canonical: String): DispatchResult {
        val resolved = appResolver.resolve(canonical)
        if (resolved.isAmbiguous) {
            return DispatchResult(false, DispatchMethod.PACKAGE_LAUNCH, null,
                "Multiple apps match '$canonical' — please specify")
        }
        val app = resolved.app ?: return DispatchResult(
            false, DispatchMethod.PACKAGE_LAUNCH, null,
            "$canonical is not installed"
        )
        val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: return DispatchResult(false, DispatchMethod.PACKAGE_LAUNCH, app.packageName,
                "No launch intent for ${app.label}")
        return try {
            context.startActivity(intent)
            DispatchResult(true, DispatchMethod.PACKAGE_LAUNCH, app.packageName,
                "Launched ${app.label} via PackageManager")
        } catch (e: Exception) {
            Log.e("IntentDispatcher", "Package launch failed: ${app.packageName}", e)
            DispatchResult(false, DispatchMethod.PACKAGE_LAUNCH, app.packageName,
                "Failed to launch ${app.label}")
        }
    }

    private fun tryWebFallback(canonical: String): DispatchResult {
        val config = deepLinkSchemes[canonical]
        val webUrl = config?.webFallback ?: when (canonical) {
            "google" -> "https://www.google.com"
            "chrome" -> "https://www.google.com"
            else -> null
        }
        if (webUrl == null) {
            return DispatchResult(false, DispatchMethod.WEB_FALLBACK, null,
                "No web fallback available for $canonical")
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            DispatchResult(true, DispatchMethod.WEB_FALLBACK, null,
                "Opened $canonical via browser fallback: $webUrl")
        } catch (e: Exception) {
            Log.e("IntentDispatcher", "Web fallback failed: $webUrl", e)
            DispatchResult(false, DispatchMethod.WEB_FALLBACK, null,
                "Browser fallback failed for $canonical")
        }
    }

    private fun tryWebSearch(canonical: String, query: String): DispatchResult {
        val config = deepLinkSchemes[canonical]
        val searchUrl = config?.searchUrl?.replace("%s", Uri.encode(query))
            ?: "https://www.google.com/search?q=${Uri.encode(query)}"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            DispatchResult(true, DispatchMethod.WEB_FALLBACK, null,
                "Searching '$query' via web: $searchUrl")
        } catch (e: Exception) {
            Log.e("IntentDispatcher", "Web search failed", e)
            DispatchResult(false, DispatchMethod.WEB_FALLBACK, null,
                "Web search failed for '$query'")
        }
    }

    private fun canonicalAppName(name: String): String = when (name.trim().lowercase(Locale.getDefault())) {
        "ইউটিউব" -> "youtube"
        "হোয়াটসঅ্যাপ", "হোয়াটসাপ" -> "whatsapp"
        "ক্রোম" -> "chrome"
        "গুগল" -> "google"
        "ফেসবুক" -> "facebook"
        "প্লে স্টোর" -> "play store"
        "সেটিংস" -> "settings"
        "ক্যামেরা" -> "camera"
        "ম্যাপস", "মানচিত্র" -> "maps"
        else -> name.trim().lowercase(Locale.getDefault())
    }
}
