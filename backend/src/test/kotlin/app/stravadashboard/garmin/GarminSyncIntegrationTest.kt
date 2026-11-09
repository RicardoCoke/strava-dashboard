package app.stravadashboard.garmin

import app.stravadashboard.domain.RunClassifier
import app.stravadashboard.domain.SyncOutcome
import app.stravadashboard.domain.SyncService
import app.stravadashboard.domain.model.RunType
import app.stravadashboard.persistence.DatabaseFactory
import app.stravadashboard.persistence.ExposedRunRepository
import app.stravadashboard.persistence.ExposedSyncStateRepository
import app.stravadashboard.testsupport.FitRecord
import app.stravadashboard.testsupport.GARMIN_LIST_PATH
import app.stravadashboard.testsupport.GARMIN_TOKEN_PATH
import app.stravadashboard.testsupport.MutableClock
import app.stravadashboard.testsupport.TEST_ZONE
import app.stravadashboard.testsupport.fitZip
import app.stravadashboard.testsupport.run
import app.stravadashboard.testsupport.tempDbPath
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/** Feature 020: Garmin JSON and FIT files through the real sync into SQLite. Every activity here is invented. */
class GarminSyncIntegrationTest {
    private val clock = MutableClock(Instant.parse("2026-11-14T10:00:00Z"))
    private val database = DatabaseFactory.connect(tempDbPath())
    private val runs = ExposedRunRepository(database)
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private val list =
        """
        [
          {"activityId": 31, "activityName": "Invented tempo", "startTimeLocal": "2026-11-11 07:00:00",
           "startTimeGMT": "2026-11-11 07:00:00", "activityType": {"typeKey": "running"},
           "distance": 6000.0, "averageHR": 158.0},
          {"activityId": 32, "activityName": "Invented Sunday run", "startTimeLocal": "2026-11-13 08:00:00",
           "startTimeGMT": "2026-11-13 08:00:00", "activityType": {"typeKey": "running"},
           "distance": 22000.0, "averageHR": 142.0}
        ]
        """.trimIndent()

    /** 40 minutes of records at 4 m/s with heart rate rising from 140 to 160. */
    private val fit =
        fitZip(
            (0..2400 step 5).map { s ->
                FitRecord(s, distance = s * 4f, speed = 4f, heartRate = 140 + s / 120)
            },
        )

    private val engine =
        MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path == GARMIN_TOKEN_PATH -> respond("invented-access-token", HttpStatusCode.OK)
                path == GARMIN_LIST_PATH -> respond(list, HttpStatusCode.OK, json)
                path.endsWith("/31") && path.startsWith("/activity-service/") ->
                    respond(
                        """{"description":"5x6min @ 4:00","summaryDTO":{"movingDuration":2400}}""",
                        HttpStatusCode.OK,
                        json,
                    )
                path.startsWith("/activity-service/") ->
                    respond("""{"summaryDTO":{"movingDuration":5500}}""", HttpStatusCode.OK, json)
                else -> respond(ByteReadChannel(fit), HttpStatusCode.OK)
            }
        }

    private val service =
        SyncService(
            GarminActivitySource(
                GarminConnect(garminHttpClient(engine), "invented-garmin-key", clock),
                clock,
                TEST_ZONE,
            ),
            runs,
            ExposedSyncStateRepository(database),
            clock,
            TEST_ZONE,
            LocalDate.of(2026, 11, 10),
        )

    @Test
    fun `R5 - a Garmin workout and long run get stream pace, histograms and drift`() {
        repeat(3) {
            assertIs<SyncOutcome.Done>(runBlocking { service.syncIfDue() })
            clock.advance(Duration.ofMinutes(20))
        }
        val stored = runBlocking { runs.findAll() }.associateBy { it.id.value }

        val workout = assertNotNull(stored[31])
        assertEquals("5x6min @ 4:00", workout.description)
        assertNotNull(workout.streamPace)
        assertNotNull(workout.hrHistogram)
        assertEquals(10, workout.splits?.size)

        val longRun = assertNotNull(stored[32])
        assertEquals(RunType.LongRun, RunClassifier.classify(longRun))
        assertNotNull(longRun.hrHistogram)
        assertNotNull(longRun.hrDrift)
    }

    @Test
    fun `R4 - Garmin description classifies like a Strava description`() {
        runBlocking { service.syncIfDue() }
        val garmin = assertNotNull(runBlocking { runs.findAll() }.find { it.id.value == 31L })
        // The same fields as a run built the way the Strava mappers stored them.
        val strava =
            run(
                id = 31,
                date = LocalDate.of(2026, 11, 11),
                name = "Invented tempo",
                distanceMeters = 6000.0,
                description = "5x6min @ 4:00",
            )

        assertEquals(RunClassifier.classify(strava), RunClassifier.classify(garmin))
        assertIs<RunType.Workout>(RunClassifier.classify(garmin))
    }
}
