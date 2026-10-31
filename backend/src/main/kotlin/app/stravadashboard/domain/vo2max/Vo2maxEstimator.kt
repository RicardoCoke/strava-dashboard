package app.stravadashboard.domain.vo2max

import app.stravadashboard.domain.model.Bpm
import app.stravadashboard.domain.model.METERS_PER_KM
import app.stravadashboard.domain.model.SECONDS_PER_MINUTE

/** Runalyze-style VO2max of one segment: oxygen cost of its pace ÷ the %VO2max its heart rate implies (Swain). */
object Vo2maxEstimator {
    /** Below this share of max HR the HR → %VO2max relation is unstable, so the segment is left out. */
    const val MIN_HR_FRACTION = 0.65
    private const val SWAIN_OFFSET = 0.37
    private const val SWAIN_SLOPE = 0.64

    fun estimate(
        segment: Segment,
        maxHr: Bpm,
    ): Double? {
        val hrFraction = minOf(segment.heartRateBpm / maxHr.value, 1.0)
        if (hrFraction < MIN_HR_FRACTION) return null
        val metersPerMinute = SECONDS_PER_MINUTE * METERS_PER_KM / segment.pace
        val vo2Fraction = (hrFraction - SWAIN_OFFSET) / SWAIN_SLOPE
        return DanielsFormulas.oxygenCost(metersPerMinute) / vo2Fraction
    }
}
