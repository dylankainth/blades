package com.hackmit.twins.match

import kotlin.math.abs

enum class Approach { CLOSER, FARTHER, STEADY }

/**
 * Whether the other person's signal is climbing or dropping, for someone who
 * is walking. The direction sweep needs the user to stop and turn; this is
 * the cue for the rest of the time, and all it says is warmer or colder.
 *
 * Readings are kept per BLE token, for the same reason as in
 * [DirectionFinder]: a loud badge and a quiet pocketed phone in one series
 * would look like someone running back and forth.
 *
 * Immutable: [add] returns a new tracker.
 */
class ApproachTracker private constructor(private val sources: Map<String, List<Sample>>) {

    constructor() : this(emptyMap())

    private data class Sample(val atMs: Long, val rssiDbm: Int, val headingDeg: Float?)

    fun add(token: String, rssiDbm: Int, nowMs: Long, headingDeg: Float? = null): ApproachTracker {
        val live = sources
            .mapValues { (_, samples) -> samples.filter { nowMs - it.atMs <= WINDOW_MS } }
            .filterValues { it.isNotEmpty() }
        val series = live[token].orEmpty() + Sample(nowMs, rssiDbm, headingDeg)
        return ApproachTracker(live + (token to series))
    }

    /** Null while there is too little to go on, or while the user is turning. */
    fun trend(nowMs: Long): Approach? {
        val series = sources.values
            .map { samples -> samples.filter { nowMs - it.atMs <= WINDOW_MS } }
            .filter { it.size >= MIN_SAMPLES && it.last().atMs - it.first().atMs >= MIN_SPAN_MS }
            .maxByOrNull { it.size }
            ?: return null
        if (isTurning(series)) return null
        val slope = slopeDbPerSecond(series)
        val step = medianDbm(series.drop(series.size / 2)) - medianDbm(series.take(series.size / 2))
        return when {
            slope >= TREND_DB_PER_S && step >= MIN_STEP_DB -> Approach.CLOSER
            slope <= -TREND_DB_PER_S && step <= -MIN_STEP_DB -> Approach.FARTHER
            else -> Approach.STEADY
        }
    }

    /**
     * The user's own body blocks a few dB, so turning on the spot moves the
     * signal about as much as walking does. That is the sweep's job to read.
     */
    private fun isTurning(series: List<Sample>): Boolean {
        val headings = series.mapNotNull { it.headingDeg }
        val first = headings.firstOrNull() ?: return false
        return headings.any { abs(shortestTurn(first, it)) > MAX_TURN_DEG }
    }

    /** Least-squares line through the readings. One stray reading barely moves it. */
    private fun slopeDbPerSecond(series: List<Sample>): Float {
        val meanS = series.map { it.atMs / 1000.0 }.average()
        val meanDbm = series.map { it.rssiDbm.toDouble() }.average()
        val covariance = series.sumOf { (it.atMs / 1000.0 - meanS) * (it.rssiDbm - meanDbm) }
        val variance = series.sumOf { (it.atMs / 1000.0 - meanS).let { d -> d * d } }
        return if (variance == 0.0) 0f else (covariance / variance).toFloat()
    }

    private fun medianDbm(samples: List<Sample>): Float {
        val sorted = samples.map { it.rssiDbm }.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid].toFloat() else (sorted[mid - 1] + sorted[mid]) / 2f
    }

    companion object {
        /** Long enough to average out fading, short enough to notice someone walking past. */
        const val WINDOW_MS = 6_000L
        private const val MIN_SAMPLES = 8
        private const val MIN_SPAN_MS = 2_500L

        /**
         * Walking towards someone from 10 m to 3 m takes about 5 s and gains
         * roughly 10 dB in the open, so 2 dB/s. Two phones lying still on a
         * desk tripped a 1 dB/s bar within seconds, so the bar sits closer to
         * the walking figure, and the later half of the window also has to
         * sit a clear step above (or below) the earlier half. Medians, so a
         * single faded reading cannot fake the step.
         */
        private const val TREND_DB_PER_S = 1.5f
        private const val MIN_STEP_DB = 4f

        /** More than this off the first heading in the window counts as turning. */
        private const val MAX_TURN_DEG = 45f
    }
}
