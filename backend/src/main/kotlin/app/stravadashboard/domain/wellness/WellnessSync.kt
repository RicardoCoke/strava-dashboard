package app.stravadashboard.domain.wellness

import app.stravadashboard.domain.ports.WellnessRepository
import app.stravadashboard.domain.ports.WellnessSource
import app.stravadashboard.domain.rings.RingsSync
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Refreshes the wellness cache from Garmin Connect (feature 015, R3 and R4). Runs inside the background sync job and
 * never touches activity sync state. Failures keep the cached rows; only statuses, counts and class names are logged.
 */
class WellnessSync(
    private val source: WellnessSource,
    private val repo: WellnessRepository,
    private val clock: Clock,
    private val zone: ZoneId,
    private val rings: RingsSync? = null,
) {
    @Suppress("TooGenericExceptionCaught")
    suspend fun refreshIfDue() {
        try {
            val now = clock.instant()
            val state = repo.getState()
            if (WellnessPolicy.isDue(state, now)) refresh(state, now)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Wellness refresh failed unexpectedly: {}", e::class.simpleName)
            recordUnavailable()
        }
    }

    private suspend fun refresh(
        state: WellnessState,
        now: Instant,
    ) {
        val today = LocalDate.ofInstant(now, zone)
        val range = WellnessPolicy.fetchRange(state, today, zone)
        val attempted = state.copy(lastAttemptAt = now, retryAfter = null)
        when (val result = source.fetch(range.start, range.endInclusive)) {
            is WellnessFetchResult.Success -> {
                val days = result.days.mapNotNull(::usable).filter { it.date in range }
                repo.replaceRange(range.start, range.endInclusive, days, now)
                repo.pruneBefore(WellnessPolicy.pruneCutoff(today))
                val ok = attempted.copy(lastStatus = WellnessFetchStatus.OK, lastSuccessAt = now)
                repo.saveState(ok)
                log.info("Wellness refresh finished: status={}, days={}", WellnessFetchStatus.OK, days.size)
                refreshRings(today, ok)
            }
            WellnessFetchResult.KeyRejected -> stopped(attempted, WellnessFetchStatus.KEY_REJECTED)
            is WellnessFetchResult.RateLimited ->
                stopped(attempted.copy(retryAfter = result.until), WellnessFetchStatus.RATE_LIMITED)
            WellnessFetchResult.Unavailable -> stopped(attempted, WellnessFetchStatus.UNAVAILABLE)
        }
    }

    /** A ring failure keeps the cache and the OK status (020 R3); only a 429 delays the next refresh. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun refreshRings(
        today: LocalDate,
        ok: WellnessState,
    ) {
        try {
            rings?.refresh(today)?.let { repo.saveState(ok.copy(retryAfter = it)) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Rings refresh failed unexpectedly: {}", e::class.simpleName)
        }
    }

    private suspend fun stopped(
        state: WellnessState,
        status: WellnessFetchStatus,
    ) {
        repo.saveState(state.copy(lastStatus = status))
        log.info("Wellness refresh stopped: status={}", status)
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun recordUnavailable() {
        try {
            repo.saveState(
                repo.getState().copy(lastStatus = WellnessFetchStatus.UNAVAILABLE, lastAttemptAt = clock.instant()),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("Could not record the failed wellness refresh: {}", e::class.simpleName)
        }
    }

    private companion object {
        val log = LoggerFactory.getLogger(WellnessSync::class.java)

        /** Non-positive values count as missing; a day with no usable value isn't stored. */
        fun usable(day: WellnessDay): WellnessDay? {
            val cleaned =
                day.copy(restingHr = day.restingHr?.takeIf { it.value > 0 }, hrv = day.hrv?.takeIf { it.value > 0 })
            return cleaned.takeIf { it.restingHr != null || it.hrv != null }
        }
    }
}
