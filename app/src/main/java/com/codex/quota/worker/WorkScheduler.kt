package com.codex.quota.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.codex.quota.domain.model.CodexUsage
import java.util.concurrent.TimeUnit

object WorkScheduler {

    private const val RESET_REFRESH_TAG = "five_hour_reset_refresh"
    private const val RESET_REMINDER_TAG = "five_hour_reset_reminder"

    fun scheduleFiveHourResetReminder(context: Context, accountId: String, usage: CodexUsage?) {
        val manager = WorkManager.getInstance(context)
        val name = "$RESET_REMINDER_TAG:$accountId"
        val now = System.currentTimeMillis()
        val resetAtEpochMs = usage?.fiveHourResetAtEpochMs
        if (!hasWeeklyQuotaForFiveHourReminder(usage) || resetAtEpochMs == null || resetAtEpochMs <= now) {
            manager.cancelUniqueWork(name)
            return
        }
        val delay = (resetAtEpochMs - FiveHourResetReminderWorker.LEAD_TIME_MS - now).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<FiveHourResetReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(
                FiveHourResetReminderWorker.ACCOUNT_ID to accountId,
                FiveHourResetReminderWorker.RESET_AT to resetAtEpochMs
            ))
            .addTag(RESET_REMINDER_TAG)
            .build()
        manager.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancelFiveHourResetReminders(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(RESET_REMINDER_TAG)
    }

    fun scheduleFiveHourResetRefresh(
        context: Context,
        accountId: String,
        usage: CodexUsage?,
        includeOverdue: Boolean = false
    ) {
        val manager = WorkManager.getInstance(context)
        val accountTag = "$RESET_REFRESH_TAG:$accountId"
        if (usage?.isWeeklyQuotaExhausted == true) {
            manager.cancelAllWorkByTag(accountTag)
            // Also cancel jobs scheduled by earlier app versions without the account tag.
            usage.fiveHourResetAtEpochMs?.let { manager.cancelUniqueWork("$accountTag:$it") }
            return
        }
        val resetAtEpochMs = usage?.fiveHourResetAtEpochMs
        val now = System.currentTimeMillis()
        if (resetAtEpochMs == null || resetAtEpochMs <= 0L ||
            (resetAtEpochMs <= now && !includeOverdue)) return

        val request = OneTimeWorkRequestBuilder<ResetWindowRefreshWorker>()
            .setInitialDelay((resetAtEpochMs - now + 30_000L).coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setInputData(workDataOf(
                ResetWindowRefreshWorker.ACCOUNT_ID to accountId,
                ResetWindowRefreshWorker.RESET_AT to resetAtEpochMs
            ))
            .addTag(RESET_REFRESH_TAG)
            .addTag(accountTag)
            .build()

        // Include the window boundary so a refreshed window never cancels its running predecessor.
        manager.enqueueUniqueWork(
            "$RESET_REFRESH_TAG:$accountId:$resetAtEpochMs",
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun cancelFiveHourResetRefresh(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(RESET_REFRESH_TAG)
    }

    fun schedulePeriodicRefresh(context: Context, intervalMinutes: Long) {
        val workManager = WorkManager.getInstance(context)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(false)
            .build()

        val periodicWorkRequest = PeriodicWorkRequestBuilder<QuotaRefreshWorker>(
            intervalMinutes.coerceAtLeast(15), TimeUnit.MINUTES,
            5, TimeUnit.MINUTES // Flex interval
        )
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            QuotaRefreshWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicWorkRequest
        )
    }

    fun cancelPeriodicRefresh(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(QuotaRefreshWorker.UNIQUE_WORK_NAME)
    }
}
