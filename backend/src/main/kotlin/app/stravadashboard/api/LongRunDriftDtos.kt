package app.stravadashboard.api

import kotlinx.serialization.Serializable

// The JSON contract of GET /api/long-run-drift (see docs/features/013-long-run-hr-drift/specs.md).
// Mirrored by frontend/src/app/core/api/long-run-drift.models.ts.

@Serializable
data class LongRunDriftResponse(
    val windowStart: String,
    val windowEnd: String,
    val thresholds: DriftThresholdsDto,
    val latest: LatestDriftDto?,
    val lastFourAverage: DriftAverageDto?,
    val points: List<DriftPointDto>,
    val excluded: DriftExcludedDto,
    val pendingCount: Int,
)

@Serializable
data class DriftThresholdsDto(
    val goodBelowPct: Double,
    val poorAbovePct: Double,
)

@Serializable
data class LatestDriftDto(
    val date: String,
    val driftPct: Double,
    val zone: String,
)

@Serializable
data class DriftAverageDto(
    val driftPct: Double,
    val zone: String,
    val runCount: Int,
)

@Serializable
data class DriftPointDto(
    val date: String,
    val driftPct: Double,
    val zone: String,
    val distanceKm: Double,
)

@Serializable
data class DriftExcludedDto(
    val total: Int,
    val treadmill: Int,
    val tooShort: Int,
    val missingData: Int,
)
