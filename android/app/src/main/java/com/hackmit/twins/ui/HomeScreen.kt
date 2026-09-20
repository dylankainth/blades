package com.hackmit.twins.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hackmit.twins.badge.BadgeMenu
import com.hackmit.twins.badge.PairBadgeButton
import com.hackmit.twins.ui.theme.KlickColors
import kotlinx.coroutines.delay

/** Rotates below the character — small, warm status lines rather than
 *  a single static caption, so the idle screen feels alive over time. */
private val SPLASH_MESSAGES = listOf(
    "No pings unless it's worth it.",
    "Listening for the right kind of coincidence.",
    "Not every stranger — just the right one.",
    "Quietly comparing notes nearby.",
    "Waiting for a good reason to interrupt you.",
    "Reading the room.",
    "Looking for someone worth the detour.",
    "Nothing yet. That's fine.",
    "Filtering out the small talk.",
    "Still here. Still watching.",
)

/**
 * Idle/main screen: BleProximityService is already running in the
 * background at this point, so there's not much "to do" here — the whole
 * point is that matching happens passively while the character sits there
 * listening. The negotiation feed used to live inline here; it's now a
 * separate full-screen swipe-down destination (see HomePagerScreen.kt /
 * RecentSearchesScreen.kt) — this screen just shows the character, centered,
 * and a hint at the bottom pointing at it.
 *
 * Styled after design/Kindred App.dc.html's "Ambient" screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onShowRecent: () -> Unit, onLogout: () -> Unit) {
    Scaffold(
        containerColor = KlickColors.PageBackground,
        topBar = {
            TopAppBar(
                title = { Text("Klick") },
                actions = { BadgeMenu(onLogout = onLogout) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = KlickColors.PageBackground,
                ),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Actively engaging nearby",
                    style = MaterialTheme.typography.titleMedium,
                    color = KlickColors.TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 40.dp),
                )
                ListeningAvatar(size = 148.dp)

                var splashIndex by remember { mutableIntStateOf(0) }
                LaunchedEffect(Unit) {
                    while (true) {
                        delay(3500)
                        splashIndex = (splashIndex + 1) % SPLASH_MESSAGES.size
                    }
                }
                AnimatedContent(
                    targetState = splashIndex,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "splash",
                    modifier = Modifier.padding(top = 28.dp),
                ) { index ->
                    Text(
                        text = SPLASH_MESSAGES[index],
                        style = MaterialTheme.typography.bodyLarge,
                        color = KlickColors.TextSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
                PairBadgeButton(modifier = Modifier.padding(top = 28.dp))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onShowRecent)
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Check my recent searches",
                    style = MaterialTheme.typography.labelLarge,
                    color = KlickColors.TextSecondary,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Swipe down for recent searches",
                    tint = KlickColors.TextSecondary,
                )
            }
        }
    }
}
