package app.stravadashboard.api

import app.stravadashboard.domain.model.roundHalfUpToInt
import app.stravadashboard.domain.model.roundOneDecimal
import app.stravadashboard.domain.wellness.LatestReading
import app.stravadashboard.domain.wellness.WellnessFetchStatus
import app.stravadashboard.domain.wellness.WellnessPoint
import app.stravadashboard.domain.wellness.WellnessPolicy
import app.stravadashboard.domain.wellness.WellnessResult
import app.stravadashboard.domain.wellness.WellnessState
import java.util.Locale

private const val MINUS = "−"
private const val PERCENT = 100.0

fun WellnessResult.Ready.toDto(): WellnessResponse =
    WellnessResponse(
        windowStart = view.windowStart.toString(),
        windowEnd = view.windowEnd.toString(),
        baselineDays = WellnessPolicy.BASELINE_DAYS,
        daysWithData = view.daysWithData,
        days = view.points.map { it.toDto() },
        latest = WellnessLatestDto(view.restingHr?.toRestingHrDto(), view.hrv?.toHrvDto()),
        readiness = ReadinessDto(note, loadHigh),
        source = state.toDto(),
    )

private fun WellnessPoint.toDto(): WellnessDayDto =
    WellnessDayDto(
        date = date.toString(),
        restingHrBpm = restingHr?.value,
        restingHrBaselineBpm = restingHrBaseline?.let(::roundOneDecimal),
        hrvMs = hrv?.value?.let(::roundHalfUpToInt),
        hrvBaselineMs = hrvBaseline?.let(::roundOneDecimal),
    )

private fun LatestReading.toRestingHrDto(): RestingHrLatestDto {
    val deltaBpm = delta?.let(::roundOneDecimal)
    return RestingHrLatestDto(
        date = date.toString(),
        bpm = roundHalfUpToInt(value),
        baselineBpm = baseline?.let(::roundOneDecimal),
        deltaBpm = deltaBpm,
        deltaLabel = deltaBpm?.let { "${signedOneDecimal(it)} bpm vs baseline" },
        flagged = flagged,
    )
}

private fun LatestReading.toHrvDto(): HrvLatestDto {
    val deltaPercent = delta?.let { roundHalfUpToInt(it * PERCENT) }
    return HrvLatestDto(
        date = date.toString(),
        ms = roundHalfUpToInt(value),
        baselineMs = baseline?.let(::roundOneDecimal),
        deltaPercent = deltaPercent,
        deltaLabel = deltaPercent?.let { "${signedInt(it)}% vs baseline" },
        flagged = flagged,
    )
}

private fun WellnessState.toDto(): WellnessSourceDto =
    WellnessSourceDto(
        status =
            when (lastStatus) {
                null -> "pending"
                WellnessFetchStatus.OK -> "ok"
                WellnessFetchStatus.RATE_LIMITED -> "rateLimited"
                WellnessFetchStatus.UNAVAILABLE -> "unavailable"
                WellnessFetchStatus.KEY_REJECTED -> "keyRejected"
            },
        lastFetchedAt = lastSuccessAt?.toString(),
    )

/** `+5.6`, `−1.2` (U+2212) or `0.0`. */
internal fun signedOneDecimal(value: Double): String =
    when {
        value > 0 -> "+%.1f".format(Locale.ROOT, value)
        value < 0 -> MINUS + "%.1f".format(Locale.ROOT, -value)
        else -> "0.0"
    }

internal fun signedInt(value: Int): String =
    when {
        value > 0 -> "+$value"
        value < 0 -> "$MINUS${-value}"
        else -> "0"
    }
