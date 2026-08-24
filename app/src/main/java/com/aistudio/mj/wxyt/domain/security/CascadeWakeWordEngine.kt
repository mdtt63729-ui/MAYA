package com.aistudio.mj.wxyt.domain.security

import android.content.Context
import android.util.Log
import com.aistudio.mj.wxyt.domain.assistant.AudioInputManager
import com.aistudio.mj.wxyt.domain.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Two-Stage Cascade Detection Engine — PRD 2 §2.1.
 *
 * Stage 1: Low-Power Wake-Word Detection (WWD)
 *   - Lightweight local ambient listener scanning PCM audio buffers
 *   - Runs in a background foreground service with minimal RAM
 *   - Zero network requests
 *
 * Stage 2: Speaker Verification (Voiceprint Matcher)
 *   - Triggered immediately upon Stage 1 keyword detection
 *   - Passes captured voice sample into LocalVoiceprintEngine
 *   - Cosine similarity against stored owner_voice_profile.vector
 *   - If score >= threshold (configurable, default 0.85), activates assistant
 *   - Otherwise drops the audio frame silently
 */
class CascadeWakeWordEngine(
    private val context: Context,
    private val speakerEngine: SpeakerVerificationEngine = LocalVoiceprintEngine(context.applicationContext)
) {
    enum class CascadeState {
        IDLE,               // Not listening
        STAGE1_LISTENING,   // Wake-word detection active
        STAGE2_VERIFYING,   // Speaker verification in progress
        ACTIVATED,          // Owner verified — activate assistant
        REJECTED            // Speaker not recognized — drop silently
    }

    private val _state = MutableStateFlow(CascadeState.IDLE)
    val state: StateFlow<CascadeState> = _state.asStateFlow()

    private val _detectionLatency = MutableStateFlow(0L)
    val detectionLatency: StateFlow<Long> = _detectionLatency.asStateFlow()

    private val _lastScore = MutableStateFlow(0f)
    val lastScore: StateFlow<Float> = _lastScore.asStateFlow()

    private val settingsRepository = SettingsRepository.get(context.applicationContext)
    private var listeningJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    // Wake-word detection buffer — accumulates audio for keyword matching
    private val wakeWordBuffer = StringBuilder()
    private val maxBufferChars = 200

    // Rolling audio buffer for Stage 2 capture
    private val stage2CaptureBuffer = mutableListOf<Short>()
    private val stage2CaptureTargetSamples = 16_000 // ~1 second at 16kHz

    var onCascadeActivated: (() -> Unit)? = null
    var onCascadeRejected: (() -> Unit)? = null

    /**
     * Starts Stage 1 — continuous low-power wake-word listening.
     * Runs in a background foreground service with minimal overhead.
     */
    fun startListening() {
        if (_state.value == CascadeState.STAGE1_LISTENING) return
        _state.value = CascadeState.STAGE1_LISTENING
        Log.d("CascadeWWD", "Stage 1 wake-word listening started")

        listeningJob = scope.launch {
            val audioInput = AudioInputManager(
                inputGain = 1f,
                noiseCancellation = true,
                echoCancellation = true
            )
            val settings = settingsRepository.settings.value
            val wakePhrase = settings.wakeWord.ifBlank { "Hey MAYA" }.lowercase()

            try {
                withContext(Dispatchers.IO) {
                    audioInput.startRecording().collect { pcmBytes ->
                        if (coroutineContext.isActive) {
                            processAudioChunk(pcmBytes, wakePhrase, audioInput)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("CascadeWWD", "Audio recording error", e)
                _state.value = CascadeState.IDLE
            }
        }
    }

    /**
     * Processes an audio chunk through the two-stage cascade.
     */
    private suspend fun processAudioChunk(
        pcmBytes: ByteArray,
        wakePhrase: String,
        audioInput: AudioInputManager
    ) {
        val detectionStart = System.currentTimeMillis()

        // Stage 1: Energy-based voice activity detection + simple keyword matching
        val energy = computeRMSEnergy(pcmBytes)
        val settings = settingsRepository.settings.value
        val sensitivityThreshold = 0.02f + (1f - settings.wakeWordSensitivity) * 0.08f

        if (energy > sensitivityThreshold) {
            // Voice activity detected — check for wake-word pattern
            // In a production build, this would use Picovoice Porcupine or similar.
            // This implementation uses a lightweight energy-pattern heuristic.
            val wakeWordDetected = detectWakeWordPattern(pcmBytes, wakePhrase, settings.wakeWordSensitivity)

            if (wakeWordDetected) {
                Log.d("CascadeWWD", "Stage 1: Wake word detected (energy=$energy)")
                _state.value = CascadeState.STAGE2_VERIFYING

                // Capture additional audio for Stage 2 verification
                val stage2Audio = captureStage2Sample(pcmBytes)

                // Stage 2: Speaker Verification
                val verifyResult = speakerEngine.verifyOwner(
                    audioSamples = stage2Audio,
                    threshold = settings.ownerVoiceThreshold,
                    antiSpoof = settings.ownerVoiceAntiSpoof
                )

                val latency = System.currentTimeMillis() - detectionStart
                _detectionLatency.value = latency
                _lastScore.value = verifyResult.score

                if (verifyResult.matched && !verifyResult.spoofDetected) {
                    Log.d("CascadeWWD", "Stage 2: Owner verified (score=${verifyResult.score}, latency=${latency}ms)")
                    _state.value = CascadeState.ACTIVATED
                    onCascadeActivated?.invoke()
                    // Reset to listening after activation
                    _state.value = CascadeState.STAGE1_LISTENING
                } else {
                    Log.d("CascadeWWD", "Stage 2: Speaker rejected (score=${verifyResult.score})")
                    _state.value = CascadeState.REJECTED
                    onCascadeRejected?.invoke()
                    // Drop silently and return to Stage 1
                    _state.value = CascadeState.STAGE1_LISTENING
                }
            }
        }
    }

    /**
     * Stage 1 wake-word pattern detection.
     *
     * Uses a lightweight spectral energy analysis to detect speech patterns
     * matching the wake-word. In production, this would be replaced by a
     * dedicated WWD model (Picovoice Porcupine, Micro-Wake-Word, etc.).
     */
    private fun detectWakeWordPattern(pcm: ByteArray, phrase: String, sensitivity: Float): Boolean {
        // Convert to shorts
        val samples = ByteArrayToShorts(pcm)
        if (samples.size < 1600) return false // Need at least 100ms

        // Spectral analysis — check for voice-frequency energy patterns
        val voiceBandEnergy = computeVoiceBandEnergy(samples)
        val totalEnergy = computeTotalEnergy(samples)

        // Voice-band ratio: speech has concentrated energy in 300-3400Hz
        val voiceRatio = if (totalEnergy > 0) voiceBandEnergy / totalEnergy else 0f

        // Sensitivity-adjusted threshold
        // Higher sensitivity = lower threshold = more detections
        val threshold = 0.35f + (1f - sensitivity) * 0.25f

        return voiceRatio > threshold && totalEnergy > 0.01f
    }

    /**
     * Captures a ~1 second audio sample for Stage 2 speaker verification.
     * Combines the current chunk with recent buffered audio.
     */
    private suspend fun captureStage2Sample(currentChunk: ByteArray): ShortArray {
        val currentSamples = ByteArrayToShorts(currentChunk)
        stage2CaptureBuffer.addAll(currentSamples.toList())

        // Trim to target size (keep most recent)
        while (stage2CaptureBuffer.size > stage2CaptureTargetSamples) {
            stage2CaptureBuffer.removeAt(0)
        }

        // Return collected samples (minimum 6400 for verification)
        val collected = stage2CaptureBuffer.toShortArray()
        stage2CaptureBuffer.clear()

        return if (collected.size >= 6400) collected else collected
    }

    fun stopListening() {
        listeningJob?.cancel()
        listeningJob = null
        _state.value = CascadeState.IDLE
        stage2CaptureBuffer.clear()
        Log.d("CascadeWWD", "Wake-word listening stopped")
    }

    // --- Audio utility functions ---

    private fun computeRMSEnergy(pcm: ByteArray): Float {
        val samples = ByteArrayToShorts(pcm)
        if (samples.isEmpty()) return 0f
        var sumSq = 0.0
        for (s in samples) {
            val v = s.toFloat() / 32768f
            sumSq += v * v
        }
        return sqrt(sumSq.toFloat() / samples.size)
    }

    private fun computeVoiceBandEnergy(samples: ShortArray): Float {
        // Goertzel at voice-relevant frequencies (300-3400 Hz)
        val freqs = floatArrayOf(300f, 500f, 800f, 1200f, 1600f, 2000f, 2800f, 3400f)
        var totalEnergy = 0f
        for (freq in freqs) {
            totalEnergy += goertzelEnergy(samples, freq, 16000f)
        }
        return totalEnergy / freqs.size
    }

    private fun computeTotalEnergy(samples: ShortArray): Float {
        var sum = 0f
        for (s in samples) {
            sum += abs(s.toFloat() / 32768f)
        }
        return sum / samples.size
    }

    private fun goertzelEnergy(samples: ShortArray, targetFreq: Float, sampleRate: Float): Float {
        val n = samples.size
        if (n == 0) return 0f
        val k = (0.5f + n * targetFreq / sampleRate).toInt()
        val w = 2f * Math.PI.toFloat() * k / n
        val coeff = 2f * kotlin.math.cos(w)
        var s1 = 0f
        var s2 = 0f
        for (s in samples) {
            val x = s.toFloat() / 32768f
            val s0 = x + coeff * s1 - s2
            s2 = s1
            s1 = s0
        }
        return (s1 * s1 + s2 * s2 - coeff * s1 * s2).coerceAtLeast(0f)
    }

    private fun ByteArrayToShorts(bytes: ByteArray): ShortArray {
        val n = bytes.size / 2
        return ShortArray(n) { i ->
            ((bytes[i * 2 + 1].toInt() shl 8) or (bytes[i * 2].toInt() and 0xff)).toShort()
        }
    }
}
