package com.hackmit.twins.match

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.random.Random

class DirectionSweepTest {

    /** One full turn: a body-shadow pattern that peaks when facing [peakDeg]. */
    private fun sweepOnce(
        peakDeg: Float,
        depthDb: Float = 8f,
        noiseDb: Float = 0f,
        startMs: Long = 0L,
        baseDbm: Float = -65f,
        from: DirectionSweep = DirectionSweep(),
        random: Random = Random(7),
    ): DirectionSweep {
        var sweep = from
        // 10 s per turn, a sample every 100 ms.
        for (step in 0 until 100) {
            val heading = step * 3.6f
            val shadow = depthDb * cos(Math.toRadians((heading - peakDeg).toDouble())).toFloat()
            val noise = if (noiseDb == 0f) 0f else (random.nextFloat() * 2f - 1f) * noiseDb
            sweep = sweep.add(heading, (baseDbm + shadow + noise).toInt(), startMs + step * 100L)
        }
        return sweep
    }

    private fun angleBetween(a: Float, b: Float): Float {
        val diff = abs(a - b) % 360f
        return if (diff > 180f) 360f - diff else diff
    }

    @Test
    fun `no estimate before the user has turned`() {
        var sweep = DirectionSweep()
        for (step in 0 until 50) sweep = sweep.add(10f, -60, step * 100L)
        assertNull(sweep.estimate(5_000L))
    }

    @Test
    fun `a full turn points at the strongest heading`() {
        val estimate = sweepOnce(peakDeg = 120f).estimate(10_000L)
        assertNotNull(estimate)
        assertTrue(angleBetween(estimate!!.bearingDeg, 120f) < 25f)
    }

    @Test
    fun `a peak across north does not average to south`() {
        val estimate = sweepOnce(peakDeg = 355f).estimate(10_000L)
        assertNotNull(estimate)
        assertTrue(angleBetween(estimate!!.bearingDeg, 355f) < 25f)
    }

    @Test
    fun `noisy readings still land in the right quadrant`() {
        val estimate = sweepOnce(peakDeg = 250f, noiseDb = 4f).estimate(10_000L)
        assertNotNull(estimate)
        assertTrue(angleBetween(estimate!!.bearingDeg, 250f) < 45f)
    }

    @Test
    fun `a flat signal gives no direction`() {
        assertNull(sweepOnce(peakDeg = 0f, depthDb = 0.5f).estimate(10_000L))
    }

    @Test
    fun `readings expire`() {
        val sweep = sweepOnce(peakDeg = 120f)
        assertNull(sweep.estimate(10_000L + DirectionSweep.SECTOR_TTL_MS + 1))
    }

    @Test
    fun `walking closer does not drag the arrow to where you face`() {
        // Turn once with them at 90 degrees, then face 270 while the whole
        // signal climbs 6 dB. The louder readings must not read as a new peak.
        var sweep = sweepOnce(peakDeg = 90f)
        for (step in 0 until 80) {
            val rise = 6f * step / 80f
            sweep = sweep.add(270f, (-65f - 8f + rise).toInt(), 10_000L + step * 100L)
        }
        val estimate = sweep.estimate(18_000L)
        assertNotNull(estimate)
        assertTrue(angleBetween(estimate!!.bearingDeg, 90f) < 45f)
    }

    @Test
    fun `strengths are normalised and mark unseen sectors`() {
        var sweep = DirectionSweep()
        sweep = sweep.add(0f, -80, 0L).add(90f, -50, 3_000L)
        val strengths = sweep.strengths(3_000L)
        assertEquals(DirectionSweep.SECTOR_COUNT, strengths.size)
        assertEquals(2, strengths.count { it != null })
        assertTrue(strengths.filterNotNull().all { it in 0f..1f })
    }

    @Test
    fun `add returns a new sweep and leaves the old one alone`() {
        val empty = DirectionSweep()
        empty.add(0f, -60, 0L)
        assertTrue(empty.strengths(0L).all { it == null })
    }
}
