package app.stravadashboard.domain.rings

import app.stravadashboard.domain.ports.RingDayRepository
import app.stravadashboard.domain.ports.WellnessRepository
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/** Today's rings and their history, from the local cache only (020 R7, 023 R5). All null without Garmin. */
class RingsService(
    private val configured: Boolean,
    private val wellness: WellnessRepository,
    private val rings: RingDayRepository,
    private val clock: Clock,
    private val zone: ZoneId,
) {
    suspend fun get(): RingsView {
        val today = LocalDate.now(clock.withZone(zone))
        if (!configured) return RingsView(today, null, null, null)
        // Recovery's 30-day baseline needs each of those days' own 30-day baselines.
        val from = today.minusDays(2 * RingsPolicy.BASELINE_DAYS + 1)
        return RingsCalculator(wellness.findBetween(from, today), rings.findBetween(from, today)).build(today)
    }

    /** The last [days] days, oldest first and today last (023 R5); all null when Garmin isn't connected. */
    suspend fun history(days: Int): List<RingsHistoryDay> {
        val today = LocalDate.now(clock.withZone(zone))
        val first = today.minusDays(days - 1L)
        if (!configured) return RingsCalculator(emptyList(), emptyMap()).history(first, today)
        // The first day needs 30 days of HRV / resting-HR baseline and 3 nights of sleep debt before it.
        val from = first.minusDays(RingsPolicy.BASELINE_DAYS + 1)
        return RingsCalculator(wellness.findBetween(from, today), rings.findBetween(from, today)).history(first, today)
    }
}
