package app.stravadashboard.intervals

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.basicAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.serialization.ContentConvertException
import kotlinx.serialization.SerializationException
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.channels.UnresolvedAddressException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** The outcome of one Intervals.icu call; DTOs stay inside this package. */
internal sealed interface IntervalsResult {
    data class Success(
        val days: List<IntervalsWellnessDto>,
    ) : IntervalsResult

    data object KeyRejected : IntervalsResult

    data class RateLimited(
        val until: Instant,
    ) : IntervalsResult

    data object Unavailable : IntervalsResult
}

/**
 * Calls the Intervals.icu wellness endpoint with the owner's personal API key (basic auth, user `API_KEY`).
 * Only the status code or the exception class is logged, never the key, URL, query or body.
 */
class IntervalsClient(
    private val http: HttpClient,
    private val apiKey: String,
    private val clock: Clock,
) {
    internal suspend fun wellness(
        oldest: LocalDate,
        newest: LocalDate,
    ): IntervalsResult =
        try {
            val response =
                http.get(WELLNESS_URL) {
                    parameter("oldest", oldest.toString())
                    parameter("newest", newest.toString())
                    parameter("fields", FIELDS)
                    basicAuth(API_KEY_USER, apiKey)
                    accept(ContentType.Application.Json)
                }
            map(response)
        } catch (e: IOException) {
            unavailable(e)
        } catch (e: UnresolvedAddressException) {
            unavailable(e)
        } catch (e: SerializationException) {
            unavailable(e)
        } catch (e: ContentConvertException) {
            // Its message can quote the response body, so only the class name is logged.
            unavailable(e)
        }

    private suspend fun map(response: HttpResponse): IntervalsResult =
        when {
            response.status.isSuccess() -> IntervalsResult.Success(response.body())
            response.status in REJECTED -> IntervalsResult.KeyRejected
            response.status == HttpStatusCode.TooManyRequests ->
                IntervalsResult.RateLimited(retryAt(response.headers[HttpHeaders.RetryAfter]))
            else -> {
                log.warn("Intervals.icu returned status {}", response.status.value)
                IntervalsResult.Unavailable
            }
        }

    /** `Retry-After` in whole seconds (1 to 86400); anything else waits the 15-minute fallback. */
    private fun retryAt(header: String?): Instant {
        val seconds = header?.trim()?.toLongOrNull()?.takeIf { it in 1..MAX_RETRY_AFTER_SECONDS }
        return clock.instant().plus(seconds?.let(Duration::ofSeconds) ?: RATE_LIMIT_FALLBACK)
    }

    private fun unavailable(e: Exception): IntervalsResult {
        log.warn("Intervals.icu request failed: {}", e::class.simpleName)
        return IntervalsResult.Unavailable
    }

    private companion object {
        const val WELLNESS_URL = "https://intervals.icu/api/v1/athlete/0/wellness"
        const val FIELDS = "id,restingHR,hrv"
        const val API_KEY_USER = "API_KEY"
        const val MAX_RETRY_AFTER_SECONDS = 86_400L
        val RATE_LIMIT_FALLBACK: Duration = Duration.ofMinutes(15)
        val REJECTED = setOf(HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden)
        val log = LoggerFactory.getLogger(IntervalsClient::class.java)
    }
}
