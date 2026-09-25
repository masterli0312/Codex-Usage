package com.codex.quota.ui.feature.accountdetail

import android.content.Context
import androidx.core.content.ContextCompat
import com.codex.quota.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codex.quota.domain.model.AccountWithUsage
import com.codex.quota.domain.model.CodexUsage
import com.codex.quota.domain.repository.CodexAccountRepository
import com.codex.quota.domain.repository.UserPreferencesRepository
import com.codex.quota.domain.usecase.RefreshAccountUseCase
import com.codex.quota.domain.usecase.RemoveAccountUseCase
import com.codex.quota.domain.usecase.UpdateAccountUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AccountDetailViewModel(
    context: Context,
    private val accountId: String,
    private val repository: CodexAccountRepository,
    private val preferencesRepository: UserPreferencesRepository,
    private val refreshAccountUseCase: RefreshAccountUseCase,
    private val updateAccountUseCase: UpdateAccountUseCase,
    private val removeAccountUseCase: RemoveAccountUseCase
) : ViewModel() {

    private val localizedContext = ContextCompat.getContextForLanguage(context)

    val accountState: StateFlow<AccountWithUsage?> = repository.observeAccount(accountId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val isBannerDismissed: StateFlow<Boolean> = preferencesRepository.observePreferences()
        .map { it.dismissedRenewalBannerAccountIds.contains(accountId) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()

    private val _accountDeleted = MutableStateFlow(false)
    val accountDeleted: StateFlow<Boolean> = _accountDeleted.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            val result = refreshAccountUseCase(accountId)
            _isRefreshing.value = false
            if (result.isFailure) {
                _uiMessage.value = localizedContext.getString(R.string.error_refresh_account)
            }
        }
    }

    fun dismissRenewalBanner() {
        viewModelScope.launch {
            preferencesRepository.setRenewalBannerDismissed(accountId, true)
        }
    }

    fun updateAccountDetails(newNickname: String, newColorHex: String, newRenewalDateEpochMs: Long?) {
        viewModelScope.launch {
            val result = updateAccountUseCase(accountId, newNickname, newColorHex, newRenewalDateEpochMs)
            if (result.isSuccess) {
                _uiMessage.value = localizedContext.getString(R.string.account_updated)
            } else {
                _uiMessage.value = localizedContext.getString(R.string.error_update_account)
            }
        }
    }

    fun updateRenewalDate(renewalDateEpochMs: Long?) {
        viewModelScope.launch {
            val result = updateAccountUseCase.setRenewalDate(accountId, renewalDateEpochMs)
            if (result.isSuccess) {
                _uiMessage.value = if (renewalDateEpochMs != null) localizedContext.getString(R.string.renewal_date_updated) else localizedContext.getString(R.string.renewal_date_removed)
            } else {
                _uiMessage.value = localizedContext.getString(R.string.error_update_renewal_date)
            }
        }
    }

    fun reauthenticate(newApiKey: String) {
        viewModelScope.launch {
            _isRefreshing.value = true
            val result = repository.reauthenticateAccount(accountId, newApiKey)
            _isRefreshing.value = false
            if (result.isSuccess) {
                _uiMessage.value = localizedContext.getString(R.string.account_reauthenticated)
            } else {
                _uiMessage.value = localizedContext.getString(R.string.error_reauthentication)
            }
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            val result = removeAccountUseCase(accountId)
            if (result.isSuccess) {
                _accountDeleted.value = true
            } else {
                _uiMessage.value = localizedContext.getString(R.string.error_delete_account)
            }
        }
    }

    fun clearUiMessage() {
        _uiMessage.value = null
    }
}
