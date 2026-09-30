package com.codex.quota.ui.feature.settings

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import com.codex.quota.R
import com.codex.quota.notifications.task.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Composable
fun TaskNotificationSettingsPanel() {
    val context = LocalContext.current
    val store = remember { TaskNotificationStore(context) }
    val settings by store.settings.collectAsState(initial = store.read())
    val state by TaskNotificationConnection.state.collectAsState()
    val scope = rememberCoroutineScope()
    var editEndpoint by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf(false) }
    var exportError by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var startError by remember { mutableStateOf(false) }
    val switchDescription = stringResource(R.string.task_notification_title)
    LaunchedEffect(Unit) { store.ensureEndpoint() }
    fun enable() {
        store.setEnabled(true)
        startError = !TaskCompletionService.startIfEnabled(context)
        if (startError) store.setEnabled(false)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) enable() else startError = true
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.task_notification_title), fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.task_notification_summary), style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = settings.enabled, onCheckedChange = { enabled ->
            if (!enabled) {
                store.setEnabled(false)
                TaskCompletionService.stop(context)
            } else if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                enable()
            } else if (Build.VERSION.SDK_INT >= 33) {
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else { startError = true }
        }, enabled = TaskNotificationProtocol.normalizeEndpoint(settings.endpoint) != null,
            modifier = Modifier.semantics { contentDescription = switchDescription })
    }
    val status = when {
        !settings.enabled -> R.string.task_connection_off
        state == TaskConnectionState.CONNECTED -> R.string.task_connected
        state == TaskConnectionState.RETRYING -> R.string.task_reconnecting
        else -> R.string.task_connecting
    }
    Text(stringResource(status), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
    if (startError) Text(stringResource(R.string.task_start_failed), color = MaterialTheme.colorScheme.error)
    HorizontalDivider(Modifier.padding(vertical = 12.dp))
    Text(stringResource(R.string.task_pairing_title), fontWeight = FontWeight.Bold)
    Text(stringResource(R.string.task_pairing_steps), style = MaterialTheme.typography.bodyMedium)
    Button(onClick = {
        exporting = true
        exportError = false
        scope.launch {
            try {
                val zip = withContext(Dispatchers.IO) { exportInstaller(context, settings.endpoint) }
                val uri = FileProvider.getUriForFile(context, context.packageName + ".task-notification-files", zip)
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, context.getString(R.string.task_export_installer)))
            } catch (_: Exception) { exportError = true }
            finally { exporting = false }
        }
    }, enabled = !exporting && settings.endpoint.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.task_export_installer))
    }
    if (exportError) Text(stringResource(R.string.task_export_failed), color = MaterialTheme.colorScheme.error)
    OutlinedButton(onClick = {
        val clip = ClipData.newPlainText(context.getString(R.string.task_pairing_address), settings.endpoint)
        if (Build.VERSION.SDK_INT >= 33) clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
    }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.task_copy_address)) }
    OutlinedButton(onClick = {
        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.task_background_settings)) }
    Text(stringResource(R.string.task_background_detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    HorizontalDivider(Modifier.padding(vertical = 12.dp))
    Text(stringResource(R.string.task_privacy_detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    TextButton(onClick = { input = settings.endpoint; editEndpoint = true; inputError = false }) {
        Text(stringResource(R.string.task_change_address))
    }
    if (editEndpoint) AlertDialog(onDismissRequest = { editEndpoint = false },
        title = { Text(stringResource(R.string.task_pairing_address)) },
        text = { Column {
            OutlinedTextField(value = input, onValueChange = { input = it; inputError = false }, singleLine = true,
                isError = inputError, label = { Text(stringResource(R.string.task_pairing_address)) })
            if (inputError) Text(stringResource(R.string.task_address_invalid), color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { TextButton(onClick = {
            val endpoint = TaskNotificationProtocol.normalizeEndpoint(input)
            if (endpoint == null) inputError = true else {
                TaskCompletionService.stop(context)
                store.saveEndpoint(endpoint)
                editEndpoint = false
            }
        }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = { editEndpoint = false }) { Text(stringResource(R.string.action_cancel)) } })
}

private fun exportInstaller(context: Context, endpoint: String): File {
    require(TaskNotificationProtocol.normalizeEndpoint(endpoint) != null)
    val dir = File(context.cacheDir, "task-notifications").apply { mkdirs() }
    val output = File(dir, "Codex-Usage-Windows-Setup.zip")
    ZipOutputStream(output.outputStream()).use { zip ->
        for (name in listOf("Install.ps1", "notify.cjs", "setup.cmd")) {
            zip.putNextEntry(ZipEntry(name))
            context.assets.open("task-notifications/$name").use { it.copyTo(zip) }
            zip.closeEntry()
        }
        zip.putNextEntry(ZipEntry("pairing.json"))
        zip.write(buildJsonObject { put("endpoint", endpoint) }.toString().toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }
    return output
}
