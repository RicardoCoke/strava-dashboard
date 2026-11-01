package app.stravadashboard.domain.vo2max.normalized

import app.stravadashboard.domain.model.Meters
import app.stravadashboard.domain.model.MetersPerSecond
import app.stravadashboard.domain.model.Seconds
import app.stravadashboard.domain.model.Split
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GradeAdjustmentTest {
    private fun split(
        elevation: Double? = null,
        gap: Double? = null,
        meters: Double = 1000.0,
        seconds: Int = 300,
    ) = Split(1, Meters(meters), Seconds(seconds), elevation?.let(::Meters), 150.0, gap?.let(::MetersPerSecond))

    private fun speed(split: Split): Double = assertNotNull(GradeAdjustment.equivalentSpeed(split)).value

    @Test
    fun `R2 - Strava grade adjusted speed wins`() {
        assertEquals(3.5, speed(split(elevation = 40.0, gap = 3.5)))
    }

    @Test
    fun `R2 - uphill elevation fallback`() {
        assertEquals(1.11338, GradeAdjustment.minettiCost(0.02) / GradeAdjustment.FLAT_COST, 1e-5)
        assertEquals(3.7113, speed(split(elevation = 20.0)), 1e-4)
    }

    @Test
    fun `R2 - downhill elevation fallback`() {
        assertEquals(0.76276, GradeAdjustment.minettiCost(-0.05) / GradeAdjustment.FLAT_COST, 1e-5)
        assertEquals(2.5425, speed(split(elevation = -50.0)), 1e-4)
    }

    @Test
    fun `R2 - grade clamped to 10 percent`() {
        assertEquals(speed(split(elevation = 100.0)), speed(split(elevation = 150.0)), 1e-12)
        assertEquals(speed(split(elevation = -100.0)), speed(split(elevation = -150.0)), 1e-12)
    }

    @Test
    fun `R2 - no grade data uses plain speed`() {
        assertEquals(1000.0 / 300, speed(split()), 1e-12)
        assertEquals(1000.0 / 300, speed(split(gap = 0.0)), 1e-12)
    }

    @Test
    fun `R2 - zero time or distance unusable`() {
        assertNull(GradeAdjustment.equivalentSpeed(split(seconds = 0)))
        assertNull(GradeAdjustment.equivalentSpeed(split(meters = 0.0)))
    }
}
