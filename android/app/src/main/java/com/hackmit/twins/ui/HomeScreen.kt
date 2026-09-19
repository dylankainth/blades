package com.hackmit.twins.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hackmit.twins.ui.theme.KindredColors
import com.hackmit.twins.ui.theme.KindredDisplayNumeral

/**
 * Idle/main screen: BleProximityService is already running in the
 * background at this point (started from MainActivity right after sign-in),
 * so there's deliberately not much to show here — the whole point is that
 * matching happens passively while the user goes about the event. This is
 * just an entry point to the manual check-in fallback for demo reliability.
 *
 * Styled after design/Kindred App.dc.html's "Ambient" screen: quiet status,
 * one oversized headline word, no busywork UI.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenCheckin: () -> Unit) {
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
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
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
                modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
            ) {
                Text("Manual check-in", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
