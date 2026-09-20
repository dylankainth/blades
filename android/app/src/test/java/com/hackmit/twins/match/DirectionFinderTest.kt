package com.hackmit.twins.match

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos

class DirectionFinderTest {

    private fun angleBetween(a: Float, b: Float): Float {
        val diff = abs(a - b) % 360f
        return if (diff > 180f) 360f - diff else diff
    }

    private fun shadowed(baseDbm: Float, headingDeg: Float, peakDeg: Float): Int =
        (baseDbm + 8f * cos(Math.toRadians((headingDeg - peakDeg).toDouble())).toFloat()).toInt()

    @Test
    fun `nothing heard means no signal and no direction`() {
        val finder = DirectionFinder()
        assertNull(finder.strongestRssi(0L))
        assertNull(finder.best(0L).estimate)
        assertTrue(finder.best(0L).strengths.all { it == null })
    }

    @Test
    fun `a badge and a phone for the same person do not blur each other`() {
        // The phone is in a pocket (quiet) and only heard over half the turn;
        // the badge is on their chest (loud) and heard all the way round. Filed
        // in one sweep, the quiet readings would carve a false dip.
        var finder = DirectionFinder()
        for (step in 0 until 100) {
            val heading = step * 3.6f
            val now = step * 100L
            finder = finder.add("badge", heading, shadowed(-55f, heading, peakDeg = 90f), now)
            if (heading < 180f) finder = finder.add("phone", heading, -85, now)
        }
        val estimate = finder.best(10_000L).estimate
        assertNotNull(estimate)
        assertTrue(angleBetween(estimate!!.bearingDeg, 90f) < 25f)
    }

    @Test
    fun `without an estimate the ring shows the source with most of the circle`() {
        var finder = DirectionFinder()
        finder = finder.add("phone", 10f, -70, 0L)
        for (heading in listOf(10f, 40f, 70f, 100f)) finder = finder.add("badge", heading, -60, 0L)
        val reading = finder.best(0L)
        assertNull(reading.estimate)
        assertEquals(4, reading.strengths.count { it != null })
    }

    @Test
    fun `closeness follows the strongest source heard lately`() {
        val finder = DirectionFinder()
            .add("phone", 0f, -80, 1_000L)
            .add("badge", 0f, -60, 1_200L)
            .add("phone", 0f, -78, 1_400L)
        assertEquals(-60, finder.strongestRssi(1_500L))
    }

    @Test
    fun `a source that went quiet stops counting`() {
        val finder = DirectionFinder()
            .add("badge", 0f, -60, 0L)
            .add("phone", 0f, -80, DirectionFinder.RSSI_FRESH_MS + 500L)
        assertEquals(-80, finder.strongestRssi(DirectionFinder.RSSI_FRESH_MS + 600L))
        assertNull(finder.strongestRssi(3 * DirectionFinder.RSSI_FRESH_MS))
    }

    @Test
    fun `readings without a heading still count for closeness`() {
        val finder = DirectionFinder().add("phone", null, -66, 0L)
        assertEquals(-66, finder.strongestRssi(100L))
        assertTrue(finder.best(100L).strengths.all { it == null })
    }
}
