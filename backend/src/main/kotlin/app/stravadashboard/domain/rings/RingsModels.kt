package app.stravadashboard.domain.rings

import app.stravadashboard.domain.wellness.WellnessFetchResult
import java.time.Instant
import java.time.LocalDate

/** One all-day heart-rate sample; [bpm] is null when the watch had no reading. */
data class HrSample(
    val at: Instant,
    val bpm: Int?,
)

/** One day from Garmin for the rings: the night's sleep and, when asked for, the day's heart-rate samples. */
data class DayReading(
    val sleepSeconds: Int?,
    val heartRate: List<HrSample>?,
)

sealed interface DayFetchResult {
    data class Ok(
        val reading: DayReading,
    ) : DayFetchResult

    data class Failed(
        val result: WellnessFetchResult,
    ) : DayFetchResult
}

/** What is cached per day: sleep in seconds and the unrounded day strain (0 to 21), both null when missing. */
data class RingDay(
    val sleepSeconds: Int?,
    val strain: Double?,
)

enum class RecoveryZone { GREEN, YELLOW, RED }

/** A ring's unrounded value and its 30-day baseline (null with fewer than 3 days). */
data class RingValue(
    val value: Double,
    val baseline: Double?,
)

data class RingsView(
    val date: LocalDate,
    val sleep: RingValue?,
    val recovery: RingValue?,
    val strain: RingValue?,
)
