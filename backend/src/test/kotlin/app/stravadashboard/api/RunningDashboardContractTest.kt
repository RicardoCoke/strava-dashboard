package app.stravadashboard.api

import app.stravadashboard.domain.DashboardResult
import app.stravadashboard.domain.DateWindow
import app.stravadashboard.domain.DistanceDifference
import app.stravadashboard.domain.EasyHrComparison
import app.stravadashboard.domain.HrPoint
import app.stravadashboard.domain.HrTrendLine
import app.stravadashboard.domain.HrTrendResult
import app.stravadashboard.domain.HrTypeTrend
import app.stravadashboard.domain.MissingIn
import app.stravadashboard.domain.PaceComparisonResult
import app.stravadashboard.domain.PacePoint
import app.stravadashboard.domain.PaceTypeComparison
import app.stravadashboard.domain.PaceWindowStat
import app.stravadashboard.domain.WeekComparisonResult
import app.stravadashboard.domain.WeekHrStat
import app.stravadashboard.domain.WeekPaceStat
import app.stravadashboard.domain.WeekTotal
import app.stravadashboard.domain.WorkoutPaceWeekComparison
import app.stravadashboard.domain.model.RunType
import app.stravadashboard.domain.model.SecondsPerKm
import kotlinx.serialization.json.Json
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** AC2.11: the full JSON shape, nested objects and explicit nulls included, against the spec's example. */
class RunningDashboardContractTest {
    private fun date(text: String) = LocalDate.parse(text)

    private val ready =
        DashboardResult.Ready(
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
                            HrTypeTrend(
                                type = RunType.LongRun,
                                runCount = 2,
                                runsWithoutHeartRate = 0,
                                points = listOf(HrPoint(date("2026-10-25"), 148.0, 32.0, true, false)),
                                trend = null,
                            ),
                            HrTypeTrend(RunType.Workout(5, 6), 1, 1, emptyList(), null),
                        ),
                ),
            pace =
                PaceComparisonResult(
                    windowStart = date("2026-05-29"),
                    windowEnd = date("2026-10-29"),
                    currentWindow = DateWindow(date("2026-09-30"), date("2026-10-29")),
                    previousWindow = DateWindow(date("2026-08-31"), date("2026-09-29")),
                    types =
                        listOf(
                            PaceTypeComparison(
                                type = RunType.Workout(5, 6),
                                current = PaceWindowStat(SecondsPerKm(253), 2),
                                previous = PaceWindowStat(SecondsPerKm(260), 3),
                                runCount = 6,
                                points =
                                    listOf(
                                        PacePoint(date("2026-07-10"), SecondsPerKm(262), false),
                                        PacePoint(date("2026-09-15"), SecondsPerKm(260), true),
                                        PacePoint(date("2026-10-14"), SecondsPerKm(253), false),
                                        PacePoint(date("2026-10-20"), SecondsPerKm(252), false),
                                        PacePoint(date("2026-10-25"), SecondsPerKm(254), false),
                                    ),
                            ),
                            PaceTypeComparison(
                                RunType.Workout(4, 8),
                                PaceWindowStat(SecondsPerKm(250), 1),
                                null,
                                1,
                                listOf(PacePoint(date("2026-10-27"), SecondsPerKm(250), false)),
                            ),
                        ),
                ),
            weekComparison =
                WeekComparisonResult(
                    thisWeek = DateWindow(date("2026-10-26"), date("2026-11-01")),
                    lastWeek = DateWindow(date("2026-10-19"), date("2026-10-25")),
                    distance = DistanceDifference(32.4, 29.0, 3.4),
                    easyHeartRate = EasyHrComparison(WeekHrStat(138.9, 2), WeekHrStat(141.0, 2), -2.1, null),
                    workoutPace =
                        WorkoutPaceWeekComparison(
                            thisWeek = null,
                            lastWeek = WeekPaceStat(SecondsPerKm(253), 2, listOf(RunType.Workout(5, 6))),
                            changeSecondsPerKm = null,
                            missingIn = MissingIn.THIS_WEEK,
                        ),
                ),
        )

    private val expected =
        """
        {
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
                           "changeBpm": -5.1 } },
              { "typeKey": "long", "label": "Long runs", "kind": "long", "runCount": 2, "runsWithoutHeartRate": 0,
                "hasEnoughData": false,
                "points": [ { "date": "2026-10-25", "averageHeartRateBpm": 148.0, "distanceKm": 32.0, "treadmill": true, "rising": false } ],
                "trend": null },
              { "typeKey": "5x6min", "label": "5x6min", "kind": "workout", "runCount": 1, "runsWithoutHeartRate": 1,
                "hasEnoughData": false, "points": [], "trend": null }
            ]
          },
          "workoutPaceComparison": {
            "windowStart": "2026-05-29", "windowEnd": "2026-10-29",
            "currentWindow": { "start": "2026-09-30", "end": "2026-10-29" },
            "previousWindow": { "start": "2026-08-31", "end": "2026-09-29" },
            "types": [
              { "typeKey": "5x6min", "label": "5x6min", "runCount": 6,
                "points": [
                  { "date": "2026-07-10", "repPaceSecondsPerKm": 262, "treadmill": false },
                  { "date": "2026-09-15", "repPaceSecondsPerKm": 260, "treadmill": true },
                  { "date": "2026-10-14", "repPaceSecondsPerKm": 253, "treadmill": false },
                  { "date": "2026-10-20", "repPaceSecondsPerKm": 252, "treadmill": false },
                  { "date": "2026-10-25", "repPaceSecondsPerKm": 254, "treadmill": false }
                ],
                "current": { "averageRepPaceSecondsPerKm": 253, "runCount": 2 },
                "previous": { "averageRepPaceSecondsPerKm": 260, "runCount": 3 },
                "changeSecondsPerKm": -7, "runsWithoutPace": 1, "hasEnoughData": true },
              { "typeKey": "4x8min", "label": "4x8min", "runCount": 1,
                "points": [ { "date": "2026-10-27", "repPaceSecondsPerKm": 250, "treadmill": false } ],
                "current": { "averageRepPaceSecondsPerKm": 250, "runCount": 1 },
                "previous": null, "changeSecondsPerKm": null, "runsWithoutPace": 0, "hasEnoughData": false }
            ]
          },
          "weekComparison": {
            "thisWeek": { "start": "2026-10-26", "end": "2026-11-01" },
            "lastWeek": { "start": "2026-10-19", "end": "2026-10-25" },
            "distance": { "thisWeekKm": 32.4, "lastWeekKm": 29.0, "differenceKm": 3.4 },
            "easyHeartRate": {
              "thisWeek": { "averageHeartRateBpm": 138.9, "runCount": 2 },
              "lastWeek": { "averageHeartRateBpm": 141.0, "runCount": 2 },
              "changeBpm": -2.1, "missingIn": null
            },
            "workoutPace": {
              "thisWeek": null,
              "lastWeek": { "averageRepPaceSecondsPerKm": 253, "runCount": 2, "typeLabels": ["5x6min"] },
              "changeSecondsPerKm": null, "missingIn": "thisWeek"
            }
          }
        }
        """

    @Test
    fun `R2 - dashboard JSON matches the full contract including nested objects and nulls`() {
        val actual = Json.encodeToJsonElement(RunningDashboardResponse.serializer(), ready.toDto())
        assertEquals(Json.parseToJsonElement(expected), actual)
    }
}
