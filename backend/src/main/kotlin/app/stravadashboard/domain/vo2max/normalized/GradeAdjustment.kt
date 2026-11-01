package app.stravadashboard.domain.vo2max.normalized

import app.stravadashboard.domain.model.MetersPerSecond
import app.stravadashboard.domain.model.Split

/** The flat-equivalent speed of one per-km split (feature 016 R2). */
object GradeAdjustment {
    /** Fallback grades beyond ±10 % are clamped, so GPS elevation spikes can't dominate. */
    const val MAX_FALLBACK_GRADE = 0.10

    /** Minetti's energy cost of running on the flat, J/kg/m. */
    const val FLAT_COST = 3.6

    private const val C5 = 155.4
    private const val C4 = -30.4
    private const val C3 = -43.3
    private const val C2 = 46.3
    private const val C1 = 19.5

    /**
     * Strava's grade-adjusted speed when it has one, else plain speed corrected by Minetti's cost curve on the
     * split's net grade, else plain speed. Null when the split has no time or distance.
     */
    fun equivalentSpeed(split: Split): MetersPerSecond? {
        val distance = split.distance.value
        val seconds = split.movingTime.value
        val stravaSpeed = split.gradeAdjustedSpeed?.value
        val elevation = split.elevationDifference?.value
        return when {
            seconds <= 0 || distance <= 0.0 -> null
            stravaSpeed != null && stravaSpeed > 0.0 -> MetersPerSecond(stravaSpeed)
            elevation == null -> MetersPerSecond(distance / seconds)
            else -> {
                val grade = (elevation / distance).coerceIn(-MAX_FALLBACK_GRADE, MAX_FALLBACK_GRADE)
                MetersPerSecond(distance / seconds * minettiCost(grade) / FLAT_COST)
            }
        }
    }

    /** Minetti et al. (2002): `C(i) = 155.4i⁵ − 30.4i⁴ − 43.3i³ + 46.3i² + 19.5i + 3.6`, J/kg/m. */
    fun minettiCost(grade: Double): Double =
        ((((C5 * grade + C4) * grade + C3) * grade + C2) * grade + C1) * grade + FLAT_COST
}
