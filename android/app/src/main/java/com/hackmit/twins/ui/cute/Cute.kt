package com.hackmit.twins.ui.cute

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hackmit.twins.ui.ListeningAvatar
import com.hackmit.twins.ui.theme.KlickColors
import kotlinx.coroutines.delay

/**
 * The small set of touches that make the rest of the app feel like it belongs
 * to the mascot: things squish when you press them, screens arrive with a
 * little hop, and the twin says things in a speech bubble instead of the app
 * saying them in a heading.
 *
 * All of it is built on Compose animation specs, which the system's "remove
 * animations" setting already scales to zero — so with reduced motion on,
 * presses and entrances simply snap. Nothing here needs its own check.
 */

/** True when the user has turned system animations off (reduced motion). */
@Composable
fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** Pair this with a Button: pass [interactionSource] to it and add [modifier]. */
class PressBounce(val interactionSource: MutableInteractionSource, val modifier: Modifier)

/** A springy squish while pressed, with a small overshoot on release. */
@Composable
fun rememberPressBounce(pressedScale: Float = 0.96f): PressBounce {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "pressBounce",
    )
    return PressBounce(
        interactionSource = interactionSource,
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
    )
}

/**
 * Content fades in while hopping up into place. Give siblings increasing
 * [index] values to stagger them down the screen.
 */
@Composable
fun RiseIn(
    index: Int = 0,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    val risePx = with(LocalDensity.current) { RISE_DISTANCE.toPx() }
    LaunchedEffect(Unit) {
        delay(index * STAGGER_MS)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
        )
    }
    Box(
        modifier = modifier.graphicsLayer {
            alpha = progress.value.coerceIn(0f, 1f)
            translationY = (1f - progress.value) * risePx
        },
    ) {
        content()
    }
}

/**
 * The twin saying something: mascot on the left, speech bubble on the right.
 * Use it where a screen would otherwise open with a heading and a paragraph.
 */
@Composable
fun MascotBubble(
    text: String,
    modifier: Modifier = Modifier,
    mascotSize: Dp = 52.dp,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.Top) {
        // The mascot's rings and orbiting dots draw outside its own bounds.
        ListeningAvatar(size = mascotSize, modifier = Modifier.padding(top = 10.dp, start = 10.dp, end = 18.dp))
        Surface(
            color = KlickColors.CardSurface,
            // Squared-off corner nearest the mascot, so it reads as a tail.
            shape = RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp),
            border = BorderStroke(1.dp, KlickColors.Border),
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = KlickColors.TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }
    }
}

private val RISE_DISTANCE = 18.dp
private const val STAGGER_MS = 70L
