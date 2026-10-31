package app.stravadashboard.domain

import app.stravadashboard.domain.model.RunType
import app.stravadashboard.testsupport.kmLap
import app.stravadashboard.testsupport.run
import kotlin.test.Test
import kotlin.test.assertEquals

class LapHeartRateRegressionTest {
    private val laps = listOf(kmLap(0, 300), kmLap(1, 255), kmLap(2, 250), kmLap(3, 245), kmLap(4, 310))

    @Test
    fun `008 R14 - 001 rep pace and classification unchanged with lap heart rate stored`() {
        val withHr = laps.mapIndexed { i, lap -> lap.copy(averageHeartRateBpm = 150.0 + i) }
        val workout = RunType.Workout(3, 4)
        val plain = run(name = "3x4min", laps = laps)
        val stored = run(name = "3x4min", laps = withHr)

        assertEquals(RunClassifier.classify(plain), RunClassifier.classify(stored))
        assertEquals(RepPaceCalculator.repPace(plain, workout), RepPaceCalculator.repPace(stored, workout))
        assertEquals(RepPaceCalculator.needsStream(plain), RepPaceCalculator.needsStream(stored))
    }
}
