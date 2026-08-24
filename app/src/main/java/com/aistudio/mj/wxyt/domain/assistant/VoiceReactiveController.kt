package com.aistudio.mj.wxyt.domain.assistant

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Visual mode for the ORB animation state machine.
 * Synchronizes with the assistant's internal state.
 */
enum class OrbVisualMode {
    IDLE,               // Subtle breathing animation
    LISTENING,          // Calm, monitoring microphone
    USER_SPEAKING,      // Reactive to user's microphone amplitude
    THINKING,           // Voice-independent processing animation
    SPEAKING,           // Reactive to AI output audio
    EXECUTING,          // Subtle processing state for tool execution
    ERROR               // Error visual
}

/**
 * Voice-reactive state consumed by the ORB Composable.
 *
 * Architecture:
 *   Audio Engine → VoiceReactiveController → StateFlow<VoiceReactiveState> → ORB Composable → Animation
 *
 * The UI observes this state — no Compose drawing logic in the audio engine.
 */
data class VoiceReactiveState(
    val mode: OrbVisualMode = OrbVisualMode.IDLE,
    /** Normalized smoothed energy 0..1 — drives orb scale, glow, distortion. */
    val energy: Float = 0f,
    /** Raw normalized amplitude 0..1 (less processed, for finer effects). */
    val normalizedAmplitude: Float = 0f,
    /** True when voice activity is detected on the active source. */
    val isVoiceActive: Boolean = false
)

/**
 * VoiceReactiveController
 *
 * Responsibilities:
 *   - Audio source selection (microphone vs AI output)
 *   - RMS input from both VoiceEnergyAnalyzer instances
 *   - Noise filtering (delegated to analyzers)
 *   - Normalization (delegated to analyzers)
 *   - State switching based on assistant state and audio source priority
 *   - Animation intensity output
 *   - Input/output priority (AI speaking takes priority; user barge-in switches to mic)
 *   - Lifecycle management
 *
 * Priority logic:
 *   - AI Speaking → AI output audio controls ORB
 *   - User starts speaking (barge-in) → interrupt AI → microphone becomes active source
 *
 * The controller throttles StateFlow emissions to avoid excessive recompositions.
 * It emits at most ~60 times/second, and only when the state actually changes.
 */
class VoiceReactiveController {

    private val micAnalyzer = VoiceEnergyAnalyzer(sampleRate = 16000)
    private val aiAnalyzer = VoiceEnergyAnalyzer(sampleRate = 24000)

    private val _state = MutableStateFlow(VoiceReactiveState())
    val state: StateFlow<VoiceReactiveState> = _state.asStateFlow()

    // ---- Source priority ----
    private var activeSource: AudioSource = AudioSource.NONE
    private var lastEnergy: Float = 0f
    private var lastMode: OrbVisualMode = OrbVisualMode.IDLE

    // Throttling: minimum ms between emissions
    private var lastEmitTimeMs: Long = 0
    private val minEmitIntervalMs = 8L  // ~120fps cap, most devices run 60-90

    // Energy change threshold to emit (avoid noise-level jitter causing recomposition)
    private val emitDelta = 0.003f

    private enum class AudioSource { NONE, MICROPHONE, AI_OUTPUT }

    // ---- Cross-fade for source switching ----
    private var micEnergy: Float = 0f
    private var aiEnergy: Float = 0f
    private var sourceBlend: Float = 0f  // 0 = mic, 1 = AI

    /**
     * Called when the assistant state changes (IDLE, LISTENING, THINKING, SPEAKING, etc.)
     */
    fun onAssistantStateChanged(mjState: MJState) {
        val newMode = when (mjState) {
            MJState.DISCONNECTED -> OrbVisualMode.IDLE
            MJState.IDLE -> OrbVisualMode.IDLE
            MJState.WAKE_WORD_LISTENING -> OrbVisualMode.LISTENING
            MJState.ACTIVATING -> OrbVisualMode.THINKING
            MJState.CONNECTING -> OrbVisualMode.LISTENING
            MJState.LISTENING -> OrbVisualMode.LISTENING
            MJState.THINKING -> OrbVisualMode.THINKING
            MJState.SPEAKING -> OrbVisualMode.SPEAKING
            MJState.ERROR -> OrbVisualMode.ERROR
        }

        // Determine active source based on state
        when (newMode) {
            OrbVisualMode.SPEAKING -> {
                activeSource = AudioSource.AI_OUTPUT
                micAnalyzer.reset()  // reset mic analyzer while AI talks
            }
            OrbVisualMode.USER_SPEAKING, OrbVisualMode.LISTENING -> {
                // Will be set to USER_SPEAKING when mic detects voice
                activeSource = AudioSource.MICROPHONE
                aiAnalyzer.reset()
            }
            OrbVisualMode.THINKING, OrbVisualMode.EXECUTING -> {
                activeSource = AudioSource.NONE
            }
            else -> {
                activeSource = AudioSource.NONE
            }
        }

        updateState(newMode, lastEnergy, false)
    }

