package com.codex.quota.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.codex.quota.R
import com.codex.quota.domain.model.CodexAccount
import com.codex.quota.domain.model.CodexUsage
import com.codex.quota.domain.model.QuotaWindow
import com.codex.quota.domain.model.isApiKeyPlan
import com.codex.quota.ui.MainActivity
import com.codex.quota.ui.util.isApiKeyQuotaUsage
import com.codex.quota.ui.util.localizedPlanName
import com.codex.quota.ui.util.localizedWindowLabel

fun buildQuotaAlertTitle(
    context: Context,
    window: QuotaWindow? = QuotaWindow.WEEKLY,
    isApiKey: Boolean = false
): String {
    if (isApiKey || window == null) {
        return context.getString(R.string.quota_notification_title)
    }
    return context.getString(
        R.string.quota_notification_window_title,
        notificationWindowName(context, window)
    )
}

fun buildQuotaAlertContentText(
    context: Context,
    nickname: String,
    remainingPercent: Int,
    window: QuotaWindow? = QuotaWindow.WEEKLY,
    isApiKey: Boolean = false
): String {
    if (isApiKey || window == null) {
        return context.getString(R.string.quota_notification_text, nickname, remainingPercent)
    }
    return context.getString(
        R.string.quota_notification_window_text,
        nickname,
        remainingPercent,
        notificationWindowName(context, window)
    )
}

fun buildQuotaAlertBigText(
    context: Context,
    nickname: String,
    planDisplayName: String,
    thresholdPercent: Int,
    remainingPercent: Int,
    window: QuotaWindow? = QuotaWindow.WEEKLY,
    isApiKey: Boolean = false
): String {
    if (isApiKey || window == null) {
        return context.getString(
            R.string.quota_notification_details,
            nickname,
            planDisplayName,
            thresholdPercent,
            remainingPercent
        )
    }
    return context.getString(
        R.string.quota_notification_window_details,
        nickname,
        planDisplayName,
        thresholdPercent,
        remainingPercent,
        notificationWindowName(context, window)
    )
}

private fun notificationWindowName(context: Context, window: QuotaWindow): String = when (window) {
    QuotaWindow.WEEKLY -> context.getString(R.string.window_weekly)
    QuotaWindow.FIVE_HOUR -> context.getString(R.string.window_five_hour)
}

class QuotaAlertNotificationManager(private val context: Context) {

    private val localizedContext = ContextCompat.getContextForLanguage(context)

    private val notificationManager = NotificationManagerCompat.from(context)

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                localizedContext.getString(R.string.channel_quota_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = localizedContext.getString(R.string.channel_quota_description)
            }
            val systemManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            systemManager.createNotificationChannel(channel)
        }
    }

    fun showLowQuotaAlert(
        account: CodexAccount,
        usage: CodexUsage,
        thresholdPercent: Int,
        window: QuotaWindow = QuotaWindow.WEEKLY
    ) {
        val isApiKey = account.planType.isApiKeyPlan || usage.isApiKeyQuotaUsage(account.planType)
        val effectiveWindow = if (isApiKey) null else window
        val remaining = when {
            isApiKey -> usage.remainingPercent?.toInt()
            window == QuotaWindow.WEEKLY -> usage.remainingPercent?.toInt()
            window == QuotaWindow.FIVE_HOUR -> usage.fiveHourRemainingPercent?.toInt() ?: usage.remainingPercent?.toInt()
            else -> usage.remainingPercent?.toInt()
        } ?: return

        val deepLinkUri = Uri.parse("codexquota://account/${account.id}")
        val intent = Intent(Intent.ACTION_VIEW, deepLinkUri, context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val requestKey = if (window == QuotaWindow.FIVE_HOUR && !isApiKey) {
            "${account.id}_quota_${window.name}"
        } else {
            "${account.id}_quota"
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestKey.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val planName = localizedPlanName(localizedContext, account.planType)
        val title = buildQuotaAlertTitle(localizedContext, effectiveWindow, isApiKey)
        val contentText = buildQuotaAlertContentText(
            localizedContext,
            account.nickname,
            remaining,
            effectiveWindow,
            isApiKey
        )
        val bigText = buildQuotaAlertBigText(
            localizedContext,
            account.nickname,
            planName,
            thresholdPercent,
            remaining,
            effectiveWindow,
            isApiKey
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            val tag = if (isApiKey || window == QuotaWindow.WEEKLY) {
                NOTIFICATION_TAG_PREFIX + account.id
            } else {
                NOTIFICATION_TAG_PREFIX + window.name.lowercase() + "_" + account.id
            }
            val notificationId = if (window == QuotaWindow.FIVE_HOUR && !isApiKey) {
                NOTIFICATION_ID_BASE + (account.id + "_" + window.name).hashCode()
            } else {
                NOTIFICATION_ID_BASE + account.id.hashCode()
            }
            notificationManager.notify(
                tag,
                notificationId,
                notification
            )
        } catch (e: SecurityException) {
            // Notifications permission not granted
        }
    }

    companion object {
        const val CHANNEL_ID = "channel_codex_quota_alerts"
        private const val NOTIFICATION_TAG_PREFIX = "quota_alert_"
        private const val NOTIFICATION_ID_BASE = 2000
    }
}
