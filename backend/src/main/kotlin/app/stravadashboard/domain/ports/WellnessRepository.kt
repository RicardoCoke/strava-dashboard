package app.stravadashboard.domain.ports

import app.stravadashboard.domain.wellness.WellnessDay
import app.stravadashboard.domain.wellness.WellnessState
import java.time.Instant
import java.time.LocalDate

/** The local wellness cache (feature 015): one row per day plus one state row. */
interface WellnessRepository {
    /** Days from [from] to [to] inclusive, in ascending order. */
    suspend fun findBetween(
        from: LocalDate,
        to: LocalDate,
    ): List<WellnessDay>

    /** Replaces every stored day from [from] to [to] inclusive with [days], in one transaction. */
    suspend fun replaceRange(
        from: LocalDate,
        to: LocalDate,
        days: List<WellnessDay>,
        fetchedAt: Instant,
    )

    suspend fun pruneBefore(date: LocalDate)

    suspend fun getState(): WellnessState

    suspend fun saveState(state: WellnessState)
}
