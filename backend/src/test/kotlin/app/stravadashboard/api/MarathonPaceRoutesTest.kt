package app.stravadashboard.api

import app.stravadashboard.config.AppConfig
import app.stravadashboard.config.MarathonPaceSetting
import app.stravadashboard.domain.model.ActivityId
import app.stravadashboard.domain.model.SecondsPerKm
import app.stravadashboard.domain.ports.ActivityDetail
import app.stravadashboard.module
import app.stravadashboard.persistence.DatabaseFactory
import app.stravadashboard.persistence.ExposedRunRepository
import app.stravadashboard.persistence.ExposedTokenRepository
import app.stravadashboard.testsupport.TEST_ZONE
import app.stravadashboard.testsupport.kmLap
import app.stravadashboard.testsupport.summary
import app.stravadashboard.testsupport.testConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Clock
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// All runs and laps here are invented.
class MarathonPaceRoutesTest {
    // Saturday 2026-10-31, as in the spec examples.
    private val now = Instant.parse("2026-10-31T10:00:00Z")
    private val clock = Clock.fixed(now, TEST_ZONE)
    private val stravaRequests = mutableListOf<HttpRequestData>()
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun config(setting: MarathonPaceSetting): AppConfig = testConfig().copy(marathonPace = setting)

    private fun app(
        config: AppConfig,
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        val engine =
            MockEngine { request ->
                stravaRequests += request
                if (request.url.encodedPath == "/oauth/token") {
                    respond(
                        """{"access_token":"a","refresh_token":"r","expires_at":${now.epochSecond + 99_999},
                           "athlete":{"id":7}}""",
                        HttpStatusCode.OK,
                        jsonHeaders,
                    )
                } else {
                    respond("[]", HttpStatusCode.OK, jsonHeaders)
                }
            }
        application { module(config, clock, TEST_ZONE, engine) }
        block()
    }

    private suspend fun ApplicationTestBuilder.connectedBrowser(): HttpClient {
        val browser =
            createClient {
                install(HttpCookies)
                followRedirects = false
            }
        val login = browser.get("/auth/strava/login")
        val state = Url(requireNotNull(login.headers[HttpHeaders.Location])).parameters["state"]
        browser.get("/auth/strava/callback?state=$state&code=invented&scope=read,activity:read_all")
        stravaRequests.clear()
        return browser
    }

    /** Tue 2026-10-27: 16 km with 6.4 km at goal pace. Thu 2026-10-29: 10 km on the treadmill. */
    private fun seedRuns(config: AppConfig) =
        runBlocking {
            val runs = ExposedRunRepository(DatabaseFactory.connect(config.dbPath))
            runs.upsertSummaries(
                listOf(
                    summary(1, Instant.parse("2026-10-27T07:00:00Z"), distanceMeters = 16_000.0),
                    summary(2, Instant.parse("2026-10-29T07:00:00Z"), trainer = true),
                ),
            )
            val laps = listOf(kmLap(1, 1632, 6400.0), kmLap(2, 3000, 9600.0))
            runs.saveDetail(ActivityId(1), ActivityDetail(null, laps), now)
        }

    private suspend fun HttpResponse.json(): JsonObject = Json.parseToJsonElement(bodyAsText()).jsonObject

    private fun keys(names: String): Set<String> = names.split(" ").toSet()

