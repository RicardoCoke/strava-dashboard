package app.stravadashboard.domain.rings

import app.stravadashboard.api.toDto
import app.stravadashboard.domain.model.roundHalfUpToInt
import app.stravadashboard.testsupport.wellnessDay
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

// All data here is invented.
class RingsCalculatorTest {
    private val today = LocalDate.of(2026, 11, 9)

    private fun daysBack(n: Long) = today.minusDays(n)

    @Test
    fun `R4 - sleep need with strain and debt`() {
        val rings =
            mapOf(
                today to RingDay(25_200, null),
                daysBack(1) to RingDay(25_200, 14.0),
                daysBack(2) to RingDay(28_800, null),
            )
        // need = 28800 + 300 × 4 + 0.25 × 3600 = 30900
        val performance = requireNotNull(RingsCalculator(emptyList(), rings).sleepPerformance(today))
        assertEquals(25_200.0 / 30_900 * 100, performance)
        assertEquals(82, roundHalfUpToInt(performance))
    }

    @Test
    fun `R4 - performance capped at 100`() {
        assertEquals(100.0, RingsCalculator(emptyList(), mapOf(today to RingDay(30_600, null))).sleepPerformance(today))
    }

    @Test
    fun `R4 - no sleep today is null`() {
        val view = RingsCalculator(emptyList(), mapOf(daysBack(1) to RingDay(28_800, 9.0))).build(today)
        assertNull(view.sleep)
    }

    /** 3 baseline days at HRV 60 / resting HR 50, then [hrv] and [rhr] today, and a sleep giving 81.55 %. */
    private fun recoveryCase(
        hrv: Double?,
        rhr: Int?,
    ): Double? {
        val wellness = (1L..3L).map { wellnessDay(daysBack(it), 50, 60.0) } + wellnessDay(today, rhr, hrv)
        val rings =
            mapOf(
                today to RingDay(25_200, null),
                daysBack(1) to RingDay(25_200, 14.0),
                daysBack(2) to RingDay(28_800, null),
            )
        return RingsCalculator(wellness, rings).recovery(today)
    }

    @Test
    fun `R5 - recovery weighted components`() {
        assertEquals(75, roundHalfUpToInt(requireNotNull(recoveryCase(66.0, 48))))
    }

    @Test
    fun `R5 - missing component reweights`() {
        assertEquals(76, roundHalfUpToInt(requireNotNull(recoveryCase(66.0, null))))
    }

    @Test
    fun `R5 - no HRV means no recovery`() {
        assertNull(recoveryCase(null, 48))
        // Only 2 baseline days: no HRV baseline.
        val wellness = (1L..2L).map { wellnessDay(daysBack(it), 50, 60.0) } + wellnessDay(today, 48, 66.0)
        assertNull(RingsCalculator(wellness, emptyMap()).recovery(today))
    }

    @Test
    fun `R5 - zone thresholds`() {
        assertEquals(RecoveryZone.RED, RingsCalculator.zoneOf(33.0))
        assertEquals(RecoveryZone.YELLOW, RingsCalculator.zoneOf(33.5))
        assertEquals(RecoveryZone.YELLOW, RingsCalculator.zoneOf(66.0))
        assertEquals(RecoveryZone.GREEN, RingsCalculator.zoneOf(67.0))
    }

    @Test
    fun `R6 - 30-day baselines need 3 values`() {
        val two =
            mapOf(
                today to RingDay(null, 12.0),
                daysBack(1) to RingDay(null, 10.0),
                daysBack(2) to RingDay(null, 8.0),
            )
        assertNull(RingsCalculator(emptyList(), two).build(today).strain?.baseline)

        val three = two + (daysBack(31) to RingDay(null, 1.0)) + (daysBack(30) to RingDay(null, 6.0))
        val strain = assertNotNull(RingsCalculator(emptyList(), three).build(today).strain)
        assertEquals(12.0, strain.value)
        assertEquals(8.0, strain.baseline)
    }

    @Test
    fun `R6 - delta labels per ring`() {
        val view =
            RingsView(
                today,
                sleep = RingValue(82.4, 77.0),
                recovery = RingValue(40.0, 52.4),
                strain = RingValue(12.94, 13.7),
            )
        val dto = view.toDto()
        assertEquals("+5 pts vs 30-day avg", dto.sleep?.deltaLabel)
        assertEquals("−12 pts vs 30-day avg", dto.recovery?.deltaLabel)
        assertEquals("yellow", dto.recovery?.zone)
        assertEquals("−0.8 vs 30-day avg", dto.strain?.deltaLabel)
        assertEquals(12.9, dto.strain?.value)
        assertEquals(0.614, dto.strain?.fill)
        assertEquals(0.82, dto.sleep?.fill)

        val flat = RingsView(today, RingValue(80.0, 80.2), null, RingValue(10.0, 10.02)).toDto()
        assertEquals("0 pts vs 30-day avg", flat.sleep?.deltaLabel)
        assertEquals("0.0 vs 30-day avg", flat.strain?.deltaLabel)
        assertNull(RingsView(today, RingValue(80.0, null), null, null).toDto().sleep?.deltaLabel)
    }
}

class RingsHistoryTest {
    private val today = LocalDate.of(2026, 11, 9)

    @Test
    fun `R5 - missing values are null never zero`() {
        val rings = mapOf(today.minusDays(1) to RingDay(null, null), today to RingDay(28_800, 0.0))
        val history = RingsCalculator(emptyList(), rings).history(today.minusDays(2), today)
        assertEquals(listOf(today.minusDays(2), today.minusDays(1), today), history.map { it.date })
        // No row at all, then a row with no values: every field null.
        assertEquals(RingsHistoryDay(today.minusDays(2), null, null, null), history[0])
        assertEquals(RingsHistoryDay(today.minusDays(1), null, null, null), history[1])
    }

    @Test
    fun `R5 - real zero strain kept and today included`() {
        val history = RingsCalculator(emptyList(), mapOf(today to RingDay(28_800, 0.0))).history(today, today)
        assertEquals(RingsHistoryDay(today, 100.0, null, 0.0), history.single())
    }

    @Test
    fun `R5 - each day matches that day's rings`() {
        val wellness = (1L..4L).map { wellnessDay(today.minusDays(it), 50, 60.0) } + wellnessDay(today, 48, 66.0)
        val rings = (0L..4L).associate { today.minusDays(it) to RingDay(25_200, 10.0 + it) }
        val calculator = RingsCalculator(wellness, rings)
        for (day in calculator.history(today.minusDays(1), today)) {
            val view = calculator.build(day.date)
            assertEquals(view.sleep?.value, day.sleep)
            assertEquals(view.recovery?.value, day.recovery)
            assertEquals(view.strain?.value, day.strain)
        }
    }
}
