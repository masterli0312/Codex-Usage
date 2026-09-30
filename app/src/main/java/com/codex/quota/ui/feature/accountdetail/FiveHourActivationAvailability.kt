package com.codex.quota.ui.feature.accountdetail

import com.codex.quota.domain.model.AuthStatus
import com.codex.quota.domain.model.CodexUsage

/** An upcoming server reset means the current five-hour window has already started. */
internal fun canActivateFiveHourWindow(usage: CodexUsage?, now: Long): Boolean {
    if (usage?.status != AuthStatus.AUTHENTICATED) return false
    if (usage.isWeeklyQuotaExhausted) return false
    val resetAt = usage.fiveHourResetAtEpochMs ?: return false
    return resetAt > 0L && resetAt <= now
}