    @Test
    fun `R5 - returns the marathon pace contract`() {
        val config = config(MarathonPaceSetting.Valid(SecondsPerKm(255)))
        app(config) {
            val response = connectedBrowser().also { seedRuns(config) }.get("/api/marathon-pace")
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.json()
            assertEquals(keys("goal buildStart buildEnd weeks summary runsPendingLaps"), body.keys)
            assertEquals(
                """{"paceSecondsPerKm":255,"toleranceSecondsPerKm":10,"fastestSecondsPerKm":245,""" +
                    """"slowestSecondsPerKm":265,"minLapMeters":200}""",
                body.getValue("goal").toString(),
            )
            assertEquals("2026-08-17", body["buildStart"]?.jsonPrimitive?.content)
            assertEquals("2026-12-06", body["buildEnd"]?.jsonPrimitive?.content)
            val weeks = body.getValue("weeks").jsonArray
            assertEquals(16, weeks.size)
            assertEquals(
                """{"weekIndex":11,"weekStart":"2026-10-26","weekEnd":"2026-11-01","label":"26 Oct",""" +
                    """"mpKm":6.4,"isCurrentWeek":true,"isFuture":false}""",
                weeks[10].toString(),
            )
            assertEquals("7 Sep", weeks[3].jsonObject["label"]?.jsonPrimitive?.content)
            assertEquals(
                """{"totalMpKm":6.4,"thisWeekStart":"2026-10-26","thisWeekEnd":"2026-11-01",""" +
                    """"thisWeekMpKm":6.4,"thisWeekTotalKm":26.0,"thisWeekSharePercent":25}""",
                body.getValue("summary").toString(),
            )
            assertEquals(JsonPrimitive(0), body["runsPendingLaps"])
            assertTrue(stravaRequests.isEmpty())
        }
    }

    @Test
    fun `R5 - share is null in the JSON with no runs this week`() =
        app(config(MarathonPaceSetting.Valid(SecondsPerKm(255)))) {
            val summary =
                connectedBrowser()
                    .get("/api/marathon-pace")
                    .json()
                    .getValue("summary")
                    .jsonObject
            assertEquals("null", summary["thisWeekSharePercent"].toString())
        }

    @Test
    fun `R5 - not connected gives 401`() =
        app(config(MarathonPaceSetting.Valid(SecondsPerKm(255)))) {
            val response = client.get("/api/marathon-pace")
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertEquals(
                """{"error":"not_connected","message":"Connect your Strava account."}""",
                response.bodyAsText(),
            )
        }

    @Test
    fun `R5 - session without stored tokens gives 401`() {
        val config = config(MarathonPaceSetting.Valid(SecondsPerKm(255)))
        app(config) {
            val browser = connectedBrowser().also { seedRuns(config) }
            runBlocking { ExposedTokenRepository(DatabaseFactory.connect(config.dbPath)).delete() }
            val response = browser.get("/api/marathon-pace")
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertEquals(
                """{"error":"not_connected","message":"Connect your Strava account."}""",
                response.bodyAsText(),
            )
        }
    }

    @Test
    fun `R5 - not configured gives 404`() =
        app(config(MarathonPaceSetting.NotConfigured)) {
            val response = connectedBrowser().get("/api/marathon-pace")
            assertEquals(HttpStatusCode.NotFound, response.status)
            assertEquals(
                """{"error":"marathon_pace_not_configured","message":"No goal marathon pace is configured."}""",
                response.bodyAsText(),
            )
        }

    @Test
    fun `R5 - invalid config gives 422 without the value`() =
        app(config(MarathonPaceSetting.Invalid)) {
            val response = connectedBrowser().get("/api/marathon-pace")
            assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
            val text = response.bodyAsText()
            assertEquals(
                """{"error":"marathon_pace_invalid_config",""" +
                    """"message":"MARATHON_GOAL_PACE is not a pace between 2:30 and 8:00 per km (m:ss)."}""",
                text,
            )
        }

    @Test
    fun `R5 - never calls Strava`() {
        listOf(
            MarathonPaceSetting.Valid(SecondsPerKm(255)),
            MarathonPaceSetting.NotConfigured,
            MarathonPaceSetting.Invalid,
        ).forEach { setting ->
            app(config(setting)) {
                val browser = connectedBrowser()
                browser.get("/api/marathon-pace")
                client.get("/api/marathon-pace")
                assertTrue(stravaRequests.isEmpty(), setting.toString())
                assertFalse(stravaRequests.any { it.url.encodedPath.startsWith("/api/v3") })
            }
        }
    }
}
