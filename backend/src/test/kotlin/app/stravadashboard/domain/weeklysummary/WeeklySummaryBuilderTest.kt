package app.stravadashboard.domain.weeklysummary

import app.stravadashboard.domain.WeekComparison
import app.stravadashboard.domain.WeeklyDistance
import app.stravadashboard.domain.model.Bpm
import app.stravadashboard.domain.model.HrDriftResult
import app.stravadashboard.domain.model.HrDriftStatus
import app.stravadashboard.domain.model.HrHistogram
import app.stravadashboard.domain.model.HrHistogramResult
import app.stravadashboard.domain.model.HrHistogramStatus
import app.stravadashboard.domain.model.RunActivity
import app.stravadashboard.domain.trainingload.HrZones
import app.stravadashboard.domain.wellness.WellnessDay
import app.stravadashboard.testsupport.run
import app.stravadashboard.testsupport.wellnessDay
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// All data here is invented. "Today" is Sunday 2026-11-08, so the summary week is 2026-11-02..08.
class WeeklySummaryBuilderTest {
    private val today = LocalDate.of(2026, 11, 8)
    private val week = LocalDate.of(2026, 11, 2)
    private var nextId = 1L

    /** [count] runs on consecutive days from [start], each with the given fields. */
    @Suppress("LongParameterList")
    private fun runs(
        start: LocalDate,
        count: Int,
        meters: Double = 10_000.0,
        movingSeconds: Int? = 3000,
        heartRate: Double? = null,
        histogramSeconds: Int? = null,
    ): List<RunActivity> =
        (0 until count).map {
            run(
                id = nextId++,
                date = start.plusDays(it.toLong()),
                distanceMeters = meters,
                movingSeconds = movingSeconds,
                heartRate = heartRate,
                hrHistogram =
                    histogramSeconds?.let { s ->
                        HrHistogramResult(HrHistogramStatus.COMPUTED, HrHistogram(mapOf(EASY_BPM to s)))
                    },
            )
        }

    private fun weeksAgo(n: Long): LocalDate = week.minusWeeks(n)

    private fun build(
        runs: List<RunActivity>,
        wellness: List<WellnessDay> = emptyList(),
        zones: HrZones? = null,
    ) = WeeklySummaryBuilder.build(runs, wellness, zones, today)

    private fun noteOf(
        runs: List<RunActivity>,
        metric: NoteMetric,
        wellness: List<WellnessDay> = emptyList(),
        zones: HrZones? = null,
    ): SummaryNote? =
        build(runs, wellness, zones)
            .first { it.weekStart == week }
            .notes
            .firstOrNull { it.metric == metric }

    @Test
    fun `R1 - week with 4 runs has no summary`() {
        assertTrue(build(runs(week, 4)).isEmpty())
    }

    @Test
    fun `R1 - fifth run creates in-progress summary`() {
        val summary = build(runs(week, 5)).single()
        assertEquals(week, summary.weekStart)
        assertEquals(week.plusDays(6), summary.weekEnd)
        assertEquals(5, summary.runCount)
        assertTrue(summary.inProgress)
    }

    @Test
    fun `R1 - buckets by start_date_local`() {
        val sundayLate = run(id = 99, startLocal = LocalDate.of(2026, 11, 1).atTime(23, 30))
        val summaries = build(runs(weeksAgo(1), 4) + sundayLate)
        assertEquals(listOf(weeksAgo(1)), summaries.map { it.weekStart })
        assertTrue(!summaries.single().inProgress)
    }

    @Test
    fun `R1 - non-runs ignored`() {
        val rides = runs(week, 5).map { it.copy(sportType = "Ride") }
        assertTrue(build(rides + runs(week, 4)).isEmpty())
    }

    @Test
    fun `R1 - six-month window newest first`() {
        val all =
            runs(week, 5) + runs(weeksAgo(2), 5) + runs(LocalDate.of(2026, 5, 4), 5) +
                runs(LocalDate.of(2026, 4, 27), 5)
        assertEquals(
            listOf(week, weeksAgo(2), LocalDate.of(2026, 5, 4)),
            build(all).map { it.weekStart },
        )
    }

