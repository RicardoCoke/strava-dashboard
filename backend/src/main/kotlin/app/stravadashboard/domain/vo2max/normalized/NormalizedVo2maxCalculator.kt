package app.stravadashboard.domain.vo2max.normalized

import app.stravadashboard.domain.model.Bpm
import app.stravadashboard.domain.model.RunActivity
import app.stravadashboard.domain.needsSplits
import app.stravadashboard.domain.vo2max.Segment
import app.stravadashboard.domain.vo2max.Vo2maxCalculator
import app.stravadashboard.domain.vo2max.Vo2maxEstimator
import java.time.LocalDate

/**
 * The normalized VO2max report (feature 016 R5–R6): 008's periods and weeks, but one vote per run, plus the
 * original 008 weekly value for comparison.
 */
object NormalizedVo2maxCalculator {
    private data class Valued(
        val segment: Segment,
        val vo2max: Double,
    )

    fun compute(
        runs: List<RunActivity>,
        maxHr: Bpm,
        maxHrConfigured: Boolean,
        today: LocalDate,
    ): NormalizedVo2maxReport {
        val original = Vo2maxCalculator.compute(runs, maxHr, maxHrConfigured, today)
        val valued =
            runs
                .filter { it.isRun && it.localDate <= today }
                .mapNotNull(NormalizedSegmentExtractor::segmentOf)
                .mapNotNull { segment -> Vo2maxEstimator.estimate(segment, maxHr)?.let { Valued(segment, it) } }
        val current = Vo2maxCalculator.currentOf(valued, today, { it.segment.date }, ::mean)
        return NormalizedVo2maxReport(
            maxHr = maxHr,
            maxHrConfigured = maxHrConfigured,
            current = current,
            predictions = current?.let { Vo2maxCalculator.predictions(it.vo2max) }.orEmpty(),
            weeks =
                original.weeks.map { week ->
                    val inWeek = valued.filter { it.segment.date in week.weekStart..week.weekEnd }
                    NormalizedWeek(
                        weekStart = week.weekStart,
                        weekEnd = week.weekEnd,
                        vo2max = mean(inWeek),
                        runCount = inWeek.size,
                        inProgress = week.inProgress,
                        originalVo2max = week.vo2max,
                    )
                },
            windowStart = original.windowStart,
            windowEnd = original.windowEnd,
            runsPendingDetail =
                runs.count { Vo2maxCalculator.isPending(it, original.windowStart, today, ::needsSplits) },
        )
    }

    /** Plain mean: each run counts once, whatever its duration. */
    private fun mean(valued: List<Valued>): Double? =
        if (valued.isEmpty()) null else valued.sumOf { it.vo2max } / valued.size
}
