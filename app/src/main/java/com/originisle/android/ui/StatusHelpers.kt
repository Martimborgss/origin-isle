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
 * OriginOS keeps a separate "auto-start" allow-list (Settings → Battery → Auto-start) that can't be
 * queried programmatically. Try the known vivo manager screen; fall back to the app's own details
 * page so the user can find the equivalent setting themselves.
 */
fun openAutoStartSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent().setClassName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
