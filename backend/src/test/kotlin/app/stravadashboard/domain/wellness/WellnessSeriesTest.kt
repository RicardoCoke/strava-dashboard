package app.stravadashboard.domain.wellness

import app.stravadashboard.testsupport.WELLNESS_TODAY
import app.stravadashboard.testsupport.wellnessDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WellnessSeriesTest {
    private val today = WELLNESS_TODAY

    private fun daysBefore(
        date: java.time.LocalDate,
        restingHr: List<Int?> = emptyList(),
        hrv: List<Double?> = emptyList(),
    ): List<WellnessDay> {
        // Index 0 is D − 7, index 6 is D − 1.
        val size = maxOf(restingHr.size, hrv.size)
        return (0 until size).mapNotNull { i ->
            val r = restingHr.getOrNull(i)
            val h = hrv.getOrNull(i)
            if (r == null && h == null) null else wellnessDay(date.minusDays(7L - i), r, h)
        }
    }

    @Test
    fun `R5 - 84 ascending days with nulls for missing`() {
        val view = WellnessSeries.build(listOf(wellnessDay(today.minusDays(3), restingHr = 50)), today)

        assertEquals(84, view.points.size)
        assertEquals(today.minusDays(83), view.windowStart)
        assertEquals(today, view.windowEnd)
        assertEquals(view.points.map { it.date }, view.points.map { it.date }.sorted())
        view.points.zipWithNext().forEach { (a, b) -> assertEquals(a.date.plusDays(1), b.date) }
        val missing = view.points.single { it.date == today.minusDays(2) }
        assertNull(missing.restingHr)
        assertNull(missing.hrv)
        assertNull(view.points.single { it.date == today.minusDays(3) }.hrv)
        assertEquals(1, view.daysWithData)
    }

    @Test
    fun `R5 - baseline averages previous 7 days with at least 3 values`() {
        val full = WellnessSeries.build(daysBefore(today, restingHr = listOf(50, 52, 51, 49, 50, 48, 50)), today)
        assertEquals(50.0, full.points.last().restingHrBaseline)

        val two = WellnessSeries.build(daysBefore(today, restingHr = listOf(50, null, null, 51)), today)
        assertNull(two.points.last().restingHrBaseline)

        val three = WellnessSeries.build(daysBefore(today, restingHr = listOf(50, null, 51, null, null, 53)), today)
        assertEquals(51.333, assertNotNull(three.points.last().restingHrBaseline), 0.001)
    }

    @Test
    fun `R5 - baseline excludes the day itself and is kept on a day without value`() {
        val stored = daysBefore(today, hrv = listOf(60.0, 60.0, 60.0)) + wellnessDay(today, hrv = 90.0)
        val view = WellnessSeries.build(stored, today)
        assertEquals(60.0, view.points.last().hrvBaseline)

        val gap = WellnessSeries.build(daysBefore(today, hrv = listOf(60.0, 61.0, 62.0)), today)
        assertNull(gap.points.last().hrv)
        assertEquals(61.0, gap.points.last().hrvBaseline)
    }

    @Test
    fun `R5 - first window day uses pre-window days for baseline`() {
        val first = today.minusDays(83)
        val view = WellnessSeries.build(daysBefore(first, restingHr = listOf(46, 47, 48)), today)
        assertEquals(first, view.points.first().date)
        assertEquals(47.0, view.points.first().restingHrBaseline)
        assertEquals(0, view.daysWithData)
    }

    @Test
    fun `R6 - resting HR flagged at 5 bpm above baseline`() {
        fun latestWith(value: Int) =
            WellnessSeries
                .build(daysBefore(today, restingHr = listOf(48, 48, 48)) + wellnessDay(today, restingHr = value), today)
                .restingHr

        val flagged = assertNotNull(latestWith(53))
        assertEquals(5.0, flagged.delta)
        assertTrue(flagged.flagged)
        assertFalse(assertNotNull(latestWith(52)).flagged)
        assertFalse(assertNotNull(latestWith(45)).flagged)
    }

    @Test
    fun `R6 - resting HR less than 5 bpm above an unrounded baseline is not flagged`() {
        // Baseline 48.333: the delta 4.667 would round to 4.7, and isn't flagged.
        val stored = daysBefore(today, restingHr = listOf(48, 48, 49)) + wellnessDay(today, restingHr = 53)
        val latest = assertNotNull(WellnessSeries.build(stored, today).restingHr)
        assertEquals(53 - 48.333, assertNotNull(latest.delta), 0.001)
        assertFalse(latest.flagged)
    }

    @Test
    fun `R6 - HRV flagged at 15 percent below baseline`() {
        fun latestWith(value: Double) =
            WellnessSeries
                .build(daysBefore(today, hrv = listOf(60.0, 60.0, 60.0)) + wellnessDay(today, hrv = value), today)
                .hrv

        assertTrue(assertNotNull(latestWith(51.0)).flagged)
        assertFalse(assertNotNull(latestWith(51.06)).flagged)
        assertFalse(assertNotNull(latestWith(70.0)).flagged)
    }

    @Test
    fun `R6 - no baseline or no value means no flag`() {
        val noBaseline = WellnessSeries.build(listOf(wellnessDay(today, restingHr = 70, hrv = 20.0)), today)
        val restingHr = assertNotNull(noBaseline.restingHr)
        assertNull(restingHr.baseline)
        assertNull(restingHr.delta)
        assertFalse(restingHr.flagged)
        assertEquals(0, restingHr.flagStreak)

        val none = WellnessSeries.build(emptyList(), today)
        assertNull(none.restingHr)
        assertNull(none.hrv)
    }

    @Test
    fun `R6 - latest is the most recent day with a value`() {
        val stored = listOf(wellnessDay(today.minusDays(1), hrv = 55.0), wellnessDay(today, restingHr = 50))
        val view = WellnessSeries.build(stored, today)
        assertEquals(today.minusDays(1), view.hrv?.date)
        assertEquals(today, view.restingHr?.date)
    }

    @Test
    fun `R7 - flag streak counts consecutive flagged days and a missing day ends it`() {
        val base = (10 downTo 4).map { wellnessDay(today.minusDays(it.toLong()), hrv = 60.0) }
        val low = (2 downTo 0).map { wellnessDay(today.minusDays(it.toLong()), hrv = 40.0) }
        val steady = base + wellnessDay(today.minusDays(3), hrv = 60.0) + low
        assertEquals(3, WellnessSeries.build(steady, today).hrv?.flagStreak)
        // Day today − 3 missing: the streak still covers the 3 low days after it.
        assertEquals(3, WellnessSeries.build(base + low, today).hrv?.flagStreak)
        val broken = base + low.filterNot { it.date == today.minusDays(1) }
        assertEquals(1, WellnessSeries.build(broken, today).hrv?.flagStreak)
    }
}
