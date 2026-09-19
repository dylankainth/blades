package com.hackmit.twins.checkin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hackmit.twins.ui.theme.KindredColors

/**
 * Demo-day reliability fallback for the BLE proximity trigger (see
 * CLAUDE.md: "fake the physical/sensor layer, keep the intelligence layer
 * real"). Writes to the EXACT SAME Firestore path BleProximityService
 * writes to (checkins/{locationId}/people/{twinId}) via the shared
 * CheckinRepository, so the backend matching Cloud Function doesn't need to
 * know or care whether a checkin came from real BLE detection or a manual
 * "I'm at booth X" tap — it's the same trigger either way.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckinScreen(
    twinId: String,
    onCheckedIn: (locationId: String) -> Unit,
) {
    var lastCheckedInLocation by remember { mutableStateOf<String?>(null) }

    // Hard-coded demo venue locations. Swap/extend this list for the actual
    // HackMIT floor plan before demo day.
    val locations = remember {
        listOf(
            "Main stage",
            "Booth 1",
            "Booth 2",
            "Booth 3",
            "Snack table",
            "Hallway track",
        )
    }

    Scaffold(
        containerColor = KindredColors.PageBackground,
        topBar = {
            TopAppBar(
                title = { Text("Manual check-in") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = KindredColors.PageBackground,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "BLE not cooperating? Tap where you are and we'll use " +
                    "that instead.",
                style = MaterialTheme.typography.bodyMedium,
                color = KindredColors.TextSecondary,
            )

            locations.forEach { locationName ->
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, KindredColors.Border),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = KindredColors.TextPrimary,
                    ),
                    onClick = {
                        val locationId = locationName.toLocationId()
                        CheckinRepository.recordCheckin(locationId, twinId)
                        lastCheckedInLocation = locationName
                        onCheckedIn(locationId)
                    },
                ) {
                    Text("I'm at $locationName", style = MaterialTheme.typography.labelLarge)
                }
            }

            lastCheckedInLocation?.let {
                Text(
                    text = "Checked in at $it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = KindredColors.TextSecondary,
                )
            }
        }
    }
}

private fun String.toLocationId(): String =
    lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
