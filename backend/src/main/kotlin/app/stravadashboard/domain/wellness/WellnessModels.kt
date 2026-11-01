package app.stravadashboard.domain.wellness

import app.stravadashboard.domain.model.Bpm
import java.time.Instant
import java.time.LocalDate

/** Overnight HRV (rMSSD) in milliseconds. */
@JvmInline
value class HrvMs(
    val value: Double,
)

/** One day of wellness data from Intervals.icu (feature 015); [date] is the athlete's local date. */
data class WellnessDay(
    val date: LocalDate,
    val restingHr: Bpm?,
    val hrv: HrvMs?,
)

enum class WellnessFetchStatus { OK, RATE_LIMITED, UNAVAILABLE, KEY_REJECTED }

/** When the wellness cache was last refreshed, and how the last attempt ended. */
data class WellnessState(
    val lastSuccessAt: Instant? = null,
    val lastAttemptAt: Instant? = null,
    val lastStatus: WellnessFetchStatus? = null,
    val retryAfter: Instant? = null,
)

sealed interface WellnessFetchResult {
    data class Success(
        val days: List<WellnessDay>,
    ) : WellnessFetchResult

    data object KeyRejected : WellnessFetchResult

    data class RateLimited(
        val until: Instant,
    ) : WellnessFetchResult

    data object Unavailable : WellnessFetchResult
}

/** One chart point: unrounded values and baselines (null when missing). */
data class WellnessPoint(
    val date: LocalDate,
    val restingHr: Bpm?,
    val restingHrBaseline: Double?,
    val hrv: HrvMs?,
    val hrvBaseline: Double?,
)

/** The latest reading of one metric; [delta] is in bpm for resting HR and a fraction for HRV. */
data class LatestReading(
    val date: LocalDate,
    val value: Double,
    val baseline: Double?,
    val delta: Double?,
    val flagged: Boolean,
    val flagStreak: Int,
)

data class WellnessView(
    val windowStart: LocalDate,
    val windowEnd: LocalDate,
    val points: List<WellnessPoint>,
    val restingHr: LatestReading?,
    val hrv: LatestReading?,
) {
    val daysWithData: Int get() = points.count { it.restingHr != null || it.hrv != null }
}
