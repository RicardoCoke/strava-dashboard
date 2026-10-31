package app.stravadashboard.domain.vo2max

import app.stravadashboard.domain.model.Bpm
import app.stravadashboard.domain.model.RunActivity
import app.stravadashboard.domain.model.Seconds
import app.stravadashboard.testsupport.kmLap
import app.stravadashboard.testsupport.run
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Vo2maxCalculatorTest {
    private val today = LocalDate.of(2026, 10, 31)
    private val maxHr = Bpm(194)

    /** An outdoor easy run at 5:00/km: VO2max 57.16 at HR 150 (AC5.1). */
    private fun easy(
        id: Long,
        date: LocalDate,
        heartRate: Double = 150.0,
        seconds: Int = 3000,
    ): RunActivity =
        run(
            id = id,
            date = date,
            heartRate = heartRate,
            distanceMeters = seconds / 300.0 * 1000,
            movingSeconds = seconds,
        )

    private fun vo2(heartRate: Double): Double =
        Vo2maxEstimator.estimate(Segment(today, 300.0, heartRate, Seconds(1), SegmentMethod.WHOLE_RUN), maxHr) ?: 0.0

    private fun compute(
        runs: List<RunActivity>,
        on: LocalDate = today,
    ) = Vo2maxCalculator.compute(runs, maxHr, false, on)

    @Test
    fun `R6 - current from last 7 days`() {
        val current = assertNotNull(compute(listOf(easy(1, today.minusDays(6)), easy(2, today.minusDays(7)))).current)
        assertEquals(CurrentBasis.LAST_7_DAYS, current.basis)
        assertEquals(LocalDate.of(2026, 10, 25), current.periodStart)
        assertEquals(today, current.periodEnd)
        assertEquals(1, current.runCount)
        assertEquals(57.16, current.vo2max, 0.01)
    }

    @Test
    fun `R6 - duration weighted mean`() {
        val runs =
            listOf(easy(1, today, heartRate = 150.0, seconds = 3600), easy(2, today, heartRate = 160.0, seconds = 1800))
        val expected = (vo2(150.0) * 3600 + vo2(160.0) * 1800) / 5400
        assertEquals(expected, compute(runs).current?.vo2max ?: 0.0, 1e-9)
    }

    @Test
    fun `R6 - rep segment weighted by rep time`() {
        // AC4.5's 4x8min: 3300 s moving, but only the four 480 s reps (1920 s) count, at VO2max 58.2.
        val laps =
            listOf(kmLap(0, 900, 2500.0, 140.0)) +
                (1..4).flatMap { listOf(kmLap(2 * it - 1, 480, 1950.0, 170.0), kmLap(2 * it, 120, 300.0, 150.0)) }
        val workout = run(id = 2, date = today, name = "4x8min", laps = laps, movingSeconds = 3300)
        val reps = assertNotNull(SegmentExtractor.segmentOf(workout))
        val repVo2 = assertNotNull(Vo2maxEstimator.estimate(reps, maxHr))

        val current = compute(listOf(easy(1, today, seconds = 3000), workout)).current

        assertEquals((vo2(150.0) * 3000 + repVo2 * 1920) / 4920, current?.vo2max ?: 0.0, 1e-9)
    }

    @Test
    fun `R6 - falls back to last 28 days`() {
        val current = assertNotNull(compute(listOf(easy(1, today.minusDays(20)))).current)
        assertEquals(CurrentBasis.LAST_28_DAYS, current.basis)
        assertEquals(LocalDate.of(2026, 10, 4), current.periodStart)
    }

    @Test
    fun `R6 - no current without runs in 28 days`() {
        val report = compute(listOf(easy(1, today.minusDays(28))))
        assertNull(report.current)
        assertTrue(report.predictions.isEmpty())
    }

    @Test
    fun `R6 - buckets by local start date`() {
        val late =
            easy(1, today)
                .copy(
                    startLocal = LocalDateTime.parse("2026-10-25T00:30:00"),
                    startUtc = Instant.parse("2026-10-24T23:30:00Z"),
                )
        assertEquals(CurrentBasis.LAST_7_DAYS, compute(listOf(late)).current?.basis)
    }

    @Test
    fun `R7 - 27 Monday weeks for 6 months`() {
        val report = compute(emptyList())
        assertEquals(LocalDate.of(2026, 4, 27), report.windowStart)
        assertEquals(LocalDate.of(2026, 11, 1), report.windowEnd)
        assertEquals(27, report.weeks.size)
        assertEquals(
            listOf(true),
            report.weeks.filter { it.inProgress }.map {
                it.weekStart ==
                    LocalDate.of(2026, 10, 26)
            },
        )
        assertTrue(report.weeks.dropLast(1).none { it.inProgress })
    }

    @Test
    fun `R7 - weekly weighted mean and empty week is null not zero`() {
        val report = compute(listOf(easy(1, LocalDate.of(2026, 10, 20)), easy(2, LocalDate.of(2026, 10, 22))))
        val week = report.weeks.single { it.weekStart == LocalDate.of(2026, 10, 19) }
        assertEquals(2, week.runCount)
        assertEquals(57.16, week.vo2max ?: 0.0, 0.01)
        val empty = report.weeks.single { it.weekStart == LocalDate.of(2026, 10, 12) }
        assertNull(empty.vo2max)
        assertEquals(0, empty.runCount)
    }

    @Test
    fun `R7 - Monday current week in progress`() {
        val monday = LocalDate.of(2026, 11, 2)
        val week = compute(listOf(easy(1, monday)), on = monday).weeks.last()
        assertEquals(monday, week.weekStart)
        assertTrue(week.inProgress)
        assertEquals(1, week.runCount)
    }

    @Test
    fun `R8 - order and predictions from rounded value`() {
        val report = compute(listOf(easy(1, today)))
        assertEquals(RaceDistance.entries, report.predictions.map { it.distance })
        assertEquals(DanielsFormulas.raceTime(57.2, 5000.0), report.predictions.first().time)
    }

    @Test
    fun `R9 - pending detail count excludes distance reps`() {
        val runs =
            listOf(
                run(id = 1, date = today.minusDays(3), laps = null),
                run(id = 2, date = today.minusDays(3), movingSeconds = null),
                run(id = 3, date = today.minusDays(3), name = "6x1km", movingSeconds = null),
                run(id = 4, date = today.minusDays(3), heartRate = null, laps = null),
                run(id = 5, date = today.minusMonths(7), laps = null),
            )
        assertEquals(2, compute(runs).runsPendingDetail)
    }
}
