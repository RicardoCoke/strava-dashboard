package app.stravadashboard.api

import app.stravadashboard.domain.model.ActivityId
import app.stravadashboard.domain.model.RunActivity
import app.stravadashboard.domain.model.SecondsPerKm
import app.stravadashboard.domain.workoutcompare.Coarse
import app.stravadashboard.domain.workoutcompare.CoarseReason
import app.stravadashboard.domain.workoutcompare.CompareCalculator
import app.stravadashboard.domain.workoutcompare.CompareSide
import app.stravadashboard.domain.workoutcompare.RepCell
import app.stravadashboard.domain.workoutcompare.RepComparison
import app.stravadashboard.domain.workoutcompare.RepMode
import app.stravadashboard.domain.workoutcompare.RepRow
import app.stravadashboard.domain.workoutcompare.Resolution
import app.stravadashboard.domain.workoutcompare.WorkoutComparison
import app.stravadashboard.testsupport.TODAY
import app.stravadashboard.testsupport.run
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

// All runs here are invented.
class WorkoutCompareMappersTest {
    private val earlier = run(id = 1, date = TODAY.minusDays(14), name = "5x6min")
    private val later = run(id = 2, date = TODAY, name = "8x3min")

    private fun side(run: RunActivity) = CompareSide(run, CompareCalculator.stats(run, null), emptyList())

    private fun dto(
        reps: RepComparison?,
        coarse: Coarse? = null,
    ) = WorkoutComparison(Resolution.STREAM, coarse, side(earlier), side(later), reps).toDto()

    private fun cell(
        pace: Int,
        bpm: Double?,
    ) = RepCell(SecondsPerKm(pace), bpm)

    @Test
    fun `R9 - differences are later minus earlier from the rounded values`() {
        val reps =
            RepComparison(
                RepMode.REPS,
                average = RepRow(cell(250, 169.6), cell(244, 166.4)),
                pairs = listOf(RepRow(cell(252, 168.5), cell(246, null)), RepRow(cell(248, 170.0), null)),
            )

        val json = dto(reps).reps

        assertEquals("reps", json?.mode)
        // 166 − 170 = −4 from the rounded values, not round(166.4 − 169.6) = −3.
        assertEquals(RepRowDto(RepCellDto(250, 170), RepCellDto(244, 166), -6, -4), json?.average)
        assertEquals(RepRowDto(RepCellDto(252, 169), RepCellDto(246, null), -6, null), json?.pairs?.get(0))
        assertEquals(RepRowDto(RepCellDto(248, 170), null, null, null), json?.pairs?.get(1))
    }

    @Test
    fun `R10 - pieces mode carries the piece length and the cut run`() {
        val reps =
            RepComparison(
                RepMode.PIECES,
                pieceMinutes = 3,
                piecesRunId = ActivityId(1),
                average = RepRow(cell(252, 166.0), cell(244, 163.0)),
                pairs = listOf(RepRow(null, cell(240, 160.0))),
            )

        assertEquals(
            RepComparisonDto(
                mode = "pieces",
                unavailableReason = null,
                unavailableRunId = null,
                pieceMinutes = 3,
                piecesRunId = 1,
                average = RepRowDto(RepCellDto(252, 166), RepCellDto(244, 163), -8, -3),
                pairs = listOf(RepRowDto(null, RepCellDto(240, 160), null, null)),
            ),
            dto(reps).reps,
        )
    }

    @Test
    fun `R5 - coarse reason codes and retry time`() {
        val until = Instant.parse("2026-11-26T08:15:00Z")

        assertEquals(
            CoarseDto("callLimit", null, "2026-11-26T08:15:00Z"),
            dto(null, Coarse(CoarseReason.CALL_LIMIT, retryAfter = until)).coarse,
        )
        assertEquals(
            CoarseDto("noDetailFile", 2, null),
            dto(null, Coarse(CoarseReason.NO_DETAIL_FILE, ActivityId(2))).coarse,
        )
        assertEquals("garminUnavailable", dto(null, Coarse(CoarseReason.GARMIN_UNAVAILABLE)).coarse?.reason)
    }
}
