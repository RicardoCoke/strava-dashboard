package app.stravadashboard.api

import app.stravadashboard.config.AppConfig
import app.stravadashboard.domain.model.Meters
import app.stravadashboard.domain.model.Seconds
import app.stravadashboard.domain.model.Split
import app.stravadashboard.domain.ports.ActivityDetail
import app.stravadashboard.domain.ports.ActivitySummary
import app.stravadashboard.module
import app.stravadashboard.persistence.DatabaseFactory
import app.stravadashboard.persistence.ExposedRunRepository
import app.stravadashboard.testsupport.GarminFake
import app.stravadashboard.testsupport.TEST_ZONE
import app.stravadashboard.testsupport.browser
import app.stravadashboard.testsupport.summary
import app.stravadashboard.testsupport.testConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// All runs here are invented.
class WorkoutCompareRoutesTest {
    private val now = Instant.parse("2026-11-26T10:00:00Z")
    private val clock = Clock.fixed(now, TEST_ZONE)
    private val garmin = GarminFake()
    private val config = testConfig()

    private fun app(block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) =
        testApplication {
            application { module(config, clock, TEST_ZONE, garminEngine = garmin.engine) }
            block(browser())
        }

    private fun seed(
        config: AppConfig,
        vararg runs: Pair<ActivitySummary, Int?>,
    ) = runBlocking {
        val repository = ExposedRunRepository(DatabaseFactory.connect(config.dbPath))
        repository.upsertSummaries(runs.map { it.first })
        val splits = listOf(Split(0, Meters(1000.0), Seconds(300), Meters(4.0), 150.4))
        runs.forEach { (run, moving) ->
            moving?.let { repository.saveDetail(run.id, ActivityDetail(null, emptyList(), Seconds(it), splits), now) }
        }
    }

    private fun daysAgo(days: Long): Instant = now.minus(days, ChronoUnit.DAYS).minus(3, ChronoUnit.HOURS)

    private suspend fun HttpClient.json(path: String): JsonObject =
        Json.parseToJsonElement(get(path).bodyAsText()).jsonObject

    private suspend fun HttpClient.status(path: String): HttpStatusCode = get(path).status

    @Test
    fun `R2 - list returns the 30 latest runs of the type newest first`() =
        app { browser ->
            val workouts = (1..32L).map { summary(it, daysAgo(it), name = "5x6min") to 3000 }
            seed(config, *workouts.toTypedArray(), summary(100, daysAgo(0), name = "Easy") to 3000)

            val ids =
                browser.json("/api/workout-compare/runs?type=workouts").getValue("runs").jsonArray.map {
                    it.jsonObject
                        .getValue("id")
                        .jsonPrimitive.long
                }

            assertEquals((1..30L).toList(), ids)
        }

    @Test
    fun `R2 - list item fields and units`() =
        app { browser ->
            val treadmill =
                summary(
                    7,
                    Instant.parse("2026-11-24T07:30:00Z"),
                    name = "Tempo",
                    trainer = true,
                    distanceMeters = 10_400.0,
                )
            seed(config, treadmill to 3036, summary(8, daysAgo(5), name = "") to null)

            val body = browser.get("/api/workout-compare/runs?type=all").bodyAsText()

            assertEquals(
                """{"runs":[{"id":7,"date":"2026-11-24","name":"Tempo","typeKey":"easy","distanceKm":10.4,""" +
                    """"averagePaceSecondsPerKm":292,"treadmill":true},{"id":8,"date":"2026-11-21","name":"",""" +
                    """"typeKey":"easy","distanceKm":10.0,"averagePaceSecondsPerKm":null,"treadmill":false}]}""",
                body,
            )
        }

    @Test
    fun `R2 - invalid type gives 400`() =
        app { browser ->
            for (path in listOf(
                "/api/workout-compare/runs",
                "/api/workout-compare/runs?type=Workouts",
                "/api/workout-compare/runs?type=x",
            )) {
                val response = browser.get(path)
                assertEquals(HttpStatusCode.BadRequest, response.status, path)
                assertEquals(
                    """{"error":"invalid_type","message":"type must be all, workouts, long or easy."}""",
                    response.bodyAsText(),
                )
            }
        }

