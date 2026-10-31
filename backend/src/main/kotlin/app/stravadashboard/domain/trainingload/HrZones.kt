package app.stravadashboard.domain.trainingload

import app.stravadashboard.domain.model.Bpm

/** Three zones from the lactate threshold: easy below [easyBelowBpm], subT up to [hardAboveBpm], hard above it. */
data class HrZones(
    val lthrBpm: Int,
    val easyBelowBpm: Int,
    val hardAboveBpm: Int,
) {
    fun zoneOf(bpm: Int): HrZone =
        when {
            bpm < easyBelowBpm -> HrZone.EASY
            bpm <= hardAboveBpm -> HrZone.SUBT
            else -> HrZone.HARD
        }

    companion object {
        const val EASY_PERCENT_OF_LTHR = 87
        private const val PERCENT = 100

        /** `easyBelowBpm = ceil(lthr × 87 / 100)` in integer maths. */
        fun of(lthr: Bpm): HrZones {
            val easyBelow = (lthr.value * EASY_PERCENT_OF_LTHR + PERCENT - 1) / PERCENT
            return HrZones(lthr.value, easyBelow, lthr.value)
        }
    }
}

enum class HrZone { EASY, SUBT, HARD }
