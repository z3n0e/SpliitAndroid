@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package app.spliitclient.ui.group

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.annotation.StringRes
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.spliitclient.LocalContainer
import app.spliitclient.R
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
import app.spliitclient.ui.components.ExpandBarWhenNotScrollable
import app.spliitclient.ui.components.LoadingBox
import app.spliitclient.ui.components.RefreshBox
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

enum class GroupTab(@StringRes val label: Int) {
    Expenses(R.string.tab_expenses),
    Balances(R.string.tab_balances),
    Activity(R.string.tab_activity),
    Stats(R.string.tab_summary);

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

    val expensesState = rememberLazyListState()
    val balancesState = rememberLazyListState()
    val activityState = rememberLazyListState()
    val statsState = rememberLazyListState()
    val currentList = when (tab) {
        GroupTab.Expenses -> expensesState
        GroupTab.Balances -> balancesState
        GroupTab.Activity -> activityState
        GroupTab.Stats -> statsState
    }
    val activeList by rememberUpdatedState(currentList)
    val canScroll = remember { { activeList.canScrollForward || activeList.canScrollBackward } }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(canScroll = canScroll)
    ExpandBarWhenNotScrollable(scroll, currentList)

    LaunchedEffect(tab) {
        when (tab) {
            GroupTab.Activity -> vm.ensureActivities()
            GroupTab.Stats -> vm.ensureStats()
            else -> Unit
        }
    }

    val group = ui.group
    val shareChooser = stringResource(R.string.share_chooser)
    val shareTextTemplate = stringResource(R.string.share_text)

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        group?.name ?: stringResource(R.string.group_default_name),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold,
                    )
                },
                subtitle = {
                    Text(
                        if (group == null) stringResource(R.string.loading)
                        else group.participants.joinToString(", ") { it.name },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (group != null) {
                                val link = Links.groupUrl(vm.serverUrl(), group.id)
                                shareGroup(context, group.name, String.format(shareTextTemplate, group.name, link), shareChooser)
                            }
                        },
                        enabled = group != null,
                    ) { Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.action_share_link)) }
                    IconButton(onClick = onEditGroup, enabled = group != null) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.group_settings))
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
                        label = { Text(stringResource(t.label)) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == GroupTab.Expenses && group != null) {
                ExtendedFloatingActionButton(
                    onClick = onAddExpense,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.fab_expense)) },
                    expanded = !expensesState.canScrollBackward,
                )
            }
        },
    ) { padding ->
        RefreshBox(
            isRefreshing = ui.refreshing,
            onRefresh = { vm.refresh() },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                group == null -> LazyColumn(state = currentList, modifier = Modifier.fillMaxSize()) {
                    item {
                        val error = ui.groupError
                        if (error != null) ErrorBox(error, onRetry = { vm.refresh() }) else LoadingBox()
                    }
                }
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
        val none = Participant(id = "", name = "")
        ChoiceDialog(
            title = stringResource(R.string.who_are_you_title),
            options = group.participants + none,
            selected = group.participants.firstOrNull { it.id == ui.activeParticipantId } ?: none,
            label = { if (it.id.isEmpty()) stringResource(R.string.me_unknown) else it.name },
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
        // No browser installed.
    }
}

private fun shareGroup(context: Context, subject: String, text: String, chooserTitle: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, chooserTitle))
}

