package app.stravadashboard.api

import kotlinx.serialization.Serializable

// The JSON contract of GET /api/marathon-pace (see docs/features/012-marathon-pace-volume/specs.md).
// Mirrored by frontend/src/app/core/api/marathon-pace.models.ts.

@Serializable
data class MarathonPaceResponse(
    val goal: MpGoalDto,
    val buildStart: String,
    val buildEnd: String,
    val weeks: List<MpWeekDto>,
    val summary: MpSummaryDto,
    val runsPendingLaps: Int,
)

@Serializable
data class MpGoalDto(
    val paceSecondsPerKm: Int,
    val toleranceSecondsPerKm: Int,
    val fastestSecondsPerKm: Int,
    val slowestSecondsPerKm: Int,
    val minLapMeters: Int,
)

@Serializable
data class MpWeekDto(
    val weekIndex: Int,
    val weekStart: String,
    val weekEnd: String,
    val label: String,
    val mpKm: Double,
    val isCurrentWeek: Boolean,
    val isFuture: Boolean,
)

@Serializable
data class MpSummaryDto(
    val totalMpKm: Double,
    val thisWeekStart: String,
    val thisWeekEnd: String,
    val thisWeekMpKm: Double,
    val thisWeekTotalKm: Double,
    val thisWeekSharePercent: Int?,
)

// Kept here rather than in ErrorResponse.kt so this feature doesn't touch the shared file.
val MARATHON_PACE_NOT_CONFIGURED =
    ErrorResponse("marathon_pace_not_configured", "No goal marathon pace is configured.")

/** Fixed text: never includes the configured value. */
val MARATHON_PACE_INVALID_CONFIG =
    ErrorResponse(
        "marathon_pace_invalid_config",
        "MARATHON_GOAL_PACE is not a pace between 2:30 and 8:00 per km (m:ss).",
    )