    @Test
    fun `R3 - distance note text and watch load`() {
        val all =
            runs(week, 5, meters = 12_480.0) +
                runs(weeksAgo(4), 1, meters = 50_000.0) +
                runs(weeksAgo(3), 1, meters = 51_300.0) +
                runs(weeksAgo(2), 1, meters = 52_000.0) +
                runs(weeksAgo(1), 1, meters = 58_300.0)
        val note = noteOf(all, NoteMetric.DISTANCE)
        assertEquals(
            "Volume 62.4 km over 5 runs (up 18% vs your 4-week average, +4.1 km vs last week), watch load",
            note?.text,
        )
        assertEquals(NoteTone.WORSE, note?.tone)
    }

    @Test
    fun `R3 - watch threshold strictly above 10`() {
        val baseline = (1L..4L).flatMap { runs(weeksAgo(it), 1, meters = 50_000.0) }
        val level = noteOf(runs(week, 5, meters = 11_000.0) + baseline, NoteMetric.DISTANCE)
        assertEquals(NoteTone.NEUTRAL, level?.tone)
        assertTrue(level?.text?.endsWith("watch load") == false)
        assertEquals(NoteTone.WORSE, noteOf(runs(week, 5, meters = 11_100.0) + baseline, NoteMetric.DISTANCE)?.tone)
    }

    @Test
    fun `R2 - empty prior week counts as zero`() {
        // Previous weeks 40 + 0 + 0 + 0 km: the average is 10 km, so 50 km is up 400%.
        val note = noteOf(runs(week, 5) + runs(weeksAgo(4), 1, meters = 40_000.0), NoteMetric.DISTANCE)
        assertEquals(
            "Volume 50.0 km over 5 runs (up 400% vs your 4-week average, +50.0 km vs last week), watch load",
            note?.text,
        )
    }

    @Test
    fun `R2 - missing baseline drops segment`() {
        assertEquals(
            "Volume 50.0 km over 5 runs (+50.0 km vs last week)",
            noteOf(runs(week, 5), NoteMetric.DISTANCE)?.text,
        )
    }

    @Test
    fun `R3 - time note format`() {
        val all =
            runs(week, 5, movingSeconds = 3744) +
                runs(weeksAgo(1), 1, movingSeconds = 17_220) +
                (2L..4L).flatMap { runs(weeksAgo(it), 1, movingSeconds = 17_060) }
        assertEquals(
            "Time on feet 5 h 12 min (up 9% vs your 4-week average, +25 min vs last week)",
            noteOf(all, NoteMetric.TIME)?.text,
        )
    }

    @Test
    fun `R3 - missing moving time skips time`() {
        val all = runs(week, 4) + runs(week.plusDays(5), 1, movingSeconds = null) + runs(weeksAgo(1), 1)
        assertNull(noteOf(all, NoteMetric.TIME))
        // A run without moving time in the previous 4 weeks drops only the 4-week segment.
        val gapInBaseline = runs(week, 5) + runs(weeksAgo(1), 1) + runs(weeksAgo(2), 1, movingSeconds = null)
        assertEquals("Time on feet 4 h 10 min (+3 h 20 min vs last week)", noteOf(gapInBaseline, NoteMetric.TIME)?.text)
    }

    @Test
    fun `R4 - longest of the block`() {
        val note =
            noteOf(
                runs(week, 4) + runs(week.plusDays(4), 1, meters = 32_000.0) + runs(weeksAgo(2), 1, meters = 30_500.0),
                NoteMetric.LONGEST_RUN,
            )
        assertEquals("Longest run of the block: 32.0 km", note?.text)
        assertEquals(NoteTone.BETTER, note?.tone)
    }

    @Test
    fun `R4 - longest compared with previous 4 weeks and tie`() {
        val shorter =
            noteOf(runs(week, 5, meters = 24_100.0) + runs(weeksAgo(3), 1, meters = 32_000.0), NoteMetric.LONGEST_RUN)
        assertEquals("Longest run 24.1 km (longest in the previous 4 weeks: 32.0 km)", shorter?.text)
        val tie =
            noteOf(runs(week, 5, meters = 32_000.0) + runs(weeksAgo(3), 1, meters = 32_000.0), NoteMetric.LONGEST_RUN)
        assertEquals(NoteTone.NEUTRAL, tie?.tone)
    }

