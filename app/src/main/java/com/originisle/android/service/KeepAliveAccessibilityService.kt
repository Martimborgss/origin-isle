package com.originisle.android.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.originisle.android.island.OriginIslandBuilder

/**
 * KEEP-ALIVE ONLY — this service reads nothing.
 *
 * It exists purely to anchor the app process. An *enabled* AccessibilityService keeps the process
 * alive on OriginOS without a foreground-service notification, so there is no status-bar icon
 * (which vivo force-shows for any foreground service). With the process kept alive, the
 * [NotificationCastListener] keeps receiving notifications in the background.
 *
 * It captures no data: [onAccessibilityEvent] is intentionally empty and the config sets
 * `canRetrieveWindowContent="false"`, so it can never inspect window content, text, or events.
 */
class KeepAliveAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        // This fires when the system (re)starts our process, which is exactly the moment the
        // notification listener needs recovering after a swipe-away kill. A plain requestRebind is
        // a no-op in that case — see [NotificationCastListener.forceRebind].
        runCatching { NotificationCastListener.forceRebind(this) }
        runCatching { OriginIslandBuilder.grantScenes(this) }
    }

    // Intentionally empty — this service inspects nothing.
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit
}
