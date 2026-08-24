package com.aistudio.mj.wxyt.domain.security

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.aistudio.mj.wxyt.domain.assistant.AudioInputManager
import com.aistudio.mj.wxyt.domain.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Enhanced Owner Voice Enrollment Controller — PRD 2 §3.2.
 *
 * Guided setup requiring 3 to 5 multi-pitch phrase recordings.
 * Includes:
 * - Background noise level calibration during sampling
 * - On-device feature extraction via LocalVoiceprintEngine
 * - AES-256 encrypted storage of embedding vector (owner_voice_profile.vector)
 *   in private storage (handled by EncryptedSharedPreferences in SecureCredentialRepository)
 */
class EnhancedVoiceEnrollmentController(private val context: Context) {

    enum class Phase { IDLE, CALIBRATING, RECORDING, PROCESSING, COMPLETE, ERROR }
    enum class EnrollmentStep { NOISE_CALIBRATION, PHRASE_1, PHRASE_2, PHRASE_3, PHRASE_4, PHRASE_5, COMPLETE }

    private val _phase = MutableStateFlow(Phase.IDLE)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    private val _currentStep = MutableStateFlow(EnrollmentStep.NOISE_CALIBRATION)
    val currentStep: StateFlow<EnrollmentStep> = _currentStep.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message.asStateFlow()

    private val _noiseLevel = MutableStateFlow(0f)
    val noiseLevel: StateFlow<Float> = _noiseLevel.asStateFlow()

    private val _enrollmentCount = MutableStateFlow(0)
    val enrollmentCount: StateFlow<Int> = _enrollmentCount.asStateFlow()

