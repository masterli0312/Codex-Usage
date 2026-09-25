package com.codex.quota.ui.components
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.codex.quota.R

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.quota.domain.model.AccountWithUsage
import com.codex.quota.domain.model.AuthStatus
import com.codex.quota.domain.model.QuotaWindow
import com.codex.quota.domain.model.weeklyQuotaPacing
import com.codex.quota.ui.theme.Red500
import com.codex.quota.ui.util.formatQuotaPercent
import com.codex.quota.ui.util.formatQuotaSummary
import com.codex.quota.ui.util.formatResetCountdown
import com.codex.quota.ui.util.isApiKeyQuotaUsage
import com.codex.quota.ui.util.localizedWindowLabel
import com.codex.quota.ui.util.localizedPlanName
import com.codex.quota.ui.util.localizedResetDuration
import com.codex.quota.ui.util.primarySubscriberQuotaWindow
import com.codex.quota.ui.util.subscriberQuotaWindows
import java.text.NumberFormat
import java.util.Locale

@Composable
fun AccountCard(
    item: AccountWithUsage,
    onClick: () -> Unit,
    onSignInClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val account = item.account
    val usage = item.usage
    val status = usage?.status ?: account.authStatus
    val isSignedOut = status == AuthStatus.AUTHENTICATION_REQUIRED

    val accountColor = try {
        Color(android.graphics.Color.parseColor(account.colorHex))
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header Row: Color Dot, Nickname, Plan, Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(accountColor)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = account.nickname,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = localizedPlanName(context, account.planType),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))
                StatusBadge(status = status)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isSignedOut) {
                SignedOutBanner(onSignInClick = onSignInClick)
            } else {
                ActiveQuotaSection(item = item)
            }
        }
    }
}

@Composable
private fun ActiveQuotaSection(item: AccountWithUsage) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val account = item.account
    val usage = item.usage
    val isApiKey = usage.isApiKeyQuotaUsage(account.planType)
    val subscriberWindows = usage.subscriberQuotaWindows(account.planType)
    val primarySubscriberWindow = usage.primarySubscriberQuotaWindow(account.planType)
    val remainingPercent = primarySubscriberWindow?.remainingPercent ?: usage?.remainingPercent
    val rateLimitInfo = usage?.rateLimitInfo

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularQuotaGauge(
            remainingPercent = remainingPercent,
            size = 76.dp,
            strokeWidth = 8.dp
        )

        Spacer(modifier = Modifier.width(18.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isApiKey) {
                if (rateLimitInfo?.limitTokens != null && rateLimitInfo.remainingTokens != null) {
                    val formattedTokens = NumberFormat.getNumberInstance(Locale.US).format(rateLimitInfo.remainingTokens)
                    val formattedLimit = NumberFormat.getNumberInstance(Locale.US).format(rateLimitInfo.limitTokens)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.tokens),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$formattedTokens / $formattedLimit",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    LinearQuotaBar(
                        remainingPercent = rateLimitInfo.tokenRemainingPercent,
                        height = 5.dp
                    )
                }

                if (rateLimitInfo?.limitRequests != null && rateLimitInfo.remainingRequests != null) {
                    val formattedReqs = NumberFormat.getNumberInstance(Locale.US).format(rateLimitInfo.remainingRequests)
                    val formattedLimit = NumberFormat.getNumberInstance(Locale.US).format(rateLimitInfo.limitRequests)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.requests),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$formattedReqs / $formattedLimit",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    LinearQuotaBar(
                        remainingPercent = rateLimitInfo.requestRemainingPercent,
                        height = 5.dp
                    )
                }
            } else if (subscriberWindows.isNotEmpty()) {
                val weeklyWindow = subscriberWindows.firstOrNull { it.window == QuotaWindow.WEEKLY }
                val fiveHourWindow = subscriberWindows.firstOrNull { it.window == QuotaWindow.FIVE_HOUR }
                val pacing = usage.weeklyQuotaPacing(account.planType)

                // 1. Weekly window row (Primary)
                if (weeklyWindow != null) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.quota_weekly),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.quota_remaining_reset, formatQuotaPercent(weeklyWindow.remainingPercent), formatResetCountdown(context, weeklyWindow.resetAtEpochMs)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        LinearQuotaBar(
                            remainingPercent = weeklyWindow.remainingPercent,
                            height = 4.dp
                        )
                    }
                }

                // 2. Pacing treatment immediately following weekly row
                if (pacing != null) {
                    QuotaPacingBadge(
                        pacing = pacing,
                        compact = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 3. 5-hour window row (Secondary)
                if (fiveHourWindow != null) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.quota_five_hour),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.quota_remaining_reset, formatQuotaPercent(fiveHourWindow.remainingPercent), formatResetCountdown(context, fiveHourWindow.resetAtEpochMs)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        LinearQuotaBar(
                            remainingPercent = fiveHourWindow.remainingPercent,
                            height = 4.dp
                        )
                    }
                }

                // Any other subscriber windows if present
                val otherWindows = subscriberWindows.filter { it != weeklyWindow && it != fiveHourWindow }
                otherWindows.forEach { window ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = localizedWindowLabel(context, window.window),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.quota_remaining_reset, formatQuotaPercent(window.remainingPercent), formatResetCountdown(context, window.resetAtEpochMs)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        LinearQuotaBar(
                            remainingPercent = window.remainingPercent,
                            height = 4.dp
                        )
                    }
                }
            }

            if (usage?.bankedResets != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.banked_resets),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = pluralStringResource(R.plurals.count_available, usage.bankedResets, usage.bankedResets),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(14.dp))
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
        thickness = 1.dp
    )
    Spacer(modifier = Modifier.height(10.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val renewalDateStr = usage?.subscriptionRenewalEpochMs?.let {
            java.text.SimpleDateFormat(context.getString(R.string.date_short_pattern), locale).format(java.util.Date(it))
        }

        val footerText = when {
            isApiKey -> {
                val resetDuration = localizedResetDuration(context, rateLimitInfo?.resetTokensDuration
                    ?: rateLimitInfo?.resetRequestsDuration)
                    ?: (if (usage?.resetAtEpochMs != null) stringResource(R.string.rolling_window) else stringResource(R.string.status_active))
                if (renewalDateStr != null) {
                    context.getString(R.string.resets_renews, resetDuration, renewalDateStr)
                } else {
                    context.getString(R.string.resets_in, resetDuration)
                }
            }
            renewalDateStr != null -> context.getString(R.string.subscription_renews, renewalDateStr)
            else -> stringResource(R.string.subscriber_quota_windows)
        }

        Text(
            text = footerText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        RelativeTimeText(
            epochMs = usage?.fetchedAtEpochMs,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun SignedOutBanner(
    onSignInClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Red500.copy(alpha = 0.1f))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = Red500,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.account_signed_out),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = Red500
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = stringResource(R.string.credentials_have_expired_or_been_revoked_re_authenticate_to_resum),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = onSignInClick,
            colors = ButtonDefaults.buttonColors(containerColor = Red500),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.re_authenticate_now), fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}
