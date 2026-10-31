package app.stravadashboard.domain

import java.time.Clock
import java.time.Instant

/** What a sync may still spend in the current window, as seen at one instant. */
data class BudgetView(
    val remaining: Int,
    val windowEnd: Instant,
)

/**
 * Counts Strava calls per fixed 15-minute block of UTC epoch time, the blocks Strava's own rate limit uses. 80 of
 * Strava's 100 leaves room for token refreshes and the client's retries, which aren't counted. In memory only: a
 * restart starts the count again at zero.
 */
class StravaCallBudget(
    private val clock: Clock,
    private val callsPerWindow: Int = CALLS_PER_WINDOW,
) {
    private var window = Long.MIN_VALUE
    private var used = 0

    @Synchronized
    fun remaining(): Int {
        roll()
        return callsPerWindow - used
    }

    /** Takes one call from the budget; false when the current window has none left. */
    @Synchronized
    fun tryConsume(): Boolean {
        roll()
        if (used >= callsPerWindow) return false
        used++
        return true
    }

    @Synchronized
    fun view(): BudgetView = BudgetView(remaining(), windowEnd())

    fun windowEnd(): Instant = Instant.ofEpochSecond((windowOf(clock.instant()) + 1) * WINDOW_SECONDS)

    private fun roll() {
        val current = windowOf(clock.instant())
        if (current != window) {
            window = current
            used = 0
        }
    }

    private fun windowOf(instant: Instant): Long = Math.floorDiv(instant.epochSecond, WINDOW_SECONDS)

    companion object {
        const val CALLS_PER_WINDOW = 80
        const val WINDOW_SECONDS = 900L
    }
}
