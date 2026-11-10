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

// GET /api/rings/history (docs/features/023-recovery-tab/specs.md).
@Serializable
data class RingsHistoryResponse(
    val days: List<RingsHistoryDayDto>,
)

@Serializable
data class RingsHistoryDayDto(
    val date: String,
    val sleepPercent: Int?,
    val recoveryPercent: Int?,
    val recoveryZone: String?,
    val strain: Double?,
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
