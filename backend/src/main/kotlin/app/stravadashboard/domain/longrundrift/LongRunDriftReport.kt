package app.stravadashboard.domain.longrundrift

import app.stravadashboard.domain.HeartRateTrend
import app.stravadashboard.domain.RunClassifier
import app.stravadashboard.domain.model.HrDriftStatus
import app.stravadashboard.domain.model.Meters
import app.stravadashboard.domain.model.RunActivity
import app.stravadashboard.domain.model.RunType
import app.stravadashboard.domain.model.roundOneDecimal
import java.time.LocalDate

enum class DriftZone { GOOD, WATCH, POOR }

/** [driftPct] is rounded half-up to one decimal; [zone] is derived from that rounded value. */
data class DriftPoint(
    val date: LocalDate,
    val driftPct: Double,
    val zone: DriftZone,
    val distance: Meters,
)

data class DriftAverage(
    val driftPct: Double,
    val zone: DriftZone,
    val runCount: Int,
)

data class DriftExcluded(
    val treadmill: Int,
    val tooShort: Int,
    val missingData: Int,
) {
    val total: Int get() = treadmill + tooShort + missingData
}

data class LongRunDriftReportData(
    val windowStart: LocalDate,
    val windowEnd: LocalDate,
    val points: List<DriftPoint>,
    val lastFourAverage: DriftAverage?,
    val excluded: DriftExcluded,
    val pendingCount: Int,
) {
    val latest: DriftPoint? get() = points.lastOrNull()
}

/** An outdoor long run with heart rate and a fetched detail whose drift stream hasn't been fetched yet. */
internal fun needsDriftStream(run: RunActivity): Boolean =
    run.hrDrift == null &&
        !run.trainer &&
        run.averageHeartRateBpm != null &&
        run.detailFetchedAt != null &&
        RunClassifier.classify(run) == RunType.LongRun

/** Long runs (feature 006 rule) over the last five months, each counted once as a point, excluded or pending. */
object LongRunDriftReport {
    const val GOOD_BELOW_PCT = 5.0
    const val POOR_ABOVE_PCT = 10.0
    const val AVERAGE_RUNS = 4

    private enum class Bucket { TREADMILL, MISSING_DATA, TOO_SHORT, PENDING, POINT }

    fun compute(
        runs: List<RunActivity>,
        today: LocalDate,
    ): LongRunDriftReportData {
        val windowStart = today.minusMonths(HeartRateTrend.WINDOW_MONTHS)
        val byBucket =
            runs
                .filter {
                    it.isRun &&
                        it.localDate in windowStart..today &&
                        RunClassifier.classify(it) == RunType.LongRun
                }.groupBy(::bucketOf)
        val computed = byBucket[Bucket.POINT].orEmpty().sortedBy { it.startLocal }
        val count = { bucket: Bucket -> byBucket[bucket].orEmpty().size }
        return LongRunDriftReportData(
            windowStart = windowStart,
            windowEnd = today,
            points = computed.map(::pointOf),
            lastFourAverage = averageOf(computed.takeLast(AVERAGE_RUNS).mapNotNull { it.hrDrift?.driftPct }),
            excluded = DriftExcluded(count(Bucket.TREADMILL), count(Bucket.TOO_SHORT), count(Bucket.MISSING_DATA)),
            pendingCount = count(Bucket.PENDING),
        )
    }

    fun zoneOf(roundedPct: Double): DriftZone =
        when {
            roundedPct < GOOD_BELOW_PCT -> DriftZone.GOOD
            roundedPct > POOR_ABOVE_PCT -> DriftZone.POOR
            else -> DriftZone.WATCH
        }

    private fun bucketOf(run: RunActivity): Bucket {
        val status = run.hrDrift?.status
        return when {
            run.trainer -> Bucket.TREADMILL
            run.averageHeartRateBpm == null || status == HrDriftStatus.NO_DATA -> Bucket.MISSING_DATA
            status == HrDriftStatus.TOO_SHORT -> Bucket.TOO_SHORT
            status == null -> Bucket.PENDING
            else -> Bucket.POINT
        }
    }

    private fun pointOf(run: RunActivity): DriftPoint {
        val rounded = roundOneDecimal(requireNotNull(run.hrDrift?.driftPct) { "computed drift has a value" })
        return DriftPoint(run.localDate, rounded, zoneOf(rounded), run.distance)
    }

    private fun averageOf(values: List<Double>): DriftAverage? {
        if (values.isEmpty()) return null
        val rounded = roundOneDecimal(values.average())
        return DriftAverage(rounded, zoneOf(rounded), values.size)
    }
}
