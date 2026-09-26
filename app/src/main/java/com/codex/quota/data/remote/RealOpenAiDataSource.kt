package com.codex.quota.data.remote

import com.codex.quota.auth.JwtTokenParser
import com.codex.quota.data.remote.dto.ChatGptWindowDto
import com.codex.quota.domain.model.AuthStatus
import com.codex.quota.domain.model.CodexAccount
import com.codex.quota.domain.model.CodexUsage
import com.codex.quota.domain.model.RateLimitInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.Instant

class RealOpenAiDataSource(
    private val api: OpenAiUsageService = OpenAiUsageApi()
) : CodexAccountDataSource {

    override suspend fun fetchUsage(
        account: CodexAccount,
        apiKey: String
    ): Result<CodexUsage> {
        val now = System.currentTimeMillis()

        // Check if token is a ChatGPT Subscriber OAuth/Session JWT token
        val decoded = JwtTokenParser.parseToken(apiKey)
        if (decoded != null) {
            val isExpired = decoded.expiresAtEpochMs != null && decoded.expiresAtEpochMs < now
            if (isExpired) {
                val usage = CodexUsage(
                    accountId = account.id,
                    remainingPercent = 0.0,
                    usedPercent = 100.0,
                    usedTokens = null,
                    totalLimitTokens = null,
                    remainingCredits = null,
                    resetAtEpochMs = decoded.expiresAtEpochMs,
                    status = AuthStatus.AUTHENTICATION_REQUIRED,
                    fetchedAtEpochMs = now,
                    rateLimitInfo = null,
                    errorMessage = "ChatGPT session token has expired. Please re-authenticate.",
                    subscriptionRenewalEpochMs = decoded.subscriptionExpiresAtEpochMs,
                    subscriptionStartedAtEpochMs = decoded.subscriptionStartedAtEpochMs,
                    billingPeriod = "Monthly"
                )
                return Result.success(usage)
            }

            // Fetch live ChatGPT subscriber usage from chatgpt.com/backend-api/wham/usage
            val chatgptAccountId = decoded.chatgptAccountId ?: account.organizationId
            val (whamResponse, checkResponse, resetCreditsResponse) = coroutineScope {
                val whamRequest = async {
                    api.fetchChatGptSubscriberUsage(apiKey, chatgptAccountId)
                }
                val accountCheckRequest = async {
                    api.fetchChatGptAccountCheck(apiKey, chatgptAccountId)
                }
                val resetCreditsRequest = async {
                    try {
                        api.fetchChatGptResetCredits(apiKey, chatgptAccountId)
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (_: Exception) {
                        null
                    }
                }
                Triple(
                    whamRequest.await(),
                    accountCheckRequest.await(),
                    resetCreditsRequest.await()
                )
            }

            // Supplement usage with account and subscription entitlement details.
            val checkData = if (checkResponse is ApiResponse.Success) checkResponse.data else null

            val subRenewalEpochMs = checkData?.subscriptionRenewsEpochMs ?: decoded.subscriptionExpiresAtEpochMs
            val accountCreatedEpochMs = checkData?.accountCreatedEpochMs
            val billingPeriod = checkData?.billingPeriod ?: "Monthly"
            val willAutoRenew = checkData?.willRenew
            val hasActiveSubscription = checkData?.hasActiveSubscription

            when (whamResponse) {
                is ApiResponse.Success -> {
                    val whamDto = whamResponse.data
                    val rateLimit = whamDto.rateLimit
                    val classifiedWindows = classifySubscriberWindows(
                        primaryWindow = rateLimit?.primaryWindow,
                        secondaryWindow = rateLimit?.secondaryWindow
                    )
                    val weeklyWindow = classifiedWindows.weekly
                    val fiveHourWindow = classifiedWindows.fiveHour

                    val usedPercent = weeklyWindow?.usedPercent
                    val remainingPercent = usedPercent?.let { (100.0 - it).coerceIn(0.0, 100.0) }
                    val resetAtEpochMs = weeklyWindow?.resetAt?.let { it * 1000L }

                    val fiveHourUsedPercent = fiveHourWindow?.usedPercent
                    val fiveHourRemainingPercent = fiveHourUsedPercent?.let { (100.0 - it).coerceIn(0.0, 100.0) }
                    val fiveHourResetAtEpochMs = fiveHourWindow?.resetAt?.let { it * 1000L }

                    // Codex-Meter reads named extra quotas from additional_rate_limits.
                    // Only a real gpt-reserve weekly window may populate this UI value.
                    val reserveWeeklyWindow = whamDto.additionalRateLimits.orEmpty().asSequence()
                        .filter { limit ->
                            listOfNotNull(limit.limitId, limit.limitName, limit.meteredFeature).any { value ->
                                value.trim().lowercase().replace('_', '-').replace(' ', '-') == "gpt-reserve"
                            }
                        }
                        .flatMap { limit ->
                            listOfNotNull(
                                limit.rateLimit?.primaryWindow ?: limit.primaryWindow,
                                limit.rateLimit?.secondaryWindow ?: limit.secondaryWindow
                            ).asSequence()
                        }
                        .firstOrNull { it.limitWindowSeconds in 432_000L..777_600L }
                    val gptReserveRemainingPercent = reserveWeeklyWindow?.usedPercent
                        ?.let { (100.0 - it).coerceIn(0.0, 100.0) }
                    val gptReserveResetAtEpochMs = reserveWeeklyWindow?.resetAt?.let { it * 1000L }

                    val isLimitReached = rateLimit?.limitReached == true || listOfNotNull(weeklyWindow, fiveHourWindow)
                        .any { window -> window.usedPercent?.let { it >= 100.0 } == true }
                    val status = AuthStatus.AUTHENTICATED

                    val weeklyResetDurationFormatted = formatWindowDuration(weeklyWindow?.resetAfterSeconds)
                    val limitReachedResetDuration = weeklyResetDurationFormatted
                        ?: formatWindowDuration(fiveHourWindow?.resetAfterSeconds)

                    val rateLimitInfo = if (weeklyResetDurationFormatted != null) {
                        RateLimitInfo(
                            limitRequests = null,
                            remainingRequests = null,
                            resetRequestsDuration = weeklyResetDurationFormatted,
                            limitTokens = null,
                            remainingTokens = null,
                            resetTokensDuration = null
                        )
                    } else {
                        null
                    }

                    val creditsBalance = whamDto.credits?.balance?.toDoubleOrNull()
                    val bankedResetExpiresAtEpochMs = if (resetCreditsResponse is ApiResponse.Success) {
                        resetCreditsResponse.data.credits
                            .asSequence()
                            .filter { it.status == "available" }
                            .mapNotNull { credit ->
                                credit.expiresAt?.let { expiresAt ->
                                    runCatching { Instant.parse(expiresAt).toEpochMilli() }.getOrNull()
                                }
                            }
                            .minOrNull()
                    } else {
                        null
                    }

                    val usage = CodexUsage(
                        accountId = account.id,
                        remainingPercent = remainingPercent,
                        usedPercent = usedPercent,
                        usedTokens = null,
                        totalLimitTokens = null,
                        remainingCredits = creditsBalance,
                        resetAtEpochMs = resetAtEpochMs,
                        status = status,
                        fetchedAtEpochMs = now,
                        rateLimitInfo = rateLimitInfo,
                        errorMessage = if (isLimitReached && limitReachedResetDuration != null) {
                            "Usage limit reached. Resets in $limitReachedResetDuration"
                        } else if (isLimitReached) {
                            "Usage limit reached."
                        } else null,
                        subscriptionRenewalEpochMs = subRenewalEpochMs,
                        subscriptionStartedAtEpochMs = decoded.subscriptionStartedAtEpochMs,
                        billingPeriod = billingPeriod,
                        accountCreatedEpochMs = accountCreatedEpochMs,
                        willAutoRenew = willAutoRenew,
                        hasActiveSubscription = hasActiveSubscription,
                        bankedResets = whamDto.rateLimitResetCredits?.availableCount,
                        bankedResetExpiresAtEpochMs = bankedResetExpiresAtEpochMs,
                        fiveHourRemainingPercent = fiveHourRemainingPercent,
                        fiveHourUsedPercent = fiveHourUsedPercent,
                        fiveHourResetAtEpochMs = fiveHourResetAtEpochMs,
                        gptReserveRemainingPercent = gptReserveRemainingPercent,
                        gptReserveResetAtEpochMs = gptReserveResetAtEpochMs
                    )
                    return Result.success(usage)
                }

                is ApiResponse.HttpError -> {
                    if (whamResponse.httpCode == 401) {
                        val usage = CodexUsage(
                            accountId = account.id,
                            remainingPercent = 0.0,
                            usedPercent = 100.0,
                            usedTokens = null,
                            totalLimitTokens = null,
                            remainingCredits = null,
                            resetAtEpochMs = null,
                            status = AuthStatus.AUTHENTICATION_REQUIRED,
                            fetchedAtEpochMs = now,
                            rateLimitInfo = null,
                            errorMessage = "Session expired or revoked. Please re-authenticate.",
                            subscriptionRenewalEpochMs = subRenewalEpochMs,
                            subscriptionStartedAtEpochMs = decoded.subscriptionStartedAtEpochMs,
                            billingPeriod = billingPeriod,
                            accountCreatedEpochMs = accountCreatedEpochMs,
                            willAutoRenew = willAutoRenew,
                            hasActiveSubscription = hasActiveSubscription
                        )
                        return Result.success(usage)
                    }

                    // An HTTP error does not imply an unused quota.
                    val usage = CodexUsage(
                        accountId = account.id,
                        remainingPercent = null,
                        usedPercent = null,
                        usedTokens = null,
                        totalLimitTokens = null,
                        remainingCredits = null,
                        resetAtEpochMs = null,
                        status = AuthStatus.TEMPORARY_ERROR,
                        fetchedAtEpochMs = now,
                        rateLimitInfo = null,
                        errorMessage = whamResponse.message,
                        subscriptionRenewalEpochMs = subRenewalEpochMs,
                        subscriptionStartedAtEpochMs = decoded.subscriptionStartedAtEpochMs,
                        billingPeriod = billingPeriod,
                        accountCreatedEpochMs = accountCreatedEpochMs,
                        willAutoRenew = willAutoRenew,
                        hasActiveSubscription = hasActiveSubscription
                    )
                    return Result.success(usage)
                }

                is ApiResponse.NetworkError -> {
                    val usage = CodexUsage(
                        accountId = account.id,
                        remainingPercent = null,
                        usedPercent = null,
                        usedTokens = null,
                        totalLimitTokens = null,
                        remainingCredits = null,
                        resetAtEpochMs = null,
                        status = AuthStatus.OFFLINE,
                        fetchedAtEpochMs = now,
                        rateLimitInfo = null,
                        errorMessage = whamResponse.exception.message,
                        subscriptionRenewalEpochMs = subRenewalEpochMs,
                        subscriptionStartedAtEpochMs = decoded.subscriptionStartedAtEpochMs,
                        billingPeriod = billingPeriod,
                        accountCreatedEpochMs = accountCreatedEpochMs,
                        willAutoRenew = willAutoRenew,
                        hasActiveSubscription = hasActiveSubscription
                    )
                    return Result.success(usage)
                }
            }
        }

        // Platform API Key (sk-...) validation & header rate limits
        return when (val response = api.checkAuthenticationAndFetchRateLimits(apiKey, account.organizationId)) {
            is ApiResponse.Success -> {
                val limits = response.rateLimits
                val rateLimitInfo = RateLimitInfo(
                    limitRequests = limits.limitRequests?.toLong(),
                    remainingRequests = limits.remainingRequests?.toLong(),
                    resetRequestsDuration = limits.resetRequests,
                    limitTokens = limits.limitTokens,
                    remainingTokens = limits.remainingTokens,
                    resetTokensDuration = limits.resetTokens
                )

                val remainingPercent: Double? = rateLimitInfo.tokenRemainingPercent
                    ?: rateLimitInfo.requestRemainingPercent
                    ?: 100.0

                val usedPercent: Double? = remainingPercent?.let { (100.0 - it).coerceIn(0.0, 100.0) }

                val resetDelayMs = limits.resetTokensMs ?: limits.resetRequestsMs
                val resetAtEpochMs = resetDelayMs?.let { now + it }

                val usage = CodexUsage(
                    accountId = account.id,
                    remainingPercent = remainingPercent,
                    usedPercent = usedPercent,
                    usedTokens = if (limits.limitTokens != null && limits.remainingTokens != null) {
                        (limits.limitTokens - limits.remainingTokens).coerceAtLeast(0L)
                    } else null,
                    totalLimitTokens = limits.limitTokens,
                    remainingCredits = null,
                    resetAtEpochMs = resetAtEpochMs,
                    status = AuthStatus.AUTHENTICATED,
                    fetchedAtEpochMs = now,
                    rateLimitInfo = rateLimitInfo,
                    errorMessage = null
                )
                Result.success(usage)
            }

            is ApiResponse.HttpError -> {
                val status = when (response.httpCode) {
                    401, 403 -> AuthStatus.AUTHENTICATION_REQUIRED
                    429 -> AuthStatus.TEMPORARY_ERROR
                    else -> AuthStatus.TEMPORARY_ERROR
                }

                val remainingPercent = if (response.httpCode == 429) 0.0 else null

                val usage = CodexUsage(
                    accountId = account.id,
                    remainingPercent = remainingPercent,
                    usedPercent = if (remainingPercent != null) 100.0 else null,
                    usedTokens = null,
                    totalLimitTokens = null,
                    remainingCredits = null,
                    resetAtEpochMs = response.rateLimits?.resetRequestsMs?.let { now + it },
                    status = status,
                    fetchedAtEpochMs = now,
                    rateLimitInfo = response.rateLimits?.let {
                        RateLimitInfo(
                            limitRequests = it.limitRequests?.toLong(),
                            remainingRequests = it.remainingRequests?.toLong(),
                            resetRequestsDuration = it.resetRequests,
                            limitTokens = it.limitTokens,
                            remainingTokens = it.remainingTokens,
                            resetTokensDuration = it.resetTokens
                        )
                    },
                    errorMessage = response.message
                )
                Result.success(usage)
            }

            is ApiResponse.NetworkError -> {
                val usage = CodexUsage(
                    accountId = account.id,
                    remainingPercent = null,
                    usedPercent = null,
                    usedTokens = null,
                    totalLimitTokens = null,
                    remainingCredits = null,
                    resetAtEpochMs = null,
                    status = AuthStatus.OFFLINE,
                    fetchedAtEpochMs = now,
                    rateLimitInfo = null,
                    errorMessage = response.exception.message
                )
                Result.success(usage)
            }
        }
    }

    private fun classifySubscriberWindows(
        primaryWindow: ChatGptWindowDto?,
        secondaryWindow: ChatGptWindowDto?
    ): SubscriberQuotaWindows {
        if (primaryWindow == null && secondaryWindow == null) {
            return SubscriberQuotaWindows(fiveHour = null, weekly = null)
        }

        val windows = listOfNotNull(primaryWindow, secondaryWindow)
        val classifiedKinds = windows.associateWith { windowKind(it.limitWindowSeconds) }

        var fiveHourWindow = windows.firstOrNull { classifiedKinds[it] == SubscriberWindowKind.FIVE_HOUR }
        var weeklyWindow = windows.firstOrNull { classifiedKinds[it] == SubscriberWindowKind.WEEKLY }
        val unknownWindows = windows.filter { classifiedKinds[it] == SubscriberWindowKind.UNKNOWN }

        when {
            primaryWindow != null && secondaryWindow != null && fiveHourWindow == null && weeklyWindow == null -> {
                fiveHourWindow = primaryWindow
                weeklyWindow = secondaryWindow
            }
            windows.size == 1 && classifiedKinds[windows.single()] == SubscriberWindowKind.UNKNOWN -> {
                weeklyWindow = windows.single()
            }
            fiveHourWindow != null && weeklyWindow == null && unknownWindows.size == 1 -> {
                weeklyWindow = unknownWindows.single()
            }
            weeklyWindow != null && fiveHourWindow == null && unknownWindows.size == 1 && primaryWindow != null && secondaryWindow != null -> {
                fiveHourWindow = unknownWindows.single()
            }
        }

        return SubscriberQuotaWindows(
            fiveHour = fiveHourWindow,
            weekly = weeklyWindow
        )
    }

    private fun windowKind(limitWindowSeconds: Long?): SubscriberWindowKind {
        if (limitWindowSeconds == null) return SubscriberWindowKind.UNKNOWN
        return when {
            kotlin.math.abs(limitWindowSeconds - FIVE_HOUR_WINDOW_SECONDS) <= WINDOW_CLASSIFICATION_TOLERANCE_SECONDS -> SubscriberWindowKind.FIVE_HOUR
            kotlin.math.abs(limitWindowSeconds - WEEKLY_WINDOW_SECONDS) <= WINDOW_CLASSIFICATION_TOLERANCE_SECONDS -> SubscriberWindowKind.WEEKLY
            else -> SubscriberWindowKind.UNKNOWN
        }
    }

    private fun formatWindowDuration(seconds: Long?): String? {
        if (seconds == null) return null
        val days = seconds / 86_400
        val hours = (seconds % 86_400) / 3_600
        val mins = (seconds % 3_600) / 60
        return when {
            days > 0 && hours > 0 -> "${days}d ${hours}h"
            days > 0 -> "${days}d"
            hours > 0 && mins > 0 -> "${hours}h ${mins}m"
            hours > 0 -> "${hours}h"
            else -> "${mins}m"
        }
    }

    private data class SubscriberQuotaWindows(
        val fiveHour: ChatGptWindowDto?,
        val weekly: ChatGptWindowDto?
    )

    private enum class SubscriberWindowKind {
        FIVE_HOUR,
        WEEKLY,
        UNKNOWN
    }

    private companion object {
        const val FIVE_HOUR_WINDOW_SECONDS = 18_000L
        const val WEEKLY_WINDOW_SECONDS = 604_800L
        const val WINDOW_CLASSIFICATION_TOLERANCE_SECONDS = 300L
    }
}
