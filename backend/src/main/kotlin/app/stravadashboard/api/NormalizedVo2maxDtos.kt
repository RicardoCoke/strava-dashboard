package app.stravadashboard.api

import kotlinx.serialization.Serializable

// The JSON contract of GET /api/vo2max/normalized (see docs/features/016-normalized-vo2max/specs.md).
// Mirrored by frontend/src/app/core/api/normalized-vo2max.models.ts.

@Serializable
data class NormalizedVo2maxResponse(
    val maxHr: MaxHrDto,
    val current: CurrentVo2maxDto?,
    val predictions: List<RacePredictionDto>,
    val windowStart: String,
    val windowEnd: String,
    val weeks: List<NormalizedWeekDto>,
    val runsPendingDetail: Int,
)

@Serializable
data class NormalizedWeekDto(
    val weekStart: String,
    val weekEnd: String,
    val vo2max: Double?,
    val runCount: Int,
    val inProgress: Boolean,
    val originalVo2max: Double?,
)
