package app.stravadashboard.persistence

import app.stravadashboard.domain.SyncOutcome
import app.stravadashboard.domain.SyncService
import app.stravadashboard.domain.model.ActivityId
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
