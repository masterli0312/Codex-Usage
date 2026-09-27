package com.codex.quota.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.codex.quota.CodexQuotaApplication
import com.codex.quota.domain.model.isApiKeyPlan
import com.codex.quota.notifications.FiveHourResetNotificationManager

internal fun isFiveHourReminderDue(resetAtEpochMs: Long, nowEpochMs: Long): Boolean =
    nowEpochMs >= resetAtEpochMs - FiveHourResetReminderWorker.LEAD_TIME_MS && nowEpochMs < resetAtEpochMs

class FiveHourResetReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? CodexQuotaApplication ?: return Result.failure()
        val accountId = inputData.getString(ACCOUNT_ID) ?: return Result.failure()
        val resetAt = inputData.getLong(RESET_AT, 0L)
        if (!app.preferencesRepository.getPreferences().fiveHourResetReminderEnabled) return Result.success()

        val item = app.repository.getAccount(accountId) ?: return Result.success()
        if (item.account.isDemoAccount || item.account.planType.isApiKeyPlan ||
            item.usage?.fiveHourResetAtEpochMs != resetAt ||
            !isFiveHourReminderDue(resetAt, System.currentTimeMillis())) return Result.success()

        val notifications = FiveHourResetNotificationManager(applicationContext)
        if (!notifications.canNotify()) return Result.success()
        if (app.dataStoreManager.claimFiveHourResetReminder(accountId, resetAt)) {
            notifications.showReminder(item.account)
        }
        return Result.success()
    }

    companion object {
        const val ACCOUNT_ID = "account_id"
        const val RESET_AT = "reset_at"
        const val LEAD_TIME_MS = 5 * 60_000L
    }
}
