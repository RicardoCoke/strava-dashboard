package app.stravadashboard.domain

import kotlin.test.Test
import kotlin.test.assertEquals

// All bpm values are invented (003 specs.md, R1 and R2).
class HrRisingTrendTest {
    private val exampleA = listOf(150.0, 150.0, 150.0, 151.0, 152.0, 153.0, 154.0, 148.0, 146.0, 146.0)

    private fun flags(vararg bpm: Double): List<Boolean> = HrRisingTrend.flags(bpm.toList())

    private fun pattern(text: String): List<Boolean> = text.map { it == 'T' }

    @Test
    fun `R1 - rolling average is mean of last three rounded to one decimal`() {
        // AC1.2: A[2..9] = 150.0, 150.3, 151.0, 152.0, 153.0, 151.7, 149.3, 146.7, in tenths of a bpm.
        assertEquals(listOf(1500, 1503, 1510, 1520, 1530, 1517, 1493, 1467), HrRisingTrend.rollingTenths(exampleA))
        // Small noise rounds away, so nothing moves.
        assertEquals(List(6) { false }, flags(150.0, 150.0, 150.04, 150.0, 150.0, 150.04))
    }

    @Test
    fun `R2 - first three points and short series are never rising`() {
        assertEquals(emptyList(), flags())
        assertEquals(listOf(false), flags(150.0))
        assertEquals(listOf(false, false), flags(150.0, 160.0))
        assertEquals(listOf(false, false, false), flags(150.0, 160.0, 170.0))
        assertEquals(List(4) { false }, flags(150.0, 160.0, 170.0, 180.0))
    }

    @Test
    fun `R2 - example A turns rising at point three and back at point seven`() {
        assertEquals(pattern("FFFTTTTFFF"), HrRisingTrend.flags(exampleA))
    }

    @Test
    fun `R2 - flat average and a single fall keep the current state`() {
        // Averages 150, 151, 152, 152, 151.7, 152.3: rising from point 3, one flat step and one fall don't end it.
        assertEquals(pattern("FFFTTTTT"), flags(150.0, 150.0, 150.0, 153.0, 153.0, 150.0, 152.0, 155.0))
    }

    @Test
    fun `R2 - single high run does not flip`() {
        assertEquals(List(9) { false }, flags(150.0, 150.0, 150.0, 150.0, 159.0, 150.0, 150.0, 150.0, 150.0))
    }

    @Test
    fun `R2 - single low run does not flip`() {
        assertEquals(List(9) { false }, flags(150.0, 150.0, 150.0, 150.0, 141.0, 150.0, 150.0, 150.0, 150.0))
    }

    @Test
    fun `R2 - rise under one bpm does not flip`() {
        assertEquals(List(6) { false }, flags(150.0, 150.0, 150.0, 150.3, 150.6, 150.0))
    }

    @Test
    fun `R2 - slow creep flips and backdates to where it began`() {
        assertEquals(pattern("FFFTTTTT"), flags(150.0, 150.0, 150.0, 150.4, 150.8, 151.2, 151.6, 152.0))
    }
}
