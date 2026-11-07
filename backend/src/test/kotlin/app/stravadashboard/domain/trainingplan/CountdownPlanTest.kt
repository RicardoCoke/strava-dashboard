package app.stravadashboard.domain.trainingplan

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** An invented plan in the layout of 019 specs.md: weeks count down, sessions are text, no minutes, no paces. */
class CountdownPlanTest {
    private fun week(
        label: String,
        sunday: String,
        sundayKm: String,
        sundayType: String = "",
    ): String =
        """
        $label;;MONDAY;;TUESDAY;;WEDNESDAY;;THURSDAY;;FRIDAY;;SATURDAY;;SUNDAY;
        ;;;;;;;;;;;;;;$sundayType;
        ;;;Rest or cross-train;;LT 13-14 km with 20-25 min at LT pace;;Rest;;VO2max 13-14 km with 5 x 600 m at 5K pace;;;;;;$sunday
        EASY;0,00;;;;;;;;;;;;;;
        SUBT;0,00;;;;;;;;;;;;;;
        VOLUME [km];54,00;0,00;;13,50;;0,00;;13,50;;0,00;;7,00;;$sundayKm;
        ;;;;;;;;;;;;;;;
        """.trimIndent()

    private fun planText(): String {
        val weeks = (17 downTo 1).map { week("Week $it", "Long run 26-27 km", "26,50") }
        return (weeks + week("Week 0", "Goal marathon", "42,20", sundayType = "RACE 42,2K")).joinToString("\n")
    }

    private val start = LocalDate.of(2026, 8, 3)

    private fun calendar(today: LocalDate = LocalDate.of(2026, 11, 4)): BuildCalendar {
        val parsed = TrainingPlanParser.parse(SemicolonCsv.parse(planText()))
        return BuildCalendarBuilder.build((parsed as PlanParseResult.Valid).plan, start, today)
    }

    @Test
    fun `R8 - countdown labels map to dates`() {
        val weeks = calendar().weeks
        assertEquals(18, weeks.size)
        assertEquals("Week 17", weeks.first().week.label)
        assertEquals(1, weeks.first().index)
        assertEquals(start, weeks.first().startDate)
        assertEquals("Week 0", weeks.last().week.label)
        assertEquals(18, weeks.last().index)
        assertEquals(LocalDate.of(2026, 12, 6), weeks.last().endDate)
    }

    @Test
    fun `R8 - current week and progress`() {
        val calendar = calendar()
        val current = calendar.weeks.single { it.isCurrentWeek }
        assertEquals("Week 4", current.week.label)
        assertEquals(LocalDate.of(2026, 11, 2), current.startDate)
        assertEquals("73% · week 14 of 18 · 32 days to go", calendar.progress.label)
    }

    @Test
    fun `R8 - session text and midpoint`() {
        val tuesday = calendar().weeks.first().days[1]
        assertEquals("LT 13-14 km with 20-25 min at LT pace", tuesday.day.session)
        assertEquals(13.5, tuesday.day.distance.value)
        assertFalse(tuesday.day.isRest)
    }

    @Test
    fun `R8 - 5K pace session is not a race`() {
        val thursday = calendar().weeks.first().days[3]
        assertTrue("5K pace" in thursday.day.session)
        assertNull(thursday.day.race)
    }

    @Test
    fun `R8 - marathon on last sunday`() {
        val sunday =
            calendar()
                .weeks
                .last()
                .days
                .last()
        assertEquals(LocalDate.of(2026, 12, 6), sunday.date)
        assertTrue(assertNotNull(sunday.day.race).isMarathon)
    }

    @Test
    fun `R8 - no pace rows is valid`() {
        assertTrue(calendar().paces.isEmpty())
    }

    @Test
    fun `R8 - a rest or cross-train day shows as rest`() {
        val monday =
            calendar()
                .weeks
                .first()
                .days
                .first()
        assertTrue(monday.day.isRest)
        assertEquals("Rest", monday.day.session)
    }
}
