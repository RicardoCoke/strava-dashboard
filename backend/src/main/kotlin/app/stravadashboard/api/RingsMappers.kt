package app.stravadashboard.api

import app.stravadashboard.domain.model.roundHalfUpToInt
import app.stravadashboard.domain.model.roundOneDecimal
import app.stravadashboard.domain.rings.RingValue
import app.stravadashboard.domain.rings.RingsCalculator
import app.stravadashboard.domain.rings.RingsHistoryDay
import app.stravadashboard.domain.rings.RingsPolicy
import app.stravadashboard.domain.rings.RingsView
import java.math.BigDecimal
import java.math.RoundingMode

private const val FILL_DECIMALS = 3
private const val VS_AVG = " vs 30-day avg"

fun RingsView.toDto(): RingsResponse =
    RingsResponse(
        date = date.toString(),
        sleep = sleep?.let { SleepRingDto(roundHalfUpToInt(it.value), percentFill(it), pointsLabel(it)) },
        recovery =
            recovery?.let {
                RecoveryRingDto(
                    percent = roundHalfUpToInt(it.value),
                    zone = RingsCalculator.zoneOf(it.value).name.lowercase(),
                    fill = percentFill(it),
                    deltaLabel = pointsLabel(it),
                )
            },
        // fill comes from the shown (rounded) number, so the arc matches the text (AC7.6).
        strain =
            strain?.let { ring ->
                StrainRingDto(
                    value = roundOneDecimal(ring.value),
                    fill = fill(roundOneDecimal(ring.value) / RingsPolicy.STRAIN_MAX),
                    deltaLabel = ring.baseline?.let { signedOneDecimal(roundOneDecimal(ring.value - it)) + VS_AVG },
                )
            },
    )

fun List<RingsHistoryDay>.toHistoryDto(): RingsHistoryResponse =
    RingsHistoryResponse(
        map { day ->
            RingsHistoryDayDto(
                date = day.date.toString(),
                sleepPercent = day.sleep?.let(::roundHalfUpToInt),
                recoveryPercent = day.recovery?.let(::roundHalfUpToInt),
                recoveryZone = day.recovery?.let { RingsCalculator.zoneOf(it).name.lowercase() },
                strain = day.strain?.let(::roundOneDecimal),
            )
        },
    )

private fun percentFill(ring: RingValue): Double = fill(roundHalfUpToInt(ring.value) / RingsPolicy.MAX_PERCENT)

private fun fill(fraction: Double): Double =
    BigDecimal.valueOf(fraction.coerceIn(0.0, 1.0)).setScale(FILL_DECIMALS, RoundingMode.HALF_UP).toDouble()

/** `+5 pts vs 30-day avg`: percentage points, so it isn't read as +5 %. */
private fun pointsLabel(ring: RingValue): String? =
    ring.baseline?.let { "${signedInt(roundHalfUpToInt(ring.value - it))} pts$VS_AVG" }
