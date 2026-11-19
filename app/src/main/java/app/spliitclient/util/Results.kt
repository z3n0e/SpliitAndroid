package app.spliitclient.util

import app.spliitclient.data.ApiException
import kotlin.coroutines.cancellation.CancellationException

/** Come runCatching, ma non intercetta la cancellazione delle coroutine. */
inline fun <T> attempt(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

fun Throwable.userMessage(): String =
    (this as? ApiException)?.message ?: "Si è verificato un errore imprevisto. Riprova."
