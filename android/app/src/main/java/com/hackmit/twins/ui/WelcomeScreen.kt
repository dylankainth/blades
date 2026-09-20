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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.hackmit.twins.ui.theme.KlickColors
import com.hackmit.twins.ui.theme.SpaceGroteskFamily

/**
 * App-open screen. Same structural idea as the reference design (~2/3
 * gradient hero with the brand wordmark, ~1/3 white action panel below) —
 * grayscale here instead of blue, and no floating bubbles/noise texture.
 *
 * "Build your twin" leads to Sign In, "I've already got one" leads to
 * Sign Up — per the product decision behind this flow, see MainActivity's
 * post-auth routing: signing IN checks for an existing completed twin and
 * goes straight to Home if found, while signing UP is always a fresh
 * account and always lands in Onboarding.
 */
@Composable
fun WelcomeScreen(
    onGoToSignIn: () -> Unit,
    onGoToSignUp: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
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
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Klick",
                fontFamily = SpaceGroteskFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 56.sp,
                letterSpacing = (-0.03).em,
                color = KlickColors.OnDark,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.32f)
                .background(KlickColors.PageBackground)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            Button(
                onClick = onGoToSignIn,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(vertical = 18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = KlickColors.TextPrimary,
                    contentColor = KlickColors.OnDark,
                ),
            ) {
                Text("Build your twin", style = MaterialTheme.typography.titleMedium)
            }

            OutlinedButton(
                onClick = onGoToSignUp,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(vertical = 18.dp),
                border = BorderStroke(1.5.dp, KlickColors.TextPrimary),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = KlickColors.TextPrimary,
                ),
            ) {
                Text("I've already got one", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
