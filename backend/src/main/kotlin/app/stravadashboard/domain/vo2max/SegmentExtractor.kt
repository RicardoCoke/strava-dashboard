package app.stravadashboard.domain.vo2max

import app.stravadashboard.domain.RunClassifier
import app.stravadashboard.domain.TreadmillPaceParser
import app.stravadashboard.domain.model.Lap
import app.stravadashboard.domain.model.METERS_PER_KM
import app.stravadashboard.domain.model.RunActivity
import app.stravadashboard.domain.model.RunType
import app.stravadashboard.domain.model.Seconds

/** Picks the pace + heart-rate segment of a run for the VO2max estimate, or null when the run doesn't qualify. */
object SegmentExtractor {
    const val MIN_RUN_MOVING_SECONDS = 1200
    const val MIN_REP_MINUTES = 4
    private const val MIN_PACE_SECONDS = 120.0
    private const val MAX_PACE_SECONDS = 720.0

    /** The spec's R3 table, applied top to bottom; the first matching row decides. */
    fun segmentOf(run: RunActivity): Segment? {
        val movingTime = run.movingTime
        if (DistanceRepPattern.matches(run.name) || movingTime == null) return null
        return when (val type = RunClassifier.classify(run)) {
            is RunType.Workout ->
                if (type.minutes < MIN_REP_MINUTES || movingTime.value < MIN_RUN_MOVING_SECONDS) {
                    null
                } else {
                    repSegment(run, type)
                }
            RunType.Easy, RunType.LongRun -> wholeRunSegment(run, movingTime)
        }
    }

    private fun repSegment(
        run: RunActivity,
        workout: RunType.Workout,
    ): Segment? {
        val paceLine = if (run.trainer) TreadmillPaceParser.parsePaceLine(run.description) else null
        val reps = RepLapFinder.find(run.laps.orEmpty(), workout, run.trainer)
        // A treadmill workout needs the pace line: its lap distances come from the watch and aren't reliable.
        if (reps.isEmpty() || (run.trainer && paceLine == null)) return null
        val seconds = reps.sumOf { it.elapsed.value }
        val pace =
            paceLine?.map { it.value }?.average() ?: (seconds / (reps.sumOf { it.distance.value } / METERS_PER_KM))
        return Segment(run.localDate, pace, weightedHeartRate(reps), Seconds(seconds), SegmentMethod.REP_LAPS)
    }

    private fun weightedHeartRate(laps: List<Lap>): Double =
        laps.sumOf { (it.averageHeartRateBpm ?: 0.0) * it.elapsed.value } / laps.sumOf { it.elapsed.value }

    private fun wholeRunSegment(
        run: RunActivity,
        movingTime: Seconds,
    ): Segment? {
        val heartRate = run.averageHeartRateBpm
        if (heartRate == null || movingTime.value < MIN_RUN_MOVING_SECONDS) return null
        val pace =
            if (run.trainer) {
                TreadmillPaceParser.parsePaceLine(run.description)?.map { it.value }?.average()
            } else {
                outdoorPace(run, movingTime)
            }
        return pace?.let { Segment(run.localDate, it, heartRate, movingTime, SegmentMethod.WHOLE_RUN) }
    }

    /** Moving time over distance; null for no distance or an implausible pace (walk breaks, GPS errors). */
    private fun outdoorPace(
        run: RunActivity,
        movingTime: Seconds,
    ): Double? {
        if (run.distance.value <= 0.0) return null
        val pace = movingTime.value / (run.distance.value / METERS_PER_KM)
        return pace.takeIf { it in MIN_PACE_SECONDS..MAX_PACE_SECONDS }
    }
}
