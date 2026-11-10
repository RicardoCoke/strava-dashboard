package app.stravadashboard.api

import app.stravadashboard.config.AppConfig
import app.stravadashboard.config.MaxHrSetting
import app.stravadashboard.domain.model.ActivityId
import app.stravadashboard.domain.model.Meters
import app.stravadashboard.domain.model.MetersPerSecond
import app.stravadashboard.domain.model.Seconds
import app.stravadashboard.domain.model.Split
import app.stravadashboard.domain.ports.ActivityDetail
import app.stravadashboard.module
import app.stravadashboard.persistence.DatabaseFactory
import app.stravadashboard.persistence.ExposedRunRepository
import app.stravadashboard.testsupport.GarminFake
import app.stravadashboard.testsupport.TEST_ZONE
import app.stravadashboard.testsupport.browser
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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Clock
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NormalizedVo2maxRoutesTest {
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

    /**
     * An outdoor run on Fri 2026-10-30 with HR 150: 10 km in 3000 s, so VO2max 57.16 (max HR 194) whole-run.
     * Its splits are grade-adjusted to 4:50/km with split HR 150, so the normalized value is higher.
     */
    private fun seedRun(
        config: AppConfig,
        withSplits: Boolean = true,
    ) = runBlocking {
        val runs = ExposedRunRepository(DatabaseFactory.connect(config.dbPath))
        runs.upsertSummaries(listOf(summary(500, Instant.parse("2026-10-30T07:00:00Z"))))
        val splits =
            if (withSplits) {
                (1..10).map {
                    Split(
                        it,
                        Meters(1000.0),
                        Seconds(300),
                        Meters(10.0),
                        150.0,
                        MetersPerSecond(1000.0 / 290),
                    )
                }
            } else {
                emptyList()
            }
        runs.saveDetail(ActivityId(500), ActivityDetail(null, emptyList(), Seconds(3000), splits), now)
    }

    private suspend fun HttpResponse.json(): JsonObject = Json.parseToJsonElement(bodyAsText()).jsonObject

    private fun keys(names: String): Set<String> = names.split(" ").toSet()

    @Test
    fun `R7 - 422 invalid config`() =
        app(config(MaxHrSetting.Invalid)) {
            val response = connectedBrowser().get("/api/vo2max/normalized")
            assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
            assertEquals(
                """{"error":"vo2max_invalid_config",""" +
                    """"message":"MAX_HR_BPM is not a whole number between 120 and 230."}""",
                response.bodyAsText(),
            )
            assertTrue(garmin.requests.isEmpty())
        }

    @Test
    fun `R7 - 200 exact JSON shape and rounding`() {
        val config = config(MaxHrSetting.Default)
        app(config) {
            val response = connectedBrowser().also { seedRun(config) }.get("/api/vo2max/normalized")
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.json()

            assertEquals(keys("maxHr current predictions windowStart windowEnd weeks runsPendingDetail"), body.keys)
            assertEquals("""{"bpm":194,"source":"default"}""", body.getValue("maxHr").toString())
            val current = body.getValue("current").jsonObject
            assertEquals(keys("vo2max basis periodStart periodEnd runCount"), current.keys)
            assertEquals("59.6", current["vo2max"]?.jsonPrimitive?.content)
            assertEquals("last7Days", current["basis"]?.jsonPrimitive?.content)
            val predictions = body.getValue("predictions").jsonArray.map { it.jsonObject }
            assertEquals(listOf("5k", "10k", "half", "marathon"), predictions.map { it["key"]?.jsonPrimitive?.content })
            assertEquals(
                listOf<String?>(null, null, null, MARATHON_NOTE),
                predictions.map { (it["note"] as? JsonPrimitive)?.contentOrNull },
            )
            val weeks = body.getValue("weeks").jsonArray.map { it.jsonObject }
            assertEquals(27, weeks.size)
            assertEquals(keys("weekStart weekEnd vo2max runCount inProgress originalVo2max"), weeks.first().keys)
            assertEquals(JsonNull, weeks.first()["vo2max"])
            assertEquals(JsonNull, weeks.first()["originalVo2max"])
            assertEquals("59.6", weeks.last()["vo2max"]?.jsonPrimitive?.content)
            assertEquals("57.2", weeks.last()["originalVo2max"]?.jsonPrimitive?.content)
            assertEquals(
                0,
                body
                    .getValue("runsPendingDetail")
                    .jsonPrimitive.content
                    .toInt(),
            )
            assertTrue(body.toString().contains("500").not(), "no activity ids")
        }
    }

    @Test
    fun `R7 - makes no Garmin calls`() =
        app(config(MaxHrSetting.Default)) {
            val response = connectedBrowser().get("/api/vo2max/normalized")
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(garmin.requests.isEmpty())
        }

    @Test
    fun `R7 - pending counts splits refetch`() {
        val config = config(MaxHrSetting.Default)
        app(config) {
            val browser = connectedBrowser().also { seedRun(config) }
            transaction(DatabaseFactory.connect(config.dbPath)) { exec("UPDATE runs SET splits_saved = NULL") }
            val body = browser.get("/api/vo2max/normalized").json()
            assertEquals(
                1,
                body
                    .getValue("runsPendingDetail")
                    .jsonPrimitive.content
                    .toInt(),
            )
            assertEquals(JsonNull, body["current"])
        }
    }

    /** The JSON of [path] for the seeded run, stored with or without splits. */
    private fun bodiesWithAndWithoutSplits(path: String): List<String> =
        listOf(true, false).map { splits ->
            val config = config(MaxHrSetting.Default)
            var text = ""
            app(config) {
                val browser = connectedBrowser()
                seedRun(config, splits)
                text = browser.get(path).bodyAsText()
            }
            text
        }

    @Test
    fun `R9 - vo2max JSON unchanged with splits stored`() {
        val bodies = bodiesWithAndWithoutSplits("/api/vo2max")
        assertEquals(bodies[0], bodies[1])
    }

    @Test
    fun `R13 - other reports unchanged with splits stored`() {
        listOf(
            "/api/dashboard/running",
            "/api/training-load",
            "/api/efficiency-factor",
            "/api/long-run-drift",
            "/api/marathon-pace",
        ).forEach { path ->
            val bodies = bodiesWithAndWithoutSplits(path)
            assertTrue(bodies[0].startsWith("{"), path)
            assertEquals(bodies[0], bodies[1], path)
        }
    }
}
