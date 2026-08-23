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
        val viewModel = DashboardViewModel(observeAccounts, refreshAccounts)

        viewModel.refreshAll()
        viewModel.refreshAll()

        assertTrue(viewModel.isRefreshing.value)
        runCurrent()
        coVerify(exactly = 1) { refreshAccounts() }

        refreshFinished.complete(Unit)
        advanceUntilIdle()

        assertFalse(viewModel.isRefreshing.value)
    }
}
