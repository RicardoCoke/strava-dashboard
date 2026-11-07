package app.stravadashboard.api

import app.stravadashboard.auth.OAuthState
import app.stravadashboard.config.MarathonPaceSetting
import app.stravadashboard.config.MaxHrSetting
import app.stravadashboard.config.PersonSettings
import app.stravadashboard.domain.model.Bpm
import app.stravadashboard.domain.model.SecondsPerKm
import app.stravadashboard.domain.ports.StoredTokens
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
import io.ktor.server.sessions.defaultSessionSerializer
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val RICARDO_ATHLETE = 7L
private const val MARINA_ATHLETE = 8L
private const val MARINA_CLIENT = "2222"
private const val RICARDO_CLIENT = "12345"

/** Everything here is invented. */
class PersonRoutesTest {
    private val marinaSettings =
        PersonSettings(
            stravaClientId = MARINA_CLIENT,
            stravaClientSecret = "marina-secret",
            dbPath = tempDbPath(),
            trainingPlanCsvPath = null,
            trainingPlanStartDate = LocalDate.of(2026, 8, 3),
            lthr = app.stravadashboard.config.LthrSetting.NotConfigured,
            maxHr = MaxHrSetting.Configured(Bpm(187)),
            marathonPace = MarathonPaceSetting.Valid(SecondsPerKm(255)),
            garminApiKey = null,
            buildWeeks = 18,
        )
    private val config = testConfig().copy(marina = marinaSettings)
    private val ricardoDb = DatabaseFactory.connect(config.dbPath)
    private val marinaDb = DatabaseFactory.connect(marinaSettings.dbPath)
    private val ricardoTokens = ExposedTokenRepository(ricardoDb)
    private val marinaTokens = ExposedTokenRepository(marinaDb)
    private val ricardoRuns = ExposedRunRepository(ricardoDb)
    private val marinaRuns = ExposedRunRepository(marinaDb)
    private val stravaRequests = CopyOnWriteArrayList<HttpRequestData>()

