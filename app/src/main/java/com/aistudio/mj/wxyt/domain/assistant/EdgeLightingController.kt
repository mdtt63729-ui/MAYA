package com.aistudio.mj.wxyt.domain.assistant

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Centralized Edge Lighting state machine — Premium Edge Lighting PRD §2.
 *
 * State flow:
 *   IDLE → ACTIVATING → LISTENING → THINKING → SPEAKING → IDLE
 *
 * Transitions are continuous, interruptible, and reversible. The controller
 * never resets visual parameters abruptly — it smoothly interpolates between
 * states so the user perceives a single fluid animation rather than discrete jumps.
 *
 * Audio reactivity uses an attack/release envelope (fast attack, slow release)
 * so that voice start produces immediate glow response while voice stop decays
 * gracefully — preventing jitter and flicker.
 */
enum class EdgeLightingState {
    IDLE,           // Screen clean, no visible glow
    ACTIVATING,     // Smooth light reveal from edges (350–500ms)
    LISTENING,      // Continuous soft flowing light, reactive to mic amplitude
    THINKING,       // Slow flowing motion, subtle breathing
    SPEAKING        // Audio-reactive to TTS amplitude
}

/**
 * Smoothed audio envelope consumed by the edge renderer.
 *
 * Pipeline: Audio → Amplitude/RMS → Smoothing → Normalized Energy → [this] → GPU Renderer
 *
 * Attack: fast (~20ms) — voice start lights up quickly.
 * Release: slow (~300ms) — voice stop decays gracefully.
 */
data class EdgeLightingParams(
    val state: EdgeLightingState = EdgeLightingState.IDLE,
    /** Smoothed normalized energy 0..1 — drives glow intensity, wave amplitude. */
    val energy: Float = 0f,
    /** Raw normalized amplitude for fine-grained wave effects. */
    val amplitude: Float = 0f,
    /** Continuous phase for flowing light — never resets, always advancing. */
    val flowPhase: Float = 0f,
    /** Breathing phase for THINKING state subtle pulse. */
    val breathPhase: Float = 0f,
    /** Activation reveal progress 0..1 — spring-like ramp on ACTIVATING. */
    val revealProgress: Float = 0f,
    /** Whether voice activity is currently detected. */
    val isVoiceActive: Boolean = false
)

class EdgeLightingController {

    private val _params = MutableStateFlow(EdgeLightingParams())
    val params: StateFlow<EdgeLightingParams> = _params.asStateFlow()

    private var currentState: EdgeLightingState = EdgeLightingState.IDLE
    private var currentEnergy: Float = 0f
    private var currentAmplitude: Float = 0f
    private var flowPhase: Float = 0f
    private var breathPhase: Float = 0f
    private var revealProgress: Float = 0f
    private var isVoiceActive: Boolean = false

    // Envelope follower state
    private var envelopeEnergy: Float = 0f
    private val attackCoeff: Float = 0.35f   // Fast attack
    private val releaseCoeff: Float = 0.04f  // Slow release

    // Timestamp for phase advancement
    private var lastUpdateMs: Long = System.currentTimeMillis()

    /**
     * Called when the assistant's MJState changes.
     * Maps MJState → EdgeLightingState and manages the transition.
     */
    fun onAssistantStateChanged(mjState: MJState) {
        val newState = when (mjState) {
            MJState.DISCONNECTED -> EdgeLightingState.IDLE
            MJState.IDLE -> EdgeLightingState.IDLE
            MJState.WAKE_WORD_LISTENING -> EdgeLightingState.IDLE
            MJState.ACTIVATING -> EdgeLightingState.ACTIVATING
            MJState.CONNECTING -> EdgeLightingState.ACTIVATING
            MJState.LISTENING -> EdgeLightingState.LISTENING
            MJState.THINKING -> EdgeLightingState.THINKING
            MJState.SPEAKING -> EdgeLightingState.SPEAKING
            MJState.ERROR -> EdgeLightingState.IDLE
        }

        if (newState != currentState) {
            Log.d("EdgeLightingCtrl", "State: $currentState → $newState")
            currentState = newState
            if (newState == EdgeLightingState.ACTIVATING) {
                revealProgress = 0f // Start reveal animation
            }
        }
        emit()
    }

