package app.stravadashboard.domain

import app.stravadashboard.domain.model.RunActivity
import app.stravadashboard.domain.model.SecondsPerKm
import app.stravadashboard.domain.model.StreamPaceResult
import app.stravadashboard.domain.model.StreamPaceStatus
import app.stravadashboard.testsupport.TODAY
import app.stravadashboard.testsupport.run
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Feature 006: the "Long runs" type on the HR chart. All data is invented. */
class LongRunHeartRateTest {
    private fun date(text: String): LocalDate = LocalDate.parse(text)

    private fun longRun(
        id: Long,
        day: String,
        heartRate: Double? = 150.0,
        distanceMeters: Double = 25_000.0,
        trainer: Boolean = false,
    ) = run(id = id, date = date(day), heartRate = heartRate, distanceMeters = distanceMeters, trainer = trainer)

    private fun types(runs: List<RunActivity>): Map<String, HrTypeTrend> =
        HeartRateTrend.compute(runs, TODAY).types.associateBy { it.type.key }

    private fun keys(runs: List<RunActivity>): List<String> =
        HeartRateTrend.compute(runs, TODAY).types.map { it.type.key }

    @Test
    fun `006 R1 - manual long run without HR is counted not plotted`() {
        val long = types(listOf(longRun(1, "2026-10-01", heartRate = null))).getValue("long")
        assertEquals(1, long.runCount)
        assertEquals(1, long.runsWithoutHeartRate)
        assertEquals(emptyList(), long.points)
    }

    @Test
    fun `006 R2 - easy entry excludes runs over 20 km`() {
        val runs =
            listOf(8_000.0, 12_000.0, 20_000.0, 21_000.0, 30_000.0).mapIndexed { i, metres ->
                longRun(i.toLong(), "2026-10-0${i + 1}", distanceMeters = metres)
            }
        val types = types(runs)
        assertEquals(3, types.getValue("easy").runCount)
        assertEquals(2, types.getValue("long").runCount)
        assertEquals(listOf(date("2026-10-04"), date("2026-10-05")), types.getValue("long").points.map { it.date })
    }

    @Test
    fun `006 R2 - stored runs are reclassified on read`() {
        val runs = listOf(longRun(1, "2026-10-01"), longRun(2, "2026-10-02", distanceMeters = 9_000.0))
        assertEquals(listOf("easy", "long"), keys(runs))
    }

    @Test
    fun `006 R3 - easy then long then workouts by most recent run`() {
        val runs =
            listOf(
                run(id = 1, date = date("2026-10-01"), name = "Easy"),
                longRun(2, "2026-10-04"),
                run(id = 3, date = date("2026-10-20"), name = "5x6min"),
                run(id = 4, date = date("2026-10-25"), name = "4x8min"),
            )
        assertEquals(listOf("easy", "long", "4x8min", "5x6min"), keys(runs))
    }

    @Test
    fun `006 R3 - long first when no easy runs and absent when no long runs`() {
        val workout = run(id = 1, date = date("2026-10-20"), name = "5x6min")
        assertEquals(listOf("long", "5x6min"), keys(listOf(workout, longRun(2, "2026-10-01"))))
        assertEquals(listOf("easy", "5x6min"), keys(listOf(workout, run(id = 3, date = date("2026-10-01")))))
    }

    @Test
    fun `006 R3 - long stays second even when most recent`() {
        val runs =
            listOf(
                run(id = 1, date = date("2026-10-01"), name = "Easy"),
                run(id = 2, date = date("2026-10-10"), name = "5x6min"),
                longRun(3, "2026-10-28"),
            )
        assertEquals(listOf("easy", "long", "5x6min"), keys(runs))
    }

    @Test
    fun `006 R4 - long run window is last five months by local date`() {
        val long = types(listOf(longRun(1, "2026-05-28"), longRun(2, "2026-05-29"))).getValue("long")
        assertEquals(listOf(date("2026-05-29")), long.points.map { it.date })
    }

    @Test
    fun `006 R4 - long run points rounded and sorted`() {
        val runs =
            listOf(
                longRun(2, "2026-10-12", heartRate = 148.0, distanceMeters = 32_000.0),
                longRun(1, "2026-10-05", heartRate = 151.26, distanceMeters = 21_149.0),
            )
        val points = types(runs).getValue("long").points
        assertEquals(listOf(151.3, 148.0), points.map { it.averageHeartRateBpm })
        assertEquals(listOf(21.1, 32.0), points.map { it.distanceKm })
    }

    @Test
    fun `006 R4 - treadmill long run keeps treadmill flag`() {
        val long = types(listOf(longRun(1, "2026-10-01", trainer = true))).getValue("long")
        assertEquals(listOf(true), long.points.map { it.treadmill })
    }

    @Test
    fun `006 R4 - rising flags computed over long runs only`() {
        val longBpm = listOf(140.0, 141.0, 142.0, 150.0)
        val long = longBpm.mapIndexed { i, hr -> longRun(i.toLong(), "2026-09-0${i * 2 + 1}", heartRate = hr) }
        val easy = listOf(190.0, 100.0, 190.0).mapIndexed { i, hr -> run(id = 50L + i, heartRate = hr) }
        val points = types(long + easy).getValue("long").points
        assertEquals(HrRisingTrend.flags(longBpm), points.map { it.rising })
    }

    @Test
    fun `006 R4 - long run trend line is least squares`() {
        val runs =
            listOf("2026-06-01" to 150.0, "2026-07-01" to 146.0, "2026-08-01" to 142.0).mapIndexed { i, (day, hr) ->
                longRun(i.toLong(), day, heartRate = hr)
            }
        val long = types(runs).getValue("long")
        assertTrue(long.hasEnoughData)
        assertEquals(HrTrendLine(date("2026-06-01"), 150.0, date("2026-08-01"), 142.0, -8.0), long.trend)
    }

    @Test
    fun `006 R4 - long runs with fewer than three points have no trend`() {
        val long = types(listOf(longRun(1, "2026-10-01"), longRun(2, "2026-10-02"))).getValue("long")
        assertFalse(long.hasEnoughData)
        assertNull(long.trend)
        assertEquals(2, long.points.size)
    }

    @Test
    fun `006 R7 - long workout counts in pace comparison and long easy run does not`() {
        val pace = StreamPaceResult("3x20min", StreamPaceStatus.COMPUTED, SecondsPerKm(250))
        val runs =
            listOf(
                run(id = 1, date = date("2026-10-20"), name = "3x20min", distanceMeters = 24_000.0, streamPace = pace),
                longRun(2, "2026-10-21"),
            )
        val types = WorkoutPaceComparison.compute(runs, TODAY).types
        assertEquals(listOf("3x20min"), types.map { it.type.key })
        // Only the workout is counted: the long easy run is in no window of any entry.
        assertEquals(1, types.sumOf { (it.current?.runCount ?: 0) + (it.previous?.runCount ?: 0) })
    }
}
