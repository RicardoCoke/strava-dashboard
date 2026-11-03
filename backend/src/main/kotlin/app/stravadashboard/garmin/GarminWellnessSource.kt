package app.stravadashboard.garmin

import app.stravadashboard.domain.model.Bpm
import app.stravadashboard.domain.model.roundHalfUpToInt
import app.stravadashboard.domain.ports.WellnessSource
import app.stravadashboard.domain.wellness.HrvMs
import app.stravadashboard.domain.wellness.WellnessDay
import app.stravadashboard.domain.wellness.WellnessFetchResult
import app.stravadashboard.domain.wellness.WellnessPolicy
import io.ktor.client.HttpClient
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.url
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
import java.time.format.DateTimeParseException

/** One Garmin call: its decoded body, or the fetch result to stop with. */
private sealed interface Call<out T> {
    data class Ok<T>(
        val value: T,
    ) : Call<T>

    data class Failed(
        val result: WellnessFetchResult,
    ) : Call<Nothing>
}

private inline fun <T, R> Call<T>.then(next: (T) -> Call<R>): Call<R> =
    when (this) {
        is Call.Ok -> next(value)
        is Call.Failed -> this
    }

private class AccessToken(
    val value: String,
    val expiresAt: Instant,
)

/**
 * Daily resting HR and HRV from the Garmin Connect Personal API (feature 015). The personal API key is exchanged for
 * an access token (kept in memory until shortly before it expires), then resting HR comes from the daily summaries
 * and HRV from the overnight HRV summaries, merged by local date. Only the status code or the exception class is
 * logged, never the key, token, URL, query or body.
 */
