package app.spliitclient.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Punto unico di accesso ai dati. Tiene in memoria i gruppi già scaricati
 * (così i form si aprono subito senza rete) e notifica le schermate quando
 * un gruppo cambia, invece di fare polling.
 */
class Repository(val api: SpliitApi, val store: LocalStore, val rates: ExchangeRates) {

    private val groupCache = ConcurrentHashMap<String, Group>()
    private val _changes = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val changes: SharedFlow<String> = _changes.asSharedFlow()

    fun cachedGroup(id: String): Group? = groupCache[id]

    suspend fun loadGroup(id: String, force: Boolean = false): Group? {
        if (!force) groupCache[id]?.let { return it }
        val group = api.getGroup(id)
        if (group != null) {
            groupCache[id] = group
            store.upsertGroup(id, group.name)
        } else {
            groupCache.remove(id)
        }
        return group
    }

    fun notifyChanged(groupId: String) {
        _changes.tryEmit(groupId)
    }

    fun clearCache() = groupCache.clear()
}