    /** The athlete Strava says connected. By default each app's own athlete; a test can pretend otherwise. */
    private var athleteOverride: Long? = null
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
            "/api/weekly-summaries",
            "/api/run-weather",
        )

    private fun app(
        appConfig: app.stravadashboard.config.AppConfig = config,
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        val engine =
            MockEngine { request ->
                stravaRequests += request
                if (request.url.encodedPath == "/oauth/token") {
                    val clientId = (request.body as FormDataContent).formData["client_id"]
                    val athlete =
                        athleteOverride ?: if (clientId == MARINA_CLIENT) MARINA_ATHLETE else RICARDO_ATHLETE
                    respond(
                        """{"access_token":"access-$clientId","refresh_token":"refresh-$clientId",
                           "expires_at":${NOW.epochSecond + 21600},"athlete":{"id":$athlete}}""",
                        HttpStatusCode.OK,
                        json,
                    )
                } else {
                    respond("[]", HttpStatusCode.OK, json)
                }
            }
        application { module(appConfig, fixedClock(), TEST_ZONE, engine) }
        block()
    }

    private fun ApplicationTestBuilder.browser(): HttpClient =
        createClient {
            install(HttpCookies)
            followRedirects = false
        }

    private suspend fun HttpClient.getAs(
        person: String?,
        path: String,
    ): HttpResponse = get(path) { person?.let { header("X-Person", it) } }

    private suspend fun HttpClient.connect(person: String? = null): HttpResponse {
        val login = get("/auth/strava/login" + person?.let { "?person=$it" }.orEmpty())
        val state = Url(requireNotNull(login.headers[HttpHeaders.Location])).parameters["state"]
        return get("/auth/strava/callback?state=$state&code=invented&scope=activity:read_all")
    }

    private fun seedTokens(
        ricardo: Boolean = true,
        marina: Boolean = true,
    ) = runBlocking {
        val expires = NOW.plus(Duration.ofDays(1))
        if (ricardo) {
            ricardoTokens.save(StoredTokens(RICARDO_ATHLETE, "ricardo-access", "r", expires, "activity:read_all"))
        }
        if (marina) {
            marinaTokens.save(StoredTokens(MARINA_ATHLETE, "marina-access", "m", expires, "activity:read_all"))
        }
    }

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
        seedTokens()
        seedDifferentRuns()
        app {
            val browser = browser()
            browser.connect()
            assertEquals(10.0, weekKm(browser.getAs(null, "/api/dashboard/running").bodyAsText()))
            assertEquals(10.0, weekKm(browser.getAs("ricardo", "/api/dashboard/running").bodyAsText()))
        }
    }

    @Test
    fun `R3 - every route follows marina`() {
        // Marina has no tokens, so each of her routes says 401: a route mounted only once would say 404 or 200.
        seedTokens(marina = false)
        app {
            val browser = browser()
            browser.connect()
            for (path in perPersonRoutes) {
                assertEquals(HttpStatusCode.Unauthorized, browser.getAs("marina", path).status, path)
                assertTrue(browser.getAs(null, path).status != HttpStatusCode.Unauthorized, path)
            }
        }
    }

    @Test
    fun `R3 - unknown person is 404`() {
        seedTokens()
        app {
            val browser = browser()
            browser.connect()
            for (path in perPersonRoutes) {
                assertEquals(HttpStatusCode.NotFound, browser.getAs("nobody", path).status, path)
            }
        }
    }

    @Test
    fun `R3 - health is shared`() =
        app {
            assertEquals(HttpStatusCode.OK, client.getAs("marina", "/api/health").status)
            assertEquals(HttpStatusCode.OK, client.getAs("nobody", "/api/health").status)
        }

    @Test
    fun `R1 - unconfigured marina returns 401`() {
        seedTokens(marina = false)
        app(testConfig(dbPath = config.dbPath)) {
            val browser = browser()
            browser.connect()
            for (path in perPersonRoutes) {
                val response = browser.getAs("marina", path)
                assertEquals(HttpStatusCode.Unauthorized, response.status, path)
                assertTrue("not_connected" in response.bodyAsText(), path)
            }
            assertEquals(HttpStatusCode.OK, browser.getAs("marina", "/api/health").status)
        }
    }

    @Test
    fun `R4 - ricardo session reads marina`() {
        seedTokens()
        seedDifferentRuns()
        app {
            val browser = browser()
            browser.connect()
            assertEquals(4.0, weekKm(browser.getAs("marina", "/api/dashboard/running").bodyAsText()))
        }
    }

    @Test
    fun `R4 - marina session reads ricardo`() {
        seedTokens()
        seedDifferentRuns()
        app {
            val browser = browser()
            browser.connect("marina")
            assertEquals(10.0, weekKm(browser.getAs("ricardo", "/api/dashboard/running").bodyAsText()))
        }
    }

    @Test
    fun `R4 - no cookie is 401 for both`() {
        seedTokens()
        app {
            assertEquals(HttpStatusCode.Unauthorized, client.getAs("ricardo", "/api/dashboard/running").status)
            assertEquals(HttpStatusCode.Unauthorized, client.getAs("marina", "/api/dashboard/running").status)
        }
    }

    @Test
    fun `R5 - login uses marina client`() =
        app {
            val response = browser().get("/auth/strava/login?person=marina")
            val location = Url(requireNotNull(response.headers[HttpHeaders.Location]))
            assertEquals(MARINA_CLIENT, location.parameters["client_id"])
            assertEquals(config.stravaRedirectUri, location.parameters["redirect_uri"])
            assertEquals("activity:read_all", location.parameters["scope"])
        }

    @Test
    fun `R5 - default login is ricardo`() =
        app {
            val response = browser().get("/auth/strava/login")
            val location = Url(requireNotNull(response.headers[HttpHeaders.Location]))
            assertEquals(RICARDO_CLIENT, location.parameters["client_id"])
        }

    @Test
    fun `R5 - unknown person redirects`() =
        app {
            val response = browser().get("/auth/strava/login?person=nobody")
            assertEquals("/connect?error=unknown_person", response.headers[HttpHeaders.Location])
        }

    @Test
    fun `R5 - unconfigured marina redirects`() =
        app(testConfig()) {
            val response = browser().get("/auth/strava/login?person=marina")
            assertEquals("/connect?error=person_not_configured", response.headers[HttpHeaders.Location])
        }

    @Test
    fun `R5 - callback stores in marina database`() =
        app {
            val response = browser().connect("marina")

            assertEquals("/", response.headers[HttpHeaders.Location])
            assertEquals(MARINA_ATHLETE, runBlocking { marinaTokens.find() }?.athleteId)
            assertEquals("access-$MARINA_CLIENT", runBlocking { marinaTokens.find() }?.accessToken)
            assertNull(runBlocking { ricardoTokens.find() })
            assertEquals(MARINA_ATHLETE, runBlocking { ExposedSyncStateRepository(marinaDb).get() }.ownerAthleteId)
        }

    @Test
    fun `R5 - other person's athlete rejected`() {
        seedTokens(marina = false)
        app {
            athleteOverride = RICARDO_ATHLETE
            val response = browser().connect("marina")

            assertEquals("/connect?error=wrong_athlete", response.headers[HttpHeaders.Location])
            assertNull(runBlocking { marinaTokens.find() })
        }
    }

    @Test
    fun `R5 - marina's athlete rejected as ricardo`() {
        seedTokens(ricardo = false)
        app {
            athleteOverride = MARINA_ATHLETE
            val response = browser().connect()

            assertEquals("/connect?error=wrong_athlete", response.headers[HttpHeaders.Location])
            assertNull(runBlocking { ricardoTokens.find() })
        }
    }

    @Test
    fun `R5 - old state cookie is ricardo`() {
        assertEquals("ricardo", defaultSessionSerializer<OAuthState>().deserialize("""{"value":"abc"}""").person)
    }

    private suspend fun HttpClient.postSyncAs(person: String): HttpResponse {
        getAs(person, "/api/sync")
        val token = cookies("http://localhost/").first { it.name == XSRF_COOKIE }.value
        return post("/api/sync") {
            header(XSRF_HEADER, token)
            header("X-Person", person)
        }
    }

    private suspend fun waitForStravaCall(bearer: String): Boolean {
        repeat(300) {
            if (stravaCalledWith(bearer)) return true
            delay(10)
        }
        return false
    }

    private fun stravaCalledWith(bearer: String): Boolean =
        stravaRequests.any { it.headers[HttpHeaders.Authorization] == "Bearer $bearer" }

    @Test
    fun `R6 - post starts the other person`() {
        seedTokens()
        app {
            val browser = browser()
            browser.connect("marina")
            browser.postSyncAs("ricardo")
            assertTrue(waitForStravaCall("ricardo-access"))
            assertTrue(waitForStravaCall("access-$MARINA_CLIENT"))
        }
    }

    @Test
    fun `R6 - unconnected person not started`() {
        seedTokens(marina = false)
        app {
            val browser = browser()
            browser.connect()
            browser.postSyncAs("ricardo")
            assertTrue(waitForStravaCall("access-$RICARDO_CLIENT"))
            assertEquals(HttpStatusCode.Unauthorized, browser.getAs("marina", "/api/sync").status)
            assertTrue("not_connected" in browser.getAs("marina", "/api/sync").bodyAsText())
        }
    }

    @Test
    fun `R7 - marina build has 18 weeks`() {
        seedTokens()
        app {
            val browser = browser()
            browser.connect()
            val root = Json.parseToJsonElement(browser.getAs("marina", "/api/marathon-pace").bodyAsText()).jsonObject
            assertEquals("2026-08-03", root["buildStart"]?.jsonPrimitive?.content)
            assertEquals("2026-12-06", root["buildEnd"]?.jsonPrimitive?.content)
            assertEquals(18, root["weeks"]?.jsonArray?.size)
        }
    }

    @Test
    fun `R7 - vo2max uses marina max hr`() {
        seedTokens()
        app {
            val browser = browser()
            browser.connect()
            for (path in listOf("/api/vo2max", "/api/vo2max/normalized")) {
                val root = Json.parseToJsonElement(browser.getAs("marina", path).bodyAsText()).jsonObject
                val maxHr = root["maxHr"]!!.jsonObject["bpm"]?.jsonPrimitive?.int
                assertEquals(187, maxHr, path)
            }
        }
    }

    @Test
    fun `R10 and R11 - her unset settings answer not configured`() {
        seedTokens()
        val noGoal = config.copy(marina = marinaSettings.copy(marathonPace = MarathonPaceSetting.NotConfigured))
        app(noGoal) {
            val browser = browser()
            browser.connect()
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
        seedTokens()
        seedDifferentRuns()
        app {
            val browser = browser()
            browser.connect()
            val plain = browser.getAs(null, "/api/dashboard/running").bodyAsText()
            val named = browser.getAs("ricardo", "/api/dashboard/running").bodyAsText()
            assertEquals(plain, named)
            assertFalse("access" in plain)
        }
    }
}
