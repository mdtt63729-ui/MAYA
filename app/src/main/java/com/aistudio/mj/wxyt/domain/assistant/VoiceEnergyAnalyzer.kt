package com.aistudio.mj.wxyt.domain.assistant

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Result of analyzing a single PCM audio frame.
 *
 * @param rms           Root-mean-square amplitude (0..1 normalized)
 * @param peakEnergy    Peak absolute sample value (0..1 normalized)
 * @param isVoiceActive True if the frame contains speech above noise floor
 * @param smoothedEnergy Low-pass-filtered energy for animation use (0..1)
 */
data class VoiceEnergy(
    val rms: Float,
    val peakEnergy: Float,
    val isVoiceActive: Boolean,
    val smoothedEnergy: Float
)

/**
 * VoiceEnergyAnalyzer — shared reusable analyzer for both microphone input
 * and AI audio output.
 *
 * Pipeline:
 *   PCM samples → RMS calculation → Amplitude normalization →
 *   Noise floor subtraction → Dynamic range mapping → Smoothing → VoiceEnergy
 *
 * Features:
 *   - RMS and peak amplitude
 *   - Adaptive noise floor estimation
 *   - Voice activity detection with hysteresis
 *   - Attack / release smoothing (fast attack, smooth release)
 *   - Low-pass filtering for jitter-free animation values
 *
 * This class is NOT thread-safe. Use one instance per audio source, or
 * synchronize access if sharing.
 */
