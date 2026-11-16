package com.sigil.client.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionTimingTest {
    @Test fun pausedSessionAcrossRebootUsesWallClockForBothIntervals() {
        val hour = 3_600_000L
        val session = elapsedSince(10 * hour, hour, 1, 16 * hour, 2 * hour, 2)
        val pause = elapsedSince(12 * hour, 3 * hour, 1, 16 * hour, 2 * hour, 2)
        assertEquals(2 * hour, session - pause)
    }

    @Test fun sameBootUsesMonotonicClockDespiteWallClockChanges() {
        assertEquals(2_000L, elapsedSince(10_000, 1_000, 4, 100_000, 3_000, 4))
        assertEquals(5_000L, elapsedSince(10_000, 1_000, -1, 15_000, 3_000, 4))
    }
}
