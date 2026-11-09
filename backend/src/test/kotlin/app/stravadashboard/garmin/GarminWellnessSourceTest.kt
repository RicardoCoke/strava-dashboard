package app.stravadashboard.garmin

import app.stravadashboard.domain.wellness.WellnessFetchResult
import app.stravadashboard.testsupport.NOW
import app.stravadashboard.testsupport.fixedClock
import app.stravadashboard.testsupport.wellnessDay
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

// All keys, tokens and values here are invented.
class GarminWellnessSourceTest {
    private val key = "invented-garmin-key"
    private val oldest = LocalDate.of(2026, 8, 1)
    private val newest = LocalDate.of(2026, 10, 31)
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val requests = mutableListOf<HttpRequestData>()
    private val base = "https://personal.connectapi.garmin.com"
    private val tokenPath = "/personalapitoken-service/personal-api/personal-api-key/token"

    private fun resource(name: String): String = requireNotNull(javaClass.getResource("/garmin/$name")).readText()

    /** Answers token, daily summaries and HRV with the fixtures, unless [handler] answers first. */
    private fun MockRequestHandleScope.fixtures(request: HttpRequestData): HttpResponseData =
        when {
            request.url.encodedPath == tokenPath -> respond(resource("token.txt"), HttpStatusCode.OK)
            "dailysummaries" in request.url.encodedPath -> respond(resource("summaries.json"), HttpStatusCode.OK, json)
            else -> respond(resource("hrv.json"), HttpStatusCode.OK, json)
        }

    private fun source(
        clock: Clock = fixedClock(),
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { fixtures(it) },
    ): GarminWellnessSource {
        val http =
            garminHttpClient(
                MockEngine { request ->
                    requests += request
                    handler(request)
                },
            )
        return GarminWellnessSource(GarminConnect(http, key, clock))
    }

    private fun fetch(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { fixtures(it) },
    ): WellnessFetchResult = runBlocking { source(handler = handler).fetch(oldest, newest) }

    private fun status(
        code: HttpStatusCode,
        headers: Map<String, String> = emptyMap(),
    ): WellnessFetchResult =
        fetch {
            respond("{}", code, headersOf(*headers.map { (name, value) -> name to listOf(value) }.toTypedArray()))
        }

    @Test
    fun `R2 - exchanges the key for a token, then requests resting HR and HRV with it`() {
        fetch()

        val (token, summaries, hrv) = requests
        assertEquals(HttpMethod.Post, token.method)
        assertEquals(base + tokenPath, token.url.toString())
        assertEquals("ApiKey $key", token.headers[HttpHeaders.Authorization])
        // Garmin answers 406 to a token request that asks for JSON.
        assertEquals(listOf("*/*"), token.headers.getAll(HttpHeaders.Accept))

        assertEquals(HttpMethod.Get, summaries.method)
        assertEquals("$base/usersummary-service/v2/dailysummaries", summaries.url.toString().substringBefore('?'))
        assertEquals("2026-08-01", summaries.url.parameters["startDate"])
        assertEquals("2026-10-31", summaries.url.parameters["endDate"])
        assertEquals(setOf("startDate", "endDate"), summaries.url.parameters.names())

        assertEquals("$base/hrv-service/hrv/daily/2026-08-01/2026-10-31", hrv.url.toString())
        listOf(summaries, hrv).forEach {
            assertEquals("Bearer invented-access-token", it.headers[HttpHeaders.Authorization])
            assertEquals("application/json", it.headers[HttpHeaders.Accept])
        }
    }

    @Test
    fun `R2 - merges resting HR and HRV by date and skips bad dates`() {
        assertEquals(
            WellnessFetchResult.Success(
                listOf(
                    wellnessDay(LocalDate.of(2026, 10, 29), restingHr = 49, hrv = 61.0),
                    wellnessDay(LocalDate.of(2026, 10, 30), restingHr = 52),
                    wellnessDay(LocalDate.of(2026, 10, 31), hrv = 57.0),
                ),
            ),
            fetch(),
        )
    }

    @Test
    fun `R2 - HRV 204 means no HRV days`() {
        val result =
            fetch { request ->
                if ("hrv-service" in
                    request.url.encodedPath
                ) {
                    respond("", HttpStatusCode.NoContent)
                } else {
                    fixtures(request)
                }
            }
        assertEquals(
            WellnessFetchResult.Success(
                listOf(
                    wellnessDay(LocalDate.of(2026, 10, 29), restingHr = 49),
                    wellnessDay(LocalDate.of(2026, 10, 30), restingHr = 52),
                    // Empty days are dropped by WellnessSync.
                    wellnessDay(LocalDate.of(2026, 10, 31)),
                ),
            ),
            result,
        )
    }

    @Test
    fun `token is reused until shortly before it expires`() {
        var now = NOW
        val clock =
            object : Clock() {
                override fun instant() = now

                override fun getZone() = ZoneOffset.UTC

                override fun withZone(zone: java.time.ZoneId?) = this
            }
        val source = source(clock)
        runBlocking { source.fetch(oldest, newest) }
        now = NOW.plus(Duration.ofHours(23))
        runBlocking { source.fetch(oldest, newest) }
        now = NOW.plus(Duration.ofHours(24).minusMinutes(4))
        runBlocking { source.fetch(oldest, newest) }

        assertEquals(2, requests.count { it.url.encodedPath == tokenPath })
    }

