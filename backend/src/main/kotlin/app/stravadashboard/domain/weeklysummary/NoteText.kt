package app.stravadashboard.domain.weeklysummary

import app.stravadashboard.domain.model.SECONDS_PER_MINUTE
import java.math.BigDecimal
import java.math.RoundingMode

// The shared text pieces of the 017 notes (specs.md, "Shared text pieces").

internal const val FOUR_WEEKS = "your 4-week average"
internal const val LAST_WEEK = "last week"
private const val PERCENT = 100.0
private const val MINUTES_PER_HOUR = 60

private fun rounded(
    value: Double,
    decimals: Int,
): BigDecimal = BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP)

/** Half-up to [decimals], without a locale (always a `.` separator). */
internal fun fixed(
    value: Double,
    decimals: Int,
): String = rounded(value, decimals).toPlainString()

/** Null when the baseline is 0, so the segment is dropped. */
internal fun percentOf(
    value: Double,
    baseline: Double,
): Double? = if (baseline == 0.0) null else (value - baseline) / baseline * PERCENT

internal fun pct(
    percent: Double?,
    ref: String,
    decimals: Int = 0,
): String? {
    val value = percent?.let { rounded(it, decimals) } ?: return null
    return when (value.signum()) {
        1 -> "up ${value.toPlainString()}% vs $ref"
        -1 -> "down ${value.abs().toPlainString()}% vs $ref"
        else -> "level with $ref"
    }
}

internal fun delta(
    difference: Double?,
    decimals: Int,
    unit: String,
    ref: String,
): String? {
    val value = difference?.let { rounded(it, decimals) } ?: return null
    return when (value.signum()) {
        1 -> "+${value.toPlainString()} $unit vs $ref"
        -1 -> "-${value.abs().toPlainString()} $unit vs $ref"
        else -> "same as $ref"
    }
}

/** ` (a, b)` from the segments that exist, or null when none does (the note is then skipped). */
internal fun bracket(vararg segments: String?): String? =
    segments.filterNotNull().takeIf { it.isNotEmpty() }?.joinToString(", ", " (", ")")

/** Whole minutes: `H h MM min` from an hour upwards, else `M min`. */
internal fun duration(minutes: Int): String =
    if (minutes >= MINUTES_PER_HOUR) {
        "${minutes / MINUTES_PER_HOUR} h ${(minutes % MINUTES_PER_HOUR).toString().padStart(2, '0')} min"
    } else {
        "$minutes min"
    }

internal fun durationDelta(
    seconds: Int,
    ref: String,
): String {
    val minutes = rounded(seconds.toDouble() / SECONDS_PER_MINUTE, 0).toInt()
    return when {
        minutes > 0 -> "+${duration(minutes)} vs $ref"
        minutes < 0 -> "-${duration(-minutes)} vs $ref"
        else -> "same as $ref"
    }
}

internal fun paceText(secondsPerKm: Int): String =
    "${secondsPerKm / SECONDS_PER_MINUTE}:${(secondsPerKm % SECONDS_PER_MINUTE).toString().padStart(2, '0')} /km"

/** [difference] is this week minus the other period, in s/km: negative is faster. */
internal fun paceDelta(
    difference: Int,
    ref: String,
): String =
    when {
        difference < 0 -> "${-difference} s/km faster than $ref"
        difference > 0 -> "$difference s/km slower than $ref"
        else -> "same pace as $ref"
    }
