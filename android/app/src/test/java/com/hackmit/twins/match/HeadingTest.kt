package com.hackmit.twins.match

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeadingTest {

    // Rows are world east, north, up; columns are the device's x, y, z axes.
    private val flatTopNorth = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
    private val flatTopEast = floatArrayOf(0f, 1f, 0f, -1f, 0f, 0f, 0f, 0f, 1f)
    private val uprightFacingNorth = floatArrayOf(1f, 0f, 0f, 0f, 0f, -1f, 0f, 1f, 0f)
    private val uprightFacingWest = floatArrayOf(0f, 0f, 1f, 1f, 0f, 0f, 0f, 1f, 0f)

    @Test
    fun `flat phone reads the way its top edge points`() {
        assertEquals(0f, headingFromRotationMatrix(flatTopNorth)!!, 0.5f)
        assertEquals(90f, headingFromRotationMatrix(flatTopEast)!!, 0.5f)
    }

    @Test
    fun `upright phone reads the way its back faces`() {
        assertEquals(0f, headingFromRotationMatrix(uprightFacingNorth)!!, 0.5f)
        assertEquals(270f, headingFromRotationMatrix(uprightFacingWest)!!, 0.5f)
    }

    @Test
    fun `no heading when the pointing vector is vertical`() {
        // Top edge and back of the phone both tilted 45 degrees off vertical,
        // cancelling out on the ground plane.
        val s = 0.70710677f
        val tiltedBack = floatArrayOf(-1f, 0f, 0f, 0f, s, s, 0f, s, -s)
        assertNull(headingFromRotationMatrix(tiltedBack))
    }

    @Test
    fun `relative direction wraps across north`() {
        assertEquals("ahead of you", relativeDirectionLabel(bearingDeg = 10f, headingDeg = 350f))
        assertEquals("to your right", relativeDirectionLabel(bearingDeg = 80f, headingDeg = 350f))
        assertEquals("behind you", relativeDirectionLabel(bearingDeg = 170f, headingDeg = 350f))
        assertEquals("to your left", relativeDirectionLabel(bearingDeg = 260f, headingDeg = 350f))
    }

    @Test
    fun `shortest turn never goes the long way round`() {
        assertEquals(20f, shortestTurn(fromDeg = 350f, toDeg = 10f), 0.01f)
        assertEquals(-20f, shortestTurn(fromDeg = 10f, toDeg = 350f), 0.01f)
        assertEquals(20f, shortestTurn(fromDeg = 710f, toDeg = 10f), 0.01f)
    }
}
