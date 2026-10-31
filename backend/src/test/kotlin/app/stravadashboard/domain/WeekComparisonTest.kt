package app.stravadashboard.domain

import app.stravadashboard.domain.model.RunActivity
import app.stravadashboard.domain.model.RunType
import app.stravadashboard.domain.model.SecondsPerKm
import app.stravadashboard.domain.model.StreamPaceResult
import app.stravadashboard.domain.model.StreamPaceStatus
import app.stravadashboard.testsupport.run
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Feature 007: this week vs last week for the headline tiles. All data is invented. */
class WeekComparisonTest {
    // Saturday: this week is 26 Oct - 1 Nov, last week 19-25 Oct.
    private val today = date("2026-10-31")

    private fun date(text: String): LocalDate = LocalDate.parse(text)

    private fun compute(
        runs: List<RunActivity>,
        on: LocalDate = today,
    ): WeekComparisonResult = WeekComparison.compute(runs, WeeklyDistance.compute(runs, on), on)

    private var nextId = 1L

    private fun easy(
        day: String,
        heartRate: Double?,
        distanceMeters: Double = 10_000.0,
        trainer: Boolean = false,
    ) = run(id = nextId++, date = date(day), heartRate = heartRate, distanceMeters = distanceMeters, trainer = trainer)

    private fun workout(
        day: String,
        name: String,
        pace: Int?,
    ) = run(
        id = nextId++,
        date = date(day),
        name = name,
        streamPace = pace?.let { StreamPaceResult(name, StreamPaceStatus.COMPUTED, SecondsPerKm(it)) },
    )

    @Test
    fun `R1 - week comparison distance matches weekly distance entries`() {
        val runs = listOf(easy("2026-10-20", 140.0, 29_000.0), easy("2026-10-27", 140.0, 32_400.0))
        val weeks = WeeklyDistance.compute(runs, today)
        val result = WeekComparison.compute(runs, weeks, today)
        assertEquals(weeks.last().distanceKm, result.distance.thisWeekKm)
        assertEquals(weeks[weeks.size - 2].distanceKm, result.distance.lastWeekKm)
        assertEquals(weeks.single { it.isCurrentWeek }.weekStart, result.thisWeek.start)
    }

    @Test
    fun `R2 - this and last week are Monday to Sunday around today`() {
        val result = compute(emptyList())
        assertEquals(DateWindow(date("2026-10-26"), date("2026-11-01")), result.thisWeek)
        assertEquals(DateWindow(date("2026-10-19"), date("2026-10-25")), result.lastWeek)
        assertEquals(date("2026-10-26"), compute(emptyList(), date("2026-11-01")).thisWeek.start)
        val nextMonday = compute(emptyList(), date("2026-11-02"))
        assertEquals(DateWindow(date("2026-11-02"), date("2026-11-08")), nextMonday.thisWeek)
        assertEquals(DateWindow(date("2026-10-26"), date("2026-11-01")), nextMonday.lastWeek)
    }

    @Test
    fun `R2 - km difference is this week minus last week from rounded values`() {
        val ahead = compute(listOf(easy("2026-10-20", null, 29_000.0), easy("2026-10-27", null, 32_400.0)))
        assertEquals(DistanceDifference(32.4, 29.0, 3.4), ahead.distance)
        val behind = compute(listOf(easy("2026-10-20", null, 24_200.0), easy("2026-10-27", null, 20_000.0)))
        assertEquals(-4.2, behind.distance.differenceKm)
    }

    @Test
    fun `R2 - km difference is zero not negative zero`() {
        val difference = compute(emptyList()).distance.differenceKm
        assertEquals(0.0, difference)
        assertEquals(0.0.toBits(), difference.toBits())
    }

    @Test
    fun `R2 - monday compares nothing so far with all of last week`() {
        val distance = compute(listOf(easy("2026-10-20", null, 30_000.0)), date("2026-10-26")).distance
        assertEquals(DistanceDifference(0.0, 30.0, -30.0), distance)
    }

    @Test
    fun `R4 - easy hr is unweighted mean per week with change from rounded means`() {
        val runs =
            listOf(
                easy("2026-10-20", 140.0),
                easy("2026-10-21", 142.0),
                easy("2026-10-27", 138.4, distanceMeters = 5_000.0),
                easy("2026-10-28", 139.4, distanceMeters = 15_000.0),
            )
        val hr = compute(runs).easyHeartRate
        assertEquals(EasyHrComparison(WeekHrStat(138.9, 2), WeekHrStat(141.0, 2), -2.1, null), hr)
        val unweighted = compute(listOf(easy("2026-10-27", 150.0, 5_000.0), easy("2026-10-28", 140.0, 15_000.0)))
        assertEquals(145.0, unweighted.easyHeartRate.thisWeek?.averageHeartRateBpm)
    }

    @Test
    fun `R4 - long runs and workouts are excluded from easy hr`() {
        val runs =
            listOf(
                easy("2026-10-20", 140.0),
                easy("2026-10-27", 140.0),
                easy("2026-10-28", 155.0, distanceMeters = 22_000.0),
                run(id = 99, date = date("2026-10-29"), name = "5x6min", heartRate = 165.0),
            )
        val thisWeek = compute(runs).easyHeartRate.thisWeek
        assertEquals(WeekHrStat(140.0, 1), thisWeek)
    }

    @Test
    fun `R4 - runs without hr left out and treadmill easy runs included`() {
        val runs =
            listOf(
                easy("2026-10-20", 140.0),
                easy("2026-10-27", null),
                easy("2026-10-28", 144.0, trainer = true),
            )
        val hr = compute(runs).easyHeartRate
        assertEquals(WeekHrStat(144.0, 1), hr.thisWeek)
        assertEquals(4.0, hr.changeBpm)
    }

