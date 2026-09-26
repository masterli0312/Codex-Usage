package com.codex.quota

import android.content.Context
import com.codex.quota.R
import com.codex.quota.domain.model.QuotaWindow
import com.codex.quota.domain.usecase.evaluateQuotaAlertDecision
import com.codex.quota.notifications.buildQuotaAlertBigText
import com.codex.quota.notifications.buildQuotaAlertContentText
import com.codex.quota.notifications.buildQuotaAlertTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import io.mockk.every
import io.mockk.mockk
import java.util.Locale

class QuotaAlertEvaluatorTest {

    private val thresholds = setOf(5, 10, 25)

    @Test
    fun evaluateQuotaAlertDecision_firstDropBelowThreshold_triggersAlertAtLowestMatchingMilestone() {
        // Remaining 20% is <= 25%, not <= 10%
        val decision = evaluateQuotaAlertDecision(
            remainingPercent = 20.0,
            thresholds = thresholds,
            lastNotifiedThreshold = null
        )

        assertTrue(decision.shouldNotify)
        assertEquals(25, decision.milestone)
        assertFalse(decision.shouldClearThreshold)
    }

    @Test
    fun evaluateQuotaAlertDecision_alreadyNotifiedForSameMilestone_doesNotNotifyAgain() {
        // Remaining 22% is <= 25%, but already alerted for 25%
        val decision = evaluateQuotaAlertDecision(
            remainingPercent = 22.0,
            thresholds = thresholds,
            lastNotifiedThreshold = 25
        )

        assertFalse(decision.shouldNotify)
        assertEquals(25, decision.milestone)
        assertFalse(decision.shouldClearThreshold)
    }

    @Test
    fun evaluateQuotaAlertDecision_droppingToLowerMilestone_triggersEscalationAlert() {
        // Remaining 8% is <= 10% (and <= 25%), candidate min is 10. Previously notified for 25%.
        val decision = evaluateQuotaAlertDecision(
            remainingPercent = 8.0,
            thresholds = thresholds,
            lastNotifiedThreshold = 25
        )

        assertTrue(decision.shouldNotify)
        assertEquals(10, decision.milestone)
        assertFalse(decision.shouldClearThreshold)
    }

    @Test
    fun evaluateQuotaAlertDecision_risingWithinThresholds_doesNotNotify() {
        // Previously alerted for 5%. Remaining rises to 8% (still <= 10% and <= 25%).
        // Candidate milestone is 10, which is NOT < 5.
        val decision = evaluateQuotaAlertDecision(
            remainingPercent = 8.0,
            thresholds = thresholds,
            lastNotifiedThreshold = 5
        )

        assertFalse(decision.shouldNotify)
        assertEquals(10, decision.milestone)
        assertFalse(decision.shouldClearThreshold)
    }

    @Test
    fun evaluateQuotaAlertDecision_recoveringAboveMaxThreshold_clearsThresholdState() {
        // Remaining recovered to 80% (> max threshold 25%). Previously notified for 10%.
        val decision = evaluateQuotaAlertDecision(
            remainingPercent = 80.0,
            thresholds = thresholds,
            lastNotifiedThreshold = 10
        )

        assertFalse(decision.shouldNotify)
        assertEquals(null, decision.milestone)
        assertTrue(decision.shouldClearThreshold)
    }

    @Test
    fun evaluateQuotaAlertDecision_recoveringAboveMaxThresholdWhenAlreadyNull_doesNothing() {
        val decision = evaluateQuotaAlertDecision(
            remainingPercent = 80.0,
            thresholds = thresholds,
            lastNotifiedThreshold = null
        )

        assertFalse(decision.shouldNotify)
        assertEquals(null, decision.milestone)
        assertFalse(decision.shouldClearThreshold)
    }

    @Test
    fun evaluateQuotaAlertDecision_emptyThresholds_doesNotNotify() {
        val decision = evaluateQuotaAlertDecision(
            remainingPercent = 5.0,
            thresholds = emptySet(),
            lastNotifiedThreshold = null
        )

        assertFalse(decision.shouldNotify)
        assertEquals(null, decision.milestone)
        assertFalse(decision.shouldClearThreshold)
    }