    /**
     * Feed microphone PCM data (16-bit LE byte array).
     * Called from AudioInputManager recording loop.
     */
    fun feedMicrophoneInput(pcm: ByteArray) {
        if (activeSource == AudioSource.NONE || activeSource == AudioSource.AI_OUTPUT) {
            // Even when AI is speaking, check for user barge-in
            val energy = micAnalyzer.analyze(pcm, 0, pcm.size)
            micEnergy = energy.smoothedEnergy

            // Barge-in detection: if user speaks loud enough during AI speech
            if (activeSource == AudioSource.AI_OUTPUT &&
                energy.isVoiceActive && energy.smoothedEnergy > 0.12f) {
                // User is interrupting — switch to microphone source
                activeSource = AudioSource.MICROPHONE
                sourceBlend = 0f
                updateState(OrbVisualMode.USER_SPEAKING, energy.smoothedEnergy, true)
                return
            }
            return
        }

        val energy = micAnalyzer.analyze(pcm, 0, pcm.size)
        micEnergy = energy.smoothedEnergy

        // Determine mode: LISTENING vs USER_SPEAKING
        val mode = if (energy.isVoiceActive || energy.smoothedEnergy > 0.01f) {
            OrbVisualMode.USER_SPEAKING
        } else {
            OrbVisualMode.LISTENING
        }

        maybeEmit(mode, energy.smoothedEnergy, energy.peakEnergy, energy.isVoiceActive)
    }

    /**
     * Feed microphone PCM data (ShortArray — convenience for AudioInputManager flow).
     */
    fun feedMicrophoneInput(samples: ShortArray, length: Int = samples.size) {
        if (activeSource == AudioSource.NONE || activeSource == AudioSource.AI_OUTPUT) {
            val energy = micAnalyzer.analyze(samples, length)
            micEnergy = energy.smoothedEnergy

            if (activeSource == AudioSource.AI_OUTPUT &&
                energy.isVoiceActive && energy.smoothedEnergy > 0.12f) {
                activeSource = AudioSource.MICROPHONE
                sourceBlend = 0f
                updateState(OrbVisualMode.USER_SPEAKING, energy.smoothedEnergy, true)
                return
            }
            return
        }

        val energy = micAnalyzer.analyze(samples, length)
        micEnergy = energy.smoothedEnergy

        val mode = if (energy.isVoiceActive || energy.smoothedEnergy > 0.01f) {
            OrbVisualMode.USER_SPEAKING
        } else {
            OrbVisualMode.LISTENING
        }

        maybeEmit(mode, energy.smoothedEnergy, energy.peakEnergy, energy.isVoiceActive)
    }

    /**
     * Feed AI output PCM data (16-bit LE byte array).
     * Called from AudioOutputManager playback path.
     */
    fun feedAIOutput(pcm: ByteArray) {
        if (activeSource == AudioSource.MICROPHONE) return

        val energy = aiAnalyzer.analyze(pcm, 0, pcm.size)
        aiEnergy = energy.smoothedEnergy

        if (activeSource == AudioSource.AI_OUTPUT) {
            maybeEmit(OrbVisualMode.SPEAKING, energy.smoothedEnergy, energy.peakEnergy, energy.isVoiceActive)
        }
    }

    /**
     * Called when tool execution starts (EXECUTING mode).
     */
    fun onToolExecutionStart() {
        updateState(OrbVisualMode.EXECUTING, 0f, false)
    }

    fun onToolExecutionEnd() {
        updateState(OrbVisualMode.LISTENING, 0f, false)
    }

    private fun maybeEmit(mode: OrbVisualMode, energy: Float, amplitude: Float, isVoiceActive: Boolean) {
        val now = System.currentTimeMillis()
        val timeSinceLastEmit = now - lastEmitTimeMs

        // Emit if: enough time passed, OR significant energy change, OR mode changed
        val energyChanged = kotlin.math.abs(energy - lastEnergy) > emitDelta
        val modeChanged = mode != lastMode

        if (timeSinceLastEmit >= minEmitIntervalMs && (energyChanged || modeChanged || timeSinceLastEmit > 50)) {
            updateState(mode, energy, isVoiceActive)
            // Store amplitude separately — it's passed in the state
            _state.value = _state.value.copy(normalizedAmplitude = amplitude)
            lastEmitTimeMs = now
        }
    }

    private fun updateState(mode: OrbVisualMode, energy: Float, isVoiceActive: Boolean) {
        val clampedEnergy = energy.coerceIn(0f, 1f)
        _state.value = VoiceReactiveState(
            mode = mode,
            energy = clampedEnergy,
            normalizedAmplitude = clampedEnergy,
            isVoiceActive = isVoiceActive
        )
        lastEnergy = clampedEnergy
        lastMode = mode
    }

    fun reset() {
        micAnalyzer.reset()
        aiAnalyzer.reset()
        micEnergy = 0f
        aiEnergy = 0f
        sourceBlend = 0f
        activeSource = AudioSource.NONE
        lastEnergy = 0f
        lastMode = OrbVisualMode.IDLE
        _state.value = VoiceReactiveState()
    }
}
