package com.hackmit.twins.match

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkingModeTest {

    private val now = 1_000_000_000L

    private fun decide(
        on: Boolean = true,
        status: String? = "confirmed",
        revealStatus: String? = "pending",
        myApproval: String? = null,
        createdAtMs: Long? = now - 10_000L,
    ) = NetworkingMode.shouldSayYes(on, status, revealStatus, myApproval, createdAtMs, now)

    @Test
    fun `a fresh confirmed match gets a yes`() {
        assertTrue(decide())
    }

    @Test
    fun `a match doc written before revealStatus existed counts as pending`() {
        assertTrue(decide(revealStatus = null))
    }

    @Test
    fun `nothing happens with the mode off`() {
        assertFalse(decide(on = false))
    }

    @Test
    fun `a negotiation that is still running or was dismissed is left alone`() {
        assertFalse(decide(status = "negotiating"))
        assertFalse(decide(status = "dismissed"))
    }

    @Test
    fun `an answer the user already gave is never overwritten`() {
        assertFalse(decide(myApproval = "declined"))
        assertFalse(decide(myApproval = "approved"))
    }

    @Test
    fun `a revealed or cancelled match is left alone`() {
        assertFalse(decide(revealStatus = "revealed"))
        assertFalse(decide(revealStatus = "cancelled"))
    }

    @Test
    fun `old matches in the history are not answered`() {
        assertFalse(decide(createdAtMs = now - 6 * 60_000L))
        assertFalse(decide(createdAtMs = null))
    }
}
