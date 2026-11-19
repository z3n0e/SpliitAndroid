package app.spliitclient

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.spliitclient.ui.AppNav
import app.spliitclient.ui.theme.SpliitTheme
import app.spliitclient.util.Links
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    /** Gruppo da aprire arrivato da un link o da "Condividi". */
    private val pendingGroup = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)

        val container = (application as SpliitApp).container
        setContent {
            val dynamic by container.store.dynamicColor.collectAsStateWithLifecycle()
            SpliitTheme(dynamicColor = dynamic) {
                CompositionLocalProvider(LocalContainer provides container) {
                    AppNav(pendingGroup = pendingGroup, onPendingConsumed = { pendingGroup.value = null })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val text = when (intent.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        } ?: return
        Links.extractFromLink(text.take(2_000))?.let { pendingGroup.value = it }
    }
}
