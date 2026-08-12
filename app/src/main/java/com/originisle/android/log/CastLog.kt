package com.originisle.android.log

import androidx.compose.runtime.mutableStateListOf

/**
 * A small in-memory ring buffer of what the notification listener decided for each notification —
 * cast (which card type) or skipped (with the reason). Backs the in-app "Log" screen so "why didn't
 * this cast?" is answerable at a glance. Lives in the app process alongside the listener; cleared on
 * process death (it's a live debugging aid, not history).
 */
object CastLog {

    /** @param cast true = it was cast to the island, false = skipped. */
    data class Entry(
        val time: Long,
        val app: String,
        val title: String,
        val outcome: String,
        val cast: Boolean,
    )

    private const val MAX = 200

    /** Newest first. A Compose snapshot list, so the Log screen updates live. */
    val entries = mutableStateListOf<Entry>()

    fun add(app: String, title: String, outcome: String, cast: Boolean) {
        entries.add(0, Entry(System.currentTimeMillis(), app, title, outcome, cast))
        while (entries.size > MAX) entries.removeAt(entries.size - 1)
    }

    fun clear() = entries.clear()
}
