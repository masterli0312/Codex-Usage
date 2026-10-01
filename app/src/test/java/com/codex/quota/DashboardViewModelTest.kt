package com.codex.quota

import com.codex.quota.domain.model.CodexUsage
import com.codex.quota.domain.usecase.ObserveAccountsUseCase
import com.codex.quota.domain.usecase.RefreshAllAccountsUseCase
import com.codex.quota.ui.feature.dashboard.DashboardViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun refreshAll_whileRefreshIsRunning_doesNotStartAnotherRefresh() = runTest(dispatcher) {
        val observeAccounts = mockk<ObserveAccountsUseCase>()
        every { observeAccounts() } returns flowOf(emptyList())

        val refreshFinished = CompletableDeferred<Unit>()
        val refreshAccounts = mockk<RefreshAllAccountsUseCase>()
        coEvery { refreshAccounts() } coAnswers {
            refreshFinished.await()
            Result.success(emptyList<CodexUsage>())
        }
        val repository = mockk<com.codex.quota.domain.repository.CodexAccountRepository>()
        every { repository.refreshingAccountIds } returns flowOf(emptySet())
        val viewModel = DashboardViewModel(observeAccounts, repository, refreshAccounts)

        viewModel.refreshAll()
        viewModel.refreshAll()

        assertTrue(viewModel.isRefreshing.value)
        runCurrent()
        coVerify(exactly = 1) { refreshAccounts() }

        refreshFinished.complete(Unit)
        advanceUntilIdle()

        assertFalse(viewModel.isRefreshing.value)
    }
    @Test
    fun partialRefreshReportsEachAccountWithoutClaimingTotalSuccess() = runTest(dispatcher) {
        val repo = mockk<com.codex.quota.domain.repository.CodexAccountRepository>()
        val a = com.codex.quota.domain.model.CodexAccount("a", "a", null, com.codex.quota.domain.model.PlanType.PLUS,
            null, "#10B981", com.codex.quota.domain.model.AuthStatus.AUTHENTICATED, false, 0, 1, 1)
        val b = a.copy(id = "b")
        every { repo.observeAccounts() } returns flowOf(listOf(com.codex.quota.domain.model.AccountWithUsage(a, null),
            com.codex.quota.domain.model.AccountWithUsage(b, null)))
        every { repo.refreshingAccountIds } returns flowOf(emptySet())
        coEvery { repo.refreshAllAccounts() } returns Result.success(listOf(CodexUsage.empty("a", com.codex.quota.domain.model.AuthStatus.AUTHENTICATED)))
        val vm = DashboardViewModel(ObserveAccountsUseCase(repo), repo, RefreshAllAccountsUseCase(repo))
        runCurrent()
        vm.refreshAll()
        advanceUntilIdle()
        org.junit.Assert.assertEquals(R.string.account_refresh_success, vm.refreshFeedback.value["a"])
        org.junit.Assert.assertEquals(R.string.account_refresh_failed, vm.refreshFeedback.value["b"])
        org.junit.Assert.assertEquals(R.string.error_refresh_quotas, vm.errorMessage.value)
        assertFalse(vm.isRefreshing.value)
    }

    @Test
    fun renameFailureKeepsSheetOpenAndAllowsRetry() = runTest(dispatcher) {
        val repo = mockk<com.codex.quota.domain.repository.CodexAccountRepository>()
        every { repo.observeAccounts() } returns flowOf(emptyList())
        every { repo.refreshingAccountIds } returns flowOf(emptySet())
        coEvery { repo.renameAccount("a", "new") } returns Result.failure(Exception("fixture"))
        val vm = DashboardViewModel(ObserveAccountsUseCase(repo), repo, RefreshAllAccountsUseCase(repo))
        var closed = false
        vm.rename("a", "new") { closed = true }
        advanceUntilIdle()
        assertFalse(closed)
        assertFalse(vm.isSaving.value)
        org.junit.Assert.assertEquals(R.string.error_update_account, vm.saveError.value)
        coEvery { repo.renameAccount("a", "new") } returns Result.success(Unit)
        vm.rename("a", "new") { closed = true }
        advanceUntilIdle()
        assertTrue(closed)
        org.junit.Assert.assertNull(vm.saveError.value)
    }

}
