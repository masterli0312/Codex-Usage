package com.codex.quota.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.codex.quota.CodexQuotaApplication
import com.codex.quota.domain.model.AuthStatus
import com.codex.quota.domain.usecase.FiveHourActivationOutcome
import com.codex.quota.widget.WidgetUpdateHelper

/** Reads official usage shortly after the known five-hour window boundary. */
class ResetWindowRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? CodexQuotaApplication ?: return Result.failure()
        val preferences = app.preferencesRepository.getPreferences()
        if (!preferences.backgroundSyncEnabled) return Result.success()

        val accountId = inputData.getString(ACCOUNT_ID) ?: return Result.failure()
        val resetAt = inputData.getLong(RESET_AT, 0L)
        val current = app.repository.getAccount(accountId) ?: return Result.success()
        if (current.usage?.isWeeklyQuotaExhausted == true) return Result.success()
        if (current.account.isDemoAccount || resetAt <= 0L || resetAt > System.currentTimeMillis()) {
            return Result.success()
        }

        if (accountId in preferences.autoActivateFiveHourAccountIds) {
            val outcome = app.activateFiveHourWindow(accountId, resetAt) {
                app.preferencesRepository.getPreferences().let {
                    it.backgroundSyncEnabled && accountId in it.autoActivateFiveHourAccountIds
                }
            }
            if (outcome == FiveHourActivationOutcome.RefreshFailed) return Result.retry()
        } else {
            val refreshed = app.repository.refreshAccount(accountId).getOrNull()
            if (refreshed?.status !in listOf(AuthStatus.AUTHENTICATED, AuthStatus.AUTHENTICATION_REQUIRED)) {
                return Result.retry()
            }
        }
        WidgetUpdateHelper.updateAllWidgets(applicationContext)
        return Result.success()
    }

    companion object {
        const val ACCOUNT_ID = "account_id"
        const val RESET_AT = "reset_at"
    }
}
