package com.aistudio.mj.wxyt.domain.assistant

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.yield
import kotlin.coroutines.coroutineContext

class AudioInputManager(
    private val inputGain: Float = 1f,
    private val noiseCancellation: Boolean = true,
    private val echoCancellation: Boolean = true
) {
    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    /**
     * Optional callback invoked for every PCM chunk read from the microphone.
     * Used by VoiceReactiveController to analyze user voice energy in real-time.
     * Called on the IO dispatcher — must be thread-safe.
     */
    var onAudioChunk: ((ByteArray) -> Unit)? = null

    @SuppressLint("MissingPermission")
    fun startRecording(): Flow<ByteArray> = flow {
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                throw Exception("AudioRecord initialization failed")
            }

            val sessionId = audioRecord?.audioSessionId ?: 0
            if (echoCancellation && AcousticEchoCanceler.isAvailable() && sessionId != 0) {
                echoCanceler = AcousticEchoCanceler.create(sessionId)?.also { it.enabled = true }
            }
            if (noiseCancellation && NoiseSuppressor.isAvailable() && sessionId != 0) {
                noiseSuppressor = NoiseSuppressor.create(sessionId)?.also { it.enabled = true }
            }

            audioRecord?.startRecording()

            // Read 640 bytes at a time (~20ms chunks at 16kHz) for low-latency VAD/barge-in.
            val buffer = ByteArray(640)
            while (coroutineContext.isActive) {
                val readResult = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (readResult > 0) {
                    val chunk = buffer.copyOf(readResult)
                    val gained = applyGain(chunk, inputGain)
                    onAudioChunk?.invoke(gained)
                    emit(gained)
                } else if (readResult < 0) {
                    break
                }
                yield()
            }
        } finally {
            stopRecording()
        }
    }.flowOn(Dispatchers.IO)

    private fun applyGain(pcm: ByteArray, gain: Float): ByteArray {
        val g = gain.coerceIn(0.25f, 2.5f)
        if (kotlin.math.abs(g - 1f) < 0.001f) return pcm
        val out = pcm.copyOf()
        var i = 0
        while (i + 1 < out.size) {
            val sample = ((out[i + 1].toInt() shl 8) or (out[i].toInt() and 0xFF)).toShort().toInt()
            val scaled = (sample * g).toInt().coerceIn(-32768, 32767)
            out[i] = (scaled and 0xFF).toByte()
            out[i + 1] = ((scaled shr 8) and 0xFF).toByte()
            i += 2
        }
        return out
    }

    fun stopRecording() {
        try { echoCanceler?.enabled = false } catch (_: Exception) {}
        try { noiseSuppressor?.enabled = false } catch (_: Exception) {}
        try { echoCanceler?.release() } catch (_: Exception) {}
        try { noiseSuppressor?.release() } catch (_: Exception) {}
        echoCanceler = null
        noiseSuppressor = null
        try { audioRecord?.stop() } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null
    }
}
