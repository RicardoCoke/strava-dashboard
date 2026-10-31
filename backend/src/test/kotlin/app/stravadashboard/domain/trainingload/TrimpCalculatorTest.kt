package app.stravadashboard.domain.trainingload

import app.stravadashboard.domain.model.Bpm
import app.stravadashboard.domain.model.HrHistogram
import kotlin.test.Test
import kotlin.test.assertEquals

class TrimpCalculatorTest {
    private val histogram = HrHistogram(mapOf(140 to 1800, 160 to 1200, 180 to 600))

    @Test
    fun `R5 - zone boundaries from LTHR`() {
        val zones = HrZones.of(Bpm(175))
        assertEquals(HrZones(175, 153, 175), zones)
        assertEquals(HrZone.EASY, zones.zoneOf(152))
        assertEquals(HrZone.SUBT, zones.zoneOf(153))
        assertEquals(HrZone.SUBT, zones.zoneOf(175))
        assertEquals(HrZone.HARD, zones.zoneOf(176))
        assertEquals(140, HrZones.of(Bpm(160)).easyBelowBpm)
    }

    @Test
    fun `R5 - zone weighted load`() {
        assertEquals(100.0, TrimpCalculator.load(histogram, HrZones.of(Bpm(175))), 1e-9)
    }

    @Test
    fun `R5 - LTHR change changes load`() {
        assertEquals(130.0, TrimpCalculator.load(histogram, HrZones.of(Bpm(160))), 1e-9)
    }

    @Test
    fun `R5 - empty histogram load zero`() {
        assertEquals(0.0, TrimpCalculator.load(HrHistogram(emptyMap()), HrZones.of(Bpm(175))))
    }
}
