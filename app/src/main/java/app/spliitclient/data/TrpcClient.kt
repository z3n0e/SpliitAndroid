package app.spliitclient.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class ApiException(message: String, val kind: Kind, val httpCode: Int = 0) : Exception(message) {
    enum class Kind { Network, NotFound, Invalid, Forbidden, Server }
}

/**
 * Client minimale per l'API tRPC (v11 + superjson) di Spliit.
 * Usa HttpURLConnection di sistema: nessuna libreria di rete, connessioni keep-alive
 * riutilizzate, gzip trasparente, solo HTTPS.
 */
class TrpcClient(private val baseUrl: () -> String) {

    @OptIn(ExperimentalSerializationApi::class)
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        encodeDefaults = true
    }

    suspend fun query(path: String, input: JsonObject? = null): JsonElement {
        val query = if (input == null) "" else {
            "?input=" + URLEncoder.encode(envelope(input).toString(), "UTF-8")
        }
        return execute("GET", path, query, null)
    }

    suspend fun mutation(path: String, input: JsonObject): JsonElement =
        execute("POST", path, "", envelope(input).toString())

    private fun envelope(input: JsonObject) = buildJsonObject { put("json", input) }

    private suspend fun execute(method: String, path: String, query: String, body: String?): JsonElement =
        withContext(Dispatchers.IO) {
            val base = baseUrl().trimEnd('/')
            if (!base.startsWith("https://")) {
                throw ApiException("L'indirizzo del server deve iniziare con https://", ApiException.Kind.Invalid)
            }
            val connection = try {
                URL("$base/api/trpc/$path$query").openConnection() as HttpURLConnection
            } catch (e: Exception) {
                throw ApiException("Indirizzo del server non valido.", ApiException.Kind.Invalid)
            }
            try {
                connection.requestMethod = method
                connection.connectTimeout = 15_000
                connection.readTimeout = 25_000
                connection.useCaches = false
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", "SpliitAndroid/1.0")
                if (body != null) {
                    val bytes = body.toByteArray(Charsets.UTF_8)
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.setFixedLengthStreamingMode(bytes.size)
                    connection.outputStream.use { it.write(bytes) }
                }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.use { readLimited(it) }.orEmpty()
                parse(text, code)
            } catch (e: ApiException) {
                connection.disconnect()
                throw e
            } catch (e: IOException) {
                connection.disconnect()
                throw ApiException(
                    "Connessione non riuscita. Controlla la rete e riprova.",
                    ApiException.Kind.Network,
                )
            }
        }

    private fun parse(text: String, httpCode: Int): JsonElement {
        val root = try {
            json.parseToJsonElement(text) as? JsonObject
        } catch (e: Exception) {
            null
        } ?: throw ApiException(
            if (httpCode >= 500) "Il server non risponde correttamente ($httpCode)." else "Risposta non valida dal server ($httpCode).",
            ApiException.Kind.Server,
            httpCode,
        )

        val error = root["error"]
        if (error != null && error !is JsonNull) {
            val errorObj = (error as? JsonObject)?.let { (it["json"] as? JsonObject) ?: it }
            val data = errorObj?.get("data") as? JsonObject
            val trpcCode = (data?.get("code") as? JsonPrimitive)?.contentOrNull
            throw when (trpcCode) {
                "NOT_FOUND" -> ApiException("Non trovato sul server.", ApiException.Kind.NotFound, httpCode)
                "BAD_REQUEST" -> ApiException("Il server ha rifiutato i dati: controlla i campi e riprova.", ApiException.Kind.Invalid, httpCode)
                "FORBIDDEN", "UNAUTHORIZED" -> ApiException("Operazione non consentita su questo server.", ApiException.Kind.Forbidden, httpCode)
                "TOO_MANY_REQUESTS" -> ApiException("Troppe richieste: attendi un momento e riprova.", ApiException.Kind.Server, httpCode)
                else -> ApiException("Errore del server ($httpCode). Riprova tra poco.", ApiException.Kind.Server, httpCode)
            }
        }
        val result = root["result"] as? JsonObject
        val data = result?.get("data") as? JsonObject
        return data?.get("json") ?: JsonNull
    }

    private fun readLimited(input: InputStream): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            if (total > MAX_RESPONSE_BYTES) {
                throw ApiException("Risposta del server troppo grande.", ApiException.Kind.Server)
            }
            out.write(buffer, 0, n)
        }
        return out.toString("UTF-8")
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 8 * 1024 * 1024
    }
}
