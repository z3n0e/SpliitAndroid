package app.spliitclient.util

import app.spliitclient.data.Group
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale

/** Importi in "unità minori" (centesimi) come fa Spliit. */
object Money {

    private val zeroDecimals = setOf(
        "JPY", "HUF", "ISK", "KRW", "IDR", "VND", "COP", "CLP", "CRC", "PYG", "UGX", "XAF", "XOF",
        "AFN", "ALL", "AMD", "BIF", "DJF", "GNF", "IQD", "IRR", "KMF", "LBP", "MGA", "MMK", "MUR",
        "PKR", "RSD", "RWF", "SOS", "SYP", "TZS", "UZS", "YER",
    )
    private val threeDecimals = setOf("BHD", "JOD", "KWD", "LYD", "OMR", "TND")

    fun decimals(code: String?): Int {
        val c = code?.trim()?.uppercase().orEmpty()
        return when (c) {
            in zeroDecimals -> 0
            in threeDecimals -> 3
            else -> 2
        }
    }

    private val formats = HashMap<String, NumberFormat>()

    fun format(minor: Long, group: Group, showPlus: Boolean = false): String =
        format(minor, group.currency, group.currencyCode, showPlus)

    fun format(minor: Long, symbol: String, code: String?, showPlus: Boolean = false): String {
        val digits = decimals(code)
        val locale = Locale.getDefault()
        val key = "$symbol|$digits|${locale.toLanguageTag()}"
        val text = synchronized(formats) {
            val nf = formats.getOrPut(key) { buildFormat(symbol, digits, locale) }
            nf.format(BigDecimal.valueOf(minor).movePointLeft(digits).abs())
        }
        return when {
            minor < 0 -> "-$text"
            showPlus && minor > 0 -> "+$text"
            else -> text
        }
    }

    private fun buildFormat(symbol: String, digits: Int, locale: Locale): NumberFormat {
        val nf = NumberFormat.getCurrencyInstance(locale)
        if (nf is DecimalFormat) {
            val symbols = nf.decimalFormatSymbols
            symbols.currencySymbol = symbol
            nf.decimalFormatSymbols = symbols
        }
        nf.minimumFractionDigits = digits
        nf.maximumFractionDigits = digits
        return nf
    }

    /** Testo da mettere in un campo di input (es. "12,50"). */
    fun toInput(minor: Long, code: String?): String {
        val digits = decimals(code)
        val plain = BigDecimal.valueOf(minor).movePointLeft(digits).setScale(digits).toPlainString()
        val sep = DecimalFormatSymbols.getInstance().decimalSeparator
        return if (sep == '.') plain else plain.replace('.', sep)
    }

    /** Accetta "12,50", "12.50", "1.234,56", "1.000.000"... restituisce unità minori o null. */
    fun parse(text: String, code: String?): Long? {
        val value = parseDecimal(text, decimals(code)) ?: return null
        return try {
            value.movePointRight(decimals(code)).setScale(0, RoundingMode.HALF_UP).longValueExact()
        } catch (e: ArithmeticException) {
            null
        }
    }

    /**
     * Interpreta un numero scritto a mano, con punti e virgole come capita:
     *  - se ci sono sia punti sia virgole, l'ultimo separatore è quello dei decimali;
     *  - se lo stesso separatore compare più volte ("1.000.000") è quello delle migliaia;
     *  - un solo separatore seguito da esattamente 3 cifre ("250.000") è delle migliaia
     *    se la valuta non ha decimali o se è il separatore delle migliaia del telefono.
     */
    fun parseDecimal(text: String, decimalsHint: Int? = null): BigDecimal? {
        val t = text.trim().replace(" ", "").replace("\u00A0", "").replace("'", "")
        if (t.isEmpty()) return null
        val dots = t.count { it == '.' }
        val commas = t.count { it == ',' }
        val normalized = when {
            dots == 0 && commas == 0 -> t
            dots > 0 && commas > 0 -> {
                val decimalSep = if (t.lastIndexOf('.') > t.lastIndexOf(',')) '.' else ','
                val groupSep = if (decimalSep == '.') ',' else '.'
                if (t.count { it == decimalSep } > 1) return null
                t.replace(groupSep.toString(), "").replace(decimalSep, '.')
            }
            else -> {
                val sep = if (dots > 0) '.' else ','
                val count = if (dots > 0) dots else commas
                val digitsAfter = t.length - t.lastIndexOf(sep) - 1
                val grouping = DecimalFormatSymbols.getInstance().groupingSeparator
                val isGrouping = count > 1 ||
                    (digitsAfter == 3 && (decimalsHint == 0 || sep == grouping))
                if (isGrouping) t.replace(sep.toString(), "") else t.replace(sep, '.')
            }
        }
        return try {
            BigDecimal(normalized)
        } catch (e: NumberFormatException) {
            null
        }
    }

    /** Numero "umano" senza zeri finali, con il separatore locale (per quote e percentuali). */
    fun plainNumber(value: BigDecimal): String {
        val s = value.stripTrailingZeros().toPlainString()
        val sep = DecimalFormatSymbols.getInstance().decimalSeparator
        return if (sep == '.') s else s.replace('.', sep)
    }
}
