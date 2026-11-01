package app.stravadashboard.domain.vo2max.normalized

import app.stravadashboard.domain.RunClassifier
import app.stravadashboard.domain.model.METERS_PER_KM
import app.stravadashboard.domain.model.RunActivity
import app.stravadashboard.domain.model.RunType
import app.stravadashboard.domain.model.Seconds
import app.stravadashboard.domain.model.Split
import app.stravadashboard.domain.vo2max.DistanceRepPattern
import app.stravadashboard.domain.vo2max.Segment
import app.stravadashboard.domain.vo2max.SegmentExtractor
import app.stravadashboard.domain.vo2max.SegmentMethod

/**
 * The hill- and drift-adjusted segment of a run (feature 016 R3): outdoor easy and long runs are judged on the
 * grade-adjusted pace and heart rate of their first 60 minutes; everything else follows feature 008.
 */
object NormalizedSegmentExtractor {
    const val EARLY_PORTION_SECONDS = 3600

    private data class Piece(
        val seconds: Double,
        val speed: Double,
        val heartRate: Double,
    )

    /** The spec's R3 table, applied top to bottom; the first matching row decides. Null = excluded or pending. */
    fun segmentOf(run: RunActivity): Segment? {
        val movingTime = run.movingTime
        val splits = run.splits
        return when {
            DistanceRepPattern.matches(run.name) || movingTime == null -> null
            run.trainer || RunClassifier.classify(run) is RunType.Workout -> SegmentExtractor.segmentOf(run)
            !qualifiesAsWholeRun(run, movingTime) || splits == null -> null
            else -> earlySegmentOrWholeRun(run, earlyPieces(splits))
        }
    }

    /** Row 7, or row 6 (008's whole run) when no usable split piece with heart rate is left. */
    private fun earlySegmentOrWholeRun(
        run: RunActivity,
        pieces: List<Piece>,
    ): Segment? = if (pieces.isEmpty()) SegmentExtractor.segmentOf(run) else earlySegment(run, pieces)

    /** 008's whole-run checks (row 4): heart rate, at least 20 minutes and a distance. */
    private fun qualifiesAsWholeRun(
        run: RunActivity,
        movingTime: Seconds,
    ): Boolean =
        run.averageHeartRateBpm != null &&
            movingTime.value >= SegmentExtractor.MIN_RUN_MOVING_SECONDS &&
            run.distance.value > 0.0

    /** The usable splits' pieces within the first 60 minutes; the split crossing 60 minutes is prorated. */
    private fun earlyPieces(splits: List<Split>): List<Piece> {
        val usable =
            splits.sortedBy { it.index }.mapNotNull { split ->
                val speed = GradeAdjustment.equivalentSpeed(split)
                speed?.let { Pair(split, it) }
            }
        val startSeconds = usable.runningFold(0) { before, (split, _) -> before + split.movingTime.value }
        return usable
            .zip(startSeconds)
            .filter { (_, before) -> before < EARLY_PORTION_SECONDS }
            .mapNotNull { (usableSplit, before) ->
                val (split, speed) = usableSplit
                val seconds = minOf(split.movingTime.value, EARLY_PORTION_SECONDS - before)
                split.averageHeartRateBpm
                    ?.takeIf { it > 0.0 }
                    ?.let { Piece(seconds.toDouble(), speed.value, it) }
            }
    }

    private fun earlySegment(
        run: RunActivity,
        pieces: List<Piece>,
    ): Segment? {
        val duration = pieces.sumOf { it.seconds }
        val pace = duration / (pieces.sumOf { it.speed * it.seconds } / METERS_PER_KM)
        val plausible =
            duration >= SegmentExtractor.MIN_RUN_MOVING_SECONDS &&
                pace in SegmentExtractor.MIN_PACE_SECONDS..SegmentExtractor.MAX_PACE_SECONDS
        val heartRate = pieces.sumOf { it.heartRate * it.seconds } / duration
        return if (plausible) {
            Segment(run.localDate, pace, heartRate, Seconds(duration.toInt()), SegmentMethod.EARLY_SPLITS)
        } else {
            null
        }
    }
}
