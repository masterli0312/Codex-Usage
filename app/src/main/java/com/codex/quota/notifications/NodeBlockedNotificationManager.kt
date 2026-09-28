package com.codex.quota.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.codex.quota.R
import com.codex.quota.ui.MainActivity

class NodeBlockedNotificationManager(private val context: Context) {
    private val localizedContext = ContextCompat.getContextForLanguage(context)
    private val manager = NotificationManagerCompat.from(context)

    fun show() {
        if (!manager.areNotificationsEnabled()) return
        val channel = NotificationChannel(CHANNEL_ID, localizedContext.getString(R.string.node_blocked_title),
            NotificationManager.IMPORTANCE_DEFAULT)
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(context, NOTIFICATION_ID, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(localizedContext.getString(R.string.node_blocked_title))
            .setContentText(localizedContext.getString(R.string.node_blocked_notification))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Notification permission may be revoked while the worker is running.
        }
    }

    fun clear() = manager.cancel(NOTIFICATION_ID)

    private companion object {
        const val CHANNEL_ID = "channel_codex_node_blocked"
        const val NOTIFICATION_ID = 4103
    }
}
