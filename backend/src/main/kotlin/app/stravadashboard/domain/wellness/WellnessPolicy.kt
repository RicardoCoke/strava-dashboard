package app.stravadashboard.domain.wellness

import app.stravadashboard.domain.SyncPolicy
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The fixed numbers and pure scheduling rules of the wellness card (feature 015). */
object WellnessPolicy {
    const val WINDOW_DAYS = 84
    const val BASELINE_DAYS = 7
    const val MIN_BASELINE_VALUES = 3

    /** Rows older than today minus this are pruned; the first fetch covers this many days plus today. */
    const val RETENTION_DAYS = 90L
    const val REFETCH_DAYS = 6L
    const val RHR_FLAG_BPM = 5.0
    const val HRV_FLAG_FRACTION = 0.15
    const val HIGH_LOAD_FORM = -10.0
    val WELLNESS_INTERVAL: Duration = SyncPolicy.SYNC_INTERVAL
    val RATE_LIMIT_FALLBACK: Duration = Duration.ofMinutes(15)

    fun isDue(
        state: WellnessState,
        now: Instant,
    ): Boolean {
        val rateLimited = state.retryAfter?.let { now < it } ?: false
        val waited = state.lastAttemptAt?.let { now >= it.plus(WELLNESS_INTERVAL) } ?: true
        return !rateLimited && waited
    }

    /** The first fetch covers today − 90 to today; later ones start 6 days before the last success, capped. */
    fun fetchRange(
        state: WellnessState,
        today: LocalDate,
        zone: ZoneId,
    ): ClosedRange<LocalDate> {
        val earliest = today.minusDays(RETENTION_DAYS)
        val fromLastSuccess =
            state.lastSuccessAt
                ?.atZone(zone)
                ?.toLocalDate()
                ?.minusDays(REFETCH_DAYS)
        val oldest = fromLastSuccess?.let { maxOf(it, earliest) } ?: earliest
        return minOf(oldest, today)..today
    }

    fun pruneCutoff(today: LocalDate): LocalDate = today.minusDays(RETENTION_DAYS)
}
