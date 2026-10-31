package app.stravadashboard.api

import kotlinx.serialization.Serializable

// The JSON contract of GET /api/vo2max (see docs/features/008-vo2max-estimator/specs.md).
// Mirrored by frontend/src/app/core/api/vo2max.models.ts.

@Serializable
data class Vo2maxResponse(
    val maxHr: MaxHrDto,
    val current: CurrentVo2maxDto?,
    val predictions: List<RacePredictionDto>,
    val windowStart: String,
    val windowEnd: String,
    val weeks: List<WeekVo2maxDto>,
    val runsPendingDetail: Int,
)

@Serializable
data class MaxHrDto(
    val bpm: Int,
    val source: String,
)

@Serializable
data class CurrentVo2maxDto(
    val vo2max: Double,
    val basis: String,
    val periodStart: String,
    val periodEnd: String,
    val runCount: Int,
)

@Serializable
data class RacePredictionDto(
    val key: String,
    val label: String,
    val timeSeconds: Int,
    val note: String?,
)

@Serializable
data class WeekVo2maxDto(
    val weekStart: String,
    val weekEnd: String,
    val vo2max: Double?,
    val runCount: Int,
    val inProgress: Boolean,
)
