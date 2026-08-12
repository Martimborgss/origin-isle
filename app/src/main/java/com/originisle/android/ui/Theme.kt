package com.originisle.android.ui

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Origin Isle is dark-mode
 */
private val OriginIsleColorScheme = darkColorScheme(
    primary = Color(0xFF3FC6D6),
    onPrimary = Color(0xFF00363A),
    secondary = Color(0xFF34C759),
    onSecondary = Color(0xFF00390D),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE3E3E3),
    surface = Color(0xFF161A20),
    onSurface = Color(0xFFE3E3E3),
    surfaceVariant = Color(0xFF20252C),
    onSurfaceVariant = Color(0xFFC4C7CC),
    outline = Color(0xFF3A3F47),
)

@Composable
fun OriginIsleTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }
    }
    MaterialTheme(colorScheme = OriginIsleColorScheme, content = content)
}
