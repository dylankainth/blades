package com.hackmit.twins.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * Klick's palette is deliberately one light, warm look everywhere — the
 * design mockup (design/Kindred App.dc.html) has no dark variant, so this
 * theme doesn't branch on isSystemInDarkTheme(); it just applies the
 * mockup's look consistently.
 */
private val KlickColorScheme = lightColorScheme(
    primary = KlickColors.TextPrimary,
    onPrimary = KlickColors.OnDark,
    secondary = KlickColors.Accent,
    onSecondary = KlickColors.OnDark,
    background = KlickColors.PageBackground,
    onBackground = KlickColors.TextPrimary,
    surface = KlickColors.CardSurface,
    onSurface = KlickColors.TextPrimary,
    surfaceVariant = KlickColors.InsetSurface,
    onSurfaceVariant = KlickColors.TextSecondary,
    outline = KlickColors.Border,
    outlineVariant = KlickColors.Border,
    error = KlickColors.Accent,
)

private val KlickShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(38.dp), // the mockup's big card radius
)

@Composable
fun DigitalTwinsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KlickColorScheme,
        typography = KlickTypography,
        shapes = KlickShapes,
        content = content,
    )
}
