package app.stravadashboard.domain.marathonpace

import app.stravadashboard.domain.WeekComparison
import app.stravadashboard.domain.WeeklyDistance
import app.stravadashboard.domain.model.METERS_PER_KM
import app.stravadashboard.domain.model.RunActivity
import app.stravadashboard.domain.model.SecondsPerKm
import app.stravadashboard.testsupport.kmLap
import app.stravadashboard.testsupport.run
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// All runs and laps here are invented.
class MarathonPaceVolumeTest {
    private val goal = MpGoal(SecondsPerKm(255))
    private val buildStart = LocalDate.of(2026, 8, 17)

    /** Saturday 2026-10-31, in week 11 of the build. */
    private val today = LocalDate.of(2026, 10, 31)

    private fun compute(
        vararg runs: RunActivity,
        on: LocalDate = today,
        start: LocalDate = buildStart,
    ): MpReport = MarathonPaceVolume.compute(runs.toList(), goal, start, on)

    /** A run with one lap of [mpMeters] at (about) goal pace. */
    private fun mpRun(
        id: Long,
        date: LocalDate,
        mpMeters: Double,
        distance: Double = mpMeters,
    ): RunActivity {
        val seconds = (mpMeters * goal.pace.value / METERS_PER_KM).roundToInt()
        return run(id = id, date = date, distanceMeters = distance, laps = listOf(kmLap(1, seconds, mpMeters)))
    }

    @Test
    fun `R2 - pace edges are inclusive`() {
        assertTrue(MarathonPaceVolume.isMpLap(kmLap(1, 245), goal))
        assertTrue(MarathonPaceVolume.isMpLap(kmLap(2, 265), goal))
        assertFalse(MarathonPaceVolume.isMpLap(kmLap(3, 244), goal))
        assertFalse(MarathonPaceVolume.isMpLap(kmLap(4, 266), goal))
        val both = run(laps = listOf(kmLap(1, 245), kmLap(2, 265)))
        assertEquals(2000.0, MarathonPaceVolume.mpMeters(both, goal))
    }

    @Test
    fun `R2 - laps under 200 m are excluded`() {
        assertTrue(MarathonPaceVolume.isMpLap(kmLap(1, 51, 200.0), goal))
        assertFalse(MarathonPaceVolume.isMpLap(kmLap(1, 51, 199.9), goal))
    }

    @Test
    fun `R2 - pace check is exact`() {
        assertTrue(MarathonPaceVolume.isMpLap(kmLap(1, 265, 1000.0), goal))
        assertFalse(MarathonPaceVolume.isMpLap(kmLap(1, 265, 999.0), goal))
    }

    @Test
    fun `R2 - treadmill laps add nothing`() {
        val treadmill = run(trainer = true, laps = List(5) { kmLap(it, 255) })
        assertEquals(0.0, MarathonPaceVolume.mpMeters(treadmill, goal))
    }

    @Test
    fun `R2 - zero distance or zero time laps are ignored`() {
        assertFalse(MarathonPaceVolume.isMpLap(kmLap(1, 0, 1000.0), goal))
        assertFalse(MarathonPaceVolume.isMpLap(kmLap(1, 255, 0.0), goal))
    }

    @Test
    fun `R2 - only MP laps of a long run count`() {
        val laps = List(10) { kmLap(it, 300) } + List(8) { kmLap(10 + it, 255) } + List(3) { kmLap(18 + it, 250) }
        assertEquals(11_000.0, MarathonPaceVolume.mpMeters(run(distanceMeters = 21_000.0, laps = laps), goal))
    }

    @Test
    fun `R2 - runs without laps add nothing`() {
        assertEquals(0.0, MarathonPaceVolume.mpMeters(run(laps = null), goal))
        assertEquals(0.0, MarathonPaceVolume.mpMeters(run(laps = emptyList()), goal))
        assertEquals(0.0, MarathonPaceVolume.mpMeters(run(sportType = "Ride", laps = listOf(kmLap(1, 255))), goal))
    }

    @Test
    fun `R3 - sixteen weeks from the build start`() {
        val report = compute()
        assertEquals(16, report.weeks.size)
        assertEquals((1..16).toList(), report.weeks.map { it.index })
        assertEquals(LocalDate.of(2026, 8, 17), report.weeks.first().weekStart)
        assertEquals(LocalDate.of(2026, 12, 6), report.weeks.last().weekEnd)
        assertEquals(LocalDate.of(2026, 8, 17), report.buildStart)
        assertEquals(LocalDate.of(2026, 12, 6), report.buildEnd)
        report.weeks.forEach { assertEquals(it.weekStart.plusDays(6), it.weekEnd) }
        assertTrue(report.weeks.all { it.mpKm == 0.0 })

        val later = compute(start = LocalDate.of(2026, 9, 7))
        assertEquals(LocalDate.of(2026, 9, 7), later.weeks.first().weekStart)
        assertEquals(LocalDate.of(2026, 12, 27), later.weeks.last().weekEnd)
    }

    @Test
    fun `R3 - current and future week flags`() {
        val weeks = compute(mpRun(1, LocalDate.of(2026, 10, 27), 5000.0)).weeks
        val current = weeks.single { it.isCurrentWeek }
        assertEquals(11, current.index)
        assertEquals(LocalDate.of(2026, 10, 26), current.weekStart)
        assertFalse(current.isFuture)
        assertTrue(weeks.filter { it.index >= 12 }.all { it.isFuture && it.mpKm == 0.0 })
        assertTrue(weeks.filter { it.index <= 10 }.none { it.isFuture || it.isCurrentWeek })
    }