    @Test
    fun pureNotificationHelpers_weeklyWindow_formatsAppropriateText() {
        val context = notificationContext()
        val title = buildQuotaAlertTitle(context, QuotaWindow.WEEKLY, isApiKey = false)
        val content = buildQuotaAlertContentText(context, "Personal Plus", 10, QuotaWindow.WEEKLY, isApiKey = false)
        val bigText = buildQuotaAlertBigText(context, "Personal Plus", "ChatGPT Plus", 10, 10, QuotaWindow.WEEKLY, isApiKey = false)

        assertEquals("Low Weekly Quota Alert", title)
        assertTrue(content.contains("Personal Plus has only 10% Weekly quota remaining."))
        assertTrue(bigText.contains("Personal Plus (ChatGPT Plus) is below the 10% threshold at 10% remaining in the Weekly window."))
    }

    @Test
    fun pureNotificationHelpers_fiveHourWindow_formatsAppropriateText() {
        val context = notificationContext()
        val title = buildQuotaAlertTitle(context, QuotaWindow.FIVE_HOUR, isApiKey = false)
        val content = buildQuotaAlertContentText(context, "Team Account", 5, QuotaWindow.FIVE_HOUR, isApiKey = false)
        val bigText = buildQuotaAlertBigText(context, "Team Account", "ChatGPT Team", 5, 5, QuotaWindow.FIVE_HOUR, isApiKey = false)

        assertEquals("Low 5-hour Quota Alert", title)
        assertTrue(content.contains("Team Account has only 5% 5-hour quota remaining."))
        assertTrue(bigText.contains("Team Account (ChatGPT Team) is below the 5% threshold at 5% remaining in the 5-hour window."))
    }

    @Test
    fun pureNotificationHelpers_apiKeyAccount_formatsGenericTextWithoutWeeklyClaims() {
        val context = notificationContext()
        val title = buildQuotaAlertTitle(context, window = null, isApiKey = true)
        val content = buildQuotaAlertContentText(context, "Production API", 15, window = null, isApiKey = true)
        val bigText = buildQuotaAlertBigText(context, "Production API", "OpenAI Tier 1", 25, 15, window = null, isApiKey = true)

        assertEquals("Low Quota Alert", title)
        assertEquals("Production API has only 15% quota remaining.", content)
        assertEquals("Production API (OpenAI Tier 1) is below the 25% threshold at 15% remaining.", bigText)

        assertFalse(title.lowercase().contains("weekly"))
        assertFalse(content.lowercase().contains("weekly"))
        assertFalse(bigText.lowercase().contains("weekly"))
        assertFalse(bigText.lowercase().contains("window"))
    }

    @Test
    fun pureNotificationHelpers_gptReserveWindow_formatsAppropriateText() {
        val context = notificationContext()
        val title = buildQuotaAlertTitle(context, QuotaWindow.GPT_RESERVE, isApiKey = false)
        val content = buildQuotaAlertContentText(context, "Personal Plus", 10, QuotaWindow.GPT_RESERVE, isApiKey = false)

        assertEquals("Low GPT Reserve Quota Alert", title)
        assertTrue(content.contains("Personal Plus has only 10% GPT Reserve quota remaining."))
    }

    private fun notificationContext(): Context = mockk {
        every { getString(any()) } answers {
            when (firstArg<Int>()) {
                R.string.window_weekly -> "Weekly"
                R.string.window_five_hour -> "5-hour"
                R.string.gpt_reserve -> "GPT Reserve"
                R.string.quota_notification_title -> "Low Quota Alert"
                else -> error("Unexpected unformatted string resource")
            }
        }
        every { getString(any(), *anyVararg()) } answers {
            val resourceId = firstArg<Int>()
            val args = secondArg<Array<out Any?>>()
            val template = when (resourceId) {
                R.string.quota_notification_title -> "Low Quota Alert"
                R.string.quota_notification_window_title -> "Low %1\$s Quota Alert"
                R.string.quota_notification_text -> "%1\$s has only %2\$d%% quota remaining."
                R.string.quota_notification_window_text -> "%1\$s has only %2\$d%% %3\$s quota remaining."
                R.string.quota_notification_details -> "%1\$s (%2\$s) is below the %3\$d%% threshold at %4\$d%% remaining."
                R.string.quota_notification_window_details -> "%1\$s (%2\$s) is below the %3\$d%% threshold at %4\$d%% remaining in the %5\$s window."
                else -> error("Unexpected formatted string resource")
            }
            String.format(Locale.US, template, *args)
        }
    }
}
