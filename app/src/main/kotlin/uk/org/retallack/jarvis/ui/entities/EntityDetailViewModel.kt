package uk.org.retallack.jarvis.ui.entities

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import uk.org.retallack.jarvis.data.db.entity.AliasDb
import uk.org.retallack.jarvis.data.db.entity.HaEntityDb
import uk.org.retallack.jarvis.data.repository.EntityRepository
import javax.inject.Inject

@HiltViewModel
class EntityDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val entityRepository: EntityRepository,
) : ViewModel() {

    private val entityId: String = savedStateHandle.get<String>("entityId") ?: ""

    private val _entity = MutableStateFlow<HaEntityDb?>(null)
    val entity: StateFlow<HaEntityDb?> = _entity.asStateFlow()

    val aliases: StateFlow<List<AliasDb>> = entityRepository.getAliasesForEntity(entityId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadEntity()
    }

    private fun loadEntity() {
        viewModelScope.launch {
            _entity.value = entityRepository.getEntity(entityId)
        }
    }

    fun addAlias(alias: String) {
        viewModelScope.launch {
            entityRepository.addAlias(entityId, alias)
        }
    }

    fun removeAlias(id: Long) {
        viewModelScope.launch {
            entityRepository.removeAlias(id)
        }
    }

    fun pushAliasesToHa() {
        viewModelScope.launch {
            val currentAliases = aliases.value.map { it.alias }
            entityRepository.pushAliasToHa(entityId, currentAliases)
        }
    }
}
