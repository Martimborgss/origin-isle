package com.originisle.android.cards

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import com.originisle.android.R
import com.originisle.android.island.OriginIslandConstants
import com.originisle.android.island.PlaygroundService

/**
 * Apple-Pay-style payment card for the OriginIsland.
 *
 * A payment notification (Google Wallet, Google Pay, Revolut, PayPal, a bank, …) is re-cast as a
 * short two-phase animation, mirroring how Apple shows a tap-to-pay:
 *
 *  1. **Processing** — the app icon with a loading spinner and a "…" chip.
 *  2. **Done** — a green success tick overlays the icon, the amount shows big, the pill turns into a
 *     green "✓" capsule and the island edge glows green. Auto-dismisses a few seconds later.
 *
 * All of this rides on [com.originisle.android.island.OriginIslandBuilder]/[PlaygroundService]: the
 * tick is `iconStatusType` SUCCESS, the spinner is `generatingStatus`, and the glow is the
 * light-effect colour.
 */
object PaymentCard {

    /** Apple-style system green. */
    private const val GREEN = "#FF34C759"
    private const val WHITE = "#FFFFFFFF"

    private const val PROCESS_MS = 850L   // spinner phase
    private const val HOLD_MS = 6000L     // how long "Done" stays before auto-dismiss

    private val handler = Handler(Looper.getMainLooper())

    /**
     * Show the payment animation for [id].
     *
     * @param amount pre-extracted amount string (e.g. "€12.90"); may be blank.
     * @param merchant merchant / payee, or a short description.
     * @param appLabel the source app's name, shown as "Paid with <app>".
     */
    fun post(
        context: Context,
        id: Int,
        appIcon: Icon?,
        amount: String,
        merchant: String,
        appLabel: String,
        clickResp: PendingIntent?,
    ) {
        val app = context.applicationContext

        // Phase 1 — processing (spinner).
        startService(
            app, id,
            title = "Processing…",
            content = amount.ifBlank { merchant },
            subtext = "Paid with $appLabel",
            appIcon = appIcon,
            clickResp = clickResp,
            generating = 1,
            iconStatus = -1,
            rightTemplate = OriginIslandConstants.TEMPLATE_RIGHT_ISLAND_LOADING, // 3
            chip = "",
        )

        // Phase 2 — done (green tick + glow), a beat later so the transition animates.
        handler.postDelayed({
            startService(
                app, id,
                title = amount.ifBlank { "Paid" },
                content = merchant,
                subtext = "Paid with $appLabel",
                appIcon = appIcon,
                clickResp = clickResp,
                generating = 0,
                iconStatus = OriginIslandConstants.ICON_STATUS_SUCCESS, // 0 -> green tick
                rightTemplate = OriginIslandConstants.TEMPLATE_RIGHT_ISLAND_CAPSULE_TEXT, // 6
                chip = "✓",
            )
        }, PROCESS_MS)

        // Auto-dismiss, the way Apple's pill fades out after a payment.
        handler.postDelayed({
            app.startService(
                Intent(app, PlaygroundService::class.java)
                    .setAction(PlaygroundService.ACTION_CANCEL)
                    .putExtra("id", id)
                    .putExtra("oi_scene", "NAVIGATION"),
            )
        }, PROCESS_MS + HOLD_MS)
    }

    private fun startService(
        context: Context,
        id: Int,
        title: String,
        content: String,
        subtext: String,
        appIcon: Icon?,
        clickResp: PendingIntent?,
        generating: Int,
        iconStatus: Int,
        rightTemplate: Int,
        chip: String,
    ) {
        val intent = Intent(context, PlaygroundService::class.java).apply {
            action = PlaygroundService.ACTION_START
            putExtra("id", id)
            putExtra("oi_scene", "NAVIGATION") // granted carrier scene
            putExtra("oi_template", OriginIslandConstants.TEMPLATE_BASE) // 4
            putExtra("oi_right_template", rightTemplate)
            putExtra("title", title)
            putExtra("text", content)
            putExtra("subtext", subtext)
            putExtra("oi_left_content", title)
            putExtra("oi_right_content", chip)
            putExtra("status_chip_text", chip)
            // Use the PAYMENT app's own icon for the card image AND the pill's left icon. Without
            // oi_left_icon the pill falls back to icon_res (our launcher), which looked wrong.
            appIcon?.let {
                putExtra("oi_large_icon", it)
                putExtra("oi_left_icon", it)
            }
            // Small notification/status icon + fallback when there's no app icon: a card glyph, not us.
            putExtra("icon_res", R.drawable.ic_payment)
            putExtra("oi_generating_status", generating)
            putExtra("oi_icon_status_type", iconStatus)
            // Green success accent: chip fill, chip text and the island edge glow.
            putExtra("oi_bg_color", GREEN)
            putExtra("oi_fg_color", if (chip == "✓") WHITE else GREEN)
            putExtra("oi_light_color", GREEN)
            putExtra("oi_force_show", true)
            putExtra("oi_dismiss_when_kill", true)
            clickResp?.let { putExtra("click_resp", it) }
        }
        context.startService(intent)
    }
}
