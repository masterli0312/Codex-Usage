package com.codex.quota.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.codex.quota.R
import kotlin.math.abs
import kotlin.math.roundToInt
import com.codex.quota.domain.model.QuotaPacing
import com.codex.quota.domain.model.QuotaPacingStatus
import com.codex.quota.ui.theme.Amber500
import com.codex.quota.ui.theme.Emerald400

@Composable
fun QuotaPacingBadge(
    pacing: QuotaPacing,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val (icon, tint) = when (pacing.status) {
        QuotaPacingStatus.AHEAD -> Icons.Default.TrendingUp to Emerald400
        QuotaPacingStatus.ON_PACE -> Icons.Default.TrendingFlat to MaterialTheme.colorScheme.primary
        QuotaPacingStatus.BEHIND -> Icons.Default.TrendingDown to Amber500
    }

    val statusLabel = stringResource(when (pacing.status) {
        QuotaPacingStatus.AHEAD -> R.string.pacing_ahead
        QuotaPacingStatus.ON_PACE -> R.string.pacing_on_pace
        QuotaPacingStatus.BEHIND -> R.string.pacing_behind
    })
    val description = when (pacing.status) {
        QuotaPacingStatus.AHEAD -> pluralStringResource(R.plurals.pacing_more, abs(pacing.deltaPercent).roundToInt(), abs(pacing.deltaPercent).roundToInt())
        QuotaPacingStatus.ON_PACE -> stringResource(R.string.pacing_matching)
        QuotaPacingStatus.BEHIND -> pluralStringResource(R.plurals.pacing_less, abs(pacing.deltaPercent).roundToInt(), abs(pacing.deltaPercent).roundToInt())
    }
    val accessibilityLabel = "$statusLabel: $description"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(
                horizontal = if (compact) 8.dp else 10.dp,
                vertical = if (compact) 5.dp else 7.dp
            )
            .semantics { contentDescription = accessibilityLabel },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(if (compact) 14.dp else 16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                softWrap = true
            )
        }
    }
}
