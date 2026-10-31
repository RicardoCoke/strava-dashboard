package app.stravadashboard.strava

import java.time.Instant

/** Outcome of one Strava HTTP call. Expected failures are values; nothing is thrown across layers. */
sealed interface StravaResult<out T> {
    data class Success<T>(
        val value: T,
    ) : StravaResult<T>

    data object NotFound : StravaResult<Nothing>

    data object Unauthorized : StravaResult<Nothing>

    data class RateLimited(
        val until: Instant,
    ) : StravaResult<Nothing>

    data object Unavailable : StravaResult<Nothing>
}
