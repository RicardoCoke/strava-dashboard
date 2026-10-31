package app.stravadashboard.persistence

import app.stravadashboard.domain.SyncOutcome
import app.stravadashboard.domain.SyncService
import app.stravadashboard.domain.model.ActivityId
import app.stravadashboard.domain.model.HrHistogram
import app.stravadashboard.domain.model.HrHistogramResult
import app.stravadashboard.domain.model.HrHistogramStatus
import app.stravadashboard.domain.model.Seconds
import app.stravadashboard.domain.model.SecondsPerKm
import app.stravadashboard.domain.model.StreamPaceResult
import app.stravadashboard.domain.model.StreamPaceStatus
import app.stravadashboard.domain.ports.ActivityDetail
import app.stravadashboard.domain.ports.StoredTokens
import app.stravadashboard.domain.ports.SyncState
import app.stravadashboard.testsupport.FakeActivitySource
import app.stravadashboard.testsupport.MutableClock
import app.stravadashboard.testsupport.NOW
import app.stravadashboard.testsupport.TEST_ZONE
import app.stravadashboard.testsupport.kmLap
import app.stravadashboard.testsupport.summary
import app.stravadashboard.testsupport.tempDbPath
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExposedRunRepositoryTest {
    private val dbPath = tempDbPath()
    private val database = DatabaseFactory.connect(dbPath)
    private val runs = ExposedRunRepository(database)

    @Test
    fun `R4 - upsert keeps stored detail and stream pace`() =
        runBlocking {
            runs.upsertSummaries(listOf(summary(1, NOW)))
            runs.saveDetail(ActivityId(1), ActivityDetail("note", listOf(kmLap(0, 300))), NOW)
            runs.saveStreamPace(
                ActivityId(1),
                StreamPaceResult("5x6min", StreamPaceStatus.COMPUTED, SecondsPerKm(250)),
                NOW,
            )

            runs.upsertSummaries(listOf(summary(1, NOW, name = "Renamed")))

            val stored = runs.findAll().single()
            assertEquals("Renamed", stored.name)
            assertEquals("note", stored.description)
            assertEquals(1, stored.laps?.size)
            assertEquals(SecondsPerKm(250), stored.streamPace?.pace)
        }

    @Test
    fun `R4 - runs older than six months are pruned with laps and stream paces`() =
        runBlocking {
            runs.upsertSummaries(
                listOf(
                    summary(1, Instant.parse("2026-04-28T07:00:00Z")),
                    summary(2, Instant.parse("2026-04-29T07:00:00Z")),
                ),
            )
            runs.saveDetail(ActivityId(1), ActivityDetail(null, listOf(kmLap(0, 300))), NOW)
            runs.saveStreamPace(ActivityId(1), StreamPaceResult("easy", StreamPaceStatus.UNAVAILABLE, null), NOW)
            val syncState = ExposedSyncStateRepository(database)
            syncState.save(SyncState(listCursor = NOW))

            runs.pruneBefore(LocalDate.of(2026, 4, 29))

            assertEquals(listOf(2L), runs.findAll().map { it.id.value })
            transaction(database) {
                assertTrue(RunLapsTable.selectAll().empty())
                assertTrue(RunStreamPacesTable.selectAll().empty())
            }
            assertEquals(NOW, syncState.get().listCursor)
        }

    @Test
    fun `R4 - restart reuses stored runs without re-import`() {
        val source = FakeActivitySource()
        source.activities = listOf(summary(1, NOW.minus(Duration.ofDays(2))))
        val clock = MutableClock()
        val first = SyncService(source, runs, ExposedSyncStateRepository(database), clock, TEST_ZONE)
        assertIs<SyncOutcome.Done>(runBlocking { first.syncIfDue() })

        // A new connection to the same file, as after a backend restart.
        val reopened = DatabaseFactory.connect(dbPath)
        val restarted =
            SyncService(source, ExposedRunRepository(reopened), ExposedSyncStateRepository(reopened), clock, TEST_ZONE)
        source.listAfters.clear()
        clock.advance(Duration.ofMinutes(20))
        runBlocking { restarted.syncIfDue() }

        assertEquals(1, runBlocking { ExposedRunRepository(reopened).findAll() }.size)
        assertEquals(Instant.parse("2026-10-22T10:20:00Z"), source.listAfters.single())
    }

    @Test
    fun `005 R3 - histogram round trip and pruned with its run`() =
        runBlocking {
            runs.upsertSummaries(
                listOf(
                    summary(1, Instant.parse("2026-03-18T07:00:00Z")),
                    summary(2, Instant.parse("2026-03-19T07:00:00Z")),
                ),
            )
            val histogram = HrHistogram(mapOf(160 to 1200, 140 to 1800))
            runs.saveHrHistogram(ActivityId(1), HrHistogramResult(HrHistogramStatus.COMPUTED, histogram), NOW)
            runs.saveHrHistogram(ActivityId(2), HrHistogramResult(HrHistogramStatus.COMPUTED, histogram), NOW)
            transaction(database) {
                assertEquals(
                    "140:1800,160:1200",
                    RunHrHistogramsTable.selectAll().first()[RunHrHistogramsTable.histogram],
                )
            }

            runs.pruneBefore(LocalDate.of(2026, 3, 19))

            val stored = runs.findAll().single()
            assertEquals(2L, stored.id.value)
            assertEquals(HrHistogramResult(HrHistogramStatus.COMPUTED, histogram), stored.hrHistogram)
            transaction(database) { assertEquals(1, RunHrHistogramsTable.selectAll().count().toInt()) }
            runs.deleteAll()
            transaction(database) { assertTrue(RunHrHistogramsTable.selectAll().empty()) }
        }

    @Test
    fun `005 R2 - migration adds history_start and histogram table keeping rows`() {
        val path = tempDbPath()
        val old = Database.connect("jdbc:sqlite:$path", "org.sqlite.JDBC")
        transaction(old) {
            exec(
                "CREATE TABLE sync_state (id INT PRIMARY KEY, list_cursor BIGINT NULL, last_successful_sync_at " +
                    "BIGINT NULL, rate_limited_until BIGINT NULL, last_status TEXT NULL, owner_athlete_id BIGINT NULL)",
            )
            exec("INSERT INTO sync_state (id, list_cursor) VALUES (1, ${NOW.epochSecond})")
        }

        val migrated = DatabaseFactory.connect(path)
        val state = runBlocking { ExposedSyncStateRepository(migrated).get() }

        assertEquals(NOW, state.listCursor)
        assertNull(state.historyStart)
        assertNull(state.lastAttemptAt)
        runBlocking {
            ExposedSyncStateRepository(migrated).save(state.copy(lastAttemptAt = NOW))
            assertEquals(NOW, ExposedSyncStateRepository(migrated).get().lastAttemptAt)
        }
        transaction(migrated) { assertTrue(RunHrHistogramsTable.selectAll().empty()) }
        DatabaseFactory.connect(path) // a second start must not add the column again
    }

    @Test
    fun `008 R2 - detail stores moving time and lap heart rate, summary upsert keeps them`() =
        runBlocking {
            runs.upsertSummaries(listOf(summary(1, NOW)))
            assertNull(runs.findAll().single().movingTime)

            val laps = listOf(kmLap(1, 300, heartRate = 150.0), kmLap(2, 290))
            runs.saveDetail(ActivityId(1), ActivityDetail(null, laps, Seconds(3000)), NOW)
            runs.upsertSummaries(listOf(summary(1, NOW, name = "Renamed")))

            val stored = runs.findAll().single()
            assertEquals(Seconds(3000), stored.movingTime)
            assertEquals(listOf(150.0, null), stored.laps?.map { it.averageHeartRateBpm })
        }

    @Test
    fun `008 R2 - migration adds moving_time_s and lap average_heartrate keeping rows`() {
        val path = tempDbPath()
        val old = Database.connect("jdbc:sqlite:$path", "org.sqlite.JDBC")
        transaction(old) {
            exec(
                "CREATE TABLE runs (id BIGINT PRIMARY KEY, name TEXT NOT NULL, start_date_utc BIGINT NOT NULL, " +
                    "start_date_local TEXT NOT NULL, distance_m DOUBLE PRECISION NOT NULL, average_heartrate " +
                    "DOUBLE PRECISION NULL, trainer BOOLEAN NOT NULL, description TEXT NULL, " +
                    "detail_fetched_at BIGINT NULL)",
            )
            exec(
                "CREATE TABLE run_laps (run_id BIGINT NOT NULL, lap_index INT NOT NULL, elapsed_time_s INT NOT NULL, " +
                    "distance_m DOUBLE PRECISION NOT NULL, PRIMARY KEY (run_id, lap_index))",
            )
            exec(
                "INSERT INTO runs VALUES (1, 'Old run', ${NOW.epochSecond}, '2026-10-29T07:00:00', 10000.0, 150.0, " +
                    "0, NULL, ${NOW.epochSecond})",
            )
            exec("INSERT INTO run_laps VALUES (1, 1, 300, 1000.0)")
        }

        val stored = runBlocking { ExposedRunRepository(DatabaseFactory.connect(path)).findAll() }.single()

        assertEquals("Old run", stored.name)
        assertNull(stored.movingTime)
        assertEquals(listOf<Double?>(null), stored.laps?.map { it.averageHeartRateBpm })
        DatabaseFactory.connect(path) // a second start must not add the columns again
    }

    @Test
    fun `token repository keeps at most one row`() =
        runBlocking {
            val tokens = ExposedTokenRepository(database)
            tokens.save(StoredTokens(1, "a", "r", NOW, "activity:read_all"))
            tokens.save(StoredTokens(2, "b", "s", NOW, "activity:read_all"))
            assertEquals(2, tokens.find()?.athleteId)
            tokens.delete()
            assertNull(tokens.find())
        }
}
