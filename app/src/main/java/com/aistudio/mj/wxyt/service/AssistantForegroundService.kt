package com.aistudio.mj.wxyt.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.aistudio.mj.wxyt.domain.assistant.MJState
import com.aistudio.mj.wxyt.domain.assistant.MJVoiceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

enum class AssistantState {
    INACTIVE, ACTIVE, LISTENING, THINKING, SPEAKING, EXECUTING, ERROR
}

class AssistantForegroundService : Service() {
    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_TOGGLE_LISTEN = "ACTION_TOGGLE_LISTEN"
        const val ACTION_SHOW_BUBBLE = "ACTION_SHOW_BUBBLE"
        const val ACTION_HIDE_BUBBLE = "ACTION_HIDE_BUBBLE"
        const val ACTION_START_BACKGROUND = "ACTION_START_BACKGROUND"

        private val _currentState = MutableStateFlow(AssistantState.INACTIVE)
        val currentState: StateFlow<AssistantState> = _currentState.asStateFlow()

        private val _rmsValue = MutableStateFlow(0f)
        val rmsValue: StateFlow<Float> = _rmsValue.asStateFlow()

        private val _voiceReactiveState = MutableStateFlow(
            com.aistudio.mj.wxyt.domain.assistant.VoiceReactiveState()
        )
        val voiceReactiveState: StateFlow<com.aistudio.mj.wxyt.domain.assistant.VoiceReactiveState> =
            _voiceReactiveState.asStateFlow()
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var mjVoiceManager: MJVoiceManager
    private var floatingBubbleManager: FloatingBubbleManager? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        mjVoiceManager = MJVoiceManager.getInstance(this)
        floatingBubbleManager = FloatingBubbleManager(this)
        
        mjVoiceManager.state.onEach { mjState ->
            _currentState.value = when (mjState) {
                MJState.DISCONNECTED -> AssistantState.INACTIVE
                MJState.IDLE -> AssistantState.INACTIVE
                MJState.WAKE_WORD_LISTENING -> AssistantState.INACTIVE
                MJState.ACTIVATING -> AssistantState.THINKING
                MJState.CONNECTING -> AssistantState.THINKING
                MJState.LISTENING -> AssistantState.LISTENING
                MJState.THINKING -> AssistantState.THINKING
                MJState.SPEAKING -> AssistantState.SPEAKING
                MJState.ERROR -> AssistantState.ERROR
            }
            if (mjState == MJState.DISCONNECTED) {
                floatingBubbleManager?.hideBubble()
            }
        }.launchIn(serviceScope)

        mjVoiceManager.rmsValue.onEach { rms ->
            _rmsValue.value = rms
        }.launchIn(serviceScope)

        mjVoiceManager.voiceReactiveState.onEach { vrs ->
            _voiceReactiveState.value = vrs
        }.launchIn(serviceScope)
    }

    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_STOP) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { 
                startForeground(1, createNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE) 
            } else { 
                startForeground(1, createNotification()) 
            }
        }
        when (intent?.action) {
            ACTION_START -> {
                mjVoiceManager.startSession()
            }
            ACTION_START_BACKGROUND -> {
                mjVoiceManager.startWakeWordListening()
                floatingBubbleManager?.showBubble()
            }
            null -> {
                // START_STICKY recovery: if Android recreates the service after
                // process pressure, restore wake-word listening from persisted
                // settings instead of silently becoming inactive.
                val settings = com.aistudio.mj.wxyt.domain.settings.SettingsRepository.get(this).settings.value
                if (settings.wakeWordEnabled) {
                    mjVoiceManager.startWakeWordListening()
                }
            }
            ACTION_STOP -> {
                floatingBubbleManager?.hideBubble()
                mjVoiceManager.stopSession()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
            }
            ACTION_TOGGLE_LISTEN -> {
                if (mjVoiceManager.state.value == MJState.DISCONNECTED) {
                    mjVoiceManager.startSession()
                } else {
                    floatingBubbleManager?.hideBubble()
                    mjVoiceManager.stopSession()
                }
            }
            ACTION_SHOW_BUBBLE -> {
                floatingBubbleManager?.showBubble()
            }
            ACTION_HIDE_BUBBLE -> {
                floatingBubbleManager?.hideBubble()
            }
        }
        return START_STICKY
    }


    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "MAYA_SERVICE_CHANNEL")
            .setContentTitle("MAYA")
            .setContentText("Voice assistant active")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "MAYA_SERVICE_CHANNEL",
                "MAYA Voice Assistant",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        floatingBubbleManager?.hideBubble()
        mjVoiceManager.stopSession()
        _currentState.value = AssistantState.INACTIVE
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