    @Test
    fun `R3 - week km summed in metres then rounded`() {
        val monday = LocalDate.of(2026, 8, 24)
        val report = compute(mpRun(1, monday, 3040.0), mpRun(2, monday.plusDays(2), 2010.0))
        assertEquals(5.1, report.weeks[1].mpKm)
    }

    @Test
    fun `R3 - buckets by local start date`() {
        val lateSunday =
            run(
                id = 1,
                startLocal = LocalDateTime.of(2026, 8, 23, 23, 30),
                laps = listOf(kmLap(1, 255)),
            )
        val weeks = compute(lateSunday).weeks
        assertEquals(1.0, weeks[0].mpKm)
        assertEquals(0.0, weeks[1].mpKm)
    }

    @Test
    fun `R3 - runs outside the build are ignored`() {
        val report =
            compute(
                mpRun(1, LocalDate.of(2026, 8, 16), 5000.0),
                mpRun(2, LocalDate.of(2026, 12, 7), 5000.0),
                on = LocalDate.of(2026, 12, 20),
            )
        assertEquals(0.0, report.summary.totalMpKm)
        assertTrue(report.weeks.all { it.mpKm == 0.0 })
    }

    @Test
    fun `R3 - before and after the build`() {
        val before = compute(on = LocalDate.of(2026, 8, 10)).weeks
        assertTrue(before.all { it.isFuture && !it.isCurrentWeek })
        val after = compute(on = LocalDate.of(2026, 12, 10)).weeks
        assertTrue(after.none { it.isFuture || it.isCurrentWeek })
    }

    @Test
    fun `R4 - summary totals and share`() {
        val earlier = (0..9).map { mpRun(10L + it, buildStart.plusWeeks(it.toLong()), 3464.0) }
        val outdoor =
            run(
                id = 1,
                date = LocalDate.of(2026, 10, 27),
                distanceMeters = 16_000.0,
                laps = listOf(kmLap(1, 1632, 6400.0), kmLap(2, 3000, 9600.0)),
            )
        val treadmill = run(id = 2, date = LocalDate.of(2026, 10, 29), trainer = true, laps = listOf(kmLap(1, 255)))
        val summary = compute(*(earlier + outdoor + treadmill).toTypedArray()).summary
        // 10 × 3 464 m + 6 400 m = 41 040 m.
        assertEquals(41.0, summary.totalMpKm)
        assertEquals(LocalDate.of(2026, 10, 26), summary.thisWeek.start)
        assertEquals(LocalDate.of(2026, 11, 1), summary.thisWeek.end)
        assertEquals(6.4, summary.thisWeekMpKm)
        assertEquals(26.0, summary.thisWeekTotalKm)
        assertEquals(25, summary.thisWeekSharePercent)
    }

    @Test
    fun `R4 - this week total matches week comparison`() {
        val runs =
            listOf(
                run(id = 1, date = LocalDate.of(2026, 10, 27), distanceMeters = 16_040.0, laps = listOf(kmLap(1, 255))),
                run(id = 2, date = LocalDate.of(2026, 10, 29), distanceMeters = 10_010.0, trainer = true),
            )
        val comparison = WeekComparison.compute(runs, WeeklyDistance.compute(runs, today), today)
        val summary = MarathonPaceVolume.compute(runs, goal, buildStart, today).summary
        assertEquals(comparison.distance.thisWeekKm, summary.thisWeekTotalKm)
    }

    @Test
    fun `R4 - share is null with no runs`() {
        val summary = compute(mpRun(1, LocalDate.of(2026, 10, 19), 5000.0)).summary
        assertEquals(0.0, summary.thisWeekMpKm)
        assertEquals(0.0, summary.thisWeekTotalKm)
        assertNull(summary.thisWeekSharePercent)
    }

    @Test
    fun `R4 - share capped at 100`() {
        val run = mpRun(1, LocalDate.of(2026, 10, 27), mpMeters = 10_050.0, distance = 10_000.0)
        assertEquals(100, compute(run).summary.thisWeekSharePercent)
    }

    @Test
    fun `R4 - this week outside the build`() {
        val inBuild = mpRun(1, LocalDate.of(2026, 9, 1), 4000.0)
        val after = mpRun(2, LocalDate.of(2026, 12, 15), 3000.0)
        val summary = compute(inBuild, after, on = LocalDate.of(2026, 12, 16)).summary
        assertEquals(LocalDate.of(2026, 12, 14), summary.thisWeek.start)
        assertEquals(3.0, summary.thisWeekMpKm)
        assertEquals(4.0, summary.totalMpKm)
    }

    @Test
    fun `R9 - counts outdoor runs pending laps`() {
        val report =
            compute(
                run(id = 1, date = LocalDate.of(2026, 10, 20), laps = null),
                run(id = 2, date = LocalDate.of(2026, 10, 30), laps = null),
                run(id = 3, date = LocalDate.of(2026, 10, 30), laps = null, trainer = true),
                run(id = 4, date = LocalDate.of(2026, 8, 10), laps = null),
                run(id = 5, date = LocalDate.of(2026, 10, 28), laps = listOf(kmLap(1, 300))),
            )
        assertEquals(2, report.runsPendingLaps)
    }
}
