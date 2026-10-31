package app.stravadashboard.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

const val METERS_PER_KM = 1000.0
const val SECONDS_PER_MINUTE = 60

@JvmInline
value class ActivityId(
    val value: Long,
)

@JvmInline
value class Meters(
    val value: Double,
)

@JvmInline
value class Seconds(
    val value: Int,
)

@JvmInline
value class SecondsPerKm(
    val value: Int,
)

@JvmInline
value class Bpm(
    val value: Int,
)

@JvmInline
value class MetersPerSecond(
    val value: Double,
)

/** Metres to kilometres, rounded half-up to one decimal. */
fun Meters.toKmOneDecimal(): Double = roundOneDecimal(value / METERS_PER_KM)

fun roundOneDecimal(value: Double): Double = BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).toDouble()

fun roundHalfUpToInt(value: Double): Int = BigDecimal.valueOf(value).setScale(0, RoundingMode.HALF_UP).toInt()

/** Total seconds over total kilometres, rounded half-up to whole seconds per km. */
fun paceOf(
    totalSeconds: Double,
    totalMeters: Double,
): SecondsPerKm = SecondsPerKm(roundHalfUpToInt(totalSeconds / (totalMeters / METERS_PER_KM)))
