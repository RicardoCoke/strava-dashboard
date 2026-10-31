package app.stravadashboard.strava

import io.ktor.http.Headers
import java.time.Instant

/** Works out when Strava's rate limit lifts, from `X-ReadRateLimit-*` (or `X-RateLimit-*`) headers. */
object RateLimitHeaders {
    private const val QUARTER_HOUR_SECONDS = 15 * 60L
    private const val DAY_SECONDS = 24 * 60 * 60L

    private data class Limits(
        val daily: Int,
        val dailyUsage: Int,
    )

    fun retryAfter(
        headers: Headers,
        now: Instant,
    ): Instant {
        val limits = limits(headers, "X-ReadRateLimit") ?: limits(headers, "X-RateLimit")
        val dailyExhausted = limits != null && limits.dailyUsage >= limits.daily
        val step = if (dailyExhausted) DAY_SECONDS else QUARTER_HOUR_SECONDS
        return Instant.ofEpochSecond((now.epochSecond / step + 1) * step)
    }

    /** Header values look like `"<15-minute>,<daily>"`. */
    private fun limits(
        headers: Headers,
        prefix: String,
    ): Limits? {
        val daily =
            headers["$prefix-Limit"]
                ?.split(",")
                ?.getOrNull(1)
                ?.trim()
                ?.toIntOrNull()
        val usage =
            headers["$prefix-Usage"]
                ?.split(",")
                ?.getOrNull(1)
                ?.trim()
                ?.toIntOrNull()
        return if (daily != null && usage != null) Limits(daily, usage) else null
    }
}
