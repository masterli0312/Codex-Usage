package com.codex.quota

import com.codex.quota.domain.model.AppThemeMode
import com.codex.quota.domain.model.AuthStatus
import com.codex.quota.domain.model.CodexAccount
import com.codex.quota.domain.model.CodexUsage
import com.codex.quota.domain.model.PlanType
import com.codex.quota.domain.model.QuotaWindow
import com.codex.quota.domain.model.RefreshIntervalMinutes
import com.codex.quota.domain.model.UserPreferences
import com.codex.quota.domain.model.WidgetThemeMode
import com.codex.quota.domain.model.isApiKeyPlan
import com.codex.quota.domain.repository.UserPreferencesRepository
import com.codex.quota.domain.usecase.evaluateQuotaAlertDecision
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class QuotaAlertWorkerLogicTest {

    private lateinit var fakePrefsRepo: FakeUserPreferencesRepository

    @Before
    fun setUp() {
        fakePrefsRepo = FakeUserPreferencesRepository()
    }

    private fun createTestAccount(id: String): CodexAccount {
        return CodexAccount(
            id = id,
            nickname = "Test Account",
            email = "user@test.org",
            planType = PlanType.PLUS,
            organizationId = null,
            colorHex = "#10B981",
            authStatus = AuthStatus.AUTHENTICATED,
            isDemoAccount = true,
            orderIndex = 0,
            createdAtEpochMs = 1000L,
            lastSuccessfulSyncEpochMs = null
        )
    }

    /**
     * Replicates the worker evaluation loop pure logic for test verification.
     */
    private suspend fun runAlertEvaluation(
        account: CodexAccount,
        usage: CodexUsage,
        prefs: UserPreferences
    ): List<Pair<QuotaWindow, Int>> {
        val triggeredAlerts = mutableListOf<Pair<QuotaWindow, Int>>()
        if (!prefs.quotaAlertsEnabled || usage.status != AuthStatus.AUTHENTICATED) {
            return triggeredAlerts
        }

        val thresholds = prefs.quotaAlertThresholds
        val isApiAccount = account.planType.isApiKeyPlan

        // 1. Primary/Weekly evaluation
        val primaryRemaining = usage.remainingPercent
        if (primaryRemaining != null) {
            val lastNotified = fakePrefsRepo.getLastNotifiedQuotaThreshold(account.id, QuotaWindow.WEEKLY)
            val decision = evaluateQuotaAlertDecision(primaryRemaining, thresholds, lastNotified)
            if (decision.shouldClearThreshold) {
                fakePrefsRepo.setLastNotifiedQuotaThreshold(account.id, null, QuotaWindow.WEEKLY)
            } else if (decision.shouldNotify && decision.milestone != null) {
                triggeredAlerts.add(QuotaWindow.WEEKLY to decision.milestone)
                fakePrefsRepo.setLastNotifiedQuotaThreshold(account.id, decision.milestone, QuotaWindow.WEEKLY)
            }
        }

        // 2. 5-hour evaluation (only if opt-in enabled and not an API account)
        if (prefs.includeFiveHourQuotaAlerts && !isApiAccount) {
            val fiveHourRemaining = usage.fiveHourRemainingPercent
            if (fiveHourRemaining != null) {
                val lastNotified = fakePrefsRepo.getLastNotifiedQuotaThreshold(account.id, QuotaWindow.FIVE_HOUR)
                val decision = evaluateQuotaAlertDecision(fiveHourRemaining, thresholds, lastNotified)
                if (decision.shouldClearThreshold) {
                    fakePrefsRepo.setLastNotifiedQuotaThreshold(account.id, null, QuotaWindow.FIVE_HOUR)
                } else if (decision.shouldNotify && decision.milestone != null) {
                    triggeredAlerts.add(QuotaWindow.FIVE_HOUR to decision.milestone)
                    fakePrefsRepo.setLastNotifiedQuotaThreshold(account.id, decision.milestone, QuotaWindow.FIVE_HOUR)
                }
            }
        }

        return triggeredAlerts
    }

    @Test
    fun workerEvaluation_when5HourOptInDisabled_onlyEvaluatesWeeklyWindow() = runTest {
        val account = createTestAccount("acc-1")
        val usage = CodexUsage(
            accountId = account.id,
            remainingPercent = 8.0, // <= 10% milestone
            usedPercent = 92.0,
            usedTokens = null,
            totalLimitTokens = null,
            remainingCredits = null,
            resetAtEpochMs = 2000L,
            status = AuthStatus.AUTHENTICATED,
            fetchedAtEpochMs = 1000L,
            fiveHourRemainingPercent = 4.0 // <= 5% milestone
        )

        val prefs = UserPreferences(
            quotaAlertsEnabled = true,
            includeFiveHourQuotaAlerts = false,
            quotaAlertThresholds = setOf(5, 10, 25)
        )

        val alerts = runAlertEvaluation(account, usage, prefs)

        // Only weekly should trigger
        assertEquals(1, alerts.size)
        assertEquals(QuotaWindow.WEEKLY to 10, alerts[0])
        assertEquals(10, fakePrefsRepo.getLastNotifiedQuotaThreshold(account.id, QuotaWindow.WEEKLY))
        assertNull(fakePrefsRepo.getLastNotifiedQuotaThreshold(account.id, QuotaWindow.FIVE_HOUR))
    }

    @Test
    fun workerEvaluation_when5HourOptInEnabled_evaluatesBothWindowsIndependently() = runTest {
        val account = createTestAccount("acc-2")
        val usage = CodexUsage(
            accountId = account.id,
            remainingPercent = 22.0, // <= 25% milestone
            usedPercent = 78.0,
            usedTokens = null,
            totalLimitTokens = null,
            remainingCredits = null,
            resetAtEpochMs = 2000L,
            status = AuthStatus.AUTHENTICATED,
            fetchedAtEpochMs = 1000L,
            fiveHourRemainingPercent = 4.0 // <= 5% milestone
        )

        val prefs = UserPreferences(
            quotaAlertsEnabled = true,
            includeFiveHourQuotaAlerts = true,
            quotaAlertThresholds = setOf(5, 10, 25)
        )

        val alerts = runAlertEvaluation(account, usage, prefs)

        // Both weekly and 5-hour should trigger
        assertEquals(2, alerts.size)
        assertEquals(QuotaWindow.WEEKLY to 25, alerts[0])
        assertEquals(QuotaWindow.FIVE_HOUR to 5, alerts[1])

        // Dedupe state stored independently per window
        assertEquals(25, fakePrefsRepo.getLastNotifiedQuotaThreshold(account.id, QuotaWindow.WEEKLY))
        assertEquals(5, fakePrefsRepo.getLastNotifiedQuotaThreshold(account.id, QuotaWindow.FIVE_HOUR))

        // Second run with same values should produce 0 alerts
        val secondRunAlerts = runAlertEvaluation(account, usage, prefs)
        assertEquals(0, secondRunAlerts.size)
    }

    @Test
    fun workerEvaluation_apiKeyAccount_neverEvaluatesFiveHourWindow() = runTest {
        val account = createTestAccount("acc-api").copy(planType = PlanType.API_TIER_1)
        val usage = CodexUsage(
            accountId = account.id,
            remainingPercent = 8.0, // <= 10% milestone
            usedPercent = 92.0,
            usedTokens = null,
            totalLimitTokens = null,
            remainingCredits = null,
            resetAtEpochMs = null,
            status = AuthStatus.AUTHENTICATED,
            fetchedAtEpochMs = 1000L,
            fiveHourRemainingPercent = 4.0 // would trigger 5% if evaluated
        )

        val prefs = UserPreferences(
            quotaAlertsEnabled = true,
            includeFiveHourQuotaAlerts = true,
            quotaAlertThresholds = setOf(5, 10, 25)
        )

        val alerts = runAlertEvaluation(account, usage, prefs)

        // Only generic primary alert triggered, 5-hour is skipped
        assertEquals(1, alerts.size)
        assertEquals(QuotaWindow.WEEKLY to 10, alerts[0])
        assertEquals(10, fakePrefsRepo.getLastNotifiedQuotaThreshold(account.id, QuotaWindow.WEEKLY))
        assertNull(fakePrefsRepo.getLastNotifiedQuotaThreshold(account.id, QuotaWindow.FIVE_HOUR))
    }

    @Test
    fun workerEvaluation_weeklyAnd5HourDedupe_doNotOverwriteEachOther() = runTest {
        val account = createTestAccount("acc-3")
        fakePrefsRepo.setLastNotifiedQuotaThreshold(account.id, 25, QuotaWindow.WEEKLY)
        fakePrefsRepo.setLastNotifiedQuotaThreshold(account.id, 5, QuotaWindow.FIVE_HOUR)

        assertEquals(25, fakePrefsRepo.getLastNotifiedQuotaThreshold(account.id, QuotaWindow.WEEKLY))
        assertEquals(5, fakePrefsRepo.getLastNotifiedQuotaThreshold(account.id, QuotaWindow.FIVE_HOUR))

        // Clearing weekly should not affect 5-hour
        fakePrefsRepo.setLastNotifiedQuotaThreshold(account.id, null, QuotaWindow.WEEKLY)
        assertNull(fakePrefsRepo.getLastNotifiedQuotaThreshold(account.id, QuotaWindow.WEEKLY))
        assertEquals(5, fakePrefsRepo.getLastNotifiedQuotaThreshold(account.id, QuotaWindow.FIVE_HOUR))
    }
}

