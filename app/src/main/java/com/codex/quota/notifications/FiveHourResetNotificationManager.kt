package com.codex.quota.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.codex.quota.R
import com.codex.quota.domain.model.CodexAccount
import com.codex.quota.ui.MainActivity
import com.codex.quota.ui.util.localizedAccountNickname

class FiveHourResetNotificationManager(private val context: Context) {
    private val localizedContext = ContextCompat.getContextForLanguage(context)
    private val manager = NotificationManagerCompat.from(context)

    fun canNotify(): Boolean = manager.areNotificationsEnabled()

    fun showReminder(account: CodexAccount) {
        if (!canNotify()) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            localizedContext.getString(R.string.five_hour_reset_channel),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("codexquota://account/" + account.id), context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, ("five_hour_reset_" + account.id).hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(localizedContext.getString(R.string.five_hour_reset_notification_title))
            .setContentText(localizedContext.getString(
                R.string.five_hour_reset_notification_text,
                localizedAccountNickname(localizedContext, account)
            ))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        try {
            manager.notify("five_hour_reset_" + account.id, 3000, notification)
        } catch (_: SecurityException) {
            // The user may revoke notification permission while this work is running.
        }
    }

    companion object {
        private const val CHANNEL_ID = "channel_five_hour_reset_reminder"
    }
}
