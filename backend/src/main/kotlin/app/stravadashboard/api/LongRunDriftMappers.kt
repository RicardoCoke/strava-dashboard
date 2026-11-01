package app.stravadashboard.api

import app.stravadashboard.domain.longrundrift.DriftAverage
import app.stravadashboard.domain.longrundrift.DriftExcluded
import app.stravadashboard.domain.longrundrift.DriftPoint
import app.stravadashboard.domain.longrundrift.DriftZone
import app.stravadashboard.domain.longrundrift.LongRunDriftReport
import app.stravadashboard.domain.longrundrift.LongRunDriftReportData
import app.stravadashboard.domain.model.toKmOneDecimal

fun LongRunDriftReportData.toDto(): LongRunDriftResponse =
    LongRunDriftResponse(
        windowStart = windowStart.toString(),
        windowEnd = windowEnd.toString(),
        thresholds = DriftThresholdsDto(LongRunDriftReport.GOOD_BELOW_PCT, LongRunDriftReport.POOR_ABOVE_PCT),
        latest = latest?.let { LatestDriftDto(it.date.toString(), it.driftPct, it.zone.toDto()) },
        lastFourAverage = lastFourAverage?.toDto(),
        points = points.map { it.toDto() },
        excluded = excluded.toDto(),
        pendingCount = pendingCount,
    )

private fun DriftPoint.toDto(): DriftPointDto =
    DriftPointDto(date.toString(), driftPct, zone.toDto(), distance.toKmOneDecimal())

private fun DriftAverage.toDto(): DriftAverageDto = DriftAverageDto(driftPct, zone.toDto(), runCount)

private fun DriftExcluded.toDto(): DriftExcludedDto = DriftExcludedDto(total, treadmill, tooShort, missingData)

private fun DriftZone.toDto(): String = name.lowercase()
