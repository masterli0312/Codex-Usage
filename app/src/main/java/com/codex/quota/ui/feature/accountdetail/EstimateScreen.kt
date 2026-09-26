package com.codex.quota.ui.feature.accountdetail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codex.quota.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EstimateScreen(viewModel: AccountDetailViewModel, onNavigateBack: () -> Unit) {
    val data by viewModel.accountState.collectAsState()
    val usage = data?.usage
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.estimated_remaining)) }, navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            EstimatePanel {
                Text(stringResource(R.string.value_unavailable), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.estimate_insufficient_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            EstimatePanel {
                Text(stringResource(R.string.estimate_composition), fontWeight = FontWeight.Bold)
                EstimateRow(stringResource(R.string.estimated_weekly_budget), stringResource(R.string.value_unavailable))
                EstimateRow(stringResource(R.string.used_this_period), stringResource(R.string.value_unavailable))
                EstimateRow(stringResource(R.string.estimated_remaining), stringResource(R.string.value_unavailable))
            }
            EstimatePanel {
                Text(stringResource(R.string.estimate_reliability), fontWeight = FontWeight.Bold)
                EstimateRow(stringResource(R.string.confidence), stringResource(R.string.estimate_insufficient_data))
                EstimateRow(stringResource(R.string.estimate_range), stringResource(R.string.value_unavailable))
                EstimateRow(stringResource(R.string.valid_samples), stringResource(R.string.value_unavailable))
                EstimateRow(stringResource(R.string.sample_coverage), stringResource(R.string.value_unavailable))
                EstimateRow(stringResource(R.string.last_calibrated), stringResource(R.string.value_unavailable))
            }
            EstimatePanel {
                Text(stringResource(R.string.estimate_basis), fontWeight = FontWeight.Bold)
                EstimateRow(stringResource(R.string.calculation_basis), stringResource(R.string.official_api_usd))
                Text(stringResource(R.string.estimate_data_missing), style = MaterialTheme.typography.bodySmall)
            }
            Text(stringResource(R.string.estimate_disclaimer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EstimatePanel(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content) }
}

@Composable
private fun EstimateRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}
