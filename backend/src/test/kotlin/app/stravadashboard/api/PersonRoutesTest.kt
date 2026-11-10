package app.stravadashboard.api

import app.stravadashboard.config.LthrSetting
import app.stravadashboard.config.MarathonPaceSetting
import app.stravadashboard.config.MaxHrSetting
import app.stravadashboard.config.PersonSettings
import app.stravadashboard.domain.model.Bpm
import app.stravadashboard.domain.model.SecondsPerKm
import app.stravadashboard.domain.rings.RingDay
import app.stravadashboard.module
import app.stravadashboard.persistence.DatabaseFactory
import app.stravadashboard.persistence.ExposedRingDayRepository
import app.stravadashboard.persistence.ExposedRunRepository
import app.stravadashboard.testsupport.GARMIN_TOKEN_PATH
import app.stravadashboard.testsupport.NOW
import app.stravadashboard.testsupport.TEST_ZONE
import app.stravadashboard.testsupport.browser
import app.stravadashboard.testsupport.fixedClock
import app.stravadashboard.testsupport.summary
import app.stravadashboard.testsupport.tempDbPath
import app.stravadashboard.testsupport.testConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.cookies.cookies
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Duration
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val RICARDO_KEY = "invented-garmin-key"
private const val MARINA_KEY = "marina-garmin-key"

/** Everything here is invented. */
class PersonRoutesTest {
    private val marinaSettings =
        PersonSettings(
            dbPath = tempDbPath(),
            trainingPlanCsvPath = null,
            trainingPlanStartDate = LocalDate.of(2026, 8, 3),
            lthr = LthrSetting.NotConfigured,
            maxHr = MaxHrSetting.Configured(Bpm(187)),
            marathonPace = MarathonPaceSetting.Valid(SecondsPerKm(255)),
            garminApiKey = MARINA_KEY,
            garminActivitiesFrom = LocalDate.of(2000, 1, 1),
            buildWeeks = 18,
        )
    private val config = testConfig().copy(marina = marinaSettings)
    private val ricardoRuns = ExposedRunRepository(DatabaseFactory.connect(config.dbPath))
    private val marinaRuns = ExposedRunRepository(DatabaseFactory.connect(marinaSettings.dbPath))
    private val garminRequests = CopyOnWriteArrayList<HttpRequestData>()
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private val perPersonRoutes =
        listOf(
            "/api/dashboard/running",
            "/api/sync",
            "/api/training-plan",
            "/api/training-load",
            "/api/race-day-form",
            "/api/vo2max",
            "/api/vo2max/normalized",
            "/api/long-run-drift",
            "/api/efficiency-factor",
            "/api/marathon-pace",
            "/api/wellness",
            "/api/rings",
            "/api/rings/history?days=30",
            "/api/weekly-summaries",
            "/api/run-weather",
        )

    /** Each key gets its own access token, so a request shows whose key it was made with. */
    private fun app(
        appConfig: app.stravadashboard.config.AppConfig = config,
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        val engine =
            MockEngine { request ->
                garminRequests += request
                when (request.url.encodedPath) {
                    GARMIN_TOKEN_PATH -> {
                        val key = request.headers[HttpHeaders.Authorization]?.removePrefix("ApiKey ")
                        respond("access-$key", HttpStatusCode.OK)
                    }
                    else -> respond("[]", HttpStatusCode.OK, json)
                }
            }
        application { module(appConfig, fixedClock(), TEST_ZONE, garminEngine = engine) }
        block()
    }

    private suspend fun HttpClient.getAs(
        person: String?,
        path: String,
    ): HttpResponse = get(path) { person?.let { header("X-Person", it) } }

    private fun weekKm(body: String): Double =
        Json
            .parseToJsonElement(body)
            .jsonObject["weekComparison"]!!
            .jsonObject["distance"]!!
            .jsonObject["thisWeekKm"]!!
            .jsonPrimitive
            .content
            .toDouble()

    private fun seedDifferentRuns() =
        runBlocking {
            val yesterday = NOW.minus(Duration.ofHours(1))
            ricardoRuns.upsertSummaries(listOf(summary(1, yesterday, distanceMeters = 10_000.0)))
            marinaRuns.upsertSummaries(listOf(summary(1, yesterday, distanceMeters = 4_000.0)))
        }

    @Test
    fun `R3 - no header is ricardo`() {
        seedDifferentRuns()
        app {
            val browser = browser()
            assertEquals(10.0, weekKm(browser.getAs(null, "/api/dashboard/running").bodyAsText()))
            assertEquals(10.0, weekKm(browser.getAs("ricardo", "/api/dashboard/running").bodyAsText()))
            assertEquals(4.0, weekKm(browser.getAs("marina", "/api/dashboard/running").bodyAsText()))
        }
    }

    @Test
    fun `R3 - unknown person is 404`() =
        app {
            val browser = browser()
            for (path in perPersonRoutes) {
                assertEquals(HttpStatusCode.NotFound, browser.getAs("nobody", path).status, path)
            }
        }

    @Test
    fun `R3 - health is shared`() =
        app {
            assertEquals(HttpStatusCode.OK, client.getAs("marina", "/api/health").status)
            assertEquals(HttpStatusCode.OK, client.getAs("nobody", "/api/health").status)
        }

