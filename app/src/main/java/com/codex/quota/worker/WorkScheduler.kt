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
import java.util.concurrent.TimeUnit

object WorkScheduler {

    private const val RESET_REFRESH_TAG = "five_hour_reset_refresh"

    fun scheduleFiveHourResetRefresh(context: Context, accountId: String, resetAtEpochMs: Long?) {
        val now = System.currentTimeMillis()
        if (resetAtEpochMs == null || resetAtEpochMs <= now) return

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
            .build()

        // Include the window boundary so a refreshed window never cancels its running predecessor.
        WorkManager.getInstance(context).enqueueUniqueWork(
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
