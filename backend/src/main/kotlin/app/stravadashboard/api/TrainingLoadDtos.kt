package app.stravadashboard.api

import kotlinx.serialization.Serializable

// The JSON contract of GET /api/training-load (see docs/features/005-training-load/specs.md).
// Mirrored by frontend/src/app/core/api/training-load.models.ts.

@Serializable
data class TrainingLoadResponse(
    val windowStart: String,
    val windowEnd: String,
    val fitnessDays: Int,
    val fatigueDays: Int,
    val zones: LoadZonesDto,
    val latest: LoadLatestDto,
    val days: List<LoadDayDto>,
    val ramp: RampDto,
    val runsCounted: Int,
    val runsSkippedNoHeartRate: Int,
    val runsPendingHeartRate: Int,
)

@Serializable
data class LoadZonesDto(
    val lthrBpm: Int,
    val easyBelowBpm: Int,
    val hardAboveBpm: Int,
    val label: String,
)

@Serializable
data class LoadLatestDto(
    val date: String,
    val fitness: Double,
    val fatigue: Double,
    val form: Double,
)

@Serializable
data class LoadDayDto(
    val date: String,
    val load: Double,
    val fitness: Double,
    val fatigue: Double,
    val form: Double,
)

@Serializable
data class RampDto(
    val thisWeek: WeekRampDto,
    val lastWeek: WeekRampDto,
)

@Serializable
data class WeekRampDto(
    val weekStart: String,
    val weekEnd: String,
    val weekToDate: Boolean,
    val load: Double,
    val previousLoad: Double,
    val changePercent: Int?,
    val previousWeekLight: Boolean,
    val warning: Boolean,
    val changeLabel: String?,
)
