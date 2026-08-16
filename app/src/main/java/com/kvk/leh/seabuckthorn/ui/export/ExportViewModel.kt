package com.kvk.leh.seabuckthorn.ui.export

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kvk.leh.seabuckthorn.data.export.CsvExporter
import com.kvk.leh.seabuckthorn.data.export.ExportStorage
import com.kvk.leh.seabuckthorn.data.export.GeoJsonExporter
import com.kvk.leh.seabuckthorn.data.export.KmlExporter
import com.kvk.leh.seabuckthorn.data.export.XlsxExporter
import com.kvk.leh.seabuckthorn.data.repository.SurveyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed class ExportUiState {
    data object Idle : ExportUiState()
    data object Running : ExportUiState()
    data class Done(val file: File, val label: String) : ExportUiState()
    data class Error(val message: String) : ExportUiState()
}

class ExportViewModel(
    private val context: Context,
    private val repository: SurveyRepository
) : ViewModel() {

    private val _state = MutableStateFlow<ExportUiState>(ExportUiState.Idle)
    val state: StateFlow<ExportUiState> = _state.asStateFlow()

    fun exportCsv() = runExport("CSV") {
        val records = repository.getAllRecordsForExport()
        val file = ExportStorage.timestampedFile(context, "seabuckthorn_surveys", "csv")
        CsvExporter.export(records, file)
        file
    }

    fun exportXlsx() = runExport("Excel workbook") {
        val records = repository.getAllRecordsForExport()
        val file = ExportStorage.timestampedFile(context, "seabuckthorn_surveys", "xlsx")
        XlsxExporter.export(records, file)
        file
    }

    fun exportKml() = runExport("KML") {
        val file = ExportStorage.timestampedFile(context, "seabuckthorn_gps", "kml")
        KmlExporter.export(repository.getAllRecordsForExport().map { it.survey }, file)
        file
    }

    fun exportGeoJson() = runExport("GeoJSON") {
        val file = ExportStorage.timestampedFile(context, "seabuckthorn_gps", "geojson")
        GeoJsonExporter.export(repository.getAllRecordsForExport().map { it.survey }, file)
        file
    }

    private fun runExport(label: String, block: suspend () -> File) {
        viewModelScope.launch {
            _state.value = ExportUiState.Running
            runCatching { withContext(Dispatchers.IO) { block() } }
                .onSuccess { _state.value = ExportUiState.Done(it, label) }
                .onFailure { _state.value = ExportUiState.Error(it.message ?: "Export failed") }
        }
    }

    fun clearState() { _state.value = ExportUiState.Idle }
}
