package app.stravadashboard.domain.marathonpace

import app.stravadashboard.domain.WeeklyDistance
import app.stravadashboard.domain.model.Lap
import app.stravadashboard.domain.model.METERS_PER_KM
import app.stravadashboard.domain.model.Meters
import app.stravadashboard.domain.model.RunActivity
import app.stravadashboard.domain.model.SecondsPerKm
import app.stravadashboard.domain.model.roundHalfUpToInt
import app.stravadashboard.domain.model.toKmOneDecimal
import java.time.LocalDate

/** The goal marathon pace and the inclusive pace band a lap must fall in to count as MP. */
data class MpGoal(
    val pace: SecondsPerKm,
) {
    val fastest: SecondsPerKm get() = SecondsPerKm(pace.value - MarathonPaceVolume.TOLERANCE_S_PER_KM)
    val slowest: SecondsPerKm get() = SecondsPerKm(pace.value + MarathonPaceVolume.TOLERANCE_S_PER_KM)
}

data class MpWeek(
    val index: Int,
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val mpKm: Double,
    val isCurrentWeek: Boolean,
    val isFuture: Boolean,
)

data class MpSummary(
    val totalMpKm: Double,
    val thisWeekStart: LocalDate,
    val thisWeekEnd: LocalDate,
    val thisWeekMpKm: Double,
    val thisWeekTotalKm: Double,
    val thisWeekSharePercent: Int?,
)

data class MpReport(
    val goal: MpGoal,
    val buildStart: LocalDate,
    val buildEnd: LocalDate,
    val weeks: List<MpWeek>,
    val summary: MpSummary,
    val runsPendingLaps: Int,
)

/** Marathon-pace km per build week (feature 012), from cached laps only. */
object MarathonPaceVolume {
    const val TOLERANCE_S_PER_KM = 10
    const val MIN_LAP_METERS = 200.0
    const val BUILD_WEEKS = 16
    private const val DAYS_PER_WEEK = 7L
    private const val FULL_SHARE_PERCENT = 100.0

    /** Exact comparison in seconds × 1000 against seconds-per-km × metres, so nothing is rounded first. */
    fun isMpLap(
        lap: Lap,
        goal: MpGoal,
    ): Boolean {
        val meters = lap.distance.value
        val scaledSeconds = lap.elapsed.value * METERS_PER_KM
        return meters >= MIN_LAP_METERS &&
            lap.elapsed.value > 0 &&
            scaledSeconds >= goal.fastest.value * meters &&
            scaledSeconds <= goal.slowest.value * meters
    }

    /** MP metres of one run: 0 for treadmill runs, non-Runs and runs whose laps aren't fetched yet. */
    fun mpMeters(
        run: RunActivity,
        goal: MpGoal,
    ): Double {
        if (!run.isRun || run.trainer) return 0.0
        return run.laps
            .orEmpty()
            .filter { isMpLap(it, goal) }
            .sumOf { it.distance.value }
    }

    fun compute(
        runs: List<RunActivity>,
        goal: MpGoal,
        buildStart: LocalDate,
        today: LocalDate,
    ): MpReport {
        val buildEnd = buildStart.plusWeeks(BUILD_WEEKS.toLong()).minusDays(1)
        val mpByWeek =
            runs
                .filter { it.localDate in buildStart..buildEnd }
                .groupBy { WeeklyDistance.weekStartOf(it.localDate) }
                .mapValues { (_, weekRuns) -> weekRuns.sumOf { mpMeters(it, goal) } }
        val weeks =
            (1..BUILD_WEEKS).map { index ->
                val weekStart = buildStart.plusWeeks(index - 1L)
                val weekEnd = weekStart.plusDays(DAYS_PER_WEEK - 1)
                MpWeek(
                    index = index,
                    weekStart = weekStart,
                    weekEnd = weekEnd,
                    mpKm = Meters(mpByWeek[weekStart] ?: 0.0).toKmOneDecimal(),
                    isCurrentWeek = today in weekStart..weekEnd,
                    isFuture = weekStart > today,
                )
            }
        return MpReport(
            goal = goal,
            buildStart = buildStart,
            buildEnd = buildEnd,
            weeks = weeks,
            summary = summary(runs, goal, today, Meters(mpByWeek.values.sum())),
            runsPendingLaps = pendingLaps(runs, buildStart, minOf(today, buildEnd)),
        )
    }

    private fun summary(
        runs: List<RunActivity>,
        goal: MpGoal,
        today: LocalDate,
        totalMp: Meters,
    ): MpSummary {
        val weekStart = WeeklyDistance.weekStartOf(today)
        val weekEnd = weekStart.plusDays(DAYS_PER_WEEK - 1)
        val weekRuns = runs.filter { it.isRun && it.localDate in weekStart..weekEnd }
        val mpMeters = weekRuns.sumOf { mpMeters(it, goal) }
        val totalMeters = weekRuns.sumOf { it.distance.value }
        val share =
            if (totalMeters > 0.0) {
                roundHalfUpToInt(mpMeters / totalMeters * FULL_SHARE_PERCENT).coerceIn(0, FULL_SHARE_PERCENT.toInt())
            } else {
                null
            }
        return MpSummary(
            totalMpKm = totalMp.toKmOneDecimal(),
            thisWeekStart = weekStart,
            thisWeekEnd = weekEnd,
            thisWeekMpKm = Meters(mpMeters).toKmOneDecimal(),
            thisWeekTotalKm = Meters(totalMeters).toKmOneDecimal(),
            thisWeekSharePercent = share,
        )
    }

    private fun pendingLaps(
        runs: List<RunActivity>,
        from: LocalDate,
        to: LocalDate,
    ): Int = runs.count { it.isRun && !it.trainer && it.laps == null && it.localDate in from..to }
}
