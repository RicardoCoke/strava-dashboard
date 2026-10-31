package app.stravadashboard.domain.trainingload

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RampRateTest {
    // Saturday; W0 = 2026-10-26 … 2026-11-01.
    private val today = LocalDate.of(2026, 10, 31)
    private val w0 = LocalDate.of(2026, 10, 26)

    /** One load per week, placed on that week's Monday; index 0 is W0, 1 is W-1, and so on. */
    private fun weekly(vararg loads: Double): Map<LocalDate, Double> =
        loads.withIndex().associate { (i, load) -> w0.minusWeeks(i.toLong()) to load }

    private val baseline = doubleArrayOf(300.0, 300.0, 300.0, 300.0, 300.0, 300.0, 300.0)

    @Test
    fun `R7 - this week so far vs last week`() {
        val ramp = RampRate.compute(weekly(512.0, 449.0, *baseline), today)
        with(ramp.thisWeek) {
            assertEquals(w0, weekStart)
            assertEquals(LocalDate.of(2026, 11, 1), weekEnd)
            assertTrue(weekToDate)
            assertEquals(512.0, load)
            assertEquals(449.0, previousLoad)
            assertEquals(14, changePercent)
            assertTrue(warning)
        }
        with(ramp.lastWeek) {
            assertEquals(LocalDate.of(2026, 10, 19), weekStart)
            assertFalse(weekToDate)
            assertEquals(449.0, load)
            assertEquals(300.0, previousLoad)
        }
    }

    @Test
    fun `R7 - change percent rounding and warning only above 10 percent`() {
        val ten = RampRate.compute(weekly(110.4, 100.0, 100.0, 100.0), today).thisWeek
        assertEquals(10, ten.changePercent)
        assertFalse(ten.warning)
        val eleven = RampRate.compute(weekly(110.5, 100.0, 100.0, 100.0), today).thisWeek
        assertEquals(11, eleven.changePercent)
        assertTrue(eleven.warning)
    }

    @Test
    fun `R7 - no warning after light week`() {
        val light = RampRate.compute(weekly(400.0, 140.0, *baseline), today).thisWeek
        assertEquals(186, light.changePercent)
        assertTrue(light.previousWeekLight)
        assertFalse(light.warning)

        val half = RampRate.compute(weekly(400.0, 150.0, *baseline), today).thisWeek
        assertFalse(half.previousWeekLight)
        assertTrue(half.warning)
    }

    @Test
    fun `R7 - zero previous week has no percent`() {
        val ramp = RampRate.compute(weekly(100.0, 0.0), today).thisWeek
        assertNull(ramp.changePercent)
        assertFalse(ramp.warning)
    }

    @Test
    fun `R7 - days after today are not counted`() {
        val loads = mapOf(LocalDate.of(2026, 11, 1) to 500.0, today to 20.0)
        assertEquals(20.0, RampRate.compute(loads, today).thisWeek.load)
    }

    @Test
    fun `R7 - Monday week to date`() {
        val monday = LocalDate.of(2026, 11, 2)
        val loads = mapOf(monday to 30.0, LocalDate.of(2026, 10, 27) to 200.0, LocalDate.of(2026, 11, 1) to 50.0)
        val ramp = RampRate.compute(loads, monday)
        assertEquals(30.0, ramp.thisWeek.load)
        assertEquals(250.0, ramp.lastWeek.load)
        assertEquals(w0, ramp.lastWeek.weekStart)
    }
}
