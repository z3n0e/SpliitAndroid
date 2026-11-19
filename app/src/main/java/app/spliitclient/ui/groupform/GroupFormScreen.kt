@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package app.spliitclient.ui.groupform

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.spliitclient.LocalContainer
import app.spliitclient.R
import app.spliitclient.ui.components.ChoiceDialog
import app.spliitclient.ui.components.ConfirmDialog
import app.spliitclient.ui.components.ErrorBox
import app.spliitclient.ui.components.FieldButton
import app.spliitclient.ui.components.LoadingBox
import app.spliitclient.util.CurrencyTable
import app.spliitclient.util.asString
import app.spliitclient.util.uiText

@Composable
fun GroupFormScreen(groupId: String?, onDone: (String?) -> Unit, onRemoved: () -> Unit) {
    val container = LocalContainer.current
    val vm: GroupFormViewModel = viewModel { GroupFormViewModel(groupId, container.repo) }
    var pickCurrency by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }

    LaunchedEffect(vm.savedGroupId) { vm.savedGroupId?.let { onDone(it) } }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (vm.isEdit) R.string.group_settings else R.string.action_new_group)) },
                navigationIcon = {
                    IconButton(onClick = { onDone(null) }) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close_without_saving)) }
                },
            )
        },
        bottomBar = {
            if (!vm.loading && vm.loadError == null) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    Button(
                        onClick = vm::save,
                        enabled = !vm.saving,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .height(56.dp),
                    ) {
                        Text(
                            stringResource(
                                when {
                                    vm.saving -> R.string.saving
                                    vm.isEdit -> R.string.action_save_changes
                                    else -> R.string.action_create_group
                                },
                            ),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        },
    ) { padding ->
        when {
            vm.loading -> LoadingBox(Modifier.padding(padding))
            vm.loadError != null -> ErrorBox(vm.loadError ?: uiText(R.string.error_unexpected), onRetry = vm::load, modifier = Modifier.padding(padding))
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                OutlinedTextField(
                    value = vm.name,
                    onValueChange = { if (it.length <= 50) vm.name = it },
                    label = { Text(stringResource(R.string.group_name)) },
                    placeholder = { Text(stringResource(R.string.group_name_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )

                FieldButton(
                    label = stringResource(R.string.currency),
                    value = if (vm.currencyCode.isEmpty()) stringResource(R.string.currency_custom)
                    else "${CurrencyTable.displayName(vm.currencyCode)} (${CurrencyTable.symbol(vm.currencyCode)})",
                    onClick = { pickCurrency = true },
                )
                if (vm.currencyCode.isEmpty()) {
                    OutlinedTextField(
                        value = vm.customSymbol,
                        onValueChange = { if (it.length <= 5) vm.customSymbol = it },
                        label = { Text(stringResource(R.string.currency_symbol_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.participants), style = MaterialTheme.typography.titleMedium)
                        if (!vm.isEdit) {
                            Text(
                                stringResource(R.string.participants_me_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        vm.participants.forEachIndexed { index, draft ->
                            val locked = vm.isLocked(draft)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!vm.isEdit) {
                                    RadioButton(selected = vm.meIndex == index, onClick = { vm.meIndex = index })
                                }
                                OutlinedTextField(
                                    value = draft.name,
                                    onValueChange = { vm.rename(index, it) },
                                    singleLine = true,
                                    placeholder = { Text(stringResource(R.string.name)) },
                                    supportingText = if (locked) {
                                        { Text(stringResource(R.string.participant_locked)) }
                                    } else null,
                                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    onClick = { vm.removeParticipant(index) },
                                    enabled = !locked && vm.participants.size > 1,
                                ) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.participant_remove, draft.name)) }
                            }
                        }
                        OutlinedButton(onClick = vm::addParticipant) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.padding(start = 6.dp))
                            Text(stringResource(R.string.participant_add))
                        }
                    }
                }

                OutlinedTextField(
                    value = vm.information,
                    onValueChange = { if (it.length <= 10_000) vm.information = it },
                    label = { Text(stringResource(R.string.information_optional)) },
                    placeholder = { Text(stringResource(R.string.information_hint)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )

                vm.error?.let {
                    Text(it.asString(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }

                if (vm.isEdit) {
                    TextButton(onClick = { confirmRemove = true }) {
                        Text(stringResource(R.string.remove_group_from_phone), color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (pickCurrency) {
        val options = CurrencyTable.entries.map { it.code } + ""
        ChoiceDialog(
            title = stringResource(R.string.group_currency),
            options = options,
            selected = vm.currencyCode,
            label = { code ->
                if (code.isEmpty()) stringResource(R.string.currency_other_custom)
                else "${CurrencyTable.displayName(code)} (${CurrencyTable.symbol(code)})"
            },
            onDismiss = { pickCurrency = false },
            onSelect = { vm.currencyCode = it; pickCurrency = false },
        )
    }

    if (confirmRemove) {
        ConfirmDialog(
            title = stringResource(R.string.remove_group_from_phone_title),
            text = stringResource(R.string.remove_group_text),
            confirmLabel = stringResource(R.string.action_remove),
            onConfirm = { confirmRemove = false; vm.removeFromDevice(); onRemoved() },
            onDismiss = { confirmRemove = false },
        )
    }
}
