package com.codex.quota.domain.usecase

data class QuotaAlertDecision(
    val shouldNotify: Boolean,
    val milestone: Int?,
    val shouldClearThreshold: Boolean
)

/**
 * Pure decision evaluator for quota threshold notifications.
 *
 * Rules:
 * 1. If [remainingPercent] is strictly greater than the maximum configured threshold,
 *    the quota has recovered; clear the deduplication threshold if one was previously recorded.
 * 2. Otherwise, find candidate thresholds where [remainingPercent] <= threshold.
 * 3. Select the most critical (minimum) candidate milestone.
 * 4. Trigger notification only if entering a new threshold cycle or reaching a strictly lower milestone.
 */
fun evaluateQuotaAlertDecision(
    remainingPercent: Double,
    thresholds: Set<Int>,
    lastNotifiedThreshold: Int?
): QuotaAlertDecision {
    if (thresholds.isEmpty()) {
        return QuotaAlertDecision(shouldNotify = false, milestone = null, shouldClearThreshold = false)
    }

    val maxThreshold = thresholds.maxOrNull() ?: 25
    if (remainingPercent > maxThreshold) {
        return QuotaAlertDecision(
            shouldNotify = false,
            milestone = null,
            shouldClearThreshold = lastNotifiedThreshold != null
        )
    }

    val candidateThresholds = thresholds.filter { remainingPercent <= it }
    val currentMilestone = candidateThresholds.minOrNull()
        ?: return QuotaAlertDecision(shouldNotify = false, milestone = null, shouldClearThreshold = false)

    val shouldNotify = lastNotifiedThreshold == null || currentMilestone < lastNotifiedThreshold
    return QuotaAlertDecision(
        shouldNotify = shouldNotify,
        milestone = currentMilestone,
        shouldClearThreshold = false
    )
}
