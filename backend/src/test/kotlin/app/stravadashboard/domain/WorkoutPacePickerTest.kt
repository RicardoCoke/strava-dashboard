package app.stravadashboard.domain

import app.stravadashboard.domain.model.SecondsPerKm
import app.stravadashboard.domain.model.StreamPaceResult
import app.stravadashboard.domain.model.StreamPaceStatus
import app.stravadashboard.testsupport.TODAY
import app.stravadashboard.testsupport.run
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Feature 010: per-run rep-pace points per workout type over 5 months. TODAY is 2026-10-29. */
class WorkoutPacePickerTest {
    private fun date(text: String): LocalDate = LocalDate.parse(text)

    private fun outdoor(
        id: Long,
        day: String,
        pace: Int?,
        name: String = "5x6min",
    ) = run(
        id = id,
        date = date(day),
        name = name,
        streamPace = pace?.let { StreamPaceResult(name, StreamPaceStatus.COMPUTED, SecondsPerKm(it)) },
    )

    private fun treadmill(
        id: Long,
        day: String,
        description: String?,
    ) = run(id = id, date = date(day), name = "5x6min", trainer = true, description = description)

    private fun types(vararg runs: app.stravadashboard.domain.model.RunActivity) =
        WorkoutPaceComparison.compute(runs.toList(), TODAY).types

    @Test
    fun `010 R1 - type with runs older than 60 days is listed`() {
        val type = types(outdoor(1, "2026-06-15", 262)).single()
        assertEquals("5x6min", type.type.key)
        assertEquals(listOf(date("2026-06-15")), type.points.map { it.date })
    }

    @Test
    fun `010 R1 - five month window is inclusive of its start date`() {
        val runs = listOf(outdoor(1, "2026-05-29", 260), outdoor(2, "2026-05-28", 255, "4x8min"))
        val result = WorkoutPaceComparison.compute(runs, TODAY)
        assertEquals(date("2026-05-29"), result.windowStart)
        assertEquals(TODAY, result.windowEnd)
        assertEquals(listOf("5x6min"), result.types.map { it.type.key })
    }

    @Test
    fun `010 R1 - types ordered by most recent run`() {
        val keys = types(outdoor(1, "2026-10-20", 255), outdoor(2, "2026-10-27", 250, "4x8min")).map { it.type.key }
        assertEquals(listOf("4x8min", "5x6min"), keys)
    }

    @Test
    fun `010 R1 - run count includes runs without pace`() {
        val runs = (1..5L).map { outdoor(it, "2026-09-0$it", 250) } + outdoor(6, "2026-09-06", null)
        val type = types(*runs.toTypedArray()).single()
        assertEquals(6, type.runCount)
        assertEquals(5, type.points.size)
        assertEquals(1, type.runsWithoutPace)
    }

    @Test
    fun `010 R1 - only workout runs are listed`() {
        val result =
            types(
                run(id = 1, date = date("2026-10-01"), name = "Easy run"),
                run(id = 2, date = date("2026-10-02"), name = "Long run", distanceMeters = 28_000.0),
                outdoor(3, "2026-10-03", 250).copy(sportType = "Ride"),
            )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `010 R2 - points ordered by date with one per run`() {
        val points =
            types(
                outdoor(1, "2026-09-02", 258),
                outdoor(2, "2026-07-10", 262),
                outdoor(3, "2026-10-14", 253),
                outdoor(4, "2026-10-14", 251),
            ).single().points
        assertEquals(
            listOf("2026-07-10", "2026-09-02", "2026-10-14", "2026-10-14").map(::date),
            points.map { it.date },
        )
    }

    @Test
    fun `010 R2 - outdoor point uses stream rep pace`() {
        val point = types(outdoor(1, "2026-10-14", 252)).single().points.single()
        assertEquals(PacePoint(date("2026-10-14"), SecondsPerKm(252), treadmill = false), point)
    }

    @Test
    fun `010 R2 - treadmill point uses description pace line`() {
        val point = types(treadmill(1, "2026-10-14", "Felt good\n4:15 4:10")).single().points.single()
        assertEquals(PacePoint(date("2026-10-14"), SecondsPerKm(253), treadmill = true), point)
    }

    @Test
    fun `010 R2 - runs without rep pace are excluded and counted`() {
        val type =
            types(
                treadmill(1, "2026-10-10", null),
                treadmill(2, "2026-10-11", "Felt good"),
                outdoor(3, "2026-10-12", null),
                outdoor(4, "2026-10-13", 250),
            ).single()
        assertEquals(1, type.points.size)
        assertEquals(3, type.runsWithoutPace)
        assertEquals(type.runCount, type.points.size + type.runsWithoutPace)
    }

    @Test
    fun `010 R2 - point date uses start date local`() {
        val lateEvening =
            outdoor(1, "2026-10-01", 250).copy(
                startLocal = LocalDateTime.parse("2026-10-01T23:30:00"),
                startUtc = Instant.parse("2026-10-02T05:30:00Z"),
            )
        assertEquals(
            date("2026-10-01"),
            types(lateEvening)
                .single()
                .points
                .single()
                .date,
        )
    }

    @Test
    fun `010 R3 - thirty day comparison unchanged`() {
        // Current window 2026-09-30..2026-10-29, previous 2026-08-31..2026-09-29.
        val type =
            types(
                outdoor(1, "2026-09-05", 262),
                outdoor(2, "2026-09-20", 258),
                outdoor(3, "2026-10-05", 254),
                outdoor(4, "2026-10-15", 253),
                outdoor(5, "2026-10-25", 252),
            ).single()
        assertEquals(PaceWindowStat(SecondsPerKm(260), 2), type.previous)
        assertEquals(PaceWindowStat(SecondsPerKm(253), 3), type.current)
        assertEquals(-7, type.changeSecondsPerKm)
        assertTrue(type.hasEnoughData)
    }

    @Test
    fun `010 R3 - old-only type has no comparison but has points`() {
        val type = types(outdoor(1, "2026-07-01", 262), outdoor(2, "2026-07-15", 259)).single()
        assertNull(type.current)
        assertNull(type.previous)
        assertEquals(false, type.hasEnoughData)
        assertEquals(2, type.points.size)
    }
}
