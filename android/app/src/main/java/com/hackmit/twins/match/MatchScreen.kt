package com.hackmit.twins.match

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.hackmit.twins.ui.theme.KindredColors
import com.hackmit.twins.ui.theme.KindredDisplayNumeral

/**
 * The handoff screen: shows who your twin found, the one specific reason,
 * and a single confirm action. This is intentionally the entire feature —
 * no auto-generated intro message, no scheduling, no chat with the matched
 * person's twin. Tapping "I'll say hi" just acknowledges the suggestion;
 * the actual conversation happens human-to-human, in person. See CLAUDE.md
 * guardrails: twins surface and suggest, they never decide/message/book on
 * a human's behalf.
 *
 * Styled after design/Kindred App.dc.html's "Ping" screen: amber eyebrow
 * label, the matched name as an oversized headline, dark pill CTA.
 */
@Composable
fun MatchScreen(
    matchedName: String,
    matchedPhotoUrl: String?,
    reason: String,
    onSayHiConfirmed: () -> Unit,
) {
    var confirmed by remember { mutableStateOf(false) }

    Scaffold(containerColor = KindredColors.PageBackground) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(28.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Kindred found someone",
                style = MaterialTheme.typography.labelLarge,
                color = KindredColors.Accent,
            )

            if (matchedPhotoUrl != null) {
                AsyncImage(
                    model = matchedPhotoUrl,
                    contentDescription = "$matchedName's photo",
                    modifier = Modifier
                        .padding(top = 20.dp)
                        .size(56.dp)
                        .clip(CircleShape),
                )
            } else {
                Column(
                    modifier = Modifier
                        .padding(top = 20.dp)
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(KindredColors.TextPrimary),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = KindredColors.OnDark,
                    )
                }
            }

            Text(
                text = matchedName,
                style = KindredDisplayNumeral,
                color = KindredColors.TextPrimary,
                modifier = Modifier.padding(top = 18.dp),
            )

            Text(
                text = reason,
                style = MaterialTheme.typography.bodyLarge,
                color = KindredColors.TextSecondary,
                modifier = Modifier
                    .padding(top = 18.dp, bottom = 40.dp)
                    .fillMaxWidth(),
            )

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !confirmed,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = KindredColors.TextPrimary,
                    contentColor = KindredColors.OnDark,
                    disabledContainerColor = KindredColors.TextPrimary,
                    disabledContentColor = KindredColors.OnDark,
                ),
                onClick = {
                    confirmed = true
                    onSayHiConfirmed()
                },
            ) {
                Text(
                    text = if (confirmed) "Nice — go say hi!" else "I'll say hi",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
