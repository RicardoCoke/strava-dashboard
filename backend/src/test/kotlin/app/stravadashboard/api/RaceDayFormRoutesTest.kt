package app.stravadashboard.api

import app.stravadashboard.config.AppConfig
import app.stravadashboard.config.LthrSetting
import app.stravadashboard.domain.model.ActivityId
import app.stravadashboard.domain.model.Bpm
import app.stravadashboard.domain.model.HrHistogram
import app.stravadashboard.domain.model.HrHistogramResult
import app.stravadashboard.domain.model.HrHistogramStatus
import app.stravadashboard.domain.model.Seconds
import app.stravadashboard.domain.ports.ActivityDetail
import app.stravadashboard.domain.ports.PlanFile
import app.stravadashboard.module
import app.stravadashboard.persistence.DatabaseFactory
import app.stravadashboard.persistence.ExposedRunRepository
import app.stravadashboard.testsupport.FakePlanSource
import app.stravadashboard.testsupport.TEST_ZONE
import app.stravadashboard.testsupport.samplePlanText
import app.stravadashboard.testsupport.sixteenWeekPlanText
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Clock
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Feature 011: `GET /api/race-day-form`. Today is Saturday 2026-10-31; the 16-week plan ends Sunday 2026-12-06. */
class RaceDayFormRoutesTest {
    private val now = Instant.parse("2026-10-31T10:00:00Z")
    private val stravaRequests = mutableListOf<HttpRequestData>()
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val plan = FakePlanSource()
    private val lthr = LthrSetting.Valid(Bpm(175))

