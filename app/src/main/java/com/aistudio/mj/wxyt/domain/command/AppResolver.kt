package com.aistudio.mj.wxyt.domain.command

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

class AppResolver(private val context: Context) {
    private val aliases = mapOf(
        "youtube" to "YouTube",
        "ইউটিউব" to "YouTube",
        "whatsapp" to "WhatsApp",
        "হোয়াটসঅ্যাপ" to "WhatsApp",
        "chrome" to "Chrome",
        "ক্রোম" to "Chrome",
        "play store" to "Google Play Store",
        "প্লে স্টোর" to "Google Play Store",
        "google" to "Google",
        "facebook" to "Facebook",
        "ফেসবুক" to "Facebook",
        "settings" to "Settings",
        "সেটিংস" to "Settings",
        "netflix" to "Netflix",
        "নেটফ্লিক্স" to "Netflix"
    )

    fun resolve(targetName: String): ResolveResult {
        val pm = context.packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        
        val normalizedTarget = targetName.lowercase().trim()
        val mappedName = aliases[normalizedTarget] ?: targetName
        val mappedNameLower = mappedName.lowercase()
        
        val bestMatches = mutableListOf<ApplicationInfo>()
        
        for (appInfo in packages) {
            val appLabel = pm.getApplicationLabel(appInfo).toString()
            if (appLabel.equals(mappedName, ignoreCase = true) || appLabel.equals(targetName, ignoreCase = true)) {
                if (pm.getLaunchIntentForPackage(appInfo.packageName) != null) {
                    bestMatches.add(appInfo)
                }
            }
        }
        
        if (bestMatches.isEmpty()) {
            for (appInfo in packages) {
                val appLabel = pm.getApplicationLabel(appInfo).toString().lowercase()
                if (appLabel.contains(mappedNameLower) || appLabel.contains(normalizedTarget)) {
                    if (pm.getLaunchIntentForPackage(appInfo.packageName) != null) {
                        bestMatches.add(appInfo)
                    }
                }
            }
        }

        return when {
            bestMatches.isEmpty() -> ResolveResult(null, false)
            bestMatches.size == 1 -> ResolveResult(
                ResolvedApp(bestMatches[0].packageName, pm.getApplicationLabel(bestMatches[0]).toString()), 
                false
            )
            else -> ResolveResult(null, true)
        }
    }
}

data class ResolvedApp(val packageName: String, val label: String)
data class ResolveResult(val app: ResolvedApp?, val isAmbiguous: Boolean)
