package com.hackmit.twins.match

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.hackmit.twins.ble.BleProximityService
import com.hackmit.twins.ui.theme.KlickColors
import kotlinx.coroutines.delay

/** How long the "you met" confirmation stays up before the radar closes itself. */
private const val MET_DISMISS_MS = 3_500L

/**
 * Both people approved — real identity is fully revealed here (unlike
 * MatchTeaserScreen's blurred name), plus a live "find each other" radar
 * driven by the other person's real BLE signal strength (RSSI), not a
 * decorative animation. See BleProximityService's nearbyRssi.
 *
 * Signal strength is a rough, noisy stand-in for distance (no UWB on these
 * phones) — good for "warmer/colder" feedback, not precise range/direction.
 *
 * Once the pair shakes their badges the backend sets matches/{id}.metAt; the
 * radar then confirms it and closes itself via [onMet], since there is
 * nobody left to find.
 */
@Composable
fun RadarScreen(
    myTwinId: String,
    otherTwinId: String,
    otherName: String,
    otherPhotoUrl: String?,
    onMet: () -> Unit,
) {
    var met by remember { mutableStateOf(false) }
    DisposableEffect(myTwinId, otherTwinId) {
        // Same id the backend derives in negotiateTwins.ts: both twin ids, sorted.
        val matchId = listOf(myTwinId, otherTwinId).sorted().joinToString("_")
        val registration = MatchDetailRepository.listen(matchId, myTwinId) { detail ->
            if (detail?.met == true) met = true
        }
        onDispose { registration.remove() }
    }

    val haptics = LocalHapticFeedback.current
    val currentOnMet by rememberUpdatedState(onMet)
    LaunchedEffect(met) {
        if (!met) return@LaunchedEffect
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        delay(MET_DISMISS_MS)
        currentOnMet()
    }

    val rssiByTwin by BleProximityService.nearbyRssi.collectAsState()
    val rssi = rssiByTwin[otherTwinId]

    // -100 dBm (no signal) -> 0f, -40 dBm (very close) -> 1f.
    val targetCloseness = if (met) 1f else rssi?.let { ((it + 100f) / 60f).coerceIn(0f, 1f) } ?: 0f
    val closeness by animateFloatAsState(
        targetValue = targetCloseness,
        animationSpec = tween(600),
        label = "closeness",
    )

    val statusText = when {
        met -> "You met $otherName"
        rssi == null -> "Searching for them..."
        closeness > 0.85f -> "Very close — look around!"
        closeness > 0.55f -> "Getting warmer"
        closeness > 0.25f -> "Somewhere nearby"
        else -> "Still far — keep moving"
    }

    Scaffold(containerColor = KlickColors.PageBackground) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (otherPhotoUrl != null) {
                AsyncImage(
                    model = otherPhotoUrl,
                    contentDescription = null,
                    modifier = Modifier.padding(top = 8.dp).size(64.dp).clip(CircleShape),
                )
            } else {
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(KlickColors.TextPrimary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = KlickColors.OnDark)
                }
            }
            Text(
                text = otherName,
                style = MaterialTheme.typography.headlineLarge,
                color = KlickColors.TextPrimary,
                modifier = Modifier.padding(top = 10.dp),
            )

            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                RadarRings(closeness = closeness)
            }

            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium,
                color = KlickColors.TextPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = if (met) {
                    "Marked as met. Closing this match."
                } else {
                    "Signal strength only — not exact distance or direction."
                },
                style = MaterialTheme.typography.bodySmall,
                color = KlickColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun RadarRings(closeness: Float) {
    Canvas(modifier = Modifier.size(260.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val maxRadius = size.minDimension / 2
        // Three rings, spaced further apart when far, tighter (converging
        // on the center dot) as closeness increases — the "getting warmer"
        // visual cue.
        val ringCount = 3
        for (i in 0 until ringCount) {
            val spread = 1f - closeness * 0.7f
            val radius = maxRadius * (0.35f + (i + 1) * 0.2f * spread)
            val alpha = (0.5f - i * 0.12f).coerceIn(0.08f, 0.5f)
            drawCircle(
                color = KlickColors.TextPrimary.copy(alpha = alpha),
                radius = radius,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f),
            )
        }
        // Center dot brightens/grows with closeness.
        val dotRadius = maxRadius * (0.08f + closeness * 0.10f)
        drawCircle(
            color = if (closeness > 0.55f) KlickColors.Accent else KlickColors.TextPrimary,
            radius = dotRadius,
            center = center,
        )
    }
}
