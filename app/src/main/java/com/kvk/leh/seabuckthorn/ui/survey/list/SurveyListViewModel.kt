package com.kvk.leh.seabuckthorn.ui.survey.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kvk.leh.seabuckthorn.data.local.entity.SurveyEntity
import com.kvk.leh.seabuckthorn.data.repository.SurveyRepository
import com.kvk.leh.seabuckthorn.domain.SurveyCodeGenerator
import com.kvk.leh.seabuckthorn.domain.model.MaturityStage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class SurveyListFilters(
    val query: String = "",
    val village: String = "",
    val surveyor: String = "",
    val maturityStage: MaturityStage? = null,
    val fromEpochDay: Long? = null,
    val toEpochDay: Long? = null
)

class SurveyListViewModel(
    private val repository: SurveyRepository,
    private val codeGenerator: SurveyCodeGenerator
) : ViewModel() {

    private val _filters = MutableStateFlow(SurveyListFilters())
    val filters: StateFlow<SurveyListFilters> = _filters.asStateFlow()

    val villages: StateFlow<List<String>> =
        repository.observeDistinctVillages().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val surveyors: StateFlow<List<String>> =
        repository.observeDistinctSurveyors().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val surveys: StateFlow<List<SurveyEntity>> = _filters
        .flatMapLatest { f ->
            repository.search(
                query = f.query,
                village = f.village,
                surveyor = f.surveyor,
                maturityStage = f.maturityStage?.name ?: "",
                fromEpochDay = f.fromEpochDay,
                toEpochDay = f.toEpochDay
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateQuery(query: String) { _filters.value = _filters.value.copy(query = query) }
    fun updateVillage(village: String) { _filters.value = _filters.value.copy(village = village) }
    fun updateSurveyor(surveyor: String) { _filters.value = _filters.value.copy(surveyor = surveyor) }
    fun updateMaturityStage(stage: MaturityStage?) { _filters.value = _filters.value.copy(maturityStage = stage) }
    fun updateDateRange(fromEpochDay: Long?, toEpochDay: Long?) {
        _filters.value = _filters.value.copy(fromEpochDay = fromEpochDay, toEpochDay = toEpochDay)
    }
    fun clearFilters() { _filters.value = SurveyListFilters() }

    fun deleteSurvey(surveyId: String) {
        viewModelScope.launch { repository.deleteSurvey(surveyId) }
    }

    fun duplicateSurvey(surveyId: String, onDuplicated: (String) -> Unit) {
        viewModelScope.launch {
            val newId = UUID.randomUUID().toString()
            val newCode = codeGenerator.nextCode()
            val duplicated = repository.duplicateSurvey(surveyId, newId, newCode)
            if (duplicated != null) onDuplicated(newId)
        }
    }
}
