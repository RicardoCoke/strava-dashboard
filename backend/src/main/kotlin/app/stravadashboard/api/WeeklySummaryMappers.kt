package app.stravadashboard.api

import app.stravadashboard.domain.weeklysummary.SummaryNote
import app.stravadashboard.domain.weeklysummary.WeeklySummary

fun List<WeeklySummary>.toDto(): WeeklySummariesResponse = WeeklySummariesResponse(map { it.toDto() })

private fun WeeklySummary.toDto(): WeeklySummaryDto =
    WeeklySummaryDto(
        weekStart = weekStart.toString(),
        weekEnd = weekEnd.toString(),
        inProgress = inProgress,
        runCount = runCount,
        revision = revision,
        notes = notes.map { it.toDto() },
    )

private fun SummaryNote.toDto(): SummaryNoteDto = SummaryNoteDto(metric.key, tone.name.lowercase(), text)
