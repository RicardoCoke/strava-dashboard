package app.stravadashboard.domain.trainingplan

import app.stravadashboard.domain.model.SecondsPerKm
import app.stravadashboard.testsupport.parsePlan
import app.stravadashboard.testsupport.samplePlanText
import java.time.DayOfWeek
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// The fixture is invented; see src/test/resources/trainingplan/plan-sample.csv.
class TrainingPlanParserTest {
    private val plan = parsePlan(samplePlanText())

    private fun day(
        week: Int,
        dayOfWeek: DayOfWeek,
    ): PlanDay = plan.weeks[week - 1].days[dayOfWeek.value - 1]

    private fun invalidReason(text: String): String {
        val result = TrainingPlanParser.parse(SemicolonCsv.parse(text))
        return (result as PlanParseResult.Invalid).reason
    }

    private fun pace(seconds: Int) = SecondsPerKm(seconds)

    @Test
    fun `004 R3 - single pace`() {
        assertEquals(TargetPace("easy", "Easy", pace(350), null, null), plan.paces.first())
    }

    @Test
    fun `004 R3 - pace range with target`() {
        val byKey = plan.paces.associateBy { it.key }
        assertEquals(TargetPace("15k", "15K", pace(255), pace(250), pace(260)), byKey["15k"])
        assertEquals(TargetPace("hm", "HM", pace(265), pace(260), pace(270)), byKey["hm"])
        assertEquals(TargetPace("30k", "30K", null, pace(270), pace(280)), byKey["30k"])
        assertEquals(SecondsPerKm(338), PlanHeaderParser.parsePaceSeconds("0:05:38"))
        assertEquals(SecondsPerKm(338), PlanHeaderParser.parsePaceSeconds("5:38"))
    }

    @Test
    fun `004 R3 - unparseable pace skipped`() {
        val broken = parsePlan(samplePlanText().replace("0:20:00;0:05:50;", "0:20:00;fast;"))
        assertEquals(listOf("15k", "hm", "30k"), broken.paces.map { it.key })
        assertEquals(3, broken.weeks.size)
    }

    @Test
    fun `004 R3 - sheet notes from column after NOTES`() {
        assertEquals(listOf("6x10 - rest 1'30\"", "2x20 - jog 3'", "4x8' during long"), plan.notes)
        assertEquals(emptyList(), parsePlan(samplePlanText().replace(";NOTES;", ";;")).notes)
    }

    @Test
    fun `004 R3 - note text mentioning pace is not a pace`() {
        val withPaceNote = parsePlan(samplePlanText().replace("2x20 - jog 3'", "4x8' at HM pace"))
        assertEquals(listOf("easy", "15k", "hm", "30k"), withPaceNote.paces.map { it.key })
        assertTrue("4x8' at HM pace" in withPaceNote.notes)
    }

    @Test
    fun `004 R3 - pace label as written`() {
        val mixedCase = parsePlan(samplePlanText().replace("HM PACE (min-max)", "Hm pace (min-max)"))
        assertEquals("Hm", mixedCase.paces.single { it.key == "hm" }.label)
    }

    @Test
    fun `004 R3 - paces keep column order`() {
        assertEquals(listOf("easy", "15k", "hm", "30k"), plan.paces.map { it.key })
    }

    @Test
    fun `004 R4 - blocks detected in order with labels`() {
        assertEquals(listOf("WEEK 1", "WEEK 2", "RACE WEEK"), plan.weeks.map { it.label })
        plan.weeks.forEach { week -> assertEquals(DayOfWeek.entries, week.days.map { it.dayOfWeek }) }
    }

    @Test
    fun `004 R4 - no blocks is invalid`() {
        assertEquals(
            "No week blocks found (expected rows starting with WEEK 1 … RACE WEEK).",
            invalidReason(samplePlanText().replace(';', ',')),
        )
    }

    @Test
    fun `004 R4 - missing VOLUME row is invalid`() {
        val text = samplePlanText().lines().filterNot { it.startsWith("VOLUME [km];55,30") }.joinToString("\n")
        assertEquals("WEEK 1: VOLUME row missing", invalidReason(text))
    }

