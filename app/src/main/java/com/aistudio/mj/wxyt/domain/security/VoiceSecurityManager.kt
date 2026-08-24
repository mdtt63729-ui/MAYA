package com.aistudio.mj.wxyt.domain.security

import android.content.Context
import android.util.Log
import com.aistudio.mj.wxyt.accessibility.ORBAccessibilityService
import com.aistudio.mj.wxyt.domain.settings.SettingsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SecurityState {
    IDLE,       // Waiting for Wake Word
    VERIFYING,  // Wake Word detected, checking voice biometrics
    AUTHORIZED, // Voice matched, AI session active
    LOCKED      // Device locked due to violations
}

/**
 * Voice Security Manager — ported from Maya.
 * Handles voice authentication, profiles (max 5 slots), and security violation logic.
 *
 * Verification flow:
 *   Wake Word → Voice Verification → Owner recognized → AUTHORIZED → Command execution enabled
 *   Unauthorized voice → command execution blocked
 *
 * Violation system:
 *   1st failed verification → warning message
 *   2nd consecutive failed verification → device lock via GLOBAL_ACTION_LOCK_SCREEN
 */
class VoiceSecurityManager(private val context: Context) {
    private val _securityState = MutableStateFlow(SecurityState.IDLE)
    val securityState: StateFlow<SecurityState> = _securityState.asStateFlow()

    private var violationCount = 0
    private val settingsRepository = SettingsRepository.get(context.applicationContext)

    var onWarningAudioOut: ((String) -> Unit)? = null

    // Voice profiles — maximum 5 slots (Slot 1 = Owner)
    private val _profiles = MutableStateFlow(List(5) {
        VoiceProfile(
            slotIndex = it,
            name = if (it == 0) "Owner (Slot 1)" else "Slot ${it + 1}",
            isEnrolled = false
        )
    })
    val profiles: StateFlow<List<VoiceProfile>> = _profiles.asStateFlow()

    // Device-local acoustic voiceprint. This gives Parent Mode a real enrollment
    // and matching path without trusting SpeechRecognizer text as speaker identity.
    // It is not a substitute for a dedicated anti-spoof neural speaker model.
    var speakerEngine: SpeakerVerificationEngine = LocalVoiceprintEngine(context.applicationContext)

    fun onWakeWordDetected() {
        if (_securityState.value == SecurityState.IDLE) {
            Log.d("VoiceSecurityManager", "Wake word detected. Transitioning to VERIFYING.")
            _securityState.value = SecurityState.VERIFYING
        }
    }

    fun canAuthorizeOwner(): Boolean = speakerEngine.isOperational && speakerEngine.isEnrolled() && settingsRepository.settings.value.ownerVoiceEnrolled

    suspend fun enrollOwner(audioData: ShortArray): Boolean {
        val ok = speakerEngine.enrollOwner(audioData)
        if (ok) {
            val currentProfiles = _profiles.value.toMutableList()
            currentProfiles[0] = currentProfiles[0].copy(isEnrolled = true)
            _profiles.value = currentProfiles
            settingsRepository.updateSettings(settingsRepository.settings.value.copy(ownerVoiceEnrolled = true, ownerVoiceEnabled = true))
        }
        return ok
    }

    fun enrollProfile(slotIndex: Int) {
        val currentProfiles = _profiles.value.toMutableList()
        currentProfiles[slotIndex] = currentProfiles[slotIndex].copy(isEnrolled = true)
        _profiles.value = currentProfiles
        Log.d("VoiceSecurityManager", "Enrolled profile at slot $slotIndex")
    }

    fun deleteProfile(slotIndex: Int) {
        val currentProfiles = _profiles.value.toMutableList()
        currentProfiles[slotIndex] = currentProfiles[slotIndex].copy(isEnrolled = false)
        _profiles.value = currentProfiles
    }

    suspend fun verifyVoice(audioData: ShortArray) {
        if (_securityState.value != SecurityState.VERIFYING) return

        // In a real implementation, VoiceBiometricsEngine would process this.
        delay(500) // Simulating processing time

        val settings = settingsRepository.settings.value

        // In normal mode there is no speaker restriction.
        if (!settings.parentMode) {
            resetViolations()
            _securityState.value = SecurityState.AUTHORIZED
            return
        }

        // Parent Mode requires an enrolled owner profile. Android SpeechRecognizer
        // itself is not a secure speaker-biometric engine; a production verifier
        // must supply the real match result here.
        if (!settings.ownerVoiceEnrolled || !_profiles.value[0].isEnrolled) {
            handleViolation()
            return
        }

        val result = speakerEngine.verifyOwner(
            audioSamples = audioData,
            threshold = settings.ownerVoiceThreshold,
            antiSpoof = settings.ownerVoiceAntiSpoof
        )
        if (result.matched && !result.spoofDetected && result.score >= settings.ownerVoiceThreshold) {
            Log.d("VoiceSecurityManager", "Owner voice verified score=${result.score}")
            resetViolations()
            _securityState.value = SecurityState.AUTHORIZED
        } else {
            Log.d("VoiceSecurityManager", "Owner voice rejected score=${result.score} spoof=${result.spoofDetected}")
            handleViolation()
        }
    }

    private fun handleViolation() {
        val settings = settingsRepository.settings.value
        val lockThreshold = settings.lockAfterUnauthorizedAttempts.coerceIn(2, 5)
        if (violationCount < lockThreshold - 1) {
            violationCount += 1
            Log.d("VoiceSecurityManager", "Attempt 1 Violation: Warning issued.")
            if (settings.unauthorizedWarningEnabled) {
                onWarningAudioOut?.invoke(
                    "This is not my owner's voice. I will not execute your command. Please use the enrolled owner's voice."
                )
            }
            _securityState.value = SecurityState.IDLE
        } else {
            violationCount = lockThreshold
            Log.d("VoiceSecurityManager", "Unauthorized voice threshold reached.")
            if (settings.lockOnUnauthorizedVoice) lockDevice()
            resetViolations()
            _securityState.value = SecurityState.LOCKED
        }
    }

    fun endSession() {
        if (_securityState.value == SecurityState.AUTHORIZED) {
            _securityState.value = SecurityState.IDLE
        }
    }

    private fun resetViolations() {
        violationCount = 0
    }

    private fun lockDevice() {
        val service = ORBAccessibilityService.instance
        if (service != null) {
            service.performGlobalAction(
                android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
            )
        } else {
            Log.e("VoiceSecurityManager", "Accessibility service not running, cannot lock screen.")
        }
    }
}
