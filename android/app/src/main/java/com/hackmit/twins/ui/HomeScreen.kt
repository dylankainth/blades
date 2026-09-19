package com.hackmit.twins.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.hackmit.twins.ui.theme.KindredColors
import com.hackmit.twins.ui.theme.KindredDisplayNumeral

/**
 * Idle/main screen: BleProximityService is already running in the
 * background at this point, so there's not much "to do" here — the point
 * is that matching happens passively. Below the status hero, a live feed
 * of this twin's recent negotiations: dismissed non-matches show inline
 * (read-only), and tapping a confirmed match reopens the same dedicated
 * MatchScreen a push notification would open (see MainActivity).
 *
 * Styled after design/Kindred App.dc.html's "Ambient" screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    twinId: String,
    onOpenCheckin: () -> Unit,
    onOpenMatch: (MatchFeedItem) -> Unit,
    onOpenNegotiationDetail: (MatchFeedItem) -> Unit,
) {
    var feed by remember { mutableStateOf<List<MatchFeedItem>>(emptyList()) }

    DisposableEffect(twinId) {
        val registration = MatchFeedRepository.listen(twinId) { feed = it }
        onDispose { registration.remove() }
    }

    Scaffold(
        containerColor = KindredColors.PageBackground,
        topBar = {
            TopAppBar(
                title = { Text("Kindred") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = KindredColors.PageBackground,
                ),
                actions = {
                    IconButton(onClick = onOpenCheckin) {
                        Icon(
                            Icons.Filled.Menu,
                            contentDescription = "Manual check-in",
                            tint = KindredColors.TextPrimary,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Listening",
                    style = KindredDisplayNumeral,
                    color = KindredColors.TextPrimary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Working quietly — no pings unless it's worth it.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = KindredColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp),
                )
                OutlinedButton(
                    onClick = onOpenCheckin,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, KindredColors.Border),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = KindredColors.TextSecondary,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                ) {
                    Text("Manual check-in", style = MaterialTheme.typography.labelLarge)
                }
            }

            if (feed.isNotEmpty()) {
                Text(
                    text = "Recent",
                    style = MaterialTheme.typography.labelLarge,
                    color = KindredColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 24.dp,
                        vertical = 4.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(feed) { item ->
                        MatchFeedRow(
                            item = item,
                            onClick = {
                                if (item.status == "confirmed") {
                                    onOpenMatch(item)
                                } else {
                                    onOpenNegotiationDetail(item)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchFeedRow(item: MatchFeedItem, onClick: () -> Unit) {
    val isConfirmed = item.status == "confirmed"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = KindredColors.CardSurface),
        border = BorderStroke(1.dp, KindredColors.Border),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (item.otherPhotoUrl != null) {
                AsyncImage(
                    model = item.otherPhotoUrl,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).clip(CircleShape),
                )
            } else {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(KindredColors.TextPrimary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = item.otherName.take(1).uppercase(),
                        color = KindredColors.OnDark,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(
                    text = item.otherName,
                    style = MaterialTheme.typography.titleMedium,
                    color = KindredColors.TextPrimary,
                )
                Text(
                    text = if (isConfirmed) {
                        item.reason ?: "Worth talking to — tap to see why"
                    } else {
                        "Not a strong match"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = KindredColors.TextSecondary,
                )
            }

            Text(
                text = if (isConfirmed) "Say hi →" else "See why →",
                style = MaterialTheme.typography.labelMedium,
                color = if (isConfirmed) KindredColors.Accent else KindredColors.TextSecondary,
            )
        }
    }
}
