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

data class PaceTypeComparison(
    val type: RunType.Workout,
    val current: PaceWindowStat?,
    val previous: PaceWindowStat?,
    val runsWithoutPace: Int,
) {
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
    val currentWindow: DateWindow,
    val previousWindow: DateWindow,
    val types: List<PaceTypeComparison>,
)

/** Average rep pace per workout type: the last 30 days against the 30 days before. */
object WorkoutPaceComparison {
    private const val WINDOW_DAYS = 30L

    fun compute(
        runs: List<RunActivity>,
        today: LocalDate,
    ): PaceComparisonResult {
        val current = DateWindow(today.minusDays(WINDOW_DAYS - 1), today)
        val previous = DateWindow(today.minusDays(2 * WINDOW_DAYS - 1), today.minusDays(WINDOW_DAYS))
        val types =
            runs
                .filter { it.isRun && (it.localDate in current || it.localDate in previous) }
                .mapNotNull { run -> (RunClassifier.classify(run) as? RunType.Workout)?.let { it to run } }
                .groupBy({ it.first }, { it.second })
                .entries
                .sortedByDescending { (_, typeRuns) -> typeRuns.maxOf { it.startUtc } }
                .map { (type, typeRuns) -> compare(type, typeRuns, current, previous) }
        return PaceComparisonResult(current, previous, types)
    }

    private fun compare(
        type: RunType.Workout,
        runs: List<RunActivity>,
        current: DateWindow,
        previous: DateWindow,
    ): PaceTypeComparison {
        val paced = runs.map { it to RepPaceCalculator.repPace(it, type) }
        return PaceTypeComparison(
            type = type,
            current = stat(paced.filter { it.first.localDate in current }.mapNotNull { it.second }),
            previous = stat(paced.filter { it.first.localDate in previous }.mapNotNull { it.second }),
            runsWithoutPace = paced.count { it.second == null },
        )
    }

    private fun stat(paces: List<SecondsPerKm>): PaceWindowStat? =
        if (paces.isEmpty()) {
            null
        } else {
            PaceWindowStat(SecondsPerKm(roundHalfUpToInt(paces.map { it.value }.average())), paces.size)
        }
}
