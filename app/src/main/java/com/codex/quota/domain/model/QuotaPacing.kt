package com.codex.quota.domain.model

import kotlin.math.abs
import kotlin.math.roundToInt

enum class QuotaPacingStatus {
    AHEAD,
    ON_PACE,
    BEHIND
}

data class QuotaPacing(
    val status: QuotaPacingStatus,
    val deltaPercent: Double,
    val idealRemainingPercent: Double,
    val actualRemainingPercent: Double,
    val statusLabel: String,
    val description: String
)

const val SEVEN_DAYS_MS = 7L * 24L * 60L * 60L * 1000L

/**
 * Pure weekly quota pacing calculation.
 *
 * Evaluates remaining capacity against an ideal linear consumption curve across a fixed 7-day window.
 * Returns null if data is missing, non-finite, stale/past reset time, or belongs to an API-key quota.
 */
fun calculateWeeklyQuotaPacing(
    remainingPercent: Double?,
    resetAtEpochMs: Long?,
    isApiKeyUsage: Boolean = false,
    now: Long = System.currentTimeMillis()
): QuotaPacing? {
    if (isApiKeyUsage) return null
    if (remainingPercent == null || remainingPercent.isNaN() || remainingPercent.isInfinite()) return null
    if (resetAtEpochMs == null || resetAtEpochMs <= now) return null

    val actualRemaining = remainingPercent.coerceIn(0.0, 100.0)
    val timeUntilResetMs = (resetAtEpochMs - now).coerceAtLeast(0L)
    val timeFraction = (timeUntilResetMs.toDouble() / SEVEN_DAYS_MS.toDouble()).coerceIn(0.0, 1.0)
    val idealRemaining = timeFraction * 100.0
    val delta = actualRemaining - idealRemaining

    val status = when {
        delta > 5.0 -> QuotaPacingStatus.AHEAD
        delta < -5.0 -> QuotaPacingStatus.BEHIND
        else -> QuotaPacingStatus.ON_PACE
    }

    val deltaInt = abs(delta).roundToInt()
    val statusLabel = when (status) {
        QuotaPacingStatus.AHEAD -> "Ahead of ideal pace"
        QuotaPacingStatus.ON_PACE -> "On ideal pace"
        QuotaPacingStatus.BEHIND -> "Behind ideal pace"
    }

    val description = when (status) {
        QuotaPacingStatus.AHEAD -> "$deltaInt points more quota remaining than ideal"
        QuotaPacingStatus.BEHIND -> "$deltaInt points less quota remaining than ideal"
        QuotaPacingStatus.ON_PACE -> "Matching expected weekly pace"
    }

    return QuotaPacing(
        status = status,
        deltaPercent = delta,
        idealRemainingPercent = idealRemaining,
        actualRemainingPercent = actualRemaining,
        statusLabel = statusLabel,
        description = description
    )
}

/**
 * Convenience extension on [CodexUsage] to compute weekly quota pacing.
 */
fun CodexUsage?.weeklyQuotaPacing(
    planType: PlanType? = null,
    now: Long = System.currentTimeMillis()
): QuotaPacing? {
    if (this == null) return null
    val isApiKey = planType?.isApiKeyPlan == true ||
            this.rateLimitInfo?.limitTokens != null ||
            this.rateLimitInfo?.limitRequests != null
    return calculateWeeklyQuotaPacing(
        remainingPercent = this.remainingPercent,
        resetAtEpochMs = this.resetAtEpochMs,
        isApiKeyUsage = isApiKey,
        now = now
    )
}
