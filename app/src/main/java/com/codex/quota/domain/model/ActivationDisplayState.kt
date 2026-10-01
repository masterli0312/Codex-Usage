package com.codex.quota.domain.model

enum class ActivationDisplayState {
    OFF, BACKGROUND_OFF, LOGIN_REQUIRED, WAITING_NETWORK, WAITING_RETRY,
    WEEKLY_EXHAUSTED, ACTIVE, WAITING_ACTIVATION, CHECK_RESULT, CHECK_DATA
}

/** Presentation only: an active provider window does not prove who sent a request. */
fun activationDisplayState(usage: CodexUsage?, selected: Boolean, backgroundEnabled: Boolean,
    claimedReset: Long?, now: Long): ActivationDisplayState {
    if (!selected) return ActivationDisplayState.OFF
    if (!backgroundEnabled) return ActivationDisplayState.BACKGROUND_OFF
    if (usage?.status == AuthStatus.AUTHENTICATION_REQUIRED) return ActivationDisplayState.LOGIN_REQUIRED
    if (usage?.status == AuthStatus.OFFLINE) return ActivationDisplayState.WAITING_NETWORK
    if (usage?.status == AuthStatus.TEMPORARY_ERROR) return ActivationDisplayState.WAITING_RETRY
    if (usage?.status != AuthStatus.AUTHENTICATED || now - usage.fetchedAtEpochMs > CodexUsage.STALE_THRESHOLD_MS)
        return ActivationDisplayState.CHECK_DATA
    if (usage.isWeeklyQuotaExhausted) return ActivationDisplayState.WEEKLY_EXHAUSTED
    val reset = usage.fiveHourResetAtEpochMs?.takeIf { it > 0 } ?: return ActivationDisplayState.CHECK_DATA
    if (reset > now) return ActivationDisplayState.ACTIVE
    return if ((claimedReset ?: 0) >= reset) ActivationDisplayState.CHECK_RESULT else ActivationDisplayState.WAITING_ACTIVATION
}