    @Test
    fun `R4 - no prior runs no note`() {
        assertNull(noteOf(runs(week, 5), NoteMetric.LONGEST_RUN))
    }

    @Test
    fun `R5 - pace note faster and same`() {
        val all =
            runs(week, 5, movingSeconds = 3000) + runs(weeksAgo(1), 1, movingSeconds = 3000) +
                runs(weeksAgo(2), 1, movingSeconds = 3200)
        assertEquals(
            "Average pace 5:00 /km (10 s/km faster than your 4-week average, same pace as last week)",
            noteOf(all, NoteMetric.PACE)?.text,
        )
    }

    @Test
    fun `R6 - load note with watch load`() {
        val zones = HrZones.of(Bpm(LTHR))
        val all =
            runs(week, 5, heartRate = 120.0, histogramSeconds = 6240) +
                runs(weeksAgo(1), 1, heartRate = 120.0, histogramSeconds = 28_800) +
                (2L..4L).flatMap { runs(weeksAgo(it), 1, heartRate = 120.0, histogramSeconds = 26_400) }
        val note = noteOf(all, NoteMetric.TRAINING_LOAD, zones = zones)
        assertEquals("Training load 520 (up 16% vs your 4-week average, up 8% vs last week), watch load", note?.text)
        assertEquals(NoteTone.WORSE, note?.tone)
    }

    @Test
    fun `R6 - no LTHR or pending HR stream skips load`() {
        val all =
            runs(week, 5, heartRate = 120.0, histogramSeconds = 600) +
                runs(weeksAgo(1), 1, heartRate = 120.0, histogramSeconds = 600)
        assertNull(noteOf(all, NoteMetric.TRAINING_LOAD))
        val pending = all + runs(week.plusDays(5), 1, heartRate = 120.0)
        assertNull(noteOf(pending, NoteMetric.TRAINING_LOAD, zones = HrZones.of(Bpm(LTHR))))
    }

    @Test
    fun `R7 - drift note with zone`() {
        fun longRun(
            date: LocalDate,
            drift: Double,
        ) = run(
            id = nextId++,
            date = date,
            distanceMeters = 25_000.0,
            hrDrift = HrDriftResult(HrDriftStatus.COMPUTED, drift),
        )
        val all =
            runs(week, 4) + longRun(week.plusDays(5), 6.24) + longRun(weeksAgo(1), 4.6) + longRun(weeksAgo(3), 5.0) +
                run(
                    id = nextId++,
                    date = weeksAgo(2),
                    distanceMeters = 25_000.0,
                    trainer = true,
                    hrDrift = HrDriftResult(HrDriftStatus.COMPUTED, 20.0),
                )
        val note = noteOf(all, NoteMetric.HR_DRIFT)
        assertEquals("Long-run HR drift 6.2%, worth watching (previous 4 weeks: 4.8%)", note?.text)
        assertEquals(NoteTone.NEUTRAL, note?.tone)
        assertNull(noteOf(runs(week, 4) + longRun(week.plusDays(5), 6.24), NoteMetric.HR_DRIFT))
    }

    @Test
    fun `R7 - EF note with tone`() {
        fun easy(
            date: LocalDate,
            ef: Double,
        ) = run(
            id = nextId++,
            date = date,
            distanceMeters = ef * EF_HR * EF_MINUTES,
            heartRate = EF_HR,
            movingSeconds =
                EF_MINUTES * 60,
        )
        val all =
            (0L..4L).map { easy(week.plusDays(it), 1.652) } +
                easy(weeksAgo(1), 1.636) + easy(weeksAgo(2), 1.606) + easy(weeksAgo(3), 1.606) +
                easy(weeksAgo(4), 1.604)
        val note = noteOf(all, NoteMetric.EFFICIENCY)
        assertEquals(
            "Easy-run efficiency 1.65 m/min/beat (up 2.4% vs your 4-week average, up 1.0% vs last week)",
            note?.text,
        )
        assertEquals(NoteTone.BETTER, note?.tone)
        assertNull(noteOf(runs(week, 5), NoteMetric.EFFICIENCY))
    }

