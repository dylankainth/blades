package com.hackmit.twins.match

import android.os.SystemClock
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.hackmit.twins.ble.BleProximityService
import com.hackmit.twins.ui.theme.KlickColors
import kotlinx.coroutines.delay

/** How long the "you met" confirmation stays up before the radar closes itself. */
private const val MET_DISMISS_MS = 3_500L

/**
 * The first snapshot can be a stale cached copy from before the pair met, with
 * the server's copy right behind it. A "met" that lands this soon after opening
 * is that correction, not two people shaking badges.
 */
private const val STALE_CACHE_GRACE_MS = 1_500L

/**
 * Both people approved — real identity is fully revealed here (unlike
 * MatchTeaserScreen's blurred name), plus a live "find each other" radar
 * driven by the other person's real BLE signal strength (RSSI), not a
 * decorative animation. See BleProximityService's nearbyRssi.
 *
 * Signal strength is a rough, noisy stand-in for distance (no UWB on these
 * phones) — good for "warmer/colder" feedback, not precise range. Direction
 * is a guess built from the same signal once the user has turned around: see
 * [DirectionSweep].
 *
 * Once the pair shakes their badges the backend sets matches/{id}.metAt; the
 * radar then confirms it and closes itself via [onMet], since there is
 * nobody left to find. Opened from the feed for a pair that had already met,
 * it just says so and stays until the user goes back.
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
    // Null until the first snapshot arrives. Only a meeting that happens while
    // this screen is open closes it; one that predates it would otherwise shut
    // the radar the moment it was reopened.
    var metBeforeOpen by remember { mutableStateOf<Boolean?>(null) }
    val openedAtMs = remember { SystemClock.elapsedRealtime() }
    DisposableEffect(myTwinId, otherTwinId) {
        // Same id the backend derives in negotiateTwins.ts: both twin ids, sorted.
        val matchId = listOf(myTwinId, otherTwinId).sorted().joinToString("_")
        val registration = MatchDetailRepository.listen(matchId, myTwinId) { detail ->
            val isMet = detail?.met == true
            val withinGrace = SystemClock.elapsedRealtime() - openedAtMs < STALE_CACHE_GRACE_MS
            if (metBeforeOpen == null || (isMet && !met && withinGrace)) metBeforeOpen = isMet
            if (isMet) met = true
        }
        onDispose { registration.remove() }
    }
    val metJustNow = met && metBeforeOpen == false

    val haptics = LocalHapticFeedback.current
    val currentOnMet by rememberUpdatedState(onMet)
    LaunchedEffect(metJustNow) {
        if (!metJustNow) return@LaunchedEffect
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        delay(MET_DISMISS_MS)
        currentOnMet()
    }

    val rssiByTwin by BleProximityService.nearbyRssi.collectAsState()
    val rssi = rssiByTwin[otherTwinId]

    // Direction: every reading is filed under the heading the user faced when
    // it arrived. Nothing left to find once the pair has met.
    DisposableEffect(met) {
        BleProximityService.setRadarMode(!met)
        onDispose { BleProximityService.setRadarMode(false) }
    }
    val heading by rememberHeadingDeg()
    val currentHeading by rememberUpdatedState(heading)
    var sweep by remember(otherTwinId) { mutableStateOf(DirectionSweep()) }
    LaunchedEffect(otherTwinId) {
        BleProximityService.rssiSamples.collect { sample ->
            val facing = currentHeading
            if (sample.twinId == otherTwinId && facing != null) {
                sweep = sweep.add(facing, sample.rssiDbm, sample.elapsedRealtimeMs)
            }
        }
    }
    // Heading changes recompose this many times a second, which is also what
    // lets old readings age out of the estimate without a timer.
    val nowMs = SystemClock.elapsedRealtime()
    val direction = if (met) null else sweep.estimate(nowMs)
    val facing = heading

    // Kept unwrapped (it can pass 360) so the arrow turns the short way round.
    var shownBearing by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(direction?.bearingDeg) {
        direction?.let { shownBearing += shortestTurn(shownBearing, it.bearingDeg) }
    }
    val animatedBearing by animateFloatAsState(
        targetValue = shownBearing,
        animationSpec = tween(500),
        label = "bearing",
    )

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
                RadarRings(
                    closeness = closeness,
                    sectorStrengths = if (met || facing == null) null else sweep.strengths(nowMs),
                    headingDeg = facing ?: 0f,
                    arrowBearingDeg = if (direction != null && facing != null) animatedBearing else null,
                    arrowConfidence = direction?.confidence ?: 0f,
                )
            }

            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium,
                color = KlickColors.TextPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = if (metJustNow) {
                    "Marked as met. Closing this match."
                } else if (met) {
                    "You two have already met."
                } else if (direction != null && facing != null) {
                    "Probably ${relativeDirectionLabel(direction.bearingDeg, facing)}. " +
                        "A rough guess from signal strength."
                } else if (rssi != null && facing != null) {
                    "Turn around slowly on the spot so the arrow can find them."
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

/** Gap between neighbouring sectors of the outer ring, in degrees. */
private const val SECTOR_GAP_DEG = 4f

/**
 * @param sectorStrengths what [DirectionSweep.strengths] returned, or null to
 *   hide the outer ring. It is drawn fixed to the room, not the phone, so it
 *   turns against the user as they turn: [headingDeg] is what is at the top.
 * @param arrowBearingDeg where the arrow points, in the same frame; null hides it.
 */
@Composable
private fun RadarRings(
    closeness: Float,
    sectorStrengths: List<Float?>?,
    headingDeg: Float,
    arrowBearingDeg: Float?,
    arrowConfidence: Float,
) {
    // The direction is in the text under the radar; the drawing adds nothing
    // a screen reader can use.
    Canvas(modifier = Modifier.size(280.dp).clearAndSetSemantics { }) {
        val center = Offset(size.width / 2, size.height / 2)
        val outerRadius = size.minDimension / 2
        val maxRadius = outerRadius * 0.8f

        if (sectorStrengths != null) {
            val stroke = 7.dp.toPx()
            val ringRadius = outerRadius - stroke / 2
            val sectorWidth = 360f / sectorStrengths.size
            sectorStrengths.forEachIndexed { i, strength ->
                drawArc(
                    color = KlickColors.TextPrimary.copy(
                        alpha = if (strength == null) 0.06f else 0.16f + 0.6f * strength,
                    ),
                    // Canvas angles start at 3 o'clock; headings start at the top.
                    startAngle = i * sectorWidth - headingDeg - 90f + SECTOR_GAP_DEG / 2,
                    sweepAngle = sectorWidth - SECTOR_GAP_DEG,
                    useCenter = false,
                    topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
                    size = Size(ringRadius * 2, ringRadius * 2),
                    style = Stroke(width = stroke),
                )
            }
        }
        if (arrowBearingDeg != null) {
            val tip = outerRadius * 0.86f
            val base = outerRadius * 0.66f
            val halfWidth = outerRadius * 0.1f
            val arrow = Path().apply {
                moveTo(center.x, center.y - tip)
                lineTo(center.x - halfWidth, center.y - base)
                lineTo(center.x + halfWidth, center.y - base)
                close()
            }
            rotate(degrees = arrowBearingDeg - headingDeg, pivot = center) {
                // Fainter when the strong sectors disagree with each other.
                drawPath(arrow, KlickColors.Accent.copy(alpha = (0.2f + arrowConfidence).coerceIn(0.5f, 1f)))
            }
        }
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
