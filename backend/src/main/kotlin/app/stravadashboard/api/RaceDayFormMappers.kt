package app.stravadashboard.api

import app.stravadashboard.domain.model.roundHalfUpToInt
import app.stravadashboard.domain.model.roundOneDecimal
import app.stravadashboard.domain.racedayform.DayKind
import app.stravadashboard.domain.racedayform.FormDay
import app.stravadashboard.domain.racedayform.ProjectionStatus
import app.stravadashboard.domain.racedayform.READY_HIGH_PERCENT
import app.stravadashboard.domain.racedayform.READY_LOW_PERCENT
import app.stravadashboard.domain.racedayform.RaceDayForm
import app.stravadashboard.domain.racedayform.RaceDaySummary

fun RaceDayForm.toDto(): RaceDayFormResponse =
    RaceDayFormResponse(
        today = today.toString(),
        raceDate = raceDate.toString(),
        chartStart = chartStart.toString(),
        chartEnd = chartEnd.toString(),
        lastActualDate = lastActualDate.toString(),
        projection =
            when (status) {
                ProjectionStatus.PROJECTED -> "projected"
                ProjectionStatus.NOT_ENOUGH_RUNS -> "notEnoughRuns"
            },
        subtEstimated = subtEstimated,
        readyBand = ReadyBandDto(READY_LOW_PERCENT, READY_HIGH_PERCENT),
        raceDay = raceDay?.toDto(),
        days = days.map { it.toDto() },
    )

private fun FormDay.toDto(): FormDayDto =
    FormDayDto(
        date = date.toString(),
        kind = kind.json(),
        load = oneDecimal(load),
        fitness = oneDecimal(fitness),
        fatigue = oneDecimal(fatigue),
        form = oneDecimal(form),
        readyLow = oneDecimal(readyLow),
        readyHigh = oneDecimal(readyHigh),
    )

/** The gap from the peak uses the rounded numbers, so the label always adds up (AC9.3). */
private fun RaceDaySummary.toDto(): RaceDayDto {
    val fromPeak = roundHalfUpToInt(fitness) - roundHalfUpToInt(peakFitness)
    val bracket = if (fromPeak == 0) "at peak" else "${signed(fromPeak)} from peak"
    return RaceDayDto(
        date = date.toString(),
        kind = kind.json(),
        form = oneDecimal(form),
        fitness = oneDecimal(fitness),
        peakFitness = oneDecimal(peakFitness),
        peakDate = peakDate.toString(),
        fitnessFromPeak = fromPeak,
        label = "Form ${signed(roundHalfUpToInt(form))} · Fitness ${roundHalfUpToInt(fitness)} ($bracket)",
    )
}

private fun DayKind.json(): String =
    when (this) {
        DayKind.ACTUAL -> "actual"
        DayKind.PLANNED -> "planned"
    }

/** "+18", "-3", "0"; ASCII minus, as in 005. */
private fun signed(value: Int): String = if (value > 0) "+$value" else value.toString()

/** Rounded half-up to 1 decimal; adding 0.0 turns a rounded -0.0 into 0.0. */
private fun oneDecimal(value: Double): Double = roundOneDecimal(value) + 0.0
