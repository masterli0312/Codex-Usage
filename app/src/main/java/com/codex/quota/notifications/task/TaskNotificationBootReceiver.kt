package com.codex.quota.notifications.task

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TaskNotificationBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            TaskCompletionService.startIfEnabled(context)
        }
    }
}
