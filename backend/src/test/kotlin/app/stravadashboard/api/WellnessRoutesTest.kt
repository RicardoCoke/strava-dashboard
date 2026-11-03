package app.stravadashboard.api

import app.stravadashboard.config.AppConfig
import app.stravadashboard.config.LthrSetting
import app.stravadashboard.domain.model.ActivityId
import app.stravadashboard.domain.model.Bpm
import app.stravadashboard.domain.model.HrHistogram
import app.stravadashboard.domain.model.HrHistogramResult
import app.stravadashboard.domain.model.HrHistogramStatus
import app.stravadashboard.domain.wellness.WellnessFetchStatus
import app.stravadashboard.domain.wellness.WellnessState
import app.stravadashboard.module
import app.stravadashboard.persistence.DatabaseFactory
import app.stravadashboard.persistence.ExposedRunRepository
import app.stravadashboard.persistence.ExposedTokenRepository
import app.stravadashboard.persistence.ExposedWellnessRepository
import app.stravadashboard.testsupport.TEST_ZONE
import app.stravadashboard.testsupport.WELLNESS_TODAY
import app.stravadashboard.testsupport.summary
import app.stravadashboard.testsupport.syncAndWait
import app.stravadashboard.testsupport.testConfig
import app.stravadashboard.testsupport.wellnessDay
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

class WellnessRoutesTest {
    private val now = Instant.parse("2026-10-31T10:00:00Z")
    private val clock = Clock.fixed(now, TEST_ZONE)
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val stravaRequests = mutableListOf<HttpRequestData>()
    private val garminRequests = mutableListOf<HttpRequestData>()
    private var garminStatus = HttpStatusCode.OK

    private fun config(key: String? = "invented-key"): AppConfig =
        testConfig().copy(lthr = LthrSetting.Valid(Bpm(175)), garminApiKey = key)

    private fun MockRequestHandleScope.strava(request: HttpRequestData): HttpResponseData =
        when (request.url.encodedPath) {
            "/oauth/token" ->
                respond(
                    """{"access_token":"a","refresh_token":"r","expires_at":${now.epochSecond + 99_999},
                       "athlete":{"id":7}}""",
                    HttpStatusCode.OK,
                    jsonHeaders,
                )
            else -> respond("[]", HttpStatusCode.OK, jsonHeaders)
        }

    // Token, then resting HR, then HRV; a 401 on the token call stops after one request.
    private fun garminBody(path: String): String =
        when {
            path.endsWith("/token") -> "invented-token"
            path.contains(
                "dailysummaries",
            ) -> """{"data":[{"calendarDate":"2026-10-31","heartRate":{"restingValue":50}}]}"""
            else -> """{"hrvSummaries":[{"calendarDate":"2026-10-31","lastNightAvg":60}]}"""
        }

