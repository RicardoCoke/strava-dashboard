package app.stravadashboard.api

import app.stravadashboard.config.AppConfig
import app.stravadashboard.config.MaxHrSetting
import app.stravadashboard.domain.model.ActivityId
import app.stravadashboard.domain.model.Bpm
import app.stravadashboard.domain.model.Seconds
import app.stravadashboard.domain.ports.ActivityDetail
import app.stravadashboard.module
import app.stravadashboard.persistence.DatabaseFactory
import app.stravadashboard.persistence.ExposedRunRepository
import app.stravadashboard.testsupport.GARMIN_LIST_PATH
import app.stravadashboard.testsupport.GarminFake
import app.stravadashboard.testsupport.TEST_ZONE
import app.stravadashboard.testsupport.browser
import app.stravadashboard.testsupport.summary
import app.stravadashboard.testsupport.syncAndWait
import app.stravadashboard.testsupport.testConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Clock
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Vo2maxRoutesTest {
    // Saturday 2026-10-31, as in the spec examples.
    private val now = Instant.parse("2026-10-31T10:00:00Z")
    private val clock = Clock.fixed(now, TEST_ZONE)
    private val garmin = GarminFake()

    private fun config(maxHr: MaxHrSetting): AppConfig = testConfig().copy(maxHr = maxHr)

    private fun app(
        config: AppConfig,
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        application { module(config, clock, TEST_ZONE, garminEngine = garmin.engine, syncAtStartup = false) }
        block()
    }

    private fun ApplicationTestBuilder.connectedBrowser(): HttpClient = browser()

    /** An outdoor easy run on Fri 2026-10-30: 10 km in 3000 s at HR 150 → VO2max 57.16 with max HR 194. */
    private fun seedRun(config: AppConfig) =
        runBlocking {
            val runs = ExposedRunRepository(DatabaseFactory.connect(config.dbPath))
            runs.upsertSummaries(listOf(summary(500, Instant.parse("2026-10-30T07:00:00Z"))))
            runs.saveDetail(ActivityId(500), ActivityDetail(null, emptyList(), Seconds(3000)), now)
        }

    private suspend fun HttpResponse.json(): JsonObject = Json.parseToJsonElement(bodyAsText()).jsonObject

    private fun keys(names: String): Set<String> = names.split(" ").toSet()

    @Test
    fun `R9 - 422 invalid config without sync`() =
        app(config(MaxHrSetting.Invalid)) {
            val response = connectedBrowser().get("/api/vo2max")
            assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
            assertEquals(
                """{"error":"vo2max_invalid_config",""" +
                    """"message":"MAX_HR_BPM is not a whole number between 120 and 230."}""",
                response.bodyAsText(),
            )
            assertTrue(garmin.requests.isEmpty())
        }

    @Test
    fun `R9 - 200 exact JSON shape and rounding`() {
        val config = config(MaxHrSetting.Default)
        app(config) {
            val response = connectedBrowser().also { seedRun(config) }.get("/api/vo2max")
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.json()

            assertEquals(
                keys("maxHr current predictions windowStart windowEnd weeks runsPendingDetail"),
                body.keys,
            )
            assertEquals("""{"bpm":194,"source":"default"}""", body.getValue("maxHr").toString())
            val current = body.getValue("current").jsonObject
            assertEquals(keys("vo2max basis periodStart periodEnd runCount"), current.keys)
            assertEquals("57.2", current["vo2max"]?.jsonPrimitive?.content)
            assertEquals("last7Days", current["basis"]?.jsonPrimitive?.content)
            val predictions = body.getValue("predictions").jsonArray.map { it.jsonObject }
            assertEquals(listOf("5k", "10k", "half", "marathon"), predictions.map { it["key"]?.jsonPrimitive?.content })
            assertEquals(keys("key label timeSeconds note"), predictions.first().keys)
            assertEquals(JsonNull, predictions.first()["note"])
            assertEquals(MARATHON_NOTE, predictions.last()["note"]?.jsonPrimitive?.content)
            val weeks = body.getValue("weeks").jsonArray.map { it.jsonObject }
            assertEquals(27, weeks.size)
            assertEquals(keys("weekStart weekEnd vo2max runCount inProgress"), weeks.first().keys)
            assertEquals(JsonNull, weeks.first()["vo2max"])
            assertEquals("57.2", weeks.last()["vo2max"]?.jsonPrimitive?.content)
        }
    }

    @Test
    fun `R9 - configured max HR is reported`() {
        val config = config(MaxHrSetting.Configured(Bpm(185)))
        app(config) {
            val body = connectedBrowser().get("/api/vo2max").json()
            assertEquals("""{"bpm":185,"source":"configured"}""", body.getValue("maxHr").toString())
            assertEquals(JsonNull, body["current"])
            assertEquals(0, body.getValue("predictions").jsonArray.size)
        }
    }

    @Test
    fun `009 R1 - read endpoints make makes no Garmin calls when a sync is due`() =
        app(config(MaxHrSetting.Default)) {
            val browser = connectedBrowser()
            val statuses =
                coroutineScope {
                    listOf("/api/vo2max", "/api/training-load", "/api/dashboard/running")
                        .map { path -> async { browser.get(path).status } }
                        .awaitAll()
                }
            assertEquals(HttpStatusCode.OK, statuses[0])
            assertEquals(0, garmin.requests.count { it.url.encodedPath == GARMIN_LIST_PATH })
        }

    @Test
    fun `R9 - rate limited or unavailable sync still serves stored data`() {
        listOf(HttpStatusCode.TooManyRequests, HttpStatusCode.InternalServerError).forEach { failure ->
            garmin.failWith = null
            val config = config(MaxHrSetting.Default)
            app(config) {
                val browser = connectedBrowser().also { seedRun(config) }
                garmin.failWith = failure
                browser.syncAndWait()
                val response = browser.get("/api/vo2max")
                assertEquals(HttpStatusCode.OK, response.status, "$failure")
                assertEquals(
                    "57.2",
                    response
                        .json()
                        .getValue("current")
                        .jsonObject["vo2max"]
                        ?.jsonPrimitive
                        ?.content,
                )
            }
        }
    }
}
