package app.stravadashboard.api

import app.stravadashboard.config.AppConfig
import app.stravadashboard.config.LthrSetting
import app.stravadashboard.config.MarathonPaceSetting
import app.stravadashboard.config.MaxHrSetting
import app.stravadashboard.config.PersonSettings
import app.stravadashboard.config.WeatherLocation
import app.stravadashboard.domain.model.ActivityId
import app.stravadashboard.domain.model.Bpm
import app.stravadashboard.domain.ports.SyncState
import app.stravadashboard.module
import app.stravadashboard.persistence.DatabaseFactory
import app.stravadashboard.persistence.ExposedRunRepository
import app.stravadashboard.persistence.ExposedSyncStateRepository
import app.stravadashboard.testsupport.GARMIN_TOKEN_PATH
import app.stravadashboard.testsupport.NOW
import app.stravadashboard.testsupport.TEST_ZONE
import app.stravadashboard.testsupport.browser
import app.stravadashboard.testsupport.fixedClock
import app.stravadashboard.testsupport.summary
import app.stravadashboard.testsupport.syncAndWait
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
import java.nio.file.Files
import java.sql.DriverManager
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private const val RICARDO_KEY = "invented-garmin-key"
private const val MARINA_KEY = "marina-garmin"

/** That one person's data, settings and failures stay theirs (feature 019). Everything here is invented. */
class PersonIsolationTest {
    private val marina =
        PersonSettings(
            dbPath = tempDbPath(),
            trainingPlanCsvPath = null,
            trainingPlanStartDate = LocalDate.of(2026, 8, 3),
            lthr = LthrSetting.Valid(Bpm(163)),
            maxHr = MaxHrSetting.Default,
            marathonPace = MarathonPaceSetting.NotConfigured,
            garminApiKey = MARINA_KEY,
            garminActivitiesFrom = LocalDate.of(2000, 1, 1),
            buildWeeks = 18,
        )
    private val config = testConfig().copy(lthr = LthrSetting.Valid(Bpm(175)), marina = marina)
    private val ricardoDb = DatabaseFactory.connect(config.dbPath)
    private val marinaDb = DatabaseFactory.connect(marina.dbPath)
    private val ricardoRuns = ExposedRunRepository(ricardoDb)
    private val marinaRuns = ExposedRunRepository(marinaDb)

