package app.spliitclient.ui.expense

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.spliitclient.data.ExpenseDetail
import app.spliitclient.data.ExpenseDocument
import app.spliitclient.data.Group
import app.spliitclient.data.Recurrence
import app.spliitclient.data.Repository
import app.spliitclient.data.SplitMode
import app.spliitclient.util.Money
import app.spliitclient.util.attempt
import app.spliitclient.util.userMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Serializable
data class ExpenseRoute(
    val groupId: String,
    val expenseId: String? = null,
    val reimburseFrom: String? = null,
    val reimburseTo: String? = null,
    val reimburseAmount: Long = 0L,
)

class ExpenseFormViewModel(private val route: ExpenseRoute, private val repo: Repository) : ViewModel() {

    val isEdit = route.expenseId != null

    var group by mutableStateOf<Group?>(null)
        private set
    var loading by mutableStateOf(true)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var saving by mutableStateOf(false)
        private set
    var deleting by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var done by mutableStateOf(false)
        private set

    var title by mutableStateOf("")
    var amountText by mutableStateOf("")
    var date by mutableStateOf(LocalDate.now())
    var categoryId by mutableIntStateOf(0)
    var paidBy by mutableStateOf<String?>(null)
    var isReimbursement by mutableStateOf(false)
    var splitMode by mutableStateOf(SplitMode.EVENLY)
        private set
    var notes by mutableStateOf("")
    var recurrence by mutableStateOf(Recurrence.NONE)
    var documents by mutableStateOf<List<ExpenseDocument>>(emptyList())
        private set

    // ---- Importo pagato in un'altra valuta (compatibile con il sito: stessi campi di Spliit) ----
    var convert by mutableStateOf(false)
        private set
    var originalCurrency by mutableStateOf("")
        private set
    var originalAmountText by mutableStateOf("")
        private set
    var rateText by mutableStateOf("")
        private set
    var rateInfo by mutableStateOf<String?>(null)
        private set
    var rateLoading by mutableStateOf(false)
        private set
    private var rateManual = false
    private var rateJob: Job? = null

    /** La conversione richiede che il gruppo abbia un codice valuta ISO (es. EUR). */
    val canConvert: Boolean
        get() = group?.currencyCode?.trim()?.length == 3

    /** Chi partecipa alla spesa. */
    val selected = mutableStateMapOf<String, Boolean>()

    /** Valore per persona: quote, percentuale o importo, a seconda della modalità. */
    val values = mutableStateMapOf<String, String>()


    /**
     * true finché i valori per persona sono stati calcolati dall'app: in quel caso li
     * ricalcoliamo quando cambi importo o persone. Appena ne modifichi uno a mano, li lasciamo stare.
     */
    private var autoValues = true

    init {
        viewModelScope.launch { load() }
    }

    fun retry() {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        loading = true
        loadError = null
        val g = attempt { repo.loadGroup(route.groupId) }.getOrElse {
            loadError = it.userMessage(); loading = false; return
        }
        if (g == null) {
            loadError = "Gruppo non trovato."; loading = false; return
        }
        group = g
        val code = g.currencyCode
        val expenseId = route.expenseId
        when {
            expenseId != null -> {
                val e = attempt { repo.api.getExpense(route.groupId, expenseId) }.getOrElse {
                    loadError = it.userMessage(); loading = false; return
                }
                fill(e, g)
            }
            route.reimburseFrom != null && route.reimburseTo != null -> {
                title = "Rimborso"
                isReimbursement = true
                categoryId = 1
                paidBy = route.reimburseFrom
                amountText = Money.toInput(route.reimburseAmount, code)
                g.participants.forEach { selected[it.id] = it.id == route.reimburseTo }
            }
            else -> {
                val active = repo.store.activeParticipant(route.groupId)
                    ?.takeIf { id -> g.participants.any { it.id == id } }
                paidBy = active ?: g.participants.firstOrNull()?.id
                g.participants.forEach { selected[it.id] = true }
            }
        }
        loading = false
    }

