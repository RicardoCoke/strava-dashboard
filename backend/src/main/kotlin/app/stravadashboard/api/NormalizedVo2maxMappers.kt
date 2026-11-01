package app.stravadashboard.api

import app.stravadashboard.domain.model.roundOneDecimal
import app.stravadashboard.domain.vo2max.normalized.NormalizedVo2maxReport
import app.stravadashboard.domain.vo2max.normalized.NormalizedWeek

fun NormalizedVo2maxReport.toDto(): NormalizedVo2maxResponse =
    NormalizedVo2maxResponse(
        maxHr = MaxHrDto(maxHr.value, if (maxHrConfigured) "configured" else "default"),
        current = current?.toDto(),
        predictions = predictions.map { it.toDto() },
        windowStart = windowStart.toString(),
        windowEnd = windowEnd.toString(),
        weeks = weeks.map { it.toDto() },
        runsPendingDetail = runsPendingDetail,
    )

private fun NormalizedWeek.toDto(): NormalizedWeekDto =
    NormalizedWeekDto(
        weekStart = weekStart.toString(),
        weekEnd = weekEnd.toString(),
        vo2max = vo2max?.let(::roundOneDecimal),
        runCount = runCount,
        inProgress = inProgress,
        originalVo2max = originalVo2max?.let(::roundOneDecimal),
    )
