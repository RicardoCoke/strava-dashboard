package app.stravadashboard.domain.ports

import app.stravadashboard.domain.rings.RingDay
import java.time.Instant
import java.time.LocalDate

/** Cached sleep and strain per day (feature 020). A stored day with null values was fetched and had no data. */
interface RingDayRepository {
    suspend fun findBetween(
        from: LocalDate,
        to: LocalDate,
    ): Map<LocalDate, RingDay>

    /** When each stored day from [from] to [to] was last fetched. */
    suspend fun fetchedAt(
        from: LocalDate,
        to: LocalDate,
    ): Map<LocalDate, Instant>

    suspend fun upsert(
        date: LocalDate,
        day: RingDay,
        fetchedAt: Instant,
    )

    suspend fun pruneBefore(date: LocalDate)
}