class VoiceEnergyAnalyzer(
    /** Sample rate of the PCM data (e.g. 16000 for mic, 24000 for AI output). */
    private val sampleRate: Int = 16000,
    sensitivity: Float = 0.55f
) {
    // ---- Noise floor estimation (adaptive) ----
    private var noiseFloor: Float = 0.01f
    private val noiseFloorAlpha = 0.002f  // slow adaptation

    // ---- Smoothing state ----
    private var smoothedEnergy: Float = 0f

    // Attack: fast response when voice starts (0.3 = ~30ms to reach target at 60fps)
    // Release: slow decay when voice stops (0.08 = ~300ms to settle)
    private val attackAlpha = 0.35f
    private val releaseAlpha = 0.06f

    // ---- Voice activity detection with hysteresis ----
    private var isVoiceActive: Boolean = false
    private val voiceOnThreshold = (0.028f - sensitivity.coerceIn(0f, 1f) * 0.020f).coerceIn(0.006f, 0.028f)
    private val voiceOffThreshold = (voiceOnThreshold * 0.55f).coerceIn(0.003f, 0.016f)
    private var silenceHoldMs: Long = 0
    private val minHoldMs = (170L - sensitivity.coerceIn(0f, 1f) * 90L).toLong().coerceIn(70L, 170L)  // hold voice-active state for 120ms to avoid flicker

    // ---- Minimum frames for stabilization ----
    private var frameCount: Long = 0

    /**
     * Analyze a PCM 16-bit audio frame.
     *
     * @param pcm    16-bit PCM samples (little-endian byte array)
     * @param offset Start offset in the byte array
     * @param length Number of bytes to analyze
     * @return VoiceEnergy for this frame
     */
    fun analyze(pcm: ByteArray, offset: Int, length: Int): VoiceEnergy {
        val numSamples = length / 2
        if (numSamples <= 0) {
            return VoiceEnergy(0f, 0f, false, smoothedEnergy)
        }

        // ---- Step 1: Calculate RMS and peak from 16-bit samples ----
        var sumSquares = 0.0
        var peakSample: Short = 0

        for (i in 0 until numSamples) {
            val byteIdx = offset + i * 2
            if (byteIdx + 1 >= pcm.size) break

            // Little-endian 16-bit signed sample
            val low = pcm[byteIdx].toInt() and 0xFF
            val high = pcm[byteIdx + 1].toInt()
            val sample = (low or (high shl 8)).toShort()

            sumSquares += sample.toDouble() * sample.toDouble()
            val absSample = if (sample < 0) (-sample).toInt() else sample.toInt()
            if (absSample > kotlin.math.abs(peakSample.toInt())) {
                peakSample = sample
            }
        }

        val rmsValue = sqrt(sumSquares / numSamples).toFloat()
        val peakAbs = kotlin.math.abs(peakSample.toInt()).toFloat()

        // ---- Step 2: Normalize to 0..1 range ----
        // 16-bit max = 32767. Normalize RMS and peak.
        val normalizedRms = (rmsValue / 32767f).coerceIn(0f, 1f)
        val normalizedPeak = (peakAbs / 32767f).coerceIn(0f, 1f)

        // ---- Step 3: Noise floor estimation (adaptive) ----
        // Noise floor slowly tracks the minimum energy level
        if (normalizedRms < noiseFloor) {
            noiseFloor = noiseFloor * (1f - noiseFloorAlpha) + normalizedRms * noiseFloorAlpha
        } else {
            // Noise floor slowly decays upward when there's signal
            noiseFloor = noiseFloor * (1f - noiseFloorAlpha * 0.5f) + normalizedRms * (noiseFloorAlpha * 0.5f)
        }
        noiseFloor = noiseFloor.coerceIn(0.001f, 0.1f)

        // ---- Step 4: Subtract noise floor ----
        val speechEnergy = max(0f, normalizedRms - noiseFloor)

        // ---- Step 5: Dynamic range mapping (compress/expand) ----
        // Apply a curve that emphasizes mid-range speech:
        //   sqrt makes quiet speech more visible, but caps loud speech
        val mappedEnergy = sqrt(speechEnergy)

        // ---- Step 6: Voice activity detection with hysteresis ----
        frameCount++
        val frameDurationMs = (numSamples.toLong() * 1000L) / sampleRate

        if (!isVoiceActive && mappedEnergy > voiceOnThreshold) {
            isVoiceActive = true
            silenceHoldMs = 0
        } else if (isVoiceActive && mappedEnergy < voiceOffThreshold) {
            silenceHoldMs += frameDurationMs
            if (silenceHoldMs > minHoldMs) {
                isVoiceActive = false
            }
        } else if (isVoiceActive) {
            silenceHoldMs = 0  // reset hold timer if energy is still above off-threshold
        }

        // ---- Step 7: Smoothing (attack/release + low-pass) ----
        val target = mappedEnergy
        val alpha = if (target > smoothedEnergy) attackAlpha else releaseAlpha
        smoothedEnergy = smoothedEnergy + (target - smoothedEnergy) * alpha
        smoothedEnergy = smoothedEnergy.coerceIn(0f, 1f)

        return VoiceEnergy(
            rms = normalizedRms,
            peakEnergy = normalizedPeak,
            isVoiceActive = isVoiceActive,
            smoothedEnergy = smoothedEnergy
        )
    }

    /**
     * Analyze a ShortArray PCM frame (convenience for microphone data).
     */
    fun analyze(samples: ShortArray, length: Int = samples.size): VoiceEnergy {
        val numSamples = min(length, samples.size)
        if (numSamples <= 0) {
            return VoiceEnergy(0f, 0f, false, smoothedEnergy)
        }

        var sumSquares = 0.0
        var peakAbs: Int = 0

        for (i in 0 until numSamples) {
            val sample = samples[i]
            sumSquares += sample.toDouble() * sample.toDouble()
            val absSample = if (sample < 0) -sample.toInt() else sample.toInt()
            if (absSample > peakAbs) peakAbs = absSample
        }

        val rmsValue = sqrt(sumSquares / numSamples).toFloat()
        val normalizedRms = (rmsValue / 32767f).coerceIn(0f, 1f)
        val normalizedPeak = (peakAbs.toFloat() / 32767f).coerceIn(0f, 1f)

        // Noise floor
        if (normalizedRms < noiseFloor) {
            noiseFloor = noiseFloor * (1f - noiseFloorAlpha) + normalizedRms * noiseFloorAlpha
        } else {
            noiseFloor = noiseFloor * (1f - noiseFloorAlpha * 0.5f) + normalizedRms * (noiseFloorAlpha * 0.5f)
        }
        noiseFloor = noiseFloor.coerceIn(0.001f, 0.1f)

        val speechEnergy = max(0f, normalizedRms - noiseFloor)
        val mappedEnergy = sqrt(speechEnergy)

        // VAD hysteresis
        frameCount++
        val frameDurationMs = (numSamples.toLong() * 1000L) / sampleRate

        if (!isVoiceActive && mappedEnergy > voiceOnThreshold) {
            isVoiceActive = true
            silenceHoldMs = 0
        } else if (isVoiceActive && mappedEnergy < voiceOffThreshold) {
            silenceHoldMs += frameDurationMs
            if (silenceHoldMs > minHoldMs) {
                isVoiceActive = false
            }
        } else if (isVoiceActive) {
            silenceHoldMs = 0
        }

        // Smoothing
        val target = mappedEnergy
        val alpha = if (target > smoothedEnergy) attackAlpha else releaseAlpha
        smoothedEnergy = smoothedEnergy + (target - smoothedEnergy) * alpha
        smoothedEnergy = smoothedEnergy.coerceIn(0f, 1f)

        return VoiceEnergy(
            rms = normalizedRms,
            peakEnergy = normalizedPeak,
            isVoiceActive = isVoiceActive,
            smoothedEnergy = smoothedEnergy
        )
    }

    fun reset() {
        noiseFloor = 0.01f
        smoothedEnergy = 0f
        isVoiceActive = false
        silenceHoldMs = 0
        frameCount = 0
    }
}
