package com.hackmit.twins.match

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

/** Smoothed signal strength last heard while the user faced one sector. */
data class SectorReading(val rssiDbm: Float, val updatedAtMs: Long)

/**
 * @param bearingDeg heading (same frame as the headings passed to
 *   [DirectionSweep.add]) the signal is strongest towards, 0 until 360.
 * @param confidence 0 to 1. How much the strong sectors agree with each other.
 */
data class DirectionEstimate(val bearingDeg: Float, val confidence: Float)

/**
 * A rough bearing to another phone from BLE signal strength alone.
 *
 * BLE gives no angle, and these phones do not both have UWB. What there is: a
 * person holding a phone in front of them blocks 2.4 GHz with their own body,
 * so the other phone reads several dB stronger when its owner is ahead than
 * when they are behind. Logging strength against the heading the user faced
 * while they turn gives a lopsided circle, and the heavy side is the bearing.
 *
 * It is a guess. Reflections, the other person turning, and anyone standing
 * in between all bend it, which is why the estimate carries a confidence and
 * is withheld until most of the circle has been covered.
 *
 * Immutable: [add] returns a new sweep.
 */
class DirectionSweep private constructor(private val sectors: List<SectorReading?>) {

    constructor() : this(List(SECTOR_COUNT) { null })

    fun add(headingDeg: Float, rssiDbm: Int, nowMs: Long): DirectionSweep {
        val index = sectorIndex(headingDeg)
        val previous = sectors[index]?.takeIf { isFresh(it, nowMs) }
        val smoothed = if (previous == null) {
            rssiDbm.toFloat()
        } else {
            previous.rssiDbm + SMOOTHING * (rssiDbm - previous.rssiDbm)
        }
        return DirectionSweep(
            sectors.mapIndexed { i, old -> if (i == index) SectorReading(smoothed, nowMs) else old },
        )
    }

    /** Per sector, 0 (weakest heard) to 1 (strongest); null where nothing recent was heard. */
    fun strengths(nowMs: Long): List<Float?> {
        val fresh = freshSectors(nowMs)
        val heard = fresh.filterNotNull().map { it.rssiDbm }
        val weakest = heard.minOrNull() ?: return fresh.map { null }
        val spread = (heard.max() - weakest).coerceAtLeast(1f)
        return fresh.map { it?.let { reading -> (reading.rssiDbm - weakest) / spread } }
    }

    /** How much of the circle has a recent reading, 0 to 1. */
    fun coverage(nowMs: Long): Float =
        freshSectors(nowMs).count { it != null }.toFloat() / SECTOR_COUNT

    fun estimate(nowMs: Long): DirectionEstimate? {
        val fresh = freshSectors(nowMs)
        val heard = fresh.filterNotNull().map { it.rssiDbm }
        if (heard.size < MIN_SECTORS) return null
        val weakest = heard.min()
        if (heard.max() - weakest < MIN_SPREAD_DB) return null

        // Circular mean of the sector centres. Squared weights let the strong
        // side dominate; a plain average of angles would put 350 and 10 at 180.
        var east = 0.0
        var north = 0.0
        var total = 0.0
        fresh.forEachIndexed { i, reading ->
            if (reading == null) return@forEachIndexed
            val above = (reading.rssiDbm - weakest).toDouble()
            val weight = above * above
            val centre = Math.toRadians((i + 0.5) * SECTOR_WIDTH_DEG)
            east += weight * sin(centre)
            north += weight * cos(centre)
            total += weight
        }
        if (total == 0.0) return null
        val confidence = (hypot(east, north) / total).toFloat()
        if (confidence < MIN_CONFIDENCE) return null
        val bearing = (Math.toDegrees(atan2(east, north)).toFloat() + 360f) % 360f
        return DirectionEstimate(bearing, confidence.coerceIn(0f, 1f))
    }

    private fun freshSectors(nowMs: Long): List<SectorReading?> =
        sectors.map { reading -> reading?.takeIf { isFresh(it, nowMs) } }

    private fun isFresh(reading: SectorReading, nowMs: Long): Boolean =
        nowMs - reading.updatedAtMs <= SECTOR_TTL_MS

    private fun sectorIndex(headingDeg: Float): Int {
        val wrapped = ((headingDeg % 360f) + 360f) % 360f
        return floor(wrapped / SECTOR_WIDTH_DEG).toInt().coerceIn(0, SECTOR_COUNT - 1)
    }

    companion object {
        const val SECTOR_COUNT = 12
        const val SECTOR_WIDTH_DEG = 360f / SECTOR_COUNT

        /** People move. A reading this old says little about where they are now. */
        const val SECTOR_TTL_MS = 45_000L

        /** Two thirds of the circle, so a half turn cannot pass for a full one. */
        private const val MIN_SECTORS = 8

        /** Below this the "lopsided circle" is within ordinary RSSI jitter. */
        private const val MIN_SPREAD_DB = 5f

        /**
         * An ideal body-shadow pattern scores about 0.67 and pure noise about
         * 0.4, so this sits between them.
         */
        private const val MIN_CONFIDENCE = 0.5f

        /** Weight of a new reading against the sector's running value. */
        private const val SMOOTHING = 0.4f
    }
}
