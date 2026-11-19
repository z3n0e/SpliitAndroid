package app.spliitclient

import android.app.Application
import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import app.spliitclient.data.ExchangeRates
import app.spliitclient.data.LocalStore
import app.spliitclient.data.Repository
import app.spliitclient.data.SpliitApi
import app.spliitclient.data.TrpcClient

class SpliitApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(context: Context) {
    val store = LocalStore(context)
    val api = SpliitApi(TrpcClient { store.serverUrl.value })
    val rates = ExchangeRates(context)
    val repo = Repository(api, store, rates)
}

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer mancante") }
