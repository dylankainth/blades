package com.hackmit.twins.match

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApproachTrackerTest {

    /** One reading every 250 ms, moving [dbPerSecond], with a repeating +-3 dB wobble. */
    private fun walk(token: String, startDbm: Int, dbPerSecond: Float, seconds: Int, from: ApproachTracker = ApproachTracker()): ApproachTracker {
        val wobble = listOf(0, 3, -2, 1, -3, 2)
        return (0 until seconds * 4).fold(from) { tracker, step ->
            val atMs = step * 250L
            val rssi = startDbm + (dbPerSecond * atMs / 1000f).toInt() + wobble[step % wobble.size]
            tracker.add(token, rssi, atMs)
        }
    }

    @Test
    fun `nothing heard means no trend`() {
        assertNull(ApproachTracker().trend(0L))
    }

    @Test
    fun `a couple of readings are not enough to call it`() {
        val tracker = ApproachTracker().add("phone", -80, 0L).add("phone", -70, 300L)
        assertNull(tracker.trend(400L))
    }

    @Test
    fun `signal climbing while you walk means getting closer`() {
        assertEquals(Approach.CLOSER, walk("phone", startDbm = -85, dbPerSecond = 2f, seconds = 5).trend(5_000L))
    }

    @Test
    fun `signal dropping means moving away`() {
        assertEquals(Approach.FARTHER, walk("phone", startDbm = -60, dbPerSecond = -2f, seconds = 5).trend(5_000L))
    }

    @Test
    fun `noise around a level reads as steady`() {
        assertEquals(Approach.STEADY, walk("phone", startDbm = -70, dbPerSecond = 0f, seconds = 5).trend(5_000L))
    }

    @Test
    fun `a loud badge and a quiet phone are not read as movement`() {
        // Alternating sources 25 dB apart. One shared series would swing wildly.
        val tracker = (0 until 24).fold(ApproachTracker()) { acc, step ->
            val atMs = step * 250L
            if (step % 2 == 0) acc.add("badge", -55, atMs) else acc.add("phone", -80, atMs)
        }
        assertEquals(Approach.STEADY, tracker.trend(6_000L))
    }

    @Test
    fun `turning on the spot is not read as walking`() {
        // Body shadowing moves the signal as much as a few steps do.
        val tracker = (0 until 20).fold(ApproachTracker()) { acc, step ->
            acc.add("phone", -85 + step / 2, step * 250L, headingDeg = step * 18f)
        }
        assertNull(tracker.trend(5_000L))
    }

    @Test
    fun `walking in a roughly straight line still counts`() {
        val tracker = (0 until 20).fold(ApproachTracker()) { acc, step ->
            acc.add("phone", -85 + step / 2, step * 250L, headingDeg = 350f + (step % 3) * 10f)
        }
        assertEquals(Approach.CLOSER, tracker.trend(5_000L))
    }

    @Test
    fun `a still phone with deep fades is steady`() {
        // What two phones on a desk actually produce: a level with 8 dB dips.
        val fades = listOf(-60, -61, -68, -60, -59, -67, -61, -60, -69, -60, -58, -66, -60, -61, -68, -59)
        val tracker = fades.foldIndexed(ApproachTracker()) { step, acc, rssi -> acc.add("phone", rssi, step * 350L) }
        assertEquals(Approach.STEADY, tracker.trend(5_600L))
    }

    @Test
    fun `old readings stop counting`() {
        val tracker = walk("phone", startDbm = -85, dbPerSecond = 2f, seconds = 5)
        assertNull(tracker.trend(5_000L + 2 * ApproachTracker.WINDOW_MS))
    }
}
