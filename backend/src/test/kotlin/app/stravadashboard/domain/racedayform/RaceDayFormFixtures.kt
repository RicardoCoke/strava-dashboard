package app.stravadashboard.domain.racedayform

import app.stravadashboard.domain.model.Bpm
import app.stravadashboard.domain.model.HrHistogram
import app.stravadashboard.domain.model.HrHistogramResult
import app.stravadashboard.domain.model.HrHistogramStatus
import app.stravadashboard.domain.model.RunActivity
import app.stravadashboard.domain.model.SecondsPerKm
import app.stravadashboard.domain.trainingload.HrZones
import app.stravadashboard.domain.trainingplan.BuildCalendar
import app.stravadashboard.domain.trainingplan.BuildCalendarBuilder
import app.stravadashboard.domain.trainingplan.Kilometres
import app.stravadashboard.domain.trainingplan.PlanDay
import app.stravadashboard.domain.trainingplan.PlanWeek
import app.stravadashboard.domain.trainingplan.TargetPace
import app.stravadashboard.domain.trainingplan.TrainingPlan
import app.stravadashboard.testsupport.run
import java.time.DayOfWeek
import java.time.LocalDate

// Invented runs and plans for feature 011. Today is Saturday 2026-10-31; the plan runs 16 weeks from Monday
// 2026-08-17 to race day, Sunday 2026-12-06.

internal val TODAY_011: LocalDate = LocalDate.of(2026, 10, 31)
internal val RACE_DATE: LocalDate = LocalDate.of(2026, 12, 6)
internal val PLAN_START_011: LocalDate = LocalDate.of(2026, 8, 17)
internal val ZONES: HrZones = HrZones.of(Bpm(175))
internal val EASY_PACE = TargetPace("easy", "Easy", SecondsPerKm(338), null, null)

/** Load = minutes at 140 bpm, the easy zone for LTHR 175 (weight 1). */
internal fun loadRun(
    id: Long,
    date: LocalDate,
    load: Double,
    movingMinutes: Int = 50,
    name: String = "Morning Run",
): RunActivity =
    run(
        id = id,
        date = date,
        name = name,
        hrHistogram = HrHistogramResult(HrHistogramStatus.COMPUTED, HrHistogram(mapOf(140 to (load * 60).toInt()))),
        movingSeconds = movingMinutes * 60,
    )

/** Three easy runs of 50 moving minutes with load 60 each: an easy rate of 1.2 per minute. */
internal fun easyHistory(): List<RunActivity> = (1L..3L).map { loadRun(100 + it, TODAY_011.minusDays(it * 3), 60.0) }

internal fun planDay(
    day: DayOfWeek,
    easyMinutes: Int = 0,
    subtMinutes: Int = 0,
    km: Double = 0.0,
): PlanDay =
    PlanDay(
        dayOfWeek = day,
        session = "",
        note = null,
        easyMinutes = easyMinutes,
        subtMinutes = subtMinutes,
        distance = Kilometres(km),
        isRest = easyMinutes + subtMinutes == 0 && km == 0.0,
        race = null,
    )

/** A 16-week plan where every day is [day] (by weekday), so planned loads are easy to predict. */
internal fun calendar(
    paces: List<TargetPace> = listOf(EASY_PACE),
    weeks: Int = 16,
    day: (DayOfWeek) -> PlanDay = { planDay(it) },
): BuildCalendar {
    val week = PlanWeek("WEEK", Kilometres(0.0), 0.0, 0.0, DayOfWeek.entries.map(day))
    return BuildCalendarBuilder.build(TrainingPlan(paces, List(weeks) { week }), PLAN_START_011, TODAY_011)
}
