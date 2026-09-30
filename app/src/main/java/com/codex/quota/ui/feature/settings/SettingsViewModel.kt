package com.codex.quota.ui.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codex.quota.domain.model.AppThemeMode
import com.codex.quota.domain.model.AccountWithUsage
import com.codex.quota.domain.model.RefreshIntervalMinutes
import com.codex.quota.domain.model.UserPreferences
import com.codex.quota.domain.model.WidgetThemeMode
import com.codex.quota.domain.repository.CodexAccountRepository
import com.codex.quota.domain.repository.UserPreferencesRepository
import com.codex.quota.widget.WidgetUpdateHelper
import com.codex.quota.worker.WorkScheduler
import com.codex.quota.notifications.task.TaskCompletionService
import com.codex.quota.notifications.task.TaskNotificationStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val preferencesRepository: UserPreferencesRepository,
    private val accountRepository: CodexAccountRepository
) : ViewModel() {

    val preferencesState: StateFlow<UserPreferences> = preferencesRepository.observePreferences()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserPreferences()
        )

    val accountsState: StateFlow<List<AccountWithUsage>> = accountRepository.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            preferencesRepository.setThemeMode(mode)
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setDynamicColor(enabled)
        }
    }

    fun setWidgetThemeMode(context: Context, mode: WidgetThemeMode) {
        viewModelScope.launch {
            preferencesRepository.setWidgetThemeMode(mode)
            WidgetUpdateHelper.updateAllWidgets(context)
        }
    }

    fun setBackgroundSyncEnabled(context: Context, enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setBackgroundSyncEnabled(enabled)
            if (enabled) {
                val preferences = preferencesRepository.getPreferences()
                WorkScheduler.schedulePeriodicRefresh(context, preferences.refreshInterval.minutes)
                accountRepository.getAllAccounts().forEach { item ->
                    if (!item.account.isDemoAccount) {
                        WorkScheduler.scheduleFiveHourResetRefresh(
                            context, item.account.id, item.usage,
                            includeOverdue = item.account.id in preferences.autoActivateFiveHourAccountIds
                        )
                    }
                }
            } else {
                WorkScheduler.cancelPeriodicRefresh(context)
                WorkScheduler.cancelFiveHourResetRefresh(context)
            }
        }
    }

    fun setAutoActivateFiveHourAccount(context: Context, accountId: String, enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setAutoActivateFiveHourAccount(accountId, enabled)
            if (enabled && preferencesRepository.getPreferences().backgroundSyncEnabled) {
                val account = accountRepository.getAccount(accountId)
                WorkScheduler.scheduleFiveHourResetRefresh(
                    context, accountId, account?.usage,
                    includeOverdue = true
                )
            }
        }
    }

    fun setRefreshInterval(context: Context, interval: RefreshIntervalMinutes) {
        viewModelScope.launch {
            preferencesRepository.setRefreshInterval(interval)
            if (preferencesState.value.backgroundSyncEnabled) {
                WorkScheduler.schedulePeriodicRefresh(context, interval.minutes)
            }
        }
    }

    fun setRefreshOnAppOpen(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setRefreshOnAppOpen(enabled)
        }
    }

    fun setSignedOutNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setSignedOutNotificationsEnabled(enabled)
        }
    }

    fun setQuotaAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setQuotaAlertsEnabled(enabled)
        }
    }

    fun setIncludeFiveHourQuotaAlerts(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setIncludeFiveHourQuotaAlerts(enabled)
        }
    }

    fun setFiveHourResetReminderEnabled(context: Context, enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setFiveHourResetReminderEnabled(enabled)
            if (enabled) {
                accountRepository.getAllAccounts().forEach { item ->
                    if (!item.account.isDemoAccount) {
                        WorkScheduler.scheduleFiveHourResetReminder(
                            context, item.account.id, item.usage
                        )
                    }
                }
            } else {
                WorkScheduler.cancelFiveHourResetReminders(context)
            }
        }
    }

    fun setIncludeWeeklyQuotaAlerts(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setIncludeWeeklyQuotaAlerts(enabled)
        }
    }

    fun setIncludeGptReserveAlerts(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setIncludeGptReserveAlerts(enabled)
        }
    }

    fun toggleQuotaAlertThreshold(threshold: Int) {
        viewModelScope.launch {
            preferencesRepository.toggleQuotaAlertThreshold(threshold)
        }
    }

    fun clearAllData(context: Context) {
        viewModelScope.launch {
            if (accountRepository.clearAllData().isSuccess) {
                TaskNotificationStore(context).clear()
                TaskCompletionService.stop(context)
                // Any queued reminder becomes invalid when its account is gone.
                preferencesRepository.getPreferences().autoActivateFiveHourAccountIds.forEach { accountId ->
                    preferencesRepository.setAutoActivateFiveHourAccount(accountId, false)
                }
            }
        }
    }
}
