package com.aistudio.mj.wxyt.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.aistudio.mj.wxyt.domain.assistant.MJState
import com.aistudio.mj.wxyt.domain.assistant.MJVoiceManager
import com.aistudio.mj.wxyt.domain.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Persistent background assistant service — PRD 1 §2.1.
 *
 * Upgrades from AssistantForegroundService:
 * - Explicit WakeLock management (PARTIAL_WAKE_LOCK) during active voice pipelines.
 * - Battery-optimization bypass request flow for OEM Doze survival.
 * - Dual notification channels (ambient low-priority + active high-priority).
 * - START_STICKY recovery with persisted settings restoration.
 */
class BackgroundAssistantService : Service() {
    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_TOGGLE_LISTEN = "ACTION_TOGGLE_LISTEN"
        const val ACTION_START_WAKE_WORD = "ACTION_START_WAKE_WORD"
        const val ACTION_ACQUIRE_WAKELOCK = "ACTION_ACQUIRE_WAKELOCK"
        const val ACTION_RELEASE_WAKELOCK = "ACTION_RELEASE_WAKELOCK"

        const val CHANNEL_AMBIENT = "MAYA_AMBIENT_CHANNEL"
        const val CHANNEL_ACTIVE = "MAYA_ACTIVE_CHANNEL"
        private const val NOTIF_ID = 1

        private val _currentState = MutableStateFlow(AssistantState.INACTIVE)
        val currentState: StateFlow<AssistantState> = _currentState.asStateFlow()

        private val _rmsValue = MutableStateFlow(0f)
        val rmsValue: StateFlow<Float> = _rmsValue.asStateFlow()

        private val _voiceReactiveState = MutableStateFlow(
            com.aistudio.mj.wxyt.domain.assistant.VoiceReactiveState()
        )
        val voiceReactiveState: StateFlow<com.aistudio.mj.wxyt.domain.assistant.VoiceReactiveState> =
            _voiceReactiveState.asStateFlow()

        private val _isWakeLockHeld = MutableStateFlow(false)
        val isWakeLockHeld: StateFlow<Boolean> = _isWakeLockHeld.asStateFlow()

        /**
         * Convenience launcher: starts the persistent background service.
         */
        fun start(context: Context, action: String = ACTION_START) {
            val intent = Intent(context, BackgroundAssistantService::class.java).apply {
                this.action = action
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, BackgroundAssistantService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var mjVoiceManager: MJVoiceManager
    private var floatingBubbleManager: FloatingBubbleManager? = null
    private var edgeLightingOverlay: EdgeLightingOverlayManager? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        mjVoiceManager = MJVoiceManager.getInstance(this)
        floatingBubbleManager = FloatingBubbleManager(this)
        edgeLightingOverlay = EdgeLightingOverlayManager(this)

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
            // Auto-manage WakeLock based on active voice pipeline states
            when (mjState) {
                MJState.LISTENING, MJState.THINKING, MJState.SPEAKING,
                MJState.ACTIVATING, MJState.CONNECTING -> {
                    acquireWakeLockIfNeeded()
                }
                MJState.DISCONNECTED, MJState.IDLE, MJState.WAKE_WORD_LISTENING -> {
                    releaseWakeLockIfNeeded()
                }
                MJState.ERROR -> releaseWakeLockIfNeeded()
            }
            // PRD §8: Show/hide edge lighting overlay based on assistant state
            val isAssistantActive = mjState in setOf(
                MJState.ACTIVATING, MJState.CONNECTING,
                MJState.LISTENING, MJState.THINKING, MJState.SPEAKING
            )
            edgeLightingOverlay?.updateVisibility(isAssistantActive)

            if (mjState == MJState.DISCONNECTED) {
                floatingBubbleManager?.hideBubble()
                edgeLightingOverlay?.hideOverlay()
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
        val action = intent?.action
        if (action != ACTION_STOP) {
            startForeground(NOTIF_ID, createAmbientNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        }
        when (action) {
            ACTION_START -> {
                mjVoiceManager.startSession()
            }
            ACTION_START_WAKE_WORD -> {
                mjVoiceManager.startWakeWordListening()
                floatingBubbleManager?.showBubble()
            }
            null -> {
                // START_STICKY recovery: restore wake-word listening from persisted settings
                val settings = SettingsRepository.get(this).settings.value
                if (settings.wakeWordEnabled || settings.backgroundEnabled) {
                    Log.d("BackgroundAssistant", "START_STICKY recovery — restoring wake-word listening")
                    mjVoiceManager.startWakeWordListening()
                    if (settings.backgroundEnabled) {
                        floatingBubbleManager?.showBubble()
                    }
                }
            }
            ACTION_STOP -> {
                releaseWakeLockIfNeeded()
                floatingBubbleManager?.hideBubble()
                edgeLightingOverlay?.hideOverlay()
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
            ACTION_ACQUIRE_WAKELOCK -> acquireWakeLockIfNeeded()
            ACTION_RELEASE_WAKELOCK -> releaseWakeLockIfNeeded()
        }
        return START_STICKY
    }

    /**
     * Acquires a PARTIAL_WAKE_LOCK for the duration of active voice processing.
     * Per PRD 2.1: WakeLocks are used sparingly, only during active voice pipelines.
     */
    private fun acquireWakeLockIfNeeded() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MAYA::VoicePipeline")
        wakeLock?.acquire(30_000L) // 30-second timeout as safety net
        _isWakeLockHeld.value = true
        Log.d("BackgroundAssistant", "PARTIAL_WAKE_LOCK acquired")
    }

    private fun releaseWakeLockIfNeeded() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
            Log.d("BackgroundAssistant", "PARTIAL_WAKE_LOCK released")
        }
        wakeLock = null
        _isWakeLockHeld.value = false
    }

    /**
     * Ambient notification — low-priority, non-intrusive per PRD §2.1.
     * Satisfies Android's foreground-service notification requirement.
     */
    private fun createAmbientNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_AMBIENT)
            .setContentTitle("MAYA")
            .setContentText("Ambient assistant listening")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            // Low-priority ambient channel for persistent background listening
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_AMBIENT,
                    "MAYA Ambient",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Low-priority persistent notification for background listening"
                    setShowBadge(false)
                }
            )
            // Higher-priority channel for active assistant interactions
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ACTIVE,
                    "MAYA Active",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifications for active assistant sessions"
                }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLockIfNeeded()
        floatingBubbleManager?.hideBubble()
        edgeLightingOverlay?.hideOverlay()
        mjVoiceManager.stopSession()
        _currentState.value = AssistantState.INACTIVE
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
