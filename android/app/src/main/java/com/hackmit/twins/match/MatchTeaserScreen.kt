package com.hackmit.twins.match

import com.hackmit.twins.ui.cute.rememberPressBounce
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.hackmit.twins.ui.theme.KlickColors
import com.hackmit.twins.ui.theme.KlickDisplayQuote
import com.hackmit.twins.ui.theme.KlickEyebrow
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

    Scaffold(containerColor = KlickColors.PageBackground) { padding ->
        val d = detail
        if (d == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = KlickColors.TextPrimary)
            }
            return@Scaffold
        }

        val waitingOnOther = d.myApproval == "approved" && d.otherApproval != "approved"

        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Scrolls on its own so a long summary can never squeeze the
            // approve/decline row below it off the screen.
            // Editorial hierarchy, deliberately: the REASON is the largest
            // thing on this screen, not the photo and not the name. This is
            // the screen CLAUDE.md calls the centerpiece, and its rule is
            // that the reasoning is the product — so the sentence gets the
            // display treatment an ordinary app would spend on a match
            // percentage. Photo and blurred name shrink to a byline, which
            // is all a teaser needs them to be.
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = "KLICK FOUND SOMEONE",
                    style = KlickEyebrow,
                    color = KlickColors.Accent,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Byline: face + teased name on one line.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (d.otherPhotoUrl != null) {
                        AsyncImage(
                            model = d.otherPhotoUrl,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp).clip(CircleShape),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(KlickColors.TextPrimary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = null,
                                tint = KlickColors.OnDark,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }

                    Column {
                        // Real name, deliberately blurred — see file header.
                        Text(
                            text = d.otherName,
                            style = MaterialTheme.typography.titleLarge,
                            color = KlickColors.TextPrimary,
                            // Blur only hides pixels; without this a screen
                            // reader reads the real name out before approval.
                            modifier = Modifier
                                .blur(11.dp)
                                .clearAndSetSemantics { contentDescription = "Name hidden" },
                        )
                        Text(
                            text = "Name unlocks when you both say yes",
                            style = MaterialTheme.typography.bodySmall,
                            color = KlickColors.TextTertiary,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }

                // The payoff.
                if (!d.reason.isNullOrBlank()) {
                    Text(
                        text = d.reason,
                        style = KlickDisplayQuote,
                        color = KlickColors.TextPrimary,
                        modifier = Modifier.fillMaxWidth().padding(top = 26.dp),
                    )
                }

                if (!d.summary.isNullOrBlank() || d.interests.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 28.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(KlickColors.CardSurface)
                            .padding(18.dp),
                    ) {
                        if (!d.summary.isNullOrBlank()) {
                            SectionLabel("KEY THINGS ABOUT THEM", topPadding = 0.dp)
                            Text(
                                text = d.summary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = KlickColors.TextPrimary,
                                modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                            )
                        }

                        if (d.interests.isNotEmpty()) {
                            SectionLabel(
                                "WORTH CHATTING ABOUT",
                                topPadding = if (d.summary.isNullOrBlank()) 0.dp else 20.dp,
                            )
                            Text(
                                text = d.interests.joinToString("  ·  "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = KlickColors.TextPrimary,
                                modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                            )
                        }
                    }
                }
            }


            if (waitingOnOther) {
                CircularProgressIndicator(
                    color = KlickColors.TextPrimary,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                Text(
                    text = "Waiting for them to respond too...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = KlickColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val declineBounce = rememberPressBounce()
                    val acceptBounce = rememberPressBounce()
                    OutlinedButton(
                        onClick = { submit(false) },
                        enabled = !submitting,
                        interactionSource = declineBounce.interactionSource,
                        modifier = Modifier.weight(1f).then(declineBounce.modifier),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(vertical = 16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = KlickColors.TextSecondary),
                    ) {
                        Text("Not this time", style = MaterialTheme.typography.labelLarge)
                    }
                    Button(
                        onClick = { submit(true) },
                        enabled = !submitting,
                        interactionSource = acceptBounce.interactionSource,
                        modifier = Modifier.weight(1f).then(acceptBounce.modifier),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(vertical = 16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KlickColors.TextPrimary,
                            contentColor = KlickColors.OnDark,
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
        style = KlickEyebrow,
        color = KlickColors.TextTertiary,
        modifier = Modifier.fillMaxWidth().padding(top = topPadding),
    )
}
