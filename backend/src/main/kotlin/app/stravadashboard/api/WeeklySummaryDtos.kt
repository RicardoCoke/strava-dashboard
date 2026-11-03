package app.stravadashboard.api

import kotlinx.serialization.Serializable

// The JSON contract of GET /api/weekly-summaries (see docs/features/017-weekly-summary/specs.md).
// Mirrored by frontend/src/app/core/api/weekly-summary.models.ts.

@Serializable
data class WeeklySummariesResponse(
    val summaries: List<WeeklySummaryDto>,
)

@Serializable
data class WeeklySummaryDto(
    val weekStart: String,
    val weekEnd: String,
    val inProgress: Boolean,
    val runCount: Int,
    val revision: String,
    val notes: List<SummaryNoteDto>,
)

@Serializable
data class SummaryNoteDto(
    val metric: String,
    val tone: String,
    val text: String,
)
