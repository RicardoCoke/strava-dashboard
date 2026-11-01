package app.stravadashboard.domain.model

/**
 * One sample per entry: seconds since start, smoothed speed, heart rate and, when Strava sends it, the moving flag.
 * Used transiently during a sync, never stored.
 */
data class DriftStream(
    val times: List<Seconds>,
    val speeds: List<MetersPerSecond>,
    val bpm: List<Bpm>,
    val moving: List<Boolean>?,
)

enum class HrDriftStatus { COMPUTED, TOO_SHORT, NO_DATA }

/** A long run's Pa:Hr drift (feature 013). [driftPct] is unrounded and null unless [status] is COMPUTED. */
data class HrDriftResult(
    val status: HrDriftStatus,
    val driftPct: Double?,
)
