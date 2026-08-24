package com.aistudio.mj.wxyt.domain.tools

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppLauncher(private val context: Context) {
    suspend fun getInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfoList: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)
        
        resolveInfoList.map {
            AppInfo(
                appName = it.loadLabel(pm).toString(),
                packageName = it.activityInfo.packageName
            )
        }
    }

    suspend fun launchApp(appName: String): Boolean = withContext(Dispatchers.IO) {
        val apps = getInstalledApps()
        val normalizedTarget = appName.lowercase().replace(" ", "")
        
        val matchedApp = apps.find { it.appName.lowercase().replace(" ", "") == normalizedTarget }
            ?: apps.find { it.appName.lowercase().contains(normalizedTarget) }
            
        if (matchedApp != null) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(matchedApp.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return@withContext true
            }
        }
        return@withContext false
    }

    fun openWebsite(url: String) {
        var parsedUrl = url
        if (!parsedUrl.startsWith("http://") && !parsedUrl.startsWith("https://")) {
            parsedUrl = "https://$parsedUrl"
        }
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(parsedUrl))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

data class AppInfo(val appName: String, val packageName: String)
