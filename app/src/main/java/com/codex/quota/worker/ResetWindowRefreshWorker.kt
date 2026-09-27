package com.codex.quota.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.codex.quota.CodexQuotaApplication
import com.codex.quota.auth.JwtTokenParser
import com.codex.quota.data.remote.CodexWindowActivator
import com.codex.quota.domain.model.AuthStatus
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
        if (current.account.isDemoAccount || resetAt > System.currentTimeMillis() ||
            System.currentTimeMillis() - resetAt >= FIVE_HOURS_MS) return Result.success()

        val refreshed = app.repository.refreshAccount(accountId)
        if (refreshed.isFailure) return Result.retry()

        if (preferences.autoActivateFiveHourEnabled &&
            refreshed.getOrThrow().status == AuthStatus.AUTHENTICATED &&
            System.currentTimeMillis() - resetAt < FIVE_HOURS_MS &&
            app.credentialStore.getRefreshToken(accountId) != null &&
            app.preferencesRepository.getPreferences().autoActivateFiveHourEnabled
        ) {
            val accessToken = app.credentialStore.getApiKey(accountId)
            val chatgptAccountId = accessToken?.let(JwtTokenParser::parseToken)?.chatgptAccountId
            if (accessToken != null && chatgptAccountId != null &&
                app.dataStoreManager.claimFiveHourActivation(accountId, resetAt)
            ) {
                // The claim is durable before sending. A timeout may have reached the server;
                // therefore no automatic retry follows an uncertain response.
                CodexWindowActivator().activate(accessToken, chatgptAccountId)
                app.repository.refreshAccount(accountId)
            }
        }
        WidgetUpdateHelper.updateAllWidgets(applicationContext)
        return Result.success()
    }

    companion object {
        private const val FIVE_HOURS_MS = 5 * 60 * 60_000L
        const val ACCOUNT_ID = "account_id"
        const val RESET_AT = "reset_at"
    }
}
