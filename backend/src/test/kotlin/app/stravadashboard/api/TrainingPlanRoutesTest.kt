package app.stravadashboard.api

import app.stravadashboard.domain.ports.PlanFile
import app.stravadashboard.module
import app.stravadashboard.testsupport.FakePlanSource
import app.stravadashboard.testsupport.GarminFake
import app.stravadashboard.testsupport.TEST_ZONE
import app.stravadashboard.testsupport.browser
import app.stravadashboard.testsupport.testConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
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
    private val garmin = GarminFake()
    private val clock = Clock.fixed(Instant.parse("2026-10-31T10:00:00Z"), TEST_ZONE)

    private fun app(block: suspend ApplicationTestBuilder.() -> Unit) =
        testApplication {
            application { module(config, clock, TEST_ZONE, source, garminEngine = garmin.engine) }
            block()
        }

    private fun ApplicationTestBuilder.connectedBrowser(): HttpClient = browser()

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
    fun `004 R8 - returns calendar json shape without notes (010 R10)`() =
        app {
            val response = connectedBrowser().get("/api/training-plan")
            assertEquals(HttpStatusCode.OK, response.status)
            val root = response.json()
            assertEquals(keys("startDate endDate progress targetPaces weeks"), root.keys)
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
    fun `004 R8 - makes no Garmin calls`() =
        app {
            val browser = connectedBrowser()
            browser.get("/api/training-plan")
            browser.get("/api/training-plan")
            assertEquals(emptyList(), garmin.requests)
        }
}