class FakeUserPreferencesRepository : UserPreferencesRepository {
    private val preferencesFlow = MutableStateFlow(UserPreferences())
    private val thresholdStorage = mutableMapOf<Pair<String, QuotaWindow>, Int>()
    private val signedOutStorage = mutableMapOf<String, Boolean>()

    override fun observePreferences(): Flow<UserPreferences> = preferencesFlow
    override suspend fun getPreferences(): UserPreferences = preferencesFlow.value

    override suspend fun setThemeMode(mode: AppThemeMode) {}
    override suspend fun setDynamicColor(enabled: Boolean) {}
    override suspend fun setWidgetThemeMode(mode: WidgetThemeMode) {}
    override suspend fun setBackgroundSyncEnabled(enabled: Boolean) {}
    override suspend fun setRefreshInterval(interval: RefreshIntervalMinutes) {}
    override suspend fun setRefreshOnAppOpen(enabled: Boolean) {}
    override suspend fun setSignedOutNotificationsEnabled(enabled: Boolean) {}
    override suspend fun setQuotaAlertsEnabled(enabled: Boolean) {}
    override suspend fun setIncludeFiveHourQuotaAlerts(enabled: Boolean) {}
    override suspend fun setQuotaAlertThresholds(thresholds: Set<Int>) {}
    override suspend fun toggleQuotaAlertThreshold(threshold: Int) {}
    override suspend fun setHasCompletedOnboarding(completed: Boolean) {}
    override suspend fun setRenewalBannerDismissed(accountId: String, dismissed: Boolean) {}

    override suspend fun getLastNotifiedQuotaThreshold(accountId: String, window: QuotaWindow): Int? =
        thresholdStorage[accountId to window]

    override suspend fun setLastNotifiedQuotaThreshold(accountId: String, threshold: Int?, window: QuotaWindow) {
        if (threshold != null) {
            thresholdStorage[accountId to window] = threshold
        } else {
            thresholdStorage.remove(accountId to window)
        }
    }

    override suspend fun isSignedOutAlertNotified(accountId: String): Boolean =
        signedOutStorage[accountId] ?: false

    override suspend fun setSignedOutAlertNotified(accountId: String, notified: Boolean) {
        if (notified) {
            signedOutStorage[accountId] = true
        } else {
            signedOutStorage.remove(accountId)
        }
    }
}
