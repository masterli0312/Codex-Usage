package com.codex.quota.ui.feature.accountdetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codex.quota.R
import com.codex.quota.domain.usecase.ResetSpendOutcome
import com.codex.quota.ui.components.CircularQuotaGauge
import com.codex.quota.ui.components.StatusBadge
import com.codex.quota.ui.util.formatQuotaPercent
import com.codex.quota.ui.util.localizedPlanName
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDetailScreen(viewModel: AccountDetailViewModel, onNavigateBack: () -> Unit, onNavigateToEstimate: () -> Unit, modifier: Modifier = Modifier) {
    val data by viewModel.accountState.collectAsState()
    val refreshing by viewModel.isRefreshing.collectAsState()
    val deleted by viewModel.accountDeleted.collectAsState()
    val resetting by viewModel.isResetting.collectAsState()
    val resetOutcome by viewModel.resetOutcome.collectAsState()
    var showDelete by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }
    LaunchedEffect(deleted) { if (deleted) onNavigateBack() }
    val account = data?.account
    val usage = data?.usage
    Scaffold(modifier = modifier.fillMaxSize(), topBar = {
        TopAppBar(title = {
            Column {
                Text(account?.nickname ?: stringResource(R.string.account_details), fontWeight = FontWeight.Bold)
                if (account != null) Text(localizedPlanName(LocalContext.current, account.planType), style = MaterialTheme.typography.labelSmall)
            }
        }, navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) } }, actions = {
            IconButton(onClick = viewModel::refresh, enabled = !refreshing) { Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh_usage_data)) }
        })
    }) { padding ->
        if (account == null) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Panel {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { StatusBadge(usage?.status ?: account.authStatus) }
                    Spacer(Modifier.height(5.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularQuotaGauge(usage?.remainingPercent, size = 116.dp, strokeWidth = 10.dp) }
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        ValueColumn(stringResource(R.string.quota_weekly), formatQuotaPercent(usage?.remainingPercent))
                        ValueColumn(stringResource(R.string.quota_five_hour), formatQuotaPercent(usage?.fiveHourRemainingPercent))
                        ValueColumn(stringResource(R.string.gpt_reserve), formatQuotaPercent(usage?.gptReserveRemainingPercent))
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth().clickable(onClick = onNavigateToEstimate), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.estimated_remaining_value, stringResource(R.string.value_unavailable)), fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.estimate_insufficient_data), style = MaterialTheme.typography.labelSmall)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }
            item {
                Panel {
                    Text(stringResource(R.string.usage_statistics), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        ValueColumn(stringResource(R.string.token_rate), stringResource(R.string.value_unavailable))
                        ValueColumn(stringResource(R.string.period_tokens), usage?.usedTokens?.let { NumberFormat.getIntegerInstance().format(it) } ?: stringResource(R.string.value_unavailable))
                        ValueColumn(stringResource(R.string.api_equivalent_spend), stringResource(R.string.value_unavailable))
                    }
                }
            }
            item {
                Panel {
                    Text(stringResource(R.string.subscription_renewal), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    LabelValue(stringResource(R.string.plan_type), localizedPlanName(LocalContext.current, account.planType))
                    LabelValue(stringResource(R.string.renewal_amount), stringResource(R.string.value_unavailable))
                    LabelValue(stringResource(R.string.auto_renewal), usage?.willAutoRenew?.let { if (it) stringResource(R.string.enabled) else stringResource(R.string.disabled) } ?: stringResource(R.string.value_unavailable))
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showReset = true }, enabled = !resetting && (usage?.bankedResets ?: 0) > 0, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.use_reset)) }
                    OutlinedButton(onClick = { showDelete = true }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Delete, contentDescription = null); Text(stringResource(R.string.remove_account)) }
                }
            }
        }
    }
    if (showDelete) AlertDialog(onDismissRequest = { showDelete = false }, title = { Text(stringResource(R.string.remove_account)) }, text = { Text(stringResource(R.string.remove_account_message, account?.nickname ?: "")) }, confirmButton = { TextButton(onClick = { showDelete = false; viewModel.deleteAccount() }) { Text(stringResource(R.string.remove_account)) } }, dismissButton = { TextButton(onClick = { showDelete = false }) { Text(stringResource(R.string.action_cancel)) } })
    if (showReset) AlertDialog(onDismissRequest = { showReset = false }, title = { Text(stringResource(R.string.reset_confirm_title)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.reset_consumes_one))
            Text(stringResource(R.string.reset_after_label), fontWeight = FontWeight.Bold)
            LabelValue(stringResource(R.string.quota_weekly), stringResource(R.string.value_unavailable))
            LabelValue(stringResource(R.string.quota_five_hour), stringResource(R.string.value_unavailable))
            LabelValue(stringResource(R.string.gpt_reserve), stringResource(R.string.reset_reserve_unaffected))
            LabelValue(stringResource(R.string.reset_remaining), ((usage?.bankedResets ?: 1) - 1).toString())
            Text(stringResource(R.string.reset_irreversible), color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { Button(onClick = { showReset = false; viewModel.consumeReset() }, enabled = !resetting) { Text(stringResource(R.string.reset_confirm_action)) } }, dismissButton = { TextButton(onClick = { showReset = false }) { Text(stringResource(R.string.action_cancel)) } })

    when (val outcome = resetOutcome) {
        is ResetSpendOutcome.Success -> AlertDialog(
            onDismissRequest = viewModel::clearResetOutcome,
            title = { Text(stringResource(R.string.reset_success_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabelValue(stringResource(R.string.quota_weekly), formatQuotaPercent(outcome.freshUsage?.remainingPercent))
                    LabelValue(stringResource(R.string.quota_five_hour), formatQuotaPercent(outcome.freshUsage?.fiveHourRemainingPercent))
                    LabelValue(stringResource(R.string.gpt_reserve), formatQuotaPercent(outcome.freshUsage?.gptReserveRemainingPercent))
                    LabelValue(stringResource(R.string.reset_remaining), outcome.freshUsage?.bankedResets?.toString() ?: stringResource(R.string.value_unavailable))
                    Text(stringResource(if (outcome.freshUsage == null) R.string.reset_success_refresh_failed else R.string.reset_success_refresh_note))
                }
            },
            confirmButton = { TextButton(onClick = viewModel::clearResetOutcome) { Text(stringResource(R.string.action_got_it)) } }
        )
        null -> Unit
        else -> AlertDialog(
            onDismissRequest = viewModel::clearResetOutcome,
            title = { Text(stringResource(R.string.use_reset)) },
            text = { Text(stringResource(when (outcome) {
                ResetSpendOutcome.NoCredit -> R.string.reset_no_credit
                ResetSpendOutcome.NothingToReset -> R.string.reset_nothing_to_reset
                ResetSpendOutcome.Uncertain -> R.string.reset_uncertain
                else -> R.string.reset_unavailable
            })) },
            confirmButton = { TextButton(onClick = viewModel::clearResetOutcome) { Text(stringResource(R.string.action_got_it)) } }
        )
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), content = content)
    }
}

@Composable
private fun ValueColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun LabelValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, style = MaterialTheme.typography.bodySmall); Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold) }
}
