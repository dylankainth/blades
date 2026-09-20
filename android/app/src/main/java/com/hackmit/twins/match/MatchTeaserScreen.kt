package com.hackmit.twins.match

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.hackmit.twins.ui.theme.KindredColors
import kotlinx.coroutines.launch

/**
 * The second, human consent gate on top of the AI's "confirmed" — see
 * CLAUDE.md guardrails and submitMatchApproval.ts's file header. Shows a
 * real photo but a *blurred* name (visible but unreadable — a tease, not a
 * redaction), a few key facts, and conversation starters, then requires an
 * explicit Approve/Disapprove. Full identity + the BLE radar only unlock
 * once BOTH people approve; either person disapproving quietly cancels it,
 * with no explanation sent to the other side.
 */
@Composable
fun MatchTeaserScreen(
    matchId: String,
    myTwinId: String,
    onCancelled: () -> Unit,
    onRevealed: (otherTwinId: String, otherName: String, otherPhotoUrl: String?) -> Unit,
) {
    var detail by remember { mutableStateOf<MatchLiveDetail?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    DisposableEffect(matchId, myTwinId) {
        val registration = MatchDetailRepository.listen(matchId, myTwinId) { detail = it }
        onDispose { registration.remove() }
    }

    // React to the live doc regardless of who caused the change — covers
    // both "I just approved and they'd already approved" and "they
    // approved a moment after I did, while this screen stayed open".
    LaunchedEffect(detail?.revealStatus) {
        val d = detail ?: return@LaunchedEffect
        if (d.revealStatus == "revealed") {
            onRevealed(d.otherTwinId, d.otherName, d.otherPhotoUrl)
        } else if (d.revealStatus == "cancelled") {
            onCancelled()
        }
    }

    fun submit(approve: Boolean) {
        if (submitting) return
        submitting = true
        scope.launch {
            try {
                val status = MatchDetailRepository.submitApproval(matchId, approve)
                if (status == "cancelled") onCancelled()
                // "revealed" and "pending" are both handled by the
                // LaunchedEffect above once the listener picks up the write.
            } finally {
                submitting = false
            }
        }
    }

    Scaffold(containerColor = KindredColors.PageBackground) { padding ->
        val d = detail
        if (d == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = KindredColors.TextPrimary)
            }
            return@Scaffold
        }

        val waitingOnOther = d.myApproval == "approved" && d.otherApproval != "approved"

        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Kindred found someone",
                style = MaterialTheme.typography.labelLarge,
                color = KindredColors.Accent,
                modifier = Modifier.fillMaxWidth(),
            )

            if (d.otherPhotoUrl != null) {
                AsyncImage(
                    model = d.otherPhotoUrl,
                    contentDescription = null,
                    modifier = Modifier.padding(top = 16.dp).size(140.dp).clip(CircleShape),
                )
            } else {
                Box(
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .size(140.dp)
                        .clip(CircleShape)
                        .background(KindredColors.TextPrimary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = KindredColors.OnDark,
                        modifier = Modifier.size(56.dp),
                    )
                }
            }

            // Real name, deliberately blurred — see file header.
            Text(
                text = d.otherName,
                style = MaterialTheme.typography.headlineLarge,
                color = KindredColors.TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 14.dp).blur(14.dp),
            )

            if (!d.reason.isNullOrBlank()) {
                Text(
                    text = d.reason,
                    style = MaterialTheme.typography.bodyLarge,
                    color = KindredColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            if (!d.summary.isNullOrBlank()) {
                SectionLabel("Key things about them", topPadding = 26.dp)
                Text(
                    text = d.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = KindredColors.TextPrimary,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }

            if (d.interests.isNotEmpty()) {
                SectionLabel("Worth chatting about", topPadding = 20.dp)
                Text(
                    text = d.interests.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = KindredColors.TextPrimary,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }

            Box(modifier = Modifier.weight(1f))

            if (waitingOnOther) {
                CircularProgressIndicator(
                    color = KindredColors.TextPrimary,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                Text(
                    text = "Waiting for them to respond too...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = KindredColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = { submit(false) },
                        enabled = !submitting,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(vertical = 16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = KindredColors.TextSecondary),
                    ) {
                        Text("Not this time", style = MaterialTheme.typography.labelLarge)
                    }
                    Button(
                        onClick = { submit(true) },
                        enabled = !submitting,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(vertical = 16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KindredColors.TextPrimary,
                            contentColor = KindredColors.OnDark,
                        ),
                    ) {
                        Text("I'm in", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, topPadding: androidx.compose.ui.unit.Dp) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = KindredColors.TextSecondary,
        modifier = Modifier.fillMaxWidth().padding(top = topPadding),
    )
}
