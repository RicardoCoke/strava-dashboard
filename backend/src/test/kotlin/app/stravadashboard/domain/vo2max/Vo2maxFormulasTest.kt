package app.stravadashboard.domain.vo2max

import app.stravadashboard.domain.model.Bpm
import app.stravadashboard.domain.model.Seconds
import java.time.LocalDate
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Vo2maxFormulasTest {
    private fun segment(
        pace: Double,
        heartRate: Double,
    ) = Segment(LocalDate.of(2026, 10, 30), pace, heartRate, Seconds(3000), SegmentMethod.WHOLE_RUN)

    /**
     * [equation] is the solved Daniels equation (the regression guard, ±3 s). Daniels' printed [table] is smoothed and
     * runs up to ~0.08 % slower (9 s on a marathon), so it is only checked within 0.1 %.
     */
    private fun assertRaceTime(
        equation: Int,
        table: Int,
        vdot: Double,
        distance: RaceDistance,
    ) {
        val actual = DanielsFormulas.raceTime(vdot, distance.meters).value
        assertTrue(
            abs(actual - equation) <= EQUATION_TOLERANCE_S,
            "$distance at VDOT $vdot: $actual s, equation $equation s",
        )
        assertTrue(
            abs(actual - table) <= maxOf(EQUATION_TOLERANCE_S.toDouble(), table * TABLE_TOLERANCE),
            "$distance at VDOT $vdot: $actual s, table $table s",
        )
    }

    private companion object {
        const val EQUATION_TOLERANCE_S = 3
        const val TABLE_TOLERANCE = 0.001
    }

    @Test
    fun `R5 - oxygen cost and HR fraction example`() {
        assertEquals(36.0116, DanielsFormulas.oxygenCost(200.0), 1e-4)
        assertEquals(57.16, Vo2maxEstimator.estimate(segment(300.0, 150.0), Bpm(194)) ?: 0.0, 0.01)
    }

    @Test
    fun `R5 - max HR change changes VO2max`() {
        assertEquals(52.3, Vo2maxEstimator.estimate(segment(300.0, 150.0), Bpm(185)) ?: 0.0, 0.05)
    }

    @Test
    fun `R5 - low HR excluded`() {
        assertNull(Vo2maxEstimator.estimate(segment(300.0, 120.0), Bpm(194)))
    }

    @Test
    fun `R5 - HR above max capped`() {
        val capped = Vo2maxEstimator.estimate(segment(240.0, 200.0), Bpm(194))
        assertEquals(Vo2maxEstimator.estimate(segment(240.0, 194.0), Bpm(194)), capped)
    }

    @Test
    fun `R8 - VDOT 50 race times`() {
        assertRaceTime(1196, 1197, 50.0, RaceDistance.FIVE_K)
        assertRaceTime(2480, 2481, 50.0, RaceDistance.TEN_K)
        assertRaceTime(5491, 5495, 50.0, RaceDistance.HALF)
        assertRaceTime(11_440, 11_449, 50.0, RaceDistance.MARATHON)
    }

    @Test
    fun `R8 - VDOT 40 race times`() {
        assertRaceTime(1446, 1448, 40.0, RaceDistance.FIVE_K)
        assertRaceTime(3001, 3003, 40.0, RaceDistance.TEN_K)
        assertRaceTime(6654, 6659, 40.0, RaceDistance.HALF)
        assertRaceTime(13_777, 13_785, 40.0, RaceDistance.MARATHON)
    }
}
