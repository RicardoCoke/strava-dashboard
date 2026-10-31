package app.stravadashboard.domain.trainingload

import app.stravadashboard.domain.model.Bpm
import app.stravadashboard.domain.model.HrHistogram
import app.stravadashboard.domain.model.HrHistogramResult
import app.stravadashboard.domain.model.HrHistogramStatus
import app.stravadashboard.domain.model.RunActivity
import app.stravadashboard.testsupport.run
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrainingLoadCalculatorTest {
    private val today = LocalDate.of(2026, 10, 31)
    private val zones = HrZones.of(Bpm(175))

    /** Load = minutes at 140 bpm (easy, weight 1) for LTHR 175. */
    private fun computed(load: Double) =
        HrHistogramResult(HrHistogramStatus.COMPUTED, HrHistogram(mapOf(140 to (load * 60).toInt())))

    private fun loadRun(
        id: Long,
        date: LocalDate,
        load: Double,
    ): RunActivity = run(id = id, date = date, hrHistogram = computed(load))

    private fun day(
        result: TrainingLoad,
        date: LocalDate,
    ): DailyLoad = result.days.single { it.date == date }

    @Test
    fun `R6 - one day per date in 6 month window`() {
        val result = TrainingLoadCalculator.compute(emptyList(), zones, today)
        assertEquals(185, result.days.size)
        assertEquals(LocalDate.of(2026, 4, 30), result.days.first().date)
        assertEquals(today, result.days.last().date)
    }

    @Test
    fun `R6 - no runs gives zeros`() {
        val result = TrainingLoadCalculator.compute(emptyList(), zones, today)
        assertTrue(result.days.all { it.load == 0.0 && it.fitness == 0.0 && it.fatigue == 0.0 && it.form == 0.0 })
    }

    @Test
    fun `R6 - daily load sums runs`() {
        val date = LocalDate.of(2026, 10, 20)
        val result =
            TrainingLoadCalculator.compute(
                listOf(loadRun(1, date, 30.0), loadRun(2, date, 12.0)),
                zones,
                today,
            )
        assertEquals(42.0, day(result, date).load, 1e-9)
    }

    @Test
    fun `R6 - single run decay example`() {
        val d = LocalDate.of(2026, 6, 1)
        val result = TrainingLoadCalculator.compute(listOf(loadRun(1, d, 84.0)), zones, today)

        with(day(result, d)) {
            assertEquals(12.0, fatigue, 1e-9)
            assertEquals(2.0, fitness, 1e-9)
            assertEquals(-10.0, form, 1e-9)
        }
        with(day(result, d.plusDays(7))) {
            assertEquals(0.0, fatigue, 1e-9)
            assertEquals(2.0, fitness, 1e-9)
            assertEquals(2.0, form, 1e-9)
        }
        assertEquals(0.0, day(result, d.plusDays(42)).fitness, 1e-9)
    }

    @Test
    fun `R6 - hard week raises fatigue faster than fitness`() {
        val start = LocalDate.of(2026, 8, 1)
        val runs =
            (0L until 42L).map { loadRun(it, start.plusDays(it), 50.0) } +
                (42L until 49L).map { loadRun(it, start.plusDays(it), 100.0) }
        val result = TrainingLoadCalculator.compute(runs, zones, today)

        with(day(result, start.plusDays(48))) {
            assertEquals(100.0, fatigue, 1e-9)
            assertEquals(58.3, fitness, 0.05)
            assertEquals(-41.7, form, 0.05)
        }
    }

    @Test
    fun `R6 - warm-up runs before the window count in fitness`() {
        val result = TrainingLoadCalculator.compute(listOf(loadRun(1, LocalDate.of(2026, 4, 1), 84.0)), zones, today)
        assertEquals(2.0, result.days.first().fitness, 1e-9)
        assertEquals(0, result.counts.counted)
    }

    @Test
    fun `R6 - buckets by local start date`() {
        val run =
            run(id = 1, startLocal = LocalDateTime.parse("2026-10-26T00:30:00"), hrHistogram = computed(10.0))
                .copy(startUtc = Instant.parse("2026-10-25T23:30:00Z"))
        val result = TrainingLoadCalculator.compute(listOf(run), zones, today)
        assertEquals(10.0, day(result, LocalDate.of(2026, 10, 26)).load, 1e-9)
    }

    @Test
    fun `R6 - skipped runs add no load`() {
        val date = LocalDate.of(2026, 10, 20)
        val runs =
            listOf(
                run(id = 1, date = date, hrHistogram = HrHistogramResult(HrHistogramStatus.UNAVAILABLE, null)),
                run(id = 2, date = date, heartRate = null, hrHistogram = computed(10.0)),
                run(id = 3, date = date),
            )
        val result = TrainingLoadCalculator.compute(runs, zones, today)
        assertEquals(0.0, day(result, date).load)
    }

    @Test
    fun `R8 - window run counts`() {
        val inWindow = LocalDate.of(2026, 9, 1)
        val before = LocalDate.of(2026, 4, 1)
        val runs =
            (1L..7L).map { loadRun(it, inWindow, 10.0) } +
                listOf(
                    run(id = 8, date = inWindow, heartRate = null),
                    run(id = 9, date = inWindow, heartRate = null),
                    run(id = 10, date = inWindow),
                ) +
                (11L..13L).map { loadRun(it, before, 10.0) }

        val counts = TrainingLoadCalculator.compute(runs, zones, today).counts

        assertEquals(RunCounts(counted = 7, skippedNoHeartRate = 2, pendingHeartRate = 1), counts)
    }
}
