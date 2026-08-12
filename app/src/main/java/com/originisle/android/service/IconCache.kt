package com.originisle.android.service

import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.media.session.MediaController
import android.service.notification.StatusBarNotification
import java.util.concurrent.ConcurrentHashMap

/**
 * Shared per-cast-id state, written by the card posters in `cards/` (as they inspect a source
 * notification) and read by [com.originisle.android.island.PlaygroundService] (when it builds the
 * SuperX bundle) and [MediaControlReceiver] (to dispatch transport controls back to the right
 * [MediaController]). Centralising this avoids the card posters and the service depending on each
 * other just to share these maps.
 */
object IconCache {
    val activeControllers = ConcurrentHashMap<Int, MediaController>()
    val activeSmallIcons = ConcurrentHashMap<Int, Icon>()
    val activeLargeIcons = ConcurrentHashMap<Int, Icon>()
    val activeLargeBitmaps = ConcurrentHashMap<Int, Bitmap>()

    /** Deterministic, stable cast id per notification key (band 20000-29999). */
    fun castIdFor(sbn: StatusBarNotification): Int =
        (sbn.key.hashCode() and Int.MAX_VALUE) % 10000 + 20000

    /** Drop everything cached for a card id once its source notification is gone. */
    fun clear(id: Int) {
        activeControllers.remove(id)
        activeSmallIcons.remove(id)
        activeLargeIcons.remove(id)
        activeLargeBitmaps.remove(id)
    }
}
