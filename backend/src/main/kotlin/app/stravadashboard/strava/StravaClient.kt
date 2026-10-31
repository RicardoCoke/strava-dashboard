package app.stravadashboard.strava

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import java.time.Clock
import java.time.Instant

/**
 * Strava API v3 calls. Each call gets a fresh token; a 401 triggers one refresh and one retry, and a second 401
 * deletes the tokens.
 */
class StravaClient(
    private val http: HttpClient,
    private val tokenProvider: StravaTokenProvider,
    private val clock: Clock,
    private val baseUrl: String = BASE_URL,
) {
    internal suspend fun listActivities(
        after: Instant,
        page: Int,
        perPage: Int,
    ): StravaResult<List<SummaryActivityDto>> =
        authorizedGet(
            "/athlete/activities",
            mapOf("after" to after.epochSecond.toString(), "page" to page.toString(), "per_page" to perPage.toString()),
        ) { it.body() }

    internal suspend fun activity(id: Long): StravaResult<DetailedActivityDto> =
        authorizedGet("/activities/$id", mapOf("include_all_efforts" to "false")) { it.body() }

    /** Only `time` and `velocity_smooth`; location and heart-rate streams are never requested. */
    internal suspend fun speedStreams(id: Long): StravaResult<StreamSetDto> =
        authorizedGet("/activities/$id/streams", mapOf("keys" to "time,velocity_smooth", "key_by_type" to "true")) {
            it.body()
        }

    private suspend fun <T> authorizedGet(
        path: String,
        params: Map<String, String>,
        decode: suspend (HttpResponse) -> T,
    ): StravaResult<T> =
        withToken(tokenProvider.current()) { token ->
            val first = get(path, params, token, decode)
            if (first is StravaResult.Unauthorized) {
                withToken(tokenProvider.refreshed()) { retryToken ->
                    get(
                        path,
                        params,
                        retryToken,
                        decode,
                    ).also { if (it is StravaResult.Unauthorized) tokenProvider.revoke() }
                }
            } else {
                first
            }
        }

    private suspend fun <T> get(
        path: String,
        params: Map<String, String>,
        token: String,
        decode: suspend (HttpResponse) -> T,
    ): StravaResult<T> =
        stravaCall(
            clock = clock,
            request = {
                http.get("$baseUrl$path") {
                    bearerAuth(token)
                    params.forEach { (key, value) -> parameter(key, value) }
                }
            },
            decode = decode,
        )

    private inline fun <T> withToken(
        token: StravaResult<String>,
        block: (String) -> StravaResult<T>,
    ): StravaResult<T> =
        when (token) {
            is StravaResult.Success -> block(token.value)
            StravaResult.Unauthorized -> StravaResult.Unauthorized
            StravaResult.NotFound -> StravaResult.Unavailable
            is StravaResult.RateLimited -> token
            StravaResult.Unavailable -> StravaResult.Unavailable
        }

    companion object {
        const val BASE_URL = "https://www.strava.com/api/v3"
    }
}
