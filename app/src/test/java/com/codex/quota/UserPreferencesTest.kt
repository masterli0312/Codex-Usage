package com.codex.quota

import com.codex.quota.domain.model.AppThemeMode
import com.codex.quota.domain.model.RefreshIntervalMinutes
import com.codex.quota.domain.model.UserPreferences
import com.codex.quota.domain.model.WidgetThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserPreferencesTest {

    @Test
    fun userPreferences_defaultValues_areCorrect() {
        val prefs = UserPreferences()

        assertEquals(AppThemeMode.SYSTEM, prefs.themeMode)
        assertTrue(prefs.dynamicColor)
        assertEquals(WidgetThemeMode.DARK_OBSIDIAN, prefs.widgetThemeMode)
        assertTrue(prefs.backgroundSyncEnabled)
        assertEquals(RefreshIntervalMinutes.MINUTES_30, prefs.refreshInterval)
        assertTrue(prefs.refreshOnAppOpen)
        assertTrue(prefs.signedOutNotificationsEnabled)
        assertTrue(prefs.quotaAlertsEnabled)
        assertFalse(prefs.includeFiveHourQuotaAlerts) // 5-hour warnings opt-in default false
        assertFalse(prefs.fiveHourResetReminderEnabled)
        assertFalse(prefs.includeGptReserveAlerts)
        assertEquals(setOf(5, 10, 25), prefs.quotaAlertThresholds)
        assertFalse(prefs.hasCompletedOnboarding)
        assertTrue(prefs.dismissedRenewalBannerAccountIds.isEmpty())
    }

    @Test
    fun userPreferences_copy_updatesIncludeFiveHourQuotaAlerts() {
        val prefs = UserPreferences()
        val updated = prefs.copy(includeFiveHourQuotaAlerts = true)

        assertTrue(updated.includeFiveHourQuotaAlerts)
        assertTrue(updated.quotaAlertsEnabled)
    }
}
