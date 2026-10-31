package app.stravadashboard.api

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
import app.stravadashboard.testsupport.testConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
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
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoutesTest {
    private val config = testConfig()
    private val database = DatabaseFactory.connect(config.dbPath)
    private val tokens = ExposedTokenRepository(database)
    private val stravaRequests = mutableListOf<HttpRequestData>()
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private fun resource(name: String): String = requireNotNull(javaClass.getResource("/strava/$name")).readText()

    private fun MockRequestHandleScope.tokenGrant(athleteId: Long = 7): HttpResponseData =
        respond(
            """{"access_token":"secret-access","refresh_token":"secret-refresh","expires_at":${NOW.epochSecond + 21600},
               "athlete":{"id":$athleteId,"firstname":"Invented"}}""",
            HttpStatusCode.OK,
            json,
        )

    private fun MockRequestHandleScope.happyStrava(request: HttpRequestData): HttpResponseData =
        when (request.url.encodedPath) {
            "/oauth/token" -> tokenGrant()
            "/api/v3/athlete/activities" ->
                if (request.url.parameters["page"] ==
                    "1"
                ) {
                    respond(resource("activities.json"), HttpStatusCode.OK, json)
                } else {
                    respond("[]", HttpStatusCode.OK, json)
                }
            "/api/v3/activities/111/streams" -> respond(resource("streams.json"), HttpStatusCode.OK, json)
            else -> respond(resource("activity-detail.json"), HttpStatusCode.OK, json)
        }

    private fun app(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { happyStrava(it) },
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        val engine =
            MockEngine { request ->
                stravaRequests += request
                handler(request)
            }
        application { module(config, fixedClock(), TEST_ZONE, engine) }
        block()
    }

    private fun ApplicationTestBuilder.browser(): HttpClient =
        createClient {
            install(HttpCookies)
            followRedirects = false
        }

    private suspend fun HttpClient.login(): String {
        val response = get("/auth/strava/login")
        return requireNotNull(Url(requireNotNull(response.headers[HttpHeaders.Location])).parameters["state"])
    }

    private suspend fun HttpClient.callback(query: String): HttpResponse = get("/auth/strava/callback?$query")

    private suspend fun HttpClient.connect(): HttpResponse =
        callback("state=${login()}&code=invented-code&scope=read,activity:read_all")

    @Test
    fun `R2 - dashboard without session returns 401 not_connected`() =
        app {
            val response = client.get("/api/dashboard/running")
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertEquals(
                """{"error":"not_connected","message":"Connect your Strava account."}""",
                response.bodyAsText(),
            )
        }

    @Test
    fun `R2 - login redirects to Strava with activity read_all scope and state cookie`() =
        app {
            val response = browser().get("/auth/strava/login")

            assertEquals(HttpStatusCode.Found, response.status)
            val location = Url(requireNotNull(response.headers[HttpHeaders.Location]))
            assertEquals("www.strava.com", location.host)
            assertEquals("/oauth/authorize", location.encodedPath)
            assertEquals("12345", location.parameters["client_id"])
            assertEquals(config.stravaRedirectUri, location.parameters["redirect_uri"])
            assertEquals("code", location.parameters["response_type"])
            assertEquals("auto", location.parameters["approval_prompt"])
            assertEquals("activity:read_all", location.parameters["scope"])
            assertTrue(requireNotNull(location.parameters["state"]).length >= 43)
            val cookie =
                requireNotNull(
                    response.headers.getAll(HttpHeaders.SetCookie),
                ).single { it.startsWith("sd_oauth_state=") }
            assertTrue("HttpOnly" in cookie)
            assertTrue("SameSite=Lax" in cookie)
            assertTrue("Max-Age=600" in cookie)
        }

    @Test
    fun `R2 - callback with mismatched state redirects with state_mismatch`() =
        app {
            val browser = browser()
            browser.login()
            val response = browser.callback("state=wrong&code=invented-code&scope=activity:read_all")
            assertEquals("/connect?error=state_mismatch", response.headers[HttpHeaders.Location])
            assertTrue(stravaRequests.isEmpty())
        }

    @Test
    fun `R2 - callback with access_denied redirects with access_denied`() =
        app {
            val browser = browser()
            val response = browser.callback("state=${browser.login()}&error=access_denied")
            assertEquals("/connect?error=access_denied", response.headers[HttpHeaders.Location])
        }

    @Test
    fun `R2 - callback without activity read_all scope stores nothing`() =
        app {
            val browser = browser()
            val response = browser.callback("state=${browser.login()}&code=invented-code&scope=read")
            assertEquals("/connect?error=scope_missing", response.headers[HttpHeaders.Location])
            assertTrue(stravaRequests.isEmpty())
            assertNull(runBlocking { tokens.find() })
        }

    @Test
    fun `R2 - successful callback stores tokens sets session and leaks no token`() =
        app {
            val response = browser().connect()

            assertEquals(HttpStatusCode.Found, response.status)
            assertEquals("/", response.headers[HttpHeaders.Location])
            val stored = assertNotNull(runBlocking { tokens.find() })
            assertEquals(7, stored.athleteId)
            assertEquals("secret-access", stored.accessToken)
            assertEquals("read,activity:read_all", stored.scope)
            val cookies = requireNotNull(response.headers.getAll(HttpHeaders.SetCookie))
            val session = cookies.single { it.startsWith("sd_session=") }
            assertTrue("HttpOnly" in session && "SameSite=Lax" in session)
            assertFalse("Secure" in session)
            assertTrue(
                cookies.any {
                    it.startsWith("sd_oauth_state=;") ||
                        it.startsWith("sd_oauth_state=\"\"") ||
                        "Max-Age=0" in it
                },
            )
            val everything = response.headers.entries().joinToString() + response.bodyAsText()
            assertFalse("secret-access" in everything || "secret-refresh" in everything)
        }

    @Test
    fun `R2 - failed token exchange redirects with token_exchange_failed`() =
        app(handler = { respond("", HttpStatusCode.InternalServerError) }) {
            val response = browser().connect()
            assertEquals("/connect?error=token_exchange_failed", response.headers[HttpHeaders.Location])
            assertNull(runBlocking { tokens.find() })
        }

    @Test
    fun `R2 - connecting a different athlete wipes previous data`() {
        val runs = ExposedRunRepository(database)
        runBlocking {
            tokens.save(StoredTokens(99, "old-access", "old-refresh", NOW, "activity:read_all"))
            runs.upsertSummaries(listOf(summary(500, NOW.minus(Duration.ofDays(1)))))
        }
        app {
            browser().connect()
            assertEquals(7, runBlocking { tokens.find() }?.athleteId)
            assertTrue(runBlocking { runs.findAll() }.isEmpty())
        }
    }

    @Test
    fun `R2 - a different athlete connecting after a revoke gets a clean store`() {
        val runs = ExposedRunRepository(database)
        val syncState = ExposedSyncStateRepository(database)
        runBlocking {
            // Athlete 99's tokens were revoked and deleted; their runs and import cursor are still stored.
            syncState.save(SyncState(listCursor = NOW, lastSuccessfulSyncAt = NOW, ownerAthleteId = 99))
            runs.upsertSummaries(listOf(summary(500, NOW.minus(Duration.ofDays(1)))))
        }
        app {
            browser().connect()

            assertTrue(runBlocking { runs.findAll() }.isEmpty())
            val state = runBlocking { syncState.get() }
            assertEquals(7, state.ownerAthleteId)
            assertNull(state.listCursor)
        }
    }

    @Test
    fun `R2 - malformed token response redirects with token_exchange_failed`() =
        app(handler = { respond("""{"access_token":"x","expires_at":"soon"}""", HttpStatusCode.OK, json) }) {
            val response = browser().connect()
            assertEquals("/connect?error=token_exchange_failed", response.headers[HttpHeaders.Location])
            assertNull(runBlocking { tokens.find() })
        }

    private suspend fun ApplicationTestBuilder.connectedDashboardBody(): String {
        val browser = browser()
        browser.connect()
        val response = browser.get("/api/dashboard/running")
        assertEquals(HttpStatusCode.OK, response.status)
        return response.bodyAsText()
    }

    private fun JsonObject.firstOf(
        section: String,
        list: String,
    ): JsonObject = obj(section).arr(list).first().jsonObject

    @Test
    fun `R2 - dashboard JSON matches contract`() =
        app {
            val root = Json.parseToJsonElement(connectedDashboardBody()).jsonObject

            assertEquals(
                setOf("sync", "weeklyDistance", "heartRateTrend", "workoutPaceComparison", "weekComparison"),
                root.keys,
            )
            assertEquals(setOf("status", "lastSyncedAt", "retryAfter", "runsPendingDetail"), root.obj("sync").keys)
            assertEquals(9, root.obj("weeklyDistance").arr("weeks").size)
            assertEquals(
                setOf("weekStart", "weekEnd", "label", "distanceKm", "runCount", "isCurrentWeek"),
                root.firstOf("weeklyDistance", "weeks").keys,
            )
            assertEquals(
                setOf(
                    "typeKey",
                    "label",
                    "kind",
                    "runCount",
                    "runsWithoutHeartRate",
                    "hasEnoughData",
                    "points",
                    "trend",
                ),
                root.firstOf("heartRateTrend", "types").keys,
            )
            assertEquals(
                setOf(
                    "typeKey",
                    "label",
                    "current",
                    "previous",
                    "changeSecondsPerKm",
                    "runsWithoutPace",
                    "hasEnoughData",
                ),
                root.firstOf("workoutPaceComparison", "types").keys,
            )
        }

    @Test
    fun `007 R2 - dashboard JSON includes the week comparison block`() =
        app {
            val week = Json.parseToJsonElement(connectedDashboardBody()).jsonObject.obj("weekComparison")
            assertEquals(setOf("thisWeek", "lastWeek", "distance", "easyHeartRate", "workoutPace"), week.keys)
            assertEquals(setOf("thisWeekKm", "lastWeekKm", "differenceKm"), week.obj("distance").keys)
            assertEquals(setOf("thisWeek", "lastWeek", "changeBpm", "missingIn"), week.obj("easyHeartRate").keys)
            assertEquals(
                setOf("thisWeek", "lastWeek", "changeSecondsPerKm", "missingIn"),
                week.obj("workoutPace").keys,
            )
        }

    @Test
    fun `003 R3 - HR points include rising boolean`() =
        app {
            val root = Json.parseToJsonElement(connectedDashboardBody()).jsonObject
            val point =
                root
                    .firstOf("heartRateTrend", "types")
                    .arr("points")
                    .first()
                    .jsonObject
            assertEquals(setOf("date", "averageHeartRateBpm", "distanceKm", "treadmill", "rising"), point.keys)
            assertEquals(JsonPrimitive(false), point["rising"])
        }

    @Test
    fun `R2 - dashboard JSON has no sensitive fields`() =
        app {
            val body = connectedDashboardBody()
            listOf(
                "secret-access",
                "secret-refresh",
                "Invented",
                "Treadmill notes",
                "athlete",
                "111",
                "latlng",
                "polyline",
                "3.917",
            ).forEach { assertFalse(it in body, "dashboard JSON leaked '$it'") }
        }

    @Test
    fun `R3 - failed refresh deletes tokens and returns strava_reauth_required`() {
        var connected = false
        app(handler = { request ->
            when {
                request.url.encodedPath == "/oauth/token" && !connected -> tokenGrant().also { connected = true }
                request.url.encodedPath == "/oauth/token" -> respond("", HttpStatusCode.BadRequest)
                else -> respond("", HttpStatusCode.Unauthorized)
            }
        }) {
            val browser = browser()
            browser.connect()

            val response = browser.get("/api/dashboard/running")

            assertEquals(HttpStatusCode.Unauthorized, response.status)
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            assertEquals("\"strava_reauth_required\"", body["error"].toString())
            assertEquals("\"Your Strava connection expired. Please connect again.\"", body["message"].toString())
            assertNull(runBlocking { tokens.find() })
        }
    }

    private fun JsonObject.obj(key: String): JsonObject = requireNotNull(this[key]).jsonObject

    private fun JsonObject.arr(key: String) = requireNotNull(this[key]).jsonArray
}
