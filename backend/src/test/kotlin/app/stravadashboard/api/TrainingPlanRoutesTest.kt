package app.stravadashboard.api

import app.stravadashboard.domain.ports.PlanFile
import app.stravadashboard.module
import app.stravadashboard.testsupport.FakePlanSource
import app.stravadashboard.testsupport.NOW
import app.stravadashboard.testsupport.TEST_ZONE
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.time.Clock
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrainingPlanRoutesTest {
    private val config = testConfig()
    private val source = FakePlanSource()
    private val stravaRequests = mutableListOf<HttpRequestData>()
    private val clock = Clock.fixed(Instant.parse("2026-10-31T10:00:00Z"), TEST_ZONE)

    private fun app(block: suspend ApplicationTestBuilder.() -> Unit) =
        testApplication {
            val engine =
                MockEngine { request ->
                    stravaRequests += request
                    respond(
                        """{"access_token":"a","refresh_token":"r","expires_at":${NOW.epochSecond + 99_999_999},
                           "athlete":{"id":7}}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            application { module(config, clock, TEST_ZONE, engine, source) }
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

    private suspend fun HttpResponse.json(): JsonObject = Json.parseToJsonElement(bodyAsText()).jsonObject

    private fun keys(names: String): Set<String> = names.split(" ").toSet()

    private val progressKeys =
        keys("phase percentComplete daysPassed totalDays currentWeekIndex weekCount daysToGo daysUntilStart label")
    private val paceKeys = keys("key label targetSecondsPerKm minSecondsPerKm maxSecondsPerKm")
    private val weekKeys = keys("index label startDate endDate distanceKm easyPercent subtPercent isCurrentWeek days")
    private val dayKeys =
        keys("date dayOfWeek session note totalMinutes easyMinutes subtMinutes distanceKm isRest race isToday isPast")

    private fun JsonObject.first(list: String): JsonObject = getValue(list).jsonArray.first().jsonObject

    @Test
    fun `004 R8 - returns calendar json shape`() =
        app {
            val response = connectedBrowser().get("/api/training-plan")
            assertEquals(HttpStatusCode.OK, response.status)
            val root = response.json()
            assertEquals(keys("startDate endDate progress targetPaces notes weeks"), root.keys)
            assertEquals(progressKeys, root.getValue("progress").jsonObject.keys)
            assertEquals(paceKeys, root.first("targetPaces").keys)
            val week = root.first("weeks")
            assertEquals(weekKeys, week.keys)
            assertEquals(7, week.getValue("days").jsonArray.size)
            assertEquals(dayKeys, week.first("days").keys)
        }

    @Test
    fun `004 R8 - returns progress and marathon values`() =
        app {
            val body = connectedBrowser().get("/api/training-plan").bodyAsText()
            assertTrue(""""label":"66% · week 11 of 16 · 36 days to go"""" in body)
            assertTrue(""""phase":"inProgress"""" in body)
            assertTrue(""""race":{"label":"Marathon · 42.2 km","distanceKm":42.2,"isMarathon":true}""" in body)
            assertTrue(""""startDate":"2026-08-17","endDate":"2026-12-06"""" in body)
        }

    @Test
    fun `004 R8 - 401 without session`() =
        app {
            val response = client.get("/api/training-plan")
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertEquals(
                "not_connected",
                response
                    .json()
                    .getValue("error")
                    .toString()
                    .trim('"'),
            )
        }

    @Test
    fun `004 R8 - 404 not configured`() =
        app {
            source.file = PlanFile.NotConfigured
            val response = connectedBrowser().get("/api/training-plan")
            assertEquals(HttpStatusCode.NotFound, response.status)
            assertEquals(
                """{"error":"training_plan_not_configured","message":"No training plan is configured."}""",
                response.bodyAsText(),
            )
        }

    @Test
    fun `004 R8 - 503 unreadable`() =
        app {
            source.file = PlanFile.Unreadable
            val response = connectedBrowser().get("/api/training-plan")
            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
            assertEquals(
                """{"error":"training_plan_unavailable","message":"The training plan file could not be read."}""",
                response.bodyAsText(),
            )
        }

    @Test
    fun `004 R8 - 422 invalid with safe message`() =
        app {
            source.file = PlanFile.Content("WEEK 1;;MONDAY\n")
            val response = connectedBrowser().get("/api/training-plan")
            assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
            assertEquals(
                """{"error":"training_plan_invalid","message":"WEEK 1: EASY row missing"}""",
                response.bodyAsText(),
            )
        }

    @Test
    fun `004 R8 - no Strava call`() =
        app {
            val browser = connectedBrowser()
            browser.get("/api/training-plan")
            browser.get("/api/training-plan")
            assertEquals(emptyList(), stravaRequests)
        }
}
