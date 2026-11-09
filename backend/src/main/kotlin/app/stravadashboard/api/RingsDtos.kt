package app.stravadashboard.api

import kotlinx.serialization.Serializable

// The JSON contract of GET /api/rings (see docs/features/020-whoop-rings/specs.md).
// Mirrored by frontend/src/app/core/api/rings.models.ts.

@Serializable
data class RingsResponse(
    val date: String,
    val sleep: SleepRingDto?,
    val recovery: RecoveryRingDto?,
    val strain: StrainRingDto?,
)

@Serializable
data class SleepRingDto(
    val percent: Int,
    val fill: Double,
    val deltaLabel: String?,
)

@Serializable
data class RecoveryRingDto(
    val percent: Int,
    val zone: String,
    val fill: Double,
    val deltaLabel: String?,
)

@Serializable
data class StrainRingDto(
    val value: Double,
    val fill: Double,
    val deltaLabel: String?,
)
