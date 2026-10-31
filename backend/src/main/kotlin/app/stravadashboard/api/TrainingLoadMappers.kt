package app.stravadashboard.api

import app.stravadashboard.domain.model.roundOneDecimal
import app.stravadashboard.domain.trainingload.DailyLoad
import app.stravadashboard.domain.trainingload.HrZones
import app.stravadashboard.domain.trainingload.TrainingLoadCalculator
import app.stravadashboard.domain.trainingload.TrainingLoadResult
import app.stravadashboard.domain.trainingload.WeekRamp

fun TrainingLoadResult.Ready.toDto(): TrainingLoadResponse {
    val days = load.days.map { it.toDto() }
    val last = days.last()
    return TrainingLoadResponse(
        windowStart = load.windowStart.toString(),
        windowEnd = load.windowEnd.toString(),
        fitnessDays = TrainingLoadCalculator.FITNESS_DAYS,
        fatigueDays = TrainingLoadCalculator.FATIGUE_DAYS,
        zones = zones.toDto(),
        latest = LoadLatestDto(last.date, last.fitness, last.fatigue, last.form),
        days = days,
        ramp = RampDto(ramp.thisWeek.toDto("vs last week"), ramp.lastWeek.toDto("vs the week before")),
        runsCounted = load.counts.counted,
        runsSkippedNoHeartRate = load.counts.skippedNoHeartRate,
        runsPendingHeartRate = load.counts.pendingHeartRate,
    )
}

/** Form is rounded from the unrounded difference, not from the rounded fitness and fatigue. */
private fun DailyLoad.toDto(): LoadDayDto =
    LoadDayDto(
        date = date.toString(),
        load = roundOneDecimal(load),
        fitness = roundOneDecimal(fitness),
        fatigue = roundOneDecimal(fatigue),
        form = roundOneDecimal(form),
    )

private fun HrZones.toDto(): LoadZonesDto =
    LoadZonesDto(
        lthrBpm = lthrBpm,
        easyBelowBpm = easyBelowBpm,
        hardAboveBpm = hardAboveBpm,
        label =
            "Zones from LTHR $lthrBpm bpm: easy < $easyBelowBpm · subT $easyBelowBpm–$hardAboveBpm · " +
                "hard > $hardAboveBpm",
    )

private fun WeekRamp.toDto(comparedWith: String): WeekRampDto =
    WeekRampDto(
        weekStart = weekStart.toString(),
        weekEnd = weekEnd.toString(),
        weekToDate = weekToDate,
        load = roundOneDecimal(load),
        previousLoad = roundOneDecimal(previousLoad),
        changePercent = changePercent,
        previousWeekLight = previousWeekLight,
        warning = warning,
        changeLabel = changePercent?.let { "${signed(it)}% $comparedWith" },
    )

private fun signed(percent: Int): String = if (percent > 0) "+$percent" else percent.toString()
