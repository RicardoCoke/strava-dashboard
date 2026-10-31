package app.stravadashboard.api

import app.stravadashboard.domain.model.Bpm
import app.stravadashboard.domain.trainingload.HrZones
import app.stravadashboard.domain.trainingload.Ramp
import app.stravadashboard.domain.trainingload.TrainingLoadCalculator
import app.stravadashboard.domain.trainingload.TrainingLoadResult
import app.stravadashboard.domain.trainingload.WeekRamp
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TrainingLoadMappersTest {
    private val today = LocalDate.of(2026, 10, 31)
    private val zones = HrZones.of(Bpm(175))

    private fun week(changePercent: Int?) =
        WeekRamp(today, today, true, 100.0, 90.0, changePercent, previousWeekLight = false, warning = false)

    private fun labels(changePercent: Int?): Pair<String?, String?> {
        val ready =
            TrainingLoadResult.Ready(
                TrainingLoadCalculator.compute(emptyList(), zones, today),
                Ramp(week(changePercent), week(changePercent)),
                zones,
            )
        val ramp = ready.toDto().ramp
        return ramp.thisWeek.changeLabel to ramp.lastWeek.changeLabel
    }

    @Test
    fun `R7 - change labels`() {
        assertEquals("+14% vs last week" to "+14% vs the week before", labels(14))
        assertEquals("-5% vs last week" to "-5% vs the week before", labels(-5))
        assertEquals("0% vs last week" to "0% vs the week before", labels(0))
        val (thisWeek, lastWeek) = labels(null)
        assertNull(thisWeek)
        assertNull(lastWeek)
    }
}
