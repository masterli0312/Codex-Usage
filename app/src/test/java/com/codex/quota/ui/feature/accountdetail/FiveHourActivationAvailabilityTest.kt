package com.codex.quota.ui.feature.accountdetail

import com.codex.quota.domain.model.AuthStatus
import com.codex.quota.domain.model.CodexUsage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FiveHourActivationAvailabilityTest {
    private val now = 1_000_000_000L

    @Test
    fun exhaustedWeeklyQuotaDisablesActivationEvenWithAFullExpiredFiveHourWindow() {
        val usage = usageWithReset(now - 60_000L).copy(remainingPercent = 0.0)
        assertFalse(canActivateFiveHourWindow(usage, now))
    }

    @Test
    fun exhaustedAccountDoesNotDisableAnotherAccount() {
        val exhausted = usageWithReset(now - 60_000L).copy(remainingPercent = 0.0)
        val available = exhausted.copy(accountId = "account-2", remainingPercent = 50.0)
        assertFalse(canActivateFiveHourWindow(exhausted, now))
        assertTrue(canActivateFiveHourWindow(available, now))
    }

    @Test
    fun newWindowWithFullQuotaIsAlreadyActive() {
        val usage = usageWithReset(now + 4 * 60 * 60_000L)
        assertFalse(canActivateFiveHourWindow(usage, now))
    }

    @Test
    fun expiredWindowCanBeActivatedEvenWhenItsOldQuotaWasPartlyUsed() {
        val usage = usageWithReset(now - 60_000L).copy(fiveHourRemainingPercent = 42.0)
        assertTrue(canActivateFiveHourWindow(usage, now))
    }

    @Test
    fun inactiveWindowCanStillBeActivatedMoreThanFiveHoursAfterReset() {
        assertTrue(canActivateFiveHourWindow(usageWithReset(now - 6 * 60 * 60_000L), now))
    }

    @Test
    fun unknownOrUnauthenticatedWindowCannotBeActivated() {
        assertFalse(canActivateFiveHourWindow(usageWithReset(null), now))
        assertFalse(canActivateFiveHourWindow(usageWithReset(now - 60_000L).copy(status = AuthStatus.OFFLINE), now))
    }

    private fun usageWithReset(resetAt: Long?) =
        CodexUsage.empty("account-1", AuthStatus.AUTHENTICATED).copy(
            fiveHourResetAtEpochMs = resetAt,
            fiveHourRemainingPercent = 100.0,
            fiveHourUsedPercent = 0.0
        )
}
