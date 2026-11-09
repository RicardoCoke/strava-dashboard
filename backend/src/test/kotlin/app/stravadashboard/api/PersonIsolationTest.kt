package app.stravadashboard.api

import app.stravadashboard.config.AppConfig
import app.stravadashboard.config.LthrSetting
import app.stravadashboard.config.MarathonPaceSetting
import app.stravadashboard.config.MaxHrSetting
import app.stravadashboard.config.PersonSettings
import app.stravadashboard.config.WeatherLocation
import app.stravadashboard.domain.model.ActivityId
import app.stravadashboard.domain.model.Bpm
import app.stravadashboard.domain.ports.StoredTokens
import app.stravadashboard.domain.ports.SyncState
import app.stravadashboard.module
import app.stravadashboard.persistence.DatabaseFactory
import app.stravadashboard.persistence.ExposedRunRepository
import app.stravadashboard.persistence.ExposedSyncStateRepository
import app.stravadashboard.persistence.ExposedTokenRepository
import app.stravadashboard.testsupport.NOW
import app.stravadashboard.testsupport.TEST_ZONE
import app.stravadashboard.testsupport.fixedClock
import app.stravadashboard.testsupport.summary
import app.stravadashboard.testsupport.tempDbPath
import app.stravadashboard.testsupport.testConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.cookies.cookies
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val MARINA_CLIENT = "2222"
private const val RICARDO_CLIENT = "12345"

/** That one person's data, settings and failures stay theirs (feature 019). Everything here is invented. */
class PersonIsolationTest {
    private val marina =
        PersonSettings(
            stravaClientId = MARINA_CLIENT,
            stravaClientSecret = "marina-secret",
            dbPath = tempDbPath(),
            trainingPlanCsvPath = null,
            trainingPlanStartDate = LocalDate.of(2026, 8, 3),
            lthr = LthrSetting.Valid(Bpm(163)),
            maxHr = MaxHrSetting.Default,
            marathonPace = MarathonPaceSetting.NotConfigured,
            garminApiKey = null,
            buildWeeks = 18,
        )
    private val config = testConfig().copy(lthr = LthrSetting.Valid(Bpm(175)), marina = marina)
    private val ricardoDb = DatabaseFactory.connect(config.dbPath)
    private val marinaDb = DatabaseFactory.connect(marina.dbPath)
    private val ricardoTokens = ExposedTokenRepository(ricardoDb)
    private val marinaTokens = ExposedTokenRepository(marinaDb)
    private val ricardoRuns = ExposedRunRepository(ricardoDb)
    private val marinaRuns = ExposedRunRepository(marinaDb)

    private val stravaRequests = CopyOnWriteArrayList<HttpRequestData>()
    private val garminRequests = CopyOnWriteArrayList<HttpRequestData>()
    private val weatherRequests = CopyOnWriteArrayList<HttpRequestData>()
    private var rejectRefreshFor: String? = null
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

    private fun form(request: HttpRequestData) = (request.body as FormDataContent).formData

    private fun app(
        appConfig: AppConfig = config,
        clock: Clock = fixedClock(),
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        val strava =
            MockEngine { request ->
                stravaRequests += request
                when {
                    request.url.encodedPath != "/oauth/token" -> respond("[]", HttpStatusCode.OK, json)
                    form(request)["client_id"] == rejectRefreshFor && form(request)["grant_type"] == "refresh_token" ->
                        respond("", HttpStatusCode.BadRequest)
                    else -> {
                        val client = form(request)["client_id"]
                        val athlete = if (client == MARINA_CLIENT) 8 else 7
                        respond(
                            """{"access_token":"access-$client","refresh_token":"refresh-$client",
                               "expires_at":${NOW.epochSecond + 21600},"athlete":{"id":$athlete}}""",
                            HttpStatusCode.OK,
                            json,
                        )
                    }
                }
            }
        val garmin =
            MockEngine { request ->
                garminRequests += request
                respond("invented-token", HttpStatusCode.OK, json)
            }
        val weather =
            MockEngine { request ->
                weatherRequests += request
                respond("""{"hourly":{"time":[],"precipitation":[],"cloud_cover":[]}}""", HttpStatusCode.OK, json)
            }
        application {
            module(appConfig, clock, TEST_ZONE, strava, garminEngine = garmin, weatherEngine = weather)
        }
        block()
    }

