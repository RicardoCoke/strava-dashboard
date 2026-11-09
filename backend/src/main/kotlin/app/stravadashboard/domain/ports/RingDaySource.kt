package app.stravadashboard.domain.ports

import app.stravadashboard.domain.rings.DayFetchResult
import java.time.LocalDate

/** One day's sleep, plus all-day heart rate when [withHeartRate], from Garmin Connect (feature 020). */
interface RingDaySource {
    suspend fun fetchDay(
        date: LocalDate,
        withHeartRate: Boolean,
    ): DayFetchResult
}
