package app.stravadashboard.domain.rings

import app.stravadashboard.domain.model.Bpm
import app.stravadashboard.domain.ports.RingDayRepository
import app.stravadashboard.domain.ports.RingDaySource
import app.stravadashboard.domain.wellness.WellnessFetchResult
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Fills the ring cache from Garmin (feature 020, R2 and R3): every uncached day of the last 92, plus today and
 * yesterday again, one day at a time, stopping at the first failure. Only the night's sleep and the day's strain are
 * stored; heart-rate samples never leave memory. Without [lthr] no heart rate is fetched and strain stays null.
 */
class RingsSync(
    private val source: RingDaySource,
    private val repo: RingDayRepository,
    private val lthr: Bpm?,
    private val clock: Clock,
    private val zone: ZoneId,
) {
    /** Returns when to retry after a 429, otherwise null. */
    suspend fun refresh(today: LocalDate): Instant? {
        val from = today.minusDays(RingsPolicy.HISTORY_DAYS)
        val fetchedAt = repo.fetchedAt(from, today)
        // A day fetched before it ended holds partial strain (and maybe no sleep yet), so it counts as missing.
        val complete = fetchedAt.filter { (day, at) -> at >= day.plusDays(1).atStartOfDay(zone).toInstant() }.keys
        val days =
            generateSequence(from) { it.plusDays(1) }
                .takeWhile { it <= today }
                .filter { it !in complete || it >= today.minusDays(1) }
                .toList()
        var fetched = 0
        var stopped: WellnessFetchResult? = null
        for (day in days) {
            when (val result = source.fetchDay(day, withHeartRate = lthr != null)) {
                is DayFetchResult.Ok -> {
                    repo.upsert(day, ringDay(result.reading), clock.instant())
                    fetched++
                }
                is DayFetchResult.Failed -> {
                    stopped = result.result
                    break
                }
            }
        }
        if (stopped == null) repo.pruneBefore(today.minusDays(RingsPolicy.RETENTION_DAYS))
        // Counts and a status only: the values are health data.
        log.info("Rings refresh finished: days={}, stopped={}", fetched, statusOf(stopped))
        return (stopped as? WellnessFetchResult.RateLimited)?.until
    }

    private fun ringDay(reading: DayReading): RingDay =
        RingDay(
            sleepSeconds = reading.sleepSeconds?.takeIf { it > 0 },
            // shortcut: days fetched while LTHR was unset keep a null strain until pruned; re-fetch them if LTHR
            // is added later and that history matters.
            strain = lthr?.let { reading.heartRate?.let { samples -> StrainCalculator.strain(samples, it) } },
        )

    private fun statusOf(result: WellnessFetchResult?): String =
        when (result) {
            null -> "NONE"
            is WellnessFetchResult.RateLimited -> "RATE_LIMITED"
            WellnessFetchResult.KeyRejected -> "KEY_REJECTED"
            else -> "UNAVAILABLE"
        }

    private companion object {
        val log = LoggerFactory.getLogger(RingsSync::class.java)
    }
}
