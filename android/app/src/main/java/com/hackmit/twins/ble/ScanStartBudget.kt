package com.hackmit.twins.ble

/**
 * Android allows an app about five scan starts in any 30 seconds. One more
 * and the scan is accepted but delivers nothing, with no error callback, until
 * the next restart minutes later. This counts our own starts so a restart can
 * wait for a free slot instead of walking into that.
 *
 * Immutable: [recorded] returns a new budget.
 */
class ScanStartBudget private constructor(private val startsMs: List<Long>) {

    constructor() : this(emptyList())

    fun recorded(nowMs: Long): ScanStartBudget = ScanStartBudget(recent(nowMs) + nowMs)

    /** How long to hold off before the next start; 0 when there is a free slot now. */
    fun waitMs(nowMs: Long): Long {
        val recent = recent(nowMs)
        if (recent.size < MAX_STARTS) return 0L
        return recent[recent.size - MAX_STARTS] + WINDOW_MS - nowMs
    }

    private fun recent(nowMs: Long): List<Long> = startsMs.filter { nowMs - it < WINDOW_MS }

    companion object {
        /** One under the platform's five, as a spare for the periodic restart. */
        const val MAX_STARTS = 4
        const val WINDOW_MS = 30_000L
    }
}