    /**
     * Called from VoiceReactiveController with smoothed audio energy.
     * This drives the audio-reactive glow for LISTENING and SPEAKING states.
     */
    fun onAudioEnergy(energy: Float, amplitude: Float, isVoiceActive: Boolean) {
        this.isVoiceActive = isVoiceActive
        this.currentEnergy = energy.coerceIn(0f, 1f)
        this.currentAmplitude = amplitude.coerceIn(0f, 1f)

        // Apply attack/release envelope
        val target = this.currentEnergy
        val coeff = if (target > envelopeEnergy) attackCoeff else releaseCoeff
        envelopeEnergy += (target - envelopeEnergy) * coeff
        envelopeEnergy = envelopeEnergy.coerceIn(0f, 1f)

        emit()
    }

    /**
     * Advances continuous animation phases. Called on each frame tick (~16ms).
     * The phases never reset — they advance continuously so transitions are seamless.
     */
    fun tick() {
        val now = System.currentTimeMillis()
        val dt = (now - lastUpdateMs).coerceAtMost(64L) / 1000f // Cap at ~64ms (avoid jumps)
        lastUpdateMs = now

        // Flow phase: continuous slow movement (never resets)
        // Speed varies by state: LISTENING faster, THINKING slower
        val flowSpeed = when (currentState) {
            EdgeLightingState.LISTENING -> 0.35f + envelopeEnergy * 0.4f
            EdgeLightingState.SPEAKING -> 0.40f + envelopeEnergy * 0.5f
            EdgeLightingState.THINKING -> 0.15f
            EdgeLightingState.ACTIVATING -> 0.25f
            EdgeLightingState.IDLE -> 0f
        }
        flowPhase = (flowPhase + flowSpeed * dt) % 1f

        // Breath phase: subtle breathing for THINKING
        val breathSpeed = when (currentState) {
            EdgeLightingState.THINKING -> 0.8f
            EdgeLightingState.ACTIVATING -> 0.5f
            EdgeLightingState.IDLE -> 0f
            else -> 0.3f
        }
        breathPhase = (breathPhase + breathSpeed * dt) % 1f

        // Activation reveal: spring-like ramp over ~400ms
        if (currentState == EdgeLightingState.ACTIVATING) {
            revealProgress = (revealProgress + dt / 0.4f).coerceAtMost(1f)
            // Auto-advance to LISTENING once reveal is complete
            if (revealProgress >= 1f) {
                currentState = EdgeLightingState.LISTENING
            }
        } else if (currentState != EdgeLightingState.IDLE) {
            // Keep reveal at 1 when active
            revealProgress = revealProgress.coerceAtLeast(0f).let {
                if (currentState == EdgeLightingState.IDLE) 0f else 1f
            }
        } else {
            // IDLE: decay reveal smoothly
            revealProgress = (revealProgress - dt / 0.3f).coerceAtLeast(0f)
        }

        emit()
    }

    /**
     * Forces immediate transition to IDLE — used when screen turns off
     * or assistant is deactivated.
     */
    fun deactivate() {
        currentState = EdgeLightingState.IDLE
        isVoiceActive = false
        currentEnergy = 0f
        currentAmplitude = 0f
        envelopeEnergy = 0f
        emit()
    }

    fun isIdle(): Boolean = currentState == EdgeLightingState.IDLE && revealProgress < 0.01f

    private fun emit() {
        // Determine final energy based on state
        val finalEnergy = when (currentState) {
            EdgeLightingState.IDLE -> envelopeEnergy * revealProgress // Fade out
            EdgeLightingState.ACTIVATING -> envelopeEnergy * revealProgress.coerceIn(0f, 1f)
            EdgeLightingState.LISTENING -> envelopeEnergy
            EdgeLightingState.THINKING -> envelopeEnergy * 0.3f + 0.08f // Subtle baseline
            EdgeLightingState.SPEAKING -> envelopeEnergy
        }

        _params.value = EdgeLightingParams(
            state = currentState,
            energy = finalEnergy.coerceIn(0f, 1f),
            amplitude = currentAmplitude,
            flowPhase = flowPhase,
            breathPhase = breathPhase,
            revealProgress = revealProgress,
            isVoiceActive = isVoiceActive
        )
    }
}
