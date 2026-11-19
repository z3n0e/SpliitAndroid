@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package app.spliitclient.ui.expense

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.spliitclient.LocalContainer
import app.spliitclient.R
import app.spliitclient.data.Group
import app.spliitclient.data.Recurrence
import app.spliitclient.data.SplitMode
import app.spliitclient.ui.components.ChoiceDialog
import app.spliitclient.ui.components.ConfirmDialog
import app.spliitclient.ui.components.ErrorBox
import app.spliitclient.ui.components.FieldButton
import app.spliitclient.ui.components.LoadingBox
import app.spliitclient.util.Categories
import app.spliitclient.util.Links
import app.spliitclient.util.Money
import app.spliitclient.util.asString
import app.spliitclient.util.uiText
import app.spliitclient.util.WorldCurrencies
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private enum class Picker { None, Date, Category, PaidBy, Recurrence, Currency }

@Composable
fun ExpenseFormScreen(route: ExpenseRoute, onDone: () -> Unit) {
    val container = LocalContainer.current
    val vm: ExpenseFormViewModel = viewModel { ExpenseFormViewModel(route, container.repo) }
    var picker by remember { mutableStateOf(Picker.None) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(vm.done) { if (vm.done) onDone() }
    val reimbursementTitle = stringResource(R.string.reimbursement_title)
    LaunchedEffect(vm.loading) { if (!vm.loading) vm.applyDefaultTitle(reimbursementTitle) }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (vm.isEdit) R.string.expense_edit_title else R.string.expense_new_title)) },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close_without_saving)) }
                },
                actions = {
                    if (vm.isEdit && vm.group != null) {
                        IconButton(onClick = { confirmDelete = true }, enabled = !vm.deleting) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.expense_delete))
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (vm.group != null && !vm.loading) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    Button(
                        onClick = vm::save,
                        enabled = !vm.saving && !vm.deleting,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .height(56.dp),
                    ) {
                        Text(
                            stringResource(if (vm.saving) R.string.saving else if (vm.isEdit) R.string.action_save_changes else R.string.action_add_expense),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        },
    ) { padding ->
        val group = vm.group
        when {
            vm.loading -> LoadingBox(Modifier.padding(padding))
            vm.loadError != null || group == null ->
                ErrorBox(vm.loadError ?: uiText(R.string.error_unexpected), onRetry = vm::retry, modifier = Modifier.padding(padding))
            else -> FormBody(vm, group, Modifier.padding(padding), onPick = { picker = it })
        }
    }

    val group = vm.group
    if (group != null) {
        when (picker) {
            Picker.None -> Unit
            Picker.Date -> DateDialog(vm.date, onDismiss = { picker = Picker.None }) { vm.updateDate(it); picker = Picker.None }
            Picker.Currency -> ChoiceDialog(
                title = stringResource(R.string.currency_paid_in),
                options = WorldCurrencies.ordered(vm.suggestedCurrencies(), exclude = group.currencyCode?.uppercase()),
                selected = vm.originalCurrency,
                label = { WorldCurrencies.label(it) },
                onDismiss = { picker = Picker.None },
                onSelect = { vm.chooseOriginalCurrency(it); picker = Picker.None },
            )
            Picker.Category -> ChoiceDialog(
                title = stringResource(R.string.category),
                options = Categories.all,
                selected = Categories.get(vm.categoryId),
                label = { "${it.emoji}  ${stringResource(it.name)}" },
                onDismiss = { picker = Picker.None },
                onSelect = { vm.categoryId = it.id; picker = Picker.None },
            )
            Picker.PaidBy -> ChoiceDialog(
                title = stringResource(R.string.who_paid),
                options = group.participants,
                selected = group.participants.firstOrNull { it.id == vm.paidBy },
                label = { it.name },
                onDismiss = { picker = Picker.None },
                onSelect = { vm.paidBy = it.id; picker = Picker.None },
            )
            Picker.Recurrence -> ChoiceDialog(
                title = stringResource(R.string.repeats),
                options = Recurrence.entries.toList(),
                selected = vm.recurrence,
                label = { stringResource(it.label) },
                onDismiss = { picker = Picker.None },
                onSelect = { vm.recurrence = it; picker = Picker.None },
            )
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.expense_delete_title),
            text = stringResource(R.string.expense_delete_text),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { confirmDelete = false; vm.delete() },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun FormBody(vm: ExpenseFormViewModel, group: Group, modifier: Modifier, onPick: (Picker) -> Unit) {
    val context = LocalContext.current
    val code = group.currencyCode
    val dateLabel = formatDate(vm.date)
    val category = Categories.get(vm.categoryId)
    val payer = group.participants.firstOrNull { it.id == vm.paidBy }?.name ?: stringResource(R.string.choose)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        OutlinedTextField(
            value = vm.title,
            onValueChange = { if (it.length <= 200) vm.title = it },
            label = { Text(stringResource(R.string.expense_title_label)) },
            placeholder = { Text(stringResource(R.string.expense_title_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )

        AmountSection(vm, group, onPickCurrency = { onPick(Picker.Currency) })

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistChip(
                onClick = { onPick(Picker.Date) },
                label = { Text(dateLabel) },
                leadingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
            )
            AssistChip(
                onClick = { onPick(Picker.Category) },
                label = { Text("${category.emoji}  ${stringResource(category.name)}") },
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldButton(stringResource(R.string.paid_by), payer, onClick = { onPick(Picker.PaidBy) }, modifier = Modifier.weight(1f))
            FieldButton(stringResource(R.string.repeats), stringResource(vm.recurrence.label), onClick = { onPick(Picker.Recurrence) }, modifier = Modifier.weight(1f))
        }

        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.is_reimbursement), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.is_reimbursement_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = vm.isReimbursement, onCheckedChange = { vm.isReimbursement = it })
            }
        }

        SplitSection(vm, group)

        OutlinedTextField(
            value = vm.notes,
            onValueChange = { if (it.length <= 5000) vm.notes = it },
            label = { Text(stringResource(R.string.notes_optional)) },
            minLines = 2,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )

        if (vm.documents.isNotEmpty()) {
            Text(stringResource(R.string.receipts), style = MaterialTheme.typography.titleSmall)
            vm.documents.forEachIndexed { i, d ->
                TextButton(
                    onClick = {
                        if (Links.isSafeWebUrl(d.url)) {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(d.url)))
                            } catch (e: ActivityNotFoundException) {
                                // Nessun browser installato: ignoriamo.
                            }
                        }
                    },
                ) { Text(stringResource(R.string.open_receipt, i + 1)) }
            }
            Text(
                stringResource(R.string.receipts_web_only),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        vm.error?.let {
            Text(it.asString(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AmountSection(vm: ExpenseFormViewModel, group: Group, onPickCurrency: () -> Unit) {
    val groupCode = group.currencyCode?.uppercase().orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!vm.convert) {
            OutlinedTextField(
                value = vm.amountText,
                onValueChange = { if (it.length <= 16) vm.updateAmount(it) },
                label = { Text(stringResource(R.string.amount)) },
                prefix = { Text(group.currency + " ") },
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            OutlinedTextField(
                value = vm.originalAmountText,
                onValueChange = { if (it.length <= 16) vm.updateOriginalAmount(it) },
                label = { Text(stringResource(R.string.amount_paid)) },
                prefix = { Text(vm.originalCurrency + " ") },
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.converted_amount), style = MaterialTheme.typography.labelLarge)
                    val groupMinor = Money.parse(vm.amountText, group.currencyCode)
                    Text(
                        if (groupMinor == null) "—" else Money.format(groupMinor, group),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = vm.rateText,
                            onValueChange = { if (it.length <= 20) vm.updateRate(it) },
                            label = { Text("1 ${vm.originalCurrency} = ? $groupCode") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                        )
                        if (vm.rateLoading) {
                            LoadingIndicator(Modifier.padding(start = 8.dp).size(40.dp))
                        } else {
                            IconButton(onClick = vm::refreshRate) {
                                Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.refresh_rate))
                            }
                        }
                    }
                    vm.rateInfo?.let {
                        Text(it.asString(), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (vm.canConvert) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = vm.convert,
                    onClick = { vm.switchConversion(!vm.convert) },
                    label = { Text(stringResource(R.string.paid_other_currency)) },
                    leadingIcon = if (vm.convert) {
                        { Icon(Icons.Filled.Check, contentDescription = null) }
                    } else null,
                )
                if (vm.convert) {
                    AssistChip(onClick = onPickCurrency, label = { Text(WorldCurrencies.label(vm.originalCurrency)) })
                }
            }
        }
    }
}

