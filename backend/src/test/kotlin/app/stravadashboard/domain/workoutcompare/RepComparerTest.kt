package app.stravadashboard.domain.workoutcompare

import app.stravadashboard.domain.StreamRepPaceFinder
import app.stravadashboard.domain.model.ActivityId
import app.stravadashboard.domain.model.RunType
import app.stravadashboard.domain.model.SecondsPerKm
import app.stravadashboard.domain.model.roundHalfUpToInt
import app.stravadashboard.testsupport.Segment
import app.stravadashboard.testsupport.driftStream
import app.stravadashboard.testsupport.intervalStream
import app.stravadashboard.testsupport.run
import app.stravadashboard.testsupport.toRunStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// All runs and streams here are invented.
class RepComparerTest {
    private val fiveBySix = run(id = 1, name = "5x6min threshold")
    private val eightByThree = run(id = 2, name = "8x3min hills")
    private val fiveByThree = run(id = 3, name = "5x3min")

    private fun reps(
        count: Int,
        seconds: Int,
        speed: Double = 4.0,
        bpm: Int = 170,
    ): RunStream = intervalStream(count, seconds, rep = listOf(Segment(seconds, speed, bpm))).toRunStream()

    private fun RepCell.rounded(): Pair<Int, Int?> = pace.value to heartRate?.let(::roundHalfUpToInt)

    @Test
    fun `R9 - reps are the finder windows in time order`() {
        val stream = reps(5, 360)

        val result = assertNotNull(RepComparer.compare(fiveBySix, fiveBySix.copy(id = ActivityId(9)), stream, stream))

        assertEquals(RepMode.REPS, result.mode)
        assertTrue(result.pairs.all { it.earlier?.rounded() == (250 to 170) })
        assertEquals(StreamRepPaceFinder.find(stream.speed, RunType.Workout(5, 6)), result.average?.earlier?.pace)
    }

    @Test
    fun `R9 - same count pairs rep N with rep N`() {
        val result =
            assertNotNull(
                RepComparer.compare(
                    fiveBySix,
                    fiveBySix.copy(id = ActivityId(9)),
                    reps(5, 360),
                    reps(5, 360, 4.2, 168),
                ),
            )

        assertEquals(5, result.pairs.size)
        assertTrue(result.pairs.all { it.earlier?.pace == SecondsPerKm(250) && it.later?.pace == SecondsPerKm(238) })
        assertNull(result.pieceMinutes)
        assertNull(result.piecesRunId)
    }

    @Test
    fun `R9 - extra reps listed alone`() {
        val result = assertNotNull(RepComparer.compare(fiveByThree, eightByThree, reps(5, 180), reps(8, 180)))

        assertEquals(RepMode.REPS, result.mode)
        assertEquals(8, result.pairs.size)
        assertTrue(result.pairs.take(5).all { it.earlier != null && it.later != null })
        assertTrue(result.pairs.drop(5).all { it.earlier == null && it.later != null })
    }

    @Test
    fun `R9 - average over each run's own reps`() {
        val later = reps(8, 180, 4.2, 168)
        val result = assertNotNull(RepComparer.compare(fiveByThree, eightByThree, reps(5, 180), later))

        assertEquals(StreamRepPaceFinder.find(later.speed, RunType.Workout(8, 3)), result.average?.later?.pace)
        assertEquals(
            168,
            result.average
                ?.later
                ?.heartRate
                ?.let(::roundHalfUpToInt),
        )
    }

    @Test
    fun `R9 - no HR keeps pace differences`() {
        val noHr = intervalStream(5, 180).toRunStream(withHr = false)
        val result = assertNotNull(RepComparer.compare(fiveByThree, eightByThree, noHr, reps(8, 180)))

        assertTrue(result.pairs.take(5).all { it.earlier?.heartRate == null && it.earlier?.pace == SecondsPerKm(250) })
        assertNull(result.average?.earlier?.heartRate)
    }

    @Test
    fun `R9 - no rep comparison unless both are workouts`() {
        val easy = run(id = 4, name = "Easy")
        assertNull(RepComparer.compare(fiveBySix, easy, reps(5, 360), reps(5, 360)))
        assertNull(RepComparer.compare(easy, fiveBySix, null, null))
    }

    @Test
    fun `R10 - longer reps cut into pieces from the rep start`() {
        val result = assertNotNull(RepComparer.compare(fiveBySix, eightByThree, reps(5, 360), reps(8, 180, 4.2, 168)))

        assertEquals(RepMode.PIECES, result.mode)
        assertEquals(3, result.pieceMinutes)
        assertEquals(fiveBySix.id, result.piecesRunId)
        assertTrue(result.pairs.mapNotNull { it.earlier }.all { it.rounded() == (250 to 170) })
    }

    @Test
    fun `R10 - uneven length gives one piece per rep`() {
        // 5-minute reps: 3 minutes at 4.0 m/s, then 2 at 3.0 m/s; the second part is left over and ignored.
        val fourByFive = run(id = 5, name = "4x5min")
        val sixByThree = run(id = 6, name = "6x3min")
        val longer =
            intervalStream(
                4,
                300,
                rep = listOf(Segment(180, 4.0, 170), Segment(120, 3.0, 175)),
                recovery = Segment(120, 2.0, 140),
            )

        val result = assertNotNull(RepComparer.compare(fourByFive, sixByThree, longer.toRunStream(), reps(6, 180)))

        assertEquals(6, result.pairs.size)
        assertEquals(4, result.pairs.count { it.earlier != null })
        assertTrue(result.pairs.mapNotNull { it.earlier }.all { it.pace == SecondsPerKm(250) })
    }

    @Test
    fun `R10 - piece N pairs with rep N and extras listed`() {
        val result = assertNotNull(RepComparer.compare(fiveBySix, eightByThree, reps(5, 360), reps(8, 180)))
        val swapped = assertNotNull(RepComparer.compare(eightByThree, fiveBySix, reps(8, 180), reps(5, 360)))

        assertEquals(10, result.pairs.size)
        assertTrue(result.pairs.take(8).all { it.earlier != null && it.later != null })
        assertTrue(result.pairs.drop(8).all { it.earlier != null && it.later == null })
        assertEquals(fiveBySix.id, swapped.piecesRunId)
        assertTrue(swapped.pairs.drop(8).all { it.earlier == null && it.later != null })
    }

    @Test
    fun `R10 - all pieces against all reps average`() {
        val result = assertNotNull(RepComparer.compare(fiveBySix, eightByThree, reps(5, 360), reps(8, 180, 4.2, 168)))

        assertEquals(250 to 170, result.average?.earlier?.rounded())
        assertEquals(
            StreamRepPaceFinder.find(reps(8, 180, 4.2, 168).speed, RunType.Workout(8, 3)),
            result.average?.later?.pace,
        )
    }

    @Test
    fun `R11 - splits resolution gives noDetailedData`() {
        val result = RepComparer.compare(fiveBySix, eightByThree, null, reps(8, 180))

        assertEquals(RepComparison(RepMode.UNAVAILABLE, UnavailableReason.NO_DETAILED_DATA), result)
    }

    @Test
    fun `R11 - missing windows give repsNotFound`() {
        // 20 minutes can't hold 5x6min.
        val tooShort = driftStream(Segment(1200, 4.0, 170)).toRunStream()

        val result = RepComparer.compare(fiveBySix, eightByThree, tooShort, reps(8, 180))

        assertEquals(
            RepComparison(RepMode.UNAVAILABLE, UnavailableReason.REPS_NOT_FOUND, unavailableRunId = fiveBySix.id),
            result,
        )
    }
}
