package app.stravadashboard.domain.efficiency

import app.stravadashboard.domain.model.RunActivity
import app.stravadashboard.testsupport.run
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EfficiencyFactorCalculatorTest {
    private val today = LocalDate.of(2026, 10, 31)

    private fun compute(runs: List<RunActivity>) = EfficiencyFactorCalculator.compute(runs, today)

    /** An outdoor easy run of 50 min at 125 bpm whose distance gives exactly [ef] m/min/beat. */
    private fun easy(
        id: Long,
        date: LocalDate,
        ef: Double = 1.6,
        startLocal: LocalDateTime = date.atTime(7, 0),
    ): RunActivity =
        run(
            id = id,
            date = date,
            distanceMeters = ef * HR * MINUTES,
            heartRate = HR,
            movingSeconds = MINUTES * 60,
            startLocal = startLocal,
        )

    private fun day(
        month: Int,
        dayOfMonth: Int,
    ): LocalDate = LocalDate.of(2026, month, dayOfMonth)

    @Test
    fun `R1 - easy and long outdoor runs with HR and 20+ min are included`() {
        val runs =
            listOf(
                run(id = 1, date = day(10, 20), distanceMeters = 8_000.0, movingSeconds = 2700),
                run(id = 2, date = day(10, 20), distanceMeters = 24_000.0, movingSeconds = 8000),
            )
        assertEquals(listOf(EfRunType.EASY, EfRunType.LONG), compute(runs).points.map { it.runType })
    }

    @Test
    fun `R1 - workout runs are excluded`() {
        assertTrue(compute(listOf(run(name = "5x6min", date = day(10, 20)))).points.isEmpty())
    }

    @Test
    fun `R1 - treadmill runs are excluded and not pending`() {
        val report = compute(listOf(run(trainer = true, date = day(10, 20)), run(trainer = true, laps = null)))
        assertTrue(report.points.isEmpty())
        assertEquals(0, report.runsPendingDetail)
    }

    @Test
    fun `R1 - runs without average HR are excluded and not pending`() {
        val report = compute(listOf(run(heartRate = null, date = day(10, 20)), run(heartRate = null, laps = null)))
        assertTrue(report.points.isEmpty())
        assertEquals(0, report.runsPendingDetail)
    }

    @Test
    fun `R1 - moving time 1199 s excluded, 1200 s included`() {
        val runs = listOf(run(id = 1, movingSeconds = 1199), run(id = 2, movingSeconds = 1200))
        assertEquals(listOf(1200), compute(runs).points.map { it.movingTime.value })
    }

    @Test
    fun `R1 - zero distance or zero moving time is excluded`() {
        assertTrue(compute(listOf(run(id = 1, distanceMeters = 0.0), run(id = 2, movingSeconds = 0))).points.isEmpty())
    }

    @Test
    fun `R1 - only runs from today minus 6 months to today are plotted`() {
        val report =
            compute(listOf(easy(1, day(4, 29)), easy(2, day(4, 30)), easy(3, day(10, 31)), easy(4, day(11, 1))))
        assertEquals(day(4, 30), report.windowStart)
        assertEquals(today, report.windowEnd)
        assertEquals(listOf(day(4, 30), day(10, 31)), report.points.map { it.date })
    }

    @Test
    fun `R1 - point date uses start_date_local`() {
        val late = LocalDateTime.of(2026, 5, 1, 23, 30)
        // 23:30 local at UTC-2 is already 2 May in UTC.
        val run = easy(1, day(5, 1), startLocal = late).copy(startUtc = Instant.parse("2026-05-02T01:30:00Z"))
        assertEquals(day(5, 1), compute(listOf(run)).points.single().date)
    }

    @Test
    fun `R1 - two runs on one day are two points in start order`() {
        val runs =
            listOf(
                easy(1, day(10, 20), ef = 1.7, startLocal = day(10, 20).atTime(18, 0)),
                easy(2, day(10, 20), ef = 1.5, startLocal = day(10, 20).atTime(7, 0)),
            )
        assertEquals(listOf(1.5, 1.7), compute(runs).points.map { round3(it.efficiency) })
    }

    @Test
    fun `R2 - efficiency is metres per minute per beat rounded to 3 decimals`() {
        val runs =
            listOf(
                run(id = 1, distanceMeters = 10_000.0, movingSeconds = 3000, heartRate = 125.0),
                run(id = 2, distanceMeters = 12_345.0, movingSeconds = 3725, heartRate = 141.3, date = day(10, 30)),
            )
        assertEquals(listOf(1.6, 1.407), compute(runs).points.map { round3(it.efficiency) })
    }

    @Test
    fun `R2 - point carries distance km, moving time, pace and HR`() {
        val point =
            compute(
                listOf(run(distanceMeters = 10_000.0, movingSeconds = 3000, heartRate = 125.0)),
            ).points.single()
        assertEquals(10_000.0, point.distance.value)
        assertEquals(3000, point.movingTime.value)
        assertEquals(300, point.pace.value)
        assertEquals(125.0, point.averageHeartRateBpm)
    }

    @Test
    fun `R3 - rolling average needs 3 runs`() {
        val points =
            compute(
                listOf(easy(1, day(10, 1), 1.5), easy(2, day(10, 10), 1.6), easy(3, day(10, 20), 1.7)),
            ).points
        assertEquals(listOf(null, null, 1.6), points.map { it.rollingEfficiency?.let(::round3) })
    }

    @Test
    fun `R3 - rolling set is the 28 days ending on the run date`() {
        val runs =
            listOf(
                easy(1, day(9, 20), 1.4),
                easy(2, day(10, 10), 1.6),
                easy(3, day(10, 15), 1.6),
                easy(4, day(10, 18), 1.7),
            )
        val last = compute(runs).points.last()
        assertEquals(1.633, last.rollingEfficiency?.let(::round3))
        assertEquals(3, last.rollingRunCount)
    }

    @Test
    fun `R3 - runs before the window count towards the first rolling value`() {
        val runs = listOf(easy(1, day(4, 10)), easy(2, day(4, 20)), easy(3, day(4, 29)), easy(4, day(5, 1)))
        val first = compute(runs).points.first()
        assertEquals(day(5, 1), first.date)
        assertEquals(4, first.rollingRunCount)
        assertNotNull(first.rollingEfficiency)
    }

    @Test
    fun `R3 - non-qualifying runs never count towards rolling`() {
        val runs =
            listOf(
                easy(1, day(10, 1)),
                run(id = 2, date = day(10, 2), trainer = true),
                run(id = 3, date = day(10, 3), heartRate = null),
                run(id = 4, date = day(10, 4), movingSeconds = 600),
                run(id = 5, date = day(10, 5), name = "4x8min"),
                easy(6, day(10, 6)),
            )
        val last = compute(runs).points.last()
        assertEquals(2, last.rollingRunCount)
        assertNull(last.rollingEfficiency)
    }

    @Test
    fun `R3 - same-day runs roll in start order`() {
        val date = day(10, 20)
        val runs =
            listOf(
                easy(1, day(10, 10)),
                easy(2, date, startLocal = date.atTime(18, 0)),
                easy(3, date, startLocal = date.atTime(7, 0)),
            )
        assertEquals(listOf(1, 2, 3), compute(runs).points.map { it.rollingRunCount })
    }

    @Test
    fun `R4 - current is the latest rolling value within 28 days`() {
        fun lastOn(date: LocalDate) =
            compute(listOf(easy(1, date.minusDays(10)), easy(2, date.minusDays(5)), easy(3, date))).current

        val current = assertNotNull(lastOn(day(10, 29)))
        assertEquals(1.6, round3(current.efficiency))
        assertEquals(day(10, 29), current.date)
        assertEquals(3, current.runCount)
        assertNull(lastOn(day(10, 3)))
        assertNotNull(lastOn(day(10, 4)))
        assertNull(compute(listOf(easy(1, day(10, 20)), easy(2, day(10, 29)))).current)
        assertNull(compute(emptyList()).current)
    }

    @Test
    fun `R5 - change percent from first rolling value to current`() {
        val rising = threeRunsEndingOn(day(5, 3), 1.585) + threeRunsEndingOn(day(10, 29), 1.652, firstId = 10)
        val change = assertNotNull(compute(rising).change)
        assertEquals(4.227, change.percent, 0.001)
        assertEquals(day(5, 3), change.from.date)
        assertEquals(day(10, 29), change.to.date)

        val falling = threeRunsEndingOn(day(5, 3), 1.7) + threeRunsEndingOn(day(10, 29), 1.615, firstId = 10)
        assertEquals(-5.0, assertNotNull(compute(falling).change).percent, 1e-9)
    }

    @Test
    fun `R5 - no change without current or with under 28 days between`() {
        assertNull(compute(threeRunsEndingOn(day(9, 1), 1.6)).change)
        val shortSpan = threeRunsEndingOn(day(10, 5), 1.6) + listOf(easy(10, day(10, 29)))
        assertNotNull(compute(shortSpan).current)
        assertNull(compute(shortSpan).change)
        val exactSpan = threeRunsEndingOn(day(10, 1), 1.6) + threeRunsEndingOn(day(10, 29), 1.6, firstId = 10)
        assertNotNull(compute(exactSpan).change)
    }

    @Test
    fun `R6 - pending counts easy and long outdoor runs with HR but no moving time`() {
        val runs =
            listOf(
                run(id = 1, laps = null),
                run(id = 2, distanceMeters = 25_000.0, movingSeconds = null),
                run(id = 3, laps = null, trainer = true),
                run(id = 4, laps = null, heartRate = null),
                run(id = 5, laps = null, name = "5x6min"),
                run(id = 6, laps = null, date = day(4, 29)),
            )
        val report = compute(runs)
        assertEquals(2, report.runsPendingDetail)
        assertTrue(report.points.isEmpty())
    }

    /** Three runs with the same [ef] on the three days ending on [date], so its rolling value is [ef]. */
    private fun threeRunsEndingOn(
        date: LocalDate,
        ef: Double,
        firstId: Long = 1,
    ): List<RunActivity> = (0L..2L).map { easy(firstId + it, date.minusDays(2 - it), ef) }

    private fun round3(value: Double): Double = BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP).toDouble()

    private companion object {
        const val HR = 125.0
        const val MINUTES = 50
    }
}
