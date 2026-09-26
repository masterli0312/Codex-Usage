package com.codex.quota.ui.feature.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import com.codex.quota.R
import com.codex.quota.domain.model.AppThemeMode
import com.codex.quota.domain.model.RefreshIntervalMinutes
import com.codex.quota.ui.theme.Red500

private data class SettingEntry(val page: String, val title: Int, val subtitle: Int, val icon: String)

private val entries = listOf(
    SettingEntry("language", R.string.language_title, R.string.language_summary, "🌐"),
    SettingEntry("appearance", R.string.appearance_theme, R.string.appearance_summary, "◉"),
    SettingEntry("sync", R.string.background_synchronization, R.string.sync_summary, "⟳"),
    SettingEntry("notifications", R.string.notifications_alerts, R.string.notifications_summary, "♟"),
    SettingEntry("renewal", R.string.renewal_protection, R.string.renewal_summary, "◷"),
    SettingEntry("privacy", R.string.privacy_local_storage, R.string.privacy_summary, "◆")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, modifier: Modifier = Modifier, page: String = "home", onNavigate: (String) -> Unit = {}, onNavigateBack: () -> Unit = {}) {
    val preferences by viewModel.preferencesState.collectAsState()
    val context = LocalContext.current
    var showClearData by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun notificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    val title = if (page == "home") R.string.settings_title else entries.firstOrNull { it.page == page }?.title ?: R.string.settings_title
    Scaffold(modifier = modifier.fillMaxSize(), topBar = { TopAppBar(title = { Text(stringResource(title), fontWeight = FontWeight.Bold) }, navigationIcon = { if (page != "home") IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when (page) {
                "home" -> entries.forEach { entry ->
                    Row(Modifier.fillMaxWidth().clickable { onNavigate(entry.page) }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(entry.icon, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(40.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(entry.title), fontWeight = FontWeight.SemiBold)
                            Text(stringResource(entry.subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                "language" -> {
                    val selected = AppLanguage.fromLanguageTags(AppCompatDelegate.getApplicationLocales().toLanguageTags())
                    AppLanguage.entries.forEach { language ->
                        val label = when (language) { AppLanguage.FOLLOW_SYSTEM -> R.string.language_follow_system; AppLanguage.SIMPLIFIED_CHINESE -> R.string.language_chinese; AppLanguage.ENGLISH -> R.string.language_english }
                        SelectRow(stringResource(label), selected == language) { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.languageTag)) }
                    }
                }
                "appearance" -> {
                    Text(stringResource(R.string.app_theme), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                    AppThemeMode.entries.forEach { mode ->
                        val label = when (mode) { AppThemeMode.SYSTEM -> R.string.theme_system; AppThemeMode.LIGHT -> R.string.theme_light; AppThemeMode.DARK -> R.string.theme_dark }
                        SelectRow(stringResource(label), preferences.themeMode == mode) { viewModel.setThemeMode(mode) }
                    }
                    ToggleRow(stringResource(R.string.dynamic_colors_material_you), stringResource(R.string.adaptive_palette_based_on_system_wallpaper), preferences.dynamicColor, viewModel::setDynamicColor)
                    ToggleRow(stringResource(R.string.solid_color_theme), stringResource(R.string.solid_color_summary), !preferences.dynamicColor, { viewModel.setDynamicColor(!it) })
                }
                "sync" -> {
                    ToggleRow(stringResource(R.string.periodic_background_sync), stringResource(R.string.app_wakes_periodically_in_background_to_refresh_quotas), preferences.backgroundSyncEnabled, { viewModel.setBackgroundSyncEnabled(context, it) })
                    Text(stringResource(R.string.refresh_frequency), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(RefreshIntervalMinutes.MINUTES_15, RefreshIntervalMinutes.MINUTES_30, RefreshIntervalMinutes.HOURS_1, RefreshIntervalMinutes.HOURS_3).forEach { interval ->
                            val label = when (interval) { RefreshIntervalMinutes.MINUTES_15 -> R.string.interval_15_minutes; RefreshIntervalMinutes.MINUTES_30 -> R.string.interval_30_minutes; RefreshIntervalMinutes.HOURS_1 -> R.string.interval_1_hour; else -> R.string.interval_3_hours }
                            FilterChip(selected = preferences.refreshInterval == interval, onClick = { viewModel.setRefreshInterval(context, interval) }, label = { Text(stringResource(label), style = MaterialTheme.typography.labelSmall) }, modifier = Modifier.weight(1f))
                        }
                    }
                }
                "notifications" -> {
                    ToggleRow(stringResource(R.string.low_quota_warnings), stringResource(R.string.alerts_when_an_account_reaches_critical_quota_thresholds), preferences.quotaAlertsEnabled, { if (it) notificationPermission(); viewModel.setQuotaAlertsEnabled(it) })
                    ToggleRow(stringResource(R.string.five_hour_warning), stringResource(R.string.five_hour_warning_summary), preferences.includeFiveHourQuotaAlerts, { if (it) notificationPermission(); viewModel.setIncludeFiveHourQuotaAlerts(it) })
                    ToggleRow(stringResource(R.string.weekly_warning), stringResource(R.string.weekly_warning_summary), preferences.includeWeeklyQuotaAlerts, { if (it) notificationPermission(); viewModel.setIncludeWeeklyQuotaAlerts(it) })
                    ToggleRow(stringResource(R.string.reset_alert), stringResource(R.string.feature_unavailable), false, {}, enabled = false)
                    ToggleRow(stringResource(R.string.reserve_alert), stringResource(R.string.reserve_alert_summary), preferences.includeGptReserveAlerts, { if (it) notificationPermission(); viewModel.setIncludeGptReserveAlerts(it) })
                    ToggleRow(stringResource(R.string.official_credit_alert), stringResource(R.string.feature_unavailable), false, {}, enabled = false)
                    Text(stringResource(R.string.alert_method), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                    Text(stringResource(R.string.system_notification), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.notification_channel_controls), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                "renewal" -> {
                    ToggleRow(stringResource(R.string.renewal_advance_alert), stringResource(R.string.feature_unavailable), false, {}, enabled = false)
                    SettingsInfo(stringResource(R.string.advance_days), stringResource(R.string.feature_unavailable))
                    ToggleRow(stringResource(R.string.renewal_confirm_alert), stringResource(R.string.feature_unavailable), false, {}, enabled = false)
                    ToggleRow(stringResource(R.string.renewal_failure_alert), stringResource(R.string.feature_unavailable), false, {}, enabled = false)
                    OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.test_notification)) }
                }
                "privacy" -> {
                    SettingsInfo(stringResource(R.string.zero_telemetry), stringResource(R.string.zero_telemetry_detail))
                    SettingsInfo(stringResource(R.string.local_encryption), stringResource(R.string.local_encryption_detail))
                    SettingsInfo(stringResource(R.string.no_cloud_relay), stringResource(R.string.no_cloud_relay_detail))
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = { showClearData = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = Red500)) { Icon(Icons.Default.DeleteForever, contentDescription = null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.clear_all_local_data_secrets)) }
                    Text(stringResource(R.string.clear_data_detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (showClearData) AlertDialog(onDismissRequest = { showClearData = false }, title = { Text(stringResource(R.string.clear_all_data)) }, text = { Text(stringResource(R.string.this_will_permanently_delete_all_registered_accounts_cached_usage)) }, confirmButton = { TextButton(onClick = { showClearData = false; viewModel.clearAllData() }) { Text(stringResource(R.string.clear_everything), color = Red500) } }, dismissButton = { TextButton(onClick = { showClearData = false }) { Text(stringResource(R.string.action_cancel)) } })
}

@Composable
private fun SelectRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = selected, onClick = onClick); Spacer(Modifier.width(8.dp)); Text(label) }
}

@Composable
private fun ToggleRow(label: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(label, fontWeight = FontWeight.Medium); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun SettingsInfo(label: String, detail: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) { Text(label, fontWeight = FontWeight.Medium); Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}
