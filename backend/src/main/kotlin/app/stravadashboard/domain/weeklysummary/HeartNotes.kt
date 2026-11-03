package app.stravadashboard.domain.weeklysummary

import app.stravadashboard.domain.RunClassifier
import app.stravadashboard.domain.efficiency.EfficiencyFactorCalculator
import app.stravadashboard.domain.longrundrift.DriftZone
import app.stravadashboard.domain.longrundrift.LongRunDriftReport
import app.stravadashboard.domain.model.HrDriftStatus
import app.stravadashboard.domain.model.RunActivity
import app.stravadashboard.domain.model.RunType
import app.stravadashboard.domain.model.roundHalfUpToInt
import app.stravadashboard.domain.model.roundOneDecimal
import app.stravadashboard.domain.wellness.WellnessDay
import app.stravadashboard.domain.wellness.WellnessPolicy
import java.time.LocalDate

/** HR drift, efficiency factor, resting HR and HRV notes (R7, R8). */
internal object HeartNotes {
    const val EF_NOTABLE_PERCENT = 2.0
    private const val PERCENT = 100.0

    /** 013's POINT bucket: outdoor long runs with a computed drift. */
    private fun driftOf(runs: List<RunActivity>): Double? =
        runs
            .filter { !it.trainer && it.averageHeartRateBpm != null && RunClassifier.classify(it) == RunType.LongRun }
            .mapNotNull { run -> run.hrDrift?.takeIf { it.status == HrDriftStatus.COMPUTED }?.driftPct }
            .takeIf { it.isNotEmpty() }
            ?.average()

    fun drift(data: WeekData): SummaryNote? {
        val week = driftOf(data.week)?.let(::roundOneDecimal)
        val previous = driftOf(data.fourWeeks)
        if (week == null || previous == null) return null
        val (word, tone) =
            when (LongRunDriftReport.zoneOf(week)) {
                DriftZone.GOOD -> "good" to NoteTone.BETTER
                DriftZone.WATCH -> "worth watching" to NoteTone.NEUTRAL
                DriftZone.POOR -> "high" to NoteTone.WORSE
            }
        val text = "Long-run HR drift ${fixed(week, 1)}%, $word (previous 4 weeks: ${fixed(previous, 1)}%)"
        return SummaryNote(NoteMetric.HR_DRIFT, tone, text)
    }

    private fun efficiencyOf(
        runs: List<RunActivity>,
        today: LocalDate,
    ): Double? =
        runs
            .mapNotNull { EfficiencyFactorCalculator.efficiencyOf(it, today) }
            .takeIf { it.isNotEmpty() }
            ?.average()

    fun efficiency(
        data: WeekData,
        today: LocalDate,
    ): SummaryNote? {
        val week = efficiencyOf(data.week, today) ?: return null
        val fourWeek = efficiencyOf(data.fourWeeks, today)?.let { percentOf(week, it) }
        val lastWeek = efficiencyOf(data.lastWeek, today)?.let { percentOf(week, it) }
        val rounded = fourWeek?.let(::roundOneDecimal)
        val tone =
            when {
                rounded == null -> NoteTone.NEUTRAL
                rounded >= EF_NOTABLE_PERCENT -> NoteTone.BETTER
                rounded <= -EF_NOTABLE_PERCENT -> NoteTone.WORSE
                else -> NoteTone.NEUTRAL
            }
        return bracket(pct(fourWeek, FOUR_WEEKS, 1), pct(lastWeek, LAST_WEEK, 1))?.let {
            SummaryNote(NoteMetric.EFFICIENCY, tone, "Easy-run efficiency ${fixed(week, 2)} m/min/beat$it")
        }
    }

    /** Mean of the days in [range] with a value; null with fewer than 3 values. */
    private fun meanOf(
        days: List<WellnessDay>,
        range: ClosedRange<LocalDate>,
        value: (WellnessDay) -> Double?,
    ): Double? =
        days
            .filter { it.date in range }
            .mapNotNull(value)
            .takeIf { it.size >= WellnessPolicy.MIN_BASELINE_VALUES }
            ?.average()

    fun restingHr(
        data: WeekData,
        days: List<WellnessDay>,
    ): SummaryNote? {
        val value = { day: WellnessDay -> day.restingHr?.value?.toDouble() }
        val week = meanOf(days, data.range, value) ?: return null
        val fourWeek = meanOf(days, data.fourRange, value)?.let { roundOneDecimal(week - it) }
        val lastWeek = meanOf(days, data.lastRange, value)?.let { week - it }
        val tone = if (fourWeek != null && fourWeek >= WellnessPolicy.RHR_FLAG_BPM) NoteTone.WORSE else NoteTone.NEUTRAL
        return bracket(delta(fourWeek, 1, "bpm", FOUR_WEEKS), delta(lastWeek, 1, "bpm", LAST_WEEK))?.let {
            SummaryNote(NoteMetric.RESTING_HR, tone, "Resting HR averaged ${fixed(week, 1)} bpm$it")
        }
    }

    fun hrv(
        data: WeekData,
        days: List<WellnessDay>,
    ): SummaryNote? {
        val value = { day: WellnessDay -> day.hrv?.value }
        val week = meanOf(days, data.range, value) ?: return null
        val fourWeek = meanOf(days, data.fourRange, value)?.let { percentOf(week, it) }
        val lastWeek = meanOf(days, data.lastRange, value)?.let { percentOf(week, it) }
        val flagged = fourWeek != null && roundHalfUpToInt(fourWeek) <= -WellnessPolicy.HRV_FLAG_FRACTION * PERCENT
        val tone = if (flagged) NoteTone.WORSE else NoteTone.NEUTRAL
        return bracket(pct(fourWeek, FOUR_WEEKS), pct(lastWeek, LAST_WEEK))?.let {
            SummaryNote(NoteMetric.HRV, tone, "HRV averaged ${fixed(week, 0)} ms$it")
        }
    }
}
