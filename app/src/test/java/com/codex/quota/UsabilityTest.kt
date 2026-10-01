package com.codex.quota

import com.codex.quota.domain.model.*
import com.codex.quota.ui.navigation.accountIdFromNotificationLink
import com.codex.quota.data.local.dao.CreditHistoryDao
import com.codex.quota.data.local.entity.CreditHistoryEntity
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class UsabilityTest {
    private val now = 1_000_000L
    private fun usage() = CodexUsage.empty("a", AuthStatus.AUTHENTICATED).copy(
        fetchedAtEpochMs = now, remainingPercent = 50.0, fiveHourResetAtEpochMs = now + 1_000)

    @Test fun futureWindowIsActiveEvenWhenRemainingIs100() {
        assertEquals(ActivationDisplayState.ACTIVE, activationDisplayState(usage().copy(fiveHourRemainingPercent = 100.0), true, true, null, now))
    }
    @Test fun exhaustedWeekPreventsActivationStatus() {
        assertEquals(ActivationDisplayState.WEEKLY_EXHAUSTED, activationDisplayState(usage().copy(remainingPercent = 0.0), true, true, null, now))
    }
    @Test fun offlineAndUncertainClaimsNeverReportSuccess() {
        assertEquals(ActivationDisplayState.WAITING_NETWORK, activationDisplayState(usage().copy(status = AuthStatus.OFFLINE), true, true, null, now))
        assertEquals(ActivationDisplayState.CHECK_RESULT, activationDisplayState(usage().copy(fiveHourResetAtEpochMs = now - 1), true, true, now - 1, now))
    }
    @Test fun staleAndMissingDataNeverReportActive() {
        assertEquals(ActivationDisplayState.CHECK_DATA, activationDisplayState(usage().copy(fetchedAtEpochMs = 0), true, true, null, now + CodexUsage.STALE_THRESHOLD_MS))
        assertEquals(ActivationDisplayState.CHECK_DATA, activationDisplayState(null, true, true, null, now))
    }
    @Test fun settingsAndClaimsAreAccountSpecific() {
        assertEquals(ActivationDisplayState.OFF, activationDisplayState(usage(), false, true, null, now))
        assertEquals(ActivationDisplayState.BACKGROUND_OFF, activationDisplayState(usage(), true, false, null, now))
        assertEquals(ActivationDisplayState.WAITING_ACTIVATION, activationDisplayState(usage().copy(fiveHourResetAtEpochMs = now - 1), true, true, null, now))
    }
    @Test fun notificationLinkRejectsOtherRoutesAndExtraSegments() {
        assertEquals("account-a", accountIdFromNotificationLink("codexquota://account/account-a"))
        assertNull(accountIdFromNotificationLink("https://account/account-a"))
        assertNull(accountIdFromNotificationLink("codexquota://oauth/account-a"))
        assertNull(accountIdFromNotificationLink("codexquota://account/a/b"))
    }
    @Test fun historyStoresBaselineThenOnlyChangesWithDelta() = runTest {
        val dao = spyk(object : CreditHistoryDao {
            val rows = mutableListOf<CreditHistoryEntity>()
            override fun observe(accountId: String) = kotlinx.coroutines.flow.flowOf(rows.filter { it.accountId == accountId })
            override suspend fun latest(accountId: String) = rows.lastOrNull { it.accountId == accountId }
            override suspend fun insert(entry: CreditHistoryEntity) { rows.add(entry) }
            override suspend fun prune(accountId: String) { }
            override suspend fun accountExists(accountId: String) = true
        })
        dao.record("a", 3000.0, 10)
        dao.record("a", 3000.0, 20)
        dao.record("a", 2999.27, 30)
        coVerify(exactly = 2) { dao.insert(any()) }
        coVerify { dao.insert(match { it.accountId == "a" && it.balance == 3000.0 && it.change == null }) }
        assertEquals(-0.73, dao.latest("a")!!.change!!, 0.00001)
        dao.record("b", 100.0, 40)
        assertNull(dao.latest("b")!!.change)
    }
    @Test fun historyRejectsInvalidBalancesAndOutOfOrderSnapshots() = runTest {
        val dao = mockk<CreditHistoryDao>()
        coEvery { dao.accountExists(any()) } returns true
        coEvery { dao.latest("a") } returns CreditHistoryEntity(accountId = "a", balance = 10.0, observedAtEpochMs = 100, change = null)
        coEvery { dao.record(any(), any(), any()) } coAnswers { callOriginal() }
        dao.record("a", Double.NaN, 200)
        dao.record("a", Double.POSITIVE_INFINITY, 200)
        dao.record("a", -1.0, 200)
        dao.record("a", 9.0, 99)
        coVerify(exactly = 0) { dao.insert(any()) }
    }
}
