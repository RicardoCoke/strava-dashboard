package app.stravadashboard.domain.racedayform

import app.stravadashboard.domain.model.RunActivity
import app.stravadashboard.domain.trainingload.TrainingLoadCalculator
import app.stravadashboard.domain.trainingplan.PlanRace
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RaceDayFormProjectorTest {
    private fun project(
        runs: List<RunActivity>,
        today: LocalDate = TODAY_011,
        plan: (DayOfWeek) -> PlanDayOrRest = { PlanDayOrRest.Rest },
    ): RaceDayForm = RaceDayFormProjector.project(runs, ZONES, calendar { plan(it).toDay(it) }, today)

    /** Load 50 every day from [from] to [to], 50 moving minutes each (an easy rate of 1.0). */
    private fun dailyFifty(
        from: LocalDate,
        to: LocalDate,
    ): List<RunActivity> = from.datesUntil(to.plusDays(1)).toList().mapIndexed { i, d -> loadRun(i + 1L, d, 50.0) }

    private fun RaceDayForm.day(date: String): FormDay = days.single { it.date == LocalDate.parse(date) }

    @Test
    fun `R1 - chart from six weeks ago to plan end`() {
        val result = project(easyHistory())
        assertEquals(LocalDate.parse("2026-09-19"), result.chartStart)
        assertEquals(RACE_DATE, result.chartEnd)
        assertEquals(79, result.days.size)
        assertEquals(result.days.map { it.date }, result.chartStart.datesUntil(RACE_DATE.plusDays(1)).toList())
    }

    @Test
    fun `R1 - after race day chart ends today`() {
        val today = LocalDate.parse("2026-12-10")
        val result = project(easyHistory().map { it.copy(startLocal = it.startLocal.plusDays(40)) }, today)
        assertEquals(today, result.chartEnd)
        assertTrue(result.days.all { it.kind == DayKind.ACTUAL })
        assertEquals(DayKind.ACTUAL, assertNotNull(result.raceDay).kind)
    }

    @Test
    fun `R2 - actual days match training load`() {
        val runs = easyHistory() + loadRun(9, TODAY_011.minusDays(40), 120.0) + loadRun(8, TODAY_011, 30.0)
        val result = project(runs)
        val expected = TrainingLoadCalculator.compute(runs, ZONES, TODAY_011).days.associateBy { it.date }
        result.days.filter { it.kind == DayKind.ACTUAL }.forEach { day ->
            val load = expected.getValue(day.date)
            assertEquals(load.load, day.load, 1e-9, "${day.date}")
            assertEquals(load.fitness, day.fitness, 1e-9, "${day.date}")
            assertEquals(load.fatigue, day.fatigue, 1e-9, "${day.date}")
        }
    }

    @Test
    fun `R3 - run today makes today actual`() {
        val result = project(easyHistory() + loadRun(9, TODAY_011, 30.0).copy(averageHeartRateBpm = null))
        assertEquals(TODAY_011, result.lastActualDate)
        assertEquals(DayKind.ACTUAL, result.day("2026-10-31").kind)
        assertEquals(0.0, result.day("2026-10-31").load)
    }

    @Test
    fun `R3 - no run today makes today planned`() {
        val result = project(easyHistory()) { PlanDayOrRest.Easy(50) }
        assertEquals(TODAY_011.minusDays(1), result.lastActualDate)
        assertEquals(DayKind.PLANNED, result.day("2026-10-31").kind)
        assertEquals(60.0, result.day("2026-10-31").load, 1e-9) // 50 minutes × 1.2
        assertTrue(result.days.filter { it.date < TODAY_011 }.all { it.kind == DayKind.ACTUAL })
        assertTrue(result.days.filter { it.date > TODAY_011 }.all { it.kind == DayKind.PLANNED })
    }

    @Test
    fun `R5 - race day load excluded`() {
        val result = project(easyHistory()) { PlanDayOrRest.Easy(50) }
        assertEquals(0.0, result.day("2026-12-06").load)
        assertEquals(60.0, result.day("2026-12-05").load, 1e-9)
    }

    @Test
    fun `R6 - zero plan decays fatigue`() {
        val result = project(dailyFifty(TODAY_011.minusDays(60), TODAY_011))
        val week = result.day("2026-11-07")
        assertEquals(0.0, week.fatigue, 1e-9)
        assertEquals(50.0 * 35 / 42, week.fitness, 1e-9)
        assertEquals(50.0 * 35 / 42, week.form, 1e-9)
    }

    @Test
    fun `R6 - steady plan keeps form zero`() {
        val result = project(dailyFifty(TODAY_011.minusDays(60), TODAY_011)) { PlanDayOrRest.Easy(50) }
        val planned = result.day("2026-11-20")
        assertEquals(50.0, planned.fitness, 1e-9)
        assertEquals(50.0, planned.fatigue, 1e-9)
        assertEquals(0.0, planned.form, 1e-9)
    }

    @Test
    fun `R7 - not enough easy runs returns past only`() {
        val result = project(easyHistory().take(2)) { PlanDayOrRest.Easy(50) }
        assertEquals(ProjectionStatus.NOT_ENOUGH_RUNS, result.status)
        assertEquals(43, result.days.size)
        assertEquals(TODAY_011, result.chartEnd)
        assertEquals(TODAY_011, result.lastActualDate)
        assertTrue(result.days.all { it.kind == DayKind.ACTUAL })
        assertNull(result.raceDay)
        assertEquals(false, result.subtEstimated)
    }

    @Test
    fun `R8 - ready band from same day fitness`() {
        val day = FormDay(TODAY_011, DayKind.ACTUAL, 0.0, fitness = 60.0, fatigue = 40.0)
        assertEquals(3.0, day.readyLow, 1e-9)
        assertEquals(15.0, day.readyHigh, 1e-9)
        assertEquals(0.0, day.copy(fitness = 0.0).readyHigh)
    }

    @Test
    fun `R9 - peak over six months`() {
        val big = loadRun(9, TODAY_011.minusDays(100), 900.0)
        val summary = assertNotNull(project(easyHistory() + big).raceDay)
        assertEquals(900.0 / 42, summary.peakFitness, 1e-9)
        assertEquals(TODAY_011.minusDays(100), summary.peakDate)

        val noRuns = RaceDayFormProjector.project(emptyList(), ZONES, calendar(), RACE_DATE.plusDays(1))
        assertEquals(0.0, assertNotNull(noRuns.raceDay).peakFitness)
        assertEquals(RACE_DATE.plusDays(1).minusMonths(6), noRuns.raceDay?.peakDate)
    }

    @Test
    fun `R1 - race long before the chart has no race day`() {
        val today = RACE_DATE.plusDays(50)
        val result = project(easyHistory().map { it.copy(startLocal = it.startLocal.plusDays(50)) }, today)
        assertTrue(RACE_DATE < result.chartStart)
        assertNull(result.raceDay)
    }

    @Test
    fun `R1 - other races are normal planned days`() {
        val tenK = PlanRace(distanceKm = 10.0, isMarathon = false, label = "10K")
        val result =
            RaceDayFormProjector.project(
                easyHistory(),
                ZONES,
                calendar { day -> planDay(day, easyMinutes = 40).copy(race = tenK.takeIf { day == DayOfWeek.SUNDAY }) },
                TODAY_011,
            )
        val sundayRace = result.day("2026-11-15")
        assertEquals(DayKind.PLANNED, sundayRace.kind)
        assertEquals(48.0, sundayRace.load, 1e-9) // 40 minutes × 1.2, like any other day
        assertEquals(RACE_DATE, result.raceDate)
    }

    @Test
    fun `R2 - skipped sessions use actual load`() {
        val result = project(easyHistory()) { PlanDayOrRest.Easy(50) }
        // Every day is planned at 50 minutes, but nothing was run on 20 Oct.
        assertEquals(DayKind.ACTUAL, result.day("2026-10-20").kind)
        assertEquals(0.0, result.day("2026-10-20").load)
        assertTrue(result.days.filter { it.kind == DayKind.ACTUAL }.all { it.load == 0.0 || it.load == 60.0 })
    }

    /** A planned weekday: rest or a number of easy minutes. */
    sealed interface PlanDayOrRest {
        data object Rest : PlanDayOrRest

        data class Easy(
            val minutes: Int,
        ) : PlanDayOrRest

        fun toDay(day: DayOfWeek) =
            when (this) {
                Rest -> planDay(day)
                is Easy -> planDay(day, easyMinutes = minutes)
            }
    }
}
