package app.stravadashboard.domain.racedayform

import app.stravadashboard.domain.model.HrHistogramResult
import app.stravadashboard.domain.model.HrHistogramStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LoadRateEstimatorTest {
    private fun rates(vararg runs: app.stravadashboard.domain.model.RunActivity): LoadRates =
        assertIs<RatesResult.Learned>(LoadRateEstimator.estimate(runs.toList(), ZONES, TODAY_011)).rates

    private val easyRuns =
        listOf(50.0, 60.0, 70.0).mapIndexed { i, load -> loadRun(i + 1L, TODAY_011.minusDays(i + 1L), load) }

    @Test
    fun `R4 - easy rate per moving minute`() {
        assertEquals(1.2, rates(*easyRuns.toTypedArray()).easyPerMinute, 1e-9)
    }

    @Test
    fun `R4 - subT rate after removing easy part`() {
        val workouts = (1L..3L).map { loadRun(10 + it, TODAY_011.minusDays(10 + it), 90.0, 60, "3x10min") }
        val rates = rates(*(easyRuns + workouts).toTypedArray())
        // (270 − 1.2 × 90 easy minutes) / 90 rep minutes.
        assertEquals(1.8, rates.subtPerMinute, 1e-9)
        assertEquals(false, rates.subtEstimated)
    }

    @Test
    fun `R4 - subT fallback twice easy with flag`() {
        val twoWorkouts = (1L..2L).map { loadRun(10 + it, TODAY_011.minusDays(10 + it), 90.0, 60, "3x10min") }
        val rates = rates(*(easyRuns + twoWorkouts).toTypedArray())
        assertEquals(2.4, rates.subtPerMinute, 1e-9)
        assertTrue(rates.subtEstimated)
    }

    @Test
    fun `R4 - fewer than three easy runs no rates`() {
        val workouts = (1L..5L).map { loadRun(10 + it, TODAY_011.minusDays(it), 90.0, 60, "3x10min") }
        assertEquals(
            RatesResult.NotEnoughEasyRuns,
            LoadRateEstimator.estimate(easyRuns.take(2) + workouts, ZONES, TODAY_011),
        )
    }

    @Test
    fun `R4 - sample excludes runs without HR or moving time`() {
        val excluded =
            listOf(
                loadRun(20, TODAY_011.minusDays(2), 500.0).copy(averageHeartRateBpm = null),
                loadRun(21, TODAY_011.minusDays(2), 500.0).copy(
                    hrHistogram = HrHistogramResult(HrHistogramStatus.UNAVAILABLE, null),
                ),
                loadRun(22, TODAY_011.minusDays(2), 500.0).copy(hrHistogram = null),
                loadRun(23, TODAY_011.minusDays(2), 500.0).copy(movingTime = null),
                loadRun(24, TODAY_011.minusDays(2), 500.0, movingMinutes = 0),
                loadRun(25, TODAY_011.minusMonths(6).minusDays(1), 500.0),
            )
        assertEquals(
            RatesResult.NotEnoughEasyRuns,
            LoadRateEstimator.estimate(easyRuns.take(2) + excluded, ZONES, TODAY_011),
        )
        assertEquals(1.2, rates(*(easyRuns + excluded).toTypedArray()).easyPerMinute, 1e-9)
    }
}
