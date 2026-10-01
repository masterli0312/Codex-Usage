package com.codex.quota.ui.feature.dashboard

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.codex.quota.R
import com.codex.quota.domain.model.AccountWithUsage
import com.codex.quota.ui.util.localizedAccountNickname
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountManagementSheet(selected: AccountWithUsage, accounts: List<AccountWithUsage>,
    viewModel: DashboardViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var name by remember(selected.account.id) { mutableStateOf(localizedAccountNickname(context, selected.account)) }
    var sorting by remember { mutableStateOf(false) }
    val order = remember { mutableStateListOf<AccountWithUsage>().apply { addAll(accounts) } }
    val saving by viewModel.isSaving.collectAsState()
    LaunchedEffect(selected.account.id) { viewModel.clearSaveError() }
    val saveError by viewModel.saveError.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !saving })
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = rememberCoroutineScope()
    val height = with(LocalDensity.current) { 64.dp.toPx() }
    val currentSaving by rememberUpdatedState(saving)
    fun move(id: String, direction: Int) {
        if (currentSaving) return
        val index = order.indexOfFirst { it.account.id == id }
        val target = index + direction
        if (index >= 0 && target in order.indices) order.add(target, order.removeAt(index))
    }
    val mover by rememberUpdatedState<(String, Int) -> Unit>(::move)
    ModalBottomSheet(sheetState = sheetState, onDismissRequest = { if (!saving) onDismiss() }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(if (sorting) R.string.account_order else R.string.manage_accounts), style = MaterialTheme.typography.titleLarge)
            if (!sorting) {
                OutlinedTextField(value = name, onValueChange = { if (it.length <= 40) name = it },
                    singleLine = true, enabled = !saving, label = { Text(stringResource(R.string.nickname)) }, modifier = Modifier.fillMaxWidth())
                Button(onClick = { viewModel.rename(selected.account.id, name, onDismiss) },
                    enabled = name.isNotBlank() && !saving, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_save))
                }
                TextButton(onClick = { sorting = true }, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Reorder, contentDescription = null)
                    Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.account_order))
                }
            } else {
                Text(stringResource(R.string.account_order_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp), state = listState) {
                    itemsIndexed(order, key = { _, item -> item.account.id }) { index, item ->
                        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DragHandle, contentDescription = stringResource(R.string.drag_account),
                                modifier = Modifier.size(48.dp).pointerInput(item.account.id) {
                                    var distance = 0f
                                    detectDragGestures(onDragStart = { distance = 0f }, onDragEnd = { distance = 0f },
                                        onDragCancel = { distance = 0f }) { change, amount ->
                                        if (!currentSaving) {
                                            change.consume(); distance += amount.y
                                            if (kotlin.math.abs(distance) >= height) {
                                                val direction = if (distance > 0) 1 else -1
                                                mover(item.account.id, direction); distance -= height * direction
                                            }
                                            val visible = listState.layoutInfo.visibleItemsInfo
                                            val currentIndex = order.indexOfFirst { it.account.id == item.account.id }
                                            if ((amount.y > 0 && currentIndex == visible.lastOrNull()?.index) ||
                                                (amount.y < 0 && currentIndex == visible.firstOrNull()?.index)) {
                                                scope.launch { listState.scrollBy(amount.y) }
                                            }
                                        }
                                    }
                                }.padding(12.dp))
                            Text(localizedAccountNickname(context, item.account), Modifier.weight(1f), maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            IconButton(onClick = { move(item.account.id, -1) }, enabled = index > 0 && !saving) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = stringResource(R.string.move_account_up))
                            }
                            IconButton(onClick = { move(item.account.id, 1) }, enabled = index < order.lastIndex && !saving) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = stringResource(R.string.move_account_down))
                            }
                        }
                    }
                }
                Button(onClick = { viewModel.reorder(order.map { it.account.id }, onDismiss) }, enabled = !saving,
                    modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_save)) }
            }
            saveError?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}
