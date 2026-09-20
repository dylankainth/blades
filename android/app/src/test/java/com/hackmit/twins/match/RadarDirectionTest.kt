package com.hackmit.twins.match

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarDirectionTest {

    @Test
    fun `no signal reads as far away`() {
        assertEquals(0f, closenessFromRssi(null), 0.001f)
        assertEquals(0f, closenessFromRssi(-110), 0.001f)
    }

    @Test
    fun `a phone in a pocket a step away still reads as warm`() {
        // Around -70 dBm: a pocketed phone at about a metre.
        assertTrue(closenessFromRssi(-70) > 0.55f)
    }

    @Test
    fun `closeness is capped at one`() {
        assertEquals(1f, closenessFromRssi(-30), 0.001f)
    }

    @Test
    fun `a compass heading is kept when the sensor gives no error estimate`() {
        assertTrue(magneticHeadingUsable(null))
        assertTrue(magneticHeadingUsable(-1f))
    }

    @Test
    fun `a compass heading worse than one sector is dropped`() {
        assertTrue(magneticHeadingUsable(Math.toRadians(10.0).toFloat()))
        assertFalse(magneticHeadingUsable(Math.toRadians(45.0).toFloat()))
    }
}
