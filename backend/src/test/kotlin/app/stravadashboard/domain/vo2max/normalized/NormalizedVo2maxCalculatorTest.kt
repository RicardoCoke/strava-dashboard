package app.stravadashboard.domain.vo2max.normalized

import app.stravadashboard.domain.model.Bpm
import app.stravadashboard.domain.model.Meters
import app.stravadashboard.domain.model.MetersPerSecond
import app.stravadashboard.domain.model.RunActivity
import app.stravadashboard.domain.model.Seconds
import app.stravadashboard.domain.model.Split
import app.stravadashboard.domain.vo2max.CurrentBasis
import app.stravadashboard.domain.vo2max.DanielsFormulas
import app.stravadashboard.domain.vo2max.RaceDistance
import app.stravadashboard.domain.vo2max.Segment
import app.stravadashboard.domain.vo2max.SegmentMethod
import app.stravadashboard.domain.vo2max.Vo2maxCalculator
import app.stravadashboard.domain.vo2max.Vo2maxEstimator
import app.stravadashboard.testsupport.run
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NormalizedVo2maxCalculatorTest {
    private val today = LocalDate.of(2026, 11, 1)
    private val maxHr = Bpm(194)

    /** An outdoor run at 5:00/km (flat splits) for [seconds], at [heartRate]. */
    private fun outdoor(
        id: Long,
        date: LocalDate,
        heartRate: Double = 150.0,
        seconds: Int = 3000,
    ): RunActivity {
        val kms = seconds / 300
        return run(
            id = id,
            date = date,
            heartRate = heartRate,
            distanceMeters = kms * 1000.0,
            movingSeconds = seconds,
            splits =
                (1..kms).map {
                    Split(
                        it,
                        Meters(1000.0),
                        Seconds(300),
                        null,
                        heartRate,
                        MetersPerSecond(
                            1000.0 / 300,
                        ),
                    )
                },
        )
    }

    private fun vo2(heartRate: Double): Double =
        assertNotNull(
            Vo2maxEstimator.estimate(Segment(today, 300.0, heartRate, Seconds(1), SegmentMethod.EARLY_SPLITS), maxHr),
        )

    private fun compute(runs: List<RunActivity>) = NormalizedVo2maxCalculator.compute(runs, maxHr, false, today)

    @Test
    fun `R4 - uses 008 estimator and configured max HR`() {
        val runs = listOf(outdoor(1, today))
        val at185 = NormalizedVo2maxCalculator.compute(runs, Bpm(185), true, today)
        val expected =
            Vo2maxEstimator.estimate(
                Segment(today, 300.0, 150.0, Seconds(1), SegmentMethod.WHOLE_RUN),
                Bpm(185),
            )
        assertEquals(assertNotNull(expected), assertNotNull(at185.current).vo2max, 1e-9)
        assertEquals(Bpm(185), at185.maxHr)
        assertTrue(at185.maxHrConfigured)
    }

    @Test
    fun `R5 - current is plain mean per run`() {
        val runs =
            listOf(
                outdoor(1, today, heartRate = 150.0, seconds = 3600),
                outdoor(2, today, heartRate = 160.0, seconds = 1800),
            )
        val current = assertNotNull(compute(runs).current)
        assertEquals((vo2(150.0) + vo2(160.0)) / 2, current.vo2max, 1e-9)
        assertEquals(2, current.runCount)
        assertEquals(CurrentBasis.LAST_7_DAYS, current.basis)
    }

    @Test
    fun `R5 - long run is one vote in its week`() {
        val runs =
            listOf(
                outdoor(1, today, heartRate = 160.0, seconds = 10_800),
                outdoor(2, today.minusDays(2), seconds = 1800),
            )
        val week = compute(runs).weeks.last()
        assertEquals(2, week.runCount)
        assertEquals((vo2(160.0) + vo2(150.0)) / 2, assertNotNull(week.vo2max), 1e-9)
    }

    @Test
    fun `R5 - falls back to 28 days or none`() {
        val fallback = assertNotNull(compute(listOf(outdoor(1, today.minusDays(20)))).current)
        assertEquals(CurrentBasis.LAST_28_DAYS, fallback.basis)
        assertEquals(LocalDate.of(2026, 10, 5), fallback.periodStart)
        assertEquals(today, fallback.periodEnd)

        val none = compute(listOf(outdoor(1, today.minusDays(40))))
        assertNull(none.current)
        assertEquals(emptyList(), none.predictions)
    }

    @Test
    fun `R5 - 27 Monday weeks`() {
        val report = compute(emptyList())
        assertEquals(LocalDate.of(2026, 4, 27), report.windowStart)
        assertEquals(today, report.windowEnd)
        assertEquals(27, report.weeks.size)
        assertEquals(listOf(true), report.weeks.filter { it.inProgress }.map { it.inProgress })
        assertTrue(report.weeks.last().inProgress)
        assertEquals(report.weeks.map { it.weekStart }.sorted(), report.weeks.map { it.weekStart })
    }

    @Test
    fun `R5 - empty week is null not zero`() {
        val week = compute(listOf(outdoor(1, today))).weeks.first()
        assertNull(week.vo2max)
        assertEquals(0, week.runCount)
        assertNull(week.originalVo2max)
    }

    @Test
    fun `R5 - original weekly value matches 008`() {
        val runs =
            listOf(
                outdoor(1, today, heartRate = 160.0, seconds = 9000),
                outdoor(2, today.minusDays(1), seconds = 1800),
                outdoor(3, today.minusDays(9)),
            )
        val original = Vo2maxCalculator.compute(runs, maxHr, false, today).weeks
        assertEquals(original.map { it.vo2max }, compute(runs).weeks.map { it.originalVo2max })
    }

    @Test
    fun `R5 - buckets by local start date`() {
        val late =
            outdoor(1, today).copy(
                startUtc = Instant.parse("2026-10-25T23:30:00Z"),
                startLocal = LocalDateTime.parse("2026-10-26T00:30:00"),
            )
        val report = compute(listOf(late))
        assertEquals(1, report.weeks.single { it.weekStart == LocalDate.of(2026, 10, 26) }.runCount)
        assertEquals(1, assertNotNull(report.current).runCount)
    }

    @Test
    fun `R6 - predictions from rounded normalized current`() {
        val report = compute(listOf(outdoor(1, today)))
        val vdot = 57.2
        assertEquals(RaceDistance.entries, report.predictions.map { it.distance })
        report.predictions.forEach { assertEquals(DanielsFormulas.raceTime(vdot, it.distance.meters), it.time) }
    }

    @Test
    fun `R7 - pending counts splits refetch`() {
        val oldDetail = outdoor(1, today).copy(splits = null)
        val treadmill = outdoor(2, today).copy(splits = null, trainer = true)
        val noHr = outdoor(3, today).copy(splits = null, averageHeartRateBpm = null)
        val reps = outdoor(4, today).copy(splits = null, name = "6x1km")
        assertEquals(1, compute(listOf(oldDetail, treadmill, noHr, reps)).runsPendingDetail)
    }
}
