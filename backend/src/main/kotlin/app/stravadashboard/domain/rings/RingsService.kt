package app.stravadashboard.domain.rings

import app.stravadashboard.domain.ports.RingDayRepository
import app.stravadashboard.domain.ports.WellnessRepository
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/** Today's rings from the local cache only (feature 020, R7). All null when Garmin isn't connected. */
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
}
