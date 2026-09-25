package com.codex.quota.ui.feature.settings
import androidx.compose.ui.res.stringResource
import com.codex.quota.R

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.codex.quota.domain.model.AppThemeMode
import com.codex.quota.domain.model.RefreshIntervalMinutes
import com.codex.quota.domain.model.WidgetThemeMode
import com.codex.quota.ui.theme.Red500
import com.codex.quota.widget.WidgetUpdateHelper
import kotlinx.coroutines.launch
import com.codex.quota.CodexQuotaApplication
import com.codex.quota.notifications.QuotaAlertNotificationManager
import com.codex.quota.notifications.SignedOutNotificationManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateToAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val preferences by viewModel.preferencesState.collectAsState()
    val context = LocalContext.current
    val selectedLanguage = AppLanguage.fromLanguageTags(AppCompatDelegate.getApplicationLocales().toLanguageTags())
    val snackbarHostState = remember { SnackbarHostState() }
    var showClearDataDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val isGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!isGranted) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(16.dp)
                )
            }
        },
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsSection(title = stringResource(R.string.language_title), icon = Icons.Default.ColorLens) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppLanguage.entries.forEach { language ->
                        val label = when (language) {
                            AppLanguage.FOLLOW_SYSTEM -> R.string.language_follow_system
                            AppLanguage.SIMPLIFIED_CHINESE -> R.string.language_chinese
                            AppLanguage.ENGLISH -> R.string.language_english
                        }
                        FilterChip(
                            selected = selectedLanguage == language,
                            onClick = {
                                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.languageTag))
                                val applicationContext = context.applicationContext
                                (applicationContext as CodexQuotaApplication).applicationScope.launch {
                                    QuotaAlertNotificationManager(applicationContext)
                                    SignedOutNotificationManager(applicationContext)
                                    WidgetUpdateHelper.updateAllWidgets(applicationContext)
                                }
                            },
                            label = { Text(stringResource(label)) }
                        )
                    }
                }
            }

            // Appearance Section
            SettingsSection(title = stringResource(R.string.appearance_theme), icon = Icons.Default.ColorLens) {
                Text(stringResource(R.string.app_theme), style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = preferences.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = { Text(stringResource(when (mode) {
                                AppThemeMode.SYSTEM -> R.string.theme_system
                                AppThemeMode.LIGHT -> R.string.theme_light
                                AppThemeMode.DARK -> R.string.theme_dark
                            })) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.dynamic_colors_material_you), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.adaptive_palette_based_on_system_wallpaper),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = preferences.dynamicColor,
                        onCheckedChange = { viewModel.setDynamicColor(it) }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(stringResource(R.string.home_screen_widget_style), style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WidgetThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = preferences.widgetThemeMode == mode,
                            onClick = { viewModel.setWidgetThemeMode(context, mode) },
                            label = { Text(stringResource(when (mode) {
                                WidgetThemeMode.DARK_OBSIDIAN -> R.string.widget_theme_dark
                                WidgetThemeMode.SYSTEM_MATERIAL_YOU -> R.string.widget_theme_material
                            })) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Sync Section
            SettingsSection(title = stringResource(R.string.background_synchronization), icon = Icons.Default.Sync) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.periodic_background_sync), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (preferences.backgroundSyncEnabled) stringResource(R.string.app_wakes_periodically_in_background_to_refresh_quotas) else stringResource(R.string.disabled_app_only_refreshes_when_opened),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = preferences.backgroundSyncEnabled,
                        onCheckedChange = { viewModel.setBackgroundSyncEnabled(context, it) }
                    )
                }

                if (preferences.backgroundSyncEnabled) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(stringResource(R.string.refresh_frequency), style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            RefreshIntervalMinutes.MINUTES_15,
                            RefreshIntervalMinutes.MINUTES_30,
                            RefreshIntervalMinutes.HOURS_1,
                            RefreshIntervalMinutes.HOURS_3
                        ).forEach { interval ->
                            FilterChip(
                                selected = preferences.refreshInterval == interval,
                                onClick = { viewModel.setRefreshInterval(context, interval) },
                                label = { Text(stringResource(when (interval) {
                                    RefreshIntervalMinutes.MINUTES_15 -> R.string.interval_15_minutes
                                    RefreshIntervalMinutes.MINUTES_30 -> R.string.interval_30_minutes
                                    RefreshIntervalMinutes.HOURS_1 -> R.string.interval_1_hour
                                    RefreshIntervalMinutes.HOURS_3 -> R.string.interval_3_hours
                                    RefreshIntervalMinutes.HOURS_6 -> R.string.interval_6_hours
                                }), fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.refresh_on_app_open), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.automatically_checks_for_stale_data_upon_launching_the_app),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = preferences.refreshOnAppOpen,
                        onCheckedChange = { viewModel.setRefreshOnAppOpen(it) }
                    )
                }
            }

            // Notifications Section
            SettingsSection(title = stringResource(R.string.notifications_alerts), icon = Icons.Default.Notifications) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.signed_out_alerts), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.sends_an_android_notification_when_account_tokens_expire),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = preferences.signedOutNotificationsEnabled,
                        onCheckedChange = {
                            if (it) checkAndRequestNotificationPermission()
                            viewModel.setSignedOutNotificationsEnabled(it)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.low_quota_warnings), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.alerts_when_an_account_reaches_critical_quota_thresholds),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = preferences.quotaAlertsEnabled,
                        onCheckedChange = {
                            if (it) checkAndRequestNotificationPermission()
                            viewModel.setQuotaAlertsEnabled(it)
                        }
                    )
                }

                if (preferences.quotaAlertsEnabled) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.alert_thresholds_toggle_multi_select),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(5, 10, 25).forEach { threshold ->
                            val isSelected = preferences.quotaAlertThresholds.contains(threshold)
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.toggleQuotaAlertThreshold(threshold) },
                                label = { Text(stringResource(R.string.threshold_label, threshold)) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.include_5_hour_quota_warnings), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                stringResource(R.string.weekly_alerts_are_always_monitored_enable_this_to_apply_your_sele),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = preferences.includeFiveHourQuotaAlerts,
                            onCheckedChange = {
                                if (it) checkAndRequestNotificationPermission()
                                viewModel.setIncludeFiveHourQuotaAlerts(it)
                            }
                        )
                    }
                }
            }

            // Privacy & Security Section
            SettingsSection(title = stringResource(R.string.privacy_local_storage), icon = Icons.Default.Security) {
                Text(
                    text = stringResource(R.string.zero_telemetry_no_analytics_tracking_or_remote_error_reporting_n_),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = { showClearDataDialog = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Red500),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.DeleteForever, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.clear_all_local_data_secrets))
                }
            }

            // About & Disclaimers Navigation
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToAbout() },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.about_community), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Text(stringResource(R.string.open_source_notices_app_version_and_github_repo), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        if (showClearDataDialog) {
            AlertDialog(
                onDismissRequest = { showClearDataDialog = false },
                title = { Text(stringResource(R.string.clear_all_data)) },
                text = { Text(stringResource(R.string.this_will_permanently_delete_all_registered_accounts_cached_usage)) },
                confirmButton = {
                    Button(
                        onClick = {
                            showClearDataDialog = false
                            viewModel.clearAllData()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Red500)
                    ) {
                        Text(stringResource(R.string.clear_everything), color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearDataDialog = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}
