package com.kvkleh.sbtsurvey.ui.map

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kvkleh.sbtsurvey.data.local.SurveyEntity
import com.kvkleh.sbtsurvey.data.repo.SurveyRepository
import com.kvkleh.sbtsurvey.map.BaseMapLayer
import com.kvkleh.sbtsurvey.map.GeoBounds
import com.kvkleh.sbtsurvey.map.MapExportFormat
import com.kvkleh.sbtsurvey.map.MapExportResult
import com.kvkleh.sbtsurvey.map.MapExporter
import com.kvkleh.sbtsurvey.map.TileCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MapUiState(
    val exporting: Boolean = false,
    val pendingShare: MapExportResult? = null,
    val pendingSave: MapExportResult? = null,
    val message: String? = null
)

class SurveyMapViewModel(
    repository: SurveyRepository,
    val tileCache: TileCache,
    private val context: Context
) : ViewModel() {

    private val preferences =
        context.getSharedPreferences("sbt_map", Context.MODE_PRIVATE)

    private val exporter = MapExporter(context, tileCache)

    /** Only records that actually carry coordinates can be plotted. */
    val located: StateFlow<List<SurveyEntity>> = repository.observeWithLocation()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _layer = MutableStateFlow(
        BaseMapLayer.fromId(preferences.getString(KEY_LAYER, null))
    )
    val layer: StateFlow<BaseMapLayer> = _layer.asStateFlow()

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    val tileVersion: StateFlow<Int> get() = tileCache.version
    val tileError: StateFlow<String?> get() = tileCache.error

    fun selectLayer(value: BaseMapLayer) {
        if (_layer.value == value) return
        _layer.value = value
        preferences.edit().putString(KEY_LAYER, value.id).apply()
        // A failure on the previous provider says nothing about the new one.
        tileCache.clearError()
    }

    fun dismissTileError() = tileCache.clearError()

    fun exportMap(format: MapExportFormat, bounds: GeoBounds, share: Boolean) {
        if (_uiState.value.exporting) return
        viewModelScope.launch {
            _uiState.update { it.copy(exporting = true) }
            try {
                val result = exporter.export(format, located.value, _layer.value, bounds)
                _uiState.update {
                    it.copy(
                        exporting = false,
                        pendingShare = if (share) result else null,
                        pendingSave = if (share) null else result
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        exporting = false,
                        message = e.message ?: "The map could not be exported."
                    )
                }
            }
        }
    }

    fun clearTileCache() {
        viewModelScope.launch {
            tileCache.clear()
            _uiState.update { it.copy(message = "Saved map imagery cleared.") }
        }
    }

    fun consumeShare() = _uiState.update { it.copy(pendingShare = null) }

    fun consumeSave() = _uiState.update { it.copy(pendingSave = null) }

    fun reportSaved(savedName: String?, error: String?) {
        val message = error ?: savedName?.let { "Saved $it" } ?: return
        _uiState.update { it.copy(message = message) }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    private companion object {
        const val KEY_LAYER = "basemap_layer"
    }
}
