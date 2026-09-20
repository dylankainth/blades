package com.hackmit.twins.ble

import org.junit.Assert.assertEquals
import org.junit.Test

class ScanStartBudgetTest {

    @Test
    fun `a fresh budget allows a start straight away`() {
        assertEquals(0L, ScanStartBudget().waitMs(nowMs = 1_000L))
    }

    @Test
    fun `starts under the limit do not wait`() {
        var budget = ScanStartBudget()
        for (i in 0 until ScanStartBudget.MAX_STARTS - 1) budget = budget.recorded(i * 1_000L)
        assertEquals(0L, budget.waitMs(nowMs = 5_000L))
    }

    @Test
    fun `at the limit the wait runs until the oldest start leaves the window`() {
        var budget = ScanStartBudget()
        for (i in 0 until ScanStartBudget.MAX_STARTS) budget = budget.recorded(1_000L + i * 1_000L)
        // Oldest start was at 1 s, so a slot frees at 1 s + the window.
        assertEquals(ScanStartBudget.WINDOW_MS - 9_000L, budget.waitMs(nowMs = 10_000L))
    }

    @Test
    fun `old starts are forgotten`() {
        var budget = ScanStartBudget()
        for (i in 0 until ScanStartBudget.MAX_STARTS) budget = budget.recorded(i * 1_000L)
        assertEquals(0L, budget.waitMs(nowMs = ScanStartBudget.WINDOW_MS + 10_000L))
    }

    @Test
    fun `recorded leaves the old budget alone`() {
        val budget = ScanStartBudget()
        for (i in 0 until ScanStartBudget.MAX_STARTS) budget.recorded(i * 1_000L)
        assertEquals(0L, budget.waitMs(nowMs = 5_000L))
    }
}
