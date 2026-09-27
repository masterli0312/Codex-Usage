package com.codex.quota

import com.codex.quota.worker.isFiveHourReminderDue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FiveHourResetReminderTest {
    @Test fun reminderRunsOnlyInFiveMinutesBeforeReset() {
        val reset = 1_000_000L
        assertFalse(isFiveHourReminderDue(reset, reset - 300_001L))
        assertTrue(isFiveHourReminderDue(reset, reset - 300_000L))
        assertTrue(isFiveHourReminderDue(reset, reset - 1L))
        assertFalse(isFiveHourReminderDue(reset, reset))
    }
}
