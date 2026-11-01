package app.stravadashboard.api

import app.stravadashboard.config.AppConfig
import app.stravadashboard.domain.model.ActivityId
import app.stravadashboard.domain.model.Seconds
import app.stravadashboard.domain.ports.ActivityDetail
import app.stravadashboard.module
import app.stravadashboard.persistence.DatabaseFactory
import app.stravadashboard.persistence.ExposedRunRepository
import app.stravadashboard.persistence.ExposedTokenRepository
import app.stravadashboard.testsupport.TEST_ZONE
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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EfficiencyFactorRoutesTest {
    // Saturday 2026-10-31, as in the spec examples.
    private val now = Instant.parse("2026-10-31T10:00:00Z")
    private val clock = Clock.fixed(now, TEST_ZONE)
    private val stravaRequests = mutableListOf<HttpRequestData>()
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun app(
        config: AppConfig,
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        val engine =
            MockEngine { request ->
                stravaRequests += request
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

    /** Three outdoor easy runs ending Fri 2026-10-30: 10 km in 3000 s at HR 125 → EF 1.6; plus one pending detail. */
    private fun seedRuns(config: AppConfig) =
        runBlocking {
            val runs = ExposedRunRepository(DatabaseFactory.connect(config.dbPath))
            val dates = listOf(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 28), LocalDate.of(2026, 10, 30))
            runs.upsertSummaries(
                dates.mapIndexed {
                    i,
                    date,
                    ->
                    summary(500L + i, date.atTime(7, 0).atZone(TEST_ZONE).toInstant(), heartRate = 125.0)
                } +
                    summary(600, Instant.parse("2026-10-31T07:00:00Z")),
            )
            dates.indices.forEach {
                runs.saveDetail(
                    ActivityId(500L + it),
                    ActivityDetail(null, emptyList(), Seconds(3000)),
                    now,
                )
            }
        }

    private suspend fun HttpResponse.json(): JsonObject = Json.parseToJsonElement(bodyAsText()).jsonObject

    private fun keys(names: String): Set<String> = names.split(" ").toSet()

    @Test
    fun `R7 - efficiency factor JSON shape`() {
        val config = testConfig()
        app(config) {
            val response = connectedBrowser().also { seedRuns(config) }.get("/api/efficiency-factor")
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.json()

            assertEquals(
                keys("windowStart windowEnd rollingDays minRunsForRolling current change points runsPendingDetail"),
                body.keys,
            )
            assertEquals("2026-04-30", body["windowStart"]?.jsonPrimitive?.content)
            assertEquals("2026-10-31", body["windowEnd"]?.jsonPrimitive?.content)
            assertEquals("28", body["rollingDays"]?.jsonPrimitive?.content)
            assertEquals("3", body["minRunsForRolling"]?.jsonPrimitive?.content)
            assertEquals("""{"efficiency":1.6,"date":"2026-10-30","runCount":3}""", body.getValue("current").toString())
            assertEquals(JsonNull, body["change"])
            assertEquals("1", body["runsPendingDetail"]?.jsonPrimitive?.content)
            val points = body.getValue("points").jsonArray.map { it.jsonObject }
            assertEquals(3, points.size)
            assertEquals(
                """{"date":"2026-10-26","runType":"easy","efficiency":1.6,"rollingEfficiency":null,""" +
                    """"distanceKm":10.0,"movingTimeSeconds":3000,"paceSecondsPerKm":300,""" +
                    """"averageHeartRateBpm":125.0}""",
                points.first().toString(),
            )
            assertTrue("Morning Run" !in response.bodyAsText())
        }
    }

    @Test
    fun `R7 - empty store returns empty points`() =
        app(testConfig()) {
            val body = connectedBrowser().get("/api/efficiency-factor").json()
            assertEquals(0, body.getValue("points").jsonArray.size)
            assertEquals(JsonNull, body["current"])
            assertEquals(JsonNull, body["change"])
        }

    @Test
    fun `R7 - 401 not_connected without session or token`() =
        app(testConfig()) {
            val response = client.get("/api/efficiency-factor")
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertEquals(
                """{"error":"not_connected","message":"Connect your Strava account."}""",
                response.bodyAsText(),
            )
        }

    @Test
    fun `R7 - 401 not_connected with a session but no stored token`() {
        val config = testConfig()
        app(config) {
            val browser = connectedBrowser()
            runBlocking { ExposedTokenRepository(DatabaseFactory.connect(config.dbPath)).delete() }
            val response = browser.get("/api/efficiency-factor")
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertEquals("not_connected", response.json()["error"]?.jsonPrimitive?.content)
        }
    }

    @Test
    fun `R7 - no Strava request is made`() {
        val config = testConfig()
        app(config) {
            val browser = connectedBrowser().also { seedRuns(config) }
            assertEquals(HttpStatusCode.OK, browser.get("/api/efficiency-factor").status)
            assertTrue(stravaRequests.isEmpty())
        }
    }
}
