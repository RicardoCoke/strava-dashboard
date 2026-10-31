package app.stravadashboard.api

import app.stravadashboard.config.AppConfig
import app.stravadashboard.config.LthrSetting
import app.stravadashboard.domain.model.ActivityId
import app.stravadashboard.domain.model.Bpm
import app.stravadashboard.domain.model.HrHistogram
import app.stravadashboard.domain.model.HrHistogramResult
import app.stravadashboard.domain.model.HrHistogramStatus
import app.stravadashboard.module
import app.stravadashboard.persistence.DatabaseFactory
import app.stravadashboard.persistence.ExposedRunRepository
import app.stravadashboard.testsupport.TEST_ZONE
import app.stravadashboard.testsupport.summary
import app.stravadashboard.testsupport.syncAndWait
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Clock
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrainingLoadRoutesTest {
    // Saturday 2026-10-31, as in the spec examples.
    private val now = Instant.parse("2026-10-31T10:00:00Z")
    private val clock = Clock.fixed(now, TEST_ZONE)
    private val stravaRequests = mutableListOf<HttpRequestData>()
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private var revoked = false
    private var rateLimited = false

    private fun config(lthr: LthrSetting): AppConfig = testConfig().copy(lthr = lthr)

    private fun MockRequestHandleScope.strava(request: HttpRequestData): HttpResponseData =
        when {
            revoked -> respond("{}", HttpStatusCode.Unauthorized, jsonHeaders)
            rateLimited && request.url.encodedPath != "/oauth/token" ->
                respond("{}", HttpStatusCode.TooManyRequests, jsonHeaders)
            request.url.encodedPath == "/oauth/token" ->
                respond(
                    """{"access_token":"a","refresh_token":"r","expires_at":${now.epochSecond + 99_999},
                       "athlete":{"id":7}}""",
                    HttpStatusCode.OK,
                    jsonHeaders,
                )
            request.url.encodedPath == "/api/v3/athlete/activities" -> respond("[]", HttpStatusCode.OK, jsonHeaders)
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

    /** One run on Fri 2026-10-30 with 50 minutes at 140 bpm: load 50.0 for LTHR 175. */
    private fun seedRun(config: AppConfig) =
        runBlocking {
            val runs = ExposedRunRepository(DatabaseFactory.connect(config.dbPath))
            runs.upsertSummaries(listOf(summary(500, Instant.parse("2026-10-30T07:00:00Z"))))
            runs.saveHrHistogram(
                ActivityId(500),
                HrHistogramResult(HrHistogramStatus.COMPUTED, HrHistogram(mapOf(140 to 3000))),
                now,
            )
        }

    private suspend fun HttpResponse.json(): JsonObject = Json.parseToJsonElement(bodyAsText()).jsonObject

    private fun keys(names: String): Set<String> = names.split(" ").toSet()

    private fun listCalls(): Int = stravaRequests.count { it.url.encodedPath == "/api/v3/athlete/activities" }

    @Test
    fun `009 R1 - read endpoints make no Strava calls when a sync is due`() =
        app(config(LthrSetting.Valid(Bpm(175)))) {
            val browser = connectedBrowser()
            val statuses =
                coroutineScope {
                    listOf("/api/training-load", "/api/dashboard/running")
                        .map { path -> async { browser.get(path).status } }
                        .awaitAll()
                }
            assertEquals(listOf(HttpStatusCode.OK, HttpStatusCode.OK), statuses)
            assertEquals(0, listCalls())
        }

    @Test
    fun `R9 - rate limited sync still serves stored data`() {
        val config = config(LthrSetting.Valid(Bpm(175)))
        app(config) {
            val browser = connectedBrowser().also { seedRun(config) }
            rateLimited = true
            browser.syncAndWait()
            val response = browser.get("/api/training-load")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(
                1,
                response
                    .json()["runsCounted"]
                    ?.jsonPrimitive
                    ?.content
                    ?.toInt(),
            )
            assertEquals(1, listCalls())
        }
    }

    @Test
    fun `R9 - 401 without session`() =
        app(config(LthrSetting.Valid(Bpm(175)))) {
            val response = client.get("/api/training-load")
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertEquals("not_connected", response.json()["error"]?.jsonPrimitive?.content)
        }

    @Test
    fun `R9 - 404 not configured without sync`() =
        app(config(LthrSetting.NotConfigured)) {
            val response = connectedBrowser().get("/api/training-load")
            assertEquals(HttpStatusCode.NotFound, response.status)
            assertEquals(
                """{"error":"training_load_not_configured","message":"No threshold heart rate is configured."}""",
                response.bodyAsText(),
            )
            assertTrue(stravaRequests.isEmpty())
        }

    @Test
    fun `R9 - 422 invalid config without value`() =
        app(config(LthrSetting.Invalid)) {
            val response = connectedBrowser().get("/api/training-load")
            assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
            assertEquals(
                """{"error":"training_load_invalid_config",""" +
                    """"message":"LTHR_BPM is not a whole number between 100 and 220."}""",
                response.bodyAsText(),
            )
            assertTrue(stravaRequests.isEmpty())
        }

    @Test
    fun `R9 - 200 exact JSON shape`() {
        val config = config(LthrSetting.Valid(Bpm(175)))
        app(config) {
            val response = connectedBrowser().also { seedRun(config) }.get("/api/training-load")
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.json()

            assertEquals(
                keys(
                    "windowStart windowEnd fitnessDays fatigueDays zones latest days ramp runsCounted " +
                        "runsSkippedNoHeartRate runsPendingHeartRate",
                ),
                body.keys,
            )
            assertEquals("2026-04-30", body["windowStart"]?.jsonPrimitive?.content)
            assertEquals("2026-10-31", body["windowEnd"]?.jsonPrimitive?.content)
            val zones = body.getValue("zones").jsonObject
            assertEquals(keys("lthrBpm easyBelowBpm hardAboveBpm label"), zones.keys)
            assertEquals(
                "Zones from LTHR 175 bpm: easy < 153 · subT 153–175 · hard > 175",
                zones["label"]?.jsonPrimitive?.content,
            )
            val days = body.getValue("days").jsonArray
            assertEquals(185, days.size)
            assertEquals(keys("date load fitness fatigue form"), days.first().jsonObject.keys)
            assertEquals(keys("date fitness fatigue form"), body.getValue("latest").jsonObject.keys)
            val ramp = body.getValue("ramp").jsonObject
            assertEquals(
                keys(
                    "weekStart weekEnd weekToDate load previousLoad changePercent previousWeekLight warning " +
                        "changeLabel",
                ),
                ramp.getValue("thisWeek").jsonObject.keys,
            )
            assertEquals(1, body["runsCounted"]?.jsonPrimitive?.content?.toInt())
        }
    }

    @Test
    fun `R9 - rounding of form from unrounded values`() {
        val config = config(LthrSetting.Valid(Bpm(175)))
        app(config) {
            val body = connectedBrowser().also { seedRun(config) }.get("/api/training-load").json()
            val friday =
                body.getValue("days").jsonArray.single { it.jsonObject["date"]?.jsonPrimitive?.content == "2026-10-30" }
            val values = friday.jsonObject.filterKeys { it != "date" }.mapValues { it.value.jsonPrimitive.double }
            assertEquals(50.0, values["load"])
            assertEquals(1.2, values["fitness"])
            assertEquals(7.1, values["fatigue"])
            // -5.952… rounds to -6.0; subtracting the rounded values would give -5.9.
            assertEquals(-6.0, values["form"])
        }
    }
}