    @Test
    fun `a cached token that is rejected gets one retry with a new token`() {
        var rejectData = false
        val source =
            source { request ->
                if (rejectData && request.url.encodedPath != tokenPath) {
                    rejectData = false
                    respond("{}", HttpStatusCode.Unauthorized)
                } else {
                    fixtures(request)
                }
            }
        runBlocking { source.fetch(oldest, newest) }
        rejectData = true
        val result = runBlocking { source.fetch(oldest, newest) }

        assertEquals(3, (result as WellnessFetchResult.Success).days.size)
        assertEquals(2, requests.count { it.url.encodedPath == tokenPath })
    }

    @Test
    fun `R2 - 401 and 403 map to key rejected`() {
        assertEquals(WellnessFetchResult.KeyRejected, status(HttpStatusCode.Unauthorized))
        assertEquals(WellnessFetchResult.KeyRejected, status(HttpStatusCode.Forbidden))
    }

    @Test
    fun `R2 - 429 uses Retry-After or 15 minutes`() {
        val fallback = WellnessFetchResult.RateLimited(NOW.plus(Duration.ofMinutes(15)))
        assertEquals(
            WellnessFetchResult.RateLimited(NOW.plusSeconds(120)),
            status(HttpStatusCode.TooManyRequests, mapOf(HttpHeaders.RetryAfter to "120")),
        )
        assertEquals(fallback, status(HttpStatusCode.TooManyRequests))
        assertEquals(fallback, status(HttpStatusCode.TooManyRequests, mapOf(HttpHeaders.RetryAfter to "0")))
        assertEquals(fallback, status(HttpStatusCode.TooManyRequests, mapOf(HttpHeaders.RetryAfter to "86401")))
        assertEquals(fallback, status(HttpStatusCode.TooManyRequests, mapOf(HttpHeaders.RetryAfter to "soon")))
    }

    @Test
    fun `R2 - 5xx, network error and bad JSON map to unavailable`() {
        assertEquals(WellnessFetchResult.Unavailable, status(HttpStatusCode.InternalServerError))
        assertEquals(WellnessFetchResult.Unavailable, status(HttpStatusCode.NotFound))
        assertEquals(WellnessFetchResult.Unavailable, fetch { throw IOException("invented network failure") })
        assertEquals(
            WellnessFetchResult.Unavailable,
            fetch { request ->
                if ("dailysummaries" in request.url.encodedPath) {
                    respond(resource("wellness-malformed.json"), HttpStatusCode.OK, json)
                } else {
                    fixtures(request)
                }
            },
        )
        assertEquals(WellnessFetchResult.Unavailable, fetch { respond("not json", HttpStatusCode.OK, json) })
        val html = headersOf(HttpHeaders.ContentType, "text/html")
        assertEquals(WellnessFetchResult.Unavailable, fetch { respond("<html>proxy</html>", HttpStatusCode.OK, html) })
        assertEquals(
            WellnessFetchResult.Unavailable,
            fetch { request -> throw HttpRequestTimeoutException(request.url.toString(), 15_000) },
        )
    }

    @Test
    fun `a body without the expected list or an unexpected 204 is unavailable, not empty`() {
        // Reading these as "no data" would make the sync wipe the cached range.
        val noList =
            fetch {
                if (it.url.encodedPath ==
                    tokenPath
                ) {
                    fixtures(it)
                } else {
                    respond("{}", HttpStatusCode.OK, json)
                }
            }
        val summaries204 =
            fetch {
                if ("dailysummaries" in
                    it.url.encodedPath
                ) {
                    respond("", HttpStatusCode.NoContent)
                } else {
                    fixtures(it)
                }
            }
        assertEquals(WellnessFetchResult.Unavailable, noList)
        assertEquals(WellnessFetchResult.Unavailable, summaries204)
    }

    @Test
    fun `a blank token is unavailable and makes no data calls`() {
        assertEquals(WellnessFetchResult.Unavailable, fetch { respond("  \n", HttpStatusCode.OK) })
        assertEquals(1, requests.size)
    }

    @Test
    fun `R2 - logs never contain key, token, query or values`() {
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        val root = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
        root.addAppender(appender)
        try {
            fetch()
            status(HttpStatusCode.Unauthorized)
            status(HttpStatusCode.TooManyRequests)
            status(HttpStatusCode.BadGateway)
            fetch { request ->
                if ("dailysummaries" in request.url.encodedPath) {
                    respond(resource("wellness-malformed.json"), HttpStatusCode.OK, json)
                } else {
                    fixtures(request)
                }
            }
            fetch { throw IOException("startDate=2026-08-01 $key") }
        } finally {
            root.detachAppender(appender)
        }

        val logged = appender.list.joinToString("\n") { it.formattedMessage + (it.throwableProxy?.message ?: "") }
        listOf(key, "invented-access-token", "ApiKey", "Bearer", "Authorization", "startDate=", "61", "fifty").forEach {
            assertFalse(it in logged, "log leaked '$it'")
        }
    }
}
