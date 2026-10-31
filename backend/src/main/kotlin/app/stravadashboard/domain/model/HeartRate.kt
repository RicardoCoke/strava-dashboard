package app.stravadashboard.domain.model

/** One sample per entry: seconds since start and heart rate. Used transiently, never stored. */
data class HeartRateStream(
    val times: List<Seconds>,
    val bpm: List<Bpm>,
)

/** Seconds spent at each whole bpm. Has no timestamps, so the heart-rate series can't be rebuilt from it. */
data class HrHistogram(
    val secondsByBpm: Map<Int, Int>,
)

enum class HrHistogramStatus { COMPUTED, UNAVAILABLE }

/** [histogram] is null when [status] is [HrHistogramStatus.UNAVAILABLE]. */
data class HrHistogramResult(
    val status: HrHistogramStatus,
    val histogram: HrHistogram?,
)
