package app.spliitclient

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.spliitclient.ui.AppNav
import app.spliitclient.ui.theme.SpliitTheme
import app.spliitclient.ui.theme.isDark
import app.spliitclient.util.AppLanguage
import app.spliitclient.util.Links
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    /** Group to open, coming from a link or from "Share". */
    private val pendingGroup = MutableStateFlow<String?>(null)

    override fun attachBaseContext(newBase: Context) {
        // Android 12 and older: apply the language chosen in the app.
        val language = (newBase.applicationContext as? SpliitApp)?.container?.store?.language.orEmpty()
        super.attachBaseContext(AppLanguage.wrap(newBase, language))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)

        val container = (application as SpliitApp).container
        setContent {
            val appearance by container.store.appearance.collectAsStateWithLifecycle()
            val dark = appearance.isDark()
            // Status/navigation bar icons follow the app theme, not only the system one.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                )
                onDispose {}
            }
            SpliitTheme(appearance = appearance, dark = dark) {
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

    private companion object {
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
