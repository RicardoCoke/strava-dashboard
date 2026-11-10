package app.stravadashboard.api

import app.stravadashboard.config.AppConfig
import app.stravadashboard.config.MarathonPaceSetting
import app.stravadashboard.domain.model.ActivityId
import app.stravadashboard.domain.model.SecondsPerKm
import app.stravadashboard.domain.ports.ActivityDetail
import app.stravadashboard.module
import app.stravadashboard.persistence.DatabaseFactory
import app.stravadashboard.persistence.ExposedRunRepository
import app.stravadashboard.testsupport.GARMIN_LIST_PATH
import app.stravadashboard.testsupport.GarminFake
import app.stravadashboard.testsupport.TEST_ZONE
import app.stravadashboard.testsupport.browser
import app.stravadashboard.testsupport.kmLap
import app.stravadashboard.testsupport.summary
import app.stravadashboard.testsupport.testConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
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
    private val garmin = GarminFake()

    private fun config(setting: MarathonPaceSetting): AppConfig = testConfig().copy(marathonPace = setting)

    private fun app(
        config: AppConfig,
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        application { module(config, clock, TEST_ZONE, garminEngine = garmin.engine, syncAtStartup = false) }
        block()
    }

    private fun ApplicationTestBuilder.connectedBrowser(): HttpClient = browser()

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
            assertTrue(garmin.requests.isEmpty())
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
                    """"message":"The goal pace is not a pace between 2:30 and 8:00 per km (m:ss)."}""",
                text,
            )
        }

    @Test
    fun `R5 - makes no Garmin calls`() {
        listOf(
            MarathonPaceSetting.Valid(SecondsPerKm(255)),
            MarathonPaceSetting.NotConfigured,
            MarathonPaceSetting.Invalid,
        ).forEach { setting ->
            app(config(setting)) {
                val browser = connectedBrowser()
                browser.get("/api/marathon-pace")
                client.get("/api/marathon-pace")
                assertTrue(garmin.requests.isEmpty(), setting.toString())
                assertFalse(garmin.requests.any { it.url.encodedPath == GARMIN_LIST_PATH })
            }
        }
    }
}