class GarminWellnessSource(
    private val http: HttpClient,
    private val apiKey: String,
    private val clock: Clock,
) : WellnessSource {
    private var token: AccessToken? = null

    override suspend fun fetch(
        oldest: LocalDate,
        newest: LocalDate,
    ): WellnessFetchResult {
        val reusedToken = validToken() != null
        val result = attempt(oldest, newest)
        // A cached token Garmin no longer accepts gets one retry with a fresh token.
        return if (result == WellnessFetchResult.KeyRejected && reusedToken) attempt(oldest, newest) else result
    }

    private suspend fun attempt(
        oldest: LocalDate,
        newest: LocalDate,
    ): WellnessFetchResult {
        val days =
            accessToken().then { access ->
                call<GarminDailySummariesDto>(access) {
                    url(BASE_URL + SUMMARIES_PATH)
                    parameter("startDate", oldest.toString())
                    parameter("endDate", newest.toString())
                }.then { summaries ->
                    call(access, empty = GarminHrvDailyDto(emptyList())) { url("$BASE_URL$HRV_PATH/$oldest/$newest") }
                        .then { hrv -> Call.Ok(summaries to hrv) }
                }
            }
        return when (days) {
            is Call.Ok -> {
                val (summaries, hrv) = days.value
                // Counts only: the values are health data.
                log.info(
                    "Garmin Connect returned {} summaries ({} with resting HR), {} HRV summaries ({} with HRV)",
                    summaries.data.size,
                    summaries.data.count { it.heartRate?.restingValue != null },
                    hrv.hrvSummaries.size,
                    hrv.hrvSummaries.count { it.lastNightAvg != null },
                )
                WellnessFetchResult.Success(merge(summaries, hrv))
            }
            is Call.Failed -> days.result
        }
    }

    private fun validToken(): AccessToken? = token?.takeIf { clock.instant() < it.expiresAt }

    private suspend fun accessToken(): Call<String> {
        validToken()?.let { return Call.Ok(it.value) }
        token = null
        // The token endpoint answers with the bare token as plain text, not JSON.
        val call =
            request<String> { http.post(BASE_URL + TOKEN_PATH) { header(HttpHeaders.Authorization, "ApiKey $apiKey") } }
        val value = (call as? Call.Ok)?.value?.trim()
        return when {
            call is Call.Failed -> call
            value.isNullOrEmpty() -> {
                log.warn("Garmin Connect returned an empty token")
                Call.Failed(WellnessFetchResult.Unavailable)
            }
            else -> {
                // shortcut: assumes the documented 24 h lifetime instead of reading the JWT's `exp`.
                token = AccessToken(value, clock.instant().plus(MAX_TOKEN_LIFETIME).minus(TOKEN_MARGIN))
                Call.Ok(value)
            }
        }
    }

    /** [empty] is what a 204 ("no data") means for this call; without it a 204 is unexpected. */
    private suspend inline fun <reified T> call(
        access: String,
        empty: T? = null,
        crossinline block: HttpRequestBuilder.() -> Unit,
    ): Call<T> {
        val result =
            request(empty) {
                http.get {
                    block()
                    header(HttpHeaders.Authorization, "Bearer $access")
                    accept(ContentType.Application.Json)
                }
            }
        if (result == Call.Failed(WellnessFetchResult.KeyRejected)) token = null
        return result
    }

    private suspend inline fun <reified T> request(
        empty: T? = null,
        send: () -> HttpResponse,
    ): Call<T> =
        try {
            map(send(), empty)
        } catch (e: IOException) {
            unavailable(e)
        } catch (e: UnresolvedAddressException) {
            unavailable(e)
        } catch (e: SerializationException) {
            unavailable(e)
        } catch (e: ContentConvertException) {
            // Its message can quote the response body, so only the class name is logged.
            unavailable(e)
        } catch (e: NoTransformationFoundException) {
            // A 200 that isn't JSON, such as an HTML page from a proxy.
            unavailable(e)
        }

    private suspend inline fun <reified T> map(
        response: HttpResponse,
        empty: T?,
    ): Call<T> =
        when {
            response.status == HttpStatusCode.NoContent && empty != null -> Call.Ok(empty)
            response.status == HttpStatusCode.NoContent -> {
                log.warn("Garmin Connect returned status 204")
                Call.Failed(WellnessFetchResult.Unavailable)
            }
            response.status.isSuccess() -> Call.Ok(response.body())
            response.status in REJECTED -> Call.Failed(WellnessFetchResult.KeyRejected)
            response.status == HttpStatusCode.TooManyRequests ->
                Call.Failed(WellnessFetchResult.RateLimited(retryAt(response.headers[HttpHeaders.RetryAfter])))
            else -> {
                log.warn("Garmin Connect returned status {}", response.status.value)
                Call.Failed(WellnessFetchResult.Unavailable)
            }
        }

    /** `Retry-After` in whole seconds (1 to 86400); anything else waits the 15-minute fallback. */
    private fun retryAt(header: String?): Instant {
        val seconds = header?.trim()?.toLongOrNull()?.takeIf { it in 1..MAX_RETRY_AFTER_SECONDS }
        return clock.instant().plus(seconds?.let(Duration::ofSeconds) ?: WellnessPolicy.RATE_LIMIT_FALLBACK)
    }

    private fun unavailable(e: Exception): Call<Nothing> {
        log.warn("Garmin Connect request failed: {}", e::class.simpleName)
        return Call.Failed(WellnessFetchResult.Unavailable)
    }

    private companion object {
        const val BASE_URL = "https://personal.connectapi.garmin.com"
        const val TOKEN_PATH = "/personalapitoken-service/personal-api/personal-api-key/token"
        const val SUMMARIES_PATH = "/usersummary-service/v2/dailysummaries"
        const val HRV_PATH = "/hrv-service/hrv/daily"
        const val MAX_RETRY_AFTER_SECONDS = 86_400L
        val MAX_TOKEN_LIFETIME: Duration = Duration.ofHours(24)
        val TOKEN_MARGIN: Duration = Duration.ofMinutes(5)
        val REJECTED = setOf(HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden)
        val log = LoggerFactory.getLogger(GarminWellnessSource::class.java)

        /** One day per date seen in either response; an element whose date isn't ISO is skipped. */
        fun merge(
            summaries: GarminDailySummariesDto,
            hrv: GarminHrvDailyDto,
        ): List<WellnessDay> {
            val resting =
                summaries.data
                    .mapNotNull { dto ->
                        date(dto.calendarDate)?.let { it to dto.heartRate?.restingValue }
                    }.toMap()
            val overnight =
                hrv.hrvSummaries
                    .mapNotNull { dto ->
                        date(dto.calendarDate)?.let { it to dto.lastNightAvg }
                    }.toMap()
            return (resting.keys + overnight.keys).sorted().map { date ->
                WellnessDay(
                    date,
                    resting[date]?.let { Bpm(roundHalfUpToInt(it)) },
                    overnight[date]?.let(::HrvMs),
                )
            }
        }

        @Suppress("SwallowedException")
        fun date(text: String): LocalDate? =
            try {
                LocalDate.parse(text)
            } catch (e: DateTimeParseException) {
                null
            }
    }
}
