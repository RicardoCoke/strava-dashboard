package app.stravadashboard.domain.rings

import app.stravadashboard.domain.ports.RingDayRepository
import app.stravadashboard.domain.ports.WellnessRepository
import app.stravadashboard.domain.wellness.WellnessDay
import app.stravadashboard.persistence.DatabaseFactory
import app.stravadashboard.persistence.ExposedRingDayRepository
import app.stravadashboard.persistence.ExposedWellnessRepository
import app.stravadashboard.testsupport.TEST_ZONE
import app.stravadashboard.testsupport.tempDbPath
import kotlinx.coroutines.runBlocking
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class RingsServiceTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-10T10:00:00Z"), TEST_ZONE)
    private val today = LocalDate.of(2026, 11, 10)

    @Test
    fun `R5 - reads from first day minus 31`() =
        runBlocking {
            val database = DatabaseFactory.connect(tempDbPath())
            val reads = mutableListOf<Pair<LocalDate, LocalDate>>()
            val wellness =
                object : WellnessRepository by ExposedWellnessRepository(database) {
                    override suspend fun findBetween(
                        from: LocalDate,
                        to: LocalDate,
                    ): List<WellnessDay> = emptyList<WellnessDay>().also { reads += from to to }
                }
            val rings =
                object : RingDayRepository by ExposedRingDayRepository(database) {
                    override suspend fun findBetween(
                        from: LocalDate,
                        to: LocalDate,
                    ): Map<LocalDate, RingDay> = emptyMap<LocalDate, RingDay>().also { reads += from to to }
                }

            val history = RingsService(true, wellness, rings, clock, TEST_ZONE).history(90)

            val first = today.minusDays(89)
            assertEquals(first, history.first().date)
            assertEquals(listOf(first.minusDays(31) to today, first.minusDays(31) to today), reads)
        }
}
