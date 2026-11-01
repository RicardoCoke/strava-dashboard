package app.stravadashboard.domain.longrundrift

import app.stravadashboard.domain.model.HrDriftResult
import app.stravadashboard.domain.model.HrDriftStatus
import app.stravadashboard.domain.model.Meters
import app.stravadashboard.testsupport.run
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// All runs here are invented.
class LongRunDriftReportTest {
    private val today = LocalDate.of(2026, 10, 31)

    private fun computed(pct: Double) = HrDriftResult(HrDriftStatus.COMPUTED, pct)

    @Suppress("LongParameterList")
    private fun longRun(
        id: Long,
        date: LocalDate,
        drift: HrDriftResult? = null,
        distanceMeters: Double = 25_000.0,
        heartRate: Double? = 148.0,
        trainer: Boolean = false,
        name: String = "Sunday long run",
    ) = run(
        id = id,
        date = date,
        name = name,
        distanceMeters = distanceMeters,
        heartRate = heartRate,
        trainer = trainer,
        hrDrift = drift,
    )

    @Test
    fun `R5 - window is last five months by local date`() {
        val report =
            LongRunDriftReport.compute(
                listOf(
                    longRun(1, LocalDate.of(2026, 5, 30), computed(3.0)),
                    longRun(2, LocalDate.of(2026, 5, 31), computed(4.0)),
                ),
                today,
            )
        assertEquals(listOf(LocalDate.of(2026, 5, 31)), report.points.map { it.date })
        assertEquals(LocalDate.of(2026, 5, 31), report.windowStart)
        assertEquals(today, report.windowEnd)
    }

    @Test
    fun `R5 - points sorted and rounded`() {
        val report =
            LongRunDriftReport.compute(
                listOf(
                    longRun(2, LocalDate.of(2026, 10, 25), computed(3.75), distanceMeters = 28_149.0),
                    longRun(1, LocalDate.of(2026, 6, 7), computed(7.249), distanceMeters = 21_100.0),
                ),
                today,
            )
        assertEquals(
            listOf(
                DriftPoint(LocalDate.of(2026, 6, 7), 7.2, DriftZone.WATCH, Meters(21_100.0)),
                DriftPoint(LocalDate.of(2026, 10, 25), 3.8, DriftZone.GOOD, Meters(28_149.0)),
            ),
            report.points,
        )
    }

    @Test
    fun `R5 - zone from rounded value with inclusive watch band`() {
        assertEquals(DriftZone.GOOD, LongRunDriftReport.zoneOf(4.9))
        assertEquals(DriftZone.WATCH, LongRunDriftReport.zoneOf(5.0))
        assertEquals(DriftZone.WATCH, LongRunDriftReport.zoneOf(10.0))
        assertEquals(DriftZone.POOR, LongRunDriftReport.zoneOf(10.1))
        assertEquals(DriftZone.GOOD, LongRunDriftReport.zoneOf(-3.2))
        // 4.96 shows as 5.0, so it is Watch.
        val point = LongRunDriftReport.compute(listOf(longRun(1, today, computed(4.96))), today).points.single()
        assertEquals(DriftZone.WATCH, point.zone)
    }

    @Test
    fun `R5 - latest and last four average`() {
        val drifts = listOf(3.0, 6.0, 4.0, 5.0, 2.0)
        val runs =
            drifts.mapIndexed {
                i,
                pct,
                ->
                longRun(i + 1L, LocalDate.of(2026, 9, 1).plusWeeks(i.toLong()), computed(pct))
            }

        val report = LongRunDriftReport.compute(runs, today)

        assertEquals(DriftPoint(LocalDate.of(2026, 9, 29), 2.0, DriftZone.GOOD, Meters(25_000.0)), report.latest)
        assertEquals(DriftAverage(4.3, DriftZone.GOOD, 4), report.lastFourAverage)
    }

    @Test
    fun `R5 - average uses fewer runs and is null with none`() {
        val two =
            LongRunDriftReport.compute(
                listOf(longRun(1, LocalDate.of(2026, 10, 1), computed(4.0)), longRun(2, today, computed(7.0))),
                today,
            )
        assertEquals(DriftAverage(5.5, DriftZone.WATCH, 2), two.lastFourAverage)

        val none = LongRunDriftReport.compute(emptyList(), today)
        assertNull(none.latest)
        assertNull(none.lastFourAverage)
    }

    @Test
    fun `R5 - excluded counts by reason and pending count`() {
        val day = LocalDate.of(2026, 10, 1)
        val runs =
            listOf(
                longRun(1, day, trainer = true),
                longRun(2, day, heartRate = null),
                longRun(3, day, HrDriftResult(HrDriftStatus.NO_DATA, null)),
                longRun(4, day, HrDriftResult(HrDriftStatus.TOO_SHORT, null)),
                longRun(5, day),
                longRun(6, day),
                longRun(7, day, computed(3.0)),
                longRun(8, day, computed(4.0)),
                longRun(9, day, computed(5.0)),
                // Not long runs: out of scope entirely.
                longRun(10, day, distanceMeters = 20_000.0),
                longRun(11, day, distanceMeters = 25_000.0, name = "5x6min"),
            )

        val report = LongRunDriftReport.compute(runs, today)

        assertEquals(DriftExcluded(treadmill = 1, tooShort = 1, missingData = 2), report.excluded)
        assertEquals(4, report.excluded.total)
        assertEquals(2, report.pendingCount)
        assertEquals(3, report.points.size)
    }

    @Test
    fun `R5 - treadmill without HR counted once as treadmill`() {
        val report = LongRunDriftReport.compute(listOf(longRun(1, today, trainer = true, heartRate = null)), today)
        assertEquals(DriftExcluded(treadmill = 1, tooShort = 0, missingData = 0), report.excluded)
    }

    @Test
    fun `R5 - run reclassified as workout leaves scope`() {
        val report = LongRunDriftReport.compute(listOf(longRun(1, today, computed(3.0), name = "4x8min")), today)
        assertEquals(emptyList(), report.points)
        assertEquals(0, report.excluded.total)
        assertEquals(0, report.pendingCount)
    }
}
