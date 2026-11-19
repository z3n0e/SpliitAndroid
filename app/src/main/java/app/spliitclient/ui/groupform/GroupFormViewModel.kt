package app.spliitclient.ui.groupform

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.spliitclient.data.GroupFormParticipant
import app.spliitclient.data.GroupFormValues
import app.spliitclient.data.Repository
import app.spliitclient.util.CurrencyTable
import app.spliitclient.util.attempt
import app.spliitclient.util.userMessage
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class GroupFormRoute(val groupId: String? = null)

data class ParticipantDraft(val key: Long, val id: String?, val name: String)

class GroupFormViewModel(private val groupId: String?, private val repo: Repository) : ViewModel() {

    val isEdit = groupId != null

    var loading by mutableStateOf(groupId != null)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    /** Valorizzato a salvataggio riuscito (id del gruppo). */
    var savedGroupId by mutableStateOf<String?>(null)
        private set

    var name by mutableStateOf("")
    /** Codice ISO della valuta; stringa vuota = simbolo personalizzato. */
    var currencyCode by mutableStateOf(CurrencyTable.defaultCode())
    var customSymbol by mutableStateOf("")
    var information by mutableStateOf("")
    val participants = mutableStateListOf<ParticipantDraft>()
    /** In creazione: quale partecipante sei tu (indice), -1 = nessuno. */
    var meIndex by mutableIntStateOf(0)

    private var lockedIds: Set<String> = emptySet()
    /** Codice ISO conservato quando il gruppo usa un simbolo non standard. */
    private var customCode: String = ""
    private var nextKey = 0L

    init {
        if (groupId == null) {
            participants += ParticipantDraft(nextKey++, null, "")
            participants += ParticipantDraft(nextKey++, null, "")
        } else {
            load()
        }
    }

    fun load() {
        val id = groupId ?: return
        loading = true
        loadError = null
        viewModelScope.launch {
            attempt { repo.api.getGroupDetails(id) }
                .onSuccess { details ->
                    val g = details.group
                    name = g.name
                    information = g.information.orEmpty()
                    val code = g.currencyCode.orEmpty()
                    if (code.isNotEmpty() && CurrencyTable.entries.any { it.code == code } && CurrencyTable.symbol(code) == g.currency) {
                        currencyCode = code
                    } else {
                        currencyCode = ""
                        customSymbol = g.currency
                        customCode = code
                    }
                    lockedIds = details.participantsWithExpenses.toSet()
                    participants.clear()
                    g.participants.forEach { participants += ParticipantDraft(nextKey++, it.id, it.name) }
                    loading = false
                }
                .onFailure {
                    loadError = it.userMessage()
                    loading = false
                }
        }
    }

    fun isLocked(draft: ParticipantDraft): Boolean = draft.id != null && draft.id in lockedIds

    fun addParticipant() {
        if (participants.size >= 100) return
        participants += ParticipantDraft(nextKey++, null, "")
    }

    fun removeParticipant(index: Int) {
        val draft = participants.getOrNull(index) ?: return
        if (isLocked(draft) || participants.size <= 1) return
        participants.removeAt(index)
        if (meIndex == index) meIndex = -1 else if (meIndex > index) meIndex--
    }

    fun rename(index: Int, value: String) {
        val draft = participants.getOrNull(index) ?: return
        if (value.length <= 50) participants[index] = draft.copy(name = value)
    }

    private fun validate(): String? {
        val n = name.trim()
        if (n.length !in 2..50) return "Il nome del gruppo deve avere tra 2 e 50 caratteri."
        val symbol = symbol()
        if (symbol.isEmpty() || symbol.length > 5) return "Il simbolo della valuta deve avere da 1 a 5 caratteri."
        if (information.length > 10_000) return "Le informazioni sono troppo lunghe."
        val names = participants.map { it.name.trim() }
        if (names.isEmpty()) return "Serve almeno un partecipante."
        if (names.any { it.length !in 2..50 }) return "Ogni nome deve avere tra 2 e 50 caratteri."
        if (names.toSet().size != names.size) return "Due partecipanti hanno lo stesso nome."
        return null
    }

    private fun symbol(): String =
        if (currencyCode.isEmpty()) customSymbol.trim() else CurrencyTable.symbol(currencyCode)

    fun save() {
        if (saving) return
        val problem = validate()
        if (problem != null) {
            error = problem
            return
        }
        error = null
        saving = true
        val form = GroupFormValues(
            name = name.trim(),
            information = information.trim(),
            currency = symbol(),
            currencyCode = if (currencyCode.isEmpty()) customCode else currencyCode,
            participants = participants.map { GroupFormParticipant(id = it.id, name = it.name.trim()) },
        )
        val myName = participants.getOrNull(meIndex)?.name?.trim()
        viewModelScope.launch {
            val id = groupId
            if (id == null) {
                attempt {
                    val newId = repo.api.createGroup(form)
                    repo.store.upsertGroup(newId, form.name, touch = true)
                    // Ricaviamo il tuo id partecipante dal nome scelto.
                    if (myName != null) {
                        val created = repo.loadGroup(newId, force = true)
                        val me = created?.participants?.firstOrNull { it.name == myName }
                        if (me != null) repo.store.setActiveParticipant(newId, me.id)
                    }
                    newId
                }.onSuccess { savedGroupId = it }
                    .onFailure { error = it.userMessage(); saving = false }
            } else {
                attempt { repo.api.updateGroup(id, form, repo.store.activeParticipant(id)) }
                    .onSuccess {
                        repo.store.upsertGroup(id, form.name)
                        repo.notifyChanged(id)
                        savedGroupId = id
                    }
                    .onFailure { error = it.userMessage(); saving = false }
            }
        }
    }

    fun removeFromDevice() {
        groupId?.let { repo.store.removeGroup(it) }
    }
}
