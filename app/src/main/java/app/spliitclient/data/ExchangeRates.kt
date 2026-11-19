package app.spliitclient.data

import android.content.Context
import android.telephony.TelephonyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.util.Currency
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

/** Un tasso "1 [from] = rate [to]", come lo salva Spliit (conversionRate). */
data class RateQuote(
    val rate: Double,
    /** Giorno a cui si riferisce il tasso (può essere il giorno lavorativo precedente). */
    val date: String,
    val source: String,
    val savedAt: Long,
    /** true se la rete non era disponibile e stiamo usando l'ultimo tasso salvato. */
    val offline: Boolean = false,
)

/**
 * Tassi di cambio aggiornati, senza chiavi né account:
 *  1. Frankfurter (dati BCE), lo stesso servizio usato dal sito di Spliit;
 *  2. currency-api (oltre 200 valute, compreso VND), con due indirizzi alternativi.
 * Ogni tasso ottenuto viene salvato: senza rete si usa l'ultimo tasso salvato.
 * I tassi di giorni passati non cambiano, quindi non vengono mai richiesti due volte.
 */
class ExchangeRates(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("exchange_rates", Context.MODE_PRIVATE)
    private val memory = ConcurrentHashMap<String, RateQuote>()
    private val json = Json { ignoreUnknownKeys = true }
    private val codeRegex = Regex("^[A-Z]{3}$")

    suspend fun quote(from: String, to: String, date: LocalDate, force: Boolean = false): RateQuote? =
        withContext(Dispatchers.IO) {
            try {
                findQuote(from.uppercase(Locale.ROOT), to.uppercase(Locale.ROOT), date, force)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }

    private fun findQuote(from: String, to: String, date: LocalDate, force: Boolean): RateQuote? {
        if (!codeRegex.matches(from) || !codeRegex.matches(to)) return null
        val now = System.currentTimeMillis()
        if (from == to) return RateQuote(1.0, date.toString(), "", now)

        val today = LocalDate.now()
        val day = if (date.isAfter(today)) today else date
        val key = "$from>$to@$day"
        val lastKey = "$from>$to@last"

        val cached = read(key)
        if (!force && cached != null && (day.isBefore(today) || now - cached.savedAt < FRESH_MS)) return cached

        val fetched = fetchFrankfurter(from, to, day, now) ?: fetchCurrencyApi(from, to, day, today, now)
        if (fetched != null) {
            write(key, fetched)
            write(lastKey, fetched)
            prune()
            return fetched
        }
        return (cached ?: read(lastKey))?.copy(offline = true)
    }

    private fun fetchFrankfurter(from: String, to: String, day: LocalDate, now: Long): RateQuote? {
        val text = httpGet("https://api.frankfurter.dev/v2/rates?date=$day&base=$from&quotes=$to") ?: return null
        val array = parseOrNull(text) as? JsonArray ?: return null
        for (element in array) {
            val obj = element as? JsonObject ?: continue
            if ((obj["quote"] as? JsonPrimitive)?.contentOrNull != to) continue
            val rate = (obj["rate"] as? JsonPrimitive)?.doubleOrNull ?: continue
            if (!isUsable(rate)) continue
            val rateDay = (obj["date"] as? JsonPrimitive)?.contentOrNull ?: day.toString()
            return RateQuote(rate, rateDay, "BCE", now)
        }
        return null
    }

    private fun fetchCurrencyApi(from: String, to: String, day: LocalDate, today: LocalDate, now: Long): RateQuote? {
        val base = from.lowercase(Locale.ROOT)
        val quote = to.lowercase(Locale.ROOT)
        val tags = if (day == today) listOf("latest") else listOf(day.toString(), "latest")
        for (tag in tags) {
            val urls = listOf(
                "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@$tag/v1/currencies/$base.min.json",
                "https://$tag.currency-api.pages.dev/v1/currencies/$base.min.json",
            )
            for (url in urls) {
                val text = httpGet(url) ?: continue
                val obj = parseOrNull(text) as? JsonObject ?: continue
                val rates = obj[base] as? JsonObject ?: continue
                val rate = (rates[quote] as? JsonPrimitive)?.doubleOrNull ?: continue
                if (!isUsable(rate)) continue
                val rateDay = (obj["date"] as? JsonPrimitive)?.contentOrNull ?: day.toString()
                return RateQuote(rate, rateDay, "currency-api", now)
            }
        }
        return null
    }

    private fun parseOrNull(text: String): JsonElement? = try {
        json.parseToJsonElement(text)
    } catch (e: Exception) {
        null
    }

    private fun isUsable(rate: Double) = rate.isFinite() && rate > 0.0 && rate < 1e12

    // ---- memoria locale ----

    private fun read(key: String): RateQuote? {
        memory[key]?.let { return it }
        val raw = prefs.getString(key, null) ?: return null
        val parts = raw.split('|')
        if (parts.size != 4) return null
        val rate = parts[0].toDoubleOrNull() ?: return null
        val savedAt = parts[3].toLongOrNull() ?: return null
        if (!isUsable(rate)) return null
        return RateQuote(rate, parts[1], parts[2], savedAt).also { memory[key] = it }
    }

    private fun write(key: String, q: RateQuote) {
        memory[key] = q
        prefs.edit().putString(key, "${q.rate}|${q.date}|${q.source}|${q.savedAt}").apply()
    }

    /** Evita che la memoria cresca all'infinito: oltre 300 voci teniamo solo gli "ultimi tassi". */
    private fun prune() {
        val keys = prefs.all.keys
        if (keys.size <= 300) return
        val editor = prefs.edit()
        keys.filter { it.contains('@') && !it.endsWith("@last") }.forEach {
            editor.remove(it)
            memory.remove(it)
        }
        editor.apply()
    }

    // ---- valuta suggerita ----

    fun rememberCurrency(code: String) {
        prefs.edit().putString(KEY_LAST_CURRENCY, code).apply()
    }

    /** Ultima valuta usata, altrimenti quella del Paese in cui ti trovi (rete mobile), altrimenti del telefono. */
    fun suggestedCurrency(groupCode: String): String {
        val candidates = listOfNotNull(
            prefs.getString(KEY_LAST_CURRENCY, null),
            networkCurrency(),
            currencyOf(Locale.getDefault()),
            "USD",
            "EUR",
        )
        return candidates.first { codeRegex.matches(it) && it != groupCode.uppercase(Locale.ROOT) }
    }

    fun networkCurrency(): String? {
        // Il Paese della rete mobile non richiede permessi.
        val tm = appContext.getSystemService(TelephonyManager::class.java) ?: return null
        val iso = (tm.networkCountryIso?.takeIf { it.length == 2 } ?: tm.simCountryIso?.takeIf { it.length == 2 })
            ?: return null
        val locale = try {
            Locale.Builder().setRegion(iso.uppercase(Locale.ROOT)).build()
        } catch (e: Exception) {
            return null
        }
        return currencyOf(locale)
    }

    private fun currencyOf(locale: Locale): String? = try {
        Currency.getInstance(locale)?.currencyCode
    } catch (e: Exception) {
        null
    }

    private fun httpGet(url: String): String? {
        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            return null
        }
        return try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 10_000
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "SpliitAndroid/1.0")
            if (connection.responseCode != 200) {
                connection.errorStream?.close()
                return null
            }
            connection.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    total += n
                    if (total > MAX_BYTES) return null
                    out.write(buffer, 0, n)
                }
                out.toString("UTF-8")
            }
        } catch (e: Exception) {
            connection.disconnect()
            null
        }
    }

    private companion object {
        const val FRESH_MS = 6L * 60 * 60 * 1000 // i tassi di oggi si riaggiornano dopo 6 ore
        const val MAX_BYTES = 2 * 1024 * 1024
        const val KEY_LAST_CURRENCY = "last_currency"
    }
}
