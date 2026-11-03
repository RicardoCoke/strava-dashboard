package app.stravadashboard.domain.ports

import app.stravadashboard.domain.wellness.WellnessFetchResult
import java.time.LocalDate

/** Daily resting HR and HRV from Garmin Connect (feature 015), for [oldest] to [newest] inclusive. */
interface WellnessSource {
    suspend fun fetch(
        oldest: LocalDate,
        newest: LocalDate,
    ): WellnessFetchResult
}
