package app.stravadashboard.intervals

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
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.io.IOException
import java.time.Duration
import java.time.LocalDate
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class IntervalsClientTest {
    private val key = "invented-intervals-key"
    private val oldest = LocalDate.of(2026, 8, 1)
    private val newest = LocalDate.of(2026, 10, 31)
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val requests = mutableListOf<HttpRequestData>()

    private fun resource(name: String): String = requireNotNull(javaClass.getResource("/intervals/$name")).readText()

    private fun fetch(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): WellnessFetchResult {
        val http =
            intervalsHttpClient(
                MockEngine { request ->
                    requests += request
                    handler(request)
                },
            )
        return runBlocking { IntervalsWellnessSource(IntervalsClient(http, key, fixedClock())).fetch(oldest, newest) }
    }

    private fun status(
        code: HttpStatusCode,
        headers: Map<String, String> = emptyMap(),
    ): WellnessFetchResult =
        fetch {
            respond("{}", code, headersOf(*headers.map { (name, value) -> name to listOf(value) }.toTypedArray()))
        }

    @Test
    fun `R2 - requests wellness with basic auth and three fields`() {
        fetch { respond("[]", HttpStatusCode.OK, json) }

        val request = requests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("https://intervals.icu/api/v1/athlete/0/wellness", request.url.toString().substringBefore('?'))
        assertEquals("2026-08-01", request.url.parameters["oldest"])
        assertEquals("2026-10-31", request.url.parameters["newest"])
        assertEquals("id,restingHR,hrv", request.url.parameters["fields"])
        assertEquals(setOf("oldest", "newest", "fields"), request.url.parameters.names())
        val basic = Base64.getEncoder().encodeToString("API_KEY:$key".toByteArray())
        assertEquals("Basic $basic", request.headers[HttpHeaders.Authorization])
        assertEquals("application/json", request.headers[HttpHeaders.Accept])
    }

    @Test
    fun `R2 - maps wellness array and skips bad dates`() {
        val result = fetch { respond(resource("wellness.json"), HttpStatusCode.OK, json) }

        assertEquals(
            WellnessFetchResult.Success(
                listOf(
                    wellnessDay(LocalDate.of(2026, 10, 29), restingHr = 49, hrv = 61.5),
                    wellnessDay(LocalDate.of(2026, 10, 30), restingHr = 52),
                    wellnessDay(LocalDate.of(2026, 10, 31), restingHr = 51),
                ),
            ),
            result,
        )
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
            fetch { respond(resource("wellness-malformed.json"), HttpStatusCode.OK, json) },
        )
        assertEquals(WellnessFetchResult.Unavailable, fetch { respond("not json", HttpStatusCode.OK, json) })
    }

    @Test
    fun `R2 - logs never contain key, query or values`() {
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        val root = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
        root.addAppender(appender)
        try {
            fetch { respond(resource("wellness.json"), HttpStatusCode.OK, json) }
            status(HttpStatusCode.Unauthorized)
            status(HttpStatusCode.TooManyRequests)
            status(HttpStatusCode.BadGateway)
            fetch { respond(resource("wellness-malformed.json"), HttpStatusCode.OK, json) }
            fetch { throw IOException("oldest=2026-08-01 $key") }
        } finally {
            root.detachAppender(appender)
        }

        val logged = appender.list.joinToString("\n") { it.formattedMessage + (it.throwableProxy?.message ?: "") }
        listOf(key, "API_KEY", "Authorization", "Basic ", "oldest=", "fields=", "61.5", "fifty", "invented").forEach {
            assertFalse(it in logged, "log leaked '$it'")
        }
    }
}
