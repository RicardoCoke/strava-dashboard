package app.stravadashboard.domain

import app.stravadashboard.domain.model.MetersPerSecond
import app.stravadashboard.domain.model.RunType
import app.stravadashboard.domain.model.Seconds
import app.stravadashboard.domain.model.SecondsPerKm
import app.stravadashboard.domain.model.SpeedStream
import app.stravadashboard.domain.model.StreamPaceResult
import app.stravadashboard.domain.model.StreamPaceStatus
import app.stravadashboard.testsupport.kmLap
import app.stravadashboard.testsupport.run
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RepPaceTest {
    private val fiveBySix = RunType.Workout(5, 6)

    /** One sample per second; each segment is (seconds, speed). */
    private fun stream(vararg segments: Pair<Int, Double>): SpeedStream {
        val speeds = segments.flatMap { (seconds, speed) -> List(seconds) { speed } }
        return SpeedStream(speeds.indices.map(::Seconds), speeds.map(::MetersPerSecond))
    }

    @Test
    fun `R10 - treadmill paces from description are averaged`() {
        val treadmill = run(name = "5x6min", trainer = true, description = "4:15 4:10 4:05 4:10 4:00")
        assertEquals(SecondsPerKm(248), RepPaceCalculator.repPace(treadmill, fiveBySix))
    }

    @Test
    fun `R10 - pace line must contain only m ss tokens in range`() {
        assertEquals(
            listOf(255, 250),
            TreadmillPaceParser.parsePaceLine("Felt strong at 6:30\n4:15 4:10")?.map { it.value },
        )
        assertNull(TreadmillPaceParser.parsePaceLine("Rep 1 4:15, rep 2 4:10"))
        assertNull(TreadmillPaceParser.parsePaceLine("1:50 4:10"))
        assertNull(TreadmillPaceParser.parsePaceLine("12:30"))
        assertNull(TreadmillPaceParser.parsePaceLine(null))
        assertEquals(listOf(240, 245, 250), TreadmillPaceParser.parsePaceLine("  4:00 4:05   4:10 ")?.map { it.value })
    }

    @Test
    fun `R10 - treadmill without pace line has no pace`() {
        val treadmill =
            run(
                name = "5x6min",
                trainer = true,
                description = "felt good",
                laps = List(9) { kmLap(it, 250) },
                streamPace = StreamPaceResult("5x6min", StreamPaceStatus.COMPUTED, SecondsPerKm(240)),
            )
        assertNull(RepPaceCalculator.repPace(treadmill, fiveBySix))
        assertFalse(RepPaceCalculator.needsStream(treadmill))
    }

    @Test
    fun `R10 - pace line ignored on outdoor workout`() {
        val outdoor =
            run(
                name = "5x6min",
                description = "4:15 4:10",
                streamPace = StreamPaceResult("5x6min", StreamPaceStatus.COMPUTED, SecondsPerKm(250)),
            )
        assertEquals(SecondsPerKm(250), RepPaceCalculator.repPace(outdoor, fiveBySix))
    }

    @Test
    fun `R10 - stream windows find work reps`() {
        val reps = Array(5) { listOf(360 to 4.0, 120 to 2.5) }.flatMap { it }.toTypedArray()
        val speedStream = stream(600 to 3.0, *reps, 600 to 3.0)
        assertEquals(SecondsPerKm(250), StreamRepPaceFinder.find(speedStream, fiveBySix))
    }

    @Test
    fun `R10 - stream windows do not overlap`() {
        // Two fast blocks with one minute of jogging between them; overlapping windows would both sit on one block.
        val speedStream = stream(300 to 3.0, 360 to 5.0, 60 to 2.0, 360 to 4.0, 300 to 3.0)
        val pace = requireNotNull(StreamRepPaceFinder.find(speedStream, RunType.Workout(2, 6)))
        // 720 s over about 1.8 + 1.44 km -> about 222 s/km; an overlapping pick would be close to 200.
        assertTrue(pace.value in 220..225, "pace was ${pace.value}")
    }

    @Test
    fun `R10 - stream too short for N windows gives no pace`() {
        assertNull(StreamRepPaceFinder.find(stream(1500 to 4.0), fiveBySix))
        assertNull(StreamRepPaceFinder.find(SpeedStream(listOf(Seconds(0)), listOf(MetersPerSecond(3.0))), fiveBySix))
    }

    @Test
    fun `R10 - fallback uses km laps faster than median`() {
        val laps = listOf(330, 330, 330, 250, 300, 255, 300, 252, 330).mapIndexed { i, s -> kmLap(i, s) }
        assertEquals(SecondsPerKm(252), FastLapPaceFinder.find(laps))

        val outdoor =
            run(
                name = "5x6min",
                laps = laps,
                streamPace = StreamPaceResult("5x6min", StreamPaceStatus.UNAVAILABLE, null),
            )
        assertEquals(SecondsPerKm(252), RepPaceCalculator.repPace(outdoor, fiveBySix))
    }

    @Test
    fun `R10 - fallback without fast laps gives no pace`() {
        assertNull(FastLapPaceFinder.find(List(6) { kmLap(it, 300) }))
        assertNull(FastLapPaceFinder.find(listOf(kmLap(0, 300), kmLap(1, 250), kmLap(2, 100, meters = 400.0))))
    }

    @Test
    fun `R10 - pending streams give no pace yet`() {
        val pending = run(name = "5x6min", streamPace = null)
        assertNull(RepPaceCalculator.repPace(pending, fiveBySix))
        assertTrue(RepPaceCalculator.needsStream(pending))

        val renamed =
            run(name = "4x8min", streamPace = StreamPaceResult("5x6min", StreamPaceStatus.COMPUTED, SecondsPerKm(250)))
        assertNull(RepPaceCalculator.repPace(renamed, RunType.Workout(4, 8)))
        assertTrue(RepPaceCalculator.needsStream(renamed))

        assertFalse(RepPaceCalculator.needsStream(run(name = "5x6min", laps = null)))
        assertFalse(RepPaceCalculator.needsStream(run(name = "Easy")))
    }
}
