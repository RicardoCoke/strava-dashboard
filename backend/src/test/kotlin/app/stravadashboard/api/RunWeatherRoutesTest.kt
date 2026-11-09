package app.stravadashboard.api

import app.stravadashboard.config.WeatherLocation
import app.stravadashboard.module
import app.stravadashboard.testsupport.FakePlanSource
import app.stravadashboard.testsupport.GarminFake
import app.stravadashboard.testsupport.TEST_ZONE
import app.stravadashboard.testsupport.browser
import app.stravadashboard.testsupport.testConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.time.Clock
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

// All forecast values here are invented.
class RunWeatherRoutesTest {
    private val garmin = GarminFake()
    private val weatherRequests = mutableListOf<HttpRequestData>()
    private val clock = Clock.fixed(Instant.parse("2026-11-05T10:00:00Z"), TEST_ZONE)
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val forecast =
        """{"hourly":{
            "time":["2026-11-05T09:00","2026-11-05T10:00","2026-11-05T11:00",
                    "2026-11-06T09:00","2026-11-06T10:00","2026-11-06T11:00",
                    "2026-11-07T09:00","2026-11-07T10:00","2026-11-07T11:00"],
            "precipitation":[0,0,0, 0,0,0, 0,0.4,0.8],
            "cloud_cover":[0,0,0, 100,100,100, 0,0,0]}}"""

    private fun app(
        home: WeatherLocation? = WeatherLocation(10.5, 20.5),
        weatherStatus: HttpStatusCode = HttpStatusCode.OK,
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        val weather =
            MockEngine { request ->
                weatherRequests += request
                respond(forecast, weatherStatus, json)
            }
        application {
            module(
                testConfig().copy(weatherHome = home),
                clock,
                TEST_ZONE,
                FakePlanSource(),
                garminEngine = garmin.engine,
                weatherEngine = weather,
            )
        }
        block()
    }

    private fun ApplicationTestBuilder.connectedBrowser(): HttpClient = browser()

    @Test
    fun `R6 - returns the exact JSON`() =
        app {
            val response = connectedBrowser().get("/api/run-weather")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(
                """{"days":[{"date":"2026-11-05","condition":"sun","rainMm":null},""" +
                    """{"date":"2026-11-06","condition":"cloud","rainMm":null},""" +
                    """{"date":"2026-11-07","condition":"rain","rainMm":1.2}]}""",
                response.bodyAsText(),
            )
        }

    @Test
    fun `R6 - no location is 404`() =
        app(home = null) {
            val response = connectedBrowser().get("/api/run-weather")
            assertEquals(HttpStatusCode.NotFound, response.status)
            assertEquals(
                """{"error":"weather_not_configured","message":"No weather location is configured."}""",
                response.bodyAsText(),
            )
            assertEquals(emptyList(), weatherRequests)
        }

    @Test
    fun `R6 - home failure is 503`() =
        app(weatherStatus = HttpStatusCode.InternalServerError) {
            val response = connectedBrowser().get("/api/run-weather")
            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
            assertEquals(
                """{"error":"weather_unavailable","message":"The weather forecast is unavailable."}""",
                response.bodyAsText(),
            )
        }

    @Test
    fun `R10 - weather makes no Strava call`() =
        app {
            connectedBrowser().get("/api/run-weather")
            assertEquals(1, weatherRequests.size)
            assertEquals(emptyList(), garmin.requests)
        }
}
