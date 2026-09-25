package com.codex.quota.ui.feature.accountdetail
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.quota.domain.model.AuthStatus
import com.codex.quota.domain.model.weeklyQuotaPacing
import com.codex.quota.ui.components.CircularQuotaGauge
import com.codex.quota.ui.components.QuotaPacingBadge
import com.codex.quota.ui.components.RelativeTimeText
import com.codex.quota.ui.components.StatusBadge
import com.codex.quota.ui.util.formatQuotaPercent
import com.codex.quota.ui.util.formatQuotaSummary
import com.codex.quota.ui.util.formatResetCountdown
import com.codex.quota.ui.util.isApiKeyQuotaUsage
import com.codex.quota.ui.util.localizedWindowLabel
import com.codex.quota.ui.util.localizedPlanName
import com.codex.quota.ui.util.primarySubscriberQuotaWindow
import com.codex.quota.ui.util.subscriberQuotaWindows
import com.codex.quota.ui.theme.Amber500
import com.codex.quota.ui.theme.Red500
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDetailScreen(
    viewModel: AccountDetailViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accountWithUsage by viewModel.accountState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val uiMessage by viewModel.uiMessage.collectAsState()
    val accountDeleted by viewModel.accountDeleted.collectAsState()
    val appLocale = LocalConfiguration.current.locales[0]
    val context = LocalContext.current

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val isBannerDismissed by viewModel.isBannerDismissed.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showReauthDialog by remember { mutableStateOf(false) }
    var showDatePickerDialog by remember { mutableStateOf(false) }

    LaunchedEffect(accountDeleted) {
        if (accountDeleted) {
            onNavigateBack()
        }
    }

    LaunchedEffect(uiMessage) {
        uiMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearUiMessage()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                androidx.compose.material3.Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(16.dp)
                )
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = accountWithUsage?.account?.nickname ?: stringResource(R.string.account_details),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 8.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.refresh_usage_data),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        val data = accountWithUsage
        if (data == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val account = data.account
            val usage = data.usage
            val status = usage?.status ?: account.authStatus
            val subscriberWindows = usage.subscriberQuotaWindows(account.planType)
            val primarySubscriberWindow = usage.primarySubscriberQuotaWindow(account.planType)
            val remainingPercent = primarySubscriberWindow?.remainingPercent ?: usage?.remainingPercent
            val usedPercent = primarySubscriberWindow?.usedPercent
                ?: usage?.usedPercent
                ?: (remainingPercent?.let { (100.0 - it).coerceIn(0.0, 100.0) })
            val effectiveRenewalEpochMs = account.customRenewalDateEpochMs ?: usage?.subscriptionRenewalEpochMs

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Subscription Renewal Setup Banner (if not yet configured and not dismissed)
                if (effectiveRenewalEpochMs == null && !isBannerDismissed) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Event,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = stringResource(R.string.track_subscription_renewal),
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            viewModel.dismissRenewalBanner()
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar(context.getString(R.string.you_can_set_your_renewal_date_anytime_by_tapping_the_edit_button))
                                            }
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = stringResource(R.string.dismiss),
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = stringResource(R.string.set_when_your_subscription_renews_each_month_to_track_days_remain),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                    lineHeight = 18.sp
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Button(
                                    onClick = { showDatePickerDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarMonth,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.set_renewal_date), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                item {
                    // Main Quota Metric Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            StatusBadge(status = status)
                            Spacer(modifier = Modifier.height(16.dp))

                            CircularQuotaGauge(
                                remainingPercent = remainingPercent,
                                size = 160.dp,
                                strokeWidth = 14.dp
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            // 3-Metric Summary Box
                            val resetStr = if (usage.isApiKeyQuotaUsage(account.planType)) {
                                usage?.rateLimitInfo?.resetRequestsDuration
                                    ?: usage?.rateLimitInfo?.resetTokensDuration
                                    ?: stringResource(R.string.status_active)
                            } else {
                                formatResetCountdown(context, primarySubscriberWindow?.resetAtEpochMs)
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = if (subscriberWindows.isNotEmpty()) stringResource(R.string.weekly) else stringResource(R.string.remaining),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (remainingPercent != null) "${remainingPercent.toInt()}%" else "--%",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(28.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                )

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = if (subscriberWindows.isNotEmpty()) stringResource(R.string.weekly_used) else stringResource(R.string.used),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (usedPercent != null) "${usedPercent.toInt()}%" else "--%",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(28.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                )

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = if (subscriberWindows.isNotEmpty()) stringResource(R.string.weekly_reset) else stringResource(R.string.reset_in),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = resetStr,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }

                            val pacing = usage.weeklyQuotaPacing(account.planType)
                            if (pacing != null) {
                                Spacer(modifier = Modifier.height(14.dp))
                                QuotaPacingBadge(
                                    pacing = pacing,
                                    compact = false,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            if (subscriberWindows.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                subscriberWindows.forEach { window ->
                                    DetailMetricRow(
                                        label = localizedWindowLabel(context, window.window),
                                        value = formatQuotaSummary(context, window)
                                    )
                                    DetailMetricRow(
                                        label = stringResource(R.string.window_reset, localizedWindowLabel(context, window.window)),
                                        value = formatResetCountdown(context, window.resetAtEpochMs)
                                    )
                                }
                            }

                            if (usage?.bankedResets != null) {
                                val expiry = usage.bankedResetExpiresAtEpochMs
                                    ?.takeIf { usage.bankedResets > 0 }
                                    ?.let { epochMs ->
                                        SimpleDateFormat(stringResource(R.string.date_time_pattern), appLocale)
                                            .apply { timeZone = TimeZone.getDefault() }
                                            .format(Date(epochMs))
                                    }
                                val expiryPrefix = if (usage.bankedResets > 1) {
                                    stringResource(R.string.next_expiry)
                                } else {
                                    stringResource(R.string.expires)
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                DetailMetricRow(
                                    label = stringResource(R.string.banked_usage_resets),
                                    value = buildString {
                                        append(context.resources.getQuantityString(R.plurals.count_available, usage.bankedResets, usage.bankedResets))
                                        if (expiry != null) append("\n" + context.getString(R.string.expiry_line, expiryPrefix, expiry))
                                    }
                                )
                            }
                        }
                    }
                }

                // Subscription & Renewal Card (shown if configured)
                if (effectiveRenewalEpochMs != null) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.subscription_renewal),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    IconButton(
                                        onClick = { showDatePickerDialog = true },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = stringResource(R.string.edit_renewal_date),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                val renewalDate = SimpleDateFormat(stringResource(R.string.date_long_pattern), appLocale)
                                    .format(Date(effectiveRenewalEpochMs))

                                val daysLeft = ((effectiveRenewalEpochMs - System.currentTimeMillis()) / (1000L * 60 * 60 * 24)).coerceAtLeast(0)

                                DetailMetricRow(
                                    label = stringResource(R.string.plan_type),
                                    value = "${localizedPlanName(context, account.planType)} (${when (usage?.billingPeriod?.lowercase()) {
                                        "monthly" -> stringResource(R.string.billing_monthly)
                                        "yearly", "annual" -> stringResource(R.string.billing_yearly)
                                        else -> usage?.billingPeriod ?: stringResource(R.string.billing_monthly)
                                    }})"
                                )

                                DetailMetricRow(
                                    label = stringResource(R.string.renewal_expiration_date),
                                    value = pluralStringResource(R.plurals.renewal_in_days, daysLeft.toInt(), renewalDate, daysLeft)
                                )

                                DetailMetricRow(
                                    label = stringResource(R.string.auto_renewal_status),
                                    value = if (usage?.willAutoRenew == false) {
                                        stringResource(R.string.manual_renewal_cancels_at_period_end)
                                    } else {
                                        stringResource(R.string.active_subscription_will_auto_renew_on_next_cycle)
                                    }
                                )
                            }
                        }
                    }
                }

                // Rate Limit Dimensions Card
                item {
                    val rateLimits = usage?.rateLimitInfo
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = stringResource(R.string.rate_limit_dimensions),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            val numberFormat = NumberFormat.getNumberInstance(appLocale)

                            DetailMetricRow(
                                label = stringResource(R.string.requests_per_minute_rpm),
                                value = if (rateLimits?.limitRequests != null && rateLimits.remainingRequests != null) {
                                    "${numberFormat.format(rateLimits.remainingRequests)} / ${numberFormat.format(rateLimits.limitRequests)}"
                                } else stringResource(R.string.available_standard)
                            )

                            DetailMetricRow(
                                label = stringResource(R.string.tokens_per_minute_tpm),
                                value = if (rateLimits?.limitTokens != null && rateLimits.remainingTokens != null) {
                                    "${numberFormat.format(rateLimits.remainingTokens)} / ${numberFormat.format(rateLimits.limitTokens)}"
                                } else stringResource(R.string.available_standard)
                            )

                            if (usage?.remainingCredits != null) {
                                DetailMetricRow(
                                    label = stringResource(R.string.remaining_balance_credits),
                                    value = "$${String.format(Locale.US, "%.2f", usage.remainingCredits)}"
                                )
                            }
                        }
                    }
                }

                // Account Metadata & Security Card (Omits "Added to Codex Quota")
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.account_details),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                IconButton(onClick = { showEditDialog = true }) {
                                    Icon(imageVector = Icons.Default.Edit, contentDescription = stringResource(R.string.edit_account))
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            DetailMetricRow(label = stringResource(R.string.nickname), value = account.nickname)
                            if (account.email != null) {
                                DetailMetricRow(label = stringResource(R.string.email_address), value = account.email)
                            }
                            if (account.organizationId != null) {
                                DetailMetricRow(label = stringResource(R.string.organization_id), value = account.organizationId)
                            }
                            if (usage?.accountCreatedEpochMs != null) {
                                DetailMetricRow(
                                    label = stringResource(R.string.openai_account_created),
                                    value = SimpleDateFormat(stringResource(R.string.date_long_pattern), appLocale).format(Date(usage.accountCreatedEpochMs))
                                )
                            }
                            DetailMetricRow(
                                label = stringResource(R.string.key_encryption_storage),
                                value = stringResource(R.string.hardware_backed_android_keystore_aes_256_gcm)
                            )
                        }
                    }
                }

                // Sync Log Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = stringResource(R.string.sync_status),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stringResource(R.string.last_successful_sync), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                RelativeTimeText(epochMs = account.lastSuccessfulSyncEpochMs)
                            }

                            if (usage?.errorMessage != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.diagnostic_message, usage.errorMessage),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (status == AuthStatus.AUTHENTICATION_REQUIRED) Red500 else Amber500
                                )
                            }
                        }
                    }
                }

                // Action Buttons
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { showReauthDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Key, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.re_authenticate_credentials))
                        }

                        OutlinedButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Red500)
                        ) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.remove_account))
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }

            // Material 3 Date Picker Dialog for Subscription Renewal
            if (showDatePickerDialog) {
                val datePickerState = rememberDatePickerState(
                    initialSelectedDateMillis = effectiveRenewalEpochMs ?: (System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000)
                )

                DatePickerDialog(
                    onDismissRequest = { showDatePickerDialog = false },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val selectedDate = datePickerState.selectedDateMillis
                                if (selectedDate != null) {
                                    viewModel.updateRenewalDate(selectedDate)
                                }
                                showDatePickerDialog = false
                            }
                        ) {
                            Text(stringResource(R.string.save_date))
                        }
                    },
                    dismissButton = {
                        Row {
                            if (effectiveRenewalEpochMs != null) {
                                TextButton(
                                    onClick = {
                                        viewModel.updateRenewalDate(null)
                                        showDatePickerDialog = false
                                    }
                                ) {
                                    Text(stringResource(R.string.clear), color = Red500)
                                }
                            }
                            TextButton(onClick = { showDatePickerDialog = false }) {
                                Text(stringResource(R.string.action_cancel))
                            }
                        }
                    }
                ) {
                    DatePicker(state = datePickerState)
                }
            }

            // Edit Nickname & Color Dialog
            if (showEditDialog) {
                EditAccountDialog(
                    currentNickname = account.nickname,
                    currentColorHex = account.colorHex,
                    currentRenewalEpochMs = effectiveRenewalEpochMs,
                    onOpenDatePicker = {
                        showEditDialog = false
                        showDatePickerDialog = true
                    },
                    onDismiss = { showEditDialog = false },
                    onConfirm = { name, color ->
                        viewModel.updateAccountDetails(name, color, account.customRenewalDateEpochMs)
                        showEditDialog = false
                    }
                )
            }

            // Re-authenticate Dialog
            if (showReauthDialog) {
                ReauthDialog(
                    onDismiss = { showReauthDialog = false },
                    onConfirm = { newKey ->
                        viewModel.reauthenticate(newKey)
                        showReauthDialog = false
                    }
                )
            }

            // Delete Account Confirmation Dialog
            if (showDeleteDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    title = { Text(stringResource(R.string.remove_account_confirm_title)) },
                    text = { Text(stringResource(R.string.remove_account_message, account.nickname)) },
                    confirmButton = {
                        Button(
                            onClick = {
                                showDeleteDialog = false
                                viewModel.deleteAccount()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Red500)
                        ) {
                            Text(stringResource(R.string.remove), color = Color.White)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteDialog = false }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun DetailMetricRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun EditAccountDialog(
    currentNickname: String,
    currentColorHex: String,
    currentRenewalEpochMs: Long?,
    onOpenDatePicker: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var nickname by remember { mutableStateOf(currentNickname) }
    var selectedColor by remember { mutableStateOf(currentColorHex) }

    val colorOptions = listOf(
        "#10B981", "#3B82F6", "#8B5CF6", "#EC4899",
        "#F59E0B", "#06B6D4", "#6366F1", "#84CC16"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_account)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text(stringResource(R.string.nickname)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(stringResource(R.string.theme_color), style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    colorOptions.forEach { hex ->
                        val color = Color(android.graphics.Color.parseColor(hex))
                        val isSelected = selectedColor.equals(hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable { selectedColor = hex }
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                        .align(Alignment.Center)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.subscription_renewal_label), style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = if (currentRenewalEpochMs != null) {
                                SimpleDateFormat(stringResource(R.string.date_long_pattern), LocalConfiguration.current.locales[0]).format(Date(currentRenewalEpochMs))
                            } else stringResource(R.string.not_set),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onOpenDatePicker) {
                        Text(if (currentRenewalEpochMs != null) stringResource(R.string.change) else stringResource(R.string.set_date))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (nickname.isNotBlank()) onConfirm(nickname.trim(), selectedColor) },
                enabled = nickname.isNotBlank()
            ) {
                Text(stringResource(R.string.save_changes))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun ReauthDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var keyInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.re_authenticate_credentials)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.enter_a_fresh_openai_api_key_sk_or_session_jwt_token_for_this_acc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    label = { Text(stringResource(R.string.api_key_or_token)) },
                    placeholder = { Text(stringResource(R.string.api_key_example)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (keyInput.isNotBlank()) onConfirm(keyInput.trim()) },
                enabled = keyInput.isNotBlank()
            ) {
                Text(stringResource(R.string.update_key))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
