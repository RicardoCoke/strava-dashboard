package app.stravadashboard.weather

import app.stravadashboard.config.WeatherLocation
import app.stravadashboard.domain.weather.HourlyWeather
import app.stravadashboard.domain.weather.WeatherFetchResult
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
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.channels.UnresolvedAddressException
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

// All forecast values here are invented.
class OpenMeteoClientTest {
    private val location = WeatherLocation(10.5, 20.5)
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val requests = mutableListOf<HttpRequestData>()
    private val fixture = requireNotNull(javaClass.getResource("/open-meteo/forecast.json")).readText()

    private fun fetch(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = {
            respond(fixture, HttpStatusCode.OK, json)
        },
    ): WeatherFetchResult {
        val http =
            openMeteoHttpClient(
                MockEngine { request ->
                    requests += request
                    handler(request)
                },
            )
        return runBlocking { OpenMeteoClient(http).fetch(location) }
    }

    @Test
    fun `R2 - sends the exact forecast query`() {
        fetch()
        val url = requests.single().url
        assertEquals("api.open-meteo.com", url.host)
        assertEquals("/v1/forecast", url.encodedPath)
        assertEquals(
            mapOf(
                "latitude" to "10.5",
                "longitude" to "20.5",
                "hourly" to "precipitation,cloud_cover",
                "forecast_days" to "16",
                "timezone" to "auto",
            ),
            url.parameters.entries().associate { it.key to it.value.single() },
        )
    }

    @Test
    fun `R2 - decodes hourly values and keeps nulls`() {
        assertEquals(
            WeatherFetchResult.Success(
                listOf(
                    HourlyWeather(LocalDateTime.of(2026, 11, 5, 9, 0), 0.0, 75.0),
                    HourlyWeather(LocalDateTime.of(2026, 11, 5, 10, 0), 0.4, null),
                    HourlyWeather(LocalDateTime.of(2026, 11, 5, 11, 0), null, 100.0),
                ),
            ),
            fetch(),
        )
    }

    @Test
    fun `R2 - every failure is Unavailable after one attempt`() {
        val assertUnavailable = { handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData ->
            requests.clear()
            assertEquals(WeatherFetchResult.Unavailable, fetch(handler))
            assertEquals(1, requests.size)
        }
        listOf(
            """not json""",
            """{"hourly":{"time":["2026-11-05T09:00"],"precipitation":[],"cloud_cover":[0]}}""",
            """{"hourly":{"time":["2026-11-05T09:00"],"precipitation":[0.0],"cloud_cover":[]}}""",
            """{"hourly":{"time":["yesterday"],"precipitation":[0.0],"cloud_cover":[0]}}""",
            """{"nothing":true}""",
        ).forEach { body -> assertUnavailable { respond(body, HttpStatusCode.OK, json) } }
        assertUnavailable { respond("slow down", HttpStatusCode.TooManyRequests) }
        assertUnavailable { respond("no", HttpStatusCode.InternalServerError) }
        assertUnavailable { respond("<html>", HttpStatusCode.OK) }
        listOf(
            IOException("offline"),
            HttpRequestTimeoutException("invented-url", 15_000),
            UnresolvedAddressException(),
        ).forEach { failure -> assertUnavailable { throw failure } }
    }

    @Test
    fun `R2 - failure logs omit url and coordinates`() {
        val logger = LoggerFactory.getLogger(OpenMeteoClient::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        try {
            fetch { respond("no", HttpStatusCode.InternalServerError) }
            fetch { throw IOException("https://api.open-meteo.com/v1/forecast?latitude=10.5") }
        } finally {
            logger.detachAppender(appender)
        }
        val logged = appender.list.map { it.formattedMessage }
        assertEquals(2, logged.size)
        logged.forEach {
            assertFalse("10.5" in it)
            assertFalse("20.5" in it)
            assertFalse("open-meteo.com" in it)
        }
    }
}
