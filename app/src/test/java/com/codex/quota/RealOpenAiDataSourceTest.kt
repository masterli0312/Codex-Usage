package com.codex.quota

import com.codex.quota.auth.DecodedTokenInfo
import com.codex.quota.auth.JwtTokenParser
import com.codex.quota.data.remote.ApiResponse
import com.codex.quota.data.remote.OpenAiUsageService
import com.codex.quota.data.remote.RealOpenAiDataSource
import com.codex.quota.data.remote.dto.ChatGptAccountCheckData
import com.codex.quota.data.remote.dto.ChatGptAdditionalRateLimitDto
import com.codex.quota.data.remote.dto.ChatGptRateLimitDto
import com.codex.quota.data.remote.dto.ChatGptRateLimitResetCreditsDto
import com.codex.quota.data.remote.dto.ChatGptResetCreditDto
import com.codex.quota.data.remote.dto.ChatGptResetCreditsDto
import com.codex.quota.data.remote.dto.ChatGptWhamUsageDto
import com.codex.quota.data.remote.dto.ChatGptWindowDto
import com.codex.quota.data.remote.dto.OpenAiModelsResponseDto
import com.codex.quota.data.remote.dto.ParsedRateLimits
import com.codex.quota.domain.model.AuthStatus
import com.codex.quota.domain.model.CodexAccount
import com.codex.quota.domain.model.PlanType
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class RealOpenAiDataSourceTest {
    private val ignoreUnknownKeysJson = Json { ignoreUnknownKeys = true }

    @Test
    fun subscriberRequests_runConcurrentlyAndPreserveApiMetadataOnUsageFailure() = runTest {
        val jwtRenewal = 9_000_000_000_000L
        val apiRenewal = 8_000_000_000_000L
        mockkObject(JwtTokenParser)
        every { JwtTokenParser.parseToken(any()) } returns DecodedTokenInfo(
            email = "person@example.com",
            userId = "user-1",
            organizationId = null,
            chatgptAccountId = "chatgpt-account-1",
            planType = PlanType.PLUS,
            expiresAtEpochMs = Long.MAX_VALUE,
            subscriptionExpiresAtEpochMs = jwtRenewal,
            subscriptionStartedAtEpochMs = 1_000L
        )

        try {
            val api = GatedSubscriberApi(apiRenewal)
            val fetch = async {
                RealOpenAiDataSource(api).fetchUsage(account(), "subscriber.jwt.token")
            }
            runCurrent()

            assertTrue(api.whamStarted.isCompleted)
            assertTrue(api.accountCheckStarted.isCompleted)
            assertTrue(api.resetCreditsStarted.isCompleted)
            api.releaseResponses.complete(Unit)

            val usage = fetch.await().getOrThrow()
            assertEquals(AuthStatus.TEMPORARY_ERROR, usage.status)
            assertEquals(null, usage.remainingPercent)
            assertEquals(apiRenewal, usage.subscriptionRenewalEpochMs)
            assertEquals(1_000L, usage.subscriptionStartedAtEpochMs)
            assertEquals("Annual", usage.billingPeriod)
            assertEquals(2_000L, usage.accountCreatedEpochMs)
            assertFalse(usage.willAutoRenew!!)
            assertTrue(usage.hasActiveSubscription!!)
            assertEquals(null, usage.bankedResets)
        } finally {
            unmockkObject(JwtTokenParser)
        }
    }

    @Test
    fun successfulSubscriberUsage_mapsBankedCountAndEarliestAvailableExpiry() = runTest {
        mockkObject(JwtTokenParser)
        every { JwtTokenParser.parseToken(any()) } returns DecodedTokenInfo(
            email = "person@example.com",
            userId = "user-1",
            organizationId = null,
            chatgptAccountId = "chatgpt-account-1",
            planType = PlanType.PLUS,
            expiresAtEpochMs = Long.MAX_VALUE,
            subscriptionExpiresAtEpochMs = 9_000_000_000_000L,
            subscriptionStartedAtEpochMs = 1_000L
        )

        try {
            val api = GatedSubscriberApi(
                apiRenewal = 8_000_000_000_000L,
                bankedResets = 3,
                resetCredits = listOf(
                    ChatGptResetCreditDto(
                        status = "available",
                        expiresAt = "2026-09-20T22:11:08.943280Z"
                    ),
                    ChatGptResetCreditDto(
                        status = "available",
                        expiresAt = "2026-09-19T10:00:00Z"
                    ),
                    ChatGptResetCreditDto(
                        status = "used",
                        expiresAt = "2026-09-01T10:00:00Z"
                    ),
                    ChatGptResetCreditDto(status = "available", expiresAt = "not-a-date")
                )
            )
            val fetch = async {
                RealOpenAiDataSource(api).fetchUsage(account(), "subscriber.jwt.token")
            }
            runCurrent()

            assertTrue(api.whamStarted.isCompleted)
            assertTrue(api.accountCheckStarted.isCompleted)
            assertTrue(api.resetCreditsStarted.isCompleted)
            api.releaseResponses.complete(Unit)

            val usage = fetch.await().getOrThrow()
            assertEquals(3, usage.bankedResets)
            assertEquals(
                Instant.parse("2026-09-19T10:00:00Z").toEpochMilli(),
                usage.bankedResetExpiresAtEpochMs
            )
        } finally {
            unmockkObject(JwtTokenParser)
        }
    }

    @Test
    fun resetCreditDetailsFailure_keepsSuccessfulUsageAndBankedCount() = runTest {
        mockkObject(JwtTokenParser)
        every { JwtTokenParser.parseToken(any()) } returns DecodedTokenInfo(
            email = null,
            userId = "user-1",
            organizationId = null,
            chatgptAccountId = "chatgpt-account-1",
            planType = PlanType.PLUS,
            expiresAtEpochMs = Long.MAX_VALUE,
            subscriptionExpiresAtEpochMs = null,
            subscriptionStartedAtEpochMs = null
        )

        try {
            val api = GatedSubscriberApi(
                apiRenewal = 8_000_000_000_000L,
                bankedResets = 2,
                throwResetCreditsFailure = true
            )
            val fetch = async {
                RealOpenAiDataSource(api).fetchUsage(account(), "subscriber.jwt.token")
            }
            runCurrent()
            api.releaseResponses.complete(Unit)

            val result = fetch.await()
            assertTrue(result.isSuccess)
            assertEquals(2, result.getOrThrow().bankedResets)
            assertEquals(null, result.getOrThrow().bankedResetExpiresAtEpochMs)
        } finally {
            unmockkObject(JwtTokenParser)
        }
    }

    @Test
    fun subscriberUsage_classifiesDualWindowsIndependentOfOrder() = runTest {
        val usage = fetchSubscriberUsage(
            primaryWindow = quotaWindow(usedPercent = 12.0, limitWindowSeconds = 604_800L, resetAtSeconds = 2_000_000L),
            secondaryWindow = quotaWindow(usedPercent = 75.0, limitWindowSeconds = 18_000L, resetAtSeconds = 1_000_000L)
        )

        assertEquals(88.0, usage.remainingPercent!!, 0.01)
        assertEquals(12.0, usage.usedPercent!!, 0.01)
        assertEquals(75.0, usage.fiveHourUsedPercent!!, 0.01)
        assertEquals(25.0, usage.fiveHourRemainingPercent!!, 0.01)
        assertEquals(2_000_000L * 1000L, usage.resetAtEpochMs)
        assertEquals(1_000_000L * 1000L, usage.fiveHourResetAtEpochMs)
    }

    @Test
    fun subscriberUsage_defaultsUnknownDualWindowOrderToPrimaryFiveHourSecondaryWeekly() = runTest {
        val usage = fetchSubscriberUsage(
            primaryWindow = quotaWindow(usedPercent = 61.0, limitWindowSeconds = null, resetAtSeconds = 1_000_000L),
            secondaryWindow = quotaWindow(usedPercent = 14.0, limitWindowSeconds = null, resetAtSeconds = 2_000_000L)
        )

        assertEquals(39.0, usage.fiveHourRemainingPercent!!, 0.01)
        assertEquals(86.0, usage.remainingPercent!!, 0.01)
        assertEquals(1_000_000L * 1000L, usage.fiveHourResetAtEpochMs)
        assertEquals(2_000_000L * 1000L, usage.resetAtEpochMs)
    }

    @Test
    fun subscriberUsage_treatsSingleDurationlessWindowAsWeeklyLegacy() = runTest {
        val usage = fetchSubscriberUsage(
            primaryWindow = quotaWindow(usedPercent = 41.0, limitWindowSeconds = null, resetAtSeconds = 3_000_000L),
            secondaryWindow = null
        )

        assertEquals(59.0, usage.remainingPercent!!, 0.01)
        assertEquals(null, usage.fiveHourRemainingPercent)
        assertEquals(3_000_000L * 1000L, usage.resetAtEpochMs)
    }

    @Test
    fun subscriberUsage_leavesWeeklyNullWhenOnlyFiveHourWindowIsClassifiable() = runTest {
        val usage = fetchSubscriberUsage(
            primaryWindow = quotaWindow(usedPercent = 91.0, limitWindowSeconds = 18_000L, resetAtSeconds = 4_000_000L),
            secondaryWindow = null
        )

        assertEquals(null, usage.remainingPercent)
        assertEquals(9.0, usage.fiveHourRemainingPercent!!, 0.01)
        assertEquals(null, usage.resetAtEpochMs)
        assertEquals(4_000_000L * 1000L, usage.fiveHourResetAtEpochMs)
    }

    @Test
    fun subscriberUsage_limitReachedChecksBothWindows() = runTest {
        val usage = fetchSubscriberUsage(
            primaryWindow = quotaWindow(usedPercent = 10.0, limitWindowSeconds = 604_800L, resetAtSeconds = 5_000_000L),
            secondaryWindow = quotaWindow(usedPercent = 100.0, limitWindowSeconds = 18_000L, resetAtSeconds = 5_100_000L),
            limitReached = false
        )

        assertEquals(90.0, usage.remainingPercent!!, 0.01)
        assertEquals(0.0, usage.fiveHourRemainingPercent!!, 0.01)
        assertTrue(usage.errorMessage!!.contains("Usage limit reached"))
    }

    @Test
    fun whamDto_decodesBankedResetsAndIgnoresUnknownFields() {
        val dto = ignoreUnknownKeysJson.decodeFromString<ChatGptWhamUsageDto>(
            """{"rate_limit_reset_credits":{"available_count":1,"future_field":true},"unknown_root":"value"}"""
        )

        assertEquals(1, dto.rateLimitResetCredits?.availableCount)
    }

    @Test
    fun subscriberUsage_readsOnlyGptReserveWeeklyAdditionalLimit() = runTest {
        val usage = withSubscriberToken {
            val api = GatedSubscriberApi(
                apiRenewal = 8_000_000_000_000L,
                whamUsage = ChatGptWhamUsageDto(
                    additionalRateLimits = listOf(
                        ChatGptAdditionalRateLimitDto(
                            limitName = "GPT-5.3-Codex-Spark",
                            rateLimit = ChatGptRateLimitDto(secondaryWindow = quotaWindow(90.0, 604_800L, 2_000_000L))
                        ),
                        ChatGptAdditionalRateLimitDto(
                            limitName = "gpt-reserve",
                            rateLimit = ChatGptRateLimitDto(
                                primaryWindow = quotaWindow(40.0, 18_000L, 1_000_000L),
                                secondaryWindow = quotaWindow(25.0, 604_800L, 3_000_000L)
                            )
                        )
                    )
                )
            )
            api.releaseResponses.complete(Unit)
            RealOpenAiDataSource(api).fetchUsage(account(), "subscriber.jwt.token").getOrThrow()
        }

        assertEquals(75.0, usage.gptReserveRemainingPercent!!, 0.01)
        assertEquals(3_000_000_000L, usage.gptReserveResetAtEpochMs)
        assertEquals(null, usage.remainingPercent)
    }

    @Test
    fun subscriberHttpFailureDoesNotInventFullQuota() = runTest {
        val usage = withSubscriberToken {
            val api = GatedSubscriberApi(apiRenewal = 8_000_000_000_000L)
            api.releaseResponses.complete(Unit)
            RealOpenAiDataSource(api).fetchUsage(account(), "subscriber.jwt.token").getOrThrow()
        }

        assertEquals(AuthStatus.TEMPORARY_ERROR, usage.status)
        assertEquals(null, usage.remainingPercent)
        assertEquals(null, usage.gptReserveRemainingPercent)
    }

    @Test
    fun whamDto_decodesAdditionalRateLimitFromApiShape() {
        val dto = ignoreUnknownKeysJson.decodeFromString<ChatGptWhamUsageDto>(
            """{"additional_rate_limits":[{"limit_name":"gpt-reserve","metered_feature":"gpt-reserve","rate_limit":{"primary_window":{"used_percent":25,"limit_window_seconds":604800,"reset_at":3000000}}}]}"""
        )

        assertEquals("gpt-reserve", dto.additionalRateLimits!!.single().limitName)
        assertEquals(25.0, dto.additionalRateLimits!!.single().rateLimit?.primaryWindow?.usedPercent)
    }

    @Test
    fun resetCreditsDto_decodesTypedExpiryDetailsAndIgnoresUnknownFields() {
        val dto = ignoreUnknownKeysJson.decodeFromString<ChatGptResetCreditsDto>(
            """{"available_count":2,"credits":[{"status":"available","reset_type":"weekly","granted_at":"2026-08-20T10:00:00Z","expires_at":"2026-09-20T22:11:08.943280Z","title":"Banked reset","unknown_credit_field":42}],"unknown_root":true}"""
        )

        assertEquals(2, dto.availableCount)
        assertEquals("available", dto.credits.single().status)
        assertEquals("weekly", dto.credits.single().resetType)
        assertEquals("2026-08-20T10:00:00Z", dto.credits.single().grantedAt)
        assertEquals("2026-09-20T22:11:08.943280Z", dto.credits.single().expiresAt)
        assertEquals("Banked reset", dto.credits.single().title)
    }

    private suspend fun fetchSubscriberUsage(
        primaryWindow: ChatGptWindowDto?,
        secondaryWindow: ChatGptWindowDto?,
        limitReached: Boolean = false
    ) = withSubscriberToken {
        val api = GatedSubscriberApi(
            apiRenewal = 8_000_000_000_000L,
            whamUsage = ChatGptWhamUsageDto(
                rateLimit = ChatGptRateLimitDto(
                    limitReached = limitReached,
                    primaryWindow = primaryWindow,
                    secondaryWindow = secondaryWindow
                )
            )
        )
        api.releaseResponses.complete(Unit)
        val result = RealOpenAiDataSource(api).fetchUsage(account(), "subscriber.jwt.token")
        assertTrue(result.isSuccess)
        result.getOrThrow()
    }

    private fun quotaWindow(
        usedPercent: Double,
        limitWindowSeconds: Long?,
        resetAtSeconds: Long
    ) = ChatGptWindowDto(
        usedPercent = usedPercent,
        limitWindowSeconds = limitWindowSeconds,
        resetAfterSeconds = 600L,
        resetAt = resetAtSeconds
    )

    private suspend fun <T> withSubscriberToken(block: suspend () -> T): T {
        mockkObject(JwtTokenParser)
        every { JwtTokenParser.parseToken(any()) } returns DecodedTokenInfo(
            email = "person@example.com",
            userId = "user-1",
            organizationId = null,
            chatgptAccountId = "chatgpt-account-1",
            planType = PlanType.PLUS,
            expiresAtEpochMs = Long.MAX_VALUE,
            subscriptionExpiresAtEpochMs = 9_000_000_000_000L,
            subscriptionStartedAtEpochMs = 1_000L
        )

        return try {
            block()
        } finally {
            unmockkObject(JwtTokenParser)
        }
    }

    private fun account() = CodexAccount(
        id = "account-1",
        nickname = "Subscriber",
        email = null,
        planType = PlanType.PLUS,
        organizationId = null,
        colorHex = "#10B981",
        authStatus = AuthStatus.AUTHENTICATED,
        isDemoAccount = false,
        orderIndex = 0,
        createdAtEpochMs = 0L,
        lastSuccessfulSyncEpochMs = null
    )

    private class GatedSubscriberApi(
        private val apiRenewal: Long,
        private val bankedResets: Int? = null,
        private val resetCredits: List<ChatGptResetCreditDto> = emptyList(),
        private val throwResetCreditsFailure: Boolean = false,
        private val whamUsage: ChatGptWhamUsageDto? = null
    ) : OpenAiUsageService {
        val whamStarted = CompletableDeferred<Unit>()
        val accountCheckStarted = CompletableDeferred<Unit>()
        val resetCreditsStarted = CompletableDeferred<Unit>()
        val releaseResponses = CompletableDeferred<Unit>()

        override suspend fun fetchChatGptSubscriberUsage(
            accessToken: String,
            chatgptAccountId: String?
        ): ApiResponse<ChatGptWhamUsageDto> {
            whamStarted.complete(Unit)
            releaseResponses.await()
            return whamUsage?.let {
                ApiResponse.Success(
                    data = it,
                    rateLimits = emptyRateLimits(),
                    httpCode = 200
                )
            } ?: if (bankedResets != null) {
                ApiResponse.Success(
                    data = ChatGptWhamUsageDto(
                        rateLimitResetCredits = ChatGptRateLimitResetCreditsDto(
                            availableCount = bankedResets
                        )
                    ),
                    rateLimits = emptyRateLimits(),
                    httpCode = 200
                )
            } else {
                ApiResponse.HttpError(503, "temporary outage", null)
            }
        }

        override suspend fun fetchChatGptAccountCheck(
            accessToken: String,
            chatgptAccountId: String?
        ): ApiResponse<ChatGptAccountCheckData> {
            accountCheckStarted.complete(Unit)
            releaseResponses.await()
            return ApiResponse.Success(
                data = ChatGptAccountCheckData(
                    accountCreatedEpochMs = 2_000L,
                    subscriptionRenewsEpochMs = apiRenewal,
                    subscriptionExpiresEpochMs = apiRenewal,
                    billingPeriod = "Annual",
                    willRenew = false,
                    hasActiveSubscription = true,
                    planType = "plus"
                ),
                rateLimits = emptyRateLimits(),
                httpCode = 200
            )
        }

        override suspend fun fetchChatGptResetCredits(
            accessToken: String,
            chatgptAccountId: String?
        ): ApiResponse<ChatGptResetCreditsDto> {
            resetCreditsStarted.complete(Unit)
            releaseResponses.await()
            if (throwResetCreditsFailure) throw IOException("details unavailable")
            return ApiResponse.Success(
                data = ChatGptResetCreditsDto(
                    availableCount = resetCredits.size,
                    credits = resetCredits
                ),
                rateLimits = emptyRateLimits(),
                httpCode = 200
            )
        }

        override suspend fun checkAuthenticationAndFetchRateLimits(
            apiKey: String,
            organizationId: String?
        ): ApiResponse<OpenAiModelsResponseDto> = error("Not used for subscriber tokens")

        private fun emptyRateLimits() = ParsedRateLimits(
            limitRequests = null,
            remainingRequests = null,
            resetRequests = null,
            resetRequestsMs = null,
            limitTokens = null,
            remainingTokens = null,
            resetTokens = null,
            resetTokensMs = null
        )
    }
}
