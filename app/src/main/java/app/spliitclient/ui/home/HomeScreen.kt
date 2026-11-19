@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package app.spliitclient.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.spliitclient.LocalContainer
import app.spliitclient.data.GroupSummary
import app.spliitclient.data.SavedGroup
import app.spliitclient.data.UserBalance
import app.spliitclient.ui.components.Avatar
import app.spliitclient.ui.components.ConfirmDialog
import app.spliitclient.ui.components.EmptyState
import app.spliitclient.ui.components.SectionTitle
import app.spliitclient.ui.components.SegmentedRow
import app.spliitclient.util.Money
import kotlin.math.roundToLong

@Composable
fun HomeScreen(
    onOpenGroup: (String) -> Unit,
    onCreateGroup: () -> Unit,
    onSettings: () -> Unit,
) {
    val container = LocalContainer.current
    val vm: HomeViewModel = viewModel { HomeViewModel(container.repo) }
    val groups by vm.groups.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf<SavedGroup?>(null) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { vm.onVisible() }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Spliit", fontWeight = FontWeight.Bold) },
                subtitle = {
                    Text(if (groups.isEmpty()) "Dividi le spese con chi vuoi" else "${groups.size} gruppi su questo telefono")
                },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Impostazioni") }
                },
                scrollBehavior = scroll,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateGroup,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Nuovo gruppo") },
                expanded = !listState.canScrollBackward,
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = vm::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 112.dp),
            ) {
                item(key = "add") {
                    FilledTonalButton(
                        onClick = { vm.clearAddError(); showAdd = true },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    ) { Text("Entra in un gruppo con un link") }
                }
                if (state.error != null) {
                    item(key = "error") {
                        Text(
                            state.error ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp),
                        )
                    }
                }
                if (groups.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            title = "Nessun gruppo",
                            body = "Crea un gruppo per una vacanza, la casa o una cena, oppure entra con il link che ti hanno mandato.",
                        )
                    }
                } else {
                    val starred = groups.filter { it.starred }
                    val others = groups.filterNot { it.starred }
                    if (starred.isNotEmpty()) {
                        item(key = "h-star") { SectionTitle("Preferiti") }
                        itemsIndexed(starred, key = { _, g -> "s-" + g.id }) { i, g ->
                            GroupRow(g, i, starred.size, state.summaries[g.id], state.myBalances[g.id], g.id in state.checkedIds,
                                onClick = { onOpenGroup(g.id) },
                                onStar = { vm.setStarred(g.id, !g.starred) },
                                onRemove = { confirmRemove = g })
                        }
                    }
                    if (others.isNotEmpty()) {
                        item(key = "h-all") { SectionTitle(if (starred.isEmpty()) "I tuoi gruppi" else "Altri gruppi") }
                        itemsIndexed(others, key = { _, g -> "o-" + g.id }) { i, g ->
                            GroupRow(g, i, others.size, state.summaries[g.id], state.myBalances[g.id], g.id in state.checkedIds,
                                onClick = { onOpenGroup(g.id) },
                                onStar = { vm.setStarred(g.id, !g.starred) },
                                onRemove = { confirmRemove = g })
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddGroupDialog(
            busy = state.adding,
            error = state.addError,
            onDismiss = { showAdd = false },
            onSubmit = { text -> vm.addGroup(text) { id -> showAdd = false; onOpenGroup(id) } },
        )
    }

    confirmRemove?.let { g ->
        ConfirmDialog(
            title = "Rimuovere «${g.name.ifBlank { "gruppo" }}»?",
            text = "Il gruppo resta sul server per gli altri. Per ritrovarlo ti servirà di nuovo il link.",
            confirmLabel = "Rimuovi",
            onConfirm = { vm.remove(g.id); confirmRemove = null },
            onDismiss = { confirmRemove = null },
        )
    }
}

@Composable
private fun GroupRow(
    group: SavedGroup,
    index: Int,
    count: Int,
    summary: GroupSummary?,
    balance: UserBalance?,
    checked: Boolean,
    onClick: () -> Unit,
    onStar: () -> Unit,
    onRemove: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val name = summary?.name ?: group.name.ifBlank { "Gruppo" }
    val subtitle = when {
        summary != null -> "${summary.count?.participants ?: 0} partecipanti"
        checked -> "Non trovato su questo server"
        else -> "…"
    }
    SegmentedRow(index = index, count = count, onClick = onClick) {
        Avatar(name)
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (balance != null) {
            val minor = balance.amount.roundToLong()
            Text(
                Money.format(minor, balance.currency, balance.currencyCode, showPlus = true),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = when {
                    minor > 0 -> MaterialTheme.colorScheme.primary
                    minor < 0 -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Opzioni") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(if (group.starred) "Togli dai preferiti" else "Aggiungi ai preferiti") },
                    leadingIcon = { Icon(if (group.starred) Icons.Outlined.Star else Icons.Filled.Star, contentDescription = null) },
                    onClick = { menu = false; onStar() },
                )
                DropdownMenuItem(
                    text = { Text("Rimuovi da questo telefono") },
                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                    onClick = { menu = false; onRemove() },
                )
            }
        }
    }
}

@Composable
private fun AddGroupDialog(
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Entra in un gruppo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Incolla il link che ti hanno condiviso (o solo l'ID del gruppo).")
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text("Link del gruppo") },
                    isError = error != null,
                    supportingText = if (error != null) {
                        { Text(error) }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(text) }, enabled = !busy && text.isNotBlank()) {
                Text(if (busy) "Controllo…" else "Entra")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}