    @Test
    fun `R2 - empty store gives empty list`() =
        app { browser ->
            assertEquals("""{"runs":[]}""", browser.get("/api/workout-compare/runs?type=all").bodyAsText())
        }

    @Test
    fun `R2 - list makes no Garmin calls`() =
        app { browser ->
            seed(config, summary(1, daysAgo(1)) to 3000)
            assertEquals(HttpStatusCode.OK, browser.status("/api/workout-compare/runs?type=all"))
            assertTrue(garmin.requests.isEmpty())
        }

    @Test
    fun `R4 - runs are ordered earlier then later whatever a and b`() =
        app { browser ->
            seed(
                config,
                summary(10, daysAgo(16), name = "5x6min") to 3000,
                summary(20, daysAgo(2), name = "8x3min") to 3000,
            )

            val forward = browser.get("/api/workout-compare?a=10&b=20").bodyAsText()
            val backward = browser.get("/api/workout-compare?a=20&b=10").bodyAsText()

            assertEquals(forward, backward)
            val ids =
                Json.parseToJsonElement(forward).jsonObject.getValue("runs").jsonArray.map {
                    it.jsonObject
                        .getValue("id")
                        .jsonPrimitive.long
                }
            assertEquals(listOf(10L, 20L), ids)
        }

    @Test
    fun `R4 - missing, malformed or equal ids give 400`() =
        app { browser ->
            val paths = listOf("?a=1", "?b=1", "?a=x&b=2", "?a=1.5&b=2", "?a=3&b=3")
            for (query in paths) {
                val response = browser.get("/api/workout-compare$query")
                assertEquals(HttpStatusCode.BadRequest, response.status, query)
                assertEquals(
                    """{"error":"invalid_runs","message":"a and b must be two different run ids."}""",
                    response.bodyAsText(),
                )
            }
        }

    @Test
    fun `R4 - unknown or other person's run gives 404`() =
        app { browser ->
            seed(config, summary(10, daysAgo(3)) to 3000)
            val response = browser.get("/api/workout-compare?a=10&b=99")
            assertEquals(HttpStatusCode.NotFound, response.status)
            assertEquals("""{"error":"run_not_found","message":"Run not found."}""", response.bodyAsText())
        }

    @Test
    fun `R4 - response shape`() =
        app { browser ->
            seed(
                config,
                summary(10, daysAgo(16), name = "5x6min") to 3000,
                summary(20, daysAgo(2), name = "8x3min") to 3000,
            )

            val body = browser.json("/api/workout-compare?a=10&b=20")
            val earlier = body.getValue("runs").jsonArray[0].jsonObject

            assertEquals(setOf("resolution", "coarse", "runs", "reps"), body.keys)
            // Garmin has no file for the earlier run (the fake answers 404), so both use splits.
            assertEquals(""""splits"""", body["resolution"].toString())
            assertEquals("""{"reason":"noDetailFile","runId":10,"retryAfter":null}""", body["coarse"].toString())
            assertEquals(setOf("id", "date", "name", "typeKey", "treadmill", "stats", "points"), earlier.keys)
            assertEquals(
                """{"distanceKm":10.0,"movingTimeSeconds":3000,"averagePaceSecondsPerKm":300,""" +
                    """"averageHeartRateBpm":150,"elevationGainMeters":4,"efficiencyFactor":1.333,"hrDriftPct":null}""",
                earlier["stats"].toString(),
            )
            assertEquals(
                """[{"distanceKm":1.0,"timeSeconds":300,"paceSecondsPerKm":300,"heartRateBpm":150}]""",
                earlier["points"].toString(),
            )
            assertEquals(
                """{"mode":"unavailable","unavailableReason":"noDetailedData","unavailableRunId":null,""" +
                    """"pieceMinutes":null,"piecesRunId":null,"average":null,"pairs":[]}""",
                body["reps"].toString(),
            )
            assertTrue(garmin.dataCalls.any { it.url.encodedPath.startsWith("/download-service/") })
        }
}
