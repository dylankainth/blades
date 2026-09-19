package com.hackmit.twins.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.hackmit.twins.ui.theme.KindredColors
import com.hackmit.twins.ui.theme.KindredDisplayNumeral

/**
 * "Why weren't we a match" screen — the full negotiation transcript between
 * the two twins, followed by the score and reason the negotiation converged
 * on. Read-only, no actions: this is transparency into what the twins
 * actually discussed, not something to act on (that's MatchScreen's job,
 * for actual matches).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NegotiationDetailScreen(
    otherName: String,
    detail: NegotiationDetail?,
) {
    Scaffold(
        containerColor = KindredColors.PageBackground,
        topBar = {
            TopAppBar(
                title = { Text(otherName) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = KindredColors.PageBackground,
                ),
            )
        },
    ) { padding ->
        if (detail == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = KindredColors.TextPrimary)
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

            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
            ) {
                Text(
                    text = "Alignment score",
                    style = MaterialTheme.typography.labelLarge,
                    color = KindredColors.TextSecondary,
                )
                Text(
                    text = "${detail.score ?: 0}",
                    style = KindredDisplayNumeral,
                    color = KindredColors.TextPrimary,
                )
                if (!detail.reason.isNullOrBlank()) {
                    Text(
                        text = detail.reason,
                        style = MaterialTheme.typography.bodyLarge,
                        color = KindredColors.TextSecondary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
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
                containerColor = if (turn.fromMe) KindredColors.TextPrimary else KindredColors.CardSurface,
            ),
            border = if (turn.fromMe) null else BorderStroke(1.dp, KindredColors.Border),
        ) {
            Text(
                text = turn.text,
                modifier = Modifier.padding(14.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (turn.fromMe) KindredColors.OnDark else KindredColors.TextPrimary,
            )
        }
    }
}
