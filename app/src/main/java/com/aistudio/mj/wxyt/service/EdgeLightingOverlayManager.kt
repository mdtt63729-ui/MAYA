package com.aistudio.mj.wxyt.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.aistudio.mj.wxyt.ui.MayaEdgeLighting
import com.aistudio.mj.wxyt.ui.theme.MyApplicationTheme

/**
 * Full-screen Edge Lighting Overlay — PRD §8 (Background / Overlay).
 *
 * When MAYA is minimized while actively listening, thinking, or speaking,
 * the premium edge-lighting overlay MUST remain visible and responsive
 * across the home screen and other applications.
 *
 * Key properties:
 * - TYPE_APPLICATION_OVERLAY — renders above other apps
 * - FLAG_NOT_FOCUSABLE — does not steal input focus
 * - FLAG_NOT_TOUCHABLE — does not intercept user touch/input
 * - Managed independently by the persistent foreground assistant service
 *
 * If overlay permission is not granted, the animation falls back to the
 * foreground Activity's in-app edge lighting.
 */
class EdgeLightingOverlayManager(private val context: Context) {

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var lifecycleOwner: MyLifecycleOwner? = null
    private var isShowing = false

    /**
     * Shows the full-screen edge lighting overlay.
     * The overlay covers the entire screen but is completely transparent
     * to touch events — it only renders the edge glow animation.
     */
    fun showOverlay() {
        if (isShowing) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            Log.w("EdgeOverlay", "SYSTEM_ALERT_WINDOW permission not granted — cannot show overlay")
            return
        }

        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        lifecycleOwner = MyLifecycleOwner().apply {
            performRestore(null)
            handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        }

        composeView = ComposeView(context).apply {
            setContent {
                MyApplicationTheme {
                    val assistantState by BackgroundAssistantService.currentState.collectAsState()
                    val voiceReactiveState by BackgroundAssistantService.voiceReactiveState.collectAsState()

                    // Map AssistantState to MJState for the edge lighting composable
                    val mjState = when (assistantState) {
                        AssistantState.INACTIVE -> com.aistudio.mj.wxyt.domain.assistant.MJState.DISCONNECTED
                        AssistantState.ACTIVE -> com.aistudio.mj.wxyt.domain.assistant.MJState.CONNECTING
                        AssistantState.LISTENING -> com.aistudio.mj.wxyt.domain.assistant.MJState.LISTENING
                        AssistantState.THINKING -> com.aistudio.mj.wxyt.domain.assistant.MJState.THINKING
                        AssistantState.EXECUTING -> com.aistudio.mj.wxyt.domain.assistant.MJState.CONNECTING
                        AssistantState.SPEAKING -> com.aistudio.mj.wxyt.domain.assistant.MJState.SPEAKING
                        AssistantState.ERROR -> com.aistudio.mj.wxyt.domain.assistant.MJState.ERROR
                    }

                    MayaEdgeLighting(
                        assistantState = mjState,
                        reactiveState = voiceReactiveState,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        composeView!!.setViewTreeLifecycleOwner(lifecycleOwner)
        composeView!!.setViewTreeViewModelStoreOwner(lifecycleOwner)
        composeView!!.setViewTreeSavedStateRegistryOwner(lifecycleOwner)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // Full-screen overlay — completely pass-through to touch
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutFlag,
            // FLAG_NOT_FOCUSABLE: doesn't steal input focus
            // FLAG_NOT_TOUCHABLE: touch events pass through to underlying app
            // FLAG_LAYOUT_NO_LIMITS: extends to full screen including system bars
            // FLAG_LAYOUT_IN_SCREEN: draws relative to the physical screen
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        try {
            windowManager?.addView(composeView, params)
            lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            isShowing = true
            Log.d("EdgeOverlay", "Full-screen edge lighting overlay shown")
        } catch (e: Exception) {
            Log.e("EdgeOverlay", "Failed to show overlay", e)
            isShowing = false
        }
    }

    /**
     * Hides and removes the edge lighting overlay.
     */
    fun hideOverlay() {
        if (!isShowing) return
        try {
            lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            composeView?.disposeComposition()
            windowManager?.removeView(composeView)
            Log.d("EdgeOverlay", "Edge lighting overlay hidden")
        } catch (e: Exception) {
            Log.e("EdgeOverlay", "Error hiding overlay", e)
        }
        isShowing = false
        composeView = null
        lifecycleOwner = null
    }

    fun isShowing(): Boolean = isShowing

    /**
     * Checks whether the overlay permission is granted.
     */
    fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    /**
     * Shows the overlay if the assistant is in an active state and
     * permission is available. Otherwise hides it.
     */
    fun updateVisibility(isActive: Boolean) {
        if (isActive && !isShowing && hasOverlayPermission()) {
            showOverlay()
        } else if (!isActive && isShowing) {
            hideOverlay()
        }
    }
}
