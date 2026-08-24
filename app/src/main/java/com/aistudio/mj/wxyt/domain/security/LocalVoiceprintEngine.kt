package com.aistudio.mj.wxyt.domain.security

import android.content.Context
import android.util.Base64
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Lightweight on-device acoustic voiceprint.
 *
 * This is intentionally a local, dependency-free matcher so Parent Mode has a
 * real enrollment/verification path in this build. It is not equivalent to a
 * dedicated anti-spoof speaker-recognition model and must not be treated as a
 * high-security biometric boundary.
 */
class LocalVoiceprintEngine(context: Context) : SpeakerVerificationEngine {
    private val prefs = context.getSharedPreferences("maya_voiceprint", Context.MODE_PRIVATE)
    private var template: FloatArray?
        get() = prefs.getString("template", null)?.let { decode(it) }
        set(value) {
            if (value == null) prefs.edit().remove("template").apply()
            else prefs.edit().putString("template", encode(value)).apply()
        }

    override val isOperational: Boolean get() = true

    override suspend fun enrollOwner(audioSamples: ShortArray): Boolean {
        if (audioSamples.size < 8_000) return false
        val features = VoiceprintFeatures.extract(audioSamples)
        if (features.all { it.isFinite() }) {
            template = features
            return true
        }
        return false
    }

    suspend fun addEnrollmentSample(audioSamples: ShortArray): Boolean {
        if (audioSamples.size < 8_000) return false
        val next = VoiceprintFeatures.extract(audioSamples)
        val old = template
        template = if (old == null) next else FloatArray(old.size) { i -> old[i] * 0.65f + next[i] * 0.35f }
        return true
    }

    override suspend fun verifyOwner(audioSamples: ShortArray, threshold: Float, antiSpoof: Boolean): SpeakerVerificationResult {
        val reference = template ?: return SpeakerVerificationResult(false, 0f, false)
        if (audioSamples.size < 6_400) return SpeakerVerificationResult(false, 0f, false)
        val current = VoiceprintFeatures.extract(audioSamples)
        val score = cosine(reference, current)
        return SpeakerVerificationResult(score >= threshold, score, spoofDetected = false)
    }

    override fun isEnrolled(): Boolean = template != null
    fun clear() { template = null }

    private fun encode(v: FloatArray): String = Base64.encodeToString(ByteArray(v.size * 4).also { out ->
        v.forEachIndexed { i, f -> java.nio.ByteBuffer.wrap(out, i * 4, 4).putFloat(f) }
    }, Base64.NO_WRAP)

    private fun decode(s: String): FloatArray? {
        return try {
            val b = Base64.decode(s, Base64.NO_WRAP)
            if (b.size % 4 != 0) return null
            FloatArray(b.size / 4) { i -> java.nio.ByteBuffer.wrap(b, i * 4, 4).float }
        } catch (_: Exception) {
            null
        }
    }

    private fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        return if (na <= 0 || nb <= 0) 0f else (dot / (sqrt(na) * sqrt(nb))).toFloat().coerceIn(-1f, 1f)
    }
}

private object VoiceprintFeatures {
    fun extract(samples: ShortArray): FloatArray {
        val n = samples.size.coerceAtMost(32_000)
        var sum = 0.0
        var sq = 0.0
        var zc = 0
        var prev = samples[0].toInt()
        for (i in 0 until n) {
            val x = samples[i].toInt()
            sum += abs(x)
            sq += x.toDouble() * x.toDouble()
            if ((x >= 0) != (prev >= 0)) zc++
            prev = x
        }
        val rms = sqrt(sq / n).toFloat() / 32768f
        val meanAbs = (sum / n).toFloat() / 32768f
        val zeroCross = zc.toFloat() / n

        // Compact spectral fingerprint using Goertzel energy at voice-relevant bands.
        val freqs = floatArrayOf(180f, 240f, 320f, 420f, 540f, 680f, 840f, 1020f, 1240f, 1500f, 1800f, 2150f, 2550f, 3000f, 3500f, 4100f)
        val spectral = FloatArray(freqs.size)
        for (i in freqs.indices) spectral[i] = goertzel(samples, n, freqs[i])
        val total = spectral.sum().coerceAtLeast(1e-6f)
        for (i in spectral.indices) spectral[i] /= total

        val out = FloatArray(19)
        out[0] = rms
        out[1] = meanAbs
        out[2] = zeroCross
        spectral.copyInto(out, 3)
        return normalize(out)
    }

    private fun goertzel(samples: ShortArray, n: Int, freq: Float): Float {
        val sampleRate = 16_000f
        val k = (0.5f + n * freq / sampleRate).toInt()
        val w = 2f * Math.PI.toFloat() * k / n
        val coeff = 2f * kotlin.math.cos(w)
        var s1 = 0f
        var s2 = 0f
        val step = if (n > 12_000) 2 else 1
        var i = 0
        while (i < n) {
            val s = samples[i].toFloat() / 32768f
            val s0 = s + coeff * s1 - s2
            s2 = s1
            s1 = s0
            i += step
        }
        return (s1 * s1 + s2 * s2 - coeff * s1 * s2).coerceAtLeast(0f)
    }

    private fun normalize(v: FloatArray): FloatArray {
        var norm = 0.0
        for (x in v) norm += x * x
        val d = sqrt(norm).toFloat().coerceAtLeast(1e-6f)
        return FloatArray(v.size) { i -> v[i] / d }
    }
}
