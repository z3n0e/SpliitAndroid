@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package app.spliitclient.ui.settings

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.spliitclient.BuildConfig
import app.spliitclient.LocalContainer
import app.spliitclient.R
import app.spliitclient.data.LocalStore
import app.spliitclient.data.ThemeMode
import app.spliitclient.ui.components.ChoiceDialog
import app.spliitclient.ui.components.FieldButton
import app.spliitclient.ui.theme.AccentColor
import app.spliitclient.ui.theme.dynamicColorAvailable
import app.spliitclient.util.AppLanguage
import app.spliitclient.util.Links

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val container = LocalContainer.current
    val store = container.store
    val context = LocalContext.current
    val server by store.serverUrl.collectAsStateWithLifecycle()
    val appearance by store.appearance.collectAsStateWithLifecycle()
    var serverText by rememberSaveable { mutableStateOf(server) }
    var serverMessage by rememberSaveable { mutableStateOf<Int?>(null) }
    var pickLanguage by remember { mutableStateOf(false) }
    val language = remember { AppLanguage.current(context, store.language) }

    fun applyServer(value: String) {
        val normalized = Links.normalizeServer(value)
        if (normalized == null) {
            serverMessage = R.string.server_invalid
            return
        }
        store.setServerUrl(normalized)
        container.repo.clearCache()
        serverText = normalized
        serverMessage = R.string.server_saved
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---- Appearance ----
            Card {
                Text(stringResource(R.string.appearance), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.theme), style = MaterialTheme.typography.labelLarge)
                val modes = ThemeMode.entries
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    modes.forEachIndexed { i, mode ->
                        SegmentedButton(
                            selected = appearance.mode == mode,
                            onClick = { store.setThemeMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = modes.size),
                            label = {
                                Text(
                                    stringResource(
                                        when (mode) {
                                            ThemeMode.SYSTEM -> R.string.theme_system
                                            ThemeMode.LIGHT -> R.string.theme_light
                                            ThemeMode.DARK -> R.string.theme_dark
                                        },
                                    ),
                                    maxLines = 1,
                                )
                            },
                        )
                    }
                }
                SwitchRow(
                    title = stringResource(R.string.amoled),
                    subtitle = stringResource(R.string.amoled_hint),
                    checked = appearance.amoled,
                    onChange = store::setAmoled,
                )
                if (dynamicColorAvailable()) {
                    SwitchRow(
                        title = stringResource(R.string.dynamic_color),
                        subtitle = stringResource(R.string.dynamic_color_hint),
                        checked = appearance.dynamicColor,
                        onChange = store::setDynamicColor,
                    )
                }
                Text(stringResource(R.string.accent_color), style = MaterialTheme.typography.labelLarge)
                if (appearance.dynamicColor && dynamicColorAvailable()) {
                    Text(
                        stringResource(R.string.accent_hint_dynamic),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AccentColor.entries.forEach { accent ->
                        Swatch(
                            color = Color(accent.seed),
                            label = stringResource(accent.label),
                            selected = (!appearance.dynamicColor || !dynamicColorAvailable()) &&
                                AccentColor.from(appearance.accent) == accent,
                            onClick = { store.setAccent(accent.name) },
                        )
                    }
                }
            }

            // ---- Language ----
            Card {
                FieldButton(
                    label = stringResource(R.string.language),
                    value = languageName(language),
                    onClick = { pickLanguage = true },
                )
            }

            // ---- Server ----
            Card {
                Text(stringResource(R.string.server), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.server_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = serverText,
                    onValueChange = { serverText = it.take(200); serverMessage = null },
                    singleLine = true,
                    label = { Text(stringResource(R.string.server_address)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                serverMessage?.let {
                    Text(
                        stringResource(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (it == R.string.server_invalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { applyServer(serverText) }, enabled = serverText.trim() != server) {
                        Text(stringResource(R.string.server_save))
                    }
                    if (server != LocalStore.DEFAULT_SERVER) {
                        TextButton(onClick = { applyServer(LocalStore.DEFAULT_SERVER) }) { Text(stringResource(R.string.server_reset)) }
                    }
                }
            }

            // ---- Privacy ----
            Card {
                Text(stringResource(R.string.privacy_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.privacy_body), style = MaterialTheme.typography.bodyMedium)
            }

            // ---- About ----
            Card {
                Text(stringResource(R.string.about_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.about_body, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/spliit-app/spliit")))
                    } catch (e: ActivityNotFoundException) {
                        // No browser installed.
                    }
                }) { Text(stringResource(R.string.source_code)) }
            }
        }
    }

    if (pickLanguage) {
        ChoiceDialog(
            title = stringResource(R.string.language),
            options = AppLanguage.supported,
            selected = language,
            label = { languageName(it) },
            onDismiss = { pickLanguage = false },
            onSelect = { tag ->
                pickLanguage = false
                if (tag != language) {
                    store.language = tag
                    context.findActivity()?.let { AppLanguage.apply(it, tag) }
                }
            },
        )
    }
}

@Composable
private fun languageName(tag: String): String = when (tag) {
    "en" -> "English"
    "it" -> "Italiano"
    else -> stringResource(R.string.language_system)
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Swatch(color: Color, label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = color,
        border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null,
        modifier = Modifier.size(48.dp).semantics { contentDescription = label },
    ) {
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.padding(12.dp))
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            content()
        }
    }
}
