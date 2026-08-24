package com.aistudio.mj.wxyt.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
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

class MyLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    fun performRestore(savedState: Bundle?) {
        savedStateRegistryController.performRestore(savedState)
    }

    fun handleLifecycleEvent(event: Lifecycle.Event) {
        lifecycleRegistry.handleLifecycleEvent(event)
    }
}

class FloatingBubbleManager(private val context: Context) {

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var lifecycleOwner: MyLifecycleOwner? = null
    private var isShowing = false

    fun showBubble() {
        if (isShowing) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(context)) {
            return
        }

        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        composeView = ComposeView(context).apply {
            setContent {
                MyApplicationTheme {
                    val state by AssistantForegroundService.currentState.collectAsState()
                    val rmsValue by AssistantForegroundService.rmsValue.collectAsState()
                    val voiceReactiveState by AssistantForegroundService.voiceReactiveState.collectAsState()
                    val mjState = when(state) {
                        AssistantState.INACTIVE -> com.aistudio.mj.wxyt.domain.assistant.MJState.DISCONNECTED
                        AssistantState.ACTIVE -> com.aistudio.mj.wxyt.domain.assistant.MJState.CONNECTING
                        AssistantState.LISTENING -> com.aistudio.mj.wxyt.domain.assistant.MJState.LISTENING
                        AssistantState.THINKING -> com.aistudio.mj.wxyt.domain.assistant.MJState.THINKING
                        AssistantState.EXECUTING -> com.aistudio.mj.wxyt.domain.assistant.MJState.CONNECTING
                        AssistantState.SPEAKING -> com.aistudio.mj.wxyt.domain.assistant.MJState.SPEAKING
                        AssistantState.ERROR -> com.aistudio.mj.wxyt.domain.assistant.MJState.ERROR
                    }
                    Box(modifier = Modifier.size(100.dp)) {
                        LiquidAIOrb(
                            state = mjState,
                            rmsValue = rmsValue,
                            onClick = {
                                // The host ComposeView click listener launches MAYA.
                            },
                            voiceReactiveState = voiceReactiveState
                        )
                    }
                }
            }
        }

        lifecycleOwner = MyLifecycleOwner().apply {
            performRestore(null)
            handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        }

        composeView!!.setViewTreeLifecycleOwner(lifecycleOwner)
        composeView!!.setViewTreeViewModelStoreOwner(lifecycleOwner)
        composeView!!.setViewTreeSavedStateRegistryOwner(lifecycleOwner)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
        }

        composeView?.setOnClickListener {
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
        }

        setupDrag(composeView!!, params)

        windowManager?.addView(composeView, params)
        lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        isShowing = true
    }

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
                    false // Return false so onClick still works
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

    fun hideBubble() {
        if (!isShowing) return
        try {
            lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            lifecycleOwner?.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            composeView?.disposeComposition()
            windowManager?.removeView(composeView)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        isShowing = false
        composeView = null
        lifecycleOwner = null
    }
}
