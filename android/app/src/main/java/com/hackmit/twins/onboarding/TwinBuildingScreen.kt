package com.hackmit.twins.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hackmit.twins.ui.theme.KlickColors
import kotlin.math.roundToInt

/**
 * A deliberately fake "building your twin" screen shown for a few seconds
 * right after the text dump (and optional Instagram/LinkedIn/Facebook
 * context) is submitted, before landing on Home. The real work — the
 * `submitContext` / `importSocialContext` calls — has already completed by
 * the time this appears (see OnboardingScreen.submitTextDump); this is pure
 * theater so the moment your twin comes into existence feels like something,
 * instead of a network spinner blinking out into the next screen.
 */
private data class BuildStep(val threshold: Float, val message: String)

private val BUILD_STEPS = listOf(
    BuildStep(0f, "Reading everything you told me."),
    BuildStep(0.4f, "Cross-referencing Instagram and LinkedIn."),
    BuildStep(0.78f, "Finding your twin's voice."),
)

private const val DURATION_MS = 2600

@Composable
fun TwinBuildingScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, animationSpec = tween(DURATION_MS, easing = LinearEasing))
        onComplete()
    }

    val percent = (progress.value * 100f).roundToInt().coerceIn(0, 100)
    val step = BUILD_STEPS.last { progress.value >= it.threshold }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(KlickColors.PageBackground),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TwinMascotGlyph()

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "$percent%",
                style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Black),
                color = KlickColors.TextPrimary,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Building your twin",
                style = MaterialTheme.typography.titleMedium,
                color = KlickColors.TextSecondary,
            )

            Spacer(modifier = Modifier.height(20.dp))

            LinearProgressIndicator(
                progress = { progress.value },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = KlickColors.TextPrimary,
                trackColor = KlickColors.Border,
            )

            Spacer(modifier = Modifier.height(20.dp))

            AnimatedContent(
                targetState = step.message,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                label = "buildStepMessage",
            ) { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = KlickColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** The dark rounded-square face with two simple bar eyes — the same mascot
 *  motif used elsewhere, drawn plain here since this screen has no room for
 *  [com.hackmit.twins.ui.ListeningAvatar]'s idle rings/orbit animation. */
@Composable
private fun TwinMascotGlyph() {
    Surface(
        color = KlickColors.TextPrimary,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.size(84.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(2) {
                    Box(
                        modifier = Modifier
                            .width(7.dp)
                            .height(22.dp)
                            .background(KlickColors.OnDark, RoundedCornerShape(3.5.dp)),
                    )
                }
            }
        }
    }
}
