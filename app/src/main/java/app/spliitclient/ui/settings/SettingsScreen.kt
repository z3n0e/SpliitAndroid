@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package app.spliitclient.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.spliitclient.BuildConfig
import app.spliitclient.LocalContainer
import app.spliitclient.data.LocalStore
import app.spliitclient.util.Links

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val container = LocalContainer.current
    val store = container.store
    val context = LocalContext.current
    val server by store.serverUrl.collectAsStateWithLifecycle()
    val dynamic by store.dynamicColor.collectAsStateWithLifecycle()
    var serverText by rememberSaveable { mutableStateOf(server) }
    var serverMessage by rememberSaveable { mutableStateOf<String?>(null) }

    fun applyServer(value: String) {
        val normalized = Links.normalizeServer(value)
        if (normalized == null) {
            serverMessage = "Indirizzo non valido: deve iniziare con https://"
            return
        }
        store.setServerUrl(normalized)
        container.repo.clearCache()
        serverText = normalized
        serverMessage = "Server salvato. Vedrai i gruppi salvati per questo server."
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text("Impostazioni") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro") }
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
            Card {
                Text("Server", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Usa spliit.app oppure la tua istanza self-hosted. Sono ammessi solo indirizzi https.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = serverText,
                    onValueChange = { serverText = it.take(200); serverMessage = null },
                    singleLine = true,
                    label = { Text("Indirizzo") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                serverMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { applyServer(serverText) }, enabled = serverText.trim() != server) {
                        Text("Salva server")
                    }
                    if (server != LocalStore.DEFAULT_SERVER) {
                        TextButton(onClick = { applyServer(LocalStore.DEFAULT_SERVER) }) { Text("Torna a spliit.app") }
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Colori dallo sfondo", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Usa i colori del tuo sfondo (Material You).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = dynamic, onCheckedChange = { store.setDynamicColor(it) })
                    }
                }
            }

            Card {
                Text("Privacy e consumi", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Nessuna pubblicità, nessun tracciamento, nessun account. I link dei gruppi restano solo su questo " +
                        "telefono e sono esclusi dai backup. L'app usa la rete solo quando apri o aggiorni una schermata: " +
                        "niente sincronizzazioni o servizi in background. Per i cambi valuta l'app chiede solo i codici delle " +
                        "valute (es. VND, EUR) a Frankfurter/BCE (come il sito di Spliit) o, se non disponibile, a currency-api; " +
                        "i tassi ottenuti restano salvati per l'uso offline.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Card {
                Text("Informazioni", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Client Android non ufficiale per Spliit (progetto open source con licenza MIT). Versione ${BuildConfig.VERSION_NAME}.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/spliit-app/spliit")))
                    } catch (e: ActivityNotFoundException) {
                        // Nessun browser: niente da fare.
                    }
                }) { Text("Codice sorgente di Spliit") }
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            content()
        }
    }
}
