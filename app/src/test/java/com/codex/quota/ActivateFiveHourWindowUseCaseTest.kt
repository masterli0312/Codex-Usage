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

    private fun createUseCase(activator: CodexWindowActivator): ActivateFiveHourWindowUseCase {
        val repository = mockk<CodexAccountRepository>()
        coEvery { repository.getAccount(accountId) } returns AccountWithUsage(account, usage)
        coEvery { repository.refreshAccount(accountId) } returns Result.success(usage)
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