    private fun ApplicationTestBuilder.browser(): HttpClient =
        createClient {
            install(HttpCookies)
            followRedirects = false
        }

    private suspend fun HttpClient.connect(person: String = "ricardo") {
        val login = get("/auth/strava/login?person=$person")
        val state = Url(requireNotNull(login.headers[HttpHeaders.Location])).parameters["state"]
        get("/auth/strava/callback?state=$state&code=invented&scope=activity:read_all")
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

    private fun stravaCalledWith(bearer: String): Boolean =
        stravaRequests.any { it.headers[HttpHeaders.Authorization] == "Bearer $bearer" }

    private fun tokens(
        athlete: Long,
        access: String,
        refresh: String = "$access-refresh",
        expires: Instant = NOW.plus(Duration.ofDays(1)),
    ) = StoredTokens(athlete, access, refresh, expires, "activity:read_all")

    private fun jdbc(
        path: String,
        block: (java.sql.Statement) -> Unit,
    ) = DriverManager.getConnection("jdbc:sqlite:$path").use { connection ->
        connection.createStatement().use(block)
    }

    /** The schema and every row of the tables that hold data. */
    private fun snapshot(path: String): List<String> {
        val out = mutableListOf<String>()
        jdbc(path) { statement ->
            statement.executeQuery("SELECT sql FROM sqlite_master WHERE sql IS NOT NULL ORDER BY sql").use {
                while (it.next()) out += it.getString(1)
            }
            for (table in listOf("strava_tokens", "runs", "sync_state")) {
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
            ricardoTokens.save(tokens(7, "ricardo-access"))
            ricardoRuns.upsertSummaries(listOf(summary(500, NOW.minus(Duration.ofDays(1)))))
            ExposedSyncStateRepository(ricardoDb).save(SyncState(listCursor = NOW, ownerAthleteId = 7))
        }
        val before = snapshot(config.dbPath)
        app { assertEquals(HttpStatusCode.OK, client.get("/api/health").status) }
        assertEquals(before, snapshot(config.dbPath))
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
            ricardoTokens.save(tokens(7, "ricardo-access"))
            marinaTokens.save(tokens(8, "marina-access"))
            ricardoRuns.upsertSummaries(listOf(summary(1, start, distanceMeters = 10_000.0)))
            // Five runs is the minimum for a weekly summary, so only Marina has one.
            marinaRuns.upsertSummaries(
                (1L..5L).map { summary(it, NOW.minus(Duration.ofHours(it)), distanceMeters = 4_000.0) },
            )
            ExposedSyncStateRepository(ricardoDb).save(SyncState(lastSuccessfulSyncAt = NOW, ownerAthleteId = 7))
            ExposedSyncStateRepository(marinaDb).save(
                SyncState(lastSuccessfulSyncAt = NOW.minus(Duration.ofDays(2)), ownerAthleteId = 8),
            )
        }
        app {
            val browser = browser()
            browser.connect("marina")
            // Connecting replaced nothing of Ricardo's, and each read below answers from its own file.
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
    fun `R3 - post sync follows the person`() {
        runBlocking { ricardoTokens.save(tokens(7, "ricardo-access")) }
        app {
            val browser = browser()
            browser.connect()
            val response = browser.postSyncAs("marina")
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertTrue("not_connected" in response.bodyAsText())
        }
    }

    @Test
    fun `R4 - unsigned cookie is 401 for both`() {
        runBlocking {
            ricardoTokens.save(tokens(7, "ricardo-access"))
            marinaTokens.save(tokens(8, "marina-access"))
        }
        app {
            for (person in listOf("ricardo", "marina")) {
                val response =
                    client.get("/api/dashboard/running") {
                        header("X-Person", person)
                        header(HttpHeaders.Cookie, "sd_session=%7B%22athleteId%22%3A7%7D")
                    }
                assertEquals(HttpStatusCode.Unauthorized, response.status, person)
            }
        }
    }

    @Test
    fun `R4 - revoke affects one person`() {
        runBlocking { marinaTokens.save(tokens(8, "marina-access", expires = NOW.minus(Duration.ofDays(1)))) }
        rejectRefreshFor = MARINA_CLIENT
        app {
            val browser = browser()
            browser.connect()
            browser.postSyncAs("marina")

            val revoked =
                waitUntil { "strava_reauth_required" in browser.getAs("marina", "/api/sync").bodyAsText() }

            assertTrue(revoked)
            assertEquals(HttpStatusCode.Unauthorized, browser.getAs("marina", "/api/sync").status)
            assertNull(runBlocking { marinaTokens.find() })
            assertNotNull(runBlocking { ricardoTokens.find() })
            assertEquals(HttpStatusCode.OK, browser.getAs("ricardo", "/api/dashboard/running").status)
        }
    }

    @Test
    fun `R6 - post as marina starts ricardo too`() {
        runBlocking { marinaTokens.save(tokens(8, "marina-access")) }
        app {
            val browser = browser()
            browser.connect()
            assertEquals(HttpStatusCode.OK, browser.postSyncAs("marina").status)
            assertTrue(waitUntil { stravaCalledWith("marina-access") })
            assertTrue(waitUntil { stravaCalledWith("access-$RICARDO_CLIENT") })
        }
    }

    @Test
    fun `R6 - unconnected person not started`() =
        app {
            val browser = browser()
            browser.connect()
            browser.postSyncAs("ricardo")
            assertTrue(waitUntil { stravaCalledWith("access-$RICARDO_CLIENT") })

            assertFalse(stravaCalledWith("access-$MARINA_CLIENT"))
            assertTrue("not_connected" in browser.getAs("marina", "/api/sync").bodyAsText())
        }

    @Test
    fun `R6 - other failure keeps response`() =
        app {
            val browser = browser()
            browser.connect()
            // Reading Marina's tokens now throws, as it would with a broken database file.
            jdbc(marina.dbPath) { it.execute("DROP TABLE strava_tokens") }

            val response = browser.postSyncAs("ricardo")

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue("\"status\"" in response.bodyAsText())
            assertTrue(waitUntil { stravaCalledWith("access-$RICARDO_CLIENT") })
        }

    @Test
    fun `R6 - budgets are separate`() {
        runBlocking {
            marinaTokens.save(tokens(8, "marina-access"))
            ExposedSyncStateRepository(marinaDb).save(
                SyncState(rateLimitedUntil = NOW.plus(Duration.ofHours(1)), ownerAthleteId = 8),
            )
        }
        app {
            val browser = browser()
            browser.connect()
            browser.postSyncAs("ricardo")

            assertTrue(waitUntil { stravaCalledWith("access-$RICARDO_CLIENT") })
            assertFalse(stravaCalledWith("marina-access"))
        }
    }

    @Test
    fun `R6 - refresh uses own credentials`() =
        app {
            val browser = browser()
            browser.connect()
            val expired = NOW.minus(Duration.ofDays(1))
            runBlocking {
                ricardoTokens.save(tokens(7, "old-r", refresh = "refresh-r", expires = expired))
                marinaTokens.save(tokens(8, "old-m", refresh = "refresh-m", expires = expired))
            }

            browser.postSyncAs("ricardo")

            fun refreshOf(token: String) =
                stravaRequests
                    .filter { it.url.encodedPath == "/oauth/token" }
                    .map(::form)
                    .firstOrNull { it["refresh_token"] == token }
            assertTrue(waitUntil { refreshOf("refresh-r") != null && refreshOf("refresh-m") != null })
            assertEquals(RICARDO_CLIENT, refreshOf("refresh-r")?.get("client_id"))
            assertEquals("test-client-secret", refreshOf("refresh-r")?.get("client_secret"))
            assertEquals(MARINA_CLIENT, refreshOf("refresh-m")?.get("client_id"))
            assertEquals("marina-secret", refreshOf("refresh-m")?.get("client_secret"))
        }

    @Test
    fun `R7 - load uses marina lthr`() {
        runBlocking { marinaTokens.save(tokens(8, "marina-access")) }
        app {
            val browser = browser()
            browser.connect()
            assertTrue("\"lthrBpm\":163" in browser.getAs("marina", "/api/training-load").bodyAsText())
            assertTrue("\"lthrBpm\":175" in browser.getAs("ricardo", "/api/training-load").bodyAsText())
        }
    }

    @Test
    fun `R10 - other cards still load without her goal pace`() {
        runBlocking { marinaTokens.save(tokens(8, "marina-access")) }
        app {
            val browser = browser()
            browser.connect()
            for (path in routes) {
                val status = browser.getAs("marina", path).status
                assertNotEquals(HttpStatusCode.Unauthorized, status, path)
                assertNotEquals(HttpStatusCode.InternalServerError, status, path)
            }
        }
    }

    private fun garminKeys() = garminRequests.mapNotNull { it.headers[HttpHeaders.Authorization] }

    @Test
    fun `R11 - each key is used for its own person`() {
        val both =
            config.copy(garminApiKey = "ricardo-garmin", marina = marina.copy(garminApiKey = "marina-garmin"))
        runBlocking { marinaTokens.save(tokens(8, "marina-access")) }
        app(both) {
            val browser = browser()
            browser.connect()
            browser.postSyncAs("marina")
            assertTrue(waitUntil { "ApiKey marina-garmin" in garminKeys() && "ApiKey ricardo-garmin" in garminKeys() })
            assertEquals(
                setOf("ApiKey marina-garmin", "ApiKey ricardo-garmin"),
                garminKeys().filter { "ApiKey" in it }.toSet(),
            )
        }
    }

    @Test
    fun `R11 - no garmin call without her key`() {
        val onlyRicardo = config.copy(garminApiKey = "ricardo-garmin")
        app(onlyRicardo) {
            val browser = browser()
            browser.connect("marina")
            browser.postSyncAs("marina")
            assertTrue(waitUntil { stravaCalledWith("access-$MARINA_CLIENT") })
            assertTrue(waitUntil { "\"syncing\"" !in browser.getAs("marina", "/api/sync").bodyAsText() })
            assertTrue(garminRequests.isEmpty())
        }
    }

    @Test
    fun `R12 - same home for both`() {
        val withHome = config.copy(weatherHome = WeatherLocation(10.5, 20.5))
        runBlocking { marinaTokens.save(tokens(8, "marina-access")) }
        app(withHome) {
            val browser = browser()
            browser.connect()
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
        runBlocking { marinaTokens.save(tokens(8, "marina-access")) }
        app(withPlan, Clock.fixed(Instant.parse("2026-12-05T10:00:00Z"), TEST_ZONE)) {
            val browser = browser()
            browser.connect()
            browser.getAs("marina", "/api/run-weather")
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
        runBlocking { marinaTokens.save(tokens(8, "marina-access")) }
        app(withPlans) {
            val browser = browser()
            browser.connect()
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
            browser.connect()
            for (path in routes) {
                val plain = browser.get(path)
                val named = browser.getAs("ricardo", path)
                assertEquals(plain.status, named.status, path)
                assertEquals(plain.bodyAsText(), named.bodyAsText(), path)
            }
        }
    }
}
