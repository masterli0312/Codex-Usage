package com.codex.quota.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codex.quota.R
import com.codex.quota.domain.model.AccountWithUsage
import com.codex.quota.domain.model.AuthStatus
import com.codex.quota.ui.util.formatQuotaPercent
import com.codex.quota.ui.util.localizedPlanName
import java.text.NumberFormat
import java.util.Locale

@Composable
fun AccountCard(item: AccountWithUsage, onClick: () -> Unit, onSignInClick: () -> Unit, modifier: Modifier = Modifier) {
    val account = item.account
    val usage = item.usage
    val signedOut = (usage?.status ?: account.authStatus) == AuthStatus.AUTHENTICATION_REQUIRED
    Card(modifier = modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(Color(0xFF0CCB88), CircleShape))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(account.nickname, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(localizedPlanName(LocalContext.current, account.planType), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusBadge(usage?.status ?: account.authStatus)
            }
            if (signedOut) {
                TextButton(onClick = onSignInClick) { Text(stringResource(R.string.re_authenticate_now)) }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularQuotaGauge(usage?.remainingPercent, size = 82.dp, strokeWidth = 8.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        QuotaLine(stringResource(R.string.quota_weekly), usage?.remainingPercent)
                        QuotaLine(stringResource(R.string.quota_five_hour), usage?.fiveHourRemainingPercent)
                        QuotaLine(stringResource(R.string.gpt_reserve), usage?.gptReserveRemainingPercent)
                    }
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    SummaryMetric(stringResource(R.string.official_credit), usage?.remainingCredits?.let { NumberFormat.getCurrencyInstance(Locale.US).format(it) } ?: stringResource(R.string.value_unavailable))
                    SummaryMetric(stringResource(R.string.estimated_weekly_budget), stringResource(R.string.value_unavailable))
                    SummaryMetric(stringResource(R.string.reset_opportunities), usage?.bankedResets?.toString() ?: stringResource(R.string.value_unavailable))
                }
            }
        }
    }
}

@Composable
private fun QuotaLine(label: String, percent: Double?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(formatQuotaPercent(percent), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    }
    LinearProgressIndicator(progress = { ((percent ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(5.dp), color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun SummaryMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}
