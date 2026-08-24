package com.aistudio.mj.wxyt.domain.security

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.aistudio.mj.wxyt.domain.assistant.AudioInputManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class OwnerVoiceEnrollmentController(private val context: Context) {
    enum class Phase { IDLE, RECORDING, PROCESSING, COMPLETE, ERROR }
    private val _phase = MutableStateFlow(Phase.IDLE)
    val phase: StateFlow<Phase> = _phase.asStateFlow()
    private val _sampleIndex = MutableStateFlow(0)
    val sampleIndex: StateFlow<Int> = _sampleIndex.asStateFlow()
    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()
    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message.asStateFlow()

    private val engine = LocalVoiceprintEngine(context.applicationContext)
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main.immediate)

    val phrases = listOf(
        "Hey MAYA, are you listening?",
        "MAYA, what's the weather tomorrow?",
        "MAYA, open my assistant."
    )

    fun startSample(index: Int) {
        if (_phase.value == Phase.RECORDING || _phase.value == Phase.PROCESSING) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _phase.value = Phase.ERROR
            _message.value = "Microphone permission is required."
            return
        }
        job?.cancel()
        job = scope.launch {
            _sampleIndex.value = index
            _progress.value = 0f
            _message.value = phrases.getOrElse(index) { phrases.last() }
            _phase.value = Phase.RECORDING
            val audio = AudioInputManager(1f, true, true)
            val out = ByteArrayOutputStream()
            val started = System.currentTimeMillis()
            try {
                withContext(Dispatchers.IO) {
                    audio.startRecording().collect { chunk ->
                        out.write(chunk)
                        _progress.value = ((System.currentTimeMillis() - started) / 2200f).coerceIn(0f, 1f)
                        if (System.currentTimeMillis() - started >= 2200L) throw StopEnrollmentCollection()
                    }
                }
            } catch (_: StopEnrollmentCollection) {
                // expected end of the sample
            } catch (t: Throwable) {
                _phase.value = Phase.ERROR
                _message.value = t.message ?: "Unable to record voice sample."
                return@launch
            } finally {
                audio.stopRecording()
            }
            _phase.value = Phase.PROCESSING
            val pcm = bytesToShorts(out.toByteArray())
            val ok = withContext(Dispatchers.Default) {
                if (index == 0) engine.enrollOwner(pcm) else engine.addEnrollmentSample(pcm)
            }
            if (!ok) {
                _phase.value = Phase.ERROR
                _message.value = "The sample was too short or too noisy. Please try again."
                return@launch
            }
            _progress.value = 1f
            if (index == phrases.lastIndex) {
                _phase.value = Phase.COMPLETE
                _message.value = "Owner voice enrolled on this device."
            } else {
                _sampleIndex.value = index + 1
                _phase.value = Phase.IDLE
                _message.value = "Good. Continue with the next phrase."
            }
        }
    }

    fun cancel() { job?.cancel(); job = null; _phase.value = Phase.IDLE; _progress.value = 0f }

    private fun bytesToShorts(bytes: ByteArray): ShortArray {
        val n = bytes.size / 2
        return ShortArray(n) { i -> ((bytes[i * 2 + 1].toInt() shl 8) or (bytes[i * 2].toInt() and 0xff)).toShort() }
    }

    private class StopEnrollmentCollection : RuntimeException()
}
