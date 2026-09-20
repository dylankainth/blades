package com.hackmit.twins.ui.theme

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Overrides the status bar for one screen and puts the previous look back
 * when that screen leaves. The default (page colour, dark icons) lives in
 * res/values/themes.xml; only screens with a dark top need this.
 */
@Composable
fun StatusBarStyle(color: Color, darkIcons: Boolean) {
    val view = LocalView.current
    DisposableEffect(color, darkIcons) {
        val window = (view.context as? Activity)?.window
        if (window == null) {
            onDispose { }
        } else {
            val controller = WindowCompat.getInsetsController(window, view)
            val previousColor = window.statusBarColor
            val previousDarkIcons = controller.isAppearanceLightStatusBars
            window.statusBarColor = color.toArgb()
            controller.isAppearanceLightStatusBars = darkIcons
            onDispose {
                window.statusBarColor = previousColor
                controller.isAppearanceLightStatusBars = previousDarkIcons
            }
        }
    }
}
