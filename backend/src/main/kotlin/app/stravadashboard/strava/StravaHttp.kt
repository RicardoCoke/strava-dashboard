package app.stravadashboard.strava

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.serialization.ContentConvertException
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.channels.UnresolvedAddressException
import java.time.Clock

private val log = LoggerFactory.getLogger("app.stravadashboard.strava.StravaHttp")

private const val CONNECT_TIMEOUT_MS = 5_000L
private const val REQUEST_TIMEOUT_MS = 15_000L

/** The one HTTP client for Strava. The Ktor `Logging` plugin is deliberately not installed: it would log tokens. */
fun stravaHttpClient(engine: HttpClientEngine): HttpClient =
    HttpClient(engine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout) {
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
        }
    }

/**
 * Runs one request and maps the response to a [StravaResult]. Network errors, timeouts, 5xx and bad JSON are
 * [StravaResult.Unavailable]. Only the exception class is logged, never URLs, headers or bodies.
 */
internal suspend fun <T> stravaCall(
    clock: Clock,
    unauthorizedStatuses: Set<HttpStatusCode> = setOf(HttpStatusCode.Unauthorized),
    request: suspend () -> HttpResponse,
    decode: suspend (HttpResponse) -> T,
): StravaResult<T> =
    try {
        val response = request()
        when {
            response.status.isSuccess() -> StravaResult.Success(decode(response))
            response.status in unauthorizedStatuses -> StravaResult.Unauthorized
            response.status == HttpStatusCode.NotFound -> StravaResult.NotFound
            response.status == HttpStatusCode.TooManyRequests ->
                StravaResult.RateLimited(RateLimitHeaders.retryAfter(response.headers, clock.instant()))
            else -> StravaResult.Unavailable.also { log.warn("Strava returned status {}", response.status.value) }
        }
    } catch (e: IOException) {
        unavailable(e)
    } catch (e: UnresolvedAddressException) {
        unavailable(e)
    } catch (e: SerializationException) {
        unavailable(e)
    } catch (e: ContentConvertException) {
        // Ktor wraps JSON decode errors; their message can quote the response body, so it is never logged.
        unavailable(e)
    }

private fun unavailable(e: Exception): StravaResult<Nothing> {
    log.warn("Strava request failed: {}", e::class.simpleName)
    return StravaResult.Unavailable
}
