package app.spliitclient.util

import java.util.Currency
import java.util.Locale

/** Categorie di Spliit (gli id sono fissi lato server) con nomi italiani. */
object Categories {
    data class Category(val id: Int, val name: String, val group: String, val emoji: String)

    val all = listOf(
        Category(0, "Generale", "Senza categoria", "🧾"),
        Category(1, "Pagamento", "Senza categoria", "💸"),
        Category(2, "Svago", "Svago", "🎉"),
        Category(3, "Giochi", "Svago", "🎮"),
        Category(4, "Cinema", "Svago", "🎬"),
        Category(5, "Musica", "Svago", "🎵"),
        Category(6, "Sport", "Svago", "⚽"),
        Category(7, "Cibo e bevande", "Cibo e bevande", "🍽️"),
        Category(8, "Ristorante", "Cibo e bevande", "🍝"),
        Category(9, "Spesa alimentare", "Cibo e bevande", "🛒"),
        Category(10, "Alcolici", "Cibo e bevande", "🍷"),
        Category(11, "Casa", "Casa", "🏠"),
        Category(12, "Elettronica", "Casa", "🔌"),
        Category(13, "Arredamento", "Casa", "🛋️"),
        Category(14, "Articoli per la casa", "Casa", "🧴"),
        Category(15, "Manutenzione", "Casa", "🔧"),
        Category(16, "Mutuo", "Casa", "🏦"),
        Category(17, "Animali", "Casa", "🐾"),
        Category(18, "Affitto", "Casa", "🔑"),
        Category(19, "Servizi", "Casa", "🧰"),
        Category(20, "Figli", "Vita", "🧸"),
        Category(21, "Abbigliamento", "Vita", "👕"),
        Category(22, "Istruzione", "Vita", "📚"),
        Category(23, "Regali", "Vita", "🎁"),
        Category(24, "Assicurazioni", "Vita", "🛡️"),
        Category(25, "Spese mediche", "Vita", "💊"),
        Category(26, "Tasse", "Vita", "📄"),
        Category(43, "Donazioni", "Vita", "🤝"),
        Category(27, "Trasporti", "Trasporti", "🚗"),
        Category(28, "Bicicletta", "Trasporti", "🚲"),
        Category(29, "Bus e treno", "Trasporti", "🚆"),
        Category(30, "Auto", "Trasporti", "🚙"),
        Category(31, "Carburante", "Trasporti", "⛽"),
        Category(32, "Hotel", "Trasporti", "🏨"),
        Category(33, "Parcheggio", "Trasporti", "🅿️"),
        Category(34, "Aereo", "Trasporti", "✈️"),
        Category(35, "Taxi", "Trasporti", "🚕"),
        Category(36, "Utenze", "Utenze", "💡"),
        Category(37, "Pulizie", "Utenze", "🧹"),
        Category(38, "Elettricità", "Utenze", "⚡"),
        Category(39, "Riscaldamento e gas", "Utenze", "🔥"),
        Category(40, "Rifiuti", "Utenze", "🗑️"),
        Category(41, "TV, telefono e internet", "Utenze", "📶"),
        Category(42, "Acqua", "Utenze", "🚰"),
    )

    private val byId = all.associateBy { it.id }

    fun get(id: Int): Category = byId[id] ?: all[0]
}

/** Tutte le valute ISO (per l'importo pagato in un'altra valuta). */
object WorldCurrencies {
    private val keepX = setOf("XAF", "XOF", "XPF", "XCD")

    val codes: List<String> by lazy {
        Currency.getAvailableCurrencies()
            .map { it.currencyCode }
            .filter { it.length == 3 && (!it.startsWith("X") || it in keepX) }
            .distinct()
            .sortedBy { CurrencyTable.displayName(it).lowercase(Locale.getDefault()) }
    }

    /** Suggerite in cima, poi tutte le altre; esclusa la valuta del gruppo. */
    fun ordered(first: List<String>, exclude: String?): List<String> =
        (first + codes).distinct().filter { it != exclude }

    fun label(code: String): String = "$code  ${CurrencyTable.displayName(code)}"
}

/** Valute proposte nella creazione del gruppo (simbolo come lo usa Spliit). */
object CurrencyTable {
    data class Entry(val code: String, val symbol: String)

    val entries = listOf(
        Entry("EUR", "€"), Entry("USD", "$"), Entry("GBP", "£"), Entry("CHF", "CHF"),
        Entry("JPY", "¥"), Entry("CNY", "CN¥"), Entry("CAD", "CA$"), Entry("AUD", "AU$"),
        Entry("NZD", "NZ$"), Entry("SEK", "Skr"), Entry("NOK", "Nkr"), Entry("DKK", "Dkr"),
        Entry("ISK", "Ikr"), Entry("PLN", "zł"), Entry("CZK", "Kč"), Entry("HUF", "Ft"),
        Entry("RON", "RON"), Entry("BGN", "BGN"), Entry("RSD", "din."), Entry("ALL", "ALL"),
        Entry("TRY", "TL"), Entry("ILS", "₪"), Entry("AED", "AED"), Entry("MAD", "MAD"),
        Entry("INR", "Rs"), Entry("THB", "฿"), Entry("SGD", "S$"), Entry("HKD", "HK$"),
        Entry("KRW", "₩"), Entry("BRL", "R$"), Entry("MXN", "MX$"), Entry("ARS", "AR$"),
        Entry("ZAR", "R"),
    )

    fun symbol(code: String): String = entries.firstOrNull { it.code == code }?.symbol ?: code

    fun displayName(code: String): String = try {
        Currency.getInstance(code).getDisplayName(Locale.getDefault())
            .replaceFirstChar { it.titlecase(Locale.getDefault()) }
    } catch (e: Exception) {
        code
    }

    fun defaultCode(): String {
        val local = try {
            Currency.getInstance(Locale.getDefault()).currencyCode
        } catch (e: Exception) {
            null
        }
        return if (entries.any { it.code == local }) local!! else "EUR"
    }
}
