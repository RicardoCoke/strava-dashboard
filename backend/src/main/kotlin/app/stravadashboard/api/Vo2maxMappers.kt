package app.stravadashboard.api

import app.stravadashboard.domain.model.roundOneDecimal
import app.stravadashboard.domain.vo2max.CurrentBasis
import app.stravadashboard.domain.vo2max.CurrentVo2max
import app.stravadashboard.domain.vo2max.RaceDistance
import app.stravadashboard.domain.vo2max.RacePrediction
import app.stravadashboard.domain.vo2max.Vo2maxReport
import app.stravadashboard.domain.vo2max.WeekVo2max

const val MARATHON_NOTE = "Marathon predictions from VO2max tend to be optimistic."

fun Vo2maxReport.toDto(): Vo2maxResponse =
    Vo2maxResponse(
        maxHr = MaxHrDto(maxHr.value, if (maxHrConfigured) "configured" else "default"),
        current = current?.toDto(),
        predictions = predictions.map { it.toDto() },
        windowStart = windowStart.toString(),
        windowEnd = windowEnd.toString(),
        weeks = weeks.map { it.toDto() },
        runsPendingDetail = runsPendingDetail,
    )

private fun CurrentVo2max.toDto(): CurrentVo2maxDto =
    CurrentVo2maxDto(
        vo2max = roundOneDecimal(vo2max),
        basis =
            when (basis) {
                CurrentBasis.LAST_7_DAYS -> "last7Days"
                CurrentBasis.LAST_28_DAYS -> "last28Days"
            },
        periodStart = periodStart.toString(),
        periodEnd = periodEnd.toString(),
        runCount = runCount,
    )

private fun RacePrediction.toDto(): RacePredictionDto =
    RacePredictionDto(
        key = distance.key,
        label = distance.label,
        timeSeconds = time.value,
        note = if (distance == RaceDistance.MARATHON) MARATHON_NOTE else null,
    )

private fun WeekVo2max.toDto(): WeekVo2maxDto =
    WeekVo2maxDto(
        weekStart = weekStart.toString(),
        weekEnd = weekEnd.toString(),
        vo2max = vo2max?.let(::roundOneDecimal),
        runCount = runCount,
        inProgress = inProgress,
    )
