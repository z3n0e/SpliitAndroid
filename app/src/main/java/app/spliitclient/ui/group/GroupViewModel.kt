package app.spliitclient.ui.group

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.spliitclient.R
import app.spliitclient.data.BalancesResult
import app.spliitclient.data.ExpenseItem
import app.spliitclient.data.Group
import app.spliitclient.data.GroupActivity
import app.spliitclient.data.Repository
import app.spliitclient.data.StatsOverview
import app.spliitclient.util.UiText
import app.spliitclient.util.attempt
import app.spliitclient.util.uiText
import app.spliitclient.util.userMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

data class GroupUi(
    val group: Group? = null,
    val groupError: UiText? = null,
    val refreshing: Boolean = false,
    val activeParticipantId: String? = null,
    val filter: String = "",
    val expenses: List<ExpenseItem> = emptyList(),
    val expensesCursor: Int = 0,
    val expensesHasMore: Boolean = true,
    val expensesLoading: Boolean = false,
    val expensesLoaded: Boolean = false,
    val expensesError: UiText? = null,
    val balances: BalancesResult? = null,
    val balancesError: UiText? = null,
    val activities: List<GroupActivity> = emptyList(),
    val activitiesCursor: Int = 0,
    val activitiesHasMore: Boolean = true,
    val activitiesLoading: Boolean = false,
    val activitiesLoaded: Boolean = false,
    val activitiesError: UiText? = null,
    val stats: StatsOverview? = null,
    val statsLoading: Boolean = false,
    val statsError: UiText? = null,
)

/**
 * Carica solo ciò che serve: gruppo, saldi e prima pagina di spese all'apertura (in parallelo);
 * attività e riepilogo solo quando apri quella scheda. Si aggiorna da solo quando salvi una spesa.
 */
class GroupViewModel(private val groupId: String, private val repo: Repository) : ViewModel() {

    var ui by mutableStateOf(
        GroupUi(group = repo.cachedGroup(groupId), activeParticipantId = repo.store.activeParticipant(groupId)),
    )
        private set

    private var expensesJob: Job? = null
    private var activitiesJob: Job? = null
    private var statsJob: Job? = null
    private var wantActivities = false
    private var wantStats = false

    private var touched = false

    init {
        refresh(showIndicator = false)
        viewModelScope.launch {
            repo.changes.collect { changed -> if (changed == groupId) refresh(showIndicator = false) }
        }
    }

    fun refresh(showIndicator: Boolean = true) {
        if (showIndicator) ui = ui.copy(refreshing = true)
        viewModelScope.launch {
            val jobs = mutableListOf<Job>()
            jobs += launch { loadGroup() }
            jobs += launch { loadBalances() }
            expensesJob?.cancel()
            jobs += launch { loadExpenses(reset = true) }.also { expensesJob = it }
            if (wantActivities) {
                activitiesJob?.cancel()
                jobs += launch { loadActivities(reset = true) }.also { activitiesJob = it }
            }
            if (wantStats) {
                statsJob?.cancel()
                jobs += launch { loadStats() }.also { statsJob = it }
            }
            jobs.joinAll()
            ui = ui.copy(refreshing = false)
        }
    }

    fun ensureActivities() {
        if (wantActivities) return
        wantActivities = true
        activitiesJob = viewModelScope.launch { loadActivities(reset = true) }
    }

    fun ensureStats() {
        if (wantStats) return
        wantStats = true
        statsJob = viewModelScope.launch { loadStats() }
    }

    fun retryStats() {
        statsJob?.cancel()
        statsJob = viewModelScope.launch { loadStats() }
    }

    fun setActiveParticipant(id: String?) {
        repo.store.setActiveParticipant(groupId, id)
        ui = ui.copy(activeParticipantId = id)
        if (wantStats) retryStats()
    }

    private var filterJob: Job? = null

    fun setFilter(query: String) {
        ui = ui.copy(filter = query)
        expensesJob?.cancel()
        filterJob?.cancel()
        filterJob = viewModelScope.launch {
            delay(350)
            loadExpenses(reset = true)
        }
        expensesJob = filterJob
    }