    private val garminRequests = CopyOnWriteArrayList<HttpRequestData>()
    private val weatherRequests = CopyOnWriteArrayList<HttpRequestData>()
    private var rejectKey: String? = null
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private val routes =
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
            "/api/weekly-summaries",
            "/api/run-weather",
        )

    /** Each key gets its own access token, so every data call shows whose key it was made with. */
    private fun app(
        appConfig: AppConfig = config,
        clock: Clock = fixedClock(),
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        val garmin =
            MockEngine { request ->
                garminRequests += request
                val key = request.headers[HttpHeaders.Authorization]?.removePrefix("ApiKey ")
                when {
                    request.url.encodedPath != GARMIN_TOKEN_PATH -> respond("[]", HttpStatusCode.OK, json)
                    key == rejectKey -> respond("", HttpStatusCode.Unauthorized)
                    else -> respond("access-$key", HttpStatusCode.OK)
                }
            }
        val weather =
            MockEngine { request ->
                weatherRequests += request
                respond("""{"hourly":{"time":[],"precipitation":[],"cloud_cover":[]}}""", HttpStatusCode.OK, json)
            }
        application {
            module(appConfig, clock, TEST_ZONE, garminEngine = garmin, weatherEngine = weather, syncAtStartup = false)
        }
        block()
    }

    private suspend fun HttpClient.getAs(
        person: String,
        path: String,
    ): HttpResponse = get(path) { header("X-Person", person) }

    private suspend fun HttpClient.postSyncAs(person: String): HttpResponse {
        getAs(person, "/api/sync")
        val token = cookies("http://localhost/").first { it.name == XSRF_COOKIE }.value
        return post("/api/sync") {
            header(XSRF_HEADER, token)
            header("X-Person", person)
        }
    }

    private suspend fun waitUntil(condition: suspend () -> Boolean): Boolean {
        repeat(300) {
            if (condition()) return true
            delay(10)
        }
        return false
    }

    private fun garminCalledWith(key: String): Boolean =
        garminRequests.any { it.headers[HttpHeaders.Authorization] == "Bearer access-$key" }

    private fun jdbc(
        path: String,
        block: (java.sql.Statement) -> Unit,
    ) = DriverManager.getConnection("jdbc:sqlite:$path").use { connection ->
        connection.createStatement().use(block)
    }

    /** The schema and every row of [tables]. */
    private fun snapshot(
        path: String,
        tables: List<String> = listOf("runs", "sync_state"),
    ): List<String> {
        val out = mutableListOf<String>()
        jdbc(path) { statement ->
            statement.executeQuery("SELECT sql FROM sqlite_master WHERE sql IS NOT NULL ORDER BY sql").use {
                while (it.next()) out += it.getString(1)
            }
            for (table in tables) {
                statement.executeQuery("SELECT * FROM $table").use { rows ->
                    val columns = rows.metaData.columnCount
                    while (rows.next()) out += (1..columns).joinToString { "${rows.getObject(it)}" }
                }
            }
        }
        return out
    }

    @Test
    fun `R2 - existing database file is unchanged at startup`() {
        runBlocking {
            ricardoRuns.upsertSummaries(listOf(summary(500, NOW.minus(Duration.ofDays(1)))))
            ExposedSyncStateRepository(ricardoDb).save(SyncState(listCursor = NOW))
        }
        val before = snapshot(config.dbPath)
        app { assertEquals(HttpStatusCode.OK, client.get("/api/health").status) }
        assertEquals(before, snapshot(config.dbPath))
    }

    @Test
    fun `021 R9 - legacy strava_tokens and owner column are left untouched`() {
        jdbc(config.dbPath) {
            it.execute(
                "CREATE TABLE strava_tokens (id INTEGER PRIMARY KEY, athlete_id BIGINT, access_token TEXT, " +
                    "refresh_token TEXT, expires_at BIGINT, scope TEXT)",
            )
            it.execute("INSERT INTO strava_tokens VALUES (1, 7, 'invented-a', 'invented-r', 1, 'activity:read_all')")
            it.execute("ALTER TABLE sync_state ADD COLUMN owner_athlete_id BIGINT")
            it.execute("INSERT INTO sync_state (id, owner_athlete_id) VALUES (1, 7)")
        }

        fun legacy() =
            mutableListOf<String>().also { out ->
                jdbc(config.dbPath) { statement ->
                    statement.executeQuery("SELECT * FROM strava_tokens").use { rows ->
                        while (rows.next()) out += (1..6).joinToString { "${rows.getObject(it)}" }
                    }
                    statement.executeQuery("SELECT owner_athlete_id FROM sync_state").use { rows ->
                        while (rows.next()) out += "${rows.getObject(1)}"
                    }
                }
            }
        val before = legacy()

        app {
            assertEquals(
                "ok",
                Regex("\"status\":\"(\\w+)\"").find(browser().syncAndWait().bodyAsText())?.groupValues?.get(1),
            )
        }

        assertEquals(listOf("1, 7, invented-a, invented-r, 1, activity:read_all", "7"), before)
        assertEquals(before, legacy())
    }

    @Test
    fun `R2 - same activity id stays separate`() =
        runBlocking {
            val start = NOW.minus(Duration.ofDays(1))
            ricardoRuns.upsertSummaries(listOf(summary(1, start, distanceMeters = 10_000.0)))
            marinaRuns.upsertSummaries(listOf(summary(1, start, distanceMeters = 4_000.0)))

            marinaRuns.upsertSummaries(listOf(summary(1, start, distanceMeters = 5_000.0)))
            assertEquals(
                10_000.0,
                ricardoRuns
                    .findAll()
                    .single()
                    .distance.value,
            )

            marinaRuns.delete(listOf(ActivityId(1)))
            assertTrue(marinaRuns.findAll().isEmpty())
            assertEquals(1, ricardoRuns.findAll().size)
        }

    @Test
    fun `R2 - each person reads only their runs and sync status`() {
        runBlocking {
            val start = NOW.minus(Duration.ofHours(1))
            ricardoRuns.upsertSummaries(listOf(summary(1, start, distanceMeters = 10_000.0)))
            // Five runs is the minimum for a weekly summary, so only Marina has one.
            marinaRuns.upsertSummaries(
                (1L..5L).map { summary(it, NOW.minus(Duration.ofHours(it)), distanceMeters = 4_000.0) },
            )
            ExposedSyncStateRepository(ricardoDb).save(SyncState(lastSuccessfulSyncAt = NOW))
            ExposedSyncStateRepository(marinaDb).save(SyncState(lastSuccessfulSyncAt = NOW.minus(Duration.ofDays(2))))
        }
        app {
            val browser = browser()
            for (path in listOf("/api/dashboard/running", "/api/weekly-summaries", "/api/sync")) {
                assertNotEquals(
                    browser.getAs("ricardo", path).bodyAsText(),
                    browser.getAs("marina", path).bodyAsText(),
                    path,
                )
            }
        }
    }

    @Test
    fun `R3 - post sync follows the person`() =
        app(config.copy(marina = marina.copy(garminApiKey = null))) {
            val response = browser().postSyncAs("marina")
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue("\"notConfigured\"" in response.bodyAsText())
        }

    @Test
    fun `021 R8 - a rejected key affects one person`() {
        rejectKey = MARINA_KEY
        app {
            val browser = browser()
            browser.postSyncAs("marina")

            assertTrue(waitUntil { "\"garminUnavailable\"" in browser.getAs("marina", "/api/sync").bodyAsText() })
            assertTrue(waitUntil { garminCalledWith(RICARDO_KEY) })
            assertEquals(HttpStatusCode.OK, browser.getAs("ricardo", "/api/dashboard/running").status)
        }
    }

    @Test
    fun `R6 - post as marina starts ricardo too`() =
        app {
            val browser = browser()
            assertEquals(HttpStatusCode.OK, browser.postSyncAs("marina").status)
            assertTrue(waitUntil { garminCalledWith(MARINA_KEY) })
            assertTrue(waitUntil { garminCalledWith(RICARDO_KEY) })
        }

    @Test
    fun `R6 - person without a key is not started`() =
        app(config.copy(marina = marina.copy(garminApiKey = null))) {
            val browser = browser()
            browser.postSyncAs("ricardo")
            assertTrue(waitUntil { garminCalledWith(RICARDO_KEY) })

            assertFalse(garminRequests.any { it.headers[HttpHeaders.Authorization] == "ApiKey $MARINA_KEY" })
            assertTrue("\"notConfigured\"" in browser.getAs("marina", "/api/sync").bodyAsText())
        }

    @Test
    fun `R6 - other failure keeps response`() =
        app {
            val browser = browser()
            // Reading Marina's sync state now throws, as it would with a broken database file.
            jdbc(marina.dbPath) { it.execute("DROP TABLE sync_state") }

            val response = browser.postSyncAs("ricardo")

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue("\"status\"" in response.bodyAsText())
            assertTrue(waitUntil { garminCalledWith(RICARDO_KEY) })
        }

    @Test
    fun `R6 - budgets are separate`() {
        runBlocking {
            ExposedSyncStateRepository(marinaDb).save(SyncState(rateLimitedUntil = NOW.plus(Duration.ofHours(1))))
        }
        app {
            browser().postSyncAs("ricardo")

            assertTrue(waitUntil { garminCalledWith(RICARDO_KEY) })
            assertFalse(garminCalledWith(MARINA_KEY))
        }
    }

    @Test
    fun `R7 - load uses marina lthr`() =
        app {
            val browser = browser()
            assertTrue("\"lthrBpm\":163" in browser.getAs("marina", "/api/training-load").bodyAsText())
            assertTrue("\"lthrBpm\":175" in browser.getAs("ricardo", "/api/training-load").bodyAsText())
        }

    @Test
    fun `R10 - other cards still load without her goal pace`() =
        app {
            val browser = browser()
            for (path in routes) {
                val status = browser.getAs("marina", path).status
                assertNotEquals(HttpStatusCode.Unauthorized, status, path)
                assertNotEquals(HttpStatusCode.InternalServerError, status, path)
            }
        }

    private fun garminKeys() = garminRequests.mapNotNull { it.headers[HttpHeaders.Authorization] }

    @Test
    fun `R11 - each key is used for its own person`() =
        app {
            browser().postSyncAs("marina")
            assertTrue(waitUntil { "ApiKey $MARINA_KEY" in garminKeys() && "ApiKey $RICARDO_KEY" in garminKeys() })
            assertEquals(
                setOf("ApiKey $MARINA_KEY", "ApiKey $RICARDO_KEY"),
                garminKeys().filter { "ApiKey" in it }.toSet(),
            )
        }

    @Test
    fun `R12 - same home for both`() {
        val withHome = config.copy(weatherHome = WeatherLocation(10.5, 20.5))
        app(withHome) {
            val browser = browser()
            for (person in listOf("ricardo", "marina")) {
                weatherRequests.clear()
                browser.getAs(person, "/api/run-weather")
                assertEquals("10.5", weatherRequests.first().url.parameters["latitude"], person)
                assertEquals("20.5", weatherRequests.first().url.parameters["longitude"], person)
            }
        }
    }

    @Test
    fun `R12 - marathon date rule for both`() {
        val plan = Files.createTempFile("invented-plan", ".csv")
        Files.writeString(
            plan,
            """
            Week 0;;MONDAY;;TUESDAY;;WEDNESDAY;;THURSDAY;;FRIDAY;;SATURDAY;;SUNDAY;
            ;;;;;;;;;;;;;;RACE 42,2K;
            ;;;;;;;;;;;;;;;Goal marathon
            EASY;0,00;;;;;;;;;;;;;;
            SUBT;0,00;;;;;;;;;;;;;;
            VOLUME [km];30,00;;;;;;;;;;;;;42,20;
            """.trimIndent(),
        )
        val withPlan =
            config.copy(
                weatherHome = WeatherLocation(10.5, 20.5),
                marina =
                    marina.copy(
                        trainingPlanCsvPath = plan.toString(),
                        trainingPlanStartDate = LocalDate.of(2026, 11, 30),
                    ),
            )
        app(withPlan, Clock.fixed(Instant.parse("2026-12-05T10:00:00Z"), TEST_ZONE)) {
            browser().getAs("marina", "/api/run-weather")
            assertEquals(
                listOf("10.5", "39.47"),
                weatherRequests.map { it.url.parameters["latitude"] },
            )
        }
    }

    private fun planFile(label: String): String {
        val plan = Files.createTempFile("invented-plan", ".csv")
        Files.writeString(
            plan,
            """
            $label;;MONDAY;;TUESDAY;;WEDNESDAY;;THURSDAY;;FRIDAY;;SATURDAY;;SUNDAY;
            ;;;;;;;;;;;;;;;
            ;;;;;;;;;;;;;;;
            EASY;0,00;;;;;;;;;;;;;;
            SUBT;0,00;;;;;;;;;;;;;;
            VOLUME [km];30,00;;;;;;;;;;;;;;
            """.trimIndent(),
        )
        return plan.toString()
    }

    @Test
    fun `R7 - training plan follows the person`() {
        val withPlans =
            config.copy(
                trainingPlanCsvPath = planFile("WEEK 1"),
                marina = marina.copy(trainingPlanCsvPath = planFile("Week 17")),
            )
        app(withPlans) {
            val browser = browser()
            val ricardoPlan = browser.getAs("ricardo", "/api/training-plan").bodyAsText()
            val marinaPlan = browser.getAs("marina", "/api/training-plan").bodyAsText()
            assertTrue("\"label\":\"WEEK 1\"" in ricardoPlan)
            assertFalse("Week 17" in ricardoPlan)
            assertTrue("\"label\":\"Week 17\"" in marinaPlan)
            assertFalse("WEEK 1\"" in marinaPlan)
        }
    }

    @Test
    fun `R18 - header makes no difference for ricardo on any route`() {
        runBlocking { ricardoRuns.upsertSummaries(listOf(summary(1, NOW.minus(Duration.ofHours(1))))) }
        app {
            val browser = browser()
            for (path in routes) {
                val plain = browser.get(path)
                val named = browser.getAs("ricardo", path)
                assertEquals(plain.status, named.status, path)
                assertEquals(plain.bodyAsText(), named.bodyAsText(), path)
            }
        }
    }
}
