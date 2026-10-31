package app.stravadashboard.domain

import app.stravadashboard.domain.model.RunType
import app.stravadashboard.domain.model.SecondsPerKm
import app.stravadashboard.domain.model.StreamPaceResult
import app.stravadashboard.domain.model.StreamPaceStatus
import app.stravadashboard.testsupport.TODAY
import app.stravadashboard.testsupport.run
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AggregationsTest {
    private fun date(text: String): LocalDate = LocalDate.parse(text)

    private fun paced(
        id: Long,
        day: String,
        pace: Int?,
        name: String = "5x6min",
    ) = run(
        id = id,
        date = date(day),
        name = name,
        streamPace = StreamPaceResult("5x6min", StreamPaceStatus.COMPUTED, pace?.let(::SecondsPerKm)),
    )

    @Test
    fun `R6 - only Run sport type counts in all aggregates`() {
        val runs =
            listOf(
                run(id = 1, name = "5x6min"),
                run(id = 2, name = "5x6min", trainer = true, description = "4:10"),
                run(id = 3),
            ) +
                listOf("Walk", "Ride", "Hike", "TrailRun", "VirtualRun").mapIndexed { i, type ->
                    run(id = 10L + i, name = "5x6min", sportType = type)
                }

        assertEquals(3, WeeklyDistance.compute(runs, TODAY).last().runCount)
        assertEquals(3, HeartRateTrend.compute(runs, TODAY).types.sumOf { it.runCount })
        val pace = WorkoutPaceComparison.compute(runs, TODAY).types.single()
        assertEquals(2, pace.runsWithoutPace + (pace.current?.runCount ?: 0))
    }

    @Test
    fun `R6 - manual run counts in km but not in HR or pace`() {
        val manual = run(id = 1, name = "4x8min", heartRate = null, laps = emptyList(), streamPace = null)
        assertEquals(10.0, WeeklyDistance.compute(listOf(manual), TODAY).last().distanceKm)
        val hr = HeartRateTrend.compute(listOf(manual), TODAY).types.single()
        assertEquals(0, hr.points.size)
        assertEquals(1, hr.runsWithoutHeartRate)
        assertEquals(
            1,
            WorkoutPaceComparison
                .compute(listOf(manual), TODAY)
                .types
                .single()
                .runsWithoutPace,
        )
    }

    @Test
    fun `R8 - nine Monday to Sunday weeks ending in current week`() {
        val weeks = WeeklyDistance.compute(emptyList(), TODAY)
        assertEquals(9, weeks.size)
        assertEquals(date("2026-08-31"), weeks.first().weekStart)
        assertEquals(date("2026-10-26"), weeks.last().weekStart)
        weeks.forEach { assertEquals(it.weekStart.plusDays(6), it.weekEnd) }
        assertEquals(
            listOf(false, false, false, false, false, false, false, false, true),
            weeks.map { it.isCurrentWeek },
        )
    }

    @Test
    fun `R8 - weekly totals bucket by local date`() {
        val lateSunday =
            run(startLocal = LocalDateTime.parse("2026-10-25T23:30:00"))
                .copy(startUtc = java.time.Instant.parse("2026-10-26T03:30:00Z"))
        val weeks = WeeklyDistance.compute(listOf(lateSunday), TODAY)
        assertEquals(1, weeks.single { it.weekStart == date("2026-10-19") }.runCount)
        assertEquals(0, weeks.last().runCount)
    }

    @Test
    fun `R8 - metres summed then converted to km with one decimal`() {
        val week =
            WeeklyDistance
                .compute(listOf(run(id = 1, distanceMeters = 5000.0), run(id = 2, distanceMeters = 10049.0)), TODAY)
                .last()
        assertEquals(15.0, week.distanceKm)
        assertEquals(2, week.runCount)
    }

    @Test
    fun `R8 - empty weeks are returned as zero`() {
        WeeklyDistance.compute(emptyList(), TODAY).forEach {
            assertEquals(0.0, it.distanceKm)
            assertEquals(0, it.runCount)
        }
    }

    @Test
    fun `R9 - HR points per type sorted by date and rounded`() {
        val runs =
            listOf(
                run(id = 2, date = date("2026-10-10"), name = "5x6min", heartRate = 151.26, distanceMeters = 9960.0),
                run(id = 1, date = date("2026-10-01"), name = "5x6min", heartRate = 155.0),
            )
        val type = HeartRateTrend.compute(runs, TODAY).types.single()
        assertEquals(listOf(date("2026-10-01"), date("2026-10-10")), type.points.map { it.date })
        assertEquals(151.3, type.points.last().averageHeartRateBpm)
        assertEquals(10.0, type.points.last().distanceKm)
    }

    @Test
    fun `R9 - HR window is last five months inclusive`() {
        val result =
            HeartRateTrend.compute(
                listOf(run(id = 1, date = date("2026-05-28")), run(id = 2, date = date("2026-05-29"))),
                TODAY,
            )
        assertEquals(date("2026-05-29"), result.windowStart)
        assertEquals(TODAY, result.windowEnd)
        assertEquals(
            listOf(date("2026-05-29")),
            result.types
                .single()
                .points
                .map { it.date },
        )
    }

    @Test
    fun `R9 - runs without HR are counted not plotted`() {
        val type =
            HeartRateTrend.compute(listOf(run(id = 1), run(id = 2, heartRate = null)), TODAY).types.single()
        assertEquals(2, type.runCount)
        assertEquals(1, type.runsWithoutHeartRate)
        assertEquals(1, type.points.size)
    }

    @Test
    fun `R9 - treadmill and outdoor runs share a type`() {
        val runs =
            listOf(
                run(id = 1, date = date("2026-10-01"), name = "5x6min", trainer = true),
                run(id = 2, date = date("2026-10-02"), name = "5x6min", trainer = false),
            )
        val type = HeartRateTrend.compute(runs, TODAY).types.single()
        assertEquals(listOf(true, false), type.points.map { it.treadmill })
    }

    @Test
    fun `R9 - trend line is least squares over points`() {
        val runs =
            listOf("2026-06-01" to 150.0, "2026-07-01" to 146.0, "2026-08-01" to 142.0).mapIndexed { i, (day, hr) ->
                run(id = i.toLong(), date = date(day), heartRate = hr)
            }
        val trend =
            requireNotNull(
                HeartRateTrend
                    .compute(runs, TODAY)
                    .types
                    .single()
                    .trend,
            )
        assertEquals(HrTrendLine(date("2026-06-01"), 150.0, date("2026-08-01"), 142.0, -8.0), trend)
    }

    @Test
    fun `R9 - easy first then workouts by most recent run`() {
        val runs =
            listOf(
                run(id = 1, date = date("2026-10-01"), name = "Easy"),
                run(id = 2, date = date("2026-10-05"), name = "4x8min"),
                run(id = 3, date = date("2026-10-20"), name = "5x6min"),
            )
        assertEquals(listOf("easy", "5x6min", "4x8min"), HeartRateTrend.compute(runs, TODAY).types.map { it.type.key })
    }

    // 003: invented bpm values, one run per day from 1 Sep 2026.
    private val exampleA = listOf(150.0, 150.0, 150.0, 151.0, 152.0, 153.0, 154.0, 148.0, 146.0, 146.0)
    private val exampleARising = listOf(false, false, false, true, true, true, true, false, false, false)

    private fun hrRuns(
        bpm: List<Double?>,
        name: String = "Morning Run",
        firstId: Long = 1,
        firstDay: LocalDate = date("2026-09-01"),
    ) = bpm.mapIndexed { i, hr ->
        run(id = firstId + i, date = firstDay.plusDays(i.toLong()), name = name, heartRate = hr)
    }

    @Test
    fun `003 R1 - runs without HR are skipped by the rolling window`() {
        val withGap = exampleA.take(4) + null + exampleA.drop(4)
        val type = HeartRateTrend.compute(hrRuns(withGap), TODAY).types.single()
        assertEquals(1, type.runsWithoutHeartRate)
        assertEquals(exampleARising, type.points.map { it.rising })
    }

    @Test
    fun `003 R1 - runs before the window are not used for the average`() {
        val old = hrRuns(listOf(190.0, 190.0), firstId = 100, firstDay = date("2026-05-01"))
        val type = HeartRateTrend.compute(old + hrRuns(exampleA), TODAY).types.single()
        assertEquals(exampleARising, type.points.map { it.rising })
    }

    @Test
    fun `003 R6 - rising flags are computed per run type`() {
        val falling = listOf(160.0, 159.0, 158.0, 157.0, 156.0, 155.0, 154.0)
        val runs = hrRuns(exampleA) + hrRuns(falling, name = "5x6min", firstId = 50, firstDay = date("2026-09-02"))
        val types = HeartRateTrend.compute(runs, TODAY).types.associateBy { it.type.key }
        assertEquals(exampleARising, types.getValue("easy").points.map { it.rising })
        assertEquals(List(falling.size) { false }, types.getValue("5x6min").points.map { it.rising })
    }

    @Test
    fun `R12 - HR type with fewer than three points has no trend`() {
        val type = HeartRateTrend.compute(listOf(run(id = 1), run(id = 2)), TODAY).types.single()
        assertFalse(type.hasEnoughData)
        assertNull(type.trend)
    }

    @Test
    fun `R11 - rolling 30 day windows`() {
        val result = WorkoutPaceComparison.compute(emptyList(), TODAY)
        assertEquals(DateWindow(date("2026-09-30"), TODAY), result.currentWindow)
        assertEquals(DateWindow(date("2026-08-31"), date("2026-09-29")), result.previousWindow)
    }

    @Test
    fun `R11 - window pace is mean of run paces`() {
        val result =
            WorkoutPaceComparison.compute(listOf(paced(1, "2026-10-01", 250), paced(2, "2026-10-20", 255)), TODAY)
        assertEquals(PaceWindowStat(SecondsPerKm(253), 2), result.types.single().current)
    }

    @Test
    fun `R11 - change is current minus previous`() {
        val result =
            WorkoutPaceComparison.compute(listOf(paced(1, "2026-10-20", 253), paced(2, "2026-09-10", 260)), TODAY)
        val type = result.types.single()
        assertEquals(-7, type.changeSecondsPerKm)
        assertTrue(type.hasEnoughData)
    }

    @Test
    fun `010 R1 - workout types with runs in the last 5 months appear, most recent first`() {
        val runs =
            listOf(
                run(id = 1, name = "Easy"),
                paced(2, "2026-10-20", null, name = "5x6min"),
                paced(3, "2026-08-20", 250, name = "4x8min"),
                run(id = 4, date = date("2026-10-01"), name = "3x10min"),
            )
        val types = WorkoutPaceComparison.compute(runs, TODAY).types
        assertEquals(listOf("5x6min", "3x10min", "4x8min"), types.map { it.type.key })
        assertTrue(types.none { it.type == RunType.Easy })
    }

    @Test
    fun `R12 - pace type missing a window is not enough data`() {
        val type = WorkoutPaceComparison.compute(listOf(paced(1, "2026-10-20", 250)), TODAY).types.single()
        assertFalse(type.hasEnoughData)
        assertNull(type.previous)
        assertNull(type.changeSecondsPerKm)
    }
}
