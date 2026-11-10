package app.stravadashboard.persistence

import app.stravadashboard.domain.model.ActivityId
import app.stravadashboard.domain.model.HrDriftResult
import app.stravadashboard.domain.model.HrDriftStatus
import app.stravadashboard.testsupport.NOW
import app.stravadashboard.testsupport.summary
import app.stravadashboard.testsupport.tempDbPath
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// All runs here are invented.
class HrDriftRepositoryTest {
    private val dbPath = tempDbPath()
    private val database = DatabaseFactory.connect(dbPath)
    private val runs = ExposedRunRepository(database)

    private fun driftRows(): Int = transaction(database) { RunHrDriftsTable.selectAll().count().toInt() }

    @Test
    fun `R8 - drift table created`() {
        runBlocking { runs.upsertSummaries(listOf(summary(1, NOW))) }
        // Reopening an existing file keeps its rows and the table.
        val reopened = ExposedRunRepository(DatabaseFactory.connect(dbPath))
        assertEquals(1, runBlocking { reopened.findAll() }.size)
        assertEquals(0, driftRows())
    }

    @Test
    fun `R8 - drift upsert keeps one row`() =
        runBlocking {
            runs.upsertSummaries(listOf(summary(1, NOW)))
            runs.saveHrDrift(ActivityId(1), HrDriftResult(HrDriftStatus.TOO_SHORT, null), NOW)
            runs.saveHrDrift(ActivityId(1), HrDriftResult(HrDriftStatus.COMPUTED, 4.76), NOW)

            assertEquals(1, driftRows())
            assertEquals(HrDriftResult(HrDriftStatus.COMPUTED, 4.76), runs.findAll().single().hrDrift)
        }

    @Test
    fun `R8 - drift rows deleted with run`() =
        runBlocking {
            runs.upsertSummaries(
                listOf(
                    summary(1, Instant.parse("2026-03-18T07:00:00Z")),
                    summary(2, Instant.parse("2026-03-19T07:00:00Z")),
                    summary(3, Instant.parse("2026-04-01T07:00:00Z")),
                ),
            )
            (1..3L).forEach { runs.saveHrDrift(ActivityId(it), HrDriftResult(HrDriftStatus.NO_DATA, null), NOW) }

            runs.pruneBefore(LocalDate.of(2026, 3, 19))
            assertEquals(2, driftRows())
            runs.delete(listOf(ActivityId(2)))
            assertEquals(1, driftRows())
        }

    @Test
    fun `R8 - findAll loads drift`() =
        runBlocking {
            runs.upsertSummaries(listOf(summary(1, NOW), summary(2, NOW)))
            runs.saveHrDrift(ActivityId(1), HrDriftResult(HrDriftStatus.COMPUTED, -1.25), NOW)

            val stored = runs.findAll().associateBy { it.id.value }
            assertEquals(HrDriftResult(HrDriftStatus.COMPUTED, -1.25), stored.getValue(1).hrDrift)
            assertNull(stored.getValue(2).hrDrift)
            transaction(database) {
                assertTrue(RunHrDriftsTable.selectAll().single()[RunHrDriftsTable.status] == "computed")
            }
        }
}
