package com.originisle.android.ui.samples

import android.content.Context

/**
 * A ready-made OriginIsland demo card, used by the in-app tester to fire an example of each
 * supported type without waiting for a real notification. [post] builds and sends the card.
 */
data class OriginSample(
    val name: String,
    val summary: String,
    val post: (Context) -> Unit,
)
