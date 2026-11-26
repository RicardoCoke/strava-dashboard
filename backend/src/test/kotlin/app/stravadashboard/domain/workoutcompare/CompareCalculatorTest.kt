package app.stravadashboard.domain.workoutcompare

import app.stravadashboard.domain.model.Bpm
import app.stravadashboard.domain.model.DriftStream
import app.stravadashboard.domain.model.HrDriftResult
import app.stravadashboard.domain.model.HrDriftStatus
import app.stravadashboard.domain.model.Meters
import app.stravadashboard.domain.model.MetersPerSecond
import app.stravadashboard.domain.model.Seconds
import app.stravadashboard.domain.model.SecondsPerKm
import app.stravadashboard.domain.model.Split
import app.stravadashboard.testsupport.Segment
import app.stravadashboard.testsupport.driftStream
import app.stravadashboard.testsupport.run
import app.stravadashboard.testsupport.toRunStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// All runs and streams here are invented.
class CompareCalculatorTest {
    private fun split(
        index: Int,
        meters: Double,
        seconds: Int,
        bpm: Double?,
        elevation: Double? = null,
    ) = Split(index, Meters(meters), Seconds(seconds), elevation?.let(::Meters), bpm)

    @Test
    fun `R6 - stream points in 10 second buckets`() {
        // 0..60 s at 4.0 m/s and 150 bpm.
        val points = CompareCalculator.streamPoints(driftStream(Segment(61, 4.0, 150)).toRunStream())

        assertEquals(6, points.size)
        assertEquals(ComparePoint(Meters(40.0), Seconds(10), SecondsPerKm(250), 150.0), points.first())
        assertEquals(ComparePoint(Meters(240.0), Seconds(60), SecondsPerKm(250), 150.0), points.last())
    }

    @Test
    fun `R6 - gaps for empty buckets and slow pace`() {
        // A watch pause from 20 s to 50 s, then a walk at 1.5 m/s (11:07 /km).
        val stream =
            DriftStream(
                times = (0..20).map(::Seconds) + (50..80).map(::Seconds),
                speeds = List(21) { MetersPerSecond(4.0) } + List(31) { MetersPerSecond(1.5) },
                bpm = List(52) { Bpm(150) },
                moving = null,
            ).toRunStream()

        val points = CompareCalculator.streamPoints(stream)

        assertEquals(SecondsPerKm(250), points[1].pace)
        assertNull(points[3].pace) // 30–40 s holds no sample
        assertNull(points[3].heartRate)
        assertNull(points[6].pace) // 60–70 s is walking, slower than 10:00 /km
        assertEquals(150.0, points[6].heartRate)
    }

    @Test
    fun `R6 - time weighted HR skips long gaps`() {
        val drift =
            DriftStream(
                times = listOf(0, 10, 15, 60, 70).map(::Seconds),
                speeds = List(5) { MetersPerSecond(3.0) },
                bpm = listOf(140, 160, 0, 150, 150).map(::Bpm),
                moving = null,
            )

        // 0–10 s at 140 and 10–15 s at 160; 15–60 s is a 45 s gap; 60–70 s at 150.
        assertEquals((140.0 * 10 + 160 * 5 + 150 * 10) / 25, CompareCalculator.averageBpm(drift, 0.0, 70.0))
        assertEquals(150.0, CompareCalculator.averageBpm(drift, 5.0, 15.0))
        assertNull(CompareCalculator.averageBpm(drift, 20.0, 50.0))
        assertNull(CompareCalculator.averageBpm(null, 0.0, 70.0))
    }

    @Test
    fun `R6 - split points are cumulative`() {
        val points =
            CompareCalculator.splitPoints(
                listOf(split(1, 1000.0, 290, 155.6), split(0, 1000.0, 300, 150.4), split(2, 400.0, 118, null)),
            )

        assertEquals(
            listOf(
                ComparePoint(Meters(1000.0), Seconds(300), SecondsPerKm(300), 150.4),
                ComparePoint(Meters(2000.0), Seconds(590), SecondsPerKm(290), 155.6),
                ComparePoint(Meters(2400.0), Seconds(708), SecondsPerKm(295), null),
            ),
            points,
        )
    }

    @Test
    fun `R6 - no splits gives no points`() {
        assertEquals(emptyList(), CompareCalculator.splitPoints(null))
        assertEquals(emptyList(), CompareCalculator.splitPoints(emptyList()))
    }

    @Test
    fun `R8 - stats definitions and rounding`() {
        val splits =
            listOf(
                split(0, 1000.0, 300, null, 12.4),
                split(1, 1000.0, 300, null, -8.0),
                split(2, 1000.0, 300, null, 5.3),
            )
        val stats =
            CompareCalculator.stats(
                run(distanceMeters = 10_000.0, movingSeconds = 3000, heartRate = 149.6, splits = splits),
                null,
            )

        assertEquals(SecondsPerKm(300), stats.pace)
        assertEquals(149.6, stats.averageHeartRateBpm)
        assertEquals(17.7, stats.elevationGain?.value?.let { Math.round(it * 10) / 10.0 })
        assertEquals(1.337, stats.efficiency?.let { Math.round(it * 1000) / 1000.0 })
    }

    @Test
    fun `R8 - nulls for missing HR, time or elevation`() {
        val noHr = CompareCalculator.stats(run(heartRate = null), null)
        val noTime = CompareCalculator.stats(run(movingSeconds = 0), null)
        val noDistance = CompareCalculator.stats(run(distanceMeters = 0.0), null)

        assertNull(noHr.averageHeartRateBpm)
        assertNull(noHr.efficiency)
        assertNull(noTime.pace)
        assertNull(noTime.efficiency)
        assertNull(noDistance.pace)
        assertNull(noHr.elevationGain) // no splits
        assertNull(CompareCalculator.stats(run(splits = listOf(split(0, 1000.0, 300, null))), null).elevationGain)
    }

    @Test
    fun `R8 - drift from stored value or stream`() {
        val stored = run(hrDrift = HrDriftResult(HrDriftStatus.COMPUTED, 3.84))
        val short = driftStream(Segment(45 * 60, 3.0, 150))
        val long = driftStream(Segment(40 * 60, 3.0, 140), Segment(40 * 60, 3.0, 150))

        assertEquals(3.84, CompareCalculator.stats(stored, short).driftPct)
        assertNull(CompareCalculator.stats(run(), short).driftPct) // 45 minutes is TOO_SHORT
        assertEquals(6.7, CompareCalculator.stats(run(), long).driftPct?.let { Math.round(it * 10) / 10.0 })
    }
}
