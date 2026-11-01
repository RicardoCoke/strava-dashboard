package app.stravadashboard.api

import app.stravadashboard.config.AppConfig
import app.stravadashboard.domain.model.ActivityId
import app.stravadashboard.domain.model.HrDriftResult
import app.stravadashboard.domain.model.HrDriftStatus
import app.stravadashboard.domain.model.Seconds
import app.stravadashboard.domain.ports.ActivityDetail
import app.stravadashboard.module
import app.stravadashboard.persistence.DatabaseFactory
import app.stravadashboard.persistence.ExposedRunRepository
import app.stravadashboard.testsupport.TEST_ZONE
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Clock
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// All runs here are invented.
class LongRunDriftRoutesTest {
    // Saturday 2026-10-31, as in the spec examples.
    private val now = Instant.parse("2026-10-31T10:00:00Z")
    private val clock = Clock.fixed(now, TEST_ZONE)
    private val stravaRequests = mutableListOf<HttpRequestData>()
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun MockRequestHandleScope.strava(request: HttpRequestData): HttpResponseData =
        when (request.url.encodedPath) {
            "/oauth/token" ->
                respond(
                    """{"access_token":"a","refresh_token":"r","expires_at":${now.epochSecond + 99_999},
                       "athlete":{"id":7}}""",
                    HttpStatusCode.OK,
                    jsonHeaders,
                )
            "/api/v3/athlete/activities" -> respond("[]", HttpStatusCode.OK, jsonHeaders)
            else -> respond("""{"id":1,"laps":[]}""", HttpStatusCode.OK, jsonHeaders)
        }

    private fun app(
        config: AppConfig,
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        val engine =
            MockEngine { request ->
                stravaRequests += request
                strava(request)
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

    /** Two outdoor long runs: one computed at 3.75% (28.1 km), one still waiting for its drift call. */
    private fun seedRuns(config: AppConfig) =
        runBlocking {
            val runs = ExposedRunRepository(DatabaseFactory.connect(config.dbPath))
            val computed =
                summary(500, Instant.parse("2026-10-25T07:00:00Z"), name = "Long run", distanceMeters = 28_149.0)
            val pending =
                summary(501, Instant.parse("2026-10-18T07:00:00Z"), name = "Long run", distanceMeters = 24_000.0)
            runs.upsertSummaries(listOf(computed, pending))
            listOf(
                computed,
                pending,
            ).forEach { runs.saveDetail(it.id, ActivityDetail(null, emptyList(), Seconds(9000)), now) }
            runs.saveHrDrift(ActivityId(500), HrDriftResult(HrDriftStatus.COMPUTED, 3.75), now)
        }

    private suspend fun HttpResponse.json(): JsonObject = Json.parseToJsonElement(bodyAsText()).jsonObject

    @Test
    fun `R4 - long run drift without session returns not_connected`() =
        app(testConfig()) {
            val response = client.get("/api/long-run-drift")
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertEquals("not_connected", response.json()["error"]?.jsonPrimitive?.content)
        }

    @Test
    fun `R4 - long run drift endpoint makes no Strava calls`() {
        val config = testConfig()
        app(config) {
            val browser = connectedBrowser().also { seedRuns(config) }
            val response = browser.get("/api/long-run-drift")
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(stravaRequests.isEmpty())
        }
    }

    @Test
    fun `R4 - long run drift response has exact keys`() {
        val config = testConfig()
        app(config) {
            val body = connectedBrowser().also { seedRuns(config) }.get("/api/long-run-drift").json()

            assertEquals(
                setOf(
                    "windowStart",
                    "windowEnd",
                    "thresholds",
                    "latest",
                    "lastFourAverage",
                    "points",
                    "excluded",
                    "pendingCount",
                ),
                body.keys,
            )
            assertEquals(
                """{"windowStart":"2026-05-31","windowEnd":"2026-10-31",""" +
                    """"thresholds":{"goodBelowPct":5.0,"poorAbovePct":10.0},""" +
                    """"latest":{"date":"2026-10-25","driftPct":3.8,"zone":"good"},""" +
                    """"lastFourAverage":{"driftPct":3.8,"zone":"good","runCount":1},""" +
                    """"points":[{"date":"2026-10-25","driftPct":3.8,"zone":"good","distanceKm":28.1}],""" +
                    """"excluded":{"total":0,"treadmill":0,"tooShort":0,"missingData":0},"pendingCount":1}""",
                body.toString(),
            )
            assertTrue("Long run" !in body.toString() && "500" !in body.toString())
        }
    }

    @Test
    fun `R5 - thresholds are five and ten`() {
        val config = testConfig()
        app(config) {
            val body = connectedBrowser().get("/api/long-run-drift").json()
            assertEquals("""{"goodBelowPct":5.0,"poorAbovePct":10.0}""", body.getValue("thresholds").toString())
            assertEquals("null", body.getValue("latest").toString())
        }
    }
}
