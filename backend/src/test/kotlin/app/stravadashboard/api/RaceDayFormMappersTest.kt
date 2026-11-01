package app.stravadashboard.api

import app.stravadashboard.domain.racedayform.DayKind
import app.stravadashboard.domain.racedayform.FormDay
import app.stravadashboard.domain.racedayform.ProjectionStatus
import app.stravadashboard.domain.racedayform.RaceDayForm
import app.stravadashboard.domain.racedayform.RaceDaySummary
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class RaceDayFormMappersTest {
    private val race = LocalDate.of(2026, 12, 6)

    private fun dto(
        form: Double,
        fitness: Double,
        peak: Double,
    ): RaceDayDto {
        val day = FormDay(race, DayKind.PLANNED, 0.0, fitness, fitness - form)
        val summary = RaceDaySummary(race, DayKind.PLANNED, day.form, fitness, peak, LocalDate.of(2026, 10, 18))
        val result =
            RaceDayForm(race, race, race, race, race, ProjectionStatus.PROJECTED, false, listOf(day), summary)
        return requireNotNull(result.toDto().raceDay)
    }

    @Test
    fun `R9 - from peak uses rounded values`() {
        assertEquals(-4, dto(17.8, 61.4, 65.0).fitnessFromPeak)
    }

    @Test
    fun `R9 - label formats and at peak`() {
        assertEquals("Form +18 · Fitness 61 (-4 from peak)", dto(17.8, 61.4, 65.0).label)
        assertEquals("Form -3 · Fitness 66 (+1 from peak)", dto(-3.2, 66.0, 65.0).label)
        assertEquals("Form 0 · Fitness 65 (at peak)", dto(0.3, 65.2, 64.9).label)
    }

    @Test
    fun `R10 - rounding`() {
        val day = FormDay(race, DayKind.ACTUAL, 12.34, fitness = 60.04, fatigue = 60.08)
        val result =
            RaceDayForm(race, race, race, race, race, ProjectionStatus.PROJECTED, false, listOf(day), null)
        val json = result.toDto().days.single()
        assertEquals(12.3, json.load)
        assertEquals(60.0, json.fitness)
        // -0.04 rounds to 0.0, never -0.0.
        assertEquals("0.0", json.form.toString())
        assertEquals(3.0, json.readyLow)
        assertEquals(15.0, json.readyHigh)
    }
}
