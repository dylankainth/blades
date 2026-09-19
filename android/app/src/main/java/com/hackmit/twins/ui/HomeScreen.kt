package com.hackmit.twins.ui

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hackmit.twins.ui.theme.KindredColors

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
fun HomeScreen(onShowRecent: () -> Unit) {
    Scaffold(
        containerColor = KindredColors.PageBackground,
        topBar = {
            TopAppBar(
                title = { Text("Kindred") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = KindredColors.PageBackground,
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
                ListeningAvatar(size = 148.dp)
                Text(
                    text = "Actively engaging nearby",
                    style = MaterialTheme.typography.titleMedium,
                    color = KindredColors.TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 18.dp),
                )
                Text(
                    text = "Working quietly — no pings unless it's worth it.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = KindredColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
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
                    color = KindredColors.TextSecondary,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Swipe down for recent searches",
                    tint = KindredColors.TextSecondary,
                )
            }
        }
    }
}
