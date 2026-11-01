package app.stravadashboard.domain.wellness

import app.stravadashboard.domain.wellness.WellnessPolicy.BASELINE_DAYS
import app.stravadashboard.domain.wellness.WellnessPolicy.HRV_FLAG_FRACTION
import app.stravadashboard.domain.wellness.WellnessPolicy.MIN_BASELINE_VALUES
import app.stravadashboard.domain.wellness.WellnessPolicy.RHR_FLAG_BPM
import app.stravadashboard.domain.wellness.WellnessPolicy.WINDOW_DAYS
import java.time.LocalDate

/** Builds the 84-day chart series, 7-day baselines and latest flags from stored days (feature 015, R5 and R6). */
object WellnessSeries {
    /** How one metric is read from a day, how its delta is measured, and when that delta is flagged. */
    private class Metric(
        val value: (WellnessDay) -> Double?,
        val delta: (value: Double, baseline: Double) -> Double,
        val flagged: (delta: Double) -> Boolean,
    )

    private val restingHrMetric =
        Metric(
            value = { it.restingHr?.value?.toDouble() },
            delta = { value, baseline -> value - baseline },
            flagged = { it >= RHR_FLAG_BPM },
        )

    private val hrvMetric =
        Metric(
            value = { it.hrv?.value },
            delta = { value, baseline -> (value - baseline) / baseline },
            flagged = { it <= -HRV_FLAG_FRACTION },
        )

    fun build(
        stored: List<WellnessDay>,
        today: LocalDate,
    ): WellnessView {
        val byDate = stored.associateBy { it.date }
        val start = today.minusDays(WINDOW_DAYS - 1L)
        val dates = (0 until WINDOW_DAYS).map { start.plusDays(it.toLong()) }
        val points =
            dates.map { date ->
                val day = byDate[date]
                WellnessPoint(
                    date = date,
                    restingHr = day?.restingHr,
                    restingHrBaseline = baseline(byDate, date, restingHrMetric),
                    hrv = day?.hrv,
                    hrvBaseline = baseline(byDate, date, hrvMetric),
                )
            }
        return WellnessView(
            windowStart = start,
            windowEnd = today,
            points = points,
            restingHr = latest(byDate, dates, restingHrMetric),
            hrv = latest(byDate, dates, hrvMetric),
        )
    }

    /** The mean of the values on the 7 days before [date] ([date] excluded); null with fewer than 3 values. */
    private fun baseline(
        byDate: Map<LocalDate, WellnessDay>,
        date: LocalDate,
        metric: Metric,
    ): Double? {
        val values = (1..BASELINE_DAYS).mapNotNull { back -> byDate[date.minusDays(back.toLong())]?.let(metric.value) }
        return if (values.size >= MIN_BASELINE_VALUES) values.average() else null
    }

    private fun latest(
        byDate: Map<LocalDate, WellnessDay>,
        dates: List<LocalDate>,
        metric: Metric,
    ): LatestReading? {
        val date = dates.lastOrNull { byDate[it]?.let(metric.value) != null } ?: return null
        val value = requireNotNull(byDate[date]?.let(metric.value)) { "latest date has a value" }
        val baseline = baseline(byDate, date, metric)
        val delta = baseline?.let { metric.delta(value, it) }
        val flagged = delta?.let(metric.flagged) ?: false
        return LatestReading(date, value, baseline, delta, flagged, streak(byDate, date, metric))
    }

    /** Consecutive days ending at [latest] on which the metric has a value, a baseline and is flagged. */
    private fun streak(
        byDate: Map<LocalDate, WellnessDay>,
        latest: LocalDate,
        metric: Metric,
    ): Int =
        generateSequence(latest) { it.minusDays(1) }
            .takeWhile { date -> isFlagged(byDate, date, metric) }
            .count()

    private fun isFlagged(
        byDate: Map<LocalDate, WellnessDay>,
        date: LocalDate,
        metric: Metric,
    ): Boolean {
        val value = byDate[date]?.let(metric.value)
        val baseline = baseline(byDate, date, metric)
        return value != null && baseline != null && metric.flagged(metric.delta(value, baseline))
    }
}
