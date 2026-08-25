package com.codex.quota.data.remote

import com.codex.quota.domain.model.AuthStatus
import com.codex.quota.domain.model.CodexAccount
import com.codex.quota.domain.model.CodexUsage
import com.codex.quota.domain.model.PlanType
import com.codex.quota.domain.model.RateLimitInfo

class MockOpenAiDataSource : CodexAccountDataSource {

    override suspend fun fetchUsage(
        account: CodexAccount,
        apiKey: String
    ): Result<CodexUsage> {
        val now = System.currentTimeMillis()

        val usage = when {
            apiKey.contains("signed_out", ignoreCase = true) || account.nickname.contains("Signed Out", ignoreCase = true) -> {
                CodexUsage(
                    accountId = account.id,
                    remainingPercent = null,
                    usedPercent = null,
                    usedTokens = null,
                    totalLimitTokens = null,
                    remainingCredits = null,
                    resetAtEpochMs = null,
                    status = AuthStatus.AUTHENTICATION_REQUIRED,
                    fetchedAtEpochMs = now,
                    errorMessage = "Session token has expired or was revoked by OpenAI."
                )
            }

            apiKey.contains("rate_limit", ignoreCase = true) || account.nickname.contains("Rate Limit", ignoreCase = true) -> {
                CodexUsage(
                    accountId = account.id,
                    remainingPercent = 0.0,
                    usedPercent = 100.0,
                    usedTokens = 100_000L,
                    totalLimitTokens = 100_000L,
                    remainingCredits = 0.0,
                    resetAtEpochMs = now + (12 * 60 * 1000L), // 12 mins
                    status = AuthStatus.TEMPORARY_ERROR,
                    fetchedAtEpochMs = now,
                    rateLimitInfo = RateLimitInfo(
                        limitRequests = 500,
                        remainingRequests = 0,
                        resetRequestsDuration = "12m",
                        limitTokens = 100_000,
                        remainingTokens = 0,
                        resetTokensDuration = "12m"
                    ),
                    errorMessage = "Rate limit reached (429). Reset in 12 minutes."
                )
            }

            account.planType == PlanType.TEAM -> {
                subscriberUsage(
                    accountId = account.id,
                    now = now,
                    weeklyRemainingPercent = 31.0,
                    fiveHourRemainingPercent = 64.0,
                    weeklyResetDelayMs = 3L * 24L * 60L * 60L * 1000L,
                    fiveHourResetDelayMs = 95L * 60L * 1000L,
                    remainingCredits = 45.50
                )
            }

            account.planType == PlanType.ENTERPRISE || account.planType == PlanType.API_TIER_5 -> {
                CodexUsage(
                    accountId = account.id,
                    remainingPercent = 94.0,
                    usedPercent = 6.0,
                    usedTokens = 60_000L,
                    totalLimitTokens = 1_000_000L,
                    remainingCredits = 1250.00,
                    resetAtEpochMs = now + (28 * 60 * 60 * 1000L),
                    status = AuthStatus.AUTHENTICATED,
                    fetchedAtEpochMs = now,
                    rateLimitInfo = RateLimitInfo(
                        limitRequests = 10000,
                        remainingRequests = 9400,
                        resetRequestsDuration = "28h",
                        limitTokens = 1_000_000,
                        remainingTokens = 940_000,
                        resetTokensDuration = "28h"
                    )
                )
            }

            else -> {
                subscriberUsage(
                    accountId = account.id,
                    now = now,
                    weeklyRemainingPercent = 78.0,
                    fiveHourRemainingPercent = 43.0,
                    weeklyResetDelayMs = 5L * 24L * 60L * 60L * 1000L + 6L * 60L * 60L * 1000L,
                    fiveHourResetDelayMs = 2L * 60L * 60L * 1000L + 15L * 60L * 1000L,
                    remainingCredits = null
                )
            }
        }

        return Result.success(usage)
    }

    private fun subscriberUsage(
        accountId: String,
        now: Long,
        weeklyRemainingPercent: Double,
        fiveHourRemainingPercent: Double,
        weeklyResetDelayMs: Long,
        fiveHourResetDelayMs: Long,
        remainingCredits: Double?
    ): CodexUsage {
        return CodexUsage(
            accountId = accountId,
            remainingPercent = weeklyRemainingPercent,
            usedPercent = 100.0 - weeklyRemainingPercent,
            usedTokens = null,
            totalLimitTokens = null,
            remainingCredits = remainingCredits,
            resetAtEpochMs = now + weeklyResetDelayMs,
            status = AuthStatus.AUTHENTICATED,
            fetchedAtEpochMs = now,
            rateLimitInfo = RateLimitInfo(
                limitRequests = null,
                remainingRequests = null,
                resetRequestsDuration = formatResetDuration(weeklyResetDelayMs),
                limitTokens = null,
                remainingTokens = null,
                resetTokensDuration = null
            ),
            fiveHourRemainingPercent = fiveHourRemainingPercent,
            fiveHourUsedPercent = 100.0 - fiveHourRemainingPercent,
            fiveHourResetAtEpochMs = now + fiveHourResetDelayMs
        )
    }

    private fun formatResetDuration(delayMs: Long): String {
        val totalMinutes = delayMs / 60_000L
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
}
