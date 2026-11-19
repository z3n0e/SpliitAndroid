@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package app.spliitclient.ui.group

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.spliitclient.LocalContainer
import app.spliitclient.data.ExpenseItem
import app.spliitclient.data.Group
import app.spliitclient.data.GroupActivity
import app.spliitclient.data.Participant
import app.spliitclient.data.Reimbursement
import app.spliitclient.ui.components.AppIcons
import app.spliitclient.ui.components.Avatar
import app.spliitclient.ui.components.BalanceBar
import app.spliitclient.ui.components.ChoiceDialog
import app.spliitclient.ui.components.EmptyState
import app.spliitclient.ui.components.ErrorBox
import app.spliitclient.ui.components.LoadingBox
import app.spliitclient.ui.components.SectionTitle
import app.spliitclient.ui.components.SegmentedRow
import app.spliitclient.util.Categories
import app.spliitclient.util.Links
import app.spliitclient.util.Money
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

enum class GroupTab(val label: String) {
    Expenses("Spese"),
    Balances("Saldi"),
    Activity("Attività"),
    Stats("Riepilogo");

    val icon: ImageVector
        get() = when (this) {
            Expenses -> AppIcons.Receipt
            Balances -> AppIcons.Scale
            Activity -> Icons.Filled.Notifications
            Stats -> AppIcons.Chart
        }
}

@Composable
fun GroupScreen(
    groupId: String,
    onBack: () -> Unit,
    onAddExpense: () -> Unit,
    onOpenExpense: (String) -> Unit,
    onReimburse: (Reimbursement) -> Unit,
    onEditGroup: () -> Unit,
) {
    val container = LocalContainer.current
    val vm: GroupViewModel = viewModel { GroupViewModel(groupId, container.repo) }
    val ui = vm.ui
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(GroupTab.Expenses) }
    var pickMe by remember { mutableStateOf(false) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val expensesState = rememberLazyListState()
    val balancesState = rememberLazyListState()
    val activityState = rememberLazyListState()
    val statsState = rememberLazyListState()

    LaunchedEffect(tab) {
        when (tab) {
            GroupTab.Activity -> vm.ensureActivities()
            GroupTab.Stats -> vm.ensureStats()
            else -> Unit
        }
    }

    val group = ui.group

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(group?.name ?: "Gruppo", maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold) },
                subtitle = {
                    Text(
                        if (group == null) "Caricamento…"
                        else group.participants.joinToString(", ") { it.name },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { if (group != null) shareGroup(context, group, vm.serverUrl()) },
                        enabled = group != null,
                    ) { Icon(Icons.Filled.Share, contentDescription = "Condividi il link") }
                    IconButton(onClick = onEditGroup, enabled = group != null) {
                        Icon(Icons.Filled.Edit, contentDescription = "Impostazioni del gruppo")
                    }
                },
                scrollBehavior = scroll,
            )
        },
        bottomBar = {
            ShortNavigationBar {
                GroupTab.entries.forEach { t ->
                    ShortNavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == GroupTab.Expenses && group != null) {
                ExtendedFloatingActionButton(
                    onClick = onAddExpense,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Spesa") },
                    expanded = !expensesState.canScrollBackward,
                )
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = ui.refreshing,
            onRefresh = { vm.refresh() },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                group == null && ui.groupError != null -> LazyColumn(Modifier.fillMaxSize()) {
                    item { ErrorBox(ui.groupError, onRetry = { vm.refresh() }) }
                }
                group == null -> LoadingBox()
                else -> when (tab) {
                    GroupTab.Expenses -> ExpensesTab(
                        ui = ui, group = group, listState = expensesState,
                        onFilter = vm::setFilter, onLoadMore = vm::loadMoreExpenses,
                        onRetry = vm::retryExpenses, onOpen = onOpenExpense,
                        onPickMe = { pickMe = true }, onAdd = onAddExpense,
                    )
                    GroupTab.Balances -> BalancesTab(ui, group, balancesState, onReimburse, onRetry = { vm.refresh() })
                    GroupTab.Activity -> ActivityTab(ui, group, activityState, vm::loadMoreActivities, vm::retryActivities, onOpenExpense)
                    GroupTab.Stats -> StatsTab(
                        ui, group, statsState,
                        onPickMe = { pickMe = true },
                        onRetry = vm::retryStats,
                        onExport = { format -> openUrl(context, Links.exportUrl(vm.serverUrl(), group.id, format)) },
                    )
                }
            }
        }
    }

    if (pickMe && group != null) {
        val none = Participant(id = "", name = "Non voglio dirlo")
        ChoiceDialog(
            title = "Chi sei in questo gruppo?",
            options = group.participants + none,
            selected = group.participants.firstOrNull { it.id == ui.activeParticipantId } ?: none,
            label = { it.name },
            onDismiss = { pickMe = false },
            onSelect = { p -> vm.setActiveParticipant(p.id.ifEmpty { null }); pickMe = false },
        )
    }
}

