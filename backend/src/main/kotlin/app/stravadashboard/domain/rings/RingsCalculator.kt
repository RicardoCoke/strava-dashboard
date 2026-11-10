package app.stravadashboard.domain.rings

import app.stravadashboard.domain.model.roundHalfUpToInt
import app.stravadashboard.domain.wellness.WellnessDay
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min

/**
 * Today's Sleep, Recovery and Strain (feature 020, R4 to R6), each with a 30-day baseline of the same value. Pure:
 * [wellness] (resting HR, HRV) and [rings] (sleep, strain) are what's cached, keyed by Garmin's local date.
 */
class RingsCalculator(
    wellness: List<WellnessDay>,
    private val rings: Map<LocalDate, RingDay>,
) {
    private val byDate = wellness.associateBy { it.date }

    fun build(today: LocalDate): RingsView =
        RingsView(
            date = today,
            sleep = ring(today, ::sleepPerformance),
            recovery = ring(today, ::recovery),
            strain = ring(today) { rings[it]?.strain },
        )

    /** One entry per date from [from] to [to], with the same values as the rings for that day (023 R5). */
    fun history(
        from: LocalDate,
        to: LocalDate,
    ): List<RingsHistoryDay> =
        generateSequence(from) { it.plusDays(1) }
            .takeWhile { it <= to }
            .map { RingsHistoryDay(it, sleepPerformance(it), recovery(it), rings[it]?.strain) }
            .toList()

    /** % of the night's need slept, capped at 100 (R4). */
    fun sleepPerformance(day: LocalDate): Double? {
        val slept = rings[day]?.sleepSeconds ?: return null
        val strainExtra =
            RingsPolicy.STRAIN_EXTRA_S_PER_POINT *
                max(0.0, (rings[day.minusDays(1)]?.strain ?: 0.0) - RingsPolicy.STRAIN_EXTRA_FROM)
        val debt =
            (1..RingsPolicy.DEBT_NIGHTS).sumOf { k ->
                rings[day.minusDays(k)]?.sleepSeconds?.let { max(0, RingsPolicy.BASE_SLEEP_NEED_S - it) } ?: 0
            }
        val need = RingsPolicy.BASE_SLEEP_NEED_S + strainExtra + RingsPolicy.DEBT_PAYBACK * debt
        return min(RingsPolicy.MAX_PERCENT, slept / need * RingsPolicy.MAX_PERCENT)
    }

    /** 0 to 100 from HRV, resting HR and sleep against their 30-day baselines (R5); null without HRV. */
    fun recovery(day: LocalDate): Double? {
        val hrv = byDate[day]?.hrv?.value
        val hrvBase = baseline(day) { byDate[it]?.hrv?.value }
        if (hrv == null || hrvBase == null) return null
        val rhr = byDate[day]?.restingHr?.value
        val rhrBase = baseline(day) { byDate[it]?.restingHr?.value?.toDouble() }
        val components =
            listOfNotNull(
                RingsPolicy.HRV_WEIGHT to component(hrv / hrvBase - 1, RingsPolicy.HRV_SPAN),
                if (rhr != null && rhrBase != null) {
                    RingsPolicy.RHR_WEIGHT to component(1 - rhr / rhrBase, RingsPolicy.RHR_SPAN)
                } else {
                    null
                },
                sleepPerformance(day)?.let { RingsPolicy.SLEEP_WEIGHT to it },
            )
        return components.sumOf { (w, c) -> w * c } / components.sumOf { it.first }
    }

    private fun ring(
        today: LocalDate,
        valueOf: (LocalDate) -> Double?,
    ): RingValue? = valueOf(today)?.let { RingValue(it, baseline(today, valueOf)) }

    companion object {
        private const val MIDPOINT = 50.0

        /** A relative change of ±[span] maps onto 0..100 around 50. */
        private fun component(
            change: Double,
            span: Double,
        ): Double = (MIDPOINT + MIDPOINT / span * change).coerceIn(0.0, RingsPolicy.MAX_PERCENT)

        /** Mean of the values on the 30 days before [day], or null with fewer than 3. */
        fun baseline(
            day: LocalDate,
            valueOf: (LocalDate) -> Double?,
        ): Double? =
            (1..RingsPolicy.BASELINE_DAYS)
                .mapNotNull { valueOf(day.minusDays(it)) }
                .takeIf { it.size >= RingsPolicy.MIN_BASELINE_VALUES }
                ?.average()

        fun zoneOf(recovery: Double): RecoveryZone {
            val rounded = roundHalfUpToInt(recovery)
            return when {
                rounded >= RingsPolicy.GREEN_FROM -> RecoveryZone.GREEN
                rounded >= RingsPolicy.YELLOW_FROM -> RecoveryZone.YELLOW
                else -> RecoveryZone.RED
            }
        }
    }
}
