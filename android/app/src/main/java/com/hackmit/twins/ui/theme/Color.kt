package com.hackmit.twins.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Palette lifted from design/Kindred App.dc.html (the design mockup):
 * warm off-white background, near-black text/surfaces, a single warm
 * amber accent reserved for "strong match" / standout moments. Values
 * are sRGB approximations of the mockup's oklch() colors.
 */
object KlickColors {
    val PageBackground = Color(0xFFEEECE9)
    val CardSurface = Color(0xFFFCFBFA)
    val InsetSurface = Color(0xFFF3F1EE) // e.g. oklch(96%) insight chips
    val TextPrimary = Color(0xFF1C1B1A) // near-black
    val TextSecondary = Color(0xFF8E8A85)
    val TextTertiary = Color(0xFFA3A09B)
    val Border = Color(0xFFDEDAD5)
    val Accent = Color(0xFFC97B45) // warm amber — used sparingly
    val OnDark = Color(0xFFFCFBFA) // text/icons on the near-black surfaces
}
