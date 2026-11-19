package app.spliitclient.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.spliitclient.R
import app.spliitclient.data.GroupSummary
import app.spliitclient.data.Repository
import app.spliitclient.data.SavedGroup
import app.spliitclient.data.UserBalance
import app.spliitclient.util.Links
import app.spliitclient.util.UiText
import app.spliitclient.util.uiText
import app.spliitclient.util.attempt
import app.spliitclient.util.userMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeState(
    val refreshing: Boolean = false,
    val loaded: Boolean = false,
    val summaries: Map<String, GroupSummary> = emptyMap(),
    /** Gruppi già chiesti al server (per distinguere "non trovato" da "non ancora caricato"). */
    val checkedIds: Set<String> = emptySet(),
    val myBalances: Map<String, UserBalance> = emptyMap(),
    val error: UiText? = null,
    val adding: Boolean = false,
    val addError: UiText? = null,
)

class HomeViewModel(private val repo: Repository) : ViewModel() {

    val groups: StateFlow<List<SavedGroup>> =
        combine(repo.store.groups, repo.store.serverUrl) { list, server ->
            repo.store.sortGroups(list.filter { it.server == server })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repo.store.visibleGroups())

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    private var refreshJob: Job? = null
    private var dirty = false
    private var lastServer = repo.store.serverUrl.value

    init {
        refresh()
        // Una modifica in un gruppo rende "vecchi" i saldi della home: li ricarichiamo
        // solo quando torni qui, non subito (niente richieste inutili).
        viewModelScope.launch { repo.changes.collect { dirty = true } }
    }

    /** Chiamata quando la home torna visibile. */
    fun onVisible() {
        val server = repo.store.serverUrl.value
        val unknown = repo.store.visibleGroups().any { it.id !in _state.value.checkedIds }
        if (dirty || server != lastServer || unknown) {
            if (server != lastServer) _state.update { HomeState() }
            refresh()
        }
    }

    /** Un'unica richiesta per tutti i nomi dei gruppi + una per i tuoi saldi. Niente polling. */
    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            dirty = false
            lastServer = repo.store.serverUrl.value
            val saved = repo.store.visibleGroups()
            if (saved.isEmpty()) {
                _state.update { it.copy(refreshing = false, loaded = true, error = null) }
                return@launch
            }
            _state.update { it.copy(refreshing = true, error = null) }
            attempt { repo.api.listGroups(saved.map { it.id }) }
                .onSuccess { summaries ->
                    repo.store.updateNames(summaries.associate { it.id to it.name })
                    _state.update {
                        it.copy(
                            summaries = summaries.associateBy { s -> s.id },
                            checkedIds = saved.map { s -> s.id }.toSet(),
                            loaded = true,
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(error = e.userMessage()) } }

            val pairs = saved.mapNotNull { s -> s.participantId?.let { s.id to it } }
            if (pairs.isNotEmpty()) {
                // Endpoint recente: se il server non lo supporta, semplicemente non mostriamo i saldi.
                attempt { repo.api.balancesForUser(pairs) }.onSuccess { list ->
                    _state.update { it.copy(myBalances = list.associateBy { b -> b.groupId }) }
                }
            }
            _state.update { it.copy(refreshing = false) }
        }
    }

    fun addGroup(input: String, onAdded: (String) -> Unit) {
        val id = Links.extractGroupId(input)
        if (id == null) {
            _state.update { it.copy(addError = uiText(R.string.join_error_link)) }
            return
        }
        _state.update { it.copy(adding = true, addError = null) }
        viewModelScope.launch {
            attempt { repo.loadGroup(id, force = true) }
                .onSuccess { group ->
                    if (group == null) {
                        _state.update { it.copy(adding = false, addError = uiText(R.string.join_error_not_found)) }
                    } else {
                        repo.store.upsertGroup(id, group.name, touch = true)
                        _state.update { it.copy(adding = false, addError = null) }
                        onAdded(id)
                    }
                }
                .onFailure { e -> _state.update { it.copy(adding = false, addError = e.userMessage()) } }
        }
    }

    fun clearAddError() = _state.update { it.copy(addError = null) }

    fun setStarred(id: String, starred: Boolean) = repo.store.setStarred(id, starred)

    fun remove(id: String) = repo.store.removeGroup(id)
}
