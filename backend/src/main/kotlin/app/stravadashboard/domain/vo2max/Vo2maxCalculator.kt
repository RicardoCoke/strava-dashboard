package app.stravadashboard.domain.vo2max

import app.stravadashboard.domain.model.Bpm
import app.stravadashboard.domain.model.RunActivity
import app.stravadashboard.domain.model.roundOneDecimal
import app.stravadashboard.domain.needsLapHeartRate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** The current VO2max, its race predictions and the weekly series, from stored runs. */
object Vo2maxCalculator {
    const val CURRENT_DAYS = 7L
    const val FALLBACK_DAYS = 28L
    const val WINDOW_MONTHS = 6L
    private const val DAYS_PER_WEEK = 7L

    private data class Valued(
        val segment: Segment,
        val vo2max: Double,
    )

    fun compute(
        runs: List<RunActivity>,
        maxHr: Bpm,
        maxHrConfigured: Boolean,
        today: LocalDate,
    ): Vo2maxReport {
        val valued =
            runs
                .filter { it.isRun && it.localDate <= today }
                .mapNotNull(SegmentExtractor::segmentOf)
                .mapNotNull { segment -> Vo2maxEstimator.estimate(segment, maxHr)?.let { Valued(segment, it) } }
        val current = current(valued, today)
        val windowStart = today.minusMonths(WINDOW_MONTHS).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val windowEnd = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        return Vo2maxReport(
            maxHr = maxHr,
            maxHrConfigured = maxHrConfigured,
            current = current,
            predictions = current?.let { predictions(it.vo2max) }.orEmpty(),
            weeks = weeks(valued, windowStart, windowEnd, today),
            windowStart = windowStart,
            windowEnd = windowEnd,
            runsPendingDetail = runs.count { isPending(it, windowStart, today) },
        )
    }

    private fun current(
        valued: List<Valued>,
        today: LocalDate,
    ): CurrentVo2max? =
        listOf(CurrentBasis.LAST_7_DAYS to CURRENT_DAYS, CurrentBasis.LAST_28_DAYS to FALLBACK_DAYS)
            .firstNotNullOfOrNull { (basis, days) ->
                val start = today.minusDays(days - 1)
                val inPeriod = valued.filter { it.segment.date in start..today }
                weightedMean(inPeriod)?.let { CurrentVo2max(it, basis, start, today, inPeriod.size) }
            }

    /** Uses the shown value (1 decimal), so the tiles match the number on screen. */
    private fun predictions(vo2max: Double): List<RacePrediction> {
        val vdot = roundOneDecimal(vo2max)
        return RaceDistance.entries.map { RacePrediction(it, DanielsFormulas.raceTime(vdot, it.meters)) }
    }

    private fun weeks(
        valued: List<Valued>,
        windowStart: LocalDate,
        windowEnd: LocalDate,
        today: LocalDate,
    ): List<WeekVo2max> =
        generateSequence(windowStart) { it.plusWeeks(1) }
            .takeWhile { it <= windowEnd }
            .map { monday ->
                val sunday = monday.plusDays(DAYS_PER_WEEK - 1)
                val inWeek = valued.filter { it.segment.date in monday..sunday }
                WeekVo2max(monday, sunday, weightedMean(inWeek), inWeek.size, inProgress = today in monday..sunday)
            }.toList()

    private fun weightedMean(valued: List<Valued>): Double? {
        val seconds = valued.sumOf { it.segment.duration.value }
        return if (seconds == 0) null else valued.sumOf { it.vo2max * it.segment.duration.value } / seconds
    }

    private fun isPending(
        run: RunActivity,
        windowStart: LocalDate,
        today: LocalDate,
    ): Boolean =
        run.localDate in windowStart..today &&
            run.averageHeartRateBpm != null &&
            (run.detailFetchedAt == null || needsLapHeartRate(run)) &&
            !DistanceRepPattern.matches(run.name)
}