    @Test
    fun `021 R1 - unconfigured Marina gets 404 person_not_configured on every route`() =
        app(testConfig(dbPath = config.dbPath)) {
            val browser = browser()
            for (path in perPersonRoutes) {
                val response = browser.getAs("marina", path)
                assertEquals(HttpStatusCode.NotFound, response.status, path)
                assertEquals(
                    """{"error":"person_not_configured","message":"This person is not set up."}""",
                    response.bodyAsText(),
                    path,
                )
                assertFalse("person_not_configured" in browser.getAs(null, path).bodyAsText(), path)
            }
            assertEquals(HttpStatusCode.OK, browser.getAs("marina", "/api/health").status)
        }

    private suspend fun HttpClient.postSyncAs(person: String): HttpResponse {
        getAs(person, "/api/sync")
        val token = cookies("http://localhost/").first { it.name == XSRF_COOKIE }.value
        return post("/api/sync") {
            header(XSRF_HEADER, token)
            header("X-Person", person)
        }
    }

    private suspend fun waitForGarminCall(key: String): Boolean {
        repeat(300) {
            if (garminCalledWith(key)) return true
            delay(10)
        }
        return false
    }

    private fun garminCalledWith(key: String): Boolean =
        garminRequests.any { it.headers[HttpHeaders.Authorization] == "Bearer access-$key" }

    @Test
    fun `R6 - post starts the other person`() =
        app {
            browser().postSyncAs("ricardo")
            assertTrue(waitForGarminCall(RICARDO_KEY))
            assertTrue(waitForGarminCall(MARINA_KEY))
        }

    @Test
    fun `021 R7 - each person syncs from their own Garmin key and one without a key is not started`() {
        val noKey = config.copy(marina = marinaSettings.copy(garminApiKey = null))
        app(noKey) {
            val browser = browser()
            browser.postSyncAs("ricardo")
            assertTrue(waitForGarminCall(RICARDO_KEY))
            assertTrue("\"notConfigured\"" in browser.getAs("marina", "/api/sync").bodyAsText())
            assertFalse(garminRequests.any { it.headers[HttpHeaders.Authorization] == "ApiKey $MARINA_KEY" })
        }
    }

    @Test
    fun `R7 - marina build has 18 weeks`() =
        app {
            val root =
                Json.parseToJsonElement(browser().getAs("marina", "/api/marathon-pace").bodyAsText()).jsonObject
            assertEquals("2026-08-03", root["buildStart"]?.jsonPrimitive?.content)
            assertEquals("2026-12-06", root["buildEnd"]?.jsonPrimitive?.content)
            assertEquals(18, root["weeks"]?.jsonArray?.size)
        }

    @Test
    fun `R7 - vo2max uses marina max hr`() =
        app {
            val browser = browser()
            for (path in listOf("/api/vo2max", "/api/vo2max/normalized")) {
                val root = Json.parseToJsonElement(browser.getAs("marina", path).bodyAsText()).jsonObject
                val maxHr = root["maxHr"]!!.jsonObject["bpm"]?.jsonPrimitive?.int
                assertEquals(187, maxHr, path)
            }
        }

    @Test
    fun `R10 and R11 - her unset settings answer not configured`() {
        val unset =
            config.copy(
                marina = marinaSettings.copy(marathonPace = MarathonPaceSetting.NotConfigured, garminApiKey = null),
            )
        app(unset) {
            val browser = browser()
            val pace = browser.getAs("marina", "/api/marathon-pace")
            assertEquals(HttpStatusCode.NotFound, pace.status)
            assertTrue("marathon_pace_not_configured" in pace.bodyAsText())
            val wellness = browser.getAs("marina", "/api/wellness")
            assertEquals(HttpStatusCode.NotFound, wellness.status)
            assertTrue("wellness_not_configured" in wellness.bodyAsText())
            assertEquals(HttpStatusCode.OK, browser.getAs("marina", "/api/dashboard/running").status)
        }
    }

    @Test
    fun `R18 - header makes no difference for ricardo`() {
        seedDifferentRuns()
        app {
            val browser = browser()
            val plain = browser.getAs(null, "/api/dashboard/running").bodyAsText()
            val named = browser.getAs("ricardo", "/api/dashboard/running").bodyAsText()
            assertEquals(plain, named)
            assertFalse("access" in plain)
        }
    }

    @Test
    fun `020 R7 - rings follow X-Person`() {
        val today = NOW.atZone(TEST_ZONE).toLocalDate()
        runBlocking {
            ExposedRingDayRepository(DatabaseFactory.connect(config.dbPath)).upsert(today, RingDay(null, 12.0), NOW)
            ExposedRingDayRepository(
                DatabaseFactory.connect(marinaSettings.dbPath),
            ).upsert(today, RingDay(null, 6.5), NOW)
        }
        app {
            val browser = browser()
            val strain = { person: String ->
                runBlocking {
                    Json
                        .parseToJsonElement(browser.getAs(person, "/api/rings").bodyAsText())
                        .jsonObject["strain"]!!
                        .jsonObject["value"]!!
                        .jsonPrimitive
                        .content
                }
            }
            assertEquals("12.0", strain("ricardo"))
            assertEquals("6.5", strain("marina"))
        }
    }
}