    @Test
    fun `R4 - equal easy hr gives zero change not negative zero`() {
        val change = compute(listOf(easy("2026-10-20", 140.0), easy("2026-10-27", 140.0))).easyHeartRate.changeBpm
        assertEquals(0.0.toBits(), change?.toBits())
    }

    @Test
    fun `R4 - missingIn reports which week has no easy run with hr`() {
        val noThisWeek = compute(listOf(easy("2026-10-20", 140.0))).easyHeartRate
        assertEquals(MissingIn.THIS_WEEK, noThisWeek.missingIn)
        assertNull(noThisWeek.thisWeek)
        assertNull(noThisWeek.changeBpm)
        assertEquals(MissingIn.LAST_WEEK, compute(listOf(easy("2026-10-27", 140.0))).easyHeartRate.missingIn)
        assertEquals(MissingIn.BOTH_WEEKS, compute(emptyList()).easyHeartRate.missingIn)
    }

    @Test
    fun `R6 - workout pace is unweighted mean across types with change`() {
        val runs =
            listOf(
                workout("2026-10-20", "5x6min", 250),
                workout("2026-10-22", "3x10min", 257),
                workout("2026-10-28", "4x8min", 248),
            )
        val pace = compute(runs).workoutPace
        assertEquals(
            WeekPaceStat(SecondsPerKm(254), 2, listOf(RunType.Workout(5, 6), RunType.Workout(3, 10))),
            pace.lastWeek,
        )
        assertEquals(WeekPaceStat(SecondsPerKm(248), 1, listOf(RunType.Workout(4, 8))), pace.thisWeek)
        assertEquals(-6, pace.changeSecondsPerKm)
        assertNull(pace.missingIn)
    }

    @Test
    fun `R6 - repeated type listed once`() {
        val runs = listOf(workout("2026-10-27", "5x6min", 250), workout("2026-10-29", "5x6min", 254))
        val thisWeek = compute(runs).workoutPace.thisWeek
        assertEquals(listOf(RunType.Workout(5, 6)), thisWeek?.types)
        assertEquals(2, thisWeek?.runCount)
        assertEquals(SecondsPerKm(252), thisWeek?.averageRepPace)
    }

    @Test
    fun `R6 - treadmill workout uses description pace`() {
        val treadmill =
            run(id = 50, date = date("2026-10-28"), name = "5x6min", trainer = true, description = "4:15 4:11")
        assertEquals(SecondsPerKm(253), compute(listOf(treadmill)).workoutPace.thisWeek?.averageRepPace)
    }

    @Test
    fun `R6 - unpaced workouts and non-workouts are excluded`() {
        val runs =
            listOf(
                workout("2026-10-27", "5x6min", 250),
                workout("2026-10-28", "4x8min", null),
                run(id = 60, date = date("2026-10-29"), name = "3x10min", trainer = true, description = "felt good"),
                easy("2026-10-30", 140.0),
                easy("2026-10-31", 150.0, distanceMeters = 25_000.0),
            )
        val thisWeek = compute(runs).workoutPace.thisWeek
        assertEquals(WeekPaceStat(SecondsPerKm(250), 1, listOf(RunType.Workout(5, 6))), thisWeek)
    }

    @Test
    fun `R6 - missingIn reports which week has no paced workout`() {
        val noThisWeek = compute(listOf(workout("2026-10-20", "5x6min", 250))).workoutPace
        assertEquals(MissingIn.THIS_WEEK, noThisWeek.missingIn)
        assertNull(noThisWeek.changeSecondsPerKm)
        val noLastWeek = compute(listOf(workout("2026-10-27", "5x6min", 250))).workoutPace
        assertEquals(MissingIn.LAST_WEEK, noLastWeek.missingIn)
        assertEquals(MissingIn.BOTH_WEEKS, compute(listOf(easy("2026-10-27", 140.0))).workoutPace.missingIn)
    }

    @Test
    fun `R9 - runs bucket by local start date across midnight`() {
        val sundayLate =
            run(
                id = 70,
                startLocal = LocalDateTime.parse("2026-10-25T23:30"),
                heartRate = 140.0,
                distanceMeters = 8_000.0,
            )
        val mondayEarly =
            run(
                id = 71,
                startLocal = LocalDateTime.parse("2026-10-26T00:10"),
                heartRate = 146.0,
                distanceMeters = 6_000.0,
            )
        val result = compute(listOf(sundayLate, mondayEarly))
        assertEquals(DistanceDifference(6.0, 8.0, -2.0), result.distance)
        assertEquals(WeekHrStat(140.0, 1), result.easyHeartRate.lastWeek)
        assertEquals(WeekHrStat(146.0, 1), result.easyHeartRate.thisWeek)
    }

    @Test
    fun `R9 - workouts bucket by local start date across midnight`() {
        fun pacedAt(
            id: Long,
            start: String,
            pace: Int,
        ) = run(
            id = id,
            startLocal = LocalDateTime.parse(start),
            name = "5x6min",
            streamPace = StreamPaceResult("5x6min", StreamPaceStatus.COMPUTED, SecondsPerKm(pace)),
        )
        val pace =
            compute(
                listOf(pacedAt(80, "2026-10-25T23:30", 255), pacedAt(81, "2026-10-26T00:10", 250)),
            ).workoutPace
        assertEquals(SecondsPerKm(255), pace.lastWeek?.averageRepPace)
        assertEquals(SecondsPerKm(250), pace.thisWeek?.averageRepPace)
    }
}
