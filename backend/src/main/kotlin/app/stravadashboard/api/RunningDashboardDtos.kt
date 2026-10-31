package app.stravadashboard.api

import kotlinx.serialization.Serializable

// The JSON contract of GET /api/dashboard/running (see docs/features/001-running-dashboard/specs.md).
// Mirrored by frontend/src/app/core/api/running-dashboard.models.ts.

@Serializable
data class RunningDashboardResponse(
    val sync: SyncInfoDto,
    val weeklyDistance: WeeklyDistanceDto,
    val heartRateTrend: HeartRateTrendDto,
    val workoutPaceComparison: WorkoutPaceComparisonDto,
)

@Serializable
data class SyncInfoDto(
    val status: String,
    val lastSyncedAt: String?,
    val retryAfter: String?,
    val runsPendingDetail: Int,
)

@Serializable
data class WeeklyDistanceDto(
    val weeks: List<WeekTotalDto>,
)

@Serializable
data class WeekTotalDto(
    val weekStart: String,
    val weekEnd: String,
    val label: String,
    val distanceKm: Double,
    val runCount: Int,
    val isCurrentWeek: Boolean,
)

@Serializable
data class HeartRateTrendDto(
    val windowStart: String,
    val windowEnd: String,
    val minRunsForComparison: Int,
    val types: List<HrRunTypeDto>,
)

@Serializable
data class HrRunTypeDto(
    val typeKey: String,
    val label: String,
    val kind: String,
    val runCount: Int,
    val runsWithoutHeartRate: Int,
    val hasEnoughData: Boolean,
    val points: List<HrPointDto>,
    val trend: HrTrendLineDto?,
)

@Serializable
data class HrPointDto(
    val date: String,
    val averageHeartRateBpm: Double,
    val distanceKm: Double,
    val treadmill: Boolean,
    val rising: Boolean,
)

@Serializable
data class HrTrendLineDto(
    val startDate: String,
    val startBpm: Double,
    val endDate: String,
    val endBpm: Double,
    val changeBpm: Double,
)

@Serializable
data class WorkoutPaceComparisonDto(
    val currentWindow: DateWindowDto,
    val previousWindow: DateWindowDto,
    val types: List<PaceTypeComparisonDto>,
)

@Serializable
data class DateWindowDto(
    val start: String,
    val end: String,
)

@Serializable
data class PaceTypeComparisonDto(
    val typeKey: String,
    val label: String,
    val current: PaceWindowStatDto?,
    val previous: PaceWindowStatDto?,
    val changeSecondsPerKm: Int?,
    val runsWithoutPace: Int,
    val hasEnoughData: Boolean,
)

@Serializable
data class PaceWindowStatDto(
    val averageRepPaceSecondsPerKm: Int,
    val runCount: Int,
)
