package app.stravadashboard.persistence

import app.stravadashboard.domain.wellness.WellnessFetchStatus
import app.stravadashboard.domain.wellness.WellnessState
import app.stravadashboard.testsupport.tempDbPath
import app.stravadashboard.testsupport.wellnessDay
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class ExposedWellnessRepositoryTest {
    private val repo = ExposedWellnessRepository(DatabaseFactory.connect(tempDbPath()))
    private val now = Instant.parse("2026-10-31T10:00:00Z")
    private val oct = (1..31).map { LocalDate.of(2026, 10, it) }

    @Test
    fun `R3 - replace range keeps days outside it and finds in date order`() =
        runBlocking {
            repo.replaceRange(oct.first(), oct.last(), oct.map { wellnessDay(it, restingHr = 50) }, now)
            repo.replaceRange(oct[9], oct[19], listOf(wellnessDay(oct[14], hrv = 60.5)), now)

            val stored = repo.findBetween(oct.first(), oct.last())
            assertEquals(21, stored.size)
            assertEquals(stored.sortedBy { it.date }, stored)
            assertEquals(wellnessDay(oct[14], hrv = 60.5), stored.single { it.date == oct[14] })
            assertEquals(listOf(oct[8], oct[14], oct[20]), repo.findBetween(oct[8], oct[20]).map { it.date })
        }

    @Test
    fun `R3 - prune removes days before the cutoff`() =
        runBlocking {
            repo.replaceRange(oct.first(), oct.last(), oct.map { wellnessDay(it, restingHr = 50) }, now)
            repo.pruneBefore(oct[10])
            assertEquals(oct[10], repo.findBetween(LocalDate.of(2000, 1, 1), LocalDate.of(2100, 1, 1)).first().date)
        }

    @Test
    fun `R3 - state round trips and defaults to empty`() =
        runBlocking {
            assertEquals(WellnessState(), repo.getState())
            val state = WellnessState(now, now, WellnessFetchStatus.RATE_LIMITED, now.plusSeconds(120))
            repo.saveState(state)
            assertEquals(state, repo.getState())
        }
}
