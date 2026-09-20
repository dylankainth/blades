package com.hackmit.twins.match

/**
 * One person can be heard from two radios at once: their phone (often in a
 * pocket, so quiet) and the badge on their lanyard (on their chest, so loud).
 * Filing both in one [DirectionSweep] would mix two signal levels and read
 * the difference between them as a direction. This keeps a sweep per source,
 * keyed by BLE token, and reports whichever one is surest.
 *
 * Immutable: [add] returns a new finder.
 */
class DirectionFinder private constructor(private val sources: Map<String, Source>) {

    constructor() : this(emptyMap())

    private data class Source(val sweep: DirectionSweep, val lastRssiDbm: Int, val lastHeardMs: Long)

    /** What the radar draws: the outer ring, and the arrow once there is one. */
    data class Reading(val estimate: DirectionEstimate?, val strengths: List<Float?>)

    /**
     * @param headingDeg where the user faced when the reading arrived, or null
     *   if that is not known. The reading then counts for closeness only.
     */
    fun add(token: String, headingDeg: Float?, rssiDbm: Int, nowMs: Long): DirectionFinder {
        val previous = sources[token]?.sweep ?: DirectionSweep()
        val sweep = if (headingDeg == null) previous else previous.add(headingDeg, rssiDbm, nowMs)
        // Tokens change when the other phone's service restarts. Forgetting
        // the ones that went quiet keeps the map from growing.
        val live = sources.filterValues { nowMs - it.lastHeardMs <= DirectionSweep.SECTOR_TTL_MS }
        return DirectionFinder(live + (token to Source(sweep, rssiDbm, nowMs)))
    }

    /** The loudest source heard in the last [RSSI_FRESH_MS]; null once they all went quiet. */
    fun strongestRssi(nowMs: Long): Int? =
        sources.values
            .filter { nowMs - it.lastHeardMs <= RSSI_FRESH_MS }
            .maxOfOrNull { it.lastRssiDbm }

    fun best(nowMs: Long): Reading {
        val sweeps = sources.values.map { it.sweep }
        val surest = sweeps
            .mapNotNull { sweep -> sweep.estimate(nowMs)?.let { sweep to it } }
            .maxByOrNull { (_, estimate) -> estimate.confidence }
        if (surest != null) return Reading(surest.second, surest.first.strengths(nowMs))
        val widest = sweeps.maxByOrNull { it.coverage(nowMs) } ?: DirectionSweep()
        return Reading(null, widest.strengths(nowMs))
    }

    companion object {
        /**
         * A pocketed phone in a crowd loses most of its adverts, so a few
         * seconds of silence is normal. Longer than this and they have
         * probably walked off, and the radar should say it is searching
         * rather than hold the last value.
         */
        const val RSSI_FRESH_MS = 8_000L
    }
}
