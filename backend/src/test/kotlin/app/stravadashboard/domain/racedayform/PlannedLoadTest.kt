package app.stravadashboard.domain.racedayform

import app.stravadashboard.domain.model.SecondsPerKm
import app.stravadashboard.domain.trainingplan.TargetPace
import java.time.DayOfWeek
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlannedLoadTest {
    private val rates = LoadRates(easyPerMinute = 1.0, subtPerMinute = 2.0, subtEstimated = false, null)

    @Test
    fun `R5 - load from easy and subT minutes`() {
        assertEquals(85.0, PlannedLoad.of(planDay(DayOfWeek.TUESDAY, 25, 30), rates, EasyPace(338.0)))
        assertEquals(60.0, PlannedLoad.of(planDay(DayOfWeek.TUESDAY, 60), rates, EasyPace(338.0)))
    }

    @Test
    fun `R5 - distance only uses easy pace`() {
        val load = PlannedLoad.of(planDay(DayOfWeek.SATURDAY, km = 10.0), rates, EasyPace(338.0))
        assertEquals(10.0 * 338 / 60, load, 1e-9)
    }

    @Test
    fun `R5 - empty day is zero`() {
        assertEquals(0.0, PlannedLoad.of(planDay(DayOfWeek.MONDAY), rates, EasyPace(338.0)))
        assertEquals(0.0, PlannedLoad.of(null, rates, EasyPace(338.0)))
        assertEquals(0.0, PlannedLoad.of(planDay(DayOfWeek.SATURDAY, km = 10.0), rates, null))
    }

    @Test
    fun `R5 - easy pace midpoint and run fallback`() {
        val range = TargetPace("easy", "Easy", null, SecondsPerKm(330), SecondsPerKm(346))
        assertEquals(EasyPace(338.0), PlannedLoad.easyPace(listOf(range), rates))
        assertEquals(EasyPace(338.0), PlannedLoad.easyPace(listOf(EASY_PACE), rates))
        val fromRuns = rates.copy(easyPace = EasyPace(30_000.0 / 90))
        assertEquals(333.33, PlannedLoad.easyPace(emptyList(), fromRuns)?.secondsPerKm ?: 0.0, 0.01)
        assertNull(PlannedLoad.easyPace(emptyList(), rates))
    }
}
