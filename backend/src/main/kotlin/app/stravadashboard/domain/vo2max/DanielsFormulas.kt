package app.stravadashboard.domain.vo2max

import app.stravadashboard.domain.model.SECONDS_PER_MINUTE
import app.stravadashboard.domain.model.Seconds
import app.stravadashboard.domain.model.roundHalfUpToInt
import kotlin.math.exp

/** Daniels & Gilbert: the oxygen cost of running and the VDOT race-time relation. */
object DanielsFormulas {
    private const val COST_INTERCEPT = -4.60
    private const val COST_LINEAR = 0.182258
    private const val COST_SQUARE = 0.000104

    private const val PCT_BASE = 0.8
    private const val PCT_FAST_AMPLITUDE = 0.1894393
    private const val PCT_FAST_RATE = -0.012778
    private const val PCT_SLOW_AMPLITUDE = 0.2989558
    private const val PCT_SLOW_RATE = -0.1932605

    private const val MIN_RACE_MINUTES = 1.0
    private const val MAX_RACE_MINUTES = 1000.0
    private const val TIME_PRECISION_MINUTES = 0.01 / SECONDS_PER_MINUTE

    /** ml/kg/min at [metersPerMinute]. */
    fun oxygenCost(metersPerMinute: Double): Double =
        COST_INTERCEPT + COST_LINEAR * metersPerMinute + COST_SQUARE * metersPerMinute * metersPerMinute

    /** The share of VO2max that can be held for a race lasting [minutes]. */
    fun percentMaxForMinutes(minutes: Double): Double =
        PCT_BASE + PCT_FAST_AMPLITUDE * exp(PCT_FAST_RATE * minutes) + PCT_SLOW_AMPLITUDE * exp(PCT_SLOW_RATE * minutes)

    /** Solves `oxygenCost(d / t) / percentMax(t) = vdot` for `t` by bisection; the left side falls as `t` grows. */
    fun raceTime(
        vdot: Double,
        meters: Double,
    ): Seconds {
        var low = MIN_RACE_MINUTES
        var high = MAX_RACE_MINUTES
        while (high - low > TIME_PRECISION_MINUTES) {
            val mid = (low + high) / 2
            val vdotAtMid = oxygenCost(meters / mid) / percentMaxForMinutes(mid)
            if (vdotAtMid > vdot) low = mid else high = mid
        }
        return Seconds(roundHalfUpToInt((low + high) / 2 * SECONDS_PER_MINUTE))
    }
}
