package com.codex.quota.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.codex.quota.CodexQuotaApplication
import com.codex.quota.domain.model.AuthStatus
import com.codex.quota.domain.model.CodexAccount
import com.codex.quota.domain.model.CodexUsage
import com.codex.quota.domain.model.QuotaWindow
import com.codex.quota.domain.model.isApiKeyPlan
import com.codex.quota.domain.repository.UserPreferencesRepository
import com.codex.quota.domain.usecase.evaluateQuotaAlertDecision
import com.codex.quota.notifications.QuotaAlertNotificationManager
import com.codex.quota.notifications.SignedOutNotificationManager
import com.codex.quota.ui.util.isApiKeyQuotaUsage
import com.codex.quota.widget.WidgetUpdateHelper

class QuotaRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? CodexQuotaApplication ?: return Result.failure()
        val repository = app.repository
        val prefsRepo = app.preferencesRepository
        val signedOutNotificationManager = SignedOutNotificationManager(applicationContext)
        val quotaAlertNotificationManager = QuotaAlertNotificationManager(applicationContext)

        val preferences = prefsRepo.getPreferences()

        val refreshResult = repository.refreshAllAccounts()

        // Fetch refreshed accounts
        val currentAccounts = repository.getAllAccounts()

        // Check for signed-out transitions (notify only ONCE per sign-out event)
        if (preferences.signedOutNotificationsEnabled) {
            val signedOutAccounts = currentAccounts.filter {
                it.usage?.status == AuthStatus.AUTHENTICATION_REQUIRED ||
                        it.account.authStatus == AuthStatus.AUTHENTICATION_REQUIRED
            }

            for (item in signedOutAccounts) {
                val alreadyNotified = prefsRepo.isSignedOutAlertNotified(item.account.id)
                if (!alreadyNotified) {
                    signedOutNotificationManager.showSignedOutNotification(item.account)
                    prefsRepo.setSignedOutAlertNotified(item.account.id, true)
                }
            }

            // For authenticated accounts, clear the signed-out alert state and dismiss any lingering notification
            val authenticatedAccounts = currentAccounts.filter {
                it.usage?.status == AuthStatus.AUTHENTICATED &&
                        it.account.authStatus == AuthStatus.AUTHENTICATED
            }
            for (item in authenticatedAccounts) {
                val wasNotified = prefsRepo.isSignedOutAlertNotified(item.account.id)
                if (wasNotified) {
                    prefsRepo.setSignedOutAlertNotified(item.account.id, false)
                    signedOutNotificationManager.clearNotification(item.account.id)
                }
            }
        }

        // Check for low quota alerts against multi-select thresholds (notify only ONCE per milestone)
        if (preferences.quotaAlertsEnabled) {
            val thresholds = preferences.quotaAlertThresholds

            for (item in currentAccounts) {
                val usage = item.usage ?: continue
                if (usage.status != AuthStatus.AUTHENTICATED) continue

                val isApiAccount = item.account.planType.isApiKeyPlan || usage.isApiKeyQuotaUsage(item.account.planType)

                // 1. Primary/Weekly evaluation (always evaluated for all accounts)
                val primaryRemaining = usage.remainingPercent
                if (primaryRemaining != null && preferences.includeWeeklyQuotaAlerts) {
                    evaluateQuotaAlertForWindow(
                        account = item.account,
                        usage = usage,
                        remainingPercent = primaryRemaining,
                        window = QuotaWindow.WEEKLY,
                        thresholds = thresholds,
                        prefsRepo = prefsRepo,
                        notificationManager = quotaAlertNotificationManager
                    )
                }

                // 2. 5-hour evaluation (only for non-API subscriber accounts if includeFiveHourQuotaAlerts is enabled)
                if (preferences.includeFiveHourQuotaAlerts && !isApiAccount) {
                    val fiveHourRemaining = usage.fiveHourRemainingPercent
                    if (fiveHourRemaining != null) {
                        evaluateQuotaAlertForWindow(
                            account = item.account,
                            usage = usage,
                            remainingPercent = fiveHourRemaining,
                            window = QuotaWindow.FIVE_HOUR,
                            thresholds = thresholds,
                            prefsRepo = prefsRepo,
                            notificationManager = quotaAlertNotificationManager
                        )
                    }
                }
            }
        }

        // Update home-screen widgets
        WidgetUpdateHelper.updateAllWidgets(applicationContext)

        return if (refreshResult.isSuccess) {
            Result.success()
        } else {
            Result.retry()
        }
    }

    private suspend fun evaluateQuotaAlertForWindow(
        account: CodexAccount,
        usage: CodexUsage,
        remainingPercent: Double,
        window: QuotaWindow,
        thresholds: Set<Int>,
        prefsRepo: UserPreferencesRepository,
        notificationManager: QuotaAlertNotificationManager
    ) {
        val lastNotifiedThreshold = prefsRepo.getLastNotifiedQuotaThreshold(account.id, window)
        val decision = evaluateQuotaAlertDecision(
            remainingPercent = remainingPercent,
            thresholds = thresholds,
            lastNotifiedThreshold = lastNotifiedThreshold
        )

        if (decision.shouldClearThreshold) {
            prefsRepo.setLastNotifiedQuotaThreshold(account.id, null, window)
        } else if (decision.shouldNotify && decision.milestone != null) {
            notificationManager.showLowQuotaAlert(account, usage, decision.milestone, window)
            prefsRepo.setLastNotifiedQuotaThreshold(account.id, decision.milestone, window)
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "periodic_codex_quota_refresh"
    }
}
