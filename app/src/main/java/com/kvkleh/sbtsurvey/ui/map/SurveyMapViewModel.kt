package com.kvkleh.sbtsurvey.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kvkleh.sbtsurvey.data.local.SurveyEntity
import com.kvkleh.sbtsurvey.data.repo.SurveyRepository
import com.kvkleh.sbtsurvey.map.TileCache
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SurveyMapViewModel(
    repository: SurveyRepository,
    val tileCache: TileCache
) : ViewModel() {

    /** Only records that actually carry coordinates can be plotted. */
    val located: StateFlow<List<SurveyEntity>> = repository.observeWithLocation()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tileVersion: StateFlow<Int> get() = tileCache.version

    fun clearTileCache() {
        viewModelScope.launch { tileCache.clear() }
    }
}
