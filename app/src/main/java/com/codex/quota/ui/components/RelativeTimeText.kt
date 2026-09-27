package com.codex.quota.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.codex.quota.R
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun RelativeTimeText(
    epochMs: Long?,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    now: Long = System.currentTimeMillis()
) {
    val locale = LocalConfiguration.current.locales[0]
    val text = if (epochMs == null || epochMs <= 0) {
        stringResource(R.string.never_synced)
    } else {
        val diff = now - epochMs
        val relative = when {
            diff < 60_000L -> stringResource(R.string.just_now)
            diff < 3600_000L -> stringResource(R.string.relative_minutes_ago, diff / 60_000L)
            diff < 86400_000L -> stringResource(R.string.relative_hours_ago, diff / 3600_000L)
            else -> SimpleDateFormat(stringResource(R.string.relative_date_pattern), locale).format(Date(epochMs))
        }
        stringResource(R.string.relative_updated, relative)
    }

    Text(
        text = text,
        modifier = modifier,
        style = style,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}
