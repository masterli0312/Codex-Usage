package com.codex.quota.ui.util

import com.codex.quota.domain.model.CodexUsage
import com.codex.quota.domain.model.PlanType
import com.codex.quota.domain.model.QuotaWindow
import com.codex.quota.domain.model.isApiKeyPlan
import kotlin.math.roundToInt

data class SubscriberQuotaWindowUi(
    val label: String,
    val remainingPercent: Double?,
    val usedPercent: Double?,
    val resetAtEpochMs: Long?,
    val window: QuotaWindow? = null
)

fun CodexUsage?.isApiKeyQuotaUsage(planType: PlanType? = null): Boolean {
    if (planType?.isApiKeyPlan == true) return true
    return this?.rateLimitInfo?.limitTokens != null || this?.rateLimitInfo?.limitRequests != null
}

fun CodexUsage?.subscriberQuotaWindows(planType: PlanType? = null): List<SubscriberQuotaWindowUi> {
    if (this == null || isApiKeyQuotaUsage(planType)) return emptyList()

    val windows = mutableListOf<SubscriberQuotaWindowUi>()

    // Weekly window first (primary)
    if (remainingPercent != null || usedPercent != null || resetAtEpochMs != null) {
        windows += SubscriberQuotaWindowUi(
            label = "Weekly",
            remainingPercent = remainingPercent,
            usedPercent = usedPercent,
            resetAtEpochMs = resetAtEpochMs,
            window = QuotaWindow.WEEKLY
        )
    }

    // 5-hour window second (secondary)
    if (fiveHourRemainingPercent != null || fiveHourUsedPercent != null || fiveHourResetAtEpochMs != null) {
        windows += SubscriberQuotaWindowUi(
            label = "5-hour",
            remainingPercent = fiveHourRemainingPercent,
            usedPercent = fiveHourUsedPercent,
            resetAtEpochMs = fiveHourResetAtEpochMs,
            window = QuotaWindow.FIVE_HOUR
        )
    }

    return windows
}

fun CodexUsage?.primarySubscriberQuotaWindow(planType: PlanType? = null): SubscriberQuotaWindowUi? {
    val windows = subscriberQuotaWindows(planType)
    return windows.firstOrNull { it.window == QuotaWindow.WEEKLY || it.label == "Weekly" } ?: windows.firstOrNull()
}

fun formatQuotaPercent(percent: Double?): String {
    return percent?.roundToInt()?.let { "$it%" } ?: "--%"
}

fun formatQuotaSummary(window: SubscriberQuotaWindowUi): String {
    return "${formatQuotaPercent(window.remainingPercent)} left • ${formatQuotaPercent(window.usedPercent)} used"
}

fun formatResetCountdown(epochMs: Long?, now: Long = System.currentTimeMillis()): String {
    if (epochMs == null) return "Active"

    val remainingMs = epochMs - now
    if (remainingMs <= 0L) return "Now"

    val totalMinutes = ((remainingMs + 59_999L) / 60_000L).coerceAtLeast(1L)
    val days = totalMinutes / (24L * 60L)
    val hours = (totalMinutes % (24L * 60L)) / 60L
    val minutes = totalMinutes % 60L

    return when {
        days > 0L && hours > 0L -> "${days}d ${hours}h"
        days > 0L -> "${days}d"
        hours > 0L && minutes > 0L -> "${hours}h ${minutes}m"
        hours > 0L -> "${hours}h"
        else -> "${minutes}m"
    }
}
