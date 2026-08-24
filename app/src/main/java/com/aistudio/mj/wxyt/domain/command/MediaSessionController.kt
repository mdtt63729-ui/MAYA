package com.aistudio.mj.wxyt.domain.command

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * Media Session Controller — PRD 1 §3.2.
 *
 * Intercepts and manages background audio playback via standard Android
 * MediaSession APIs. Supports Play, Pause, Next, Previous, and Volume Control.
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class MediaSessionController(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mediaSessionManager: MediaSessionManager? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        } else null

    data class MediaInfo(
        val packageName: String?,
        val title: String?,
        val artist: String?,
        val album: String?,
        val isPlaying: Boolean,
        val playbackState: Int
    )

    /**
     * Gets the active media session's controller, if any.
     */
    private fun getActiveController(): MediaController? {
        if (mediaSessionManager == null) return null
        val component = ComponentName(context.packageName, com.aistudio.mj.wxyt.service.MayaNotificationListenerService::class.java.name)
        return try {
            val sessions = mediaSessionManager.getActiveSessions(component)
            sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: sessions.firstOrNull()
        } catch (e: SecurityException) {
            Log.e("MediaSessionController", "Notification listener permission not granted", e)
            null
        }
    }

    /**
     * Gets info about the currently active media session.
     */
    fun getActiveMediaInfo(): MediaInfo? {
        val controller = getActiveController() ?: return null
        val metadata = controller.metadata
        val state = controller.playbackState
        val isPlaying = state?.state == PlaybackState.STATE_PLAYING
        return MediaInfo(
            packageName = controller.packageName,
            title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE),
            artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST),
            album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM),
            isPlaying = isPlaying,
            playbackState = state?.state ?: PlaybackState.STATE_NONE
        )
    }

    /**
     * Play / Pause toggle — sends PLAYBACK_STATE toggle to active session.
     */
    fun playPause(): Boolean {
        val controller = getActiveController() ?: return false
        val state = controller.playbackState?.state
        return try {
            if (state == PlaybackState.STATE_PLAYING) {
                controller.transportControls.pause()
                Log.d("MediaSessionController", "Paused media")
            } else {
                controller.transportControls.play()
                Log.d("MediaSessionController", "Played media")
            }
            true
        } catch (e: Exception) {
            Log.e("MediaSessionController", "Play/pause failed", e)
            false
        }
    }

    fun play(): Boolean {
        val controller = getActiveController() ?: return false
        return try {
            controller.transportControls.play()
            true
        } catch (e: Exception) { false }
    }

    fun pause(): Boolean {
        val controller = getActiveController() ?: return false
        return try {
            controller.transportControls.pause()
            true
        } catch (e: Exception) { false }
    }

    fun next(): Boolean {
        val controller = getActiveController() ?: return false
        return try {
            controller.transportControls.skipToNext()
            true
        } catch (e: Exception) { false }
    }

    fun previous(): Boolean {
        val controller = getActiveController() ?: return false
        return try {
            controller.transportControls.skipToPrevious()
            true
        } catch (e: Exception) { false }
    }

    /**
     * Volume control via AudioManager.
     */
    fun volumeUp(): Boolean {
        return try {
            audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            true
        } catch (e: Exception) { false }
    }

    fun volumeDown(): Boolean {
        return try {
            audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            true
        } catch (e: Exception) { false }
    }

    fun setVolume(percent: Int): Boolean {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (max * percent / 100).coerceIn(0, max)
        return try {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
            true
        } catch (e: Exception) { false }
    }

    /**
     * Stops playback in the active session.
     */
    fun stop(): Boolean {
        val controller = getActiveController() ?: return false
        return try {
            controller.transportControls.stop()
            true
        } catch (e: Exception) { false }
    }
}
