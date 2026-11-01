package app.stravadashboard.api

import kotlinx.serialization.Serializable

// The JSON contract of GET /api/wellness (see docs/features/015-garmin-resting-hr-hrv/specs.md).
// Mirrored by frontend/src/app/core/api/wellness.models.ts.

@Serializable
data class WellnessResponse(
    val windowStart: String,
    val windowEnd: String,
    val baselineDays: Int,
    val daysWithData: Int,
    val days: List<WellnessDayDto>,
    val latest: WellnessLatestDto,
    val readiness: ReadinessDto,
    val source: WellnessSourceDto,
)

@Serializable
data class WellnessDayDto(
    val date: String,
    val restingHrBpm: Int?,
    val restingHrBaselineBpm: Double?,
    val hrvMs: Int?,
    val hrvBaselineMs: Double?,
)

@Serializable
data class WellnessLatestDto(
    val restingHr: RestingHrLatestDto?,
    val hrv: HrvLatestDto?,
)

@Serializable
data class RestingHrLatestDto(
    val date: String,
    val bpm: Int,
    val baselineBpm: Double?,
    val deltaBpm: Double?,
    val deltaLabel: String?,
    val flagged: Boolean,
)

@Serializable
data class HrvLatestDto(
    val date: String,
    val ms: Int,
    val baselineMs: Double?,
    val deltaPercent: Int?,
    val deltaLabel: String?,
    val flagged: Boolean,
)

@Serializable
data class ReadinessDto(
    val note: String?,
    val loadHigh: Boolean?,
)

/** [status] is one of `ok`, `pending`, `rateLimited`, `unavailable`, `keyRejected`. */
@Serializable
data class WellnessSourceDto(
    val status: String,
    val lastFetchedAt: String?,
)
