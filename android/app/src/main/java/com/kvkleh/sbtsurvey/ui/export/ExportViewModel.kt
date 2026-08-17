package com.kvkleh.sbtsurvey.ui.export

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kvkleh.sbtsurvey.Graph
import com.kvkleh.sbtsurvey.data.db.SurveyEntity
import com.kvkleh.sbtsurvey.data.export.ExportFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ExportUiState(
    val savedCount: Int = 0,
    val draftCount: Int = 0,
    val includeDrafts: Boolean = false,
    val busy: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val photoFolder: String = "",
    val galleryFolder: String = "",
    /** Format chosen on the share screen. */
    val format: ExportFormat = ExportFormat.CSV,
    /** Whether sharing sends a ZIP with the photographs alongside the data. */
    val includePhotos: Boolean = false
) {
    val exportCount: Int get() = if (includeDrafts) savedCount + draftCount else savedCount
}

class ExportViewModel : ViewModel() {

    private val repository = Graph.repository
    private val exportManager = Graph.exportManager

    private val _uiState = MutableStateFlow(
        ExportUiState(
            photoFolder = Graph.photoStore.photoDir.absolutePath,
            galleryFolder = Graph.repository.galleryFolderLabel
        )
    )
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeSaved().collect { saved ->
                _uiState.value = _uiState.value.copy(savedCount = saved.size)
            }
        }
        viewModelScope.launch {
            repository.observeDraft().collect { draft ->
                _uiState.value = _uiState.value.copy(draftCount = if (draft == null) 0 else 1)
            }
        }
    }

    fun setIncludeDrafts(include: Boolean) {
        _uiState.value = _uiState.value.copy(includeDrafts = include)
    }

    fun setFormat(format: ExportFormat) {
        _uiState.value = _uiState.value.copy(format = format)
    }

    fun setIncludePhotos(include: Boolean) {
        _uiState.value = _uiState.value.copy(includePhotos = include)
    }

    /**
     * Builds whatever the share screen is currently set to - one data file, or a
     * ZIP of the data and every photograph - and hands it to the system chooser.
     */
    fun shareCurrentSelection(onReady: (Intent) -> Unit) {
        val state = _uiState.value
        if (state.busy) return
        _uiState.value = state.copy(busy = true, statusMessage = null, errorMessage = null)
        viewModelScope.launch {
            runCatching {
                val data = rows()
                val result = if (state.includePhotos) {
                    exportManager.writeBundleToCache(data, state.format)
                } else {
                    exportManager.writeToCache(data, state.format)
                }
                val file = result.file ?: error("The file was not created.")
                Triple(result.recordCount, file.name, exportManager.shareIntent(file, state.format))
            }.fold(
                onSuccess = { (count, fileName, intent) ->
                    _uiState.value = _uiState.value.copy(
                        busy = false,
                        statusMessage = "$count record(s) prepared as $fileName. " +
                            "Choose an app to send it with."
                    )
                    onReady(intent)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        busy = false,
                        errorMessage = error.message ?: "The file could not be prepared."
                    )
                }
            )
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(statusMessage = null, errorMessage = null)
    }

    private suspend fun rows(): List<SurveyEntity> {
        val all = repository.getAllForExport()
        return if (_uiState.value.includeDrafts) all else all.filterNot { it.isDraft }
    }

    fun fileNameFor(format: ExportFormat): String = exportManager.suggestedFileName(format)

    /** Writes into a location chosen with the system file picker. */
    fun exportToUri(format: ExportFormat, uri: Uri) {
        if (_uiState.value.busy) return
        _uiState.value = _uiState.value.copy(busy = true, statusMessage = null, errorMessage = null)
        viewModelScope.launch {
            runCatching {
                val data = rows()
                exportManager.writeToUri(data, format, uri)
            }.fold(
                onSuccess = { result ->
                    _uiState.value = _uiState.value.copy(
                        busy = false,
                        statusMessage = "${result.recordCount} record(s) written as " +
                            "${format.label} to the location you chose."
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        busy = false,
                        errorMessage = error.message ?: "The file could not be written."
                    )
                }
            )
        }
    }

    /** Builds the file and hands it to the Android share sheet. */
    fun shareExport(format: ExportFormat, onReady: (Intent) -> Unit) {
        if (_uiState.value.busy) return
        _uiState.value = _uiState.value.copy(busy = true, statusMessage = null, errorMessage = null)
        viewModelScope.launch {
            runCatching {
                val data = rows()
                val result = exportManager.writeToCache(data, format)
                val file = result.file ?: error("The export file was not created.")
                result.recordCount to exportManager.shareIntent(file, format)
            }.fold(
                onSuccess = { (count, intent) ->
                    _uiState.value = _uiState.value.copy(
                        busy = false,
                        statusMessage = "$count record(s) ready to share as ${format.label}."
                    )
                    onReady(intent)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        busy = false,
                        errorMessage = error.message ?: "The file could not be prepared."
                    )
                }
            )
        }
    }
}