    private fun fill(e: ExpenseDetail, g: Group) {
        autoValues = false
        val code = g.currencyCode
        title = e.title
        amountText = Money.toInput(e.amount, code)
        date = try {
            LocalDate.parse(e.expenseDate.take(10))
        } catch (ex: Exception) {
            LocalDate.now()
        }
        categoryId = e.categoryId
        paidBy = e.paidById
        isReimbursement = e.isReimbursement
        splitMode = SplitMode.from(e.splitMode)
        notes = e.notes.orEmpty()
        recurrence = Recurrence.from(e.recurrenceRule)
        documents = e.documents
        val savedRate = (e.conversionRate as? JsonPrimitive)?.contentOrNull?.toBigDecimalOrNull()
        val oc = e.originalCurrency?.uppercase()
        if (e.originalAmount != null && oc != null && oc.length == 3 && savedRate != null &&
            savedRate.signum() > 0 && code != null && code.length == 3 && oc != code.uppercase()
        ) {
            convert = true
            originalCurrency = oc
            originalAmountText = Money.toInput(e.originalAmount, oc)
            rateText = formatRate(savedRate)
            rateManual = true // teniamo il tasso usato quando la spesa è stata creata
            rateInfo = "Tasso salvato con la spesa. Tocca ↻ per usare quello di oggi."
        }
        g.participants.forEach { p -> selected[p.id] = e.paidFor.any { it.participantId == p.id } }
        e.paidFor.forEach { pf ->
            values[pf.participantId] = when (splitMode) {
                SplitMode.BY_AMOUNT -> Money.toInput(pf.shares, code)
                SplitMode.BY_SHARES, SplitMode.BY_PERCENTAGE ->
                    Money.plainNumber(BigDecimal.valueOf(pf.shares).movePointLeft(2))
                SplitMode.EVENLY -> "1"
            }
        }
    }

    fun updateDate(value: LocalDate) {
        date = value
        if (convert && !rateManual) fetchRate()
    }

    fun switchConversion(on: Boolean) {
        val g = group ?: return
        if (on && !canConvert) return
        convert = on
        if (on) {
            if (originalCurrency.isEmpty()) originalCurrency = repo.rates.suggestedCurrency(g.currencyCode.orEmpty())
            rateManual = false
            fetchRate()
        } else {
            rateJob?.cancel()
            rateLoading = false
        }
    }

    fun chooseOriginalCurrency(code: String) {
        if (code == originalCurrency) return
        originalCurrency = code
        repo.rates.rememberCurrency(code)
        rateManual = false
        rateText = ""
        recompute()
        fetchRate()
    }

    fun updateOriginalAmount(text: String) {
        originalAmountText = text
        recompute()
    }

    fun updateRate(text: String) {
        rateText = text
        rateManual = true
        rateInfo = "Tasso inserito a mano."
        recompute()
    }

    fun refreshRate() {
        rateManual = false
        fetchRate(force = true)
    }

    fun suggestedCurrencies(): List<String> {
        val groupCode = group?.currencyCode.orEmpty()
        return listOfNotNull(
            originalCurrency.ifEmpty { null },
            repo.rates.networkCurrency(),
            repo.rates.suggestedCurrency(groupCode),
            "USD", "EUR", "GBP",
        ).distinct()
    }

    private fun fetchRate(force: Boolean = false) {
        val to = group?.currencyCode?.uppercase() ?: return
        val from = originalCurrency
        if (from.isEmpty() || from == to) return
        rateJob?.cancel()
        rateLoading = true
        rateJob = viewModelScope.launch {
            val q = repo.rates.quote(from, to, date, force)
            rateLoading = false
            if (rateManual) return@launch // nel frattempo hai scritto un tasso a mano
            if (q == null) {
                rateInfo = "Nessun tasso disponibile senza connessione: inseriscilo a mano."
                return@launch
            }
            rateText = formatRate(BigDecimal.valueOf(q.rate))
            val day = try {
                LocalDate.parse(q.date.take(10)).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
            } catch (e: Exception) {
                q.date
            }
            rateInfo = if (q.offline) "Sei offline: uso l'ultimo tasso salvato, del $day."
            else "Tasso del $day (${q.source})."
            recompute()
        }
    }

    private fun formatRate(rate: BigDecimal): String = Money.plainNumber(rate.round(MathContext(8)))

    /** Importo nel gruppo = importo pagato × tasso. */
    private fun recompute() {
        if (!convert) return
        val g = group ?: return
        val original = Money.parse(originalAmountText, originalCurrency)
        val rate = Money.parseDecimal(rateText)
        amountText = if (original == null || rate == null || rate.signum() <= 0) "" else {
            val groupMinor = BigDecimal.valueOf(original)
                .movePointLeft(Money.decimals(originalCurrency))
                .multiply(rate)
                .movePointRight(Money.decimals(g.currencyCode))
                .setScale(0, RoundingMode.HALF_UP)
                .toLong()
            Money.toInput(groupMinor, g.currencyCode)
        }
        if (autoValues && splitMode == SplitMode.BY_AMOUNT) autofill()
    }

    fun selectedIds(): List<String> {
        val g = group ?: return emptyList()
        return g.participants.map { it.id }.filter { selected[it] == true }
    }

    fun toggle(id: String, checked: Boolean) {
        selected[id] = checked
        if (checked && splitMode == SplitMode.BY_SHARES && values[id].isNullOrBlank()) values[id] = "1"
        if (autoValues) autofill()
    }

