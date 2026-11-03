package app.stravadashboard.domain.weeklysummary

import app.stravadashboard.domain.WeeklyDistance
import app.stravadashboard.domain.model.Meters
import app.stravadashboard.domain.model.RunActivity
import app.stravadashboard.domain.model.SECONDS_PER_MINUTE
import app.stravadashboard.domain.model.paceOf
import app.stravadashboard.domain.model.roundHalfUpToInt
import app.stravadashboard.domain.model.roundOneDecimal
import app.stravadashboard.domain.model.toKmOneDecimal
import app.stravadashboard.domain.trainingload.RampRate
import java.time.LocalDate

/** Volume, pace and load notes (R3–R6). */
internal object RunNotes {
    private const val WATCH_SUFFIX = ", watch load"

    private fun kmOf(runs: List<RunActivity>): Double = Meters(runs.sumOf { it.distance.value }).toKmOneDecimal()

    private fun isWatch(percent: Double?): Boolean = percent != null && percent > RampRate.RAMP_THRESHOLD_PERCENT

    /** Same km rule as the weekly chart and the 007 tiles. */
    fun distance(data: WeekData): SummaryNote? {
        val km = kmOf(data.week)
        val average =
            data.fourWeekStarts.map { s ->
                kmOf(data.fourWeeks.filter { WeeklyDistance.weekStartOf(it.localDate) == s })
            }
        val percent = percentOf(km, average.average())
        val segments =
            bracket(pct(percent, FOUR_WEEKS), delta(roundOneDecimal(km - kmOf(data.lastWeek)), 1, "km", LAST_WEEK))
                ?: return null
        val watch = isWatch(percent)
        val text = "Volume ${fixed(km, 1)} km over ${data.week.size} runs$segments" + if (watch) WATCH_SUFFIX else ""
        return SummaryNote(NoteMetric.DISTANCE, if (watch) NoteTone.WORSE else NoteTone.NEUTRAL, text)
    }

    /** Null when any run in [runs] has no stored moving time. */
    private fun secondsOf(runs: List<RunActivity>): Int? = runs.map { it.movingTime?.value ?: return null }.sum()

    fun time(data: WeekData): SummaryNote? {
        val week = secondsOf(data.week) ?: return null
        val average = secondsOf(data.fourWeeks)?.let { it.toDouble() / WeekData.BASELINE_WEEKS }
        val last = secondsOf(data.lastWeek)
        val minutes = roundHalfUpToInt(week.toDouble() / SECONDS_PER_MINUTE)
        return bracket(
            average?.let { pct(percentOf(week.toDouble(), it), FOUR_WEEKS) },
            last?.let { durationDelta(week - it, LAST_WEEK) },
        )?.let { SummaryNote(NoteMetric.TIME, NoteTone.NEUTRAL, "Time on feet ${duration(minutes)}$it") }
    }

    fun longestRun(data: WeekData): SummaryNote? {
        val best = data.week.maxOf { it.distance.value }
        val previous = data.fourWeeks.maxOfOrNull { it.distance.value } ?: return null
        val bestKm = fixed(Meters(best).toKmOneDecimal(), 1)
        return if (best > previous) {
            SummaryNote(NoteMetric.LONGEST_RUN, NoteTone.BETTER, "Longest run of the block: $bestKm km")
        } else {
            val previousKm = fixed(Meters(previous).toKmOneDecimal(), 1)
            SummaryNote(
                NoteMetric.LONGEST_RUN,
                NoteTone.NEUTRAL,
                "Longest run $bestKm km (longest in the previous 4 weeks: $previousKm km)",
            )
        }
    }

    private fun paceOfRuns(runs: List<RunActivity>): Int? {
        val timed = runs.filter { (it.movingTime?.value ?: 0) > 0 && it.distance.value > 0 }
        if (timed.isEmpty()) return null
        return paceOf(timed.sumOf { it.movingTime?.value ?: 0 }.toDouble(), timed.sumOf { it.distance.value }).value
    }

    fun pace(data: WeekData): SummaryNote? {
        val week = paceOfRuns(data.week) ?: return null
        return bracket(
            paceOfRuns(data.fourWeeks)?.let { paceDelta(week - it, FOUR_WEEKS) },
            paceOfRuns(data.lastWeek)?.let { paceDelta(week - it, LAST_WEEK) },
        )?.let { SummaryNote(NoteMetric.PACE, NoteTone.NEUTRAL, "Average pace ${paceText(week)}$it") }
    }

    /** [loads] from `TrainingLoadCalculator.dailyLoads`; skipped while a week's HR stream is still pending. */
    fun trainingLoad(
        data: WeekData,
        loads: Map<LocalDate, Double>,
    ): SummaryNote? {
        if (data.week.any { it.averageHeartRateBpm != null && it.hrHistogram == null }) return null
        val sumOf = { range: ClosedRange<LocalDate> -> loads.filterKeys { it in range }.values.sum() }
        val week = sumOf(data.range)
        val percent = percentOf(week, sumOf(data.fourRange) / WeekData.BASELINE_WEEKS)
        val watch = isWatch(percent)
        val tone = if (watch) NoteTone.WORSE else NoteTone.NEUTRAL
        return bracket(pct(percent, FOUR_WEEKS), pct(percentOf(week, sumOf(data.lastRange)), LAST_WEEK))?.let {
            SummaryNote(
                NoteMetric.TRAINING_LOAD,
                tone,
                "Training load ${roundHalfUpToInt(week)}$it" + if (watch) WATCH_SUFFIX else "",
            )
        }
    }
}
