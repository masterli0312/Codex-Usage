package com.codex.quota.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codex.quota.R
import com.codex.quota.domain.model.AccountWithUsage
import com.codex.quota.domain.model.AuthStatus
import com.codex.quota.ui.util.localizedPlanName
import java.text.NumberFormat
import java.util.Locale

@Composable
fun AccountCard(item: AccountWithUsage, onClick: () -> Unit, onSignInClick: () -> Unit, modifier: Modifier = Modifier) {
    val account = item.account
    val usage = item.usage
    val now = rememberQuotaClock()
    val signedOut = (usage?.status ?: account.authStatus) == AuthStatus.AUTHENTICATION_REQUIRED
    Card(modifier = modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(account.nickname, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(localizedPlanName(LocalContext.current, account.planType), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusBadge(usage?.status ?: account.authStatus)
            }
            if (signedOut) {
                TextButton(onClick = onSignInClick) { Text(stringResource(R.string.re_authenticate_now)) }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    CircularQuotaGauge(usage?.remainingPercent, size = 82.dp, strokeWidth = 8.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        QuotaWindowLine(stringResource(R.string.quota_weekly), usage?.remainingPercent, usage?.resetAtEpochMs, now)
                        QuotaWindowLine(stringResource(R.string.quota_five_hour), usage?.fiveHourRemainingPercent, usage?.fiveHourResetAtEpochMs, now)
                        QuotaWindowLine(stringResource(R.string.gpt_reserve), usage?.gptReserveRemainingPercent, usage?.gptReserveResetAtEpochMs, now)
                    }
                }
                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).padding(vertical = 10.dp)) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        SummaryMetric(stringResource(R.string.official_credit), usage?.remainingCredits?.let { NumberFormat.getCurrencyInstance(Locale.US).format(it) } ?: stringResource(R.string.value_unavailable))
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        SummaryMetric(stringResource(R.string.reset_opportunities), usage?.bankedResets?.toString() ?: stringResource(R.string.value_unavailable))
                    }
                }
            }
            RelativeTimeText(account.lastSuccessfulSyncEpochMs, style = MaterialTheme.typography.labelSmall, now = now)
        }
    }
}

@Composable
private fun SummaryMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}
