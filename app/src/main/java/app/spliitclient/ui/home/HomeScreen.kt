@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package app.spliitclient.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.spliitclient.LocalContainer
import app.spliitclient.R
import app.spliitclient.data.GroupSummary
import app.spliitclient.data.SavedGroup
import app.spliitclient.data.UserBalance
import app.spliitclient.ui.components.Avatar
import app.spliitclient.ui.components.BrandMark
import app.spliitclient.ui.components.ConfirmDialog
import app.spliitclient.ui.components.EmptyState
import app.spliitclient.ui.components.ExpandBarWhenNotScrollable
import app.spliitclient.ui.components.RefreshBox
import app.spliitclient.ui.components.SectionTitle
import app.spliitclient.ui.components.SegmentedRow
import app.spliitclient.util.UiText
import app.spliitclient.util.asString
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
    val listState = rememberLazyListState()
    val canScroll = remember(listState) { { listState.canScrollForward || listState.canScrollBackward } }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(canScroll = canScroll)
    ExpandBarWhenNotScrollable(scroll, listState)
    val resources = LocalContext.current.resources

    LaunchedEffect(Unit) { vm.onVisible() }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold) },
                subtitle = {
                    Text(
                        if (groups.isEmpty()) stringResource(R.string.home_subtitle_empty)
                        else resources.getQuantityString(R.plurals.home_subtitle_count, groups.size, groups.size),
                    )
                },
                navigationIcon = { BrandMark(size = 40.dp, modifier = Modifier.padding(start = 12.dp, end = 4.dp)) },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        RefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = vm::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                item(key = "hero") {
                    HomeHero(
                        compact = groups.isNotEmpty(),
                        onCreate = onCreateGroup,
                        onJoin = { vm.clearAddError(); showAdd = true },
                    )
                }
                state.error?.let { error ->
                    item(key = "error") {
                        Text(
                            error.asString(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp),
                        )
                    }
                }
                if (groups.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            title = stringResource(R.string.home_empty_title),
                            body = stringResource(R.string.home_empty_body),
                        )
                    }
                } else {
                    val starred = groups.filter { it.starred }
                    val others = groups.filterNot { it.starred }
                    if (starred.isNotEmpty()) {
                        item(key = "h-star") { SectionTitle(stringResource(R.string.section_favorites)) }
                        itemsIndexed(starred, key = { _, g -> "s-" + g.id }) { i, g ->
                            GroupRow(
                                g, i, starred.size, state.summaries[g.id], state.myBalances[g.id], g.id in state.checkedIds,
                                onClick = { onOpenGroup(g.id) },
                                onStar = { vm.setStarred(g.id, !g.starred) },
                                onRemove = { confirmRemove = g },
                            )
                        }
                    }
                    if (others.isNotEmpty()) {
                        item(key = "h-all") {
                            SectionTitle(
                                stringResource(if (starred.isEmpty()) R.string.section_your_groups else R.string.section_other_groups),
                            )
                        }
                        itemsIndexed(others, key = { _, g -> "o-" + g.id }) { i, g ->
                            GroupRow(
                                g, i, others.size, state.summaries[g.id], state.myBalances[g.id], g.id in state.checkedIds,
                                onClick = { onOpenGroup(g.id) },
                                onStar = { vm.setStarred(g.id, !g.starred) },
                                onRemove = { confirmRemove = g },
                            )
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
            title = stringResource(R.string.remove_group_title, g.name.ifBlank { stringResource(R.string.group_default_name) }),
            text = stringResource(R.string.remove_group_text),
            confirmLabel = stringResource(R.string.action_remove),
            onConfirm = { vm.remove(g.id); confirmRemove = null },
            onDismiss = { confirmRemove = null },
        )
    }
}

@Composable
private fun HomeHero(compact: Boolean, onCreate: () -> Unit, onJoin: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (!compact) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    BrandMark(size = 64.dp)
                    Text(
                        stringResource(R.string.home_hero_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(stringResource(R.string.home_hero_body), style = MaterialTheme.typography.bodyLarge)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onCreate,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.action_new_group), modifier = Modifier.padding(start = 6.dp), maxLines = 1)
                }
                FilledTonalButton(onClick = onJoin, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_join_group), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
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
    val resources = LocalContext.current.resources
    val name = summary?.name ?: group.name.ifBlank { stringResource(R.string.group_default_name) }
    val subtitle = when {
        summary != null -> {
            val n = summary.count?.participants ?: 0
            resources.getQuantityString(R.plurals.participants_count, n, n)
        }
        checked -> stringResource(R.string.group_not_found_on_server)
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
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.options))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(if (group.starred) R.string.favorite_remove else R.string.favorite_add)) },
                    leadingIcon = { Icon(if (group.starred) Icons.Outlined.Star else Icons.Filled.Star, contentDescription = null) },
                    onClick = { menu = false; onStar() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.remove_from_phone)) },
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
    error: UiText?,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.join_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.join_body))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.join_label)) },
                    isError = error != null,
                    supportingText = if (error != null) {
                        { Text(error.asString()) }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(text) }, enabled = !busy && text.isNotBlank()) {
                Text(stringResource(if (busy) R.string.join_checking else R.string.join_action))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
