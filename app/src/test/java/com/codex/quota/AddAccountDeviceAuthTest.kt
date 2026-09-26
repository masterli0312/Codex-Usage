package com.codex.quota

import android.content.Context
import androidx.core.content.ContextCompat
import com.codex.quota.auth.DeviceCodeManager
import com.codex.quota.auth.DeviceCodeSession
import com.codex.quota.auth.DevicePollResult
import com.codex.quota.domain.repository.CodexAccountRepository
import com.codex.quota.domain.usecase.AddAccountUseCase
import com.codex.quota.ui.feature.addaccount.AddAccountViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddAccountDeviceAuthTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkObject(DeviceCodeManager)
        mockkStatic(ContextCompat::class)
    }

    @After
    fun tearDown() {
        unmockkAll()
        Dispatchers.resetMain()
    }

    @Test
    fun manualCheckWhilePendingDoesNotCreateAccount() = runTest(dispatcher) {
        val context = mockk<Context>()
        every { ContextCompat.getContextForLanguage(context) } returns context
        every { context.getString(any()) } returns "Waiting for approval"
        val repository = mockk<CodexAccountRepository>(relaxed = true)
        val session = DeviceCodeSession("auth-id", "ABCD-EFGH")
        coEvery { DeviceCodeManager.requestDeviceCode() } returns Result.success(session)
        coEvery { DeviceCodeManager.pollDeviceToken(session) } returns DevicePollResult.Pending
        val viewModel = AddAccountViewModel(context, AddAccountUseCase(repository))
        runCurrent()

        viewModel.completeDeviceAuthManually()
        runCurrent()

        assertFalse(viewModel.uiState.value.isSuccess)
        assertFalse(viewModel.uiState.value.isLoading)
        coVerify(exactly = 0) { repository.addAccount(any(), any(), any(), any(), any(), any(), any()) }
    }
}
