package app.stravadashboard.domain.rings

import app.stravadashboard.domain.model.Bpm
import java.time.Duration
import kotlin.math.ln
import kotlin.math.min

/** Day strain (feature 020, R2): zone-weighted minutes of all-day heart rate on a 0 to 21 log scale. */
object StrainCalculator {
    private const val SECONDS_PER_MINUTE = 60.0
    private const val PERCENT = 100.0

    /** Null when no sample has a reading. */
    fun strain(
        samples: List<HrSample>,
        lthr: Bpm,
    ): Double? {
        val sorted = samples.sortedBy { it.at }
        if (sorted.none { it.bpm != null }) return null
        // Each sample lasts until the next one, capped so a watch-off gap doesn't count; the last one counts 0.
        val trimp =
            sorted
                .zipWithNext { sample, next ->
                    val bpm = sample.bpm ?: return@zipWithNext 0.0
                    val gap = minOf(Duration.between(sample.at, next.at), RingsPolicy.MAX_SAMPLE_GAP)
                    weight(bpm, lthr) * gap.seconds / SECONDS_PER_MINUTE
                }.sum()
        return min(RingsPolicy.STRAIN_MAX, RingsPolicy.STRAIN_SCALE * ln(1 + trimp / RingsPolicy.STRAIN_KNEE_TRIMP))
    }

    /** 0 below 55 % of LTHR, then one more per band crossed, 5 at or above LTHR. */
    fun weight(
        bpm: Int,
        lthr: Bpm,
    ): Int = RingsPolicy.ZONE_PERCENTS.count { bpm >= lthr.value * it / PERCENT }
}
