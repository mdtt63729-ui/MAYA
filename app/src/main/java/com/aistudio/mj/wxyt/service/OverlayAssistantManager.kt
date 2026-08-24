package com.aistudio.mj.wxyt.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.aistudio.mj.wxyt.ui.LiquidAIOrb
import com.aistudio.mj.wxyt.ui.theme.MyApplicationTheme

/**
 * Overlay Assistant UI — PRD 1 §2.2.
 *
 * Global floating/overlay assistant that renders the Gemini/Siri-style glowing
 * animation over any running app or home screen upon wake-word trigger.
 *
 * Key features:
 * - TYPE_APPLICATION_OVERLAY permission flow
 * - Non-blocking interaction (touch events outside overlay pass through)
 * - Spring-based opacity transition on activation
 * - Glowing gradient edge animation
 */
class OverlayAssistantManager(private val context: Context) {

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var glowView: ComposeView? = null
    private var lifecycleOwner: MyLifecycleOwner? = null
    private var isShowing = false

    /**
     * Shows the overlay assistant UI.
     * Requires SYSTEM_ALERT_WINDOW permission (TYPE_APPLICATION_OVERLAY on API 26+).
     */
    fun showOverlay() {
        if (isShowing) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            Log.w("OverlayAssistant", "SYSTEM_ALERT_WINDOW permission not granted")
            return
        }

        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // Create lifecycle owner for Compose
        lifecycleOwner = MyLifecycleOwner().apply {
            performRestore(null)
            handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        }

        // Main ORB view — small floating bubble
        composeView = ComposeView(context).apply {
            setContent {
                MyApplicationTheme {
                    val state by AssistantForegroundService.currentState.collectAsState()
                    val rmsValue by AssistantForegroundService.rmsValue.collectAsState()
                    val voiceReactiveState by AssistantForegroundService.voiceReactiveState.collectAsState()
                    val mjState = when (state) {
                        AssistantState.INACTIVE -> com.aistudio.mj.wxyt.domain.assistant.MJState.DISCONNECTED
                        AssistantState.ACTIVE -> com.aistudio.mj.wxyt.domain.assistant.MJState.CONNECTING
                        AssistantState.LISTENING -> com.aistudio.mj.wxyt.domain.assistant.MJState.LISTENING
                        AssistantState.THINKING -> com.aistudio.mj.wxyt.domain.assistant.MJState.THINKING
                        AssistantState.EXECUTING -> com.aistudio.mj.wxyt.domain.assistant.MJState.CONNECTING
                        AssistantState.SPEAKING -> com.aistudio.mj.wxyt.domain.assistant.MJState.SPEAKING
                        AssistantState.ERROR -> com.aistudio.mj.wxyt.domain.assistant.MJState.ERROR
                    }
                    // Spring-based opacity transition — PRD 2 §4.2
                    val targetAlpha = if (state == AssistantState.INACTIVE) 0f else 1f
                    val alpha by animateFloatAsState(
                        targetValue = targetAlpha,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "overlay_alpha"
                    )
                    Box(modifier = Modifier.size(100.dp)) {
                        LiquidAIOrb(
                            state = mjState,
                            rmsValue = rmsValue,
                            onClick = {
                                val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                                    ?: android.content.Intent(context, com.aistudio.mj.wxyt.MainActivity::class.java)
                                intent.apply {
                                    putExtra("assistant_entry", true)
                                    putExtra("orb_click", true)
                                    addFlags(
                                        android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                                            android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                            android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                                    )
                                }
                                context.startActivity(intent)
                            },
                            voiceReactiveState = voiceReactiveState
                        )
                    }
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

        // Main ORB params — WRAP_CONTENT, pass-through touches outside
        val orbParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            // FLAG_NOT_FOCUSABLE + FLAG_LAYOUT_NO_LIMITS: non-blocking, touch passes through outside
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
        }

        setupDrag(composeView!!, orbParams)

        windowManager?.addView(composeView, orbParams)
        lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        isShowing = true
        Log.d("OverlayAssistant", "Overlay shown")
    }

    /**
     * Touch-passthrough drag handler — allows repositioning the ORB while
     * touches outside the ORB's bounds pass through to the underlying app.
     */
    private fun setupDrag(view: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        isDragging = true
                        params.x = initialX + dx.toInt()
                        params.y = initialY + dy.toInt()
                        windowManager?.updateViewLayout(view, params)
                        true
                    } else {
                        false
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        isDragging = false
                        true
                    } else {
                        v.performClick()
                        true
                    }
                }
                else -> false
            }
        }
    }

    fun hideOverlay() {
        if (!isShowing) return
        try {
            lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            composeView?.disposeComposition()
            windowManager?.removeView(composeView)
            glowView?.let { windowManager?.removeView(it) }
        } catch (e: Exception) {
            Log.e("OverlayAssistant", "Error hiding overlay", e)
        }
        isShowing = false
        composeView = null
        glowView = null
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
}
