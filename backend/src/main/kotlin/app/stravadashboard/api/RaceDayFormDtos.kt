package app.stravadashboard.api

import kotlinx.serialization.Serializable

// The JSON contract of GET /api/race-day-form (see docs/features/011-race-day-form/specs.md).
// Mirrored by frontend/src/app/core/api/race-day-form.models.ts.

@Serializable
data class RaceDayFormResponse(
    val today: String,
    val raceDate: String,
    val chartStart: String,
    val chartEnd: String,
    val lastActualDate: String,
    val projection: String,
    val subtEstimated: Boolean,
    val readyBand: ReadyBandDto,
    val raceDay: RaceDayDto?,
    val days: List<FormDayDto>,
)

@Serializable
data class ReadyBandDto(
    val lowPercent: Int,
    val highPercent: Int,
)

@Serializable
data class RaceDayDto(
    val date: String,
    val kind: String,
    val form: Double,
    val fitness: Double,
    val peakFitness: Double,
    val peakDate: String,
    val fitnessFromPeak: Int,
    val label: String,
)

@Serializable
data class FormDayDto(
    val date: String,
    val kind: String,
    val load: Double,
    val fitness: Double,
    val fatigue: Double,
    val form: Double,
    val readyLow: Double,
    val readyHigh: Double,
)
