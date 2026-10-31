package app.stravadashboard.api

import kotlinx.serialization.Serializable

// The JSON contract of GET /api/dashboard/running (see docs/features/001-running-dashboard/specs.md).
// Mirrored by frontend/src/app/core/api/running-dashboard.models.ts.

@Serializable
data class RunningDashboardResponse(
    val weeklyDistance: WeeklyDistanceDto,
    val heartRateTrend: HeartRateTrendDto,
    val workoutPaceComparison: WorkoutPaceComparisonDto,
    val weekComparison: WeekComparisonDto,
)

// Feature 007: this week vs last week, for the headline tiles.

@Serializable
data class WeekComparisonDto(
    val thisWeek: DateWindowDto,
    val lastWeek: DateWindowDto,
    val distance: DistanceDifferenceDto,
    val easyHeartRate: EasyHrWeekComparisonDto,
    val workoutPace: WorkoutPaceWeekComparisonDto,
)

@Serializable
data class DistanceDifferenceDto(
    val thisWeekKm: Double,
    val lastWeekKm: Double,
    val differenceKm: Double,
)

@Serializable
data class EasyHrWeekComparisonDto(
    val thisWeek: WeekHrStatDto?,
    val lastWeek: WeekHrStatDto?,
    val changeBpm: Double?,
    /** "thisWeek", "lastWeek", "bothWeeks" or null. */
    val missingIn: String?,
)

@Serializable
data class WeekHrStatDto(
    val averageHeartRateBpm: Double,
    val runCount: Int,
)

@Serializable
data class WorkoutPaceWeekComparisonDto(
    val thisWeek: WeekPaceStatDto?,
    val lastWeek: WeekPaceStatDto?,
    val changeSecondsPerKm: Int?,
    /** "thisWeek", "lastWeek", "bothWeeks" or null. */
    val missingIn: String?,
)

@Serializable
data class WeekPaceStatDto(
    val averageRepPaceSecondsPerKm: Int,
    val runCount: Int,
    val typeLabels: List<String>,
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
    /** "easy", "long" or "workout". */
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
