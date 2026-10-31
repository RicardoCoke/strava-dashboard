package app.stravadashboard.domain.model

/** One sample per entry: seconds since start and smoothed speed. Used transiently, never stored. */
data class SpeedStream(
    val times: List<Seconds>,
    val speeds: List<MetersPerSecond>,
)
