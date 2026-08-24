package com.aistudio.mj.wxyt.domain.security

/**
 * Pluggable speaker-verification boundary.
 * A production on-device embedding/anti-spoof model should implement this interface.
 * The default implementation deliberately fails closed instead of trusting SpeechRecognizer.
 */
interface SpeakerVerificationEngine {
    /** True only when a real speaker-embedding + anti-spoof implementation is installed. */
    val isOperational: Boolean get() = false
    suspend fun enrollOwner(audioSamples: ShortArray): Boolean
    suspend fun verifyOwner(audioSamples: ShortArray, threshold: Float, antiSpoof: Boolean): SpeakerVerificationResult
    fun isEnrolled(): Boolean
}

data class SpeakerVerificationResult(
    val matched: Boolean,
    val score: Float,
    val spoofDetected: Boolean = false
)

class FailClosedSpeakerVerificationEngine : SpeakerVerificationEngine {
    private val enrolled = false
    override suspend fun enrollOwner(audioSamples: ShortArray): Boolean {
        // Never claim that an owner voice has been enrolled without a real
        // speaker-embedding/anti-spoof implementation.
        return false
    }

    override suspend fun verifyOwner(audioSamples: ShortArray, threshold: Float, antiSpoof: Boolean): SpeakerVerificationResult {
        return SpeakerVerificationResult(matched = false, score = 0f, spoofDetected = false)
    }

    override fun isEnrolled(): Boolean = enrolled
}
