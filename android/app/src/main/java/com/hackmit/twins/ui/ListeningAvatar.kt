package com.hackmit.twins.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import com.hackmit.twins.ui.theme.KlickColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Ported from design/Kindred App.dc.html's buildAvatar(size, engaging=true)
 * — the "your twin is actively out there" character used on the mockup's
 * Ambient screen: a rounded-square face with looking/blinking eyes, a
 * talking mouth, two pulsing rings, and orbiting dots. Everything here is
 * driven by continuous Compose animations rather than the mockup's
 * setInterval-based React state, but the timings/motion are matched closely.
 */
@Composable
fun ListeningAvatar(size: Dp, modifier: Modifier = Modifier) {
    val eyeOffsetX = remember { Animatable(0f) }
    val eyeOffsetY = remember { Animatable(0f) }
    var blinking by remember { mutableStateOf(false) }
    val mouthWidthFraction = remember { Animatable(0.14f) }

    // Eyes drift to a new small random offset every ~1.3s, matching the
    // mockup's eye-look-around timer.
    LaunchedEffect(Unit) {
        while (true) {
            delay(1300)
            val targetX = Random.nextFloat() * 2 - 1
            val targetY = Random.nextFloat() * 2 - 1
            launch { eyeOffsetX.animateTo(targetX, animationSpec = tween(250)) }
            launch { eyeOffsetY.animateTo(targetY, animationSpec = tween(250)) }
        }
    }

    // Blink every ~3.4s, held briefly — matches the mockup's blink timer.
    LaunchedEffect(Unit) {
        while (true) {
            delay(3400)
            blinking = true
            delay(150)
            blinking = false
        }
    }

    // Mouth cycles through the mockup's "engaging" talk-frame widths.
    val mouthWidths = remember { listOf(0.11f, 0.25f, 0.16f, 0.28f) }
    LaunchedEffect(Unit) {
        var frame = 0
        while (true) {
            mouthWidthFraction.animateTo(mouthWidths[frame % mouthWidths.size], tween(140))
            delay(220)
            frame++
        }
    }

    val blinkScale by animateFloatAsState(
        targetValue = if (blinking) 0.08f else 1f,
        animationSpec = tween(120),
        label = "blink",
    )

    val infinite = rememberInfiniteTransition(label = "avatar")
    val ring0 by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "ring0",
    )
    val ring1 by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(2400, easing = LinearEasing),
            RepeatMode.Restart,
            initialStartOffset = StartOffset(1200),
        ),
        label = "ring1",
    )
    val orbit0 by infinite.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)),
        label = "orbit0",
    )
    val orbit1 by infinite.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing)),
        label = "orbit1",
    )
    val orbit2 by infinite.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing)),
        label = "orbit2",
    )

    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        val center = Offset(s / 2, s / 2)
        val cornerRadius = CornerRadius(s * 0.3f)

        // Pulsing rings, behind everything else.
        listOf(ring0, ring1).forEach { progress ->
            val scale = 0.92f + progress * 0.48f
            val alpha = (0.45f * (1f - progress)).coerceIn(0f, 0.45f)
            val ringSize = s * scale
            drawRoundRect(
                color = KlickColors.TextPrimary.copy(alpha = alpha),
                topLeft = Offset(center.x - ringSize / 2, center.y - ringSize / 2),
                size = Size(ringSize, ringSize),
                cornerRadius = CornerRadius(ringSize * 0.3f),
                style = Stroke(width = s * 0.012f),
            )
        }

        // Orbiting dots — two neutral, one accent, matching the mockup.
        val orbitSpecs = listOf(
            Triple(orbit0, s * 1.05f, KlickColors.TextTertiary),
            Triple(orbit1, s * 1.19f, KlickColors.TextTertiary),
            Triple(orbit2, s * 1.33f, KlickColors.Accent),
        )
        orbitSpecs.forEach { (angleDeg, radius, color) ->
            val angleRad = Math.toRadians(angleDeg.toDouble())
            val dotCenter = Offset(
                center.x + (radius / 2 * cos(angleRad)).toFloat(),
                center.y + (radius / 2 * sin(angleRad)).toFloat(),
            )
            drawCircle(color = color, radius = s * 0.045f, center = dotCenter)
        }

        // Face — rounded square, near-black.
        drawRoundRect(
            color = KlickColors.TextPrimary,
            size = this.size,
            cornerRadius = cornerRadius,
        )

        // Eyes: two white pills, shifted by the look-around offset and
        // squashed vertically to blink.
        val eyeW = s * 0.09f
        val eyeH = s * 0.22f
        val eyeGap = s * 0.16f
        val eyeShift = s * 0.05f
        val eyeY = center.y - eyeH / 2 * blinkScale
        listOf(-1, 1).forEach { side ->
            val eyeX = center.x + side * eyeGap / 2 - eyeW / 2 + eyeOffsetX.value * eyeShift
            drawRoundRect(
                color = KlickColors.OnDark,
                topLeft = Offset(eyeX, eyeY + eyeOffsetY.value * eyeShift),
                size = Size(eyeW, eyeH * blinkScale),
                cornerRadius = CornerRadius(eyeW / 2),
            )
        }

        // Mouth: a pill whose width breathes to suggest quiet activity.
        val mouthW = s * mouthWidthFraction.value
        val mouthH = s * 0.045f
        drawRoundRect(
            color = KlickColors.OnDark,
            topLeft = Offset(center.x - mouthW / 2, center.y + s * 0.14f),
            size = Size(mouthW, mouthH),
            cornerRadius = CornerRadius(mouthH / 2),
        )
    }
}
