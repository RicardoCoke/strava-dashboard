package app.stravadashboard.domain.workoutcompare

import app.stravadashboard.domain.DistanceCurve
import app.stravadashboard.domain.model.ActivityId
import app.stravadashboard.domain.model.DriftStream
import app.stravadashboard.domain.model.Meters
import app.stravadashboard.domain.model.RunActivity
import app.stravadashboard.domain.model.Seconds
import app.stravadashboard.domain.model.SecondsPerKm
import app.stravadashboard.domain.model.SpeedStream
import java.time.Instant

enum class CompareFilter { ALL, WORKOUTS, LONG, EASY }

enum class Resolution { STREAM, SPLITS }

enum class CoarseReason {
    NOT_CONFIGURED,
    BEFORE_GARMIN,
    TREADMILL,
    NO_DETAIL_FILE,
    CALL_LIMIT,
    RATE_LIMITED,
    GARMIN_UNAVAILABLE,
}

/** Why both runs are shown from per-km splits. */
data class Coarse(
    val reason: CoarseReason,
    val runId: ActivityId? = null,
    val retryAfter: Instant? = null,
)

/** One run's per-second samples for one request; never stored. [drift] is null when it was fetched without HR. */
class RunStream(
    val speed: SpeedStream,
    val drift: DriftStream?,
) {
    internal val curve: DistanceCurve by lazy { DistanceCurve(speed) }
}

data class ComparePoint(
    val distance: Meters,
    val time: Seconds,
    val pace: SecondsPerKm?,
    val heartRate: Double?,
)

data class CompareStats(
    val distance: Meters,
    val movingTime: Seconds?,
    val pace: SecondsPerKm?,
    val averageHeartRateBpm: Double?,
    val elevationGain: Meters?,
    val efficiency: Double?,
    val driftPct: Double?,
)

data class RepCell(
    val pace: SecondsPerKm,
    val heartRate: Double?,
)

data class RepRow(
    val earlier: RepCell?,
    val later: RepCell?,
)

enum class RepMode { REPS, PIECES, UNAVAILABLE }

enum class UnavailableReason { NO_DETAILED_DATA, REPS_NOT_FOUND }

data class RepComparison(
    val mode: RepMode,
    val unavailableReason: UnavailableReason? = null,
    val unavailableRunId: ActivityId? = null,
    val pieceMinutes: Int? = null,
    val piecesRunId: ActivityId? = null,
    val average: RepRow? = null,
    val pairs: List<RepRow> = emptyList(),
)

data class CompareSide(
    val run: RunActivity,
    val stats: CompareStats,
    val points: List<ComparePoint>,
)

data class WorkoutComparison(
    val resolution: Resolution,
    val coarse: Coarse?,
    val earlier: CompareSide,
    val later: CompareSide,
    val reps: RepComparison?,
)

sealed interface CompareOutcome {
    data class Found(
        val comparison: WorkoutComparison,
    ) : CompareOutcome

    data object RunNotFound : CompareOutcome
}
