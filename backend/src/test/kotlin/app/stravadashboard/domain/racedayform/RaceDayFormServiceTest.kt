package app.stravadashboard.domain.racedayform

import app.stravadashboard.config.LthrSetting
import app.stravadashboard.domain.model.Bpm
import app.stravadashboard.domain.ports.PlanFile
import app.stravadashboard.domain.trainingplan.TrainingPlanService
import app.stravadashboard.persistence.DatabaseFactory
import app.stravadashboard.persistence.ExposedRunRepository
import app.stravadashboard.testsupport.FakePlanSource
import app.stravadashboard.testsupport.PLAN_START
import app.stravadashboard.testsupport.TEST_ZONE
import app.stravadashboard.testsupport.fixedClock
import app.stravadashboard.testsupport.sixteenWeekPlanText
import app.stravadashboard.testsupport.tempDbPath
import kotlinx.coroutines.runBlocking
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Feature 011, AC10.3–AC10.5: which problem wins when settings are missing, invalid or unreadable. */
class RaceDayFormServiceTest {
    private val database = DatabaseFactory.connect(tempDbPath())
    private val runs = ExposedRunRepository(database)
    private val clock = fixedClock(Instant.parse("2026-10-31T10:00:00Z"))
    private val valid = LthrSetting.Valid(Bpm(175))
    private val validPlan = PlanFile.Content(sixteenWeekPlanText())
    private val invalidPlan = PlanFile.Content("not;a;plan")

    private fun result(
        lthr: LthrSetting,
        plan: PlanFile,
    ): RaceDayFormResult =
        runBlocking {
            val planService = TrainingPlanService(FakePlanSource(plan), PLAN_START, clock, TEST_ZONE)
            RaceDayFormService(lthr, runs, planService, clock, TEST_ZONE).get()
        }

    @Test
    fun `R10 - missing wins over invalid, and LTHR is checked before the plan`() {
        val cases =
            listOf(
                Triple(LthrSetting.NotConfigured, PlanFile.NotConfigured, RaceDayFormResult.MissingPlanAndLthr),
                Triple(valid, PlanFile.NotConfigured, RaceDayFormResult.MissingPlan),
                Triple(LthrSetting.Invalid, PlanFile.NotConfigured, RaceDayFormResult.MissingPlan),
                Triple(LthrSetting.NotConfigured, validPlan, RaceDayFormResult.MissingLthr),
                Triple(LthrSetting.NotConfigured, invalidPlan, RaceDayFormResult.MissingLthr),
                Triple(LthrSetting.NotConfigured, PlanFile.Unreadable, RaceDayFormResult.MissingLthr),
                Triple(LthrSetting.Invalid, validPlan, RaceDayFormResult.InvalidLthr),
                Triple(LthrSetting.Invalid, invalidPlan, RaceDayFormResult.InvalidLthr),
                Triple(LthrSetting.Invalid, PlanFile.Unreadable, RaceDayFormResult.InvalidLthr),
                Triple(valid, invalidPlan, RaceDayFormResult.InvalidPlan),
                Triple(valid, PlanFile.Unreadable, RaceDayFormResult.PlanUnavailable),
            )
        cases.forEach { (lthr, plan, expected) -> assertEquals(expected, result(lthr, plan), "$lthr / $plan") }
        assertIs<RaceDayFormResult.Ready>(result(valid, validPlan))
    }
}
