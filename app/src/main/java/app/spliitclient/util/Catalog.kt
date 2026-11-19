package app.spliitclient.util

import androidx.annotation.StringRes
import app.spliitclient.R
import java.util.Currency
import java.util.Locale

/** Spliit categories (ids are fixed on the server), with translated names. */
object Categories {
    data class Category(val id: Int, @StringRes val name: Int, val emoji: String)

    val all = listOf(
        Category(0, R.string.cat_general, "🧾"),
        Category(1, R.string.cat_payment, "💸"),
        Category(2, R.string.cat_entertainment, "🎉"),
        Category(3, R.string.cat_games, "🎮"),
        Category(4, R.string.cat_movies, "🎬"),
        Category(5, R.string.cat_music, "🎵"),
        Category(6, R.string.cat_sports, "⚽"),
        Category(7, R.string.cat_food_drink, "🍽️"),
        Category(8, R.string.cat_dining_out, "🍝"),
        Category(9, R.string.cat_groceries, "🛒"),
        Category(10, R.string.cat_liquor, "🍷"),
        Category(11, R.string.cat_home, "🏠"),
        Category(12, R.string.cat_electronics, "🔌"),
        Category(13, R.string.cat_furniture, "🛋️"),
        Category(14, R.string.cat_household, "🧴"),
        Category(15, R.string.cat_maintenance, "🔧"),
        Category(16, R.string.cat_mortgage, "🏦"),
        Category(17, R.string.cat_pets, "🐾"),
        Category(18, R.string.cat_rent, "🔑"),
        Category(19, R.string.cat_services, "🧰"),
        Category(20, R.string.cat_childcare, "🧸"),
        Category(21, R.string.cat_clothing, "👕"),
        Category(22, R.string.cat_education, "📚"),
        Category(23, R.string.cat_gifts, "🎁"),
        Category(24, R.string.cat_insurance, "🛡️"),
        Category(25, R.string.cat_medical, "💊"),
        Category(26, R.string.cat_taxes, "📄"),
        Category(43, R.string.cat_donation, "🤝"),
        Category(27, R.string.cat_transportation, "🚗"),
        Category(28, R.string.cat_bicycle, "🚲"),
        Category(29, R.string.cat_bus_train, "🚆"),
        Category(30, R.string.cat_car, "🚙"),
        Category(31, R.string.cat_gas_fuel, "⛽"),
        Category(32, R.string.cat_hotel, "🏨"),
        Category(33, R.string.cat_parking, "🅿️"),
        Category(34, R.string.cat_plane, "✈️"),
        Category(35, R.string.cat_taxi, "🚕"),
        Category(36, R.string.cat_utilities, "💡"),
        Category(37, R.string.cat_cleaning, "🧹"),
        Category(38, R.string.cat_electricity, "⚡"),
        Category(39, R.string.cat_heat_gas, "🔥"),
        Category(40, R.string.cat_trash, "🗑️"),
        Category(41, R.string.cat_tv_phone_internet, "📶"),
        Category(42, R.string.cat_water, "🚰"),
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
