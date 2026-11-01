package app.stravadashboard.domain.wellness

import app.stravadashboard.testsupport.WELLNESS_TODAY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ReadinessNoteTest {
    private val today = WELLNESS_TODAY

    private fun reading(
        flagged: Boolean,
        streak: Int = if (flagged) 1 else 0,
        baseline: Double? = 50.0,
    ) = LatestReading(today, 50.0, baseline, baseline?.let { 0.0 }, flagged, streak)

    private fun view(
        restingHr: LatestReading?,
        hrv: LatestReading?,
    ) = WellnessView(today.minusDays(83), today, emptyList(), restingHr, hrv)

    private val notEvaluable = reading(flagged = false, baseline = null)

    private val cases: List<Triple<WellnessView, Boolean?, String?>> =
        listOf(
            Triple(
                view(reading(false), reading(true, 3)),
                true,
                "HRV below baseline 3 days running while load is high",
            ),
            Triple(
                view(reading(true, 2), reading(true, 1)),
                false,
                "HRV below baseline and resting HR above baseline 2 days running",
            ),
            Triple(view(reading(true, 1), notEvaluable), null, "Resting HR above baseline"),
            Triple(view(reading(false), reading(false)), false, "Resting HR and HRV within baseline"),
            Triple(
                view(reading(false), reading(false)),
                true,
                "Resting HR and HRV within baseline while load is high",
            ),
            Triple(view(reading(false), null), null, "Resting HR within baseline"),
            Triple(view(notEvaluable, reading(false)), null, "HRV within baseline"),
            Triple(view(null, reading(true, 2)), true, "HRV below baseline 2 days running while load is high"),
            Triple(view(notEvaluable, notEvaluable), true, null),
            Triple(view(null, null), null, null),
        )

    @Test
    fun `R7 - readiness note exact text`() {
        cases.forEach { (view, loadHigh, expected) ->
            assertEquals(expected, ReadinessNote.build(view, loadHigh), "loadHigh=$loadHigh view=$view")
        }
    }

    @Test
    fun `R7 - no metric evaluable gives no note`() {
        assertNull(ReadinessNote.build(view(notEvaluable, null), true))
    }

    @Test
    fun `R7 - readiness note contains no advice words`() {
        val advice = Regex("\\b(rest|should|recommend|easy|skip)\\b", RegexOption.IGNORE_CASE)
        cases.mapNotNull { (view, loadHigh, _) -> ReadinessNote.build(view, loadHigh) }.forEach { note ->
            assertFalse(advice.containsMatchIn(note), note)
        }
    }
}
