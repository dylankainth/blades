package com.hackmit.twins.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.hackmit.twins.ui.cute.RiseIn
import com.hackmit.twins.ui.cute.rememberPressBounce
import com.hackmit.twins.ui.theme.KlickColors
import com.hackmit.twins.ui.theme.SpaceGroteskFamily
import com.hackmit.twins.ui.theme.StatusBarStyle

/**
 * App-open screen: a ~2/3 gradient hero (mascot, wordmark, one-line promise)
 * over a ~1/3 action panel.
 *
 * The two buttons say what they do. "Build your twin" is for someone new and
 * goes to Sign Up (always a fresh account, always lands in Onboarding). "I
 * already have a twin" goes to Sign In, which checks for a completed twin and
 * goes straight to Home if there is one. They used to be wired the other way
 * round, which made no sense to anyone arriving signed-out, see MainActivity's
 * post-auth routing for what happens after either.
 */
@Composable
fun WelcomeScreen(
    onGoToSignIn: () -> Unit,
    onGoToSignUp: () -> Unit,
) {
    // The hero starts dark, so the status bar joins it instead of sitting
    // above it as a pale band.
    StatusBarStyle(color = KlickColors.TextSecondary, darkIcons = false)

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.68f)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            KlickColors.TextSecondary,
                            KlickColors.TextPrimary,
                        ),
                    ),
                )
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            RiseIn(index = 0) {
                // Extra room: the mascot's rings and dots draw past its bounds.
                Box(modifier = Modifier.padding(28.dp)) {
                    ListeningAvatar(size = 112.dp, onDark = true)
                }
            }
            RiseIn(index = 1) {
                Text(
                    text = "Klick",
                    fontFamily = SpaceGroteskFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 56.sp,
                    letterSpacing = (-0.03).em,
                    color = KlickColors.OnDark,
                )
            }
            RiseIn(index = 2) {
                Text(
                    text = "Your twin works the room,\nso you only meet the good ones.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = KlickColors.OnDark.copy(alpha = 0.72f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.32f)
                .background(KlickColors.PageBackground)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            val primaryBounce = rememberPressBounce()
            RiseIn(index = 3) {
                Button(
                    onClick = onGoToSignUp,
                    modifier = Modifier.fillMaxWidth().then(primaryBounce.modifier),
                    interactionSource = primaryBounce.interactionSource,
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(vertical = 18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KlickColors.TextPrimary,
                        contentColor = KlickColors.OnDark,
                    ),
                ) {
                    Text("Build your twin", style = MaterialTheme.typography.titleMedium)
                }
            }

            val secondaryBounce = rememberPressBounce()
            RiseIn(index = 4) {
                OutlinedButton(
                    onClick = onGoToSignIn,
                    modifier = Modifier.fillMaxWidth().then(secondaryBounce.modifier),
                    interactionSource = secondaryBounce.interactionSource,
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(vertical = 18.dp),
                    border = BorderStroke(1.5.dp, KlickColors.TextPrimary),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = KlickColors.TextPrimary,
                    ),
                ) {
                    Text("I already have a twin", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
