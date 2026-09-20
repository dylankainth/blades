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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.hackmit.twins.ble.BleProximityService
import com.hackmit.twins.ui.theme.KindredColors

/**
 * Both people approved — real identity is fully revealed here (unlike
 * MatchTeaserScreen's blurred name), plus a live "find each other" radar
 * driven by the other person's real BLE signal strength (RSSI), not a
 * decorative animation. See BleProximityService's nearbyRssi.
 *
 * Signal strength is a rough, noisy stand-in for distance (no UWB on these
 * phones) — good for "warmer/colder" feedback, not precise range/direction.
 */
@Composable
fun RadarScreen(
    otherTwinId: String,
    otherName: String,
    otherPhotoUrl: String?,
) {
    val rssiByTwin by BleProximityService.nearbyRssi.collectAsState()
    val rssi = rssiByTwin[otherTwinId]

    // -100 dBm (no signal) -> 0f, -40 dBm (very close) -> 1f.
    val targetCloseness = rssi?.let { ((it + 100f) / 60f).coerceIn(0f, 1f) } ?: 0f
    val closeness by animateFloatAsState(
        targetValue = targetCloseness,
        animationSpec = tween(600),
        label = "closeness",
    )

    val statusText = when {
        rssi == null -> "Searching for them..."
        closeness > 0.85f -> "Very close — look around!"
        closeness > 0.55f -> "Getting warmer"
        closeness > 0.25f -> "Somewhere nearby"
        else -> "Still far — keep moving"
    }

    Scaffold(containerColor = KindredColors.PageBackground) { padding ->
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
                        .background(KindredColors.TextPrimary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = KindredColors.OnDark)
                }
            }
            Text(
                text = otherName,
                style = MaterialTheme.typography.headlineLarge,
                color = KindredColors.TextPrimary,
                modifier = Modifier.padding(top = 10.dp),
            )

            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                RadarRings(closeness = closeness)
            }

            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium,
                color = KindredColors.TextPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Signal strength only — not exact distance or direction.",
                style = MaterialTheme.typography.bodySmall,
                color = KindredColors.TextSecondary,
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
                color = KindredColors.TextPrimary.copy(alpha = alpha),
                radius = radius,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f),
            )
        }
        // Center dot brightens/grows with closeness.
        val dotRadius = maxRadius * (0.08f + closeness * 0.10f)
        drawCircle(
            color = if (closeness > 0.55f) KindredColors.Accent else KindredColors.TextPrimary,
            radius = dotRadius,
            center = center,
        )
    }
}
