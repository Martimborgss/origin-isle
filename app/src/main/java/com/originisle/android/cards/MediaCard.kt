package com.originisle.android.cards

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.originisle.android.R
import com.originisle.android.island.OriginIslandConstants
import com.originisle.android.island.PlaygroundService
import com.originisle.android.service.IconCache
import com.originisle.android.service.MediaControlReceiver
import kotlin.math.abs

/**
 * Casts an active media session (music, podcasts, video) as a card with prev / play-pause / next
 * buttons wired back to the source app through [MediaControlReceiver].
 */
object MediaCard {

    fun post(context: Context, sbn: StatusBarNotification, controller: MediaController) {
        val id = IconCache.castIdFor(sbn)
        val n = sbn.notification
        IconCache.activeControllers[id] = controller

        val md = controller.metadata
        val track = md?.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim().orEmpty()
            .ifBlank { extrasTitle(sbn) }
            // A video (e.g. Firefox playback) may carry neither — fall back to the app name so the
            // island channel's content title is never empty (an empty title makes vivo drop the post).
            .ifBlank { appLabel(context, sbn.packageName) }
        // vivo silently REJECTS a SuperX post with empty content text — falling back to a plain
        // notification instead (visible as `SUPERX_VALUE=false` in dumpsys) — so this can never be
        // blank. A video (YouTube) or a voice note (WhatsApp) has no ARTIST tag at all; fall back to
        // a generic label rather than the app name again — track already falls back to the app name,
        // so reusing it here made the pill show the same text on both sides ("WhatsApp"/"WhatsApp").
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)?.trim().orEmpty()
            .ifBlank { "Now playing" }
        val playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING

        n.smallIcon?.let { IconCache.activeSmallIcons[id] = it }
        // Scale down before it ever crosses a Binder call (startService()/notify() both parcel the
        // Icon). A video's own frame (e.g. Firefox playback) can be a full-resolution bitmap — passed
        // as-is it blows the transaction size limit and crashes with "Could not copy bitmap to
        // parcel blob", which then crash-loops the whole listener since reload() re-triggers it.
        val albumArt: Bitmap? = (md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART))?.let(::shrinkForIcon)
        albumArt?.let { IconCache.activeLargeBitmaps[id] = it }

        // A voice note (WhatsApp) or a podcast/video player often shows an elapsed-time chronometer,
        // same as a call — prefer that (live-ticking, since the listener's poll loop re-posts media
        // cards every second) over a static "Playing"/"Paused" label when the source provides one.
        val showChrono = n.extras.getBoolean(NotificationCompat.EXTRA_SHOW_CHRONOMETER, false)
        val chip = if (showChrono && n.`when` > 0) {
            formatElapsed(abs(System.currentTimeMillis() - n.`when`) / 1000)
        } else {
            // Plain ASCII, not "♪"/"❚❚" — the capsule showed as genuinely empty with those, while
            // every other working chip elsewhere (percentages, "1-1", "✓") is plain ASCII too.
            if (playing) "Playing" else "Paused"
        }

        fun mediaAction(action: String) = PendingIntent.getBroadcast(
            context, (id.toString() + action).hashCode(),
            Intent(action).setPackage(context.packageName)
                .putExtra("cast_id", id)
                .putExtra(MediaControlReceiver.EXTRA_TOKEN, controller.sessionToken),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val actions = arrayListOf(
            makeAction(context, "⏮", mediaAction(MediaControlReceiver.ACTION_PREV)),
            makeAction(context, if (playing) "⏸" else "▶", mediaAction(MediaControlReceiver.ACTION_PLAY_PAUSE)),
            makeAction(context, "⏭", mediaAction(MediaControlReceiver.ACTION_NEXT)),
        )

        val intent = Intent(context, PlaygroundService::class.java).apply {
            action = PlaygroundService.ACTION_START
            putExtra("id", id)
            // NAVIGATION, not MOVIE: only NAVIGATION is actually granted as a SuperX scene on-device —
            // a card posted under MOVIE is silently dropped even though it's logged as cast. The
            // template (base + wave right-island) still gives it the media look.
            putExtra("oi_scene", "NAVIGATION")
            putExtra("title", track)
            putExtra("text", artist)
            putExtra("source_pkg", sbn.packageName)
            putExtra("oi_left_content", track)
            // The right-island WAVE template (1) renders nothing on this device (confirmed: even with
            // an explicit colour set, it's just an empty/black area) — fall back to the CAPSULE_TEXT
            // template, which is proven reliable everywhere else (downloads, scores, payments).
            putExtra("status_chip_text", chip)
            // Mini thumbnail: use the track's own album/video art as the pill's LEFT icon when the
            // session provides one — matches how Apple/Spotify-style media cards look, instead of a
            // generic play glyph.
            albumArt?.let { putExtra("oi_left_icon", Icon.createWithBitmap(it)) }
            putExtra("icon_res", R.drawable.ic_media_play)
            putExtra("oi_template", OriginIslandConstants.TEMPLATE_BASE)
            putExtra("oi_right_template", OriginIslandConstants.TEMPLATE_RIGHT_ISLAND_CAPSULE_TEXT)
            // No oi_fg_color: that was a leftover from the WAVE template (which needed an explicit
            // colour to render at all). Forcing white text on CAPSULE_TEXT's default (light) chip
            // background made the text invisible — every other working capsule chip (downloads,
            // scores, payments' "✓") relies on the default colour, so this one should too.
            putParcelableArrayListExtra("actions", actions)
            putExtra(
                "source_content_intent",
                n.contentIntent ?: fallbackClickIntent(context, sbn.packageName),
            )
        }
        context.startService(intent)
    }

    private fun extrasTitle(sbn: StatusBarNotification): String =
        sbn.notification.extras.getCharSequence(NotificationCompat.EXTRA_TITLE)?.toString()?.trim().orEmpty()

    private fun appLabel(context: Context, pkg: String): String = runCatching {
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    private fun makeAction(context: Context, label: String, pi: PendingIntent): Notification.Action =
        Notification.Action.Builder(
            Icon.createWithResource(context, R.mipmap.ic_launcher_round), label, pi,
        ).build()
}
