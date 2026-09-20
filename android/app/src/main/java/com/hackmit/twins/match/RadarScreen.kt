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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
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
 * decorative animation. See [rememberRadarDirection].
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
    val meeting = rememberMeeting(myTwinId, otherTwinId, onMet)
    // Nothing left to find once the pair has met.
    val radar = rememberRadarDirection(otherTwinId, active = !meeting.met)
    val closeness by animateFloatAsState(
        targetValue = if (meeting.met) 1f else closenessFromRssi(radar.rssiDbm),
        animationSpec = tween(600),
        label = "closeness",
    )
    val arrowBearing = rememberArrowBearing(radar.estimate)
    val facing = radar.headingDeg

    Scaffold(containerColor = KlickColors.PageBackground) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RadarHeader(otherName, otherPhotoUrl)
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                RadarRings(
                    closeness = closeness,
                    sectorStrengths = radar.sectorStrengths.takeIf { facing != null && it.isNotEmpty() },
                    headingDeg = facing ?: 0f,
                    arrowBearingDeg = arrowBearing.takeIf { radar.estimate != null && facing != null },
                    arrowConfidence = radar.estimate?.confidence ?: 0f,
                )
            }
            Text(
                text = statusText(meeting.met, otherName, radar.rssiDbm, closeness),
                style = MaterialTheme.typography.titleMedium,
                color = KlickColors.TextPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = hintText(meeting, radar),
                style = MaterialTheme.typography.bodySmall,
                color = KlickColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
        }
    }
}

/** @param justNow the pair met while this screen was open, as opposed to before it. */
private data class Meeting(val met: Boolean, val justNow: Boolean)

/** Watches the match for the badge shake, and closes the radar via [onMet] when it lands. */
@Composable
private fun rememberMeeting(myTwinId: String, otherTwinId: String, onMet: () -> Unit): Meeting {
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
    val justNow = met && metBeforeOpen == false

    val haptics = LocalHapticFeedback.current
    val currentOnMet by rememberUpdatedState(onMet)
    LaunchedEffect(justNow) {
        if (!justNow) return@LaunchedEffect
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        delay(MET_DISMISS_MS)
        currentOnMet()
    }
    return Meeting(met, justNow)
}

/**
 * The bearing to draw, animated. Kept unwrapped (it can pass 360) so the arrow
 * turns the short way round instead of spinning through south to cross north.
 */
@Composable
private fun rememberArrowBearing(estimate: DirectionEstimate?): Float {
    var shownBearing by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(estimate?.bearingDeg) {
        estimate?.let { shownBearing += shortestTurn(shownBearing, it.bearingDeg) }
    }
    val animated by animateFloatAsState(
        targetValue = shownBearing,
        animationSpec = tween(500),
        label = "bearing",
    )
    return animated
}

@Composable
private fun RadarHeader(otherName: String, otherPhotoUrl: String?) {
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
}

private fun statusText(met: Boolean, otherName: String, rssiDbm: Int?, closeness: Float): String = when {
    met -> "You met $otherName"
    rssiDbm == null -> "Searching for them..."
    closeness > 0.85f -> "Very close — look around!"
    closeness > 0.55f -> "Getting warmer"
    closeness > 0.25f -> "Somewhere nearby"
    else -> "Still far — keep moving"
}

private fun hintText(meeting: Meeting, radar: RadarDirection): String {
    val facing = radar.headingDeg
    val estimate = radar.estimate
    return when {
        meeting.justNow -> "Marked as met. Closing this match."
        meeting.met -> "You two have already met."
        // Someone walking gets the walking cue; the arrow is for standing and turning.
        radar.approach == Approach.CLOSER -> "Signal is getting stronger. Keep going this way."
        // Still phones have tripped this, so it never sends anyone the other
        // way. It asks for the turn on the spot, which is safe advice either way.
        radar.approach == Approach.FARTHER -> "Signal is dropping. Stop and turn slowly so the arrow can find them."
        estimate != null && facing != null ->
            "Probably ${relativeDirectionLabel(estimate.bearingDeg, facing)}. " +
                "A rough guess from signal strength."
        radar.rssiDbm != null && facing != null ->
            "Hold your phone in front of you and turn around slowly on the spot so the arrow can find them."
        else -> "Signal strength only — not exact distance or direction."
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
        if (sectorStrengths != null) drawSectorRing(sectorStrengths, headingDeg)
        drawClosenessRings(closeness)
        // Last, so the rings do not draw over it.
        if (arrowBearingDeg != null) drawArrow(arrowBearingDeg - headingDeg, arrowConfidence)
    }
}

private fun DrawScope.drawSectorRing(sectorStrengths: List<Float?>, headingDeg: Float) {
    val stroke = 7.dp.toPx()
    val ringRadius = size.minDimension / 2 - stroke / 2
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

/** @param turnDeg clockwise from the top of the canvas. */
private fun DrawScope.drawArrow(turnDeg: Float, confidence: Float) {
    val outerRadius = size.minDimension / 2
    val tip = outerRadius * 0.86f
    val base = outerRadius * 0.66f
    val halfWidth = outerRadius * 0.1f
    val arrow = Path().apply {
        moveTo(center.x, center.y - tip)
        lineTo(center.x - halfWidth, center.y - base)
        lineTo(center.x + halfWidth, center.y - base)
        close()
    }
    rotate(degrees = turnDeg, pivot = center) {
        // Fainter when the strong sectors disagree with each other.
        drawPath(arrow, KlickColors.Accent.copy(alpha = (0.2f + confidence).coerceIn(0.5f, 1f)))
    }
}

private fun DrawScope.drawClosenessRings(closeness: Float) {
    val maxRadius = size.minDimension / 2 * 0.8f
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
            style = Stroke(width = 3f),
        )
    }
    // Center dot brightens/grows with closeness.
    drawCircle(
        color = if (closeness > 0.55f) KlickColors.Accent else KlickColors.TextPrimary,
        radius = maxRadius * (0.08f + closeness * 0.10f),
        center = center,
    )
}
