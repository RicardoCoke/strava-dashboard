package app.stravadashboard.api

import app.stravadashboard.domain.efficiency.EfChange
import app.stravadashboard.domain.efficiency.EfCurrent
import app.stravadashboard.domain.efficiency.EfPoint
import app.stravadashboard.domain.efficiency.EfRunType
import app.stravadashboard.domain.efficiency.EfficiencyFactorCalculator
import app.stravadashboard.domain.efficiency.EfficiencyFactorReport
import app.stravadashboard.domain.model.RunType
import app.stravadashboard.domain.model.roundOneDecimal
import app.stravadashboard.domain.model.toKmOneDecimal
import java.math.BigDecimal
import java.math.RoundingMode

private const val EF_DECIMALS = 3

fun EfficiencyFactorReport.toDto(): EfficiencyFactorResponse =
    EfficiencyFactorResponse(
        windowStart = windowStart.toString(),
        windowEnd = windowEnd.toString(),
        rollingDays = EfficiencyFactorCalculator.ROLLING_DAYS.toInt(),
        minRunsForRolling = EfficiencyFactorCalculator.MIN_RUNS_FOR_ROLLING,
        current = current?.toDto(),
        change = change?.toDto(),
        points = points.map { it.toDto() },
        runsPendingDetail = runsPendingDetail,
    )

/** Half-up to 3 decimals, the precision of every efficiency value in the JSON. */
fun roundEfficiency(value: Double): Double =
    BigDecimal.valueOf(value).setScale(EF_DECIMALS, RoundingMode.HALF_UP).toDouble()

private fun EfCurrent.toDto(): EfCurrentDto = EfCurrentDto(roundEfficiency(efficiency), date.toString(), runCount)

private fun EfChange.toDto(): EfChangeDto =
    EfChangeDto(
        percent = roundOneDecimal(percent),
        fromDate = from.date.toString(),
        fromEfficiency = roundEfficiency(from.efficiency),
        toDate = to.date.toString(),
        toEfficiency = roundEfficiency(to.efficiency),
    )

private fun EfPoint.toDto(): EfPointDto =
    EfPointDto(
        date = date.toString(),
        runType =
            when (runType) {
                EfRunType.EASY -> RunType.Easy.key
                EfRunType.LONG -> RunType.LongRun.key
            },
        efficiency = roundEfficiency(efficiency),
        rollingEfficiency = rollingEfficiency?.let(::roundEfficiency),
        distanceKm = distance.toKmOneDecimal(),
        movingTimeSeconds = movingTime.value,
        paceSecondsPerKm = pace.value,
        averageHeartRateBpm = roundOneDecimal(averageHeartRateBpm),
    )
