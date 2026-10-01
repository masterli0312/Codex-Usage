package com.codex.quota.ui.feature.credithistory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.codex.quota.CodexQuotaApplication
import com.codex.quota.R
import com.codex.quota.domain.model.AuthStatus
import com.codex.quota.ui.util.localizedAccountNickname
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditHistoryScreen(app: CodexQuotaApplication, accountId: String, onBack: () -> Unit) {
    val entries by remember(accountId) { app.database.creditHistoryDao().observe(accountId) }.collectAsState(initial = null)
    val account by remember(accountId) { app.repository.observeAccount(accountId) }.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var refreshing by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val numbers = remember(locale) { NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 6 } }
    val dates = remember(locale) { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale) }
    Scaffold(topBar = { TopAppBar(title = { Column {
        Text(stringResource(R.string.credit_history_title))
        account?.let { Text(localizedAccountNickname(context, it.account), style = MaterialTheme.typography.labelSmall) }
    } }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
        actions = { IconButton(enabled = !refreshing && account != null, onClick = {
            refreshing = true
            scope.launch {
                try {
                    val result = app.repository.refreshAccount(accountId)
                    if (result.isFailure || result.getOrNull()?.status != AuthStatus.AUTHENTICATED)
                        snackbar.showSnackbar(context.getString(R.string.error_refresh_account))
                } catch (error: CancellationException) { throw error }
                catch (_: Exception) { snackbar.showSnackbar(context.getString(R.string.error_refresh_account)) }
                finally { refreshing = false }
            }
        }) { if (refreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Icon(Icons.Default.Refresh, stringResource(R.string.refresh_usage_data)) } }) },
        snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.official_credit), style = MaterialTheme.typography.titleSmall)
                    Text(account?.usage?.remainingCredits?.takeIf { it.isFinite() }?.let(numbers::format) ?: stringResource(R.string.value_unavailable), style = MaterialTheme.typography.headlineMedium)
                    Text(stringResource(R.string.credit_history_explanation), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } }
            }
            if (entries == null) item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            else if (entries!!.isEmpty()) item { Text(stringResource(R.string.credit_history_empty), style = MaterialTheme.typography.bodyMedium) }
            items(entries.orEmpty(), key = { it.id }) { entry ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(dates.format(Date(entry.observedAtEpochMs)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(numbers.format(entry.balance), style = MaterialTheme.typography.titleMedium)
                            Text(entry.change?.let { (if (it > 0) "+" else "") + numbers.format(it) } ?: stringResource(R.string.credit_history_baseline),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
