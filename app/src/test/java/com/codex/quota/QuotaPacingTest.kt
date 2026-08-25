package com.codex.quota

import com.codex.quota.domain.model.AuthStatus
import com.codex.quota.domain.model.CodexUsage
import com.codex.quota.domain.model.PlanType
import com.codex.quota.domain.model.QuotaPacingStatus
import com.codex.quota.domain.model.RateLimitInfo
import com.codex.quota.domain.model.SEVEN_DAYS_MS
import com.codex.quota.domain.model.calculateWeeklyQuotaPacing
import com.codex.quota.domain.model.weeklyQuotaPacing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuotaPacingTest {

    private val now = 1_000_000_000L

    @Test
    fun calculateWeeklyQuotaPacing_aheadOfPace_returnsAheadStatusAndSurplusCopy() {
        // Reset in 3.5 days (50% ideal remaining), actual remaining is 70% (delta = +20%)
        val resetAt = now + (SEVEN_DAYS_MS / 2)
        val pacing = calculateWeeklyQuotaPacing(
            remainingPercent = 70.0,
            resetAtEpochMs = resetAt,
            isApiKeyUsage = false,
            now = now
        )

        assertNotNull(pacing)
        assertEquals(QuotaPacingStatus.AHEAD, pacing?.status)
        assertEquals(50.0, pacing?.idealRemainingPercent ?: 0.0, 0.001)
        assertEquals(70.0, pacing?.actualRemainingPercent ?: 0.0, 0.001)
        assertEquals(20.0, pacing?.deltaPercent ?: 0.0, 0.001)
        assertEquals("Ahead of ideal pace", pacing?.statusLabel)
        assertEquals("20 points more quota remaining than ideal", pacing?.description)
    }

    @Test
    fun calculateWeeklyQuotaPacing_behindPace_returnsBehindStatusAndDeficitCopy() {
        // Reset in 3.5 days (50% ideal remaining), actual remaining is 30% (delta = -20%)
        val resetAt = now + (SEVEN_DAYS_MS / 2)
        val pacing = calculateWeeklyQuotaPacing(
            remainingPercent = 30.0,
            resetAtEpochMs = resetAt,
            isApiKeyUsage = false,
            now = now
        )

        assertNotNull(pacing)
        assertEquals(QuotaPacingStatus.BEHIND, pacing?.status)
        assertEquals(50.0, pacing?.idealRemainingPercent ?: 0.0, 0.001)
        assertEquals(30.0, pacing?.actualRemainingPercent ?: 0.0, 0.001)
        assertEquals(-20.0, pacing?.deltaPercent ?: 0.0, 0.001)
        assertEquals("Behind ideal pace", pacing?.statusLabel)
        assertEquals("20 points less quota remaining than ideal", pacing?.description)
    }

    @Test
    fun calculateWeeklyQuotaPacing_withinFivePercentTolerance_returnsOnPace() {
        val resetAt = now + (SEVEN_DAYS_MS / 2) // ideal = 50.0%

        // Exact match (delta = 0%)
        val onPaceExact = calculateWeeklyQuotaPacing(
            remainingPercent = 50.0,
            resetAtEpochMs = resetAt,
            isApiKeyUsage = false,
            now = now
        )
        assertEquals(QuotaPacingStatus.ON_PACE, onPaceExact?.status)
        assertEquals("On ideal pace", onPaceExact?.statusLabel)
        assertEquals("Matching expected weekly pace", onPaceExact?.description)

        // +5.0% boundary (delta = +5.0%) -> ON_PACE
        val onPaceUpper = calculateWeeklyQuotaPacing(
            remainingPercent = 55.0,
            resetAtEpochMs = resetAt,
            isApiKeyUsage = false,
            now = now
        )
        assertEquals(QuotaPacingStatus.ON_PACE, onPaceUpper?.status)

        // -5.0% boundary (delta = -5.0%) -> ON_PACE
        val onPaceLower = calculateWeeklyQuotaPacing(
            remainingPercent = 45.0,
            resetAtEpochMs = resetAt,
            isApiKeyUsage = false,
            now = now
        )
        assertEquals(QuotaPacingStatus.ON_PACE, onPaceLower?.status)

        // +5.01% -> AHEAD
        val aheadEdge = calculateWeeklyQuotaPacing(
            remainingPercent = 55.01,
            resetAtEpochMs = resetAt,
            isApiKeyUsage = false,
            now = now
        )
        assertEquals(QuotaPacingStatus.AHEAD, aheadEdge?.status)

        // -5.01% -> BEHIND
        val behindEdge = calculateWeeklyQuotaPacing(
            remainingPercent = 44.99,
            resetAtEpochMs = resetAt,
            isApiKeyUsage = false,
            now = now
        )
        assertEquals(QuotaPacingStatus.BEHIND, behindEdge?.status)
    }

    @Test
    fun calculateWeeklyQuotaPacing_pastOrStaleReset_returnsNull() {
        // Reset in the past
        assertNull(
            calculateWeeklyQuotaPacing(
                remainingPercent = 50.0,
                resetAtEpochMs = now - 1000L,
                isApiKeyUsage = false,
                now = now
            )
        )

        // Reset equal to now
        assertNull(
            calculateWeeklyQuotaPacing(
                remainingPercent = 50.0,
                resetAtEpochMs = now,
                isApiKeyUsage = false,
                now = now
            )
        )
    }

    @Test
    fun calculateWeeklyQuotaPacing_nullOrNonFiniteData_returnsNull() {
        val validReset = now + 100_000L

        // Null remainingPercent
        assertNull(
            calculateWeeklyQuotaPacing(
                remainingPercent = null,
                resetAtEpochMs = validReset,
                isApiKeyUsage = false,
                now = now
            )
        )

        // NaN remainingPercent
        assertNull(
            calculateWeeklyQuotaPacing(
                remainingPercent = Double.NaN,
                resetAtEpochMs = validReset,
                isApiKeyUsage = false,
                now = now
            )
        )

        // Infinite remainingPercent
        assertNull(
            calculateWeeklyQuotaPacing(
                remainingPercent = Double.POSITIVE_INFINITY,
                resetAtEpochMs = validReset,
                isApiKeyUsage = false,
                now = now
            )
        )

        // Null resetAtEpochMs
        assertNull(
            calculateWeeklyQuotaPacing(
                remainingPercent = 50.0,
                resetAtEpochMs = null,
                isApiKeyUsage = false,
                now = now
            )
        )
    }

    @Test
    fun calculateWeeklyQuotaPacing_apiKeyUsage_returnsNull() {
        val validReset = now + 100_000L
        assertNull(
            calculateWeeklyQuotaPacing(
                remainingPercent = 50.0,
                resetAtEpochMs = validReset,
                isApiKeyUsage = true,
                now = now
            )
        )
    }

    @Test
    fun calculateWeeklyQuotaPacing_clampsSafely() {
        val resetAt = now + (SEVEN_DAYS_MS / 2) // ideal = 50%

        // Actual remaining below 0 is clamped to 0.0 -> delta = -50.0
        val clampedNegative = calculateWeeklyQuotaPacing(
            remainingPercent = -10.0,
            resetAtEpochMs = resetAt,
            isApiKeyUsage = false,
            now = now
        )
        assertEquals(0.0, clampedNegative?.actualRemainingPercent ?: -1.0, 0.001)
        assertEquals(-50.0, clampedNegative?.deltaPercent ?: 0.0, 0.001)

        // Actual remaining above 100 is clamped to 100.0 -> delta = +50.0
        val clampedOver = calculateWeeklyQuotaPacing(
            remainingPercent = 120.0,
            resetAtEpochMs = resetAt,
            isApiKeyUsage = false,
            now = now
        )
        assertEquals(100.0, clampedOver?.actualRemainingPercent ?: -1.0, 0.001)
        assertEquals(50.0, clampedOver?.deltaPercent ?: 0.0, 0.001)

        // Reset > 7 days is clamped to 7 days (100% ideal)
        val resetFar = now + (SEVEN_DAYS_MS * 2)
        val clampedTime = calculateWeeklyQuotaPacing(
            remainingPercent = 100.0,
            resetAtEpochMs = resetFar,
            isApiKeyUsage = false,
            now = now
        )
        assertEquals(100.0, clampedTime?.idealRemainingPercent ?: 0.0, 0.001)
        assertEquals(QuotaPacingStatus.ON_PACE, clampedTime?.status)
    }

    @Test
    fun calculateWeeklyQuotaPacing_copyPerspectiveSafe_noDailyUsageClaims() {
        val resetAt = now + (SEVEN_DAYS_MS / 2)
        val ahead = calculateWeeklyQuotaPacing(80.0, resetAt, false, now)
        val behind = calculateWeeklyQuotaPacing(20.0, resetAt, false, now)
        val onPace = calculateWeeklyQuotaPacing(50.0, resetAt, false, now)

        listOf(ahead?.description, behind?.description, onPace?.description).forEach { desc ->
            assertNotNull(desc)
            val lower = desc!!.lowercase()
            assertFalse(lower.contains("daily"))
            assertFalse(lower.contains("per day"))
            assertFalse(lower.contains("burned today"))
            assertTrue(lower.contains("quota") || lower.contains("pace"))
        }
    }

    @Test
    fun codexUsageExtension_weeklyQuotaPacing_worksForSubscriberAndRejectsApiKey() {
        val subscriberUsage = CodexUsage(
            accountId = "sub-1",
            remainingPercent = 80.0,
            usedPercent = 20.0,
            usedTokens = null,
            totalLimitTokens = null,
            remainingCredits = null,
            resetAtEpochMs = now + (SEVEN_DAYS_MS / 2),
            status = AuthStatus.AUTHENTICATED,
            fetchedAtEpochMs = now
        )
        val pacing = subscriberUsage.weeklyQuotaPacing(planType = PlanType.PLUS, now = now)
        assertNotNull(pacing)
        assertEquals(QuotaPacingStatus.AHEAD, pacing?.status)

        val apiKeyUsageWithHeaders = subscriberUsage.copy(
            rateLimitInfo = RateLimitInfo(
                limitTokens = 100_000,
                remainingTokens = 80_000,
                resetTokensDuration = "10s",
                limitRequests = 1000,
                remainingRequests = 800,
                resetRequestsDuration = "10s"
            )
        )
        assertNull(apiKeyUsageWithHeaders.weeklyQuotaPacing(planType = PlanType.PLUS, now = now))

        // PlanType-based API tier accounts reject pacing even without rateLimitInfo headers populated
        assertNull(subscriberUsage.weeklyQuotaPacing(planType = PlanType.API_TIER_1, now = now))
        assertNull(subscriberUsage.weeklyQuotaPacing(planType = PlanType.API_TIER_2, now = now))
        assertNull(subscriberUsage.weeklyQuotaPacing(planType = PlanType.API_TIER_5, now = now))
    }
}
