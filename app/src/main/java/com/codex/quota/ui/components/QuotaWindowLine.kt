package com.codex.quota.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codex.quota.R
import com.codex.quota.ui.util.formatQuotaPercent
import com.codex.quota.ui.util.formatResetCountdown
import kotlinx.coroutines.delay

@Composable
fun rememberQuotaClock(): Long {
    val now by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(60_000L - System.currentTimeMillis() % 60_000L)
            value = System.currentTimeMillis()
        }
    }
    return now
}

@Composable
fun QuotaWindowLine(label: String, percent: Double?, resetAtEpochMs: Long?, now: Long, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val resetText = when {
        resetAtEpochMs == null || resetAtEpochMs <= 0L -> stringResource(R.string.reset_time_unavailable)
        resetAtEpochMs <= now -> formatResetCountdown(context, resetAtEpochMs, now)
        else -> stringResource(R.string.resets_in, formatResetCountdown(context, resetAtEpochMs, now))
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            Text(formatQuotaPercent(percent), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        LinearProgressIndicator(
            progress = { ((percent ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Text(resetText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
