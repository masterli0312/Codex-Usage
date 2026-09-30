package com.codex.quota

import com.codex.quota.worker.isFiveHourReminderDue
import com.codex.quota.worker.shouldSendFiveHourResetReminder
import com.codex.quota.domain.model.AuthStatus
import com.codex.quota.domain.model.CodexUsage
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

    @Test fun exhaustedWeeklyQuotaSuppressesReminderEvenWithFiveHourQuota() {
        assertFalse(shouldSendFiveHourResetReminder(usage("li", 0.0), reset, now))
    }

    @Test fun unknownOrUnauthenticatedWeeklyQuotaDoesNotSendReminder() {
        assertFalse(shouldSendFiveHourResetReminder(null, reset, now))
        for (remaining in listOf(null, -1.0, Double.NaN, Double.POSITIVE_INFINITY, 101.0)) {
            assertFalse(shouldSendFiveHourResetReminder(usage("li", remaining), reset, now))
        }
        assertFalse(shouldSendFiveHourResetReminder(usage("li", 50.0).copy(status = AuthStatus.OFFLINE), reset, now))
    }

    @Test fun weeklyQuotaIsEvaluatedPerAccountAndRestoredAfterReset() {
        val exhausted = usage("li", 0.0)
        val available = usage("masterli", 95.0)
        assertFalse(shouldSendFiveHourResetReminder(exhausted, reset, now))
        assertTrue(shouldSendFiveHourResetReminder(available, reset, now))
        assertTrue(shouldSendFiveHourResetReminder(exhausted.copy(remainingPercent = 100.0), reset, now))
    }

    @Test fun replacedOrExpiredFiveHourWindowDoesNotSendReminder() {
        assertFalse(shouldSendFiveHourResetReminder(usage("li", 50.0), reset + 1, now))
        assertFalse(shouldSendFiveHourResetReminder(usage("li", 50.0), reset, reset))
        assertFalse(shouldSendFiveHourResetReminder(usage("li", 50.0), reset, reset - 300_001L))
    }

    private val reset = 1_000_000L
    private val now = reset - 60_000L

    private fun usage(accountId: String, weeklyRemaining: Double?) =
        CodexUsage.empty(accountId, AuthStatus.AUTHENTICATED).copy(
            remainingPercent = weeklyRemaining,
            fiveHourRemainingPercent = 97.0,
            fiveHourResetAtEpochMs = reset
        )
}
