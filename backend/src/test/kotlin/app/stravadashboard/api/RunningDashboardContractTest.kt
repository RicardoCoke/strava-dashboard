package app.stravadashboard.api

import app.stravadashboard.domain.DashboardResult
import app.stravadashboard.domain.DateWindow
import app.stravadashboard.domain.HrPoint
import app.stravadashboard.domain.HrTrendLine
import app.stravadashboard.domain.HrTrendResult
import app.stravadashboard.domain.HrTypeTrend
import app.stravadashboard.domain.PaceComparisonResult
import app.stravadashboard.domain.PaceTypeComparison
import app.stravadashboard.domain.PaceWindowStat
import app.stravadashboard.domain.SyncInfo
import app.stravadashboard.domain.WeekTotal
import app.stravadashboard.domain.model.RunType
import app.stravadashboard.domain.model.SecondsPerKm
import app.stravadashboard.domain.ports.SyncStatus
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** AC2.11: the full JSON shape, nested objects and explicit nulls included, against the spec's example. */
class RunningDashboardContractTest {
    private fun date(text: String) = LocalDate.parse(text)

    private val ready =
        DashboardResult.Ready(
            sync = SyncInfo(SyncStatus.OK, Instant.parse("2026-10-29T08:12:03.456Z"), null, 0),
            weeks = listOf(WeekTotal(date("2026-08-31"), date("2026-09-06"), 32.4, 4, false)),
            heartRate =
                HrTrendResult(
                    windowStart = date("2026-05-29"),
                    windowEnd = date("2026-10-29"),
                    minRunsForComparison = 3,
                    types =
                        listOf(
                            HrTypeTrend(
                                type = RunType.Easy,
                                runCount = 41,
                                runsWithoutHeartRate = 2,
                                points = listOf(HrPoint(date("2026-06-01"), 142.3, 8.2, false, true)),
                                trend = HrTrendLine(date("2026-06-01"), 145.2, date("2026-10-28"), 140.1, -5.1),
                            ),
                        ),
                ),
            pace =
                PaceComparisonResult(
                    currentWindow = DateWindow(date("2026-09-30"), date("2026-10-29")),
                    previousWindow = DateWindow(date("2026-08-31"), date("2026-09-29")),
                    types =
                        listOf(
                            PaceTypeComparison(
                                type = RunType.Workout(5, 6),
                                current = PaceWindowStat(SecondsPerKm(253), 2),
                                previous = PaceWindowStat(SecondsPerKm(260), 3),
                                runsWithoutPace = 1,
                            ),
                            PaceTypeComparison(RunType.Workout(4, 8), PaceWindowStat(SecondsPerKm(250), 1), null, 0),
                        ),
                ),
        )

    private val expected =
        """
        {
          "sync": { "status": "ok", "lastSyncedAt": "2026-10-29T08:12:03Z", "retryAfter": null, "runsPendingDetail": 0 },
          "weeklyDistance": {
            "weeks": [
              { "weekStart": "2026-08-31", "weekEnd": "2026-09-06", "label": "31 Aug", "distanceKm": 32.4,
                "runCount": 4, "isCurrentWeek": false }
            ]
          },
          "heartRateTrend": {
            "windowStart": "2026-05-29", "windowEnd": "2026-10-29", "minRunsForComparison": 3,
            "types": [
              { "typeKey": "easy", "label": "Easy runs", "kind": "easy", "runCount": 41, "runsWithoutHeartRate": 2,
                "hasEnoughData": true,
                "points": [ { "date": "2026-06-01", "averageHeartRateBpm": 142.3, "distanceKm": 8.2, "treadmill": false, "rising": true } ],
                "trend": { "startDate": "2026-06-01", "startBpm": 145.2, "endDate": "2026-10-28", "endBpm": 140.1,
                           "changeBpm": -5.1 } }
            ]
          },
          "workoutPaceComparison": {
            "currentWindow": { "start": "2026-09-30", "end": "2026-10-29" },
            "previousWindow": { "start": "2026-08-31", "end": "2026-09-29" },
            "types": [
              { "typeKey": "5x6min", "label": "5x6min",
                "current": { "averageRepPaceSecondsPerKm": 253, "runCount": 2 },
                "previous": { "averageRepPaceSecondsPerKm": 260, "runCount": 3 },
                "changeSecondsPerKm": -7, "runsWithoutPace": 1, "hasEnoughData": true },
              { "typeKey": "4x8min", "label": "4x8min",
                "current": { "averageRepPaceSecondsPerKm": 250, "runCount": 1 },
                "previous": null, "changeSecondsPerKm": null, "runsWithoutPace": 0, "hasEnoughData": false }
            ]
          }
        }
        """

    @Test
    fun `R2 - dashboard JSON matches the full contract including nested objects and nulls`() {
        val actual = Json.encodeToJsonElement(RunningDashboardResponse.serializer(), ready.toDto())
        assertEquals(Json.parseToJsonElement(expected), actual)
    }
}
