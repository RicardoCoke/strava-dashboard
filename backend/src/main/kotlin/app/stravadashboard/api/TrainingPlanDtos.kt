package app.stravadashboard.api

import kotlinx.serialization.Serializable

// The JSON contract of GET /api/training-plan (see docs/features/004-calendar-like-build/specs.md).
// Mirrored by frontend/src/app/core/api/training-plan.models.ts.

@Serializable
data class TrainingPlanResponse(
    val startDate: String,
    val endDate: String,
    val progress: BuildProgressDto,
    val targetPaces: List<TargetPaceDto>,
    val weeks: List<PlanWeekDto>,
)

@Serializable
data class BuildProgressDto(
    val phase: String,
    val percentComplete: Int,
    val daysPassed: Int,
    val totalDays: Int,
    val currentWeekIndex: Int?,
    val weekCount: Int,
    val daysToGo: Int?,
    val daysUntilStart: Int?,
    val label: String,
)

@Serializable
data class TargetPaceDto(
    val key: String,
    val label: String,
    val targetSecondsPerKm: Int?,
    val minSecondsPerKm: Int?,
    val maxSecondsPerKm: Int?,
)

@Serializable
data class PlanWeekDto(
    val index: Int,
    val label: String,
    val startDate: String,
    val endDate: String,
    val distanceKm: Double,
    val easyPercent: Double,
    val subtPercent: Double,
    val isCurrentWeek: Boolean,
    val days: List<PlanDayDto>,
)

@Serializable
data class PlanDayDto(
    val date: String,
    val dayOfWeek: String,
    val session: String,
    val note: String?,
    val totalMinutes: Int,
    val easyMinutes: Int,
    val subtMinutes: Int,
    val distanceKm: Double,
    val isRest: Boolean,
    val race: PlanRaceDto?,
    val isToday: Boolean,
    val isPast: Boolean,
)

@Serializable
data class PlanRaceDto(
    val label: String,
    val distanceKm: Double,
    val isMarathon: Boolean,
)
