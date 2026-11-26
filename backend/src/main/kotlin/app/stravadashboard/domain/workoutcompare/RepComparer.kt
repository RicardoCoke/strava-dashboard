package app.stravadashboard.domain.workoutcompare

import app.stravadashboard.domain.RepWindow
import app.stravadashboard.domain.RunClassifier
import app.stravadashboard.domain.StreamRepPaceFinder
import app.stravadashboard.domain.model.RunActivity
import app.stravadashboard.domain.model.RunType
import app.stravadashboard.domain.model.SECONDS_PER_MINUTE
import app.stravadashboard.domain.model.paceOf

/** One rep or piece: the distance covered and its heart-rate sums. */
private class Effort(
    val meters: Double,
    val bpm: BpmSums,
)

/**
 * Rep comparison of two workouts (feature 025, R9–R11): rep N against rep N when the rep lengths match, else the
 * longer session's reps cut from their start into pieces of the shorter length, the leftover ignored. Pure.
 */
object RepComparer {
    fun compare(
        earlier: RunActivity,
        later: RunActivity,
        earlierStream: RunStream?,
        laterStream: RunStream?,
    ): RepComparison? {
        val a = RunClassifier.classify(earlier) as? RunType.Workout
        val b = RunClassifier.classify(later) as? RunType.Workout
        return when {
            a == null || b == null -> null
            earlierStream == null || laterStream == null ->
                RepComparison(RepMode.UNAVAILABLE, UnavailableReason.NO_DETAILED_DATA)
            else -> compare(Side(earlier, a, earlierStream), Side(later, b, laterStream))
        }
    }

    private class Side(
        val run: RunActivity,
        val workout: RunType.Workout,
        val stream: RunStream,
    )

    private fun compare(
        earlier: Side,
        later: Side,
    ): RepComparison {
        val minutes = minOf(earlier.workout.minutes, later.workout.minutes)
        val a = efforts(earlier.stream, earlier.workout, minutes)
        val b = efforts(later.stream, later.workout, minutes)
        return when {
            a == null -> notFound(earlier.run)
            b == null -> notFound(later.run)
            else -> paired(earlier, later, a, b)
        }
    }

    private fun paired(
        earlier: Side,
        later: Side,
        a: List<Effort>,
        b: List<Effort>,
    ): RepComparison {
        val minutes = minOf(earlier.workout.minutes, later.workout.minutes)
        val seconds = (minutes * SECONDS_PER_MINUTE).toDouble()
        val pieces = earlier.workout.minutes != later.workout.minutes
        val cut = if (earlier.workout.minutes > later.workout.minutes) earlier else later
        return RepComparison(
            mode = if (pieces) RepMode.PIECES else RepMode.REPS,
            pieceMinutes = minutes.takeIf { pieces },
            piecesRunId = cut.run.id.takeIf { pieces },
            average = RepRow(average(a, seconds), average(b, seconds)),
            pairs =
                (0 until maxOf(a.size, b.size)).map { i ->
                    RepRow(a.getOrNull(i)?.let { cell(it, seconds) }, b.getOrNull(i)?.let { cell(it, seconds) })
                },
        )
    }

    /** The run's reps, each cut into `repMinutes / minutes` pieces; null when they aren't all found. */
    private fun efforts(
        stream: RunStream,
        workout: RunType.Workout,
        minutes: Int,
    ): List<Effort>? {
        val reps: List<RepWindow> = StreamRepPaceFinder.windows(stream.speed, workout) ?: return null
        val seconds = (minutes * SECONDS_PER_MINUTE).toDouble()
        val efforts =
            reps.flatMap { rep ->
                (0 until workout.minutes / minutes).map { piece ->
                    val start = rep.startSeconds + piece * seconds
                    val bpm = stream.drift?.let { CompareCalculator.bpmSums(it, start, start + seconds) }
                    Effort(stream.curve.at(start + seconds) - stream.curve.at(start), bpm ?: BpmSums(0.0, 0.0))
                }
            }
        // A rep without distance (a stopped watch) has no pace to compare.
        return efforts.takeIf { list -> list.all { it.meters > 0.0 } }
    }

    private fun cell(
        effort: Effort,
        seconds: Double,
    ): RepCell = RepCell(paceOf(seconds, effort.meters), effort.bpm.mean)

    private fun average(
        efforts: List<Effort>,
        seconds: Double,
    ): RepCell =
        RepCell(
            paceOf(efforts.size * seconds, efforts.sumOf { it.meters }),
            efforts.map { it.bpm }.reduce(BpmSums::plus).mean,
        )

    private fun notFound(run: RunActivity): RepComparison =
        RepComparison(RepMode.UNAVAILABLE, UnavailableReason.REPS_NOT_FOUND, unavailableRunId = run.id)
}
