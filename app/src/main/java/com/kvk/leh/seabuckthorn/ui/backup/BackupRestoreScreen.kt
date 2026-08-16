package com.kvk.leh.seabuckthorn.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kvk.leh.seabuckthorn.ui.components.ConfirmDialog
import com.kvk.leh.seabuckthorn.ui.components.SectionCard
import com.kvk.leh.seabuckthorn.util.AppRestarter
import com.kvk.leh.seabuckthorn.util.DateUtils
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(viewModel: BackupRestoreViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var backups by remember { mutableStateOf(viewModel.listBackups()) }
    var restoreTarget by remember { mutableStateOf<File?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importExternalBackup(it) }
    }

    LaunchedEffect(state) {
        if (state is BackupUiState.BackupDone || state is BackupUiState.Idle) {
            backups = viewModel.listBackups()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup & Restore") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionCard(title = "Backup Data", subtitle = "Creates a single local file containing the full database and all photographs.") {
                Button(onClick = viewModel::createBackup, modifier = Modifier.fillMaxWidth()) { Text("Backup Data") }
            }

            SectionCard(title = "Restore Data", subtitle = "Restoring replaces all current survey data on this device.") {
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Import backup file from device storage")
                }
                if (backups.isEmpty()) {
                    Text("No backups found on this device yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    backups.forEach { file ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(file.name, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        DateUtils.epochMillisToDisplayDateTime(file.lastModified()),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = { restoreTarget = file }) { Text("Restore") }
                            }
                        }
                    }
                }
            }

            when (val s = state) {
                is BackupUiState.Running -> CircularProgressIndicator()
                is BackupUiState.BackupDone -> Text("Backup created: ${s.file.name}", color = MaterialTheme.colorScheme.primary)
                is BackupUiState.Error -> Text("Error: ${s.message}", color = MaterialTheme.colorScheme.error)
                else -> Unit
            }
        }
    }

    restoreTarget?.let { file ->
        ConfirmDialog(
            title = "Restore this backup?",
            message = "All current survey data and photos on this device will be replaced with the contents of ${file.name}. This cannot be undone.",
            confirmLabel = "Restore",
            onConfirm = {
                viewModel.restoreBackup(file)
                restoreTarget = null
            },
            onDismiss = { restoreTarget = null }
        )
    }

    if (state is BackupUiState.RestoreDone) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Restore complete") },
            text = { Text("Your data has been restored. The app must restart now to load it.") },
            confirmButton = {
                TextButton(onClick = { AppRestarter.restart(context) }) { Text("Restart Now") }
            }
        )
    }
}
