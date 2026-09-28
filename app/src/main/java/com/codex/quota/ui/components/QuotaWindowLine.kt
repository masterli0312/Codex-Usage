package com.codex.quota.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codex.quota.R
import com.codex.quota.domain.model.AuthStatus
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
fun QuotaWindowLine(label: String, percent: Double?, resetAtEpochMs: Long?, now: Long, modifier: Modifier = Modifier, status: AuthStatus? = null) {
    val context = LocalContext.current
    val displayedPercent = if (status != null && status != AuthStatus.AUTHENTICATED &&
        resetAtEpochMs != null && resetAtEpochMs <= now) null else percent
    val progress by animateFloatAsState(
        targetValue = ((displayedPercent ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f),
        animationSpec = tween(500),
        label = "quota_window_progress"
    )
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val progressColor = MaterialTheme.colorScheme.primary
    val resetText = when {
        resetAtEpochMs == null || resetAtEpochMs <= 0L -> stringResource(R.string.reset_time_unavailable)
        status != null && status != AuthStatus.AUTHENTICATED && resetAtEpochMs <= now ->
            stringResource(R.string.reset_time_unavailable)
        resetAtEpochMs <= now -> formatResetCountdown(context, resetAtEpochMs, now)
        else -> stringResource(R.string.resets_in, formatResetCountdown(context, resetAtEpochMs, now))
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            Text(formatQuotaPercent(displayedPercent), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Canvas(Modifier.fillMaxWidth().height(4.dp).semantics {
            progressBarRangeInfo = if (displayedPercent == null) ProgressBarRangeInfo.Indeterminate else ProgressBarRangeInfo(progress, 0f..1f)
        }) {
            val roundedEnd = CornerRadius(size.height / 2f)
            drawRoundRect(trackColor, cornerRadius = roundedEnd)
            if (progress > 0f) {
                drawRoundRect(progressColor, size = Size(size.width * progress, size.height), cornerRadius = roundedEnd)
            }
        }
        Text(resetText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
