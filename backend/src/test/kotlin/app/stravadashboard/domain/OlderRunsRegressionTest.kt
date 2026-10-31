package app.stravadashboard.domain

import app.stravadashboard.testsupport.TODAY
import app.stravadashboard.testsupport.kmLap
import app.stravadashboard.testsupport.run
import kotlin.test.Test
import kotlin.test.assertEquals

class OlderRunsRegressionTest {
    // Inside the 001 windows (9 weeks, 5 months, 30 + 30 days).
    private val recent =
        listOf(
            run(id = 1, date = TODAY.minusDays(3), heartRate = 142.0),
            run(id = 2, date = TODAY.minusDays(20), name = "5x6min", laps = List(5) { kmLap(it, 250) }),
            run(id = 3, date = TODAY.minusDays(45), name = "5x6min", laps = List(5) { kmLap(it, 255) }),
            run(id = 4, date = TODAY.minusMonths(4), heartRate = 150.0),
        )

    // Kept only for the training load warm-up (feature 005): older than 5 months, newer than 6 months + 41 days.
    private val older =
        listOf(
            run(id = 10, date = TODAY.minusMonths(5).minusDays(1), heartRate = 160.0),
            run(id = 11, date = TODAY.minusMonths(6).minusDays(20), name = "5x6min", laps = List(5) { kmLap(it, 240) }),
        )

    @Test
    fun `R15 - 001 aggregates unchanged with older runs stored`() {
        val withOlder = recent + older
        assertEquals(WeeklyDistance.compute(recent, TODAY), WeeklyDistance.compute(withOlder, TODAY))
        assertEquals(HeartRateTrend.compute(recent, TODAY), HeartRateTrend.compute(withOlder, TODAY))
        assertEquals(WorkoutPaceComparison.compute(recent, TODAY), WorkoutPaceComparison.compute(withOlder, TODAY))
    }
}
