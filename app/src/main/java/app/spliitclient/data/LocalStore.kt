package app.spliitclient.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class SavedGroup(
    val id: String,
    val server: String = LocalStore.DEFAULT_SERVER,
    val name: String = "",
    val starred: Boolean = false,
    val participantId: String? = null,
    val lastOpened: Long = 0L,
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Appearance preferences: theme, pure black (AMOLED), wallpaper colors and accent color. */
data class Appearance(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val amoled: Boolean = false,
    val dynamicColor: Boolean = true,
    val accent: String = "TEAL",
)

/**
 * Dati locali (solo su questo dispositivo, nella memoria privata dell'app):
 * gruppi salvati, "chi sei" in ogni gruppo, server e preferenze.
 * Le scritture sono asincrone (apply) per non bloccare l'interfaccia.
 */
class LocalStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("spliit_store", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(SavedGroup.serializer())
    private val lock = Any()

    private val _groups = MutableStateFlow(readGroups())
    val groups: StateFlow<List<SavedGroup>> = _groups.asStateFlow()

    private val _serverUrl = MutableStateFlow(prefs.getString(KEY_SERVER, null) ?: DEFAULT_SERVER)
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _appearance = MutableStateFlow(
        Appearance(
            mode = ThemeMode.entries.firstOrNull { it.name == prefs.getString(KEY_THEME, null) } ?: ThemeMode.SYSTEM,
            amoled = prefs.getBoolean(KEY_AMOLED, false),
            dynamicColor = prefs.getBoolean(KEY_DYNAMIC, true),
            accent = prefs.getString(KEY_ACCENT, null) ?: "TEAL",
        ),
    )
    val appearance: StateFlow<Appearance> = _appearance.asStateFlow()

    /** App language for Android 12 and older ("" = follow the system). Android 13+ uses the system setting. */
    var language: String
        get() = prefs.getString(KEY_LANGUAGE, null).orEmpty()
        set(value) {
            prefs.edit().putString(KEY_LANGUAGE, value).apply()
        }

    private fun readGroups(): List<SavedGroup> = try {
        json.decodeFromString(listSerializer, prefs.getString(KEY_GROUPS, null) ?: "[]")
    } catch (e: Exception) {
        emptyList()
    }

    private fun mutate(block: (List<SavedGroup>) -> List<SavedGroup>) {
        val updated = synchronized(lock) {
            val current = _groups.value
            val next = block(current)
            if (next == current) return // niente da salvare: nessuna scrittura su disco
            _groups.value = next
            next
        }
        prefs.edit().putString(KEY_GROUPS, json.encodeToString(listSerializer, updated)).apply()
    }

    private fun matches(g: SavedGroup, id: String) = g.id == id && g.server == _serverUrl.value

    fun visibleGroups(): List<SavedGroup> = sortGroups(_groups.value.filter { it.server == _serverUrl.value })

    fun sortGroups(list: List<SavedGroup>): List<SavedGroup> =
        list.sortedWith(compareByDescending<SavedGroup> { it.starred }.thenByDescending { it.lastOpened })

    fun upsertGroup(id: String, name: String? = null, touch: Boolean = false) = mutate { list ->
        val existing = list.firstOrNull { matches(it, id) }
        if (existing == null) {
            list + SavedGroup(
                id = id,
                server = _serverUrl.value,
                name = name.orEmpty(),
                lastOpened = System.currentTimeMillis(),
            )
        } else {
            list.map {
                if (it === existing) it.copy(
                    name = name ?: it.name,
                    lastOpened = if (touch) System.currentTimeMillis() else it.lastOpened,
                ) else it
            }
        }
    }

    fun touch(id: String) = upsertGroup(id, touch = true)

    /** Aggiorna i nomi di più gruppi con una sola scrittura. */
    fun updateNames(names: Map<String, String>) = mutate { list ->
        list.map { g ->
            val n = if (g.server == _serverUrl.value) names[g.id] else null
            if (n != null && n != g.name) g.copy(name = n) else g
        }
    }

    fun removeGroup(id: String) = mutate { list -> list.filterNot { matches(it, id) } }

    fun setStarred(id: String, starred: Boolean) = mutate { list ->
        list.map { if (matches(it, id)) it.copy(starred = starred) else it }
    }

    fun setActiveParticipant(groupId: String, participantId: String?) = mutate { list ->
        list.map { if (matches(it, groupId)) it.copy(participantId = participantId) else it }
    }

    fun activeParticipant(groupId: String): String? =
        _groups.value.firstOrNull { matches(it, groupId) }?.participantId

    fun setServerUrl(url: String) {
        _serverUrl.value = url
        prefs.edit().putString(KEY_SERVER, url).apply()
    }

    fun setThemeMode(mode: ThemeMode) {
        _appearance.value = _appearance.value.copy(mode = mode)
        prefs.edit().putString(KEY_THEME, mode.name).apply()
    }

    fun setAmoled(enabled: Boolean) {
        _appearance.value = _appearance.value.copy(amoled = enabled)
        prefs.edit().putBoolean(KEY_AMOLED, enabled).apply()
    }

    fun setDynamicColor(enabled: Boolean) {
        _appearance.value = _appearance.value.copy(dynamicColor = enabled)
        prefs.edit().putBoolean(KEY_DYNAMIC, enabled).apply()
    }

    fun setAccent(name: String) {
        _appearance.value = _appearance.value.copy(accent = name, dynamicColor = false)
        prefs.edit().putString(KEY_ACCENT, name).putBoolean(KEY_DYNAMIC, false).apply()
    }

    companion object {
        const val DEFAULT_SERVER = "https://spliit.app"
        private const val KEY_GROUPS = "groups"
        private const val KEY_SERVER = "server"
        private const val KEY_DYNAMIC = "dynamic_color"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_AMOLED = "amoled"
        private const val KEY_ACCENT = "accent"
        private const val KEY_LANGUAGE = "language"
    }
}