@Composable
private fun SplitSection(vm: ExpenseFormViewModel, group: Group) {
    val code = group.currencyCode
    val preview = vm.preview()
    val modes = SplitMode.entries
    val allSelected = group.participants.all { vm.selected[it.id] == true }

    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.for_whom), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.setAll(!allSelected) }) { Text(stringResource(if (allSelected) R.string.select_none else R.string.select_all)) }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                modes.forEachIndexed { i, mode ->
                    SegmentedButton(
                        selected = vm.splitMode == mode,
                        onClick = { vm.changeMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = modes.size),
                        label = { Text(stringResource(mode.shortLabel), maxLines = 1) },
                    )
                }
            }
            Text(
                stringResource(vm.splitMode.label),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            group.participants.forEach { p ->
                val checked = vm.selected[p.id] == true
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = checked, onCheckedChange = { vm.toggle(p.id, it) })
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val share = preview[p.id]
                        if (checked && share != null) {
                            Text(
                                Money.format(share, group),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (checked && vm.splitMode != SplitMode.EVENLY) {
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = vm.values[p.id].orEmpty(),
                            onValueChange = { if (it.length <= 12) vm.updateValue(p.id, it) },
                            singleLine = true,
                            prefix = {
                                if (vm.splitMode == SplitMode.BY_AMOUNT) Text(group.currency + " ")
                            },
                            suffix = {
                                when (vm.splitMode) {
                                    SplitMode.BY_PERCENTAGE -> Text(" %")
                                    SplitMode.BY_SHARES -> Text(" ×")
                                    else -> Unit
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.width(132.dp),
                        )
                    }
                }
            }
            val allocated = vm.allocated()
            if (allocated != null) {
                val (text, ok) = when (vm.splitMode) {
                    SplitMode.BY_PERCENTAGE -> {
                        val pct = Money.plainNumber(java.math.BigDecimal.valueOf(allocated).movePointLeft(2))
                        stringResource(R.string.percent_total, pct) to (allocated == 10_000L)
                    }
                    else -> {
                        val total = Money.parse(vm.amountText, code) ?: 0L
                        stringResource(R.string.amount_allocated, Money.format(allocated, group), Money.format(total, group)) to (allocated == total)
                    }
                }
                Text(
                    text,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun DateDialog(current: LocalDate, onDismiss: () -> Unit, onPicked: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = current.toEpochDay() * DAY_MS)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = state.selectedDateMillis
                if (millis != null) onPicked(LocalDate.ofEpochDay(Math.floorDiv(millis, DAY_MS))) else onDismiss()
            }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        DatePicker(state = state)
    }
}

private const val DAY_MS = 86_400_000L

@Composable
private fun formatDate(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> stringResource(R.string.today)
        today.minusDays(1) -> stringResource(R.string.yesterday)
        else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
}
