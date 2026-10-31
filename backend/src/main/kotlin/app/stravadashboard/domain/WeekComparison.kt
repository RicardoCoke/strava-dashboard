package app.stravadashboard.domain

import app.stravadashboard.domain.model.RunActivity
import app.stravadashboard.domain.model.RunType
import app.stravadashboard.domain.model.SecondsPerKm
import app.stravadashboard.domain.model.roundHalfUpToInt
import app.stravadashboard.domain.model.roundOneDecimal
import java.time.LocalDate

data class DistanceDifference(
    val thisWeekKm: Double,
    val lastWeekKm: Double,
    val differenceKm: Double,
)

data class WeekHrStat(
    val averageHeartRateBpm: Double,
    val runCount: Int,
)

data class WeekPaceStat(
    val averageRepPace: SecondsPerKm,
    val runCount: Int,
    val types: List<RunType.Workout>,
)

enum class MissingIn { THIS_WEEK, LAST_WEEK, BOTH_WEEKS }

data class EasyHrComparison(
    val thisWeek: WeekHrStat?,
    val lastWeek: WeekHrStat?,
    val changeBpm: Double?,
    val missingIn: MissingIn?,
)

data class WorkoutPaceWeekComparison(
    val thisWeek: WeekPaceStat?,
    val lastWeek: WeekPaceStat?,
    val changeSecondsPerKm: Int?,
    val missingIn: MissingIn?,
)

data class WeekComparisonResult(
    val thisWeek: DateWindow,
    val lastWeek: DateWindow,
    val distance: DistanceDifference,
    val easyHeartRate: EasyHrComparison,
    val workoutPace: WorkoutPaceWeekComparison,
)

/** This Monday-Sunday week so far against all of last week, for the headline tiles. */
object WeekComparison {
    private const val DAYS_PER_WEEK = 7L

    fun compute(
        runs: List<RunActivity>,
        weeks: List<WeekTotal>,
        today: LocalDate,
    ): WeekComparisonResult {
        val thisWeekStart = WeeklyDistance.weekStartOf(today)
        val thisWeek = DateWindow(thisWeekStart, thisWeekStart.plusDays(DAYS_PER_WEEK - 1))
        val lastWeek = DateWindow(thisWeekStart.minusDays(DAYS_PER_WEEK), thisWeekStart.minusDays(1))
        val runsIn = { window: DateWindow -> runs.filter { it.isRun && it.localDate in window } }
        val thisRuns = runsIn(thisWeek)
        val lastRuns = runsIn(lastWeek)
        return WeekComparisonResult(
            thisWeek = thisWeek,
            lastWeek = lastWeek,
            distance = distance(weeks),
            easyHeartRate = easyHeartRate(thisRuns, lastRuns),
            workoutPace = workoutPace(thisRuns, lastRuns),
        )
    }

    /** From the weekly chart's last two entries, so the numbers always match the chart. */
    private fun distance(weeks: List<WeekTotal>): DistanceDifference {
        val current = weeks.indexOfFirst { it.isCurrentWeek }
        val thisWeekKm = weeks.getOrNull(current)?.distanceKm ?: 0.0
        val lastWeekKm = weeks.getOrNull(current - 1)?.distanceKm ?: 0.0
        return DistanceDifference(thisWeekKm, lastWeekKm, noNegativeZero(roundOneDecimal(thisWeekKm - lastWeekKm)))
    }

    private fun easyHeartRate(
        thisRuns: List<RunActivity>,
        lastRuns: List<RunActivity>,
    ): EasyHrComparison {
        val thisWeek = easyHrStat(thisRuns)
        val lastWeek = easyHrStat(lastRuns)
        val change =
            if (thisWeek != null && lastWeek != null) {
                noNegativeZero(roundOneDecimal(thisWeek.averageHeartRateBpm - lastWeek.averageHeartRateBpm))
            } else {
                null
            }
        return EasyHrComparison(thisWeek, lastWeek, change, missingIn(thisWeek, lastWeek))
    }

    /** Each easy run with HR counts equally; long runs and workouts are left out. */
    private fun easyHrStat(runs: List<RunActivity>): WeekHrStat? {
        val bpm = runs.filter { RunClassifier.classify(it) == RunType.Easy }.mapNotNull { it.averageHeartRateBpm }
        return if (bpm.isEmpty()) null else WeekHrStat(roundOneDecimal(bpm.average()), bpm.size)
    }

    private fun workoutPace(
        thisRuns: List<RunActivity>,
        lastRuns: List<RunActivity>,
    ): WorkoutPaceWeekComparison {
        val thisWeek = paceStat(thisRuns)
        val lastWeek = paceStat(lastRuns)
        val change =
            if (thisWeek != null && lastWeek != null) {
                thisWeek.averageRepPace.value - lastWeek.averageRepPace.value
            } else {
                null
            }
        return WorkoutPaceWeekComparison(thisWeek, lastWeek, change, missingIn(thisWeek, lastWeek))
    }

    /** Each workout with a measured rep pace counts equally, whatever its type. */
    private fun paceStat(runs: List<RunActivity>): WeekPaceStat? {
        val paced =
            runs
                .sortedBy { it.startLocal }
                .mapNotNull { run ->
                    (RunClassifier.classify(run) as? RunType.Workout)?.let { type ->
                        RepPaceCalculator.repPace(run, type)?.let { type to it }
                    }
                }
        if (paced.isEmpty()) return null
        val average = SecondsPerKm(roundHalfUpToInt(paced.map { it.second.value }.average()))
        return WeekPaceStat(average, paced.size, paced.map { it.first }.distinct())
    }

    private fun missingIn(
        thisWeek: Any?,
        lastWeek: Any?,
    ): MissingIn? =
        when {
            thisWeek == null && lastWeek == null -> MissingIn.BOTH_WEEKS
            thisWeek == null -> MissingIn.THIS_WEEK
            lastWeek == null -> MissingIn.LAST_WEEK
            else -> null
        }

    private fun noNegativeZero(value: Double): Double = if (value == 0.0) 0.0 else value
}