    fun loadMoreExpenses() {
        if (!ui.expensesHasMore || ui.expensesLoading || expensesJob?.isActive == true) return
        expensesJob = viewModelScope.launch { loadExpenses(reset = false) }
    }

    fun retryExpenses() {
        expensesJob?.cancel()
        expensesJob = viewModelScope.launch { loadExpenses(reset = ui.expenses.isEmpty()) }
    }

    fun loadMoreActivities() {
        if (!ui.activitiesHasMore || ui.activitiesLoading || activitiesJob?.isActive == true) return
        activitiesJob = viewModelScope.launch { loadActivities(reset = false) }
    }

    fun retryActivities() {
        activitiesJob?.cancel()
        activitiesJob = viewModelScope.launch { loadActivities(reset = ui.activities.isEmpty()) }
    }

    private suspend fun loadGroup() {
        attempt { repo.loadGroup(groupId, force = true) }
            .onSuccess { group ->
                if (group == null) {
                    ui = ui.copy(group = null, groupError = uiText(R.string.group_missing))
                } else {
                    val active = ui.activeParticipantId?.takeIf { id -> group.participants.any { it.id == id } }
                    if (!touched) {
                        touched = true
                        repo.store.touch(groupId)
                    }
                    if (active == null && ui.activeParticipantId != null) repo.store.setActiveParticipant(groupId, null)
                    ui = ui.copy(group = group, groupError = null, activeParticipantId = active)
                }
            }
            .onFailure { e -> if (ui.group == null) ui = ui.copy(groupError = e.userMessage()) }
    }

    private suspend fun loadBalances() {
        attempt { repo.api.balances(groupId) }
            .onSuccess { ui = ui.copy(balances = it, balancesError = null) }
            .onFailure { e -> ui = ui.copy(balancesError = e.userMessage()) }
    }

    private suspend fun loadExpenses(reset: Boolean) {
        val cursor = if (reset) 0 else ui.expensesCursor
        val filter = ui.filter.trim().ifEmpty { null }
        ui = ui.copy(expensesLoading = true, expensesError = null)
        try {
            attempt { repo.api.listExpenses(groupId, cursor, PAGE_SIZE, filter) }
                .onSuccess { page ->
                    val merged = if (reset) page.expenses else ui.expenses + page.expenses
                    ui = ui.copy(
                        expenses = merged.distinctBy { it.id },
                        expensesCursor = page.nextCursor,
                        expensesHasMore = page.hasMore,
                        expensesLoaded = true,
                    )
                }
                .onFailure { e -> ui = ui.copy(expensesError = e.userMessage()) }
        } finally {
            ui = ui.copy(expensesLoading = false)
        }
    }

    private suspend fun loadActivities(reset: Boolean) {
        val cursor = if (reset) 0 else ui.activitiesCursor
        ui = ui.copy(activitiesLoading = true, activitiesError = null)
        try {
            attempt { repo.api.activities(groupId, cursor, PAGE_SIZE) }
                .onSuccess { page ->
                    val merged = if (reset) page.activities else ui.activities + page.activities
                    ui = ui.copy(
                        activities = merged.distinctBy { it.id },
                        activitiesCursor = page.nextCursor,
                        activitiesHasMore = page.hasMore,
                        activitiesLoaded = true,
                    )
                }
                .onFailure { e -> ui = ui.copy(activitiesError = e.userMessage()) }
        } finally {
            ui = ui.copy(activitiesLoading = false)
        }
    }

    private suspend fun loadStats() {
        ui = ui.copy(statsLoading = true, statsError = null)
        try {
            attempt { repo.api.statsOverview(groupId, ui.activeParticipantId) }
                .onSuccess { ui = ui.copy(stats = it) }
                .onFailure { e -> ui = ui.copy(statsError = e.userMessage()) }
        } finally {
            ui = ui.copy(statsLoading = false)
        }
    }

    fun serverUrl(): String = repo.store.serverUrl.value

    private companion object {
        const val PAGE_SIZE = 25
    }
}
