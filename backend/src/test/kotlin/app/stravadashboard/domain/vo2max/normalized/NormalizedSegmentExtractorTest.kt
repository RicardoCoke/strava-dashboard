package app.stravadashboard.domain.vo2max.normalized

import app.stravadashboard.domain.model.Bpm
import app.stravadashboard.domain.model.Meters
import app.stravadashboard.domain.model.MetersPerSecond
import app.stravadashboard.domain.model.RunActivity
import app.stravadashboard.domain.model.Seconds
import app.stravadashboard.domain.model.Split
import app.stravadashboard.domain.vo2max.Segment
import app.stravadashboard.domain.vo2max.SegmentExtractor
import app.stravadashboard.domain.vo2max.SegmentMethod
import app.stravadashboard.domain.vo2max.Vo2maxEstimator
import app.stravadashboard.testsupport.TODAY
import app.stravadashboard.testsupport.kmLap
import app.stravadashboard.testsupport.run
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NormalizedSegmentExtractorTest {
    private val maxHr = Bpm(194)

    private fun kmSplit(
        index: Int,
        seconds: Int,
        heartRate: Double? = 145.0,
        gap: Double? = 1000.0 / seconds,
        elevation: Double? = null,
    ) = Split(index, Meters(1000.0), Seconds(seconds), elevation?.let(::Meters), heartRate, gap?.let(::MetersPerSecond))

    /** An outdoor run built from [splits]; summary HR, moving time and distance come from the splits. */
    private fun outdoor(
        splits: List<Split>?,
        heartRate: Double? = 145.0,
        name: String = "Morning Run",
    ): RunActivity =
        run(
            name = name,
            heartRate = heartRate,
            distanceMeters = splits?.sumOf { it.distance.value } ?: 10_000.0,
            movingSeconds = splits?.sumOf { it.movingTime.value } ?: 3000,
            splits = splits,
        )

    private fun segment(run: RunActivity): Segment = assertNotNull(NormalizedSegmentExtractor.segmentOf(run))

    private fun vo2(segment: Segment): Double = assertNotNull(Vo2maxEstimator.estimate(segment, maxHr))

    @Test
    fun `R3 - long run judged on first 60 minutes`() {
        val splits = (1..30).map { kmSplit(it, 330, heartRate = if (it <= 11) 145.0 else 160.0) }
        val longRun = outdoor(splits, heartRate = 154.5)

        val early = segment(longRun)

        assertEquals(3600, early.duration.value)
        assertEquals(330.0, early.pace, 1e-6)
        assertEquals(145.0, early.heartRateBpm, 1e-9)
        assertEquals(SegmentMethod.EARLY_SPLITS, early.method)
        assertEquals(54.2, vo2(early), 0.05)
        assertEquals(48.0, vo2(assertNotNull(SegmentExtractor.segmentOf(longRun))), 0.05)
    }

    @Test
    fun `R3 - easy run at same effort scores the same`() {
        val easy = segment(outdoor((1..10).map { kmSplit(it, 330) }))
        assertEquals(3300, easy.duration.value)
        assertEquals(330.0, easy.pace, 1e-6)
        assertEquals(54.2, vo2(easy), 0.05)
    }

    @Test
    fun `R3 - hilly and flat runs at same effort score the same`() {
        val flat = outdoor((1..15).map { kmSplit(it, 300, heartRate = 150.0) }, heartRate = 150.0)
        val hilly = outdoor((1..15).map { kmSplit(it, 315, heartRate = 150.0, gap = 1000.0 / 300) }, heartRate = 150.0)

        assertEquals(300.0, segment(flat).pace, 1e-6)
        assertEquals(300.0, segment(hilly).pace, 1e-6)
        assertEquals(57.2, vo2(segment(hilly)), 0.05)
        assertEquals(vo2(segment(flat)), vo2(segment(hilly)), 1e-9)
        assertEquals(57.2, vo2(assertNotNull(SegmentExtractor.segmentOf(flat))), 0.05)
        assertEquals(53.8, vo2(assertNotNull(SegmentExtractor.segmentOf(hilly))), 0.05)
    }

    @Test
    fun `R3 - run under 60 minutes uses all splits`() {
        assertEquals(2700, segment(outdoor((1..9).map { kmSplit(it, 300) })).duration.value)
    }

    @Test
    fun `R3 - exactly 60 minutes takes nothing after`() {
        val splits = (1..12).map { kmSplit(it, 300) } + (13..15).map { kmSplit(it, 300, heartRate = 190.0) }
        val early = segment(outdoor(splits))
        assertEquals(3600, early.duration.value)
        assertEquals(145.0, early.heartRateBpm, 1e-9)
    }

    @Test
    fun `R3 - no grade data scored at plain pace`() {
        val early = segment(outdoor((1..10).map { kmSplit(it, 320, gap = null) }))
        assertEquals(320.0, early.pace, 1e-6)
        assertEquals(SegmentMethod.EARLY_SPLITS, early.method)
    }

    @Test
    fun `R3 - no splits falls back to whole run`() {
        val manual = run(heartRate = 150.0, distanceMeters = 10_000.0, movingSeconds = 3000, splits = emptyList())
        assertEquals(SegmentExtractor.segmentOf(manual), NormalizedSegmentExtractor.segmentOf(manual))
    }

    @Test
    fun `R3 - splits without HR dropped or fall back`() {
        val noHr = outdoor((1..10).map { kmSplit(it, 300, heartRate = null) }, heartRate = 150.0)
        assertEquals(SegmentExtractor.segmentOf(noHr), NormalizedSegmentExtractor.segmentOf(noHr))

        val gaps = (1..10).map { kmSplit(it, 300, heartRate = if (it == 3 || it == 4) null else 150.0) }
        val partly = segment(outdoor(gaps))
        assertEquals(2400, partly.duration.value)
        assertEquals(150.0, partly.heartRateBpm, 1e-9)
    }

    @Test
    fun `R3 - workouts and treadmill follow 008`() {
        val laps =
            listOf(kmLap(0, 900, 2500.0, 140.0)) +
                (1..5).flatMap { listOf(kmLap(2 * it - 1, 360, 1450.0, 170.0), kmLap(2 * it, 90, 200.0, 150.0)) }
        val workout = run(name = "5x6min", laps = laps, movingSeconds = 3150, splits = (1..10).map { kmSplit(it, 300) })
        val treadmillWorkout =
            run(name = "5x6min", trainer = true, description = "4:15 4:10 4:10 4:05 4:05", laps = laps)
        val treadmillEasy =
            run(trainer = true, description = "5:30", movingSeconds = 5400, splits = (1..18).map { kmSplit(it, 300) })

        listOf(workout, treadmillWorkout, treadmillEasy).forEach {
            assertEquals(SegmentExtractor.segmentOf(it), NormalizedSegmentExtractor.segmentOf(it), it.name)
        }
        assertEquals(5400, segment(treadmillEasy).duration.value)
    }

    @Test
    fun `R3 - 008 exclusions still apply`() {
        assertNull(NormalizedSegmentExtractor.segmentOf(outdoor((1..10).map { kmSplit(it, 300) }, name = "6x1km")))
        assertNull(NormalizedSegmentExtractor.segmentOf(outdoor((1..10).map { kmSplit(it, 300) }, heartRate = null)))
        assertNull(NormalizedSegmentExtractor.segmentOf(run(movingSeconds = 1199, splits = emptyList())))
    }

    @Test
    fun `R3 - too short or implausible early portion excluded`() {
        val mostlyNoHr = (1..10).map { kmSplit(it, 300, heartRate = if (it <= 3) 150.0 else null) }
        assertNull(NormalizedSegmentExtractor.segmentOf(outdoor(mostlyNoHr)))

        val walking = (1..4).map { kmSplit(it, 800) }
        assertNull(NormalizedSegmentExtractor.segmentOf(outdoor(walking)))
    }

    @Test
    fun `R3 - detail from before 016 is pending`() {
        assertNull(NormalizedSegmentExtractor.segmentOf(run(date = TODAY, splits = null)))
    }
}
