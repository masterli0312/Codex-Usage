package com.codex.quota

import com.codex.quota.auth.DecodedTokenInfo
import com.codex.quota.auth.JwtTokenParser
import com.codex.quota.data.remote.CodexWindowActivator
import com.codex.quota.domain.model.AccountWithUsage
import com.codex.quota.domain.model.AuthStatus
import com.codex.quota.domain.model.CodexAccount
import com.codex.quota.domain.model.CodexUsage
import com.codex.quota.domain.model.PlanType
import com.codex.quota.domain.repository.CodexAccountRepository
import com.codex.quota.domain.repository.FiveHourActivationStore
import com.codex.quota.domain.usecase.ActivateFiveHourWindowUseCase
import com.codex.quota.domain.usecase.FiveHourActivationOutcome
import com.codex.quota.security.CredentialStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivateFiveHourWindowUseCaseTest {
    private val accountId = "account-1"
    private val resetAt = System.currentTimeMillis() - 1_000L
    private val usage = CodexUsage.empty(accountId, AuthStatus.AUTHENTICATED)
    private val account = CodexAccount(
        id = accountId, nickname = "Account", email = null, planType = PlanType.PLUS,
        organizationId = null, colorHex = "#10B981", authStatus = AuthStatus.AUTHENTICATED,
        isDemoAccount = false, orderIndex = 0, createdAtEpochMs = 0L, lastSuccessfulSyncEpochMs = null
    )

    @After
    fun tearDown() { unmockkObject(JwtTokenParser) }

    @Test
    fun transientQuotaFailuresRemainRetryableWithoutSendingAnActivation() = runTest {
        for (status in listOf(AuthStatus.OFFLINE, AuthStatus.TEMPORARY_ERROR)) {
            val activator = mockk<CodexWindowActivator>()
            val useCase = createUseCase(activator, usage.copy(status = status))

            assertEquals(FiveHourActivationOutcome.RefreshFailed, useCase(accountId, resetAt))
            coVerify(exactly = 0) { activator.activate(any(), any()) }
        }
    }

    @Test
    fun recoveredNetworkCanActivateTheSameWindowAfterAnOfflinePreflight() = runTest {
        val activator = mockk<CodexWindowActivator>()
        coEvery { activator.activate("token", "chatgpt-account") } returns Result.success(Unit)
        val repository = mockk<CodexAccountRepository>()
        val useCase = createUseCase(activator, repository = repository)
        coEvery { repository.refreshAccount(accountId) } returnsMany listOf(
            Result.success(usage.copy(status = AuthStatus.OFFLINE)),
            Result.success(usage),
            Result.success(usage)
        )

        assertEquals(FiveHourActivationOutcome.RefreshFailed, useCase(accountId, resetAt))
        assertTrue(useCase(accountId, resetAt) is FiveHourActivationOutcome.Success)
        assertEquals(FiveHourActivationOutcome.AlreadyAttempted, useCase(accountId, resetAt))
        coVerify(exactly = 1) { activator.activate("token", "chatgpt-account") }
    }

    @Test
    fun expiredLoginDoesNotRetryOrSendAnActivation() = runTest {
        val activator = mockk<CodexWindowActivator>()
        val useCase = createUseCase(activator, usage.copy(status = AuthStatus.AUTHENTICATION_REQUIRED))

        assertEquals(FiveHourActivationOutcome.LoginRequired, useCase(accountId, resetAt))
        coVerify(exactly = 0) { activator.activate(any(), any()) }
    }

    @Test
    fun exhaustedWeeklyQuotaDoesNotActivateAFullFiveHourWindow() = runTest {
        val activator = mockk<CodexWindowActivator>()
        coEvery { activator.activate("token", "chatgpt-account") } returns Result.success(Unit)
        val exhausted = usage.copy(remainingPercent = 0.0, fiveHourRemainingPercent = 100.0)

        assertEquals(FiveHourActivationOutcome.NotDue, createUseCase(activator, exhausted)(accountId, resetAt))
        coVerify(exactly = 0) { activator.activate(any(), any()) }
    }

    @Test
    fun restoredWeeklyQuotaCanActivateWithoutClearingTheAccountPreference() = runTest {
        val activator = mockk<CodexWindowActivator>()
        coEvery { activator.activate("token", "chatgpt-account") } returns Result.success(Unit)
        val exhausted = usage.copy(remainingPercent = 0.0)
        val restored = usage.copy(remainingPercent = 100.0)

        assertTrue(createUseCase(activator, restored, exhausted)(accountId, resetAt) is FiveHourActivationOutcome.Success)
        coVerify(exactly = 1) { activator.activate("token", "chatgpt-account") }
    }

    @Test
    fun completedRequestIsSentOnlyOnceForTheSameWindow() = runTest {
        val activator = mockk<CodexWindowActivator>()
        coEvery { activator.activate("token", "chatgpt-account") } returns Result.success(Unit)
        val useCase = createUseCase(activator)

        assertTrue(useCase(accountId, resetAt) is FiveHourActivationOutcome.Success)
        assertEquals(FiveHourActivationOutcome.AlreadyAttempted, useCase(accountId, resetAt))
        coVerify(exactly = 1) { activator.activate("token", "chatgpt-account") }
    }

    @Test
    fun uncertainRequestIsNotAutomaticallyRepeated() = runTest {
        val activator = mockk<CodexWindowActivator>()
        coEvery { activator.activate("token", "chatgpt-account") } returns
            Result.failure(IllegalStateException("network result unknown"))
        val useCase = createUseCase(activator)

        assertEquals(FiveHourActivationOutcome.Uncertain, useCase(accountId, resetAt))
        assertEquals(FiveHourActivationOutcome.AlreadyAttempted, useCase(accountId, resetAt))
        coVerify(exactly = 1) { activator.activate("token", "chatgpt-account") }
    }

    @Test
    fun inactiveWindowCanBeActivatedAfterMoreThanFiveHours() = runTest {
        val oldResetAt = System.currentTimeMillis() - 6 * 60 * 60_000L
        val activator = mockk<CodexWindowActivator>()
        coEvery { activator.activate("token", "chatgpt-account") } returns Result.success(Unit)
        val useCase = createUseCase(activator)

        assertTrue(useCase(accountId, oldResetAt) is FiveHourActivationOutcome.Success)
        coVerify(exactly = 1) { activator.activate("token", "chatgpt-account") }
    }

    @Test
    fun refreshedActiveWindowDoesNotSendAnotherRequest() = runTest {
        val activator = mockk<CodexWindowActivator>()
        val activeUsage = usage.copy(
            fiveHourResetAtEpochMs = System.currentTimeMillis() + 4 * 60 * 60_000L,
            fiveHourRemainingPercent = 100.0,
            fiveHourUsedPercent = 0.0
        )
        val useCase = createUseCase(activator, activeUsage)

        assertEquals(FiveHourActivationOutcome.NotDue, useCase(accountId, resetAt))
        coVerify(exactly = 0) { activator.activate(any(), any()) }
    }

    @Test
    fun overdueWindowThatAlreadyRolledOverDoesNotSendRequest() = runTest {
        val oldResetAt = System.currentTimeMillis() - 6 * 60 * 60_000L
        val activeUsage = usage.copy(fiveHourResetAtEpochMs = System.currentTimeMillis() + 4 * 60 * 60_000L)
        val activator = mockk<CodexWindowActivator>()
        val useCase = createUseCase(activator, activeUsage)

        assertEquals(FiveHourActivationOutcome.NotDue, useCase(accountId, oldResetAt))
        coVerify(exactly = 0) { activator.activate(any(), any()) }
    }

    private fun createUseCase(
        activator: CodexWindowActivator,
        refreshedUsage: CodexUsage = usage,
        cachedUsage: CodexUsage = usage,
        repository: CodexAccountRepository = mockk()
    ): ActivateFiveHourWindowUseCase {
        coEvery { repository.getAccount(accountId) } returns AccountWithUsage(account, cachedUsage)
        coEvery { repository.refreshAccount(accountId) } returns Result.success(refreshedUsage)
        val credentialStore = mockk<CredentialStore>()
        every { credentialStore.getApiKey(accountId) } returns "token"
        mockkObject(JwtTokenParser)
        every { JwtTokenParser.parseToken("token") } returns DecodedTokenInfo(
            email = null, userId = null, organizationId = null, chatgptAccountId = "chatgpt-account"
        )
        val claimed = mutableSetOf<Pair<String, Long>>()
        val operationStore = object : FiveHourActivationStore {
            override suspend fun claimFiveHourActivation(accountId: String, resetAtEpochMs: Long): Boolean =
                claimed.add(accountId to resetAtEpochMs)
        }
        return ActivateFiveHourWindowUseCase(repository, credentialStore, operationStore, activator)
    }
}
