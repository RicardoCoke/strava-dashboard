package app.stravadashboard.domain

import app.stravadashboard.domain.model.RunActivity
import app.stravadashboard.domain.model.RunType
import app.stravadashboard.domain.model.SecondsPerKm
import app.stravadashboard.domain.model.roundHalfUpToInt
import java.time.LocalDate

data class DateWindow(
    val start: LocalDate,
    val end: LocalDate,
) {
    operator fun contains(date: LocalDate): Boolean = date in start..end
}

data class PaceWindowStat(
    val averageRepPace: SecondsPerKm,
    val runCount: Int,
)

/** One run's average rep pace for the per-run chart (feature 010). */
data class PacePoint(
    val date: LocalDate,
    val repPace: SecondsPerKm,
    val treadmill: Boolean,
)

/** [runCount] is every run of [type] in the 5-month window; [runsWithoutPace] those without a rep pace. */
data class PaceTypeComparison(
    val type: RunType.Workout,
    val current: PaceWindowStat?,
    val previous: PaceWindowStat?,
    val runCount: Int,
    val points: List<PacePoint>,
) {
    val runsWithoutPace: Int get() = runCount - points.size
    val hasEnoughData: Boolean get() = current != null && previous != null
    val changeSecondsPerKm: Int?
        get() =
            if (current != null && previous != null) {
                current.averageRepPace.value - previous.averageRepPace.value
            } else {
                null
            }
}

data class PaceComparisonResult(
    val windowStart: LocalDate,
    val windowEnd: LocalDate,
    val currentWindow: DateWindow,
    val previousWindow: DateWindow,
    val types: List<PaceTypeComparison>,
)

/**
 * Workout types over the same 5 months as [HeartRateTrend], each with one rep-pace point per run and its average
 * rep pace in the last 30 days against the 30 days before.
 */
object WorkoutPaceComparison {
    private const val WINDOW_DAYS = 30L

    fun compute(
        runs: List<RunActivity>,
        today: LocalDate,
    ): PaceComparisonResult {
        val window = DateWindow(today.minusMonths(HeartRateTrend.WINDOW_MONTHS), today)
        val current = DateWindow(today.minusDays(WINDOW_DAYS - 1), today)
        val previous = DateWindow(today.minusDays(2 * WINDOW_DAYS - 1), today.minusDays(WINDOW_DAYS))
        val types =
            runs
                .filter { it.isRun && it.localDate in window }
                .mapNotNull { run -> (RunClassifier.classify(run) as? RunType.Workout)?.let { it to run } }
                .groupBy({ it.first }, { it.second })
                .entries
                .sortedByDescending { (_, typeRuns) -> typeRuns.maxOf { it.startUtc } }
                .map { (type, typeRuns) -> compare(type, typeRuns, current, previous) }
        return PaceComparisonResult(window.start, window.end, current, previous, types)
    }

    private fun compare(
        type: RunType.Workout,
        runs: List<RunActivity>,
        current: DateWindow,
        previous: DateWindow,
    ): PaceTypeComparison {
        val paced =
            runs
                .sortedBy { it.startLocal }
                .mapNotNull { run -> RepPaceCalculator.repPace(run, type)?.let { run to it } }
        return PaceTypeComparison(
            type = type,
            current = stat(paced.filter { it.first.localDate in current }.map { it.second }),
            previous = stat(paced.filter { it.first.localDate in previous }.map { it.second }),
            runCount = runs.size,
            points = paced.map { (run, pace) -> PacePoint(run.localDate, pace, run.trainer) },
        )
    }

    private fun stat(paces: List<SecondsPerKm>): PaceWindowStat? =
        if (paces.isEmpty()) {
            null
        } else {
            PaceWindowStat(SecondsPerKm(roundHalfUpToInt(paces.map { it.value }.average())), paces.size)
        }
}