    fun setAll(checked: Boolean) {
        group?.participants?.forEach { selected[it.id] = checked }
        if (checked && splitMode == SplitMode.BY_SHARES) {
            selectedIds().forEach { if (values[it].isNullOrBlank()) values[it] = "1" }
        }
        if (autoValues) autofill()
    }

    fun updateAmount(text: String) {
        amountText = text
        if (autoValues && splitMode == SplitMode.BY_AMOUNT) autofill()
    }

    fun updateValue(id: String, text: String) {
        values[id] = text
        autoValues = false
    }

    /** Cambiando modalità proponiamo valori sensati (divisione equa) da ritoccare. */
    fun changeMode(mode: SplitMode) {
        if (mode == splitMode) return
        splitMode = mode
        autoValues = true
        autofill()
    }

    private fun autofill() {
        val ids = selectedIds()
        if (ids.isEmpty()) return
        val code = group?.currencyCode
        when (splitMode) {
            SplitMode.EVENLY -> Unit
            SplitMode.BY_SHARES -> ids.forEach { values[it] = "1" }
            SplitMode.BY_PERCENTAGE -> {
                val parts = splitEvenly(10_000, ids.size)
                ids.forEachIndexed { i, id -> values[id] = Money.plainNumber(BigDecimal.valueOf(parts[i]).movePointLeft(2)) }
            }
            SplitMode.BY_AMOUNT -> {
                val amount = Money.parse(amountText, code)
                if (amount == null || amount <= 0) {
                    ids.forEach { values[it] = "" }
                } else {
                    val parts = splitEvenly(amount, ids.size)
                    ids.forEachIndexed { i, id -> values[id] = Money.toInput(parts[i], code) }
                }
            }
        }
    }

    private fun splitEvenly(total: Long, n: Int): List<Long> {
        val base = total / n
        val rest = (total - base * n).toInt()
        return List(n) { i -> if (i < rest) base + 1 else base }
    }

    /** Anteprima di quanto tocca a ciascuno (unità minori). */
    fun preview(): Map<String, Long> {
        val code = group?.currencyCode
        val amount = Money.parse(amountText, code) ?: return emptyMap()
        val ids = selectedIds()
        if (ids.isEmpty()) return emptyMap()
        return when (splitMode) {
            SplitMode.EVENLY -> ids.zip(splitEvenly(amount, ids.size)).toMap()
            SplitMode.BY_AMOUNT -> ids.associateWith { Money.parse(values[it].orEmpty(), code) ?: 0L }
            SplitMode.BY_PERCENTAGE -> ids.associateWith { id ->
                val pct = Money.parseDecimal(values[id].orEmpty()) ?: BigDecimal.ZERO
                BigDecimal.valueOf(amount).multiply(pct).divide(BigDecimal(100), 0, RoundingMode.HALF_UP).toLong()
            }
            SplitMode.BY_SHARES -> {
                val shares = ids.associateWith { Money.parseDecimal(values[it].orEmpty()) ?: BigDecimal.ZERO }
                val total = shares.values.fold(BigDecimal.ZERO) { a, b -> a + b }
                if (total.signum() == 0) ids.associateWith { 0L }
                else shares.mapValues { (_, s) ->
                    BigDecimal.valueOf(amount).multiply(s).divide(total, 0, RoundingMode.HALF_UP).toLong()
                }
            }
        }
    }

    /** Somma inserita (percentuale ×100 o importo) per mostrare quanto manca. */
    fun allocated(): Long? {
        val code = group?.currencyCode
        val ids = selectedIds()
        return when (splitMode) {
            SplitMode.BY_PERCENTAGE -> ids.sumOf { id -> hundredths(values[id].orEmpty()) ?: 0L }
            SplitMode.BY_AMOUNT -> ids.sumOf { id -> Money.parse(values[id].orEmpty(), code) ?: 0L }
            else -> null
        }
    }

    private fun hundredths(text: String): Long? =
        Money.parseDecimal(text)?.movePointRight(2)?.setScale(0, RoundingMode.HALF_UP)?.toLong()

