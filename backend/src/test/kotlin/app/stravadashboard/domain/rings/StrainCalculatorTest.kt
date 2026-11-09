package app.stravadashboard.domain.rings

import app.stravadashboard.domain.model.Bpm
import app.stravadashboard.domain.model.roundOneDecimal
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// All data here is invented.
class StrainCalculatorTest {
    private val lthr = Bpm(170)
    private val t0 = Instant.parse("2026-11-08T08:00:00Z")

    private fun at(minutes: Long) = t0.plusSeconds(minutes * 60)

    /** One sample per minute at [bpm] for [minutes], starting at [from]. */
    private fun block(
        from: Long,
        minutes: Long,
        bpm: Int,
    ) = (0 until minutes).map { HrSample(at(from + it), bpm) }

    @Test
    fun `R2 - zone weights by percent of LTHR`() {
        // 55 % of 170 = 93.5, 66 % = 112.2, 77 % = 130.9, 88 % = 149.6, 100 % = 170
        assertEquals(0, StrainCalculator.weight(93, lthr))
        assertEquals(1, StrainCalculator.weight(94, lthr))
        assertEquals(1, StrainCalculator.weight(112, lthr))
        assertEquals(2, StrainCalculator.weight(113, lthr))
        assertEquals(3, StrainCalculator.weight(131, lthr))
        assertEquals(4, StrainCalculator.weight(169, lthr))
        assertEquals(5, StrainCalculator.weight(170, lthr))
    }

    @Test
    fun `R2 - strain log scale and cap`() {
        // 60 min at weight 2 then 30 min at weight 4 = trimp 240; the closing sample counts 0.
        val samples = block(0, 60, 120) + block(60, 30, 160) + HrSample(at(90), 60)
        assertEquals(12.9, roundOneDecimal(requireNotNull(StrainCalculator.strain(samples, lthr))))

        assertEquals(0.0, StrainCalculator.strain(block(0, 600, 80), lthr))
        // 400 min at weight 5 = trimp 2000.
        assertEquals(21.0, StrainCalculator.strain(block(0, 401, 175), lthr))
    }

    @Test
    fun `R2 - sample durations capped at 5 minutes`() {
        // 2 min at weight 1, a null sample ending it, then a 60-minute gap capped at 5 min at weight 4.
        val samples =
            listOf(
                HrSample(at(0), 100),
                HrSample(at(2), null),
                HrSample(at(4), 160),
                HrSample(at(64), 60),
            )
        val trimp = 2.0 * 1 + 5.0 * 4
        assertEquals(8.35 * kotlin.math.ln(1 + trimp / 65), StrainCalculator.strain(samples, lthr))
    }

    @Test
    fun `R2 - no readings gives null strain`() {
        assertNull(StrainCalculator.strain(emptyList(), lthr))
        assertNull(StrainCalculator.strain(listOf(HrSample(at(0), null), HrSample(at(2), null)), lthr))
    }
}
