package com.originisle.android.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.service.notification.NotificationListenerService
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
        // Re-bind the notification listener and re-grant SuperX scenes now that we anchor the process.
        // Deliberately do NOT poke PlaygroundService here: tearing down the foreground service the
        // instant this connects removed the process's only anchor, and OriginOS killed the process —
        // which disabled this very accessibility service. Leave any running FGS alone; it simply won't
        // be restarted once we're enabled (see PlaygroundService.ensureForeground).
        runCatching {
            NotificationListenerService.requestRebind(
                ComponentName(this, NotificationCastListener::class.java),
            )
        }
        runCatching { OriginIslandBuilder.grantScenes(this) }
    }

    // Intentionally empty — this service inspects nothing.
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit
}
