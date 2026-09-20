package com.hackmit.twins.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hackmit.twins.ui.theme.KlickColors
import com.hackmit.twins.ui.theme.KlickDisplayQuote
import com.hackmit.twins.ui.theme.KlickEyebrow

/**
 * "Why weren't we a match" screen — the full negotiation transcript between
 * the two twins, followed by the score and reason the negotiation converged
 * on. Read-only, no actions: this is transparency into what the twins
 * actually discussed, not something to act on (that's MatchScreen's job,
 * for actual matches).
 *
 * Only ever reached for a non-match (see MainActivity's routing), so the
 * title deliberately never shows the other person's real name — a
 * rejected candidate isn't identified, even to the person who tapped in.
 * The transcript body below may still reference their name in-character
 * (the twins' own dialogue), which is a conscious, narrower trade-off —
 * see CLAUDE.md / the Firestore rules comment on matches/judge_feed for
 * the fuller anonymization story.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NegotiationDetailScreen(
    detail: NegotiationDetail?,
) {
    Scaffold(
        containerColor = KlickColors.PageBackground,
        topBar = {
            TopAppBar(
                title = { Text("Someone nearby") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = KlickColors.PageBackground,
                ),
            )
        },
    ) { padding ->
        if (detail == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = KlickColors.TextPrimary)
            }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(detail.transcript) { turn -> TranscriptBubble(turn) }
            }

            // The verdict. Deliberately NOT a score: CLAUDE.md's central
            // rule is that the product surfaces a plain-language reason and
            // never a percentage, and this screen is the one place a user
            // sees a negotiation's conclusion in full. The numeric score
            // still gates the outcome server-side (MATCH_SCORE_THRESHOLD in
            // negotiateTwins.ts) — it just isn't what we hand the human.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(KlickColors.CardSurface)
                    .padding(horizontal = 24.dp, vertical = 26.dp),
            ) {
                Text(
                    text = "WHAT YOUR TWINS CONCLUDED",
                    style = KlickEyebrow,
                    color = KlickColors.Accent,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = detail.reason?.takeIf { it.isNotBlank() }
                        ?: "Your twins talked it through and didn't find a real reason for you two to meet.",
                    style = KlickDisplayQuote,
                    color = KlickColors.TextPrimary,
                )
            }
        }
    }
}

@Composable
private fun TranscriptBubble(turn: NegotiationTurnUi) {
    val alignment = if (turn.fromMe) Alignment.CenterEnd else Alignment.CenterStart
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (turn.fromMe) KlickColors.TextPrimary else KlickColors.CardSurface,
            ),
            border = if (turn.fromMe) null else BorderStroke(1.dp, KlickColors.Border),
        ) {
            Text(
                text = turn.text,
                modifier = Modifier.padding(14.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (turn.fromMe) KlickColors.OnDark else KlickColors.TextPrimary,
            )
        }
    }
}
