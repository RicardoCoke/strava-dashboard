package app.stravadashboard.garmin

import app.stravadashboard.domain.wellness.WellnessPolicy
import io.ktor.client.HttpClient
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.exclude
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
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

/** One Garmin Connect call: its decoded body, or why it failed. */
internal sealed interface GarminCall<out T> {
    data class Ok<T>(
        val value: T,
    ) : GarminCall<T>

    data object NotFound : GarminCall<Nothing>

    data object KeyRejected : GarminCall<Nothing>

    data class RateLimited(
        val until: Instant,
    ) : GarminCall<Nothing>

    data object Unavailable : GarminCall<Nothing>
}

internal inline fun <T, R> GarminCall<T>.then(next: (T) -> GarminCall<R>): GarminCall<R> =
    when (this) {
        is GarminCall.Ok -> next(value)
        GarminCall.NotFound -> GarminCall.NotFound
        GarminCall.KeyRejected -> GarminCall.KeyRejected
        is GarminCall.RateLimited -> this
        GarminCall.Unavailable -> GarminCall.Unavailable
    }

private class AccessToken(
    val value: String,
    val expiresAt: Instant,
)

/**
 * One person's Garmin Connect Personal API connection, shared by the wellness (feature 015) and activity (feature 021)
 * sources. The personal API key is exchanged for an access token, kept in memory until shortly before it expires; a
 * cached token Garmin rejects gets one retry with a fresh one. Only the call name and the status code or exception
 * class are logged, never the key, token, URL, query or body. Calls run one at a time inside a person's background
 * sync job, so the token needs no lock.
 */
class GarminConnect(
    private val http: HttpClient,
    private val apiKey: String,
    private val clock: Clock,
) {
    private var token: AccessToken? = null

    /** A JSON GET; [empty] is what a 204 ("no data") means for this call, and without it a 204 is unexpected. */
    internal suspend fun <T> getJson(
        name: String,
        decode: suspend (HttpResponse) -> T,
        empty: T? = null,
        block: HttpRequestBuilder.() -> Unit,
    ): GarminCall<T> =
        authorized(name, empty, decode) { access ->
            http.get {
                block()
                header(HttpHeaders.Authorization, "Bearer $access")
                accept(ContentType.Application.Json)
            }
        }

    /** A binary GET (an activity file); the bytes are never logged. */
    internal suspend fun getBytes(
        name: String,
        block: HttpRequestBuilder.() -> Unit,
    ): GarminCall<ByteArray> =
        authorized(name, null, { it.body<ByteArray>() }) { access ->
            http.get {
                block()
                header(HttpHeaders.Authorization, "Bearer $access")
                exclude(ContentType.Application.Json)
                accept(ContentType.Any)
            }
        }

    private suspend fun <T> authorized(
        name: String,
        empty: T?,
        decode: suspend (HttpResponse) -> T,
        send: suspend (String) -> HttpResponse,
    ): GarminCall<T> {
        val reusedToken = validToken() != null
        val first = accessToken().then { access -> request(name, empty, decode) { send(access) } }
        if (first != GarminCall.KeyRejected) return first
        token = null
        return if (reusedToken) {
            accessToken().then { access ->
                request(name, empty, decode) { send(access) }
            }
        } else {
            first
        }
    }

    private fun validToken(): AccessToken? = token?.takeIf { clock.instant() < it.expiresAt }

    private suspend fun accessToken(): GarminCall<String> {
        validToken()?.let { return GarminCall.Ok(it.value) }
        token = null
        // The token endpoint answers with the bare token as plain text and rejects `Accept: application/json` (406),
        // which ContentNegotiation would otherwise add to every request.
        val call =
            request("token", null, { it.body<String>() }) {
                http.post(BASE_URL + TOKEN_PATH) {
                    header(HttpHeaders.Authorization, "ApiKey $apiKey")
                    exclude(ContentType.Application.Json)
                    accept(ContentType.Any)
                }
            }
        val value = (call as? GarminCall.Ok)?.value?.trim()
        return when {
            call == GarminCall.NotFound -> {
                log.warn("Garmin Connect token returned status 404")
                GarminCall.Unavailable
            }
            call !is GarminCall.Ok -> call
            value.isNullOrEmpty() -> {
                log.warn("Garmin Connect returned an empty token")
                GarminCall.Unavailable
            }
            else -> {
                // shortcut: assumes the documented 24 h lifetime instead of reading the JWT's `exp`.
                token = AccessToken(value, clock.instant().plus(MAX_TOKEN_LIFETIME).minus(TOKEN_MARGIN))
                GarminCall.Ok(value)
            }
        }
    }

    private suspend fun <T> request(
        name: String,
        empty: T?,
        decode: suspend (HttpResponse) -> T,
        send: suspend () -> HttpResponse,
    ): GarminCall<T> =
        try {
            map(name, send(), empty, decode)
        } catch (e: IOException) {
            unavailable(name, e)
        } catch (e: UnresolvedAddressException) {
            unavailable(name, e)
        } catch (e: SerializationException) {
            unavailable(name, e)
        } catch (e: ContentConvertException) {
            // Its message can quote the response body, so only the class name is logged.
            unavailable(name, e)
        } catch (e: NoTransformationFoundException) {
            // A 200 that isn't JSON, such as an HTML page from a proxy.
            unavailable(name, e)
        }

    private suspend fun <T> map(
        name: String,
        response: HttpResponse,
        empty: T?,
        decode: suspend (HttpResponse) -> T,
    ): GarminCall<T> =
        when {
            response.status == HttpStatusCode.NoContent && empty != null -> GarminCall.Ok(empty)
            response.status == HttpStatusCode.NoContent -> {
                log.warn("Garmin Connect {} returned status 204", name)
                GarminCall.Unavailable
            }
            response.status.isSuccess() -> GarminCall.Ok(decode(response))
            response.status == HttpStatusCode.NotFound -> GarminCall.NotFound
            response.status in REJECTED -> GarminCall.KeyRejected
            response.status == HttpStatusCode.TooManyRequests ->
                GarminCall.RateLimited(retryAt(response.headers[HttpHeaders.RetryAfter]))
            else -> {
                log.warn("Garmin Connect {} returned status {}", name, response.status.value)
                GarminCall.Unavailable
            }
        }

    /** `Retry-After` in whole seconds (1 to 86400); anything else waits the 15-minute fallback. */
    private fun retryAt(header: String?): Instant {
        val seconds = header?.trim()?.toLongOrNull()?.takeIf { it in 1..MAX_RETRY_AFTER_SECONDS }
        return clock.instant().plus(seconds?.let(Duration::ofSeconds) ?: WellnessPolicy.RATE_LIMIT_FALLBACK)
    }

    private fun unavailable(
        name: String,
        e: Exception,
    ): GarminCall<Nothing> {
        log.warn("Garmin Connect {} request failed: {}", name, e::class.simpleName)
        return GarminCall.Unavailable
    }

    internal companion object {
        const val BASE_URL = "https://personal.connectapi.garmin.com"
        private const val TOKEN_PATH = "/personalapitoken-service/personal-api/personal-api-key/token"
        private const val MAX_RETRY_AFTER_SECONDS = 86_400L
        private val MAX_TOKEN_LIFETIME: Duration = Duration.ofHours(24)
        private val TOKEN_MARGIN: Duration = Duration.ofMinutes(5)
        private val REJECTED = setOf(HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden)
        private val log = LoggerFactory.getLogger(GarminConnect::class.java)
    }
}
