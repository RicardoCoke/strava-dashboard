package app.stravadashboard.domain

import app.stravadashboard.domain.model.RunType
import app.stravadashboard.testsupport.run
import kotlin.test.Test
import kotlin.test.assertEquals

class RunClassifierTest {
    @Test
    fun `R7 - NxMmin variants normalise to one type key`() {
        listOf("5x6min", "5 x 6 min", "5X6MIN", "5x6 mins", "5×6 minutes", "5x6'", "Tuesday 5x6min session").forEach {
            val type = RunClassifier.classify(it, null)
            assertEquals(RunType.Workout(5, 6), type, it)
            assertEquals("5x6min", type.key)
            assertEquals("5x6min", type.label)
        }
    }

    @Test
    fun `R7 - bare NxM without a unit is minutes`() {
        listOf("10x3", "10 x 3", "10×3", "Tuesday 10x3 session", "10x3 @ threshold", "Did 10x3.").forEach {
            assertEquals(RunType.Workout(10, 3), RunClassifier.classify(it, null), it)
        }
        assertEquals("10x3min", RunClassifier.classify("Morning Run", "10x3").key)
    }

    @Test
    fun `R7 - bare NxM that looks like distance or seconds is easy`() {
        listOf("8x100", "6x800", "5x1 km", "3x2K", "6x1:30", "6x1.5", "10x30s", "6x20\"", "6x20''").forEach {
            assertEquals(RunType.Easy, RunClassifier.classify(it, null), it)
        }
    }

    @Test
    fun `R7 - description used when name has no pattern and name wins`() {
        assertEquals("5x6min", RunClassifier.classify("Morning Run", "Did 5x6min today").key)
        assertEquals("4x8min", RunClassifier.classify("4x8min", "5x6min").key)
    }

    @Test
    fun `R7 - non matching names are easy`() {
        listOf("Easy 10k", "Long run", "6x400m", "8x1k", "").forEach {
            val type = RunClassifier.classify(it, null)
            assertEquals(RunType.Easy, type, it)
            assertEquals("easy", type.key)
            assertEquals("Easy runs", type.label)
        }
    }

    @Test
    fun `R7 - first pattern in name wins`() {
        assertEquals("2x10min", RunClassifier.classify("2x10min + 4x1min", null).key)
    }

    @Test
    fun `R7 - run without detail classified by name`() {
        assertEquals("3x10min", RunClassifier.classify(run(name = "3x10min", description = null, laps = null)).key)
    }

    @Test
    fun `006 R1 - non-workout run over 20 km is a long run`() {
        val type = RunClassifier.classify(run(name = "Sunday long run", distanceMeters = 21_100.0))
        assertEquals(RunType.LongRun, type)
        assertEquals("long", type.key)
        assertEquals("Long runs", type.label)
    }

    @Test
    fun `006 R1 - threshold uses raw metres above 20000`() {
        assertEquals(RunType.LongRun, RunClassifier.classify(run(distanceMeters = 20_000.1)))
    }

    @Test
    fun `006 R1 - workout pattern over 20 km stays a workout`() {
        assertEquals(RunType.Workout(3, 20), RunClassifier.classify(run(name = "3x20min", distanceMeters = 24_000.0)))
        assertEquals(
            RunType.Workout(3, 20),
            RunClassifier.classify(run(description = "3x20min tempo", distanceMeters = 24_000.0)),
        )
    }

    @Test
    fun `006 R1 - treadmill run over 20 km is a long run`() {
        assertEquals(RunType.LongRun, RunClassifier.classify(run(trainer = true, distanceMeters = 22_000.0)))
    }

    @Test
    fun `006 R2 - exactly 20000 m and 19950 m are easy runs`() {
        assertEquals(RunType.Easy, RunClassifier.classify(run(distanceMeters = 20_000.0)))
        assertEquals(RunType.Easy, RunClassifier.classify(run(distanceMeters = 19_950.0)))
    }

    @Test
    fun `006 R1 - text-only classification ignores distance`() {
        assertEquals(RunType.Easy, RunClassifier.classify("Long run", null))
    }

    @Test
    fun `R7 - out of range reps or minutes are easy`() {
        listOf("0x6min", "60x1min", "5x0min").forEach {
            assertEquals(RunType.Easy, RunClassifier.classify(it, null), it)
        }
    }
}
