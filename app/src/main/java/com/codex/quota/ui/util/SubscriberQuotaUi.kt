package com.codex.quota.ui.util

import android.content.Context
import com.codex.quota.R
import com.codex.quota.domain.model.CodexUsage
import com.codex.quota.domain.model.PlanType
import com.codex.quota.domain.model.QuotaWindow
import com.codex.quota.domain.model.isApiKeyPlan
import com.codex.quota.data.remote.dto.ParsedRateLimits
import kotlin.math.roundToInt

data class SubscriberQuotaWindowUi(
    val remainingPercent: Double?,
    val usedPercent: Double?,
    val resetAtEpochMs: Long?,
    val window: QuotaWindow
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
            remainingPercent = remainingPercent,
            usedPercent = usedPercent,
            resetAtEpochMs = resetAtEpochMs,
            window = QuotaWindow.WEEKLY
        )
    }

    // 5-hour window second (secondary)
    if (fiveHourRemainingPercent != null || fiveHourUsedPercent != null || fiveHourResetAtEpochMs != null) {
        windows += SubscriberQuotaWindowUi(
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
    return windows.firstOrNull { it.window == QuotaWindow.WEEKLY } ?: windows.firstOrNull()
}

fun formatQuotaPercent(percent: Double?): String {
    return percent?.roundToInt()?.let { "$it%" } ?: "--%"
}

fun localizedWindowLabel(context: Context, window: QuotaWindow?): String = when (window) {
    QuotaWindow.WEEKLY -> context.getString(R.string.quota_weekly)
    QuotaWindow.FIVE_HOUR -> context.getString(R.string.quota_five_hour)
    QuotaWindow.GPT_RESERVE -> context.getString(R.string.gpt_reserve)
    null -> context.getString(R.string.quota_weekly)
}

fun localizedPlanName(context: Context, planType: PlanType): String = context.getString(
    when (planType) {
        PlanType.PLUS -> R.string.plan_chatgpt_plus
        PlanType.TEAM -> R.string.plan_chatgpt_team
        PlanType.ENTERPRISE -> R.string.plan_chatgpt_enterprise
        PlanType.API_TIER_1 -> R.string.plan_openai_tier_1
        PlanType.API_TIER_2 -> R.string.plan_openai_tier_2
        PlanType.API_TIER_5 -> R.string.plan_openai_tier_5
        PlanType.MOCK_DEMO -> R.string.demo_account_plan
    }
)

fun localizedShortPlanName(context: Context, planType: PlanType): String = context.getString(
    when (planType) {
        PlanType.PLUS -> R.string.plan_short_plus
        PlanType.TEAM -> R.string.plan_short_team
        PlanType.ENTERPRISE -> R.string.plan_short_enterprise
        PlanType.API_TIER_1 -> R.string.plan_short_api_tier_1
        PlanType.API_TIER_2 -> R.string.plan_short_api_tier_2
        PlanType.API_TIER_5 -> R.string.plan_short_api_tier_5
        PlanType.MOCK_DEMO -> R.string.plan_short_demo
    }
)

fun localizedResetDuration(context: Context, duration: String?): String? {
    val milliseconds = ParsedRateLimits.parseDurationToMillis(duration) ?: return duration
    val now = System.currentTimeMillis()
    return formatResetCountdown(context, now + milliseconds, now)
}

fun formatQuotaSummary(context: Context, window: SubscriberQuotaWindowUi): String {
    return context.getString(R.string.quota_summary, formatQuotaPercent(window.remainingPercent), formatQuotaPercent(window.usedPercent))
}

fun formatResetCountdown(context: Context, epochMs: Long?, now: Long = System.currentTimeMillis()): String {
    if (epochMs == null) return context.getString(R.string.status_active)

    val remainingMs = epochMs - now
    if (remainingMs <= 0L) return context.getString(R.string.reset_now)

    val totalMinutes = ((remainingMs + 59_999L) / 60_000L).coerceAtLeast(1L)
    val days = totalMinutes / (24L * 60L)
    val hours = (totalMinutes % (24L * 60L)) / 60L
    val minutes = totalMinutes % 60L

    return when {
        days > 0L && hours > 0L -> context.getString(R.string.reset_days_hours, days, hours)
        days > 0L -> context.getString(R.string.reset_days, days)
        hours > 0L && minutes > 0L -> context.getString(R.string.reset_hours_minutes, hours, minutes)
        hours > 0L -> context.getString(R.string.reset_hours, hours)
        else -> context.getString(R.string.reset_minutes, minutes)
    }
}
