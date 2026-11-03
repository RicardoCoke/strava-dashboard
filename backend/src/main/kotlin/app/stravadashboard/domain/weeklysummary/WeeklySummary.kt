package app.stravadashboard.domain.weeklysummary

import app.stravadashboard.domain.WeeklyDistance
import app.stravadashboard.domain.model.RunActivity
import app.stravadashboard.domain.trainingload.HrZones
import app.stravadashboard.domain.trainingload.TrainingLoadCalculator
import app.stravadashboard.domain.wellness.WellnessDay
import java.security.MessageDigest
import java.time.LocalDate
import java.util.HexFormat

/** Ranking order: earlier entries come first among notes of the same tone. */
enum class NoteMetric(
    val key: String,
) {
    DISTANCE("distance"),
    LONGEST_RUN("longestRun"),
    TRAINING_LOAD("trainingLoad"),
    HR_DRIFT("hrDrift"),
    RESTING_HR("restingHr"),
    HRV("hrv"),
    EFFICIENCY("efficiency"),
    TIME("time"),
    PACE("pace"),
}

/** Ranking order: notes that need attention first. */
enum class NoteTone { WORSE, BETTER, NEUTRAL }

data class SummaryNote(
    val metric: NoteMetric,
    val tone: NoteTone,
    val text: String,
)

data class WeeklySummary(
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val inProgress: Boolean,
    val runCount: Int,
    val notes: List<SummaryNote>,
    val revision: String,
)

/** A summary week's runs and its two baselines: last week and the previous 4 weeks (R2). */
internal class WeekData(
    val start: LocalDate,
    runs: List<RunActivity>,
) {
    val range = start..start.plusDays(DAYS_PER_WEEK - 1)
    val lastRange = start.minusDays(DAYS_PER_WEEK)..start.minusDays(1)
    val fourRange = start.minusDays(BASELINE_DAYS)..start.minusDays(1)
    val week = runs.filter { it.localDate in range }
    val lastWeek = runs.filter { it.localDate in lastRange }
    val fourWeeks = runs.filter { it.localDate in fourRange }
    val fourWeekStarts = (BASELINE_WEEKS downTo 1).map { start.minusWeeks(it.toLong()) }

    companion object {
        const val DAYS_PER_WEEK = 7L
        const val BASELINE_WEEKS = 4
        const val BASELINE_DAYS = DAYS_PER_WEEK * BASELINE_WEEKS
    }
}

/** Rule-based weekly summaries (see docs/features/017-weekly-summary/specs.md). */
object WeeklySummaryBuilder {
    const val MIN_RUNS = 5
    const val MAX_NOTES = 6
    const val WINDOW_MONTHS = 6L

    /** The earliest date any summary in the window can compare against. */
    fun oldestNeeded(today: LocalDate): LocalDate =
        WeeklyDistance.weekStartOf(today.minusMonths(WINDOW_MONTHS)).minusDays(WeekData.BASELINE_DAYS)

    fun build(
        runs: List<RunActivity>,
        wellness: List<WellnessDay>,
        zones: HrZones?,
        today: LocalDate,
    ): List<WeeklySummary> {
        val stored = runs.filter { it.isRun && it.localDate <= today }
        val loads = zones?.let { TrainingLoadCalculator.dailyLoads(stored, it, today) }
        val oldestWeek = WeeklyDistance.weekStartOf(today.minusMonths(WINDOW_MONTHS))
        return stored
            .groupBy { WeeklyDistance.weekStartOf(it.localDate) }
            .filter { (start, week) -> week.size >= MIN_RUNS && start >= oldestWeek }
            .keys
            .sortedDescending()
            .map { summaryOf(WeekData(it, stored), wellness, loads, today) }
    }

    private fun summaryOf(
        data: WeekData,
        wellness: List<WellnessDay>,
        loads: Map<LocalDate, Double>?,
        today: LocalDate,
    ): WeeklySummary {
        val candidates =
            listOf(
                RunNotes.distance(data),
                RunNotes.time(data),
                RunNotes.longestRun(data),
                RunNotes.pace(data),
                loads?.let { RunNotes.trainingLoad(data, it) },
                HeartNotes.drift(data),
                HeartNotes.efficiency(data, today),
                HeartNotes.restingHr(data, wellness),
                HeartNotes.hrv(data, wellness),
            )
        val notes =
            candidates
                .filterNotNull()
                .sortedWith(compareBy({ it.tone.ordinal }, { it.metric.ordinal }))
                .take(MAX_NOTES)
        return WeeklySummary(
            weekStart = data.start,
            weekEnd = data.range.endInclusive,
            inProgress = WeeklyDistance.weekStartOf(today) == data.start,
            runCount = data.week.size,
            notes = notes,
            revision = revisionOf(data.start, data.week.size, notes),
        )
    }

    /** Excludes `inProgress`, so the Monday rollover doesn't re-light the badge. */
    private fun revisionOf(
        weekStart: LocalDate,
        runCount: Int,
        notes: List<SummaryNote>,
    ): String {
        val text = "$weekStart|$runCount|" + notes.joinToString("\n") { it.text }
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)))
    }
}
