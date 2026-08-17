package com.originisle.android.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.originisle.android.service.KeepAliveAccessibilityService

/** Shared permission/status checks used by both [OnboardingScreen] and the Cast tab. */

fun isListenerEnabled(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

fun isAccessibilityEnabled(context: Context): Boolean {
    val flat = Settings.Secure.getString(
        context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ).orEmpty()
    val target = android.content.ComponentName(context, KeepAliveAccessibilityService::class.java)
    return flat.split(':').any { android.content.ComponentName.unflattenFromString(it) == target }
}

fun isBatteryUnrestricted(context: Context): Boolean {
    val pm = context.getSystemService(PowerManager::class.java)
    return pm?.isIgnoringBatteryOptimizations(context.packageName) == true
}

fun listenerStatusText(context: Context): String =
    if (isListenerEnabled(context)) "Notification access: granted ✓" else "Notification access: NOT granted"

fun accessibilityStatusText(context: Context): String =
    if (isAccessibilityEnabled(context)) "Keep-alive: on ✓ (no status-bar icon)" else "Keep-alive: off (status-bar icon shows)"

fun batteryStatusText(context: Context): String =
    if (isBatteryUnrestricted(context)) "Battery: unrestricted ✓" else "Battery: restricted (tap above)"

/**
 * vivo doesn't expose the auto-start / "Associated startup" state to apps, so [acknowledged] is only
 * a record that the user was sent to that screen — never proof the toggles are actually on.
 */
fun autoStartStatusText(acknowledged: Boolean): String =
    if (acknowledged) {
        "Auto-start + Associated startup: opened ✓ (vivo won't let us verify — check it's still on)"
    } else {
        "Auto-start + Associated startup: not confirmed (tap above)"
    }

fun requestIgnoreBattery(context: Context) {
    if (isBatteryUnrestricted(context)) return
    runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

/**
 * OriginOS keeps "Autostart" and "Associated startup" together on a per-app "Device management"
 * page, neither of them readable by apps — see [autoStartStatusText].
 *
 * Deep-link straight to that page instead of dumping the user in the global background-start-up
 * list to hunt for Origin Isle. It's SoftPermissionDetailActivity, which takes its target from a
 * "packagename" string extra and calls finish() immediately if that extra is missing or names a
 * package it can't resolve (read off PermissionManager.apk and confirmed on an X200 Pro). Since the
 * package we pass is our own, it's always resolvable. The remaining fallbacks cover vivo builds
 * that don't ship the same activity, where startActivity throws instead.
 */
fun openAutoStartSettings(context: Context) {
    val targets = listOf(
        Intent("permission.intent.action.softPermissionDetail")
            .setClassName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.SoftPermissionDetailActivity",
            )
            .putExtra("packagename", context.packageName),
        Intent().setClassName(
            "com.vivo.permissionmanager",
            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
        ),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
    )
    targets.firstOrNull { intent ->
        runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
    }
}
