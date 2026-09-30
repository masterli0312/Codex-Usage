package com.codex.quota.notifications.task

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.codex.quota.R
import com.codex.quota.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.collect
import java.text.DateFormat
import java.util.Date

enum class TaskConnectionState { OFF, CONNECTING, CONNECTED, RETRYING }
object TaskNotificationConnection { val state = MutableStateFlow(TaskConnectionState.OFF) }

/** Opt-in foreground connection, independent of quota refresh and all account credentials. */
class TaskCompletionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var store: TaskNotificationStore
    private var listening = false
    private val taskClient = NtfyTaskClient()
    private val localizedContext get() = ContextCompat.getContextForLanguage(this)

    override fun onCreate() {
        super.onCreate()
        store = TaskNotificationStore(this)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CONNECTION_CHANNEL, localizedContext.getString(R.string.task_connection_channel), NotificationManager.IMPORTANCE_LOW))
        manager.createNotificationChannel(NotificationChannel(EVENT_CHANNEL, localizedContext.getString(R.string.task_notification_title), NotificationManager.IMPORTANCE_DEFAULT))
        ServiceCompat.startForeground(this, CONNECTION_ID, connectionNotification(R.string.task_connecting),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!listening) {
            listening = true
            scope.launch {
                store.settings.collectLatest { settings ->
                    if (!settings.enabled || TaskNotificationProtocol.normalizeEndpoint(settings.endpoint) == null) {
                        stopSelf()
                        return@collectLatest
                    }
                    var retryDelay = 2_000L
                    while (currentCoroutineContext().isActive) {
                        setConnectionState(TaskConnectionState.CONNECTING)
                        try {
                            taskClient.events(settings.endpoint, store.since(settings.endpoint, settings.enabledAtSeconds)) {
                                setConnectionState(TaskConnectionState.CONNECTED)
                                retryDelay = 2_000L
                            }.collect { event ->
                                // Re-check the preference so disabling never leaves a queued event to notify.
                                val latest = store.read()
                                if (latest.enabled && latest.endpoint == settings.endpoint &&
                                    NotificationManagerCompat.from(this@TaskCompletionService).areNotificationsEnabled() &&
                                    store.claim(settings.endpoint, event)) showEvent(event)
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            // Never log the private subscription URL, event body, or OpenAI credentials.
                        }
                        setConnectionState(TaskConnectionState.RETRYING)
                        delay(retryDelay)
                        retryDelay = (retryDelay * 2).coerceAtMost(60_000L)
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun setConnectionState(state: TaskConnectionState) {
        TaskNotificationConnection.state.value = state
        val text = when (state) {
            TaskConnectionState.CONNECTED -> R.string.task_connected
            TaskConnectionState.RETRYING -> R.string.task_reconnecting
            else -> R.string.task_connecting
        }
        getSystemService(NotificationManager::class.java).notify(CONNECTION_ID, connectionNotification(text))
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getActivity(this, 5100,
        Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun connectionNotification(text: Int): Notification = NotificationCompat.Builder(this, CONNECTION_CHANNEL)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(localizedContext.getString(R.string.task_notification_title))
        .setContentText(localizedContext.getString(text)).setOngoing(true).setSilent(true)
        .setContentIntent(pendingIntent()).build()

    private fun showEvent(event: TaskCompletionEvent) {
        val text = when (event.status) {
            "test" -> R.string.task_test_received
            "api_error", "api_error_overloaded", "session_limit_reached" -> R.string.task_attention_error
            "permission_request", "question", "plan_ready" -> R.string.task_attention_input
            else -> R.string.task_turn_ended
        }
        val time = DateFormat.getTimeInstance(DateFormat.SHORT, localizedContext.resources.configuration.locales[0]).format(Date(event.timeEpochMs))
        val notification = NotificationCompat.Builder(this, EVENT_CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(localizedContext.getString(R.string.task_notification_title))
            .setContentText(localizedContext.getString(R.string.task_notification_body, localizedContext.getString(text), time))
            .setWhen(event.timeEpochMs).setAutoCancel(true).setContentIntent(pendingIntent()).build()
        try {
            NotificationManagerCompat.from(this).notify(event.deduplicationId, EVENT_ID, notification)
        } catch (_: SecurityException) { /* Permission may be revoked while connected. */ }
    }

    override fun onDestroy() {
        scope.cancel()
        TaskNotificationConnection.state.value = TaskConnectionState.OFF
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CONNECTION_CHANNEL = "channel_codex_task_connection"
        private const val EVENT_CHANNEL = "channel_codex_task_events"
        private const val CONNECTION_ID = 5100
        private const val EVENT_ID = 5101
        fun startIfEnabled(context: Context): Boolean {
            if (!TaskNotificationStore(context).read().enabled) return false
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
            return try {
                ContextCompat.startForegroundService(context, Intent(context, TaskCompletionService::class.java))
                true
            } catch (_: IllegalStateException) { false } catch (_: SecurityException) { false }
        }
        fun stop(context: Context) { context.stopService(Intent(context, TaskCompletionService::class.java)) }
    }
}
