package app.stravadashboard.api

import app.stravadashboard.module
import app.stravadashboard.testsupport.GarminFake
import app.stravadashboard.testsupport.TEST_ZONE
import app.stravadashboard.testsupport.browser
import app.stravadashboard.testsupport.fixedClock
import app.stravadashboard.testsupport.syncAndWait
import app.stravadashboard.testsupport.testConfig
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RoutesTest {
    private val config = testConfig()
    private val garmin = GarminFake()

    private fun resource(name: String): String = requireNotNull(javaClass.getResource("/garmin/$name")).readText()

    private fun app(block: suspend ApplicationTestBuilder.() -> Unit) =
        testApplication {
            garmin.activitiesJson = resource("activities.json")
            application { module(config, fixedClock(), TEST_ZONE, garminEngine = garmin.engine) }
            block()
        }

    @Test
    fun `021 R1 - every api route answers without a session`() =
        app {
            val paths =
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
                    "/api/weekly-summary",
                    "/api/run-weather",
                )
            paths.forEach { path ->
                val response = client.get(path)
                assertNotEquals(HttpStatusCode.Unauthorized, response.status, path)
                assertFalse("not_connected" in response.bodyAsText(), path)
            }
        }

    @Test
    fun `021 R1 - empty store returns empty dashboard not 401`() =
        app {
            val response = client.get("/api/dashboard/running")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(
                9,
                Json
                    .parseToJsonElement(response.bodyAsText())
                    .jsonObject
                    .obj("weeklyDistance")
                    .arr("weeks")
                    .size,
            )
        }

    @Test
    fun `021 R1 - auth routes are gone`() =
        app {
            listOf("/auth/strava/login", "/auth/strava/callback?code=x&state=y").forEach { path ->
                val response = client.get(path)
                assertEquals(HttpStatusCode.NotFound, response.status, path)
                val cookies = response.headers.getAll(HttpHeaders.SetCookie).orEmpty()
                assertTrue(cookies.none { it.startsWith("sd_session=") || it.startsWith("sd_oauth_state=") })
            }
        }

    @Test
    fun `021 R3 - a new Garmin run appears in weekly totals after a sync`() =
        app {
            val weeks =
                Json
                    .parseToJsonElement(syncedDashboardBody())
                    .jsonObject
                    .obj("weeklyDistance")
                    .arr("weeks")
                    .map { it.jsonObject }
            val week = weeks.single { it["weekStart"]?.jsonPrimitive?.content == "2026-10-19" }
            assertEquals("10.0", week["distanceKm"]?.jsonPrimitive?.content)
            assertEquals("1", week["runCount"]?.jsonPrimitive?.content)
        }

    private suspend fun ApplicationTestBuilder.syncedDashboardBody(): String {
        val browser = browser()
        browser.syncAndWait()
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
            val root = Json.parseToJsonElement(syncedDashboardBody()).jsonObject

            assertEquals(
                setOf("weeklyDistance", "heartRateTrend", "workoutPaceComparison", "weekComparison"),
                root.keys,
            )
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
        }

    @Test
    fun `010 R1 - pace window matches heart rate window and types carry points`() =
        app {
            val root = Json.parseToJsonElement(syncedDashboardBody()).jsonObject
            val pace = root.obj("workoutPaceComparison")
            val heartRate = root.obj("heartRateTrend")
            assertEquals(heartRate["windowStart"], pace["windowStart"])
            assertEquals(heartRate["windowEnd"], pace["windowEnd"])
            val keys = "typeKey label runCount points current previous changeSecondsPerKm runsWithoutPace hasEnoughData"
            assertEquals(keys.split(" ").toSet(), root.firstOf("workoutPaceComparison", "types").keys)
        }

    @Test
    fun `007 R2 - dashboard JSON includes the week comparison block`() =
        app {
            val week = Json.parseToJsonElement(syncedDashboardBody()).jsonObject.obj("weekComparison")
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
            val root = Json.parseToJsonElement(syncedDashboardBody()).jsonObject
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
            val body = syncedDashboardBody()
            listOf(
                "invented-access-token",
                "Invented Person",
                "Treadmill notes",
                "athlete",
                "111",
                "Latitude",
                "3.917",
            ).forEach { assertFalse(it in body, "dashboard JSON leaked '$it'") }
        }

    private fun JsonObject.obj(key: String): JsonObject = requireNotNull(this[key]).jsonObject

    private fun JsonObject.arr(key: String) = requireNotNull(this[key]).jsonArray
}
