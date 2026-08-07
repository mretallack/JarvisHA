package uk.org.retallack.jarvis.ui.entities

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uk.org.retallack.jarvis.data.db.entity.AreaDb
import uk.org.retallack.jarvis.data.db.entity.HaEntityDb
import uk.org.retallack.jarvis.data.repository.EntityRepository
import javax.inject.Inject

data class EntitiesUiState(
    val entities: List<HaEntityDb> = emptyList(),
    val areas: List<AreaDb> = emptyList(),
    val searchQuery: String = "",
    val selectedDomain: String? = null,
    val selectedAreaId: String? = null,
    val isSyncing: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EntitiesViewModel @Inject constructor(
    private val entityRepository: EntityRepository,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedDomain = MutableStateFlow<String?>(null)
    private val _selectedAreaId = MutableStateFlow<String?>(null)
    private val _isSyncing = MutableStateFlow(false)

    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val uiState: StateFlow<EntitiesUiState> = combine(
        _searchQuery.flatMapLatest { query ->
            if (query.isBlank()) {
                entityRepository.allEntities
            } else {
                entityRepository.searchEntities(query)
            }
        },
        entityRepository.allAreas,
        _selectedDomain,
        _selectedAreaId,
        _isSyncing,
    ) { entities, areas, domain, areaId, syncing ->
        val filtered = entities.filter { entity ->
            (domain == null || entity.domain == domain) &&
                (areaId == null || entity.areaId == areaId)
        }
        EntitiesUiState(
            entities = filtered,
            areas = areas,
            searchQuery = _searchQuery.value,
            selectedDomain = domain,
            selectedAreaId = areaId,
            isSyncing = syncing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EntitiesUiState())

    val favourites: StateFlow<List<HaEntityDb>> = entityRepository.favourites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setDomainFilter(domain: String?) {
        _selectedDomain.value = domain
    }

    fun setAreaFilter(areaId: String?) {
        _selectedAreaId.value = areaId
    }

    fun toggleFavourite(entityId: String, currentFavourite: Boolean) {
        viewModelScope.launch {
            entityRepository.setFavourite(entityId, !currentFavourite)
        }
    }

    fun syncFromHa() {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                entityRepository.syncEntitiesFromHa()
                entityRepository.syncAreasFromHa()
                entityRepository.syncAliasesFromHa()
            } finally {
                _isSyncing.value = false
            }
        }
    }
}
