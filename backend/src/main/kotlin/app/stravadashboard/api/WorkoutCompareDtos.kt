package app.stravadashboard.api

import kotlinx.serialization.Serializable

// The JSON contract of GET /api/workout-compare/runs and GET /api/workout-compare
// (see docs/features/025-workout-compare/specs.md). Mirrored by frontend/src/app/core/api/workout-compare.models.ts.

@Serializable
data class CompareRunsResponse(
    val runs: List<CompareRunItemDto>,
)

@Serializable
data class CompareRunItemDto(
    val id: Long,
    val date: String,
    val name: String,
    val typeKey: String,
    val distanceKm: Double,
    val averagePaceSecondsPerKm: Int?,
    val treadmill: Boolean,
)

@Serializable
data class WorkoutCompareResponse(
    val resolution: String,
    val coarse: CoarseDto?,
    val runs: List<CompareRunDto>,
    val reps: RepComparisonDto?,
)

@Serializable
data class CoarseDto(
    val reason: String,
    val runId: Long?,
    val retryAfter: String?,
)

@Serializable
data class CompareRunDto(
    val id: Long,
    val date: String,
    val name: String,
    val typeKey: String,
    val treadmill: Boolean,
    val stats: CompareStatsDto,
    val points: List<ComparePointDto>,
)

@Serializable
data class CompareStatsDto(
    val distanceKm: Double,
    val movingTimeSeconds: Int?,
    val averagePaceSecondsPerKm: Int?,
    val averageHeartRateBpm: Int?,
    val elevationGainMeters: Int?,
    val efficiencyFactor: Double?,
    val hrDriftPct: Double?,
)

@Serializable
data class ComparePointDto(
    val distanceKm: Double,
    val timeSeconds: Int,
    val paceSecondsPerKm: Int?,
    val heartRateBpm: Int?,
)

@Serializable
data class RepComparisonDto(
    val mode: String,
    val unavailableReason: String?,
    val unavailableRunId: Long?,
    val pieceMinutes: Int?,
    val piecesRunId: Long?,
    val average: RepRowDto?,
    val pairs: List<RepRowDto>,
)

@Serializable
data class RepRowDto(
    val earlier: RepCellDto?,
    val later: RepCellDto?,
    val paceDiffSecondsPerKm: Int?,
    val heartRateDiffBpm: Int?,
)

@Serializable
data class RepCellDto(
    val paceSecondsPerKm: Int,
    val heartRateBpm: Int?,
)
