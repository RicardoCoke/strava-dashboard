package app.stravadashboard.domain.trainingplan

import app.stravadashboard.testsupport.FakePlanSource
import app.stravadashboard.testsupport.PLAN_START
import app.stravadashboard.testsupport.parsePlan
import app.stravadashboard.testsupport.samplePlanText
import app.stravadashboard.testsupport.sixteenWeekPlanText
import kotlinx.coroutines.runBlocking
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BuildCalendarBuilderTest {
    private val sixteenWeeks = parsePlan(sixteenWeekPlanText())

    private fun build(today: String): BuildCalendar =
        BuildCalendarBuilder.build(sixteenWeeks, PLAN_START, LocalDate.parse(today))

    private fun BuildCalendar.todayDates(): List<LocalDate> =
        weeks
            .flatMap {
                it.days
            }.filter { it.isToday }
            .map { it.date }

    private fun BuildCalendar.currentWeeks(): List<Int> = weeks.filter { it.isCurrentWeek }.map { it.index }

    @Test
    fun `004 R6 - sixteen weeks end on 6 Dec 2026`() {
        val calendar = build("2026-10-31")
        assertEquals(LocalDate.parse("2026-08-17"), calendar.startDate)
        assertEquals(LocalDate.parse("2026-12-06"), calendar.endDate)
        assertEquals(16, calendar.weeks.size)
        assertEquals(LocalDate.parse("2026-08-23"), calendar.weeks.first().endDate)
        val raceWeek = calendar.weeks.last()
        assertEquals(16, raceWeek.index)
        assertEquals("RACE WEEK", raceWeek.week.label)
        assertEquals(LocalDate.parse("2026-11-30"), raceWeek.startDate)
        val raceDay = raceWeek.days.last()
        assertEquals(LocalDate.parse("2026-12-06"), raceDay.date)
        assertEquals(true, raceDay.day.race?.isMarathon)
    }

    @Test
    fun `004 R6 - consecutive dates for any block count`() {
        val calendar = BuildCalendarBuilder.build(parsePlan(samplePlanText()), PLAN_START, PLAN_START)
        val dates = calendar.weeks.flatMap { week -> week.days.map { it.date } }
        assertEquals((0L until 21L).map { PLAN_START.plusDays(it) }, dates)
        assertEquals(PLAN_START.plusDays(20), calendar.endDate)
        assertEquals("0% · week 1 of 3 · 20 days to go", calendar.progress.label)
    }

    @Test
    fun `004 R7 - in progress label`() {
        val calendar = build("2026-10-31")
        val progress = calendar.progress
        assertEquals(BuildPhase.IN_PROGRESS, progress.phase)
        assertEquals(
            listOf(75, 66, 11, 36),
            listOf(progress.daysPassed, progress.percentComplete, progress.currentWeekIndex, progress.daysToGo),
        )
        assertNull(progress.daysUntilStart)
        assertEquals(112, progress.totalDays)
        assertEquals("66% · week 11 of 16 · 36 days to go", progress.label)
        assertEquals(listOf(11), calendar.currentWeeks())
        assertEquals(listOf(LocalDate.parse("2026-10-31")), calendar.todayDates())
    }

    @Test
    fun `004 R7 - first day`() {
        val progress = build("2026-08-17").progress
        assertEquals(BuildPhase.IN_PROGRESS, progress.phase)
        assertEquals("0% · week 1 of 16 · 111 days to go", progress.label)
    }

    @Test
    fun `004 R7 - one day to go and race day`() {
        assertEquals("98% · week 16 of 16 · 1 day to go", build("2026-12-05").progress.label)
        val raceDay = build("2026-12-06").progress
        assertEquals(99, raceDay.percentComplete)
        assertEquals(0, raceDay.daysToGo)
        assertEquals("99% · week 16 of 16 · race day", raceDay.label)

        val noMarathon = parsePlan(samplePlanText().replace("RACE 42,2K", "WU/CD [MIN]"))
        val lastDay = BuildCalendarBuilder.build(noMarathon, PLAN_START, PLAN_START.plusDays(20))
        assertEquals("95% · week 3 of 3 · last day", lastDay.progress.label)
    }

    @Test
    fun `004 R7 - before start`() {
        val calendar = build("2026-05-01")
        val progress = calendar.progress
        assertEquals(BuildPhase.NOT_STARTED, progress.phase)
        assertEquals(listOf(0, 0, 108), listOf(progress.percentComplete, progress.daysPassed, progress.daysUntilStart))
        assertNull(progress.currentWeekIndex)
        assertNull(progress.daysToGo)
        assertEquals("0% · starts in 108 days", progress.label)
        assertEquals("0% · starts in 1 day", build("2026-08-16").progress.label)
        assertEquals(emptyList(), calendar.currentWeeks())
        assertEquals(emptyList(), calendar.todayDates())
    }

    @Test
    fun `004 R7 - after race`() {
        val calendar = build("2026-12-07")
        val progress = calendar.progress
        assertEquals(BuildPhase.FINISHED, progress.phase)
        assertEquals(listOf(100, 112), listOf(progress.percentComplete, progress.daysPassed))
        assertNull(progress.currentWeekIndex)
        assertNull(progress.daysToGo)
        assertNull(progress.daysUntilStart)
        assertEquals("100% · build complete", progress.label)
        assertEquals(emptyList(), calendar.currentWeeks())
        assertEquals(emptyList(), calendar.todayDates())
    }

    @Test
    fun `004 R7 - past days are those before today`() {
        val calendar = build("2026-10-31")
        val days = calendar.weeks.flatMap { it.days }
        val past = days.filter { it.isPast }
        assertEquals(75, past.size)
        assertEquals(calendar.progress.daysPassed, past.size)
        assertEquals(LocalDate.parse("2026-08-17"), past.first().date)
        assertEquals(LocalDate.parse("2026-10-30"), past.last().date)
        assertEquals(emptyList(), days.filter { it.isPast && it.isToday })
    }

    @Test
    fun `004 R7 - no past days before start and all past after race`() {
        assertEquals(0, build("2026-05-01").weeks.flatMap { it.days }.count { it.isPast })
        assertEquals(112, build("2026-12-07").weeks.flatMap { it.days }.count { it.isPast })
    }

    @Test
    fun `004 R7 - today uses server zone`() {
        val clock = Clock.fixed(Instant.parse("2026-10-20T23:30:00Z"), ZoneOffset.UTC)
        val service = TrainingPlanService(FakePlanSource(), PLAN_START, clock, ZoneId.of("Europe/Lisbon"))
        val result = runBlocking { service.get() } as TrainingPlanResult.Ready
        assertEquals(listOf(LocalDate.parse("2026-10-21")), result.calendar.todayDates())
    }
}