    private val engine = LocalVoiceprintEngine(context.applicationContext)
    private val settingsRepository = SettingsRepository.get(context.applicationContext)
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main.immediate)

    // Multi-pitch enrollment phrases — PRD 2 §3.2
    val phrases = listOf(
        "Hey MAYA, are you listening?",
        "MAYA, what's the weather tomorrow?",
        "MAYA, open my assistant.",
        "MAYA, set a timer for five minutes.",
        "MAYA, play my favorite song."
    )

    val requiredSamples = 3
    val maxSamples = 5

    /**
     * Step 1: Background noise calibration.
     * Records ambient audio for 1 second to establish a noise baseline.
     */
    fun startNoiseCalibration() {
        if (_phase.value == Phase.RECORDING || _phase.value == Phase.PROCESSING) return
        if (!hasMicrophonePermission()) {
            _phase.value = Phase.ERROR
            _message.value = "Microphone permission is required."
            return
        }

        job?.cancel()
        job = scope.launch {
            _currentStep.value = EnrollmentStep.NOISE_CALIBRATION
            _phase.value = Phase.CALIBRATING
            _message.value = "Calibrating background noise level..."
            _progress.value = 0f

            val audio = AudioInputManager(1f, true, true)
            val out = ByteArrayOutputStream()
            val started = System.currentTimeMillis()
            val calibrationDuration = 1000L

            try {
                withContext(Dispatchers.IO) {
                    audio.startRecording().collect { chunk ->
                        out.write(chunk)
                        _progress.value = ((System.currentTimeMillis() - started) / calibrationDuration.toFloat()).coerceIn(0f, 1f)
                        if (System.currentTimeMillis() - started >= calibrationDuration) {
                            throw StopEnrollmentCollection()
                        }
                    }
                }
            } catch (_: StopEnrollmentCollection) {
                // Expected
            } catch (t: Throwable) {
                _phase.value = Phase.ERROR
                _message.value = t.message ?: "Calibration failed."
                return@launch
            } finally {
                audio.stopRecording()
            }

            val pcm = bytesToShorts(out.toByteArray())
            val noiseRMS = computeRMS(pcm)
            _noiseLevel.value = noiseRMS

            // Warn if environment is too noisy
            if (noiseRMS > 0.15f) {
                _message.value = "High background noise detected (level: ${String.format("%.2f", noiseRMS)}). " +
                    "Try moving to a quieter location for best results."
            } else {
                _message.value = "Calibration complete. Background noise is acceptable."
            }

            _phase.value = Phase.IDLE
            _currentStep.value = EnrollmentStep.PHRASE_1
        }
    }

    /**
     * Records a single enrollment phrase sample.
     * @param index Phrase index (0-based)
     */
    fun startSample(index: Int) {
        if (_phase.value == Phase.RECORDING || _phase.value == Phase.PROCESSING) return
        if (!hasMicrophonePermission()) {
            _phase.value = Phase.ERROR
            _message.value = "Microphone permission is required."
            return
        }

        job?.cancel()
        job = scope.launch {
            _phase.value = Phase.RECORDING
            _progress.value = 0f
            val phrase = phrases.getOrElse(index) { phrases.last() }
            _message.value = "Say: \"$phrase\""
            _currentStep.value = EnrollmentStep.values().getOrNull(index + 1) ?: EnrollmentStep.PHRASE_1

            val audio = AudioInputManager(1f, true, true)
            val out = ByteArrayOutputStream()
            val started = System.currentTimeMillis()
            val recordingDuration = 2200L

            try {
                withContext(Dispatchers.IO) {
                    audio.startRecording().collect { chunk ->
                        out.write(chunk)
                        _progress.value = ((System.currentTimeMillis() - started) / recordingDuration.toFloat()).coerceIn(0f, 1f)
                        if (System.currentTimeMillis() - started >= recordingDuration) {
                            throw StopEnrollmentCollection()
                        }
                    }
                }
            } catch (_: StopEnrollmentCollection) {
                // Expected
            } catch (t: Throwable) {
                _phase.value = Phase.ERROR
                _message.value = t.message ?: "Unable to record voice sample."
                return@launch
            } finally {
                audio.stopRecording()
            }

            _phase.value = Phase.PROCESSING
            val pcm = bytesToShorts(out.toByteArray())

            // Check if sample is above noise floor
            val sampleRMS = computeRMS(pcm)
            if (sampleRMS < _noiseLevel.value + 0.02f) {
                _phase.value = Phase.ERROR
                _message.value = "Sample too quiet. Please speak louder and try again."
                return@launch
            }

            val ok = withContext(Dispatchers.Default) {
                if (index == 0) engine.enrollOwner(pcm) else engine.addEnrollmentSample(pcm)
            }

            if (!ok) {
                _phase.value = Phase.ERROR
                _message.value = "The sample was too short or too noisy. Please try again."
                return@launch
            }

            _progress.value = 1f
            _enrollmentCount.value = index + 1

            if (index + 1 >= requiredSamples) {
                // Mark enrollment as complete in settings
                settingsRepository.updateSettings(
                    settingsRepository.settings.value.copy(
                        ownerVoiceEnrolled = true,
                        ownerVoiceEnabled = true
                    )
                )
                _phase.value = Phase.COMPLETE
                _currentStep.value = EnrollmentStep.COMPLETE
                _message.value = "Owner voice enrolled successfully on this device. " +
                    "${_enrollmentCount.value} samples recorded."
            } else {
                _currentStep.value = EnrollmentStep.values().getOrNull(index + 2) ?: EnrollmentStep.PHRASE_1
                _phase.value = Phase.IDLE
                _message.value = "Good. Continue with the next phrase. (${index + 1}/$requiredSamples recorded)"
            }
        }
    }

    /**
     * Retrains the voice model from scratch — PRD 2 §3.1 Voice Retraining Button.
     */
    fun retrain() {
        engine.clear()
        _enrollmentCount.value = 0
        _currentStep.value = EnrollmentStep.NOISE_CALIBRATION
        _phase.value = Phase.IDLE
        _progress.value = 0f
        _message.value = ""
        _noiseLevel.value = 0f
        settingsRepository.updateSettings(
            settingsRepository.settings.value.copy(
                ownerVoiceEnrolled = false,
                ownerVoiceEnabled = false
            )
        )
    }

    fun cancel() {
        job?.cancel()
        job = null
        _phase.value = Phase.IDLE
        _progress.value = 0f
    }

    fun isEnrolled(): Boolean = engine.isEnrolled()

    private fun hasMicrophonePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun computeRMS(samples: ShortArray): Float {
        if (samples.isEmpty()) return 0f
        var sumSq = 0.0
        for (s in samples) {
            val v = s.toFloat() / 32768f
            sumSq += v * v
        }
        return kotlin.math.sqrt(sumSq.toFloat() / samples.size)
    }

    private fun bytesToShorts(bytes: ByteArray): ShortArray {
        val n = bytes.size / 2
        return ShortArray(n) { i ->
            ((bytes[i * 2 + 1].toInt() shl 8) or (bytes[i * 2].toInt() and 0xff)).toShort()
        }
    }

    private class StopEnrollmentCollection : RuntimeException()
}
