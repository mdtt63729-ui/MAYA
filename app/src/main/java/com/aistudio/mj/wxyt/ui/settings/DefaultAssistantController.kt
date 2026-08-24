package com.aistudio.mj.wxyt.ui.settings

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/**
 * Centralizes the Android default-assistant flow.
 * Android 10+ uses ROLE_ASSISTANT; older builds fall back to the system voice-input
 * settings screen. The caller must still obtain the user's consent.
 */
object DefaultAssistantController {
    const val REQUEST_CODE = 7001

    fun isDefaultAssistant(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return try {
            val manager = context.getSystemService(RoleManager::class.java)
            manager?.isRoleAvailable(RoleManager.ROLE_ASSISTANT) == true &&
                manager.isRoleHeld(RoleManager.ROLE_ASSISTANT)
        } catch (_: Throwable) {
            false
        }
    }

    fun canRequestRole(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return try {
            context.getSystemService(RoleManager::class.java)
                ?.isRoleAvailable(RoleManager.ROLE_ASSISTANT) == true
        } catch (_: Throwable) {
            false
        }
    }

    fun createRequestIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            val manager = context.getSystemService(RoleManager::class.java)
            if (manager?.isRoleAvailable(RoleManager.ROLE_ASSISTANT) == true) {
                manager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)
            } else null
        } catch (_: Throwable) {
            null
        }
    }

    fun openSystemAssistantSettings(activity: Activity) {
        val primary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        } else {
            Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
        }
        val fallback = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
        try {
            if (primary.resolveActivity(activity.packageManager) != null) {
                activity.startActivity(primary)
            } else if (fallback.resolveActivity(activity.packageManager) != null) {
                activity.startActivity(fallback)
            }
        } catch (_: Throwable) {
            try { activity.startActivity(fallback) } catch (_: Throwable) { }
        }
    }
}
