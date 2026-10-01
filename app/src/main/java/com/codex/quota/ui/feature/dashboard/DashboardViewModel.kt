package com.codex.quota.ui.feature.dashboard

import androidx.lifecycle.ViewModel
import com.codex.quota.R
import androidx.lifecycle.viewModelScope
import com.codex.quota.domain.model.AccountWithUsage
import com.codex.quota.domain.usecase.ObserveAccountsUseCase
import com.codex.quota.domain.usecase.RefreshAllAccountsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data class Success(
        val accounts: List<AccountWithUsage>,
        val isRefreshing: Boolean = false,
        val errorMessage: String? = null
    ) : DashboardUiState
}

class DashboardViewModel(
    private val observeAccountsUseCase: ObserveAccountsUseCase,
    private val repository: com.codex.quota.domain.repository.CodexAccountRepository,
    private val refreshAllAccountsUseCase: RefreshAllAccountsUseCase
) : ViewModel() {

    val refreshFeedback = MutableStateFlow<Map<String, Int>>(emptyMap())
    val refreshingAccountIds = repository.refreshingAccountIds
        .onEach { ids -> refreshFeedback.update { it - ids } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val isSaving = MutableStateFlow(false)
    val saveError = MutableStateFlow<Int?>(null)

    fun clearSaveError() { saveError.value = null }

    fun rename(accountId: String, name: String, onSaved: () -> Unit) = save(onSaved) {
        repository.renameAccount(accountId, name.trim())
    }
    fun reorder(ids: List<String>, onSaved: () -> Unit) = save(onSaved) { repository.reorderAccounts(ids) }
    private fun save(onSaved: () -> Unit, operation: suspend () -> Result<Unit>) {
        if (!isSaving.compareAndSet(false, true)) return
        saveError.value = null
        viewModelScope.launch {
            try {
                if (operation().isSuccess) onSaved() else saveError.value = R.string.error_update_account
            } catch (error: kotlinx.coroutines.CancellationException) { throw error }
            catch (_: Exception) { saveError.value = R.string.error_update_account }
            finally { isSaving.value = false }
        }
    }

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _errorMessage = MutableStateFlow<Int?>(null)
    val errorMessage: StateFlow<Int?> = _errorMessage.asStateFlow()

    val accountsState: StateFlow<List<AccountWithUsage>> = observeAccountsUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    fun refreshAll() {
        if (!_isRefreshing.compareAndSet(expect = false, update = true)) return

        viewModelScope.launch {
            try {
                refreshFeedback.value = emptyMap()
                val ids = accountsState.value.map { it.account.id }
                val result = refreshAllAccountsUseCase()
                val refreshed = result.getOrNull().orEmpty().associateBy { it.accountId }
                refreshFeedback.value = ids.associateWith {
                    if (refreshed[it]?.status == com.codex.quota.domain.model.AuthStatus.AUTHENTICATED)
                        R.string.account_refresh_success else R.string.account_refresh_failed
                }
                if (result.isFailure || refreshFeedback.value.values.any { it == R.string.account_refresh_failed }) {
                    _errorMessage.value = R.string.error_refresh_quotas
                }
            } catch (error: kotlinx.coroutines.CancellationException) { throw error }
            catch (_: Exception) { _errorMessage.value = R.string.error_refresh_quotas }
            finally {
                _isRefreshing.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
