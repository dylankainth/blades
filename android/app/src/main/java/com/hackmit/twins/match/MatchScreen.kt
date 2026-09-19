package com.hackmit.twins.match

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * The handoff screen: shows who your twin found, the one specific reason,
 * and a single confirm action. This is intentionally the entire feature —
 * no auto-generated intro message, no scheduling, no chat with the matched
 * person's twin. Tapping "I'll say hi" just acknowledges the suggestion;
 * the actual conversation happens human-to-human, in person. See CLAUDE.md
 * guardrails: twins surface and suggest, they never decide/message/book on
 * a human's behalf.
 */
@Composable
fun MatchScreen(
    matchedName: String,
    matchedPhotoUrl: String?,
    reason: String,
    onSayHiConfirmed: () -> Unit,
) {
    var confirmed by remember { mutableStateOf(false) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (matchedPhotoUrl != null) {
                AsyncImage(
                    model = matchedPhotoUrl,
                    contentDescription = "$matchedName's photo",
                    modifier = Modifier.size(120.dp).clip(CircleShape),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    modifier = Modifier.size(120.dp),
                )
            }

            Text(
                text = matchedName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp),
            )

            Text(
                text = reason,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )

            Button(
                modifier = Modifier.padding(top = 32.dp).fillMaxWidth(),
                enabled = !confirmed,
                onClick = {
                    confirmed = true
                    onSayHiConfirmed()
                },
            ) {
                Text(if (confirmed) "Nice — go say hi!" else "I'll say hi")
            }
        }
    }
}
