package app.stravadashboard.domain.trainingload

import app.stravadashboard.domain.model.HrHistogram
import app.stravadashboard.domain.model.SECONDS_PER_MINUTE

/** Lucia's three-zone TRIMP: minutes easy × 1 + subT × 2 + hard × 3. Unrounded. */
object TrimpCalculator {
    const val EASY_WEIGHT = 1
    const val SUBT_WEIGHT = 2
    const val HARD_WEIGHT = 3

    fun load(
        histogram: HrHistogram,
        zones: HrZones,
    ): Double =
        histogram.secondsByBpm.entries.sumOf { (bpm, seconds) ->
            weightOf(zones.zoneOf(bpm)) * seconds / SECONDS_PER_MINUTE.toDouble()
        }

    private fun weightOf(zone: HrZone): Int =
        when (zone) {
            HrZone.EASY -> EASY_WEIGHT
            HrZone.SUBT -> SUBT_WEIGHT
            HrZone.HARD -> HARD_WEIGHT
        }
}
