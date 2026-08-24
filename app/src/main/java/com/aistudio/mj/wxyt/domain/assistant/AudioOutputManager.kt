package com.aistudio.mj.wxyt.domain.assistant

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * AudioOutputManager — queue-based PCM playback for smooth real-time audio.
 *
 * Architecture mirrors Maya's ZoyaForegroundService audio playback:
 *   - Audio chunks are added to a LinkedBlockingQueue
 *   - A background coroutine drains the queue and writes to AudioTrack
 *   - Supports interrupt (barge-in): clears queue and flushes AudioTrack
 *
 * Audio format: 24kHz, Mono, PCM 16-bit (Gemini Live output format)
 */
class AudioOutputManager(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job()),
    private var outputVolume: Float = 1f
) {
    private var audioTrack: AudioTrack? = null
    private val sampleRate = 24000
    private val channelConfig = AudioFormat.CHANNEL_OUT_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    private val audioQueue = LinkedBlockingQueue<ByteArray>(32)
    private var playbackJob: Job? = null
    private var isPlaying = false

    /**
     * Optional callback invoked for every PCM chunk received for playback.
     * Used by VoiceReactiveController to analyze AI output audio energy in real-time.
     * Called on the IO dispatcher — must be thread-safe.
     */
    var onAudioChunk: ((ByteArray) -> Unit)? = null

    /** Apply a new output volume without rebuilding the AudioTrack. */
    fun setVolume(volume: Float) {
        outputVolume = volume.coerceIn(0f, 1f)
        try { audioTrack?.setVolume(outputVolume) } catch (_: Exception) {}
    }

    fun initTrack() {
        if (audioTrack != null) return

        val finalBuf = if (minBufferSize > 0) minBufferSize else 4096

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(audioFormat)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .build()
            )
            .setBufferSizeInBytes(finalBuf)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        try { audioTrack?.setVolume(outputVolume.coerceIn(0f, 1f)) } catch (_: Exception) {}
        audioTrack?.play()
        isPlaying = true
        startPlaybackLoop()
    }

    private fun startPlaybackLoop() {
        playbackJob?.cancel()
        playbackJob = scope.launch(Dispatchers.IO) {
            while (isActive && isPlaying) {
                try {
                    val data = audioQueue.poll(50, TimeUnit.MILLISECONDS)
                    if (data != null) {
                        if (audioTrack?.playState != AudioTrack.PLAYSTATE_PLAYING) {
                            audioTrack?.play()
                        }
                        audioTrack?.write(data, 0, data.size)
                    }
                } catch (e: Exception) {
                    Log.e("AudioOutput", "Playback loop error", e)
                }
            }
        }
    }

    /**
     * Queue a PCM chunk for playback. Non-blocking.
     * Also feeds the voice-reactive controller for real-time AI audio analysis.
     */
    fun playChunk(pcmData: ByteArray) {
        if (!isPlaying || audioTrack == null) {
            initTrack()
        }
        // Feed the voice-reactive controller for real-time AI audio analysis
        onAudioChunk?.invoke(pcmData)
        try {
            // Keep the queue bounded so stale audio can never build up and
            // increase conversational latency.
            if (!audioQueue.offer(pcmData)) {
                audioQueue.poll()
                audioQueue.offer(pcmData)
            }
        } catch (e: Exception) {
            Log.e("AudioOutput", "Error queueing audio", e)
        }
    }

    /**
     * Interrupt playback — clear the queue and flush AudioTrack.
     * Called when the user barges in (starts speaking while AI is talking).
     */
    fun interrupt() {
        audioQueue.clear()
        try {
            if (audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING) {
                audioTrack?.pause()
                audioTrack?.flush()
                audioTrack?.play()
            }
        } catch (e: Exception) {
            Log.e("AudioOutput", "Error during interrupt", e)
        }
    }

    fun stop() {
        isPlaying = false
        audioQueue.clear()
        playbackJob?.cancel()
        try {
            audioTrack?.pause()
            audioTrack?.flush()
        } catch (e: Exception) {}
    }

    fun release() {
        stop()
        try {
            audioTrack?.stop()
        } catch (e: Exception) {}
        try {
            audioTrack?.release()
        } catch (e: Exception) {}
        audioTrack = null
    }
}
