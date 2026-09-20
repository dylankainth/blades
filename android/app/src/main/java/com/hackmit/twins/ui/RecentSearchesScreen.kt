package com.hackmit.twins.ui

import com.hackmit.twins.ui.cute.RiseIn
import com.hackmit.twins.ui.cute.MascotBubble
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Full negotiation feed — confirmed matches and dismissed non-matches
 * alike — moved off Home onto its own swipe-down destination (see
 * HomePagerScreen.kt), with a deliberately reversed palette (black
 * background, white text) so it reads as a distinct "look back" space
 * rather than more of the same idle screen.
 */
private val Bg = Color.Black
private val Surface = Color(0xFF161616)
private val Border = Color(0xFF2E2E2E)
private val TextPrimary = Color.White
private val TextSecondary = Color(0xFFA0A0A0)
private val Accent = Color(0xFFE0985C) // same amber family as KlickColors.Accent, brightened for a dark bg

@Composable
fun RecentSearchesScreen(
    feed: List<MatchFeedItem>,
    onOpenMatch: (MatchFeedItem) -> Unit,
    onOpenNegotiationDetail: (MatchFeedItem) -> Unit,
    onBackToHome: () -> Unit,
    onShowContext: () -> Unit,
) {
    Scaffold(containerColor = Bg) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onBackToHome).padding(top = 12.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowUp,
                    contentDescription = "Swipe up for home",
                    tint = TextSecondary,
                )
            }

            Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Text(
                    text = "Recent searches",
                    style = MaterialTheme.typography.headlineLarge,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )

                if (feed.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        RiseIn {
                            MascotBubble(
                                text = "Nobody yet. I'm still working the room. Go wander, I'll " +
                                    "tap you on the shoulder when it's worth it.",
                                modifier = Modifier.padding(horizontal = 24.dp),
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(feed) { item ->
                            RecentSearchRow(
                                item = item,
                                onClick = {
                                    if (item.status == "confirmed") onOpenMatch(item) else onOpenNegotiationDetail(item)
                                },
                            )
                        }
                    }
                }
            }

            // Points further down at TwinContextScreen — same affordance
            // pattern as Home's "Check my recent searches" hint, just
            // reversed-palette to match this screen.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onShowContext)
                    .padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "See everything it's learned",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Swipe down for everything it's learned",
                    tint = TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun RecentSearchRow(item: MatchFeedItem, onClick: () -> Unit) {
    val isConfirmed = item.status == "confirmed"
    val isRevealed = item.revealStatus == "revealed"
    // Deliberate: even though the owner's own device can technically read
    // the real name/photo for a dismissed pairing (see MatchFeedRepository
    // — matches/{matchId} is participant-readable), we don't surface it as
    // a headline here. A "Someone nearby" you weren't matched with doesn't
    // need to be identified, even to you. A confirmed match still keeps the
    // name hidden until both people have passed the human-approval gate on
    // the Match Teaser screen (revealStatus == "revealed") — otherwise this
    // list would leak the identity the Teaser screen deliberately blurs.
    val displayName = when {
        isRevealed -> item.otherName
        isConfirmed -> "Someone new"
        else -> "Someone nearby"
    }
    val displayPhotoUrl = if (isRevealed) item.otherPhotoUrl else null

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        border = BorderStroke(1.dp, Border),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (displayPhotoUrl != null) {
                AsyncImage(
                    model = displayPhotoUrl,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).clip(CircleShape),
                )
            } else {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(TextPrimary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (isRevealed) displayName.take(1).uppercase() else "?",
                        color = Bg,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                )
                Text(
                    text = if (isConfirmed) {
                        item.reason ?: "Worth talking to — tap to see why"
                    } else {
                        "Not a strong match"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }

            Text(
                text = if (isConfirmed) "Say hi →" else "See why →",
                style = MaterialTheme.typography.labelMedium,
                color = if (isConfirmed) Accent else TextSecondary,
            )
        }
    }
}
