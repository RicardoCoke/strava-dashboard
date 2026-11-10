package app.stravadashboard.domain.wellness

import app.stravadashboard.domain.model.Bpm
import app.stravadashboard.domain.trainingload.DailyLoad
import app.stravadashboard.domain.trainingload.HrZones
import app.stravadashboard.domain.trainingload.Ramp
import app.stravadashboard.domain.trainingload.RunCounts
import app.stravadashboard.domain.trainingload.TrainingLoad
import app.stravadashboard.domain.trainingload.TrainingLoadResult
import app.stravadashboard.domain.trainingload.WeekRamp
import app.stravadashboard.testsupport.WELLNESS_TODAY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LoadLevelTest {
    private val today = WELLNESS_TODAY

    private fun week(warning: Boolean) = WeekRamp(today, today, true, 0.0, 0.0, null, false, warning)

    /** Form = fitness − fatigue = [form]. */
    private fun ready(
        form: Double,
        thisWeekWarning: Boolean = false,
        lastWeekWarning: Boolean = false,
    ) = TrainingLoadResult.Ready(
        TrainingLoad(today, today, listOf(DailyLoad(today, 0.0, 40.0, 40.0 - form)), RunCounts(0, 0, 0)),
        Ramp(week(thisWeekWarning), week(lastWeekWarning)),
        HrZones.of(Bpm(175)),
    )

    @Test
    fun `R7 - load high from form, this-week and last-week ramp warning`() {
        assertEquals(true, LoadLevel.isHigh(ready(form = -10.0)))
        assertEquals(false, LoadLevel.isHigh(ready(form = -9.9)))
        assertEquals(false, LoadLevel.isHigh(ready(form = 5.0)))
        assertEquals(true, LoadLevel.isHigh(ready(form = 5.0, thisWeekWarning = true)))
        assertEquals(true, LoadLevel.isHigh(ready(form = 5.0, lastWeekWarning = true)))
    }

    @Test
    fun `R7 - load high is null when training load is not ready`() {
        assertNull(LoadLevel.isHigh(TrainingLoadResult.NotConfigured))
        assertNull(LoadLevel.isHigh(TrainingLoadResult.InvalidConfig))
    }
}
