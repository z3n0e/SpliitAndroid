package app.spliitclient.util

import java.net.URI

object Links {
    private val idRegex = Regex("^[A-Za-z0-9_-]{4,64}$")
    private val pathRegex = Regex("/groups/([A-Za-z0-9_-]{4,64})")

    /** Estrae l'ID del gruppo da un link Spliit, da un testo che lo contiene o da un ID nudo. */
    fun extractGroupId(input: String): String? {
        val t = input.trim()
        if (idRegex.matches(t)) return t
        return pathRegex.find(t)?.groupValues?.get(1)
    }

    /** Più severo: solo testo che contiene un link /groups/<id> (per link e "Condividi"). */
    fun extractFromLink(input: String): String? = pathRegex.find(input)?.groupValues?.get(1)

    fun exportUrl(server: String, groupId: String, format: String): String =
        "${server.trimEnd('/')}/groups/$groupId/expenses/export/$format"

    fun groupUrl(server: String, groupId: String): String = "${server.trimEnd('/')}/groups/$groupId"

    /** Accetta solo https://host[:porta][/percorso], senza credenziali, query o frammenti. */
    fun normalizeServer(input: String): String? {
        val t = input.trim().trimEnd('/')
        val uri = try {
            URI(t)
        } catch (e: Exception) {
            return null
        }
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (uri.host.isNullOrBlank()) return null
        if (uri.rawUserInfo != null || uri.rawQuery != null || uri.rawFragment != null) return null
        return t
    }

    /** Solo link https verso i documenti allegati. */
    fun isSafeWebUrl(url: String): Boolean = try {
        URI(url).scheme.equals("https", ignoreCase = true)
    } catch (e: Exception) {
        false
    }
}
