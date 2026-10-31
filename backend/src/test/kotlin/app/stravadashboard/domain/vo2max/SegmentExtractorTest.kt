package app.stravadashboard.domain.vo2max

import app.stravadashboard.domain.model.Bpm
import app.stravadashboard.domain.model.Lap
import app.stravadashboard.domain.model.RunType
import app.stravadashboard.domain.model.Seconds
import app.stravadashboard.testsupport.kmLap
import app.stravadashboard.testsupport.run
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SegmentExtractorTest {
    private fun lap(
        index: Int,
        seconds: Int,
        heartRate: Double?,
        meters: Double = 1000.0,
    ): Lap = kmLap(index, seconds, meters, heartRate)

    /** AC4.4: warm-up, five 6-min reps with 2-min recoveries, cooldown. */
    private val treadmillLaps: List<Lap> =
        listOf(lap(0, 600, 135.0)) +
            listOf(168.0, 170.0, 172.0, 172.0, 173.0).flatMapIndexed { i, hr ->
                listOf(lap(1 + 2 * i, 360, hr), lap(2 + 2 * i, 120, 150.0))
            } + lap(11, 600, 140.0)

    @Test
    fun `R3 - easy outdoor run uses whole run`() {
        val segment = assertNotNull(SegmentExtractor.segmentOf(run(heartRate = 150.0, movingSeconds = 3000)))
        assertEquals(300.0, segment.pace, 1e-9)
        assertEquals(150.0, segment.heartRateBpm)
        assertEquals(Seconds(3000), segment.duration)
        assertEquals(SegmentMethod.WHOLE_RUN, segment.method)
    }

    @Test
    fun `R3 - run without HR excluded`() {
        assertNull(SegmentExtractor.segmentOf(run(heartRate = null)))
    }

    @Test
    fun `R3 - 20 minute minimum`() {
        assertNull(SegmentExtractor.segmentOf(run(distanceMeters = 4000.0, movingSeconds = 1199)))
        assertNotNull(SegmentExtractor.segmentOf(run(distanceMeters = 4000.0, movingSeconds = 1200)))
    }

    @Test
    fun `R3 - pending refetch not used yet`() {
        assertNull(SegmentExtractor.segmentOf(run(movingSeconds = null)))
    }

    @Test
    fun `R3 - treadmill without pace line excluded`() {
        assertNull(SegmentExtractor.segmentOf(run(trainer = true, description = "Easy on the belt")))
        assertNull(SegmentExtractor.segmentOf(run(name = "5x6min", trainer = true, laps = treadmillLaps)))
    }

    @Test
    fun `R3 - reps under 4 minutes excluded`() {
        assertNull(SegmentExtractor.segmentOf(run(name = "10x1min", laps = List(10) { lap(it, 60, 170.0) })))
    }

    @Test
    fun `R3 - workout without rep laps excluded`() {
        assertNull(SegmentExtractor.segmentOf(run(name = "5x6min", laps = List(12) { lap(it, 250, 165.0) })))
    }

    @Test
    fun `R3 - implausible pace excluded`() {
        assertNull(SegmentExtractor.segmentOf(run(distanceMeters = 3000.0, movingSeconds = 3000)))
        assertNull(SegmentExtractor.segmentOf(run(distanceMeters = 0.0)))
    }

    @Test
    fun `R3 - distance rep names are excluded`() {
        listOf("6x1km", "10x400m", "5 x 1000m", "Track 8×800m", "3x2k").forEach {
            assertTrue(DistanceRepPattern.matches(it), it)
            assertNull(SegmentExtractor.segmentOf(run(name = it, movingSeconds = 3000)), it)
        }
        assertNull(SegmentExtractor.segmentOf(run(name = "6x1km", trainer = true, description = "4:00")))
    }

    @Test
    fun `R3 - time reps and 10k names are not distance reps`() {
        listOf("6x10min", "4x8min", "5x6'", "10k easy", "10k tempo").forEach {
            assertFalse(DistanceRepPattern.matches(it), it)
        }
        assertNotNull(SegmentExtractor.segmentOf(run(name = "10k easy")))
    }

    @Test
    fun `R3 - distance rep check wins over NxMmin`() {
        assertNull(SegmentExtractor.segmentOf(run(name = "2x10min + 4x1km", laps = List(2) { lap(it, 600, 170.0) })))
    }

    @Test
    fun `R3 - treadmill run with pace line counts as whole run`() {
        val treadmill = run(name = "Easy treadmill", trainer = true, description = "5:30", heartRate = 140.0)
        val segment = assertNotNull(SegmentExtractor.segmentOf(treadmill.copy(movingTime = Seconds(2700))))
        assertEquals(330.0, segment.pace, 1e-9)
        assertEquals(140.0, segment.heartRateBpm)
        assertEquals(Seconds(2700), segment.duration)
    }

    @Test
    fun `R3 - treadmill whole run uses mean pace and usual rules`() {
        val treadmill = run(name = "Easy treadmill", trainer = true, description = "5:30 5:20", movingSeconds = 2700)
        assertEquals(325.0, SegmentExtractor.segmentOf(treadmill)?.pace ?: 0.0, 1e-9)
        assertNull(SegmentExtractor.segmentOf(treadmill.copy(movingTime = Seconds(1199))))
        assertNull(SegmentExtractor.segmentOf(treadmill.copy(averageHeartRateBpm = null)))
    }

    @Test
    fun `R4 - treadmill segment uses description pace and rep HR`() {
        val treadmill =
            run(
                name = "5x6min",
                trainer = true,
                heartRate = 150.0,
                description = "4:15 4:10 4:05 4:10 4:00",
                laps = treadmillLaps,
                movingSeconds = 3600,
            )
        val segment = assertNotNull(SegmentExtractor.segmentOf(treadmill))
        assertEquals(248.0, segment.pace, 1e-9)
        assertEquals(171.0, segment.heartRateBpm, 1e-9)
        assertEquals(Seconds(1800), segment.duration)
        assertEquals(57.0, Vo2maxEstimator.estimate(segment, Bpm(194)) ?: 0.0, 0.05)
    }

    @Test
    fun `R4 - outdoor rep segment`() {
        val laps =
            listOf(lap(0, 900, 140.0, 2500.0)) +
                (1..4).flatMap { listOf(lap(2 * it - 1, 480, 170.0, 1950.0), lap(2 * it, 120, 150.0, 300.0)) }
        val segment = assertNotNull(SegmentExtractor.segmentOf(run(name = "4x8min", laps = laps, movingSeconds = 3300)))
        assertEquals(246.15, segment.pace, 0.01)
        assertEquals(170.0, segment.heartRateBpm, 1e-9)
        assertEquals(Seconds(1920), segment.duration)
        assertEquals(58.2, Vo2maxEstimator.estimate(segment, Bpm(194)) ?: 0.0, 0.05)
    }

    @Test
    fun `R4 - rep lap duration tolerance`() {
        val laps = listOf(lap(0, 305, 170.0), lap(1, 306, 170.0), lap(2, 414, 170.0), lap(3, 415, 170.0))
        assertEquals(listOf(1, 2), RepLapFinder.find(laps, RunType.Workout(5, 6), treadmill = false).map { it.index })
    }

    @Test
    fun `R4 - keeps N fastest outdoor laps and recovery laps not taken`() {
        val laps =
            (0 until 4).flatMap { listOf(lap(2 * it, 240, 172.0, 1000.0), lap(2 * it + 1, 240, 150.0, 600.0)) }
        val found = RepLapFinder.find(laps, RunType.Workout(4, 4), treadmill = false)
        assertEquals(listOf(0, 2, 4, 6), found.map { it.index })
    }

    @Test
    fun `R4 - keeps N highest HR treadmill laps, ties to the lower lap index`() {
        // Laps 1 and 3 tie at 170 bpm for the second place; lap 1 wins.
        val laps = listOf(lap(0, 360, 150.0), lap(1, 360, 170.0), lap(2, 360, 172.0), lap(3, 360, 170.0))
        assertEquals(listOf(1, 2), RepLapFinder.find(laps, RunType.Workout(2, 6), treadmill = true).map { it.index })
    }

    @Test
    fun `R4 - outdoor speed ties go to the lower lap index`() {
        val laps = listOf(lap(0, 360, 170.0, 1400.0), lap(1, 360, 170.0, 1500.0), lap(2, 360, 170.0, 1400.0))
        assertEquals(listOf(0, 1), RepLapFinder.find(laps, RunType.Workout(2, 6), treadmill = false).map { it.index })
    }

    @Test
    fun `R4 - treadmill laps without HR excluded`() {
        val laps = treadmillLaps.map { it.copy(averageHeartRateBpm = null) }
        val treadmill = run(name = "5x6min", trainer = true, description = "4:15", laps = laps, movingSeconds = 3600)
        assertNull(SegmentExtractor.segmentOf(treadmill))
    }
}