    @Test
    fun `004 R4 - wrong weekday header is invalid`() {
        assertEquals(
            "WEEK 1: expected TUESDAY in column 4",
            invalidReason(samplePlanText().replace("TUESDAY (hilly route)", "TUSDAY")),
        )
    }

    @Test
    fun `004 R4 - day note from parentheses`() {
        assertEquals("hilly route", day(1, DayOfWeek.TUESDAY).note)
        assertEquals("long run", day(2, DayOfWeek.SUNDAY).note)
        assertNull(day(1, DayOfWeek.MONDAY).note)
        assertEquals("a (b)", PlanDayParser.dayNote("FRIDAY ( a (b) )"))
    }

    @Test
    fun `004 R4 - week totals read as written`() {
        val week = plan.weeks.first()
        assertEquals(Kilometres(55.3), week.distance)
        assertEquals(70.5, week.easyPercent)
        assertEquals(29.5, week.subtPercent)
        assertEquals(
            "WEEK 1: VOLUME total is not a number",
            invalidReason(samplePlanText().replace("VOLUME [km];55,30", "VOLUME [km];n/a")),
        )
    }

    @Test
    fun `004 R4 - day minutes and km with decimal commas`() {
        val tuesday = day(1, DayOfWeek.TUESDAY)
        assertEquals(listOf(20, 32, 52), listOf(tuesday.easyMinutes, tuesday.subtMinutes, tuesday.totalMinutes))
        assertEquals(Kilometres(10.25), tuesday.distance)
    }

    @Test
    fun `004 R4 - text in number cell is invalid`() {
        assertEquals(
            "WEEK 1, MONDAY: EASY value is not a number",
            invalidReason(samplePlanText().replace("EASY;70,50;50;", "EASY;70,50;fifty;")),
        )
    }

    @Test
    fun `004 R4 - session text composition examples`() {
        assertEquals("50' easy", day(1, DayOfWeek.MONDAY).session)
        assertEquals("20' WU/CD + 4x8' subT", day(1, DayOfWeek.TUESDAY).session)
        assertEquals("20' WU/CD + 45' subT", day(1, DayOfWeek.THURSDAY).session)
        assertEquals("100' easy", day(2, DayOfWeek.SUNDAY).session)
        assertEquals(
            "30' WU/CD + 45' subT",
            PlanDayParser.sessionText(PlanDayParser.SessionCells("WU/CD [min]", "SUBT [min]", "30", "45")),
        )
        assertEquals("", PlanDayParser.sessionText(PlanDayParser.SessionCells("", "", "", "")))
        assertEquals("easy WU/CD + 8x3'", PlanDayParser.sessionText(PlanDayParser.SessionCells("", "", "easy", "8x3'")))
    }

    @Test
    fun `004 R5 - 5K and 10K races detected`() {
        val sunday = day(1, DayOfWeek.SUNDAY)
        assertEquals(PlanRace(5.0, false, "5K race"), sunday.race)
        assertEquals("20' WU/CD + 5K RACE", sunday.session)
        assertEquals(PlanRace(10.0, false, "10K race"), PlanDayParser.detectRace("10K RACE"))
        assertEquals(PlanRace(21.1, false, "21.1K race"), PlanDayParser.detectRace("RACE 21,1K"))
        assertNull(day(1, DayOfWeek.MONDAY).race)
    }

    @Test
    fun `004 R5 - race header day is marathon`() {
        val raceDay = day(3, DayOfWeek.SUNDAY)
        assertEquals("RACE 42,2K", raceDay.session)
        assertEquals(PlanRace(42.2, true, "Marathon · 42.2 km"), raceDay.race)
        assertEquals(170, raceDay.totalMinutes)
        assertEquals(Kilometres(42.2), raceDay.distance)
    }

    @Test
    fun `004 R5 - zero km zero minutes is rest even with leftover text`() {
        listOf(day(1, DayOfWeek.WEDNESDAY), day(2, DayOfWeek.SATURDAY)).forEach {
            assertTrue(it.isRest)
            assertEquals("Rest", it.session)
        }
        assertTrue(plan.weeks.flatMap { it.days }.count { it.isRest } == 2)
    }
}