    private fun app(
        config: AppConfig,
        block: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        val strava =
            MockEngine { request ->
                stravaRequests += request
                strava(request)
            }
        val garmin =
            MockEngine { request ->
                garminRequests += request
                respond(garminBody(request.url.encodedPath), garminStatus, jsonHeaders)
            }
        application { module(config, clock, TEST_ZONE, strava, garminEngine = garmin) }
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

    /** 80 days of invented readings, the newest one well off baseline, and a last fetch with [status]. */
    private fun seedWellness(
        config: AppConfig,
        status: WellnessFetchStatus = WellnessFetchStatus.OK,
    ) = runBlocking {
        val repo = ExposedWellnessRepository(DatabaseFactory.connect(config.dbPath))
        val days =
            (1L..79L).map { wellnessDay(WELLNESS_TODAY.minusDays(it), restingHr = 47, hrv = 60.0) } +
                wellnessDay(WELLNESS_TODAY, restingHr = 53, hrv = 49.0)
        repo.replaceRange(WELLNESS_TODAY.minusDays(90), WELLNESS_TODAY, days, now)
        repo.saveState(WellnessState(Instant.parse("2026-10-31T06:15:00Z"), now, status, null))
    }

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

    private fun JsonObject.text(key: String): String? = get(key)?.jsonPrimitive?.content

    @Test
    fun `R1 - wellness returns 404 when key not configured`() =
        app(config(key = null)) {
            val response = connectedBrowser().get("/api/wellness")
            assertEquals(HttpStatusCode.NotFound, response.status)
            assertEquals(
                """{"error":"wellness_not_configured","message":"Garmin Connect is not connected."}""",
                response.bodyAsText(),
            )
        }

    @Test
    fun `R8 - wellness requires connected session`() =
        app(config()) {
            val response = client.get("/api/wellness")
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertEquals("not_connected", response.json().text("error"))
        }

    @Test
    fun `R8 - wellness requires a session matching the stored tokens`() {
        val config = config()
        app(config) {
            val browser = connectedBrowser()
            runBlocking { ExposedTokenRepository(DatabaseFactory.connect(config.dbPath)).delete() }
            val response = browser.get("/api/wellness")
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertEquals("not_connected", response.json().text("error"))
        }
    }

    @Test
    fun `R8 - pending source with no data`() =
        app(config()) {
            val body = connectedBrowser().get("/api/wellness").json()

            val days = body.getValue("days").jsonArray
            assertEquals(84, days.size)
            assertTrue(
                days.all { day ->
                    day.jsonObject
                        .filterKeys { it != "date" }
                        .values
                        .all { it == JsonNull }
                },
            )
            assertEquals("0", body.text("daysWithData"))
            val latest = body.getValue("latest").jsonObject
            assertEquals(JsonNull, latest["restingHr"])
            assertEquals(JsonNull, latest["hrv"])
            assertEquals(JsonNull, body.getValue("readiness").jsonObject["note"])
            assertEquals("""{"status":"pending","lastFetchedAt":null}""", body.getValue("source").toString())
        }

    @Test
    fun `R8 - wellness response shape`() {
        val config = config()
        app(config) {
            val browser = connectedBrowser().also { seedWellness(config) }
            val body = browser.get("/api/wellness").json()

            assertEquals(
                keys("windowStart windowEnd baselineDays daysWithData days latest readiness source"),
                body.keys,
            )
            assertEquals("2026-08-09", body.text("windowStart"))
            assertEquals("2026-10-31", body.text("windowEnd"))
            assertEquals("7", body.text("baselineDays"))
            assertEquals("80", body.text("daysWithData"))
            val today =
                body
                    .getValue("days")
                    .jsonArray
                    .last()
                    .jsonObject
            assertEquals(
                """{"date":"2026-10-31","restingHrBpm":53,"restingHrBaselineBpm":47.0,"hrvMs":49,""" +
                    """"hrvBaselineMs":60.0}""",
                today.toString(),
            )
            val readiness = body.getValue("readiness").jsonObject
            assertEquals(keys("note loadHigh"), readiness.keys)
            assertEquals("HRV below baseline and resting HR above baseline", readiness.text("note"))
            assertEquals(
                """{"status":"ok","lastFetchedAt":"2026-10-31T06:15:00Z"}""",
                body.getValue("source").toString(),
            )
        }
    }

    @Test
    fun `R6 - latest values with delta labels and flags`() {
        val config = config()
        app(config) {
            val browser = connectedBrowser().also { seedWellness(config) }
            val latest =
                browser
                    .get("/api/wellness")
                    .json()
                    .getValue("latest")
                    .jsonObject
            assertEquals(
                """{"date":"2026-10-31","bpm":53,"baselineBpm":47.0,"deltaBpm":6.0,""" +
                    """"deltaLabel":"+6.0 bpm vs baseline","flagged":true}""",
                latest.getValue("restingHr").toString(),
            )
            assertEquals(
                """{"date":"2026-10-31","ms":49,"baselineMs":60.0,"deltaPercent":-18,""" +
                    """"deltaLabel":"−18% vs baseline","flagged":true}""",
                latest.getValue("hrv").toString(),
            )
        }
    }

    @Test
    fun `R4 - cached days returned with failed source status`() {
        val config = config()
        app(config) {
            val browser = connectedBrowser().also { seedWellness(config, WellnessFetchStatus.KEY_REJECTED) }
            val body = browser.get("/api/wellness").json()
            assertEquals("80", body.text("daysWithData"))
            assertEquals("keyRejected", body.getValue("source").jsonObject.text("status"))
        }
    }

    @Test
    fun `R8 - wellness endpoint makes no external calls`() {
        val config = config()
        app(config) {
            val browser = connectedBrowser().also { seedWellness(config) }
            assertEquals(HttpStatusCode.OK, browser.get("/api/wellness").status)
            assertTrue(garminRequests.isEmpty())
            assertTrue(stravaRequests.isEmpty())
        }
    }

    @Test
    fun `R3 - background sync job refreshes wellness after the Strava step`() {
        val config = config()
        app(config) {
            val browser = connectedBrowser()
            browser.syncAndWait()

            assertEquals(3, garminRequests.size)
            val body = browser.get("/api/wellness").json()
            assertEquals("1", body.text("daysWithData"))
            assertEquals("ok", body.getValue("source").jsonObject.text("status"))
        }
    }

    /** One background sync from a fresh app: the `/api/sync` body, the Strava paths called and the Garmin calls. */
    private fun syncOnce(config: AppConfig): Triple<String, List<String>, Int> {
        var result = Triple("", emptyList<String>(), 0)
        garminRequests.clear()
        app(config) {
            val status = connectedBrowser().syncAndWait()
            assertEquals(HttpStatusCode.OK, status.status)
            result = Triple(status.bodyAsText(), stravaRequests.map { it.url.encodedPath }, garminRequests.size)
        }
        return result
    }

    @Test
    fun `R1 - job makes no Garmin calls without a key and the Strava sync is unchanged`() {
        val withoutKey = syncOnce(config(key = null))
        val withKey = syncOnce(config())

        assertEquals(0, withoutKey.third)
        assertEquals(3, withKey.third)
        assertEquals(withoutKey.first, withKey.first)
        assertEquals(withoutKey.second, withKey.second)
    }

    @Test
    fun `R4 - rejected key leaves the Strava sync and its status unchanged`() {
        val withoutKey = syncOnce(config(key = null))
        garminStatus = HttpStatusCode.Unauthorized
        val rejected = syncOnce(config())

        assertEquals(1, rejected.third)
        assertEquals(withoutKey.first, rejected.first)
        assertEquals(withoutKey.second, rejected.second)
    }

    @Test
    fun `R11 - training load response identical with wellness configured`() {
        val withKey = config()
        val withoutKey = config(key = null)
        var bodies = listOf<String>()
        app(withKey) {
            val browser = connectedBrowser().also { seedRun(withKey) }.also { seedWellness(withKey) }
            bodies = bodies + browser.get("/api/training-load").bodyAsText()
        }
        app(withoutKey) {
            val browser = connectedBrowser().also { seedRun(withoutKey) }
            bodies = bodies + browser.get("/api/training-load").bodyAsText()
        }
        assertEquals(bodies[0], bodies[1])
    }
}
