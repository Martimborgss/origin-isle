package com.originisle.android.cards

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import com.originisle.android.MainActivity

/**
 * A content intent that opens the SOURCE app's own notification target — or, if the source
 * notification has none, falls back to just launching the app. vivo requires every SuperX post to
 * carry a content intent or it silently rejects it; this is that fallback.
 */
internal fun fallbackClickIntent(context: Context, pkg: String): PendingIntent {
    val launch = context.packageManager.getLaunchIntentForPackage(pkg) ?: Intent(context, MainActivity::class.java)
    return PendingIntent.getActivity(
        context, pkg.hashCode(), launch,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/**
 * Cap a bitmap's longer side before it's ever wrapped in an [android.graphics.drawable.Icon] and
 * crosses a Binder call (`startService()`/`notify()` both parcel the Icon). A source notification's
 * bitmap isn't guaranteed to be icon-sized — a video's own frame (e.g. Firefox playback) can be
 * full-resolution — and passing it as-is throws "Could not copy bitmap to parcel blob" and crashes,
 * which then crash-loops the whole listener since it re-casts on every reconnect.
 */
internal fun shrinkForIcon(bitmap: Bitmap, maxDimension: Int = 200): Bitmap {
    val w = bitmap.width
    val h = bitmap.height
    if (w <= maxDimension && h <= maxDimension) return bitmap
    val scale = maxDimension.toFloat() / maxOf(w, h)
    return Bitmap.createScaledBitmap(bitmap, (w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1), true)
}

/** "125" -> "02:05", "3725" -> "01:02:05" — a stopwatch-style elapsed-time string. */
internal fun formatElapsed(totalSeconds: Long): String {
    val s = totalSeconds % 60
    val m = (totalSeconds / 60) % 60
    val h = totalSeconds / 3600
    return if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