    private fun days(
        from: LocalDate,
        count: Long,
        restingHr: Int? = null,
        hrv: Double? = null,
    ) = (0 until count).map { wellnessDay(from.plusDays(it), restingHr, hrv) }

    @Test
    fun `R8 - resting HR and HRV notes and thresholds`() {
        val wellness = days(weeksAgo(4), 28, restingHr = 43, hrv = 64.0) + days(week, 7, restingHr = 48, hrv = 52.0)
        val rhr = noteOf(runs(week, 5), NoteMetric.RESTING_HR, wellness)
        assertEquals("Resting HR averaged 48.0 bpm (+5.0 bpm vs your 4-week average, +5.0 bpm vs last week)", rhr?.text)
        assertEquals(NoteTone.WORSE, rhr?.tone)
        val hrv = noteOf(runs(week, 5), NoteMetric.HRV, wellness)
        assertEquals("HRV averaged 52 ms (down 19% vs your 4-week average, down 19% vs last week)", hrv?.text)
        assertEquals(NoteTone.WORSE, hrv?.tone)
    }

    @Test
    fun `R8 - too few wellness values no note`() {
        val wellness = days(weeksAgo(4), 28, restingHr = 43) + days(week, 2, restingHr = 48)
        assertNull(noteOf(runs(week, 5), NoteMetric.RESTING_HR, wellness))
    }

    @Test
    fun `R9 - ranking and cap of six`() {
        val wellness = days(weeksAgo(4), 28, restingHr = 43, hrv = 64.0) + days(week, 7, restingHr = 48, hrv = 52.0)
        val all =
            runs(week, 5, meters = 25_000.0, heartRate = 120.0, histogramSeconds = 6000) +
                (1L..4L).flatMap { runs(weeksAgo(it), 1, heartRate = 120.0, histogramSeconds = 6000) }
        val notes = build(all, wellness, HrZones.of(Bpm(LTHR))).first().notes
        // 8 candidates: worse distance, load, RHR, HRV; better longest run, EF; neutral time, pace (dropped).
        assertEquals(
            listOf(
                NoteMetric.DISTANCE,
                NoteMetric.TRAINING_LOAD,
                NoteMetric.RESTING_HR,
                NoteMetric.HRV,
                NoteMetric.LONGEST_RUN,
                NoteMetric.EFFICIENCY,
            ),
            notes.map { it.metric },
        )
    }

    @Test
    fun `R9 - every summary has a note`() {
        val all = runs(week, 5) + runs(weeksAgo(3), 5, movingSeconds = null) + runs(weeksAgo(9), 5)
        assertTrue(build(all).all { it.notes.isNotEmpty() })
    }

    @Test
    fun `R1 - manual run counts for distance only`() {
        val manual = run(id = 900, date = week.plusDays(5), distanceMeters = 5_000.0, heartRate = null, laps = null)
        val summary = build(runs(week, 4) + manual + runs(weeksAgo(1), 1)).single { it.weekStart == week }
        assertEquals(5, summary.runCount)
        val texts = summary.notes.associate { it.metric to it.text }
        assertEquals(
            "Volume 45.0 km over 5 runs (up 1700% vs your 4-week average, +35.0 km vs last week), watch load",
            texts[NoteMetric.DISTANCE],
        )
        assertNull(texts[NoteMetric.TIME])
        assertEquals(
            "Average pace 5:00 /km (same pace as your 4-week average, same pace as last week)",
            texts[NoteMetric.PACE],
        )
    }

    @Test
    fun `R2 - note skipped without any baseline`() {
        assertNull(noteOf(runs(week, 5), NoteMetric.PACE))
    }

    @Test
    fun `R3 - distance matches week comparison`() {
        val all = runs(week, 5, meters = 12_480.0) + runs(weeksAgo(1), 2, meters = 29_150.0)
        val tiles = WeekComparison.compute(all, WeeklyDistance.compute(all, today), today).distance
        val text = noteOf(all, NoteMetric.DISTANCE)?.text.orEmpty()
        assertTrue(text.startsWith("Volume ${tiles.thisWeekKm} km"), text)
        assertTrue(text.contains("+${tiles.differenceKm} km vs last week"), text)
    }