// ---------- Expenses ----------

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
                placeholder = { Text(stringResource(R.string.search_expenses)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = if (ui.filter.isNotEmpty()) {
                    {
                        IconButton(onClick = { onFilter("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.clear_search))
                        }
                    }
                } else null,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        val error = ui.expensesError
        if (error != null && ui.expenses.isEmpty()) {
            item(key = "err") { ErrorBox(error, onRetry = onRetry) }
        } else if (ui.expensesLoaded && ui.expenses.isEmpty() && !ui.expensesLoading) {
            item(key = "empty") {
                if (ui.filter.isBlank()) {
                    EmptyState(
                        title = stringResource(R.string.expenses_empty_title),
                        body = stringResource(R.string.expenses_empty_body),
                        action = { FilledTonalButton(onClick = onAdd) { Text(stringResource(R.string.action_add_expense)) } },
                    )
                } else {
                    EmptyState(
                        title = stringResource(R.string.search_no_results_title),
                        body = stringResource(R.string.search_no_results_body, ui.filter.trim()),
                    )
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
        if (error != null && ui.expenses.isNotEmpty()) {
            item(key = "err-more") { ErrorBox(error, onRetry = onRetry) }
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
                Text(stringResource(R.string.who_are_you_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.hero_pick_body), style = MaterialTheme.typography.bodyMedium)
            } else {
                val total = ui.balances?.let { (it.balances[me.id]?.total ?: 0.0).roundToLong() }
                Text(me.name, style = MaterialTheme.typography.labelLarge)
                Text(
                    if (total == null) "…" else Money.format(abs(total), group),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    stringResource(
                        when {
                            total == null -> R.string.balance_calculating
                            total > 0 -> R.string.balance_owed
                            total < 0 -> R.string.balance_owe
                            else -> R.string.balance_even
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun DateHeader(isoDate: String) {
    val today = stringResource(R.string.today)
    val yesterday = stringResource(R.string.yesterday)
    val label = remember(isoDate, today) { formatDay(isoDate, today, yesterday) }
    SectionTitle(label)
}

private fun formatDay(isoDate: String, todayLabel: String, yesterdayLabel: String): String {
    val date = try {
        LocalDate.parse(isoDate)
    } catch (e: Exception) {
        return isoDate
    }
    val today = LocalDate.now()
    return when (date) {
        today -> todayLabel
        today.minusDays(1) -> yesterdayLabel
        else -> {
            val pattern = if (date.year == today.year) "EEEE d MMMM" else "d MMMM yyyy"
            date.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
                .replaceFirstChar { it.titlecase(Locale.getDefault()) }
        }
    }
}

/** Approximate share of a participant in an expense (for display only). */
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
    val everyone = stringResource(R.string.paid_for_everyone)
    val forWhom = remember(e, group, everyone) {
        if (e.paidFor.size == group.participants.size && group.participants.size > 1) everyone
        else e.paidFor.joinToString(", ") { it.participant.name }
    }
    val line = if (e.isReimbursement) stringResource(R.string.expense_reimbursed, e.paidBy.name, forWhom)
    else stringResource(R.string.expense_paid_by, e.paidBy.name, forWhom)
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
                    stringResource(R.string.your_share, Money.format(myShare, group)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------- Balances ----------

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
                val error = ui.balancesError
                if (error != null) ErrorBox(error, onRetry = onRetry) else LoadingBox()
            }
            return@LazyColumn
        }
        val rows = group.participants.map { p -> p to (balances.balances[p.id]?.total ?: 0.0).roundToLong() }
        val max = rows.maxOfOrNull { abs(it.second) }?.coerceAtLeast(1L) ?: 1L
        val names = group.participants.associate { it.id to it.name }

        item(key = "h-bal") { SectionTitle(stringResource(R.string.balances_title)) }
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

        item(key = "h-reimb") { SectionTitle(stringResource(R.string.settle_title)) }
        if (balances.reimbursements.isEmpty()) {
            item(key = "even") {
                SegmentedRow(index = 0, count = 1) {
                    Text(stringResource(R.string.settle_none), style = MaterialTheme.typography.bodyLarge)
                }
            }
        } else {
            val list = balances.reimbursements
            itemsIndexed(list, key = { i, r -> "r-$i-${r.from}-${r.to}" }) { i, r ->
                SegmentedRow(index = i, count = list.size) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.settle_owes, names[r.from] ?: "?", names[r.to] ?: "?"),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            Money.format(r.amount.roundToLong(), group),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    FilledTonalButton(onClick = { onReimburse(r) }) { Text(stringResource(R.string.settle_mark_paid)) }
                }
            }
        }
    }
}

// ---------- Activity ----------

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
        val error = ui.activitiesError
        if (error != null && ui.activities.isEmpty()) {
            item { ErrorBox(error, onRetry = onRetry) }
        } else if (ui.activitiesLoaded && ui.activities.isEmpty()) {
            item {
                EmptyState(
                    title = stringResource(R.string.activity_empty_title),
                    body = stringResource(R.string.activity_empty_body),
                )
            }
        }
        itemsIndexed(ui.activities, key = { _, a -> a.id }) { i, a ->
            val expenseId = a.expenseId
            val canOpen = a.expense != null && expenseId != null
            SegmentedRow(
                index = i,
                count = ui.activities.size,
                onClick = if (canOpen && expenseId != null) ({ onOpenExpense(expenseId) }) else null,
            ) {
                val who = a.participantId?.let { names[it] }
                Avatar(who ?: "?", size = 36.dp)
                Column(Modifier.weight(1f)) {
                    Text(describe(a, who), style = MaterialTheme.typography.bodyLarge)
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

@Composable
private fun describe(a: GroupActivity, whoName: String?): String {
    val who = whoName ?: stringResource(R.string.someone)
    val title = a.expense?.title ?: a.data
    val expense = if (title.isNullOrBlank()) stringResource(R.string.an_expense) else stringResource(R.string.quoted, title)
    return when (a.activityType) {
        "UPDATE_GROUP" -> stringResource(R.string.activity_update_group, who)
        "CREATE_EXPENSE" -> stringResource(R.string.activity_create, who, expense)
        "UPDATE_EXPENSE" -> stringResource(R.string.activity_update, who, expense)
        "DELETE_EXPENSE" -> stringResource(R.string.activity_delete, who, expense)
        "SCHEDULE_GROUP_DELETION" -> stringResource(R.string.activity_schedule_deletion, who)
        "RESTORE_GROUP" -> stringResource(R.string.activity_restore, who)
        else -> stringResource(R.string.activity_other, who)
    }
}

private fun relativeTime(iso: String): String = try {
    val millis = Instant.parse(iso).toEpochMilli()
    DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
} catch (e: Exception) {
    ""
}

// ---------- Summary ----------

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
                        Text(stringResource(R.string.group_info), style = MaterialTheme.typography.labelLarge)
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
                OutlinedButton(onClick = { onExport("csv") }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.export_csv)) }
                OutlinedButton(onClick = { onExport("json") }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.export_json)) }
            }
        }
        if (stats == null) {
            item {
                val error = ui.statsError
                if (error != null) ErrorBox(error, onRetry = onRetry) else LoadingBox()
            }
            return@LazyColumn
        }
        item(key = "total") {
            StatCard(
                label = stringResource(R.string.stats_total),
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
                        stringResource(R.string.stats_pick_me),
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
                        label = stringResource(R.string.stats_you_paid),
                        value = Money.format((stats.totalParticipantSpendings ?: 0.0).roundToLong(), group),
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        label = stringResource(R.string.stats_your_share),
                        value = Money.format((stats.totalParticipantShare ?: 0.0).roundToLong(), group),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        val cats = stats.categories.filter { it.total != 0.0 }
        if (cats.isNotEmpty()) {
            item(key = "h-cat") { SectionTitle(stringResource(R.string.stats_by_category)) }
            val max = cats.maxOf { abs(it.total) }.coerceAtLeast(1.0)
            itemsIndexed(cats, key = { _, c -> "c-${c.categoryId}" }) { i, c ->
                val cat = Categories.get(c.categoryId)
                SegmentedRow(index = i, count = cats.size) {
                    Text(cat.emoji, style = MaterialTheme.typography.titleLarge)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(cat.name), style = MaterialTheme.typography.titleMedium)
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
