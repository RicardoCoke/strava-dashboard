package app.stravadashboard.domain

import app.stravadashboard.domain.model.RunActivity
import app.stravadashboard.domain.model.RunType
import app.stravadashboard.domain.model.SecondsPerKm
import app.stravadashboard.domain.model.StreamPaceStatus
import app.stravadashboard.domain.model.roundHalfUpToInt

/**
 * Average work-rep pace of a workout run.
 * Treadmill: the description pace line.
 * Outdoor: the stored stream result, or fast km laps when streams are unavailable.
 */
object RepPaceCalculator {
    fun repPace(
        run: RunActivity,
        workout: RunType.Workout,
    ): SecondsPerKm? = if (run.trainer) treadmillPace(run) else outdoorPace(run, workout)

    /** True when an outdoor workout still needs a streams call for its current type. */
    fun needsStream(run: RunActivity): Boolean {
        val type = RunClassifier.classify(run)
        return !run.trainer &&
            run.detailFetchedAt != null &&
            type is RunType.Workout &&
            run.streamPace?.typeKey != type.key
    }

    private fun treadmillPace(run: RunActivity): SecondsPerKm? =
        TreadmillPaceParser.parsePaceLine(run.description)?.let { paces ->
            SecondsPerKm(roundHalfUpToInt(paces.map { it.value }.average()))
        }

    private fun outdoorPace(
        run: RunActivity,
        workout: RunType.Workout,
    ): SecondsPerKm? {
        val result = run.streamPace?.takeIf { it.typeKey == workout.key } ?: return null
        return when (result.status) {
            StreamPaceStatus.COMPUTED -> result.pace
            StreamPaceStatus.UNAVAILABLE -> run.laps?.let(FastLapPaceFinder::find)
        }
    }
}
