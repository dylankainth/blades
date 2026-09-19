package com.hackmit.twins.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * Kindred's palette is deliberately one light, warm look everywhere — the
 * design mockup (design/Kindred App.dc.html) has no dark variant, so this
 * theme doesn't branch on isSystemInDarkTheme(); it just applies the
 * mockup's look consistently.
 */
private val KindredColorScheme = lightColorScheme(
    primary = KindredColors.TextPrimary,
    onPrimary = KindredColors.OnDark,
    secondary = KindredColors.Accent,
    onSecondary = KindredColors.OnDark,
    background = KindredColors.PageBackground,
    onBackground = KindredColors.TextPrimary,
    surface = KindredColors.CardSurface,
    onSurface = KindredColors.TextPrimary,
    surfaceVariant = KindredColors.InsetSurface,
    onSurfaceVariant = KindredColors.TextSecondary,
    outline = KindredColors.Border,
    outlineVariant = KindredColors.Border,
    error = KindredColors.Accent,
)

private val KindredShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(38.dp), // the mockup's big card radius
)

@Composable
fun DigitalTwinsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KindredColorScheme,
        typography = KindredTypography,
        shapes = KindredShapes,
        content = content,
    )
}
