package com.codex.quota.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.codex.quota.CodexQuotaApplication
import com.codex.quota.R
import com.codex.quota.domain.model.*

@Composable
fun ActivationStatusText(item: AccountWithUsage, now: Long, modifier: Modifier = Modifier) {
    if (item.account.isDemoAccount) return
    val app = LocalContext.current.applicationContext as CodexQuotaApplication
    val prefs by remember(app) { app.preferencesRepository.observePreferences() }.collectAsState(initial = null)
    val claims by remember(app) { app.dataStoreManager.fiveHourActivationClaims }.collectAsState(initial = null)
    val settings = prefs ?: return
    val attempts = claims ?: return
    val state = activationDisplayState(item.usage, item.account.id in settings.autoActivateFiveHourAccountIds,
        settings.backgroundSyncEnabled, attempts[item.account.id], now)
    val label = when (state) {
        ActivationDisplayState.OFF -> R.string.activation_state_off
        ActivationDisplayState.BACKGROUND_OFF -> R.string.activation_state_background_off
        ActivationDisplayState.LOGIN_REQUIRED -> R.string.activation_state_login
        ActivationDisplayState.WAITING_NETWORK -> R.string.activation_state_network
        ActivationDisplayState.WAITING_RETRY -> R.string.activation_state_retry
        ActivationDisplayState.WEEKLY_EXHAUSTED -> R.string.activation_state_weekly
        ActivationDisplayState.ACTIVE -> R.string.activation_state_active
        ActivationDisplayState.WAITING_ACTIVATION -> R.string.activation_state_waiting
        ActivationDisplayState.CHECK_RESULT -> R.string.activation_state_check_result
        ActivationDisplayState.CHECK_DATA -> R.string.activation_state_check_data
    }
    Text(stringResource(label), modifier, style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}