private fun openUrl(context: Context, url: String) {
    if (!Links.isSafeWebUrl(url)) return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        // Nessun browser installato.
    }
}

private fun shareGroup(context: Context, group: Group, server: String) {
    val link = Links.groupUrl(server, group.id)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, group.name)
        putExtra(Intent.EXTRA_TEXT, "Gestiamo le spese di «${group.name}» su Spliit: $link")
    }
    context.startActivity(Intent.createChooser(send, "Condividi il gruppo"))
}

// ---------- Spese ----------

@Composable
private fun ExpensesTab(
    ui: GroupUi,
    group: Group,
    listState: LazyListState,
    onFilter: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (String) -> Unit,
    onPickMe: () -> Unit,
    onAdd: () -> Unit,
) {
    val sections = remember(ui.expenses) { ui.expenses.groupBy { it.expenseDate.take(10) }.toList() }
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, ui.expenses.size, ui.expensesHasMore) {
        if (nearEnd && ui.expensesHasMore && ui.expensesLoaded) onLoadMore()
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 4.dp, bottom = 112.dp)) {
        item(key = "hero") { BalanceHero(ui, group, onPickMe) }
        item(key = "search") {
            OutlinedTextField(
                value = ui.filter,
                onValueChange = onFilter,
                singleLine = true,
                placeholder = { Text("Cerca tra le spese") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = if (ui.filter.isNotEmpty()) {
                    { IconButton(onClick = { onFilter("") }) { Icon(Icons.Filled.Clear, contentDescription = "Cancella ricerca") } }
                } else null,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        if (ui.expensesError != null && ui.expenses.isEmpty()) {
            item(key = "err") { ErrorBox(ui.expensesError, onRetry = onRetry) }
        } else if (ui.expensesLoaded && ui.expenses.isEmpty() && !ui.expensesLoading) {
            item(key = "empty") {
                if (ui.filter.isBlank()) {
                    EmptyState(
                        title = "Ancora nessuna spesa",
                        body = "Aggiungi la prima: chi ha pagato, quanto e per chi. Al resto pensa Spliit.",
                        action = { FilledTonalButton(onClick = onAdd) { Text("Aggiungi una spesa") } },
                    )
                } else {
                    EmptyState(title = "Nessun risultato", body = "Nessuna spesa contiene «${ui.filter.trim()}».")
                }
            }
        }
        sections.forEach { (date, list) ->
            item(key = "d-$date") { DateHeader(date) }
            itemsIndexed(list, key = { _, e -> e.id }) { i, e ->
                ExpenseRow(e, i, list.size, group, ui.activeParticipantId) { onOpen(e.id) }
            }
        }
        if (ui.expensesLoading) item(key = "loading") { LoadingBox() }
        if (ui.expensesError != null && ui.expenses.isNotEmpty()) {
            item(key = "err-more") { ErrorBox(ui.expensesError, onRetry = onRetry) }
        }
    }
}

@Composable
private fun BalanceHero(ui: GroupUi, group: Group, onPickMe: () -> Unit) {
    val me = group.participants.firstOrNull { it.id == ui.activeParticipantId }
    Surface(
        onClick = onPickMe,
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 22.dp)) {
            if (me == null) {
                Text("Chi sei in questo gruppo?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Tocca qui e scegli il tuo nome: vedrai subito quanto devi o quanto ti devono.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                val total = ui.balances?.let { (it.balances[me.id]?.total ?: 0.0).roundToLong() }
                Text(me.name, style = MaterialTheme.typography.labelLarge)
                Text(
                    if (total == null) "…" else Money.format(abs(total), group),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    when {
                        total == null -> "Calcolo del saldo…"
                        total > 0 -> "ti devono in totale"
                        total < 0 -> "devi in totale"
                        else -> "sei in pari con tutti"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun DateHeader(isoDate: String) {
    val label = remember(isoDate) { formatDay(isoDate) }
    SectionTitle(label)
}

private fun formatDay(isoDate: String): String {
    val date = try {
        LocalDate.parse(isoDate)
    } catch (e: Exception) {
        return isoDate
    }
    val today = LocalDate.now()
    return when (date) {
        today -> "Oggi"
        today.minusDays(1) -> "Ieri"
        else -> {
            val pattern = if (date.year == today.year) "EEEE d MMMM" else "d MMMM yyyy"
            date.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
                .replaceFirstChar { it.titlecase(Locale.getDefault()) }
        }
    }
}

/** Quota (approssimata per la visualizzazione) del partecipante in una spesa. */
private fun shareOf(e: ExpenseItem, participantId: String): Long? {
    val row = e.paidFor.firstOrNull { it.participant.id == participantId } ?: return null
    val totalShares = e.paidFor.sumOf { it.shares }
    return when (e.splitMode) {
        "BY_AMOUNT" -> row.shares
        "BY_PERCENTAGE" -> e.amount * row.shares / 10_000
        else -> if (totalShares == 0L) 0 else e.amount * row.shares / totalShares
    }
}

@Composable
private fun ExpenseRow(e: ExpenseItem, index: Int, count: Int, group: Group, me: String?, onClick: () -> Unit) {
    val category = Categories.get(e.category?.id ?: 0)
    val forWhom = remember(e, group) {
        if (e.paidFor.size == group.participants.size && group.participants.size > 1) "tutti"
        else e.paidFor.joinToString(", ") { it.participant.name }
    }
    val line = if (e.isReimbursement) "${e.paidBy.name} ha rimborsato $forWhom"
    else "Pagato da ${e.paidBy.name} per $forWhom"
    val myShare = if (me != null && !e.isReimbursement) shareOf(e, me) else null

    SegmentedRow(index = index, count = count, onClick = onClick) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.tertiaryContainer),
            contentAlignment = Alignment.Center,
        ) { Text(category.emoji, style = MaterialTheme.typography.titleMedium) }
        Column(Modifier.weight(1f)) {
            Text(
                e.title,
                style = MaterialTheme.typography.titleMedium,
                fontStyle = if (e.isReimbursement) FontStyle.Italic else FontStyle.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                line,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Money.format(e.amount, group), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (myShare != null) {
                Text(
                    "tua quota ${Money.format(myShare, group)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------- Saldi ----------

@Composable
private fun BalancesTab(
    ui: GroupUi,
    group: Group,
    listState: LazyListState,
    onReimburse: (Reimbursement) -> Unit,
    onRetry: () -> Unit,
) {
    val balances = ui.balances
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        if (balances == null) {
            item {
                if (ui.balancesError != null) ErrorBox(ui.balancesError, onRetry = onRetry) else LoadingBox()
            }
            return@LazyColumn
        }
        val rows = group.participants.map { p -> p to (balances.balances[p.id]?.total ?: 0.0).roundToLong() }
        val max = rows.maxOfOrNull { abs(it.second) }?.coerceAtLeast(1L) ?: 1L
        val names = group.participants.associate { it.id to it.name }

        item(key = "h-bal") { SectionTitle("Chi è in credito e chi in debito") }
        itemsIndexed(rows, key = { _, r -> "b-" + r.first.id }) { i, (p, total) ->
            SegmentedRow(index = i, count = rows.size) {
                Avatar(p.name, size = 36.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(p.name, style = MaterialTheme.typography.titleMedium)
                    BalanceBar(
                        fraction = abs(total).toFloat() / max,
                        color = if (total >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    Money.format(total, group, showPlus = true),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        total > 0 -> MaterialTheme.colorScheme.primary
                        total < 0 -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }

        item(key = "h-reimb") { SectionTitle("Come saldare i conti") }
        if (balances.reimbursements.isEmpty()) {
            item(key = "even") {
                SegmentedRow(index = 0, count = 1) {
                    Text("Nessun rimborso da fare: siete in pari.", style = MaterialTheme.typography.bodyLarge)
                }
            }
        } else {
            val list = balances.reimbursements
            itemsIndexed(list, key = { i, r -> "r-$i-${r.from}-${r.to}" }) { i, r ->
                SegmentedRow(index = i, count = list.size) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${names[r.from] ?: "?"} deve dare a ${names[r.to] ?: "?"}",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            Money.format(r.amount.roundToLong(), group),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    FilledTonalButton(onClick = { onReimburse(r) }) { Text("Segna pagato") }
                }
            }
        }
    }
}

// ---------- Attività ----------

@Composable
private fun ActivityTab(
    ui: GroupUi,
    group: Group,
    listState: LazyListState,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onOpenExpense: (String) -> Unit,
) {
    val names = remember(group) { group.participants.associate { it.id to it.name } }
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, ui.activities.size, ui.activitiesHasMore) {
        if (nearEnd && ui.activitiesHasMore && ui.activitiesLoaded) onLoadMore()
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)) {
        if (ui.activitiesError != null && ui.activities.isEmpty()) {
            item { ErrorBox(ui.activitiesError, onRetry = onRetry) }
        } else if (ui.activitiesLoaded && ui.activities.isEmpty()) {
            item { EmptyState(title = "Nessuna attività", body = "Qui vedrai chi aggiunge, modifica o elimina le spese.") }
        }
        itemsIndexed(ui.activities, key = { _, a -> a.id }) { i, a ->
            val canOpen = a.expense != null && a.expenseId != null
            SegmentedRow(
                index = i,
                count = ui.activities.size,
                onClick = if (canOpen) ({ onOpenExpense(a.expenseId!!) }) else null,
            ) {
                Avatar(a.participantId?.let { names[it] } ?: "?", size = 36.dp)
                Column(Modifier.weight(1f)) {
                    Text(describe(a, names), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        relativeTime(a.time),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (ui.activitiesLoading) item { LoadingBox() }
    }
}

private fun describe(a: GroupActivity, names: Map<String, String>): String {
    val who = a.participantId?.let { names[it] } ?: "Qualcuno"
    val title = a.expense?.title ?: a.data
    val quoted = if (title.isNullOrBlank()) "una spesa" else "«$title»"
    return when (a.activityType) {
        "UPDATE_GROUP" -> "$who ha modificato le impostazioni del gruppo"
        "CREATE_EXPENSE" -> "$who ha aggiunto $quoted"
        "UPDATE_EXPENSE" -> "$who ha modificato $quoted"
        "DELETE_EXPENSE" -> "$who ha eliminato $quoted"
        "SCHEDULE_GROUP_DELETION" -> "$who ha programmato l'eliminazione del gruppo"
        "RESTORE_GROUP" -> "$who ha ripristinato il gruppo"
        else -> "$who ha fatto una modifica"
    }
}

private fun relativeTime(iso: String): String = try {
    val millis = Instant.parse(iso).toEpochMilli()
    DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
} catch (e: Exception) {
    ""
}

// ---------- Riepilogo ----------

@Composable
private fun StatsTab(
    ui: GroupUi,
    group: Group,
    listState: LazyListState,
    onPickMe: () -> Unit,
    onRetry: () -> Unit,
    onExport: (String) -> Unit,
) {
    val stats = ui.stats
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)) {
        val info = group.information?.trim().orEmpty()
        if (info.isNotEmpty()) {
            item(key = "info") {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Informazioni del gruppo", style = MaterialTheme.typography.labelLarge)
                        SelectionContainer { Text(info, style = MaterialTheme.typography.bodyLarge) }
                    }
                }
            }
        }
        item(key = "export") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = { onExport("csv") }, modifier = Modifier.weight(1f)) { Text("Esporta CSV") }
                OutlinedButton(onClick = { onExport("json") }, modifier = Modifier.weight(1f)) { Text("Esporta JSON") }
            }
        }
        if (stats == null) {
            item { if (ui.statsError != null) ErrorBox(ui.statsError, onRetry = onRetry) else LoadingBox() }
            return@LazyColumn
        }
        item(key = "total") {
            StatCard(
                label = "Spesa totale del gruppo",
                value = Money.format(stats.totalGroupSpendings.roundToLong(), group),
                big = true,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        item(key = "mine") {
            if (ui.activeParticipantId == null) {
                Surface(
                    onClick = onPickMe,
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Text(
                        "Scegli chi sei per vedere quanto hai pagato e la tua quota.",
                        modifier = Modifier.padding(20.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatCard(
                        label = "Hai pagato",
                        value = Money.format((stats.totalParticipantSpendings ?: 0.0).roundToLong(), group),
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        label = "La tua quota",
                        value = Money.format((stats.totalParticipantShare ?: 0.0).roundToLong(), group),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        val cats = stats.categories.filter { it.total != 0.0 }
        if (cats.isNotEmpty()) {
            item(key = "h-cat") { SectionTitle("Per categoria") }
            val max = cats.maxOf { abs(it.total) }.coerceAtLeast(1.0)
            itemsIndexed(cats, key = { _, c -> "c-${c.categoryId}" }) { i, c ->
                val cat = Categories.get(c.categoryId)
                SegmentedRow(index = i, count = cats.size) {
                    Text(cat.emoji, style = MaterialTheme.typography.titleLarge)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(cat.name, style = MaterialTheme.typography.titleMedium)
                        BalanceBar(fraction = (abs(c.total) / max).toFloat(), color = MaterialTheme.colorScheme.tertiary)
                    }
                    Text(Money.format(c.total.roundToLong(), group), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier, big: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(if (big) 32.dp else 24.dp),
        color = if (big) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(
                value,
                style = if (big) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
