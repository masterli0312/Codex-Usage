package com.codex.quota.widget

import android.content.Context
import androidx.core.content.ContextCompat
import com.codex.quota.R
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.codex.quota.CodexQuotaApplication
import com.codex.quota.domain.model.AccountWithUsage
import com.codex.quota.domain.model.AuthStatus
import com.codex.quota.domain.model.QuotaWindow
import com.codex.quota.domain.model.WidgetThemeMode
import com.codex.quota.ui.MainActivity
import com.codex.quota.ui.util.formatQuotaPercent
import com.codex.quota.ui.util.localizedAccountNickname
import com.codex.quota.ui.util.formatResetCountdown
import com.codex.quota.ui.util.localizedShortPlanName
import com.codex.quota.ui.util.localizedResetDuration
import com.codex.quota.ui.util.primarySubscriberQuotaWindow
import kotlinx.coroutines.runBlocking

class MediumQuotaWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as? CodexQuotaApplication
        val accounts = app?.repository?.getAllAccounts().orEmpty()
        val primaryAccount = accounts.firstOrNull()
        val prefs = try {
            app?.preferencesRepository?.getPreferences()
        } catch (e: Exception) {
            null
        }
        val themeMode = prefs?.widgetThemeMode ?: WidgetThemeMode.DARK_OBSIDIAN

        provideContent {
            GlanceTheme {
                MediumWidgetContent(ContextCompat.getContextForLanguage(context), primaryAccount, themeMode)
            }
        }
    }

    @Composable
    private fun MediumWidgetContent(
        context: Context,
        data: AccountWithUsage?,
        themeMode: WidgetThemeMode
    ) {
        val size = LocalSize.current
        val height = size.height
        val colors = WidgetThemeHelper.getColors(themeMode)

        val intent = if (data != null) {
            Intent(Intent.ACTION_VIEW, Uri.parse("codexquota://account/${data.account.id}"), context, MainActivity::class.java)
        } else {
            Intent(context, MainActivity::class.java)
        }

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(colors.background)
                .cornerRadius(20.dp)
                .padding(14.dp)
                .clickable(actionStartActivity(intent))
        ) {
            if (data == null) {
                Column(
                    modifier = GlanceModifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = context.getString(R.string.codex_quotas),
                        style = TextStyle(color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    )
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Text(
                        text = context.getString(R.string.widget_add_account),
                        style = TextStyle(color = colors.accentBlue, fontSize = 12.sp)
                    )
                }
            } else {
                val primaryWindow = data.usage.primarySubscriberQuotaWindow()
                val remainingPercent = primaryWindow?.remainingPercent?.toInt() ?: data.usage?.remainingPercent?.toInt()
                val usedPercent = primaryWindow?.usedPercent?.toInt()
                    ?: data.usage?.usedPercent?.toInt()
                    ?: remainingPercent?.let { (100 - it).coerceIn(0, 100) }
                val status = data.usage?.status ?: data.account.authStatus
                val resetStr = primaryWindow?.resetAtEpochMs?.let { formatResetCountdown(context, it) }
                    ?: localizedResetDuration(context, data.usage?.rateLimitInfo?.resetRequestsDuration
                        ?: data.usage?.rateLimitInfo?.resetTokensDuration)
                    ?: context.getString(R.string.status_active)
                val primaryLabel = primaryWindow?.window?.let { context.getString(if (it == com.codex.quota.domain.model.QuotaWindow.WEEKLY) R.string.window_weekly else R.string.window_five_hour) } ?: context.getString(R.string.quota_weekly)
                val secondaryQuotaText = if (data.usage?.isWeeklyQuotaExhausted != true && data.usage?.fiveHourRemainingPercent != null && primaryWindow?.window == QuotaWindow.WEEKLY) {
                    context.getString(R.string.widget_5_hour_percent, formatQuotaPercent(data.usage.fiveHourRemainingPercent))
                } else {
                    null
                }

                val dotColor = try {
                    Color(android.graphics.Color.parseColor(data.account.colorHex))
                } catch (e: Exception) {
                    Color(0xFF10B981)
                }

                if (height < 85.dp) {
                    // Ultra-compact 1-row / 4x1 mode
                    Row(
                        modifier = GlanceModifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = GlanceModifier
                                .width(8.dp)
                                .height(8.dp)
                                .background(dotColor)
                                .cornerRadius(4.dp)
                        ) {}

                        Spacer(modifier = GlanceModifier.width(8.dp))

                        Column(modifier = GlanceModifier.defaultWeight()) {
                            Text(
                                text = localizedAccountNickname(context, data.account),
                                maxLines = 1,
                                style = TextStyle(color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            )
                            Spacer(modifier = GlanceModifier.height(4.dp))
                            // Progress bar
                            Box(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .background(colors.progressTrack)
                                    .cornerRadius(2.dp)
                            ) {
                                val fraction = ((remainingPercent ?: 0) / 100f).coerceIn(0f, 1f)
                                if (fraction > 0f) {
                                    Box(
                                        modifier = GlanceModifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .background(colors.accent)
                                            .cornerRadius(2.dp)
                                    ) {}
                                }
                            }
                        }

                        Spacer(modifier = GlanceModifier.width(10.dp))

                        Text(
                            text = if (remainingPercent != null) "$remainingPercent%" else "--%",
                            style = TextStyle(color = colors.accent, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        )
                    }
                } else {
                    // Standard / Large Card Mode (4x2 / 4x3 / 5x2)
                    Column(
                        modifier = GlanceModifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Header Row
                        Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = GlanceModifier
                                    .width(8.dp)
                                    .height(8.dp)
                                    .background(dotColor)
                                    .cornerRadius(4.dp)
                            ) {}

                            Spacer(modifier = GlanceModifier.width(8.dp))

                            Column(modifier = GlanceModifier.defaultWeight()) {
                                Text(
                                    text = localizedAccountNickname(context, data.account),
                                    maxLines = 1,
                                    style = TextStyle(color = colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                )
                                Text(
                                    text = secondaryQuotaText ?: localizedShortPlanName(context, data.account.planType),
                                    maxLines = 1,
                                    style = TextStyle(color = colors.textSecondary, fontSize = 11.sp)
                                )
                            }

                            Spacer(modifier = GlanceModifier.width(8.dp))

                            if (status == AuthStatus.AUTHENTICATION_REQUIRED) {
                                Text(
                                    text = context.getString(R.string.action_sign_in),
                                    style = TextStyle(color = colors.error, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                )
                            } else {
                                Text(
                                    text = if (remainingPercent != null) "$remainingPercent%" else "--%",
                                    style = TextStyle(color = colors.accent, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                                )
                            }
                        }

                        Spacer(modifier = GlanceModifier.height(8.dp))

                        // Progress Bar
                        Box(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .background(colors.progressTrack)
                                .cornerRadius(3.dp)
                        ) {
                            val fraction = ((remainingPercent ?: 0) / 100f).coerceIn(0f, 1f)
                            if (fraction > 0f) {
                                Box(
                                    modifier = GlanceModifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .background(colors.accent)
                                        .cornerRadius(3.dp)
                                ) {}
                            }
                        }

                        Spacer(modifier = GlanceModifier.height(8.dp))

                        // Stats Pill Box
                        Row(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .background(colors.surface)
                                .cornerRadius(10.dp)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = GlanceModifier.defaultWeight()) {
                                Text(
                                    text = if (primaryWindow != null) context.getString(R.string.widget_window_used, primaryLabel.uppercase()) else context.getString(R.string.widget_used),
                                    style = TextStyle(color = colors.textMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = if (usedPercent != null) "$usedPercent%" else "--%",
                                    style = TextStyle(color = colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                )
                            }

                            Column(modifier = GlanceModifier.defaultWeight()) {
                                Text(
                                    text = if (primaryWindow != null) context.getString(R.string.widget_window_reset, primaryLabel.uppercase()) else context.getString(R.string.widget_reset_in),
                                    style = TextStyle(color = colors.textMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = resetStr,
                                    maxLines = 1,
                                    style = TextStyle(color = colors.accentBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                )
                            }
                        }

                        if (height > 150.dp) {
                            Spacer(modifier = GlanceModifier.height(8.dp))
                            Row(
                                modifier = GlanceModifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = context.getString(R.string.codex_quotas),
                                    style = TextStyle(color = colors.textMuted, fontSize = 10.sp)
                                )
                                Spacer(modifier = GlanceModifier.defaultWeight())
                                Text(
                                    text = if (data.usage?.isStale == true) context.getString(R.string.widget_stale_cache) else context.getString(R.string.widget_synced),
                                    style = TextStyle(
                                        color = if (data.usage?.isStale == true) ColorProvider(Color(0xFFF59E0B)) else colors.textMuted,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

class MediumQuotaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MediumQuotaWidget()
}
