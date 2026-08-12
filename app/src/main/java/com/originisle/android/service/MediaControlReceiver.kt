package com.originisle.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.util.Log

/**
 * Routes media-button taps from a cast media card back to the real source app's
 * [MediaController]. The controller is looked up either by the cast id (kept live in
 * [IconCache.activeControllers]) or rebuilt from the session token carried in the intent.
 */
class MediaControlReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_PLAY_PAUSE = "com.originisle.android.MEDIA_PLAY_PAUSE"
        const val ACTION_NEXT = "com.originisle.android.MEDIA_NEXT"
        const val ACTION_PREV = "com.originisle.android.MEDIA_PREV"
        const val ACTION_SEEK = "com.originisle.android.MEDIA_SEEK"
        const val ACTION_CUSTOM = "com.originisle.android.MEDIA_CUSTOM"
        const val EXTRA_TOKEN = "media_session_token"
        const val EXTRA_SEEK_PERCENT = "seek_percent"
        const val EXTRA_CUSTOM_ACTION = "custom_action"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val castId = intent.getIntExtra("cast_id", -1)
        val token = intent.getParcelableExtra(EXTRA_TOKEN, MediaSession.Token::class.java)

        val controller: MediaController = try {
            (if (castId != -1) IconCache.activeControllers[castId] else null)
                ?: token?.let { MediaController(context, it) }
                ?: return
        } catch (e: Exception) {
            Log.e("MediaControlReceiver", "Failed to dispatch media control", e)
            return
        }

        val controls = controller.transportControls
        val state = controller.playbackState
        Log.d("MediaControlReceiver", "onReceive action=$action castId=$castId state=${state?.state}")

        when (action) {
            ACTION_PLAY_PAUSE ->
                if (state?.state == PlaybackState.STATE_PLAYING) controls.pause() else controls.play()
            ACTION_NEXT -> controls.skipToNext()
            ACTION_PREV -> controls.skipToPrevious()
            ACTION_CUSTOM ->
                intent.getStringExtra(EXTRA_CUSTOM_ACTION)?.let { controls.sendCustomAction(it, null) }
            ACTION_SEEK -> {
                val percent = intent.getIntExtra(EXTRA_SEEK_PERCENT, -1)
                val duration = controller.metadata?.getLong("android.media.metadata.DURATION") ?: 0L
                if (percent in 0..100 && duration > 0) controls.seekTo(duration * percent / 100)
            }
        }
    }
}
