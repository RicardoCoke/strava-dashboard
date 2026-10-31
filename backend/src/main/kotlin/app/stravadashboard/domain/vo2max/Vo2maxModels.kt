package app.stravadashboard.domain.vo2max

import app.stravadashboard.domain.model.Bpm
import app.stravadashboard.domain.model.Seconds
import java.time.LocalDate

enum class SegmentMethod { WHOLE_RUN, REP_LAPS }

/** The pace + heart-rate part of one run that the VO2max estimate uses. [pace] is unrounded seconds per km. */
data class Segment(
    val date: LocalDate,
    val pace: Double,
    val heartRateBpm: Double,
    val duration: Seconds,
    val method: SegmentMethod,
)

enum class CurrentBasis { LAST_7_DAYS, LAST_28_DAYS }

/** Unrounded duration-weighted mean over [periodStart]…[periodEnd]. */
data class CurrentVo2max(
    val vo2max: Double,
    val basis: CurrentBasis,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val runCount: Int,
)

/** [vo2max] is null exactly when the week has no qualifying run. */
data class WeekVo2max(
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val vo2max: Double?,
    val runCount: Int,
    val inProgress: Boolean,
)

private const val FIVE_K_METERS = 5000.0
private const val TEN_K_METERS = 10_000.0
private const val HALF_MARATHON_METERS = 21_097.5
private const val MARATHON_METERS = 42_195.0

enum class RaceDistance(
    val key: String,
    val label: String,
    val meters: Double,
) {
    FIVE_K("5k", "5K", FIVE_K_METERS),
    TEN_K("10k", "10K", TEN_K_METERS),
    HALF("half", "Half marathon", HALF_MARATHON_METERS),
    MARATHON("marathon", "Marathon", MARATHON_METERS),
}

data class RacePrediction(
    val distance: RaceDistance,
    val time: Seconds,
)

data class Vo2maxReport(
    val maxHr: Bpm,
    val maxHrConfigured: Boolean,
    val current: CurrentVo2max?,
    val predictions: List<RacePrediction>,
    val weeks: List<WeekVo2max>,
    val windowStart: LocalDate,
    val windowEnd: LocalDate,
    val runsPendingDetail: Int,
)