    private fun validate(g: Group): String? {
        val code = g.currencyCode
        val t = title.trim()
        if (t.length < 2) return "Il titolo deve avere almeno 2 caratteri."
        if (t.length > 200) return "Il titolo è troppo lungo (massimo 200 caratteri)."
        if (convert) {
            val original = Money.parse(originalAmountText, originalCurrency)
                ?: return "Inserisci quanto hai pagato in $originalCurrency."
            if (original == 0L) return "L'importo pagato non può essere zero."
            if (original > 1_000_000_000L || original < -1_000_000_000L) return "L'importo pagato è troppo grande."
            val rate = Money.parseDecimal(rateText)
            if (rate == null || rate.signum() <= 0) return "Inserisci un tasso di cambio valido."
        }
        val amount = Money.parse(amountText, code) ?: return "Inserisci un importo valido."
        if (amount == 0L) return "L'importo non può essere zero."
        if (amount > 1_000_000_000L || amount < -1_000_000_000L) return "L'importo è troppo grande."
        if (paidBy == null || g.participants.none { it.id == paidBy }) return "Scegli chi ha pagato."
        val ids = selectedIds()
        if (ids.isEmpty()) return "Seleziona almeno una persona per cui è stata fatta la spesa."
        if (notes.length > 5000) return "Le note sono troppo lunghe (massimo 5000 caratteri)."
        when (splitMode) {
            SplitMode.EVENLY -> Unit
            SplitMode.BY_SHARES -> if (ids.any { (hundredths(values[it].orEmpty()) ?: 0L) <= 0L }) {
                return "Ogni persona selezionata deve avere almeno una quota maggiore di zero."
            }
            SplitMode.BY_PERCENTAGE -> {
                if (ids.any { (hundredths(values[it].orEmpty()) ?: 0L) <= 0L }) return "Ogni percentuale deve essere maggiore di zero."
                if (allocated() != 10_000L) return "Le percentuali devono fare 100% in totale."
            }
            SplitMode.BY_AMOUNT -> {
                if (amount < 0) return "Con importi negativi usa la divisione in parti uguali."
                if (ids.any { (Money.parse(values[it].orEmpty(), code) ?: 0L) <= 0L }) return "Ogni importo deve essere maggiore di zero."
                if (allocated() != amount) return "La somma degli importi deve essere uguale al totale della spesa."
            }
        }
        return null
    }

    private fun sharesFor(id: String, code: String?): Long = when (splitMode) {
        SplitMode.EVENLY -> 100L
        SplitMode.BY_SHARES, SplitMode.BY_PERCENTAGE -> hundredths(values[id].orEmpty()) ?: 0L
        SplitMode.BY_AMOUNT -> Money.parse(values[id].orEmpty(), code) ?: 0L
    }

    private fun buildValues(g: Group): JsonObject {
        val code = g.currencyCode
        val amount = Money.parse(amountText, code) ?: 0L
        val rate = Money.parseDecimal(rateText)
        val sendConversion = convert && canConvert && rate != null && rate.signum() > 0
        return buildJsonObject {
            put("expenseDate", date.toString())
            put("title", title.trim())
            put("category", categoryId)
            put("amount", amount)
            // Stessi campi del sito: "1 [valuta pagata] = tasso [valuta del gruppo]".
            if (sendConversion && rate != null) {
                put("originalAmount", Money.parse(originalAmountText, originalCurrency))
                put("originalCurrency", originalCurrency)
                put("conversionRate", rate.toDouble())
            }
            put("paidBy", paidBy)
            putJsonArray("paidFor") {
                selectedIds().forEach { id ->
                    addJsonObject {
                        put("participant", id)
                        put("shares", sharesFor(id, code))
                    }
                }
            }
            put("splitMode", splitMode.name)
            put("saveDefaultSplittingOptions", false)
            put("isReimbursement", isReimbursement)
            putJsonArray("documents") {
                documents.forEach { d ->
                    addJsonObject {
                        put("id", d.id)
                        put("url", d.url)
                        put("width", d.width)
                        put("height", d.height)
                    }
                }
            }
            put("notes", notes)
            put("recurrenceRule", recurrence.name)
        }
    }

    fun save() {
        val g = group ?: return
        if (saving) return
        val problem = validate(g)
        if (problem != null) {
            error = problem
            return
        }
        error = null
        saving = true
        viewModelScope.launch {
            val values = buildValues(g)
            val me = repo.store.activeParticipant(route.groupId)
            val expenseId = route.expenseId
            attempt {
                if (expenseId == null) repo.api.createExpense(route.groupId, values, me)
                else repo.api.updateExpense(route.groupId, expenseId, values, me)
            }.onSuccess {
                repo.notifyChanged(route.groupId)
                done = true
            }.onFailure {
                error = it.userMessage()
                saving = false
            }
        }
    }

    fun delete() {
        val expenseId = route.expenseId ?: return
        if (deleting) return
        deleting = true
        error = null
        viewModelScope.launch {
            attempt { repo.api.deleteExpense(route.groupId, expenseId, repo.store.activeParticipant(route.groupId)) }
                .onSuccess {
                    repo.notifyChanged(route.groupId)
                    done = true
                }
                .onFailure {
                    error = it.userMessage()
                    deleting = false
                }
        }
    }
}