    @Test
    fun `R6 - runs without HR add zero`() {
        val all =
            runs(week, 4, heartRate = 120.0, histogramSeconds = 6000) +
                runs(week.plusDays(4), 1, heartRate = null) +
                runs(weeksAgo(1), 1, heartRate = 120.0, histogramSeconds = 24_000)
        assertEquals(
            "Training load 400 (up 300% vs your 4-week average, level with last week), watch load",
            noteOf(all, NoteMetric.TRAINING_LOAD, zones = HrZones.of(Bpm(LTHR)))?.text,
        )
    }

    @Test
    fun `R7 - excluded drift runs`() {
        val baseline =
            run(
                id = 800,
                date = weeksAgo(1),
                distanceMeters = 25_000.0,
                hrDrift = HrDriftResult(HrDriftStatus.COMPUTED, 4.0),
            )
        val excluded =
            listOf(HrDriftResult(HrDriftStatus.TOO_SHORT, null), HrDriftResult(HrDriftStatus.NO_DATA, null), null)
                .mapIndexed {
                    i,
                    drift,
                    ->
                    run(id = 810L + i, date = week.plusDays(i.toLong()), distanceMeters = 25_000.0, hrDrift = drift)
                }
        assertNull(noteOf(runs(week.plusDays(3), 2) + excluded + baseline, NoteMetric.HR_DRIFT))
    }

    @Test
    fun `R8 - neutral just below the thresholds`() {
        // Baseline mean 43.1 bpm (25 × 43 + 3 × 44) and 64 ms; week 48 bpm (+4.9) and 55 ms (-14%).
        val wellness =
            days(weeksAgo(4), 25, restingHr = 43, hrv = 64.0) +
                days(weeksAgo(4).plusDays(25), 3, restingHr = 44, hrv = 64.0) +
                days(week, 7, restingHr = 48, hrv = 55.0)
        val rhr = noteOf(runs(week, 5), NoteMetric.RESTING_HR, wellness)
        assertTrue(rhr?.text.orEmpty().contains("+4.9 bpm vs your 4-week average"), rhr?.text)
        assertEquals(NoteTone.NEUTRAL, rhr?.tone)
        val hrv = noteOf(runs(week, 5), NoteMetric.HRV, wellness)
        assertTrue(hrv?.text.orEmpty().contains("down 14% vs your 4-week average"), hrv?.text)
        assertEquals(NoteTone.NEUTRAL, hrv?.tone)
    }

    @Test
    fun `R8 - old week skips wellness only`() {
        val old = weeksAgo(15)
        val wellness = days(week.minusDays(83), 90, restingHr = 48, hrv = 50.0)
        val summary = build(runs(old, 5) + runs(week, 5), wellness).single { it.weekStart == old }
        assertEquals(listOf(NoteMetric.DISTANCE, NoteMetric.TIME), summary.notes.map { it.metric })
    }

    @Test
    fun `R9 - deterministic output and revision rules`() {
        val five = runs(week, 5)
        val first = build(five).single()
        assertEquals(first, build(five).single())
        assertTrue(Regex("[0-9a-f]{64}").matches(first.revision))
        val sixth = build(five + run(id = 500, date = week, distanceMeters = 0.0, movingSeconds = 0)).single()
        assertEquals(
            first.notes
                .first { it.metric == NoteMetric.DISTANCE }
                .text
                .replace("5 runs", "6 runs"),
            sixth.notes
                .first {
                    it.metric ==
                        NoteMetric.DISTANCE
                }.text,
        )
        assertNotEquals(first.revision, sixth.revision)
        val monday = WeeklySummaryBuilder.build(five, emptyList(), null, today.plusDays(1)).single()
        assertTrue(!monday.inProgress)
        assertEquals(first.revision, monday.revision)
    }

    private companion object {
        const val EASY_BPM = 120
        const val LTHR = 175
        const val EF_HR = 125.0
        const val EF_MINUTES = 50
    }
}
