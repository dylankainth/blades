package com.hackmit.twins.match

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.hackmit.twins.ble.BleProximityService
import kotlinx.coroutines.delay

/** Everything the radar shows that comes from the radios and the rotation sensor. */
data class RadarDirection(
    /** Loudest recent reading of the other person; null while nothing is heard. */
    val rssiDbm: Int?,
    /** Where the user faces; null without a usable rotation sensor. */
    val headingDeg: Float?,
    val estimate: DirectionEstimate?,
    /** See [DirectionSweep.strengths]. Empty while the radar is not looking. */
    val sectorStrengths: List<Float?>,
    /** Signal climbing or dropping as the user walks; null while unknown or while they turn. */
    val approach: Approach? = null,
)

// The ends of the closeness scale. The other phone is usually in a pocket,
// which reads around -70 dBm a step away and should already feel warm; two
// phones held in the open reach -55 dBm at about arm's length.
private const val FAR_DBM = -95f
private const val NEAR_DBM = -55f

/** Without this, a still phone stops recomposing and old readings never age out. */
private const val AGE_TICK_MS = 1_000L

/** 0 (not heard, or far) to 1 (right next to them). */
fun closenessFromRssi(rssiDbm: Int?): Float =
    if (rssiDbm == null) 0f else ((rssiDbm - FAR_DBM) / (NEAR_DBM - FAR_DBM)).coerceIn(0f, 1f)

/**
 * Collects BLE readings of [otherTwinId] against the heading the user faced
 * when each arrived. While [active] it also asks the BLE service for its
 * fastest scan and advertise settings; a direction needs many readings a second.
 */
@Composable
fun rememberRadarDirection(otherTwinId: String, active: Boolean): RadarDirection {
    DisposableEffect(active) {
        BleProximityService.setRadarMode(active)
        onDispose { BleProximityService.setRadarMode(false) }
    }
    val heading by rememberHeadingDeg()
    val currentHeading by rememberUpdatedState(heading)
    var finder by remember(otherTwinId) { mutableStateOf(DirectionFinder()) }
    var tracker by remember(otherTwinId) { mutableStateOf(ApproachTracker()) }
    LaunchedEffect(otherTwinId) {
        BleProximityService.rssiSamples.collect { sample ->
            if (sample.twinId != otherTwinId) return@collect
            finder = finder.add(sample.token, currentHeading, sample.rssiDbm, sample.elapsedRealtimeMs)
            tracker = tracker.add(sample.token, sample.rssiDbm, sample.elapsedRealtimeMs, currentHeading)
        }
    }
    val tick by produceState(initialValue = 0L) {
        while (true) {
            delay(AGE_TICK_MS)
            value = SystemClock.elapsedRealtime()
        }
    }
    val nowMs = maxOf(tick, SystemClock.elapsedRealtime())
    val rssi = finder.strongestRssi(nowMs)
    if (!active) return RadarDirection(rssi, heading, estimate = null, sectorStrengths = emptyList())
    val reading = finder.best(nowMs)
    return RadarDirection(rssi, heading, reading.estimate, reading.strengths, tracker.trend(nowMs))
}
