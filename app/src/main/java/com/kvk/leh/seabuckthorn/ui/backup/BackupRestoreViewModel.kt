package com.kvk.leh.seabuckthorn.ui.backup

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kvk.leh.seabuckthorn.data.backup.BackupManager
import com.kvk.leh.seabuckthorn.data.export.ExportStorage
import com.kvk.leh.seabuckthorn.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed class BackupUiState {
    data object Idle : BackupUiState()
    data object Running : BackupUiState()
    data class BackupDone(val file: File) : BackupUiState()
    data object RestoreDone : BackupUiState()
    data class Error(val message: String) : BackupUiState()
}

class BackupRestoreViewModel(
    private val context: Context,
    private val database: AppDatabase
) : ViewModel() {

    private val _state = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    fun listBackups(): List<File> =
        ExportStorage.backupsDir(context).listFiles { f -> f.extension == "sbtbackup" }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun createBackup() {
        viewModelScope.launch {
            _state.value = BackupUiState.Running
            runCatching { withContext(Dispatchers.IO) { BackupManager.createBackup(context, database) } }
                .onSuccess { _state.value = BackupUiState.BackupDone(it) }
                .onFailure { _state.value = BackupUiState.Error(it.message ?: "Backup failed") }
        }
    }

    /** Restores from [backupFile]. On success the caller MUST restart the app process. */
    fun restoreBackup(backupFile: File) {
        viewModelScope.launch {
            _state.value = BackupUiState.Running
            runCatching {
                withContext(Dispatchers.IO) {
                    AppDatabase.closeAndReset()
                    BackupManager.restoreBackup(context, backupFile)
                }
            }
                .onSuccess { _state.value = BackupUiState.RestoreDone }
                .onFailure { _state.value = BackupUiState.Error(it.message ?: "Restore failed") }
        }
    }

    fun importExternalBackup(sourceUri: android.net.Uri) {
        viewModelScope.launch {
            _state.value = BackupUiState.Running
            runCatching {
                withContext(Dispatchers.IO) {
                    val target = File(ExportStorage.backupsDir(context), "imported_${System.currentTimeMillis()}.sbtbackup")
                    context.contentResolver.openInputStream(sourceUri)?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    } ?: throw IllegalStateException("Could not read the selected file")
                    target
                }
            }
                .onSuccess { _state.value = BackupUiState.Idle }
                .onFailure { _state.value = BackupUiState.Error(it.message ?: "Import failed") }
        }
    }

    fun clearState() { _state.value = BackupUiState.Idle }
}