    private fun config(lthr: LthrSetting = this.lthr): AppConfig = testConfig().copy(lthr = lthr)

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
                    else -> respond("[]", HttpStatusCode.OK, jsonHeaders)
                }
            }
        application { module(config, Clock.fixed(now, TEST_ZONE), TEST_ZONE, engine, plan) }
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

    /** Three easy runs on 24, 27 and 30 Oct: 50 moving minutes at 140 bpm each (load 50.0, easy rate 1.0). */
    private fun seedEasyRuns(config: AppConfig) =
        runBlocking {
            val runs = ExposedRunRepository(DatabaseFactory.connect(config.dbPath))
            listOf(24, 27, 30).forEach { day ->
                val id = ActivityId(500L + day)
                runs.upsertSummaries(listOf(summary(id.value, Instant.parse("2026-10-${day}T07:00:00Z"))))
                runs.saveDetail(id, ActivityDetail(null, emptyList(), Seconds(3000)), now)
                runs.saveHrHistogram(
                    id,
                    HrHistogramResult(HrHistogramStatus.COMPUTED, HrHistogram(mapOf(140 to 3000))),
                    now,
                )
            }
        }

    private suspend fun HttpResponse.json(): JsonObject = Json.parseToJsonElement(bodyAsText()).jsonObject

    private fun keys(names: String): Set<String> = names.split(" ").toSet()

    private suspend fun HttpClient.errorOf(): Pair<HttpStatusCode, String?> {
        val response = get("/api/race-day-form")
        return response.status to response.json()["error"]?.jsonPrimitive?.content
    }

    @Test
    fun `R10 - 401 without session`() =
        app(config()) {
            val response = client.get("/api/race-day-form")
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertEquals("not_connected", response.json()["error"]?.jsonPrimitive?.content)
        }

    @Test
    fun `R10 - 200 exact JSON shape`() {
        val config = config()
        app(config) {
            val browser = connectedBrowser().also { seedEasyRuns(config) }
            val response = browser.get("/api/race-day-form")
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.json()
            assertEquals(
                keys(
                    "today raceDate chartStart chartEnd lastActualDate projection subtEstimated readyBand raceDay days",
                ),
                body.keys,
            )
            assertEquals("2026-12-06", body["raceDate"]?.jsonPrimitive?.content)
            assertEquals("2026-09-19", body["chartStart"]?.jsonPrimitive?.content)
            assertEquals("2026-10-30", body["lastActualDate"]?.jsonPrimitive?.content)
            assertEquals("projected", body["projection"]?.jsonPrimitive?.content)
            assertEquals("""{"lowPercent":5,"highPercent":25}""", body.getValue("readyBand").toString())
            val raceDay = body.getValue("raceDay").jsonObject
            assertEquals(keys("date kind form fitness peakFitness peakDate fitnessFromPeak label"), raceDay.keys)
            assertEquals("planned", raceDay["kind"]?.jsonPrimitive?.content)
            assertTrue(
                raceDay["label"]
                    ?.jsonPrimitive
                    ?.content
                    .orEmpty()
                    .startsWith("Form "),
            )
            val days = body.getValue("days").jsonArray.map { it.jsonObject }
            assertEquals(79, days.size)
            assertEquals(keys("date kind load fitness fatigue form readyLow readyHigh"), days.first().keys)
            assertTrue(stravaRequests.isEmpty())
        }
    }

    @Test
    fun `R10 - 404 codes name what is missing`() {
        app(config()) {
            plan.file = PlanFile.NotConfigured
            assertEquals(HttpStatusCode.NotFound to "race_day_form_missing_plan", connectedBrowser().errorOf())
        }
        app(config(LthrSetting.NotConfigured)) {
            plan.file = PlanFile.Content(sixteenWeekPlanText())
            assertEquals(HttpStatusCode.NotFound to "race_day_form_missing_lthr", connectedBrowser().errorOf())
        }
        app(config(LthrSetting.NotConfigured)) {
            plan.file = PlanFile.NotConfigured
            assertEquals(
                HttpStatusCode.NotFound to "race_day_form_missing_plan_and_lthr",
                connectedBrowser().errorOf(),
            )
        }
        app(config(LthrSetting.Invalid)) {
            plan.file = PlanFile.NotConfigured
            assertEquals(HttpStatusCode.NotFound to "race_day_form_missing_plan", connectedBrowser().errorOf())
        }
    }

    @Test
    fun `R10 - 422 invalid config texts`() {
        app(config(LthrSetting.Invalid)) {
            val response = connectedBrowser().get("/api/race-day-form")
            assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
            assertEquals(
                """{"error":"race_day_form_invalid_config",""" +
                    """"message":"LTHR_BPM is not a whole number between 100 and 220."}""",
                response.bodyAsText(),
            )
        }
        app(config()) {
            plan.file = PlanFile.Content("not;a;plan")
            val response = connectedBrowser().get("/api/race-day-form")
            assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
            assertEquals(
                """{"error":"race_day_form_invalid_config","message":"The training plan file has a problem."}""",
                response.bodyAsText(),
            )
        }
    }

    @Test
    fun `R10 - 503 plan unreadable`() =
        app(config()) {
            plan.file = PlanFile.Unreadable
            assertEquals(
                HttpStatusCode.ServiceUnavailable to "race_day_form_plan_unavailable",
                connectedBrowser().errorOf(),
            )
        }

    @Test
    fun `R11 - plan change shows on the next request`() {
        val config = config()
        app(config) {
            val browser = connectedBrowser().also { seedEasyRuns(config) }
            val raceDate =
                suspend {
                    browser
                        .get("/api/race-day-form")
                        .json()["raceDate"]
                        ?.jsonPrimitive
                        ?.content
                }
            assertEquals("2026-12-06", raceDate())
            // The 3-week sample plan from the same start ends on Sunday 2026-09-06.
            plan.file = PlanFile.Content(samplePlanText())
            assertEquals("2026-09-06", raceDate())
        }
    }

    @Test
    fun `R11 - plan edit changes projection on next request`() {
        val config = config()
        app(config) {
            val browser = connectedBrowser().also { seedEasyRuns(config) }
            val wednesday =
                suspend {
                    browser
                        .get("/api/race-day-form")
                        .json()
                        .getValue("days")
                        .jsonArray
                        .map { it.jsonObject }
                        .single { it["date"]?.jsonPrimitive?.content == "2026-12-02" }
                }
            // Race week Wednesday: 40 easy minutes at the learned rate of 1.0.
            assertEquals("40.0", wednesday()["load"]?.jsonPrimitive?.content)
            plan.file =
                PlanFile.Content(sixteenWeekPlanText().replace("EASY;72,00;40;;20;;40;", "EASY;72,00;40;;20;;30;"))
            assertEquals("30.0", wednesday()["load"]?.jsonPrimitive?.content)
        }
    }
}
