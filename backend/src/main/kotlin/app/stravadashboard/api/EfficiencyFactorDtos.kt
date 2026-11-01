package app.stravadashboard.api

import kotlinx.serialization.Serializable

// The JSON contract of GET /api/efficiency-factor (see docs/features/014-easy-efficiency-factor/specs.md).
// Mirrored by frontend/src/app/core/api/efficiency-factor.models.ts.

@Serializable
data class EfficiencyFactorResponse(
    val windowStart: String,
    val windowEnd: String,
    val rollingDays: Int,
    val minRunsForRolling: Int,
    val current: EfCurrentDto?,
    val change: EfChangeDto?,
    val points: List<EfPointDto>,
    val runsPendingDetail: Int,
)

@Serializable
data class EfCurrentDto(
    val efficiency: Double,
    val date: String,
    val runCount: Int,
)

@Serializable
data class EfChangeDto(
    val percent: Double,
    val fromDate: String,
    val fromEfficiency: Double,
    val toDate: String,
    val toEfficiency: Double,
)

@Serializable
data class EfPointDto(
    val date: String,
    val runType: String,
    val efficiency: Double,
    val rollingEfficiency: Double?,
    val distanceKm: Double,
    val movingTimeSeconds: Int,
    val paceSecondsPerKm: Int,
    val averageHeartRateBpm: Double,
)
