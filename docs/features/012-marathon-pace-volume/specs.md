# Spec: Marathon-pace km per week

**Feature ID:** 012 · **Status:** Approved · **Intent:** [intent.md](./intent.md) · **Date:** 2026-10-31

## Summary
A new "Marathon pace" card shows, for each Monday-Sunday week of the 16-week build, how many km I ran at goal marathon pace (MP). A lap counts as MP when its pace (elapsed time / distance) is within the goal pace ± 10 s/km (inclusive), it is at least 200 m long, and it belongs to an outdoor run. A summary shows total MP km so far, this week's MP km and this week's MP share of all running km. The goal pace comes from a new optional env setting `MARATHON_GOAL_PACE`. The data comes from a new read-only endpoint `GET /api/marathon-pace` that reads only the cached `runs` / `run_laps` tables.

## Requirements
| ID | Requirement | Priority (Must/Should/Could) |
|---|---|---|
| R1 | Config: optional `MARATHON_GOAL_PACE` (`m:ss` per km, 2:30–8:00). Unset → not configured; invalid → card-level config error. Invalid values never stop the app. | Must |
| R2 | A lap counts as MP if and only if: its run is a Run, not a treadmill run (`trainer` = false); lap distance ≥ 200 m; elapsed time > 0; and its pace (elapsed seconds / km) is within goal ± 10 s/km, both edges inclusive. MP distance is the sum of the counted laps' distances. | Must |
| R3 | The backend returns exactly 16 weeks, Monday-Sunday, starting at `TRAINING_PLAN_START_DATE` (default 2026-08-17, the same start as the 004 build calendar). Each week has its MP km (1 decimal), current-week flag and future flag. Runs are bucketed by the local date of `start_date_local`. | Must |
| R4 | The backend returns a summary: total MP km in the build so far, this week's MP km, this week's total running km (all Runs, treadmill included, same rule as 007/weekly distance), and this week's MP share as a whole percent. | Must |
| R5 | `GET /api/marathon-pace` returns the exact JSON below, needs a connected session, and never calls Strava or starts a sync. | Must |
| R6 | The card shows a 16-bar chart (one bar per build week, labelled with the week's start date) with each past/current bar labelled with MP km to 1 decimal, and the summary values above it. | Must |
| R7 | The card has loading, not-configured, invalid-config, error, empty and normal states. Not configured shows "not configured" text and no numbers. | Must |
| R8 | The card is placed after the "Workout pace by type" card and before the "Build calendar" card. | Must |
| R9 | When runs in the build still wait for their lap details, a muted footnote says how many. | Should |
| R10 | The card reloads quietly after each finished background sync, like the other self-loading cards (009 R9). | Should |

## Acceptance criteria
### R1: Goal pace setting
`MarathonPaceSetting`: `NotConfigured`, `Invalid`, `Valid(SecondsPerKm)`. `MIN_GOAL_PACE_S_PER_KM = 150`, `MAX_GOAL_PACE_S_PER_KM = 480`.
- **AC1.1** Given `MARATHON_GOAL_PACE` is unset, empty or blank, when the app starts, then `AppConfig.marathonPace` is `NotConfigured`.
- **AC1.2** Given `MARATHON_GOAL_PACE= 4:15 ` (surrounding whitespace allowed), then it is `Valid(SecondsPerKm(255))`. Given `2:30` it is `Valid(150)`; given `8:00` it is `Valid(480)`.
- **AC1.3** Given `255`, `4:5`, `4:60`, `4.15`, `abc`, `2:29` or `8:01`, then it is `Invalid` and the app still starts.
- **AC1.4** Given any setting, `AppConfig.toString()` shows `marathonPace=<unset>`, `<invalid>` or `<set>`, never the value.
- **AC1.5** `.env.example` lists `MARATHON_GOAL_PACE=` with no value and the comment "Goal marathon pace per km as m:ss (2:30-8:00), e.g. 4:15. Used by the Marathon pace card. Restart the backend after changing it."

### R2: Which laps count as MP (goal 4:15 = 255 s/km, so 245–265 s/km)
- **AC2.1** Given an outdoor Run with laps 1000 m / 245 s and 1000 m / 265 s, when computed, then both count (2.0 km MP).
- **AC2.2** Given laps 1000 m / 244 s and 1000 m / 266 s, then neither counts.
- **AC2.3** Given a lap 200.0 m / 51 s (255 s/km), then it counts (0.2 km). Given a lap 199.9 m / 51 s, then it does not count.
- **AC2.4** Given a treadmill Run (`trainer` = true) whose laps are all 1000 m / 255 s, then it adds 0 MP km.
- **AC2.5** Given a lap with distance 1000 m and elapsed 0 s, or distance 0 m, then it does not count and nothing fails.
- **AC2.6** Given an outdoor 21 km Run whose laps are 10 × 1000 m / 300 s, 8 × 1000 m / 255 s, 3 × 1000 m / 250 s, then its MP distance is 11 000 m.
- **AC2.7** Given the pace check, then it is exact (no rounding before comparing): a lap of 1000 m / 265 s counts, and a lap of 999 m / 265 s (265.27 s/km) does not.
- **AC2.8** Given a stored non-Run activity, then it adds nothing (only Runs are stored today; the check is kept for safety).
- **AC2.9** Given an outdoor Run whose detail hasn't been fetched yet (`laps` null), or whose detail has no laps (e.g. a manual activity), then it adds 0 MP km.

### R3: Build weeks
- **AC3.1** Given `TRAINING_PLAN_START_DATE` unset, when requested, then `weeks` has 16 entries, the first `weekStart` `2026-08-17`, the last `weekEnd` `2026-12-06`, `buildStart` `2026-08-17` and `buildEnd` `2026-12-06`.
- **AC3.2** Given each week, then `weekIndex` runs 1..16, `weekEnd` = `weekStart` + 6 days, and `label` is the start date as `d MMM` in English (e.g. `"17 Aug"`, `"7 Sep"`).
- **AC3.3** Given today is 2026-10-31 (Saturday), then the week `2026-10-26`–`2026-11-01` (week 11) has `isCurrentWeek` true and `isFuture` false, weeks 12–16 have `isFuture` true and `mpKm` 0.0, and weeks 1–10 have both false.
- **AC3.4** Given outdoor Runs with MP laps of 3 040 m and 2 010 m in the same week, then that week's `mpKm` is 5.1 (sum of metres, then rounded half-up to 1 decimal).
- **AC3.5** Given a week with no MP laps, then it is still returned with `mpKm` 0.0.
- **AC3.6** Given a Run with `start_date_local` 2026-08-23T23:30 (Sunday) and UTC start 2026-08-24T05:30Z, then its MP km count in week 1 (`2026-08-17`), not week 2.
- **AC3.7** Given a Run on 2026-08-16 (the day before the build) or 2026-12-07 (after the build), then it is in no week and adds nothing to `totalMpKm`.
- **AC3.8** Given today is before `buildStart`, then all 16 weeks have `isFuture` true and `isCurrentWeek` false. Given today is after `buildEnd`, then all have both false.
- **AC3.9** Given `TRAINING_PLAN_START_DATE=2026-09-07`, then the first week starts `2026-09-07` and the last ends `2026-12-27`.

### R4: Summary
- **AC4.1** Given MP laps totalling 41 040 m in weeks 1–11, then `summary.totalMpKm` is 41.0 (sum of metres over all build weeks, rounded half-up to 1 decimal).
- **AC4.2** Given today 2026-10-31, then `summary.thisWeekStart` is `2026-10-26` and `summary.thisWeekEnd` is `2026-11-01` (the calendar week containing today in the server zone, as in 007).
- **AC4.3** Given this week has an outdoor Run of 16 000 m with 6 400 m MP laps, and a treadmill Run of 10 000 m, then `thisWeekMpKm` is 6.4, `thisWeekTotalKm` is 26.0 and `thisWeekSharePercent` is 25 (6 400 / 26 000 = 24.6 %, rounded half-up).
- **AC4.4** Given the same runs, then `thisWeekTotalKm` equals `weekComparison.distance.thisWeekKm` of `GET /api/dashboard/running` (both are the sum of Run activity distances in the week, rounded half-up to 1 decimal).
- **AC4.5** Given no Runs this week, then `thisWeekMpKm` is 0.0, `thisWeekTotalKm` is 0.0 and `thisWeekSharePercent` is `null`.
- **AC4.6** Given this week's lap distances add up to more than the activity distances (e.g. MP laps 10 050 m, activity distance 10 000 m), then `thisWeekSharePercent` is capped at 100.
- **AC4.7** Given today is outside the build, then the `thisWeek*` fields still describe the current calendar week, and `totalMpKm` covers all 16 build weeks.

### R5: Endpoint
- **AC5.1** Given a connected session and a valid goal pace, when `GET /api/marathon-pace` is called, then the response is 200 with exactly the JSON shape in Design → Backend API.
- **AC5.2** Given no session cookie, or a session but no stored tokens, then the response is `401 {"error":"not_connected","message":"Connect your Strava account."}`.
- **AC5.3** Given `MARATHON_GOAL_PACE` not configured, then `404 {"error":"marathon_pace_not_configured","message":"No goal marathon pace is configured."}`.
- **AC5.4** Given `MARATHON_GOAL_PACE` invalid, then `422 {"error":"marathon_pace_invalid_config","message":"MARATHON_GOAL_PACE is not a pace between 2:30 and 8:00 per km (m:ss)."}`. The message never contains the configured value.
- **AC5.5** Given a Strava `MockEngine` that records every request, when the endpoint is called (any outcome), then the engine receives no request and no background sync is started.
- **AC5.6** Given `goal` in the response for `4:15`, then `paceSecondsPerKm` 255, `toleranceSecondsPerKm` 10, `fastestSecondsPerKm` 245, `slowestSecondsPerKm` 265, `minLapMeters` 200.

### R6: Chart and summary
- **AC6.1** Given a loaded response, then the card heading is "Marathon pace" and a subtitle reads "Goal 4:15 /km · laps from 4:05 to 4:25 /km, 200 m or longer, outdoor runs" (built from `goal`).
- **AC6.2** Given the response, then the summary shows three values: "Total at MP" `41.0 km`, "This week at MP" `6.4 km`, "Share of this week" `25%` with the caption `of 26.0 km`.
- **AC6.3** Given `thisWeekSharePercent` null, then "Share of this week" shows `–` with the caption `No runs this week`.
- **AC6.4** Given the chart options built by `buildMarathonPaceOptions(response)`, then the x-axis has the 16 `label`s in order, the y-axis name is `km`, and the bar series has 16 values equal to `mpKm`.
- **AC6.5** Given the options, then the current-week bar uses `palette.accent`, past weeks `palette.secondary`, and future weeks have no value label; past and current bars show a label with `mpKm.toFixed(1)` (including `0.0`).
- **AC6.6** Given the tooltip for week 3 with 4.2 km, then it reads `Week 3 · 31 Aug – 6 Sep: 4.2 km at MP`, with ` (this week)` appended for the current week and ` (upcoming)` for a future week.
- **AC6.7** The chart has the aria-label "Marathon-pace km per week of the build".

### R7: States
- **AC7.1 (loading)** While the request is open, the card shows "Loading marathon pace…" with `role="status"`.
- **AC7.2 (not configured)** Given 404 `marathon_pace_not_configured`, then the card shows "Marathon pace not configured." and the muted hint "Set MARATHON_GOAL_PACE in .env (for example 4:15), then restart the backend." No chart, no numbers, no Retry.
- **AC7.3 (invalid config)** Given 422 `marathon_pace_invalid_config` with message `M`, then the card shows "Marathon pace settings have a problem: M" in `role="alert"` and the same hint. No Retry.
- **AC7.4 (error)** Given any other error (500, network, or a 200 body that fails the shape check), then the card shows "Couldn't load marathon pace." and a Retry button that repeats the request. A 401 shows nothing in the card (the interceptor redirects to /connect).
- **AC7.5 (empty)** Given a 200 with `summary.totalMpKm` 0.0 and `summary.thisWeekMpKm` 0.0, then the card shows the subtitle and "No marathon-pace km in the build yet." instead of the chart and summary.
- **AC7.6 (normal)** Given a 200 with any MP km, then the subtitle, summary and chart are shown.
- **AC7.7** Given any of the states above, the rest of the dashboard keeps working (the card's request is independent of `/api/dashboard/running`).

### R8: Placement
- **AC8.1** Given the dashboard page rendered with the dashboard loaded, then `app-marathon-pace-section` comes after `app-workout-pace-comparison-chart` and before `app-build-calendar-section` in document order, and `app-build-calendar-section` is still the last child.

### R9: Pending laps
- **AC9.1** Given 2 outdoor Runs in the build up to today with `laps` null, then `runsPendingLaps` is 2 and the card shows the muted footnote "2 runs still importing laps; numbers will update." (singular "1 run").
- **AC9.2** Given `runsPendingLaps` 0, then no footnote is shown. Treadmill runs never count as pending.

### R10: Reload after sync
- **AC10.1** Given the card is loaded and `SyncStore.dataVersion` changes, then the card requests `/api/marathon-pace` again without showing the loading state, and keeps its current content if that request fails.

## Traceability
| Success criterion (intent) | Covered by |
|---|---|
| S1 | R3, R6 |
| S2 | R1, R2 |
| S3 | R4, R6 |
| S4 | R2 |
| S5 | R1, R5, R7 |
| S6 | R8 |
| S7 | R5 |

## Design

### Data from Strava
- Endpoints: none new. The card reads only the local store filled by the background sync (feature 009): run summaries (`GET /athlete/activities`) and lap details (`GET /activities/{id}`), already cached in `runs` and `run_laps`.
- Scopes: unchanged (`read,activity:read`). No new scope.
- Fields used: `runs.start_date_local`, `runs.distance_m`, `runs.trainer`; `run_laps.distance_m`, `run_laps.elapsed_time_s`; plus `runs.detail_fetched_at` (via `laps == null`) for the pending count.
- Sync & caching: no change to `SyncPolicy` or `SyncService`. The kept history (6 months + 41 days) already covers the build start (17 Aug 2026) for the whole build.
- Errors: the endpoint makes no Strava call, so 401/429/network failures from Strava can't happen here. Those are surfaced by the existing sync banner (009). The card keeps showing the last cached numbers.

### Domain model & logic
New package `domain/marathonpace/`.

```kotlin
// MarathonPaceVolume.kt (pure)
data class MpGoal(val pace: SecondsPerKm) {
    val fastest: SecondsPerKm  // pace - 10
    val slowest: SecondsPerKm  // pace + 10
}
data class MpWeek(val index: Int, val weekStart: LocalDate, val weekEnd: LocalDate,
                  val mpKm: Double, val isCurrentWeek: Boolean, val isFuture: Boolean)
data class MpSummary(val totalMpKm: Double, val thisWeek: DateWindow, val thisWeekMpKm: Double,
                     val thisWeekTotalKm: Double, val thisWeekSharePercent: Int?)
data class MpReport(val goal: MpGoal, val buildStart: LocalDate, val buildEnd: LocalDate,
                    val weeks: List<MpWeek>, val summary: MpSummary, val runsPendingLaps: Int)

object MarathonPaceVolume {
    const val TOLERANCE_S_PER_KM = 10
    const val MIN_LAP_METERS = 200.0
    const val BUILD_WEEKS = 16
    fun isMpLap(lap: Lap, goal: MpGoal): Boolean
    fun mpMeters(run: RunActivity, goal: MpGoal): Double   // 0.0 for treadmill / non-Run / laps null
    fun compute(runs: List<RunActivity>, goal: MpGoal, buildStart: LocalDate, today: LocalDate): MpReport
}
```

Rules:
- `isMpLap`: `lap.distance.value >= MIN_LAP_METERS && lap.elapsed.value > 0 && elapsed * 1000 >= fastest * distance && elapsed * 1000 <= slowest * distance` (all in `Double`, no rounding before the comparison).
- Week bucketing: `run.localDate` (from `start_date_local`); week start via `WeeklyDistance.weekStartOf` for the current week. Build week `i` (1-based) starts at `buildStart.plusWeeks(i - 1)`.
- `isCurrentWeek`: `today` within `weekStart..weekEnd`. `isFuture`: `weekStart > today`.
- km values: `Meters(sumOfMetres).toKmOneDecimal()` (half-up). `totalMpKm` sums metres across all 16 weeks, then rounds once (so it can differ by 0.1 from adding the rounded bars).
- `thisWeekTotalKm`: sum of `run.distance` for all Runs (treadmill included) in the current calendar week, same as `WeeklyDistance`.
- `thisWeekSharePercent`: `null` when the week's Run distance is 0; else `roundHalfUpToInt(mpMetres / totalMetres * 100)` coerced to `0..100`.
- `runsPendingLaps`: Runs with `!trainer && laps == null` and `localDate` in `buildStart..min(today, buildEnd)`.
- No timezone conversion beyond the existing `clock.withZone(zone)` for "today" (as in other services).

`MarathonPaceService(setting: MarathonPaceSetting, buildStart: LocalDate, tokens, runs, clock, zone)` with `suspend fun get(): MarathonPaceResult` where `sealed interface MarathonPaceResult { NotConnected; NotConfigured; InvalidConfig; Ready(report) }`. It depends only on `TokenRepository` and `RunRepository` (no Strava client, no `BackgroundSync`), which is what makes S7 hold structurally.

Config (`config/MarathonPaceSetting.kt`): `sealed interface MarathonPaceSetting { data object NotConfigured; data object Invalid; data class Valid(val pace: SecondsPerKm) }` with `parse(text: String?)`: trim; empty → `NotConfigured`; must match `^(\d{1,2}):([0-5]\d)$` and be in 150..480 s, else `Invalid`. `Valid.toString()` is redacted, as in `LthrSetting`.

### Persistence
No schema change. Reads via the existing `RunRepository.findAll()`.

### Backend API
- `GET /api/marathon-pace`: no query params; connected session required (`hasConnectedSession(tokens)`).
- Response 200 (JSON, exact; example for today 2026-10-31, goal 4:15, weeks 3–15 abbreviated):
```json
{
  "goal": {
    "paceSecondsPerKm": 255,
    "toleranceSecondsPerKm": 10,
    "fastestSecondsPerKm": 245,
    "slowestSecondsPerKm": 265,
    "minLapMeters": 200
  },
  "buildStart": "2026-08-17",
  "buildEnd": "2026-12-06",
  "weeks": [
    { "weekIndex": 1, "weekStart": "2026-08-17", "weekEnd": "2026-08-23", "label": "17 Aug", "mpKm": 0.0, "isCurrentWeek": false, "isFuture": false },
    { "weekIndex": 2, "weekStart": "2026-08-24", "weekEnd": "2026-08-30", "label": "24 Aug", "mpKm": 3.2, "isCurrentWeek": false, "isFuture": false },
    { "weekIndex": 11, "weekStart": "2026-10-26", "weekEnd": "2026-11-01", "label": "26 Oct", "mpKm": 6.4, "isCurrentWeek": true, "isFuture": false },
    { "weekIndex": 16, "weekStart": "2026-11-30", "weekEnd": "2026-12-06", "label": "30 Nov", "mpKm": 0.0, "isCurrentWeek": false, "isFuture": true }
  ],
  "summary": {
    "totalMpKm": 41.0,
    "thisWeekStart": "2026-10-26",
    "thisWeekEnd": "2026-11-01",
    "thisWeekMpKm": 6.4,
    "thisWeekTotalKm": 26.0,
    "thisWeekSharePercent": 25
  },
  "runsPendingLaps": 0
}
```
Field types: all `*SecondsPerKm`, `weekIndex`, `minLapMeters`, `runsPendingLaps`: integer. `mpKm`, `totalMpKm`, `thisWeekMpKm`, `thisWeekTotalKm`: number, km, 1 decimal. `thisWeekSharePercent`: integer 0–100 or `null`. Dates: ISO local dates (`YYYY-MM-DD`). `label`: `d MMM`, English. `weeks` always has 16 entries in ascending order.

- Errors:

| Status | `error` | `message` |
|---|---|---|
| 401 | `not_connected` | "Connect your Strava account." |
| 404 | `marathon_pace_not_configured` | "No goal marathon pace is configured." |
| 422 | `marathon_pace_invalid_config` | "MARATHON_GOAL_PACE is not a pace between 2:30 and 8:00 per km (m:ss)." |
| 500 | `internal_error` | "Something went wrong." (existing `StatusPages`) |

The two new `ErrorResponse` values are defined in `api/MarathonPaceDtos.kt` (not in the shared `ErrorResponse.kt`) to keep shared-file edits small.

### Frontend (Angular)
- Route / page: no new route. The card is rendered by the existing dashboard page.
- Components: `features/marathon-pace/marathon-pace-section/marathon-pace-section.ts` (+ `.html`, `.scss`), selector `app-marathon-pace-section`. It is a self-loading section like `TrainingLoadSection`: its own request, states and chart, `OnPush`, signals, `inject()`. It optionally injects `SyncStore` for the quiet reload (R10). No inputs/outputs.
- Pure helpers: `features/marathon-pace/marathon-pace.view.ts` (`MarathonPaceState` union, `stateFromError(error)`, `subtitleText(goal)`, `summaryItems(summary)`, `pendingText(count)`, `isEmpty(response)`), `features/marathon-pace/marathon-pace.options.ts` (`buildMarathonPaceOptions(response): EChartsOption`, `weekTooltip(week)`). Pace text uses `shared/format/pace.ts` `formatPace`; km text uses `formatOneDecimal`.
- Service & TS interfaces: new `core/api/marathon-pace.models.ts` (`MarathonPaceResponse`, `MpGoal`, `MpWeek`, `MpSummary`, `isMarathonPaceResponse` shape guard) and `core/api/marathon-pace.service.ts` (`getMarathonPace(): Observable<MarathonPaceResponse>` → `GET /api/marathon-pace`). Field names and types exactly match the JSON above. No changes to `running-dashboard.models.ts`.
- States: loading ("Loading marathon pace…") · not configured ("Marathon pace not configured." + hint) · invalid config (alert + hint) · error ("Couldn't load marathon pace." + Retry) · empty ("No marathon-pace km in the build yet.") · normal (subtitle, three summary values, chart, optional pending footnote).
- Chart: bar chart through the shared `ChartComponent`; x = category, the 16 week labels; y = value, name `km`; one series "MP km"; bar colours from `palette` (accent for current week, secondary for others); value labels on top of past/current bars with 1 decimal; tooltip per AC6.6; theme from `shared/chart/chart-theme.ts`. Tooltip text contains only dates and numbers (no Strava free text).
- Placement: in `running-dashboard-page.html`, one line `<app-marathon-pace-section />` directly before `<app-build-calendar-section />` (after the `@switch` block that holds the "Workout pace by type" card), and one import in `running-dashboard-page.ts`. `:host { display: block; margin-top: 1rem; }` as in `BuildCalendarSection`.
- Tests: page specs that render `RunningDashboardPage` and call `http.verify()` need the new card swapped for a stub, via a new `testing/marathon-pace-stub.ts` (`stubMarathonPaceSection()`), one added line per page spec.

### Files expected to change
New (backend):
- `backend/src/main/kotlin/app/stravadashboard/config/MarathonPaceSetting.kt`
- `backend/src/main/kotlin/app/stravadashboard/domain/marathonpace/MarathonPaceVolume.kt`
- `backend/src/main/kotlin/app/stravadashboard/domain/marathonpace/MarathonPaceService.kt`
- `backend/src/main/kotlin/app/stravadashboard/api/MarathonPaceDtos.kt`
- `backend/src/main/kotlin/app/stravadashboard/api/MarathonPaceMappers.kt`
- `backend/src/main/kotlin/app/stravadashboard/api/MarathonPaceRoutes.kt`
- `backend/src/test/kotlin/app/stravadashboard/config/MarathonPaceSettingTest.kt`
- `backend/src/test/kotlin/app/stravadashboard/domain/marathonpace/MarathonPaceVolumeTest.kt`
- `backend/src/test/kotlin/app/stravadashboard/api/MarathonPaceRoutesTest.kt`

Changed (backend, small):
- `backend/src/main/kotlin/app/stravadashboard/config/AppConfig.kt`: one field, one parse line, one `toString` entry.
- `backend/src/main/kotlin/app/stravadashboard/Application.kt`: construct `MarathonPaceService`, register `marathonPaceRoutes`.
- `.env.example`: `MARATHON_GOAL_PACE=` with comment.
- `backend/src/test/kotlin/app/stravadashboard/AppConfigTest.kt`: toString masking case (or put it in `MarathonPaceSettingTest`).

New (frontend):
- `frontend/src/app/core/api/marathon-pace.models.ts`
- `frontend/src/app/core/api/marathon-pace.service.ts` + `.spec.ts`
- `frontend/src/app/features/marathon-pace/marathon-pace-section/marathon-pace-section.ts` / `.html` / `.scss` / `.spec.ts`
- `frontend/src/app/features/marathon-pace/marathon-pace.view.ts` + `.spec.ts`
- `frontend/src/app/features/marathon-pace/marathon-pace.options.ts` + `.spec.ts`
- `frontend/src/app/testing/marathon-pace-fixtures.ts`
- `frontend/src/app/testing/marathon-pace-stub.ts`

Changed (frontend, small):
- `frontend/src/app/features/running-dashboard/running-dashboard-page.ts`: one import.
- `frontend/src/app/features/running-dashboard/running-dashboard-page.html`: one line.
- `frontend/src/app/features/running-dashboard/running-dashboard-page{,.load,.plan,.redesign,.vo2max}.spec.ts`: one `stubMarathonPaceSection()` call each; the placement test (AC8.1) goes in `running-dashboard-page.plan.spec.ts` or a new `running-dashboard-page.marathon-pace.spec.ts` (preferred, to avoid conflicts).

Not changed: `SyncPolicy`, `SyncService`, `Tables.kt`, `ErrorResponse.kt`, `running-dashboard.models.ts`, `testing/dashboard-fixtures.ts`, `RunningDashboardDtos.kt`.

## Edge cases
| Case | Expected behaviour |
|---|---|
| No activities in range | All 16 weeks `mpKm` 0.0, `totalMpKm` 0.0, share `null` → empty state (AC7.5). |
| Manual activity (no GPS) | Stored as a Run if Strava lists it. With no laps it adds 0 MP km (AC2.9); its distance still counts in `thisWeekTotalKm`. If Strava returns a single whole-activity lap in MP range, it counts like any outdoor lap (manual flag is not stored). |
| Activity without heart rate | No effect: heart rate is not used. |
| Week spans DST change / time zones | Buckets by the local date of `start_date_local` (AC3.6); "today" uses the server zone as elsewhere. DST doesn't change local dates. |
| Treadmill run | 0 MP km (AC2.4); its distance counts in this week's total (AC4.3); never pending (AC9.2). |
| Laps shorter than 200 m (e.g. the last partial km) | Excluded (AC2.3). |
| Lap with 0 distance or 0 elapsed time | Excluded, no division by zero (AC2.5). |
| Lap pace exactly on 4:05 or 4:25 | Counted (inclusive, AC2.1). |
| Stops inside a lap | Elapsed time includes them, so the lap is slower and may fall outside MP. |
| Run's detail not fetched yet | 0 MP km for now and counted in `runsPendingLaps` (AC9.1). |
| Lap distances sum above activity distance | Share capped at 100 % (AC4.6). |
| Today before / after the build | All weeks future / all past; summary still for the calendar week (AC3.8, AC4.7). |
| Goal pace unset / invalid | 404 / 422; card shows not-configured / problem text; no numbers (AC7.2, AC7.3). |
| `TRAINING_PLAN_START_DATE` changed | The 16 weeks start there (AC3.9), matching the build calendar's start. |
| Runs older than history retention | Not relevant during this build: retention (6 months + 41 days) covers 17 Aug 2026 until after race day. |

## Privacy
- Data fields & sensitivity: run local start date, run distance, treadmill flag, lap distance and lap elapsed time. Low sensitivity (activity data, no location, no health data). No heart rate, GPS, polylines, names or descriptions are read or returned. The goal pace (`MARATHON_GOAL_PACE`) is a personal training setting from `.env`; it is returned to the owner's session only and not logged.
- Storage & retention: nothing new stored. Reads the existing SQLite cache, with the existing retention and disconnect/delete behaviour.
- Scopes: no change (`read,activity:read`).
- Applicable privacy-check items:

| # | Status | How |
|---|---|---|
| 1 | ✅ | Goal pace only from env; `.env.example` has no value; fixtures invented. |
| 2 | ✅ | No token use; the endpoint doesn't touch Strava. |
| 3, 4 | n/a | OAuth unchanged. |
| 5 | ✅ | No new scope. |
| 6 | ✅ | No new stored fields. |
| 7 | ✅ | No GPS used. |
| 8 | ✅ | No new logging; if added, only counts. The setting value is never logged (`toString` redacted). |
| 9 | n/a | No client change. |
| 10 | ✅ | Errors use the standard `{error, message}` shape with fixed text (AC5.4). |
| 11 | ✅ | Route checks the connected session (AC5.2). |
| 12 | n/a | Sessions unchanged. |
| 13 | ✅ | Card renders only numbers, dates and fixed text through interpolation; tooltip formatter builds text from numbers/dates only. |
| 14 | n/a | GET only, no state change. |
| 15 | ✅ | Response contains only chart-ready fields; no raw Strava objects. |
| 16, 17 | ✅ | No third parties; ECharts is bundled. |
| 18–20 | ✅ | Unchanged; no new data stored. |
| 21 | ✅ | Test fixtures use invented runs and laps. |

## Test plan
| AC | Test type | Test name |
|---|---|---|
| AC1.1–AC1.3 | backend unit | `R1 - MARATHON_GOAL_PACE unset is not configured`, `R1 - MARATHON_GOAL_PACE valid range`, `R1 - MARATHON_GOAL_PACE invalid values do not stop startup` |
| AC1.4 | backend unit | `R1 - toString masks marathon pace` |
| AC1.5 | review | `.env.example` diff |
| AC2.1–AC2.3, AC2.7 | backend unit | `R2 - pace edges are inclusive`, `R2 - laps under 200 m are excluded`, `R2 - pace check is exact` |
| AC2.4 | backend unit | `R2 - treadmill laps add nothing` |
| AC2.5 | backend unit | `R2 - zero distance or zero time laps are ignored` |
| AC2.6 | backend unit | `R2 - only MP laps of a long run count` |
| AC2.8, AC2.9 | backend unit | `R2 - runs without laps add nothing` |
| AC3.1–AC3.3, AC3.5, AC3.9 | backend unit | `R3 - sixteen weeks from the build start`, `R3 - current and future week flags` |
| AC3.4 | backend unit | `R3 - week km summed in metres then rounded` |
| AC3.6 | backend unit | `R3 - buckets by local start date` |
| AC3.7, AC3.8 | backend unit | `R3 - runs outside the build are ignored`, `R3 - before and after the build` |
| AC4.1–AC4.3, AC4.5–AC4.7 | backend unit | `R4 - summary totals and share`, `R4 - share is null with no runs`, `R4 - share capped at 100`, `R4 - this week outside the build` |
| AC4.4 | backend unit | `R4 - this week total matches week comparison` (same runs through `WeekComparison`/`WeeklyDistance` and `MarathonPaceVolume`) |
| AC5.1, AC5.6 | backend route | `R5 - returns the marathon pace contract` |
| AC5.2 | backend route | `R5 - not connected gives 401` |
| AC5.3, AC5.4 | backend route | `R5 - not configured gives 404`, `R5 - invalid config gives 422 without the value` |
| AC5.5 | backend route | `R5 - never calls Strava` |
| AC9.1 (backend) | backend unit | `R9 - counts outdoor runs pending laps` |
| AC5.1 (client) | frontend service | `R5 - getMarathonPace requests GET /api/marathon-pace` |
| AC6.1–AC6.3 | frontend component | `R6 - shows subtitle and summary`, `R6 - share shows dash with no runs this week` |
| AC6.4–AC6.7 | frontend unit (options) | `R6 - one bar per build week`, `R6 - bar colours and labels`, `R6 - tooltip text` |
| AC7.1–AC7.6 | frontend component | `R7 - loading state`, `R7 - not configured`, `R7 - invalid config`, `R7 - error with retry`, `R7 - empty state`, `R7 - normal state` |
| AC7.4 (shape) | frontend unit (view) | `R7 - unexpected body shows error` |
| AC7.7 | frontend component (page) | `R7 - card error does not affect the dashboard` |
| AC8.1 | frontend component (page) | `R8 - card sits between workout pace and build calendar` |
| AC9.1, AC9.2 | frontend component | `R9 - pending laps footnote` |
| AC10.1 | frontend component | `R10 - reloads quietly after sync` |

## Out of scope
- Comparison to a plan MP target (the plan has none).
- Speed streams or sub-lap analysis.
- Treadmill MP data.
- Any change to the training-plan CSV.
- Alerts or recommendations about MP volume.
- New Strava calls, scopes or tables; changes to `SyncPolicy`.

## Suggested follow-ups
- Store Strava's `manual` flag so manual activities can be excluded explicitly.
- Make the tolerance configurable if 10 s/km proves too tight or loose.
- Take the build length from the plan CSV's week count instead of the fixed 16.

## Risks & decisions to confirm
1. **Env setting name and format:** `MARATHON_GOAL_PACE=4:15` (m:ss, 2:30–8:00). Unset gives "not configured" (S5); an invalid value gives a separate "settings have a problem" message, following `LTHR_BPM`.
2. **Build range:** 16 weeks from `TRAINING_PLAN_START_DATE` (default 2026-08-17 → ends 2026-12-06, race day). The count is a fixed constant, not read from the plan CSV, so the card works even without a plan file. If the plan's week count ever differs from 16, the two cards will disagree.
3. **"This week"** is the current calendar Monday-Sunday week (as in 007), even before or after the build.
4. **Totals rounding:** `totalMpKm` is rounded once from summed metres, so it can differ by 0.1 from adding the rounded bars.
5. **Empty state:** when there are no MP km in the build at all, the card shows a message instead of 16 zero bars and zero tiles.
6. **Lap pace uses elapsed time** (stops count against the lap), as the intent assumes; there's no moving time per lap in the cache.
7. **Shared-file touches:** `AppConfig.kt`, `Application.kt`, `.env.example`, `running-dashboard-page.ts/.html` and the five page specs (one line each to stub the new card) are unavoidable. Parallel features 011/013–015 will probably edit nearby lines, so expect small merge conflicts there. `ErrorResponse.kt`, `running-dashboard.models.ts`, `testing/dashboard-fixtures.ts` and `SyncPolicy` are left alone.
8. **Pending footnote (R9) and sync reload (R10)** are "Should" items that follow existing card patterns. They can be dropped if you want the smallest possible change.

## Change (2026-11-03): treadmill workouts
R2's treadmill exclusion is relaxed for `NxMmin` treadmill runs whose description has a pace line (e.g. `4:15 4:10`, read by `TreadmillPaceParser`). Each of the first N paces counts as one rep of M minutes at that pace, and counts as MP only if inside the same goal ± 10 s/km band. Other treadmill runs still add nothing.
