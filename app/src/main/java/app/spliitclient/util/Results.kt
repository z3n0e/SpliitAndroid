package app.spliitclient.util

import app.spliitclient.R
import app.spliitclient.data.ApiException
import kotlin.coroutines.cancellation.CancellationException

/** Like runCatching, but never swallows coroutine cancellation. */
inline fun <T> attempt(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

fun Throwable.userMessage(): UiText =
    (this as? ApiException)?.text ?: uiText(R.string.error_unexpected)
