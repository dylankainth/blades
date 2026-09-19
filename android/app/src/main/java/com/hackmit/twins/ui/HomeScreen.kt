package com.hackmit.twins.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Idle/main screen: BleProximityService is already running in the
 * background at this point (started from MainActivity right after sign-in),
 * so there's deliberately not much to show here — the whole point is that
 * matching happens passively while the user goes about the event. This is
 * just an entry point to the manual check-in fallback for demo reliability.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenCheckin: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your twin") },
                actions = {
                    IconButton(onClick = onOpenCheckin) {
                        Icon(Icons.Filled.Menu, contentDescription = "Manual check-in")
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
                text = "Your twin is listening for connections nearby.",
                style = MaterialTheme.typography.titleMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Text(
                text = "We'll notify you when it finds someone worth meeting.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            Button(onClick = onOpenCheckin, modifier = Modifier.padding(top = 24.dp)) {
                Text("Manual check-in")
            }
        }
    }
}
