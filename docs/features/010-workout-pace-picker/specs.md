# Spec: Workout pace picker

**Feature ID:** 010 · **Status:** Draft · **Intent:** [intent.md](./intent.md) · **Date:** 2026-10-31

## Summary
The "Workout pace: last 30 days vs previous 30 days" bar chart is replaced by a "Workout pace by type" card that works like "Heart rate by run type": you pick a workout type (with run counts) and see one point per run of its average rep pace over the last 5 months, with treadmill runs drawn as triangles. The existing 30-day vs previous 30-day comparison for the chosen type stays as a summary line under the chart. The "Plan notes" panel is removed from the Build calendar, and its `notes` field is dropped from `GET /api/training-plan`.

## Requirements
| ID | Requirement | Priority (Must/Should/Could) |
|---|---|---|
| R1 | The backend lists every workout type that has at least one Run in the 5-month window, with its run count, ordered by most recent run first. | Must |
| R2 | For each workout type the backend returns one point per run that has a rep pace (local date, average rep pace in whole s/km, treadmill flag). Rep pace uses the existing `RepPaceCalculator`, so treadmill paces come from the description pace line (`TreadmillPaceParser`, as in 008). Runs without a rep pace are left out and counted. | Must |
| R3 | For each listed workout type the backend still returns the last-30-days vs previous-30-days average rep pace, run counts and change, with unchanged rules. | Must |
| R4 | The card shows a "Workout type" selector listing each type as `<label> (<n> runs)`. The first type (most recent run) is preselected, and the choice is kept when the dashboard reloads. | Must |
| R5 | For the selected type the card shows a chart with one point per run: time on x, rep pace on y, with units in the axis name and tooltip. | Must |
| R6 | Treadmill points use a triangle marker in the treadmill colour. Outdoor points use a circle in the accent colour. A legend names both. | Must |
| R7 | Under the chart, a summary line shows the selected type's previous-30-days pace, last-30-days pace and the change. When either window has no paced run, a "not enough data" message replaces it. | Must |
| R8 | When the selected type has runs without a rep pace, a muted note says how many are not shown. | Should |
| R9 | The card has an empty state (no workout types in 5 months) and a no-points state (the selected type has no paced runs). Loading and error stay at page level as today. | Must |
| R10 | The Build calendar no longer shows the Plan notes panel, and `notes` is removed from the `GET /api/training-plan` contract. Target paces and the calendar are unchanged. | Must |

## Acceptance criteria
### R1: Workout types over 5 months
- **AC1.1** Given today is 2026-10-31 and a `5x6min` Run on 2026-06-15 and none later, when the dashboard is requested, then `workoutPaceComparison.types` contains `5x6min`, even though it has no run in the last 60 days.
- **AC1.2** Given a `4x8min` Run on 2026-05-30 only (before `windowStart` 2026-05-31), when requested, then `4x8min` is not in `types`.
- **AC1.3** Given a Run exactly on `windowStart` (2026-05-31, local date), when requested, then it is included.
- **AC1.4** Given `5x6min` last run on 2026-10-20 and `4x8min` last run on 2026-10-27, when requested, then `types` is ordered `["4x8min", "5x6min"]`.
- **AC1.5** Given 6 `5x6min` Runs in the window, 1 of them without a rep pace, when requested, then that type has `runCount` 6.
- **AC1.6** Given easy runs, long runs and non-Run activities (e.g. `Ride`) in the window, when requested, then none of them appear in `types`.
- **AC1.7** Given the response, then it contains `windowStart` = today minus 5 months and `windowEnd` = today, as ISO local dates, matching `heartRateTrend.windowStart`/`windowEnd`.

### R2: Per-run points
- **AC2.1** Given three paced `5x6min` runs on 2026-09-02, 2026-07-10 and 2026-10-14, when requested, then `points` has three entries ordered by date ascending: `2026-07-10`, `2026-09-02`, `2026-10-14`.
- **AC2.2** Given an outdoor workout whose stored stream result is COMPUTED at 252 s/km, when requested, then its point has `repPaceSecondsPerKm` 252 and `treadmill` false.
- **AC2.3** Given a treadmill (`trainer` = true) `5x6min` run with description `"Felt good\n4:15 4:10"`, when requested, then its point has `repPaceSecondsPerKm` 253 (half-up average of 255 and 250) and `treadmill` true.
- **AC2.4** Given a treadmill run with no description, or no valid pace line, when requested, then it has no point and adds 1 to `runsWithoutPace`.
- **AC2.5** Given an outdoor workout with no stream result yet (pending sync) and no usable laps, when requested, then it has no point and adds 1 to `runsWithoutPace`.
- **AC2.6** Given two paced runs of the same type on the same local date, when requested, then both appear as separate points with the same `date`.
- **AC2.7** For every type, `runCount` = `points.length` + `runsWithoutPace`.
- **AC2.8** Given a run with `start_date_local` 2026-10-01T23:30 and UTC start 2026-10-02T05:30Z, when requested, then its point `date` is `2026-10-01`.

### R3: 30-day comparison per type
- **AC3.1** Given today 2026-10-31, when requested, then `currentWindow` is `2026-10-02`..`2026-10-31` and `previousWindow` is `2026-09-02`..`2026-10-01` (unchanged from 001).
- **AC3.2** Given `5x6min` paced runs averaging 260 s/km in the previous window (2 runs) and 253 s/km in the current window (3 runs), when requested, then `previous` = `{260, 2}`, `current` = `{253, 3}`, `changeSecondsPerKm` = −7 and `hasEnoughData` true.
- **AC3.3** Given a type with paced runs only in the current window, when requested, then `previous` is null, `changeSecondsPerKm` is null and `hasEnoughData` false.
- **AC3.4** Given a type whose runs are all older than 60 days, when requested, then `current` and `previous` are both null and `hasEnoughData` is false, and its `points` are still returned.

### R4: Type selector
- **AC4.1** Given types `4x8min` (3 runs) and `5x6min` (6 runs), when the card renders, then the selector options read `4x8min (3 runs)` and `5x6min (6 runs)`, in that order.
- **AC4.2** Given no selection yet, when the card renders, then the first type (`4x8min`) is selected and drawn.
- **AC4.3** Given the user picks `5x6min`, when the selector changes, then the card emits `typeSelected` with `5x6min`, and the page passes it back as `selectedTypeKey`, so the chart and summary switch to `5x6min`.
- **AC4.4** Given `5x6min` is selected and the dashboard reloads (Retry) with data where `5x6min` still exists, then `5x6min` stays selected. If it no longer exists, the first type is selected.
- **AC4.5** Given a type with exactly 1 run, then its option reads `<label> (1 run)`.

### R5: Per-run chart
- **AC5.1** Given the selected type has 4 points, when the options are built, then the outdoor and treadmill scatter series together hold 4 data items `[date, repPaceSecondsPerKm]`, and a connecting line series (no symbols, not interactive) runs through all 4 in date order.
- **AC5.2** Given the options, then the x-axis is `type: 'time'` and the y-axis is named `Rep pace (min/km), lower is faster`, with labels formatted `m:ss` (253 → `4:13`).
- **AC5.3** Given an outdoor point on 2026-10-14 at 253 s/km, when its tooltip is built, then it reads `14 Oct 2026 · 4:13 /km`. A treadmill point reads `14 Oct 2026 · 4:13 /km · Treadmill`.
- **AC5.4** Given the selected type has exactly 1 point, when the card renders, then the chart is shown with that single marker.
- **AC5.5** Given the user switches type, when the card re-renders, then the chart data contains only the new type's points.

### R6: Treadmill marker
- **AC6.1** Given a type with outdoor and treadmill points, when the options are built, then series `Outdoor` has `symbol: 'circle'` in `palette.accent` and series `Treadmill` has `symbol: 'triangle'` in `palette.treadmill`, and each holds only its own points.
- **AC6.2** Given the options, then the legend lists `Outdoor` and `Treadmill` for the series that have points. A type with no treadmill points has no `Treadmill` series or legend entry, and the same goes for `Outdoor`.

### R7: 30-day summary line
- **AC7.1** Given the selected type has `previous` 260, `current` 253, change −7, when the card renders, then the text under the chart reads `Last 30 days vs previous 30 days: 4:20 /km → 4:13 /km · 7 s/km faster`, and the change span has class `tone-better` (tone from `toneForChange`).
- **AC7.2** Given a change of +3, then the change reads `3 s/km slower` with the worse tone. A change of 0 reads `same pace` with the neutral tone.
- **AC7.3** Given `previous` null, then the text reads `Not enough data for a 30-day comparison: no paced runs in the previous 30 days`. With `current` null it ends `in the last 30 days`, and with both null `in either period`.
- **AC7.4** Given the selected type changes, then the summary line shows the new type's values.

### R8: Runs not shown
- **AC8.1** Given the selected type has `runsWithoutPace` 2, when the card renders, then a muted note reads `2 runs without a rep pace are not shown`. For 1, it reads `1 run without a rep pace is not shown`.
- **AC8.2** Given `runsWithoutPace` 0, then no such note is rendered.

### R9: Card states
- **AC9.1** Given `types` is empty, when the card renders, then it shows the title, the text `No workouts (e.g. 5x6min) in the last 5 months`, and no selector or chart.
- **AC9.2** Given the selected type has 0 points (all runs lack a rep pace), when the card renders, then the selector is shown, there is no chart, the text reads `No runs with a rep pace for this workout type yet`, and the R7 summary and R8 note are still shown.
- **AC9.3** Given the dashboard request is loading or fails, then the page-level loading spinner or error-with-Retry shows as today, and the card is not rendered.
- **AC9.4** Given the card renders in the normal state, then its heading reads `Workout pace by type`.

### R10: Plan notes removed
- **AC10.1** Given a training plan whose sheet has a NOTES column with entries, when the Build calendar section renders, then there is no `Plan notes` heading and no `.notes` panel, and the target paces and calendar are still shown.
- **AC10.2** Given `GET /api/training-plan` succeeds, then the top-level JSON keys are exactly `startDate endDate progress targetPaces weeks` (no `notes`).
- **AC10.3** Given a sheet with a NOTES column, when target paces are parsed, then header cells in and right of the NOTES column are still never read as paces (the existing 004 boundary still holds).
- **AC10.4** Given a 200 body without `notes`, when the frontend checks its shape (`isTrainingPlanResponse`), then it is accepted.

## Traceability
| Success criterion (intent) | Covered by |
|---|---|
| S1: choose a workout type from a selector showing run counts | R1, R4 |
| S2: one point per run with average rep pace, last 5 months | R1, R2, R5, R9 |
| S3: last 30 vs previous 30 days pace and change below the chart | R3, R7 |
| S4: treadmill rep paces using the 008 rule, distinct marker shape | R2, R6 |
| S5: Build calendar no longer shows Plan notes | R10 |

## Design

### Data from Strava
No new Strava calls. Everything comes from the SQLite cache that the existing sync fills:
- Endpoints: unchanged (`GET /athlete/activities`, `GET /activities/{id}`, `GET /activities/{id}/streams`, already used by the sync).
- Scopes: unchanged (`read`, `activity:read`). No new scope.
- Fields used: `sport_type`, `name` (classification), `start_date`, `start_date_local`, `trainer`, `description` (treadmill pace line), laps, and the stored speed-stream rep-pace result. All of these are already stored.
- Sync & caching: unchanged. History is kept for 6 months + 41 days (`SyncPolicy.HISTORY_MONTHS`), which covers the 5-month window. Workout rep paces from streams are already fetched for every stored outdoor workout (`RepPaceCalculator.needsStream`).
- Errors: unchanged dashboard behaviour. 401 or revoked token → `ReauthRequired` → 401 → frontend redirects to `/connect`. 429 → sync stops, response built from cache with `sync.status = "rateLimited"` and the sync banner. Network failure → `stravaUnavailable` banner, cached data shown. Runs still waiting for a stream have no rep pace yet and count in `runsWithoutPace`.

### Domain model & logic
In `domain/WorkoutPaceComparison.kt`:
- New `data class PacePoint(val date: LocalDate, val repPace: SecondsPerKm, val treadmill: Boolean)`.
- `PaceTypeComparison` gains `runCount: Int` and `points: List<PacePoint>`. `runsWithoutPace` now counts over the 5-month window (`runCount - points.size`).
- `PaceComparisonResult` gains `windowStart: LocalDate` and `windowEnd: LocalDate`.
- `WorkoutPaceComparison.compute(runs, today)`:
  1. `windowStart = today.minusMonths(HeartRateTrend.WINDOW_MONTHS)`, `windowEnd = today`, inclusive, on `RunActivity.localDate` (from `start_date_local`). Make `HeartRateTrend.WINDOW_MONTHS` a public `const` and reference it, so the two cards can't drift.
  2. Filter `isRun` and in window. Classify with `RunClassifier.classify`, keep `RunType.Workout`, and group by type.
  3. Order types by most recent `startUtc` descending (rule unchanged).
  4. Per type: `repPace = RepPaceCalculator.repPace(run, type)`. Points are the runs with a non-null pace, sorted by `startLocal` ascending. `current`/`previous` use the existing `stat(...)` over the paced runs in each 30-day window (windows and rounding unchanged).
- Rep pace stays in whole seconds per km (half-up, as today). No unit conversion is needed beyond what `RepPaceCalculator` already does.
- The "today" date comes from the injected `Clock` in the configured zone (unchanged in `RunningDashboardService`).

Training plan (R10):
- Remove `TrainingPlan.notes`, `BuildCalendar.notes` and `PlanHeaderParser.notes()`. Keep the private `notesColumn()`, because `paces()` still uses it as the pace/notes boundary. Update the KDoc ("Target paces from the rows above the first week block").

### Persistence
No changes.

### Backend API
`GET /api/dashboard/running`: session required, no query params. Only the `workoutPaceComparison` object changes. All other fields stay the same. The change is additive except for the `runsWithoutPace` window.

```json
{
  "workoutPaceComparison": {
    "windowStart": "2026-05-31",
    "windowEnd": "2026-10-31",
    "currentWindow": { "start": "2026-10-02", "end": "2026-10-31" },
    "previousWindow": { "start": "2026-09-02", "end": "2026-10-01" },
    "types": [
      {
        "typeKey": "5x6min",
        "label": "5x6min",
        "runCount": 6,
        "runsWithoutPace": 1,
        "points": [
          { "date": "2026-07-10", "repPaceSecondsPerKm": 262, "treadmill": false },
          { "date": "2026-09-15", "repPaceSecondsPerKm": 260, "treadmill": true },
          { "date": "2026-10-14", "repPaceSecondsPerKm": 253, "treadmill": false }
        ],
        "current": { "averageRepPaceSecondsPerKm": 253, "runCount": 3 },
        "previous": { "averageRepPaceSecondsPerKm": 260, "runCount": 2 },
        "changeSecondsPerKm": -7,
        "hasEnoughData": true
      }
    ]
  }
}
```
| Field | Type | Meaning |
|---|---|---|
| `windowStart` / `windowEnd` | string (ISO local date) | 5-month window, inclusive; equals `heartRateTrend` window |
| `currentWindow` / `previousWindow` | `{start, end}` ISO local dates | 30-day windows (unchanged) |
| `types[]` | array | Workout types with ≥1 Run in the 5-month window, most recent run first |
| `typeKey`, `label` | string | e.g. `"5x6min"` (unchanged) |
| `runCount` | int | All runs of this type in the 5-month window |
| `runsWithoutPace` | int | Runs in the 5-month window with no rep pace (**was: in the 60-day span**) |
| `points[]` | array | One per paced run, date ascending |
| `points[].date` | string (ISO local date) | from `start_date_local` |
| `points[].repPaceSecondsPerKm` | int | average rep pace, s/km |
| `points[].treadmill` | boolean | Strava `trainer` |
| `current` / `previous` | `{averageRepPaceSecondsPerKm: int, runCount: int}` or null | unchanged |
| `changeSecondsPerKm` | int or null | current − previous; negative = faster (unchanged) |
| `hasEnoughData` | boolean | both windows have a paced run (unchanged) |

- Errors: unchanged (`401 {"error":"not_connected"|...}` as in 001; no new codes).

`GET /api/training-plan`: session required. The response loses the `notes` field and is otherwise unchanged:
```json
{ "startDate": "...", "endDate": "...", "progress": { }, "targetPaces": [ ], "weeks": [ ] }
```
- Errors: unchanged (404 `training_plan_not_configured`, 503 `training_plan_unavailable`, 422 `training_plan_invalid`).

### Frontend (Angular)
- Route / page: unchanged (`running-dashboard` page). The card stays in the `.cards` grid where the bar chart was.
- Components:
  - `WorkoutPaceComparisonChart` (`app-workout-pace-comparison-chart`, same file): inputs `comparison: WorkoutPaceComparison` (required) and `selectedTypeKey: string | null`, output `typeSelected: string`. It copies the `HeartRateTrendChart` pattern: a `selected` computed that falls back to the first type, a `<select>` styled like `.type-picker`, label `Workout type`, and options `{{label}} ({{runCount}} run|runs)`. It renders the chart, the summary line (`.summary`), the runs-not-shown note (`.note`, muted) and the empty texts from R7–R9. No business logic: text helpers live in the options file as pure functions.
  - `RunningDashboardPage`: new `selectedPaceTypeKey = signal<string | null>(null)`, wired like `selectedHrTypeKey`.
  - `TargetPaces`: remove the `notes` input, the Plan notes block and the `.note-list` styles. `BuildCalendarSection`: drop `[notes]` and update the doc comment.
- Service & TS interfaces (`core/api/running-dashboard.models.ts`): add `PacePoint { date: string; repPaceSecondsPerKm: number; treadmill: boolean }`. Add `runCount: number` and `points: PacePoint[]` to `PaceTypeComparison`, and `windowStart: string` and `windowEnd: string` to `WorkoutPaceComparison`. In `core/api/training-plan.models.ts`, remove `notes` from `TrainingPlanResponse`. In `plan-view.ts`, `isTrainingPlanResponse` no longer checks `notes`. No service method changes.
- States:
  - loading / error: page level, unchanged.
  - empty (no types): `No workouts (e.g. 5x6min) in the last 5 months`.
  - no points for the selected type: selector + `No runs with a rep pace for this workout type yet` + summary + note.
  - normal: selector, chart, summary line, optional note.
- Chart (`chart-options/workout-pace.options.ts`). Replace the bar builder with pure functions:
  - `buildWorkoutPaceTrendOptions(type: PaceTypeComparison): EChartsOption`
    - Series: `Pace line`, a line with `symbol: 'none'`, `silent: true`, width 1.5, `palette.secondary`, through all points by date. `Outdoor` is a scatter with `symbol: 'circle'`, size 7, `palette.accent`. `Treadmill` is a scatter with `symbol: 'triangle'`, size 9, `palette.treadmill`. A scatter series is left out when it has no points.
    - Legend at top with the scatter series names. Tooltip `trigger: 'item'`, using `pacePointTooltip`.
    - Axes: x is `type: 'time'`. y is `type: 'value'`, `scale: true`, named `Rep pace (min/km), lower is faster`, with labels from `formatPace`.
    - Styling via the existing `baseChartStyle`, `categoryAxisStyle`, `valueAxisStyle` and `tooltipStyle`.
  - `pacePointTooltip(point: PacePoint): string` returns `14 Oct 2026 · 4:13 /km`, plus ` · Treadmill` for treadmill points. It uses `formatDayMonthYear` and `formatPace` and has no Strava free text.
  - `paceSummaryText(type)` and `paceChangeTone(type)` build the R7 line from `formatPaceChange` and `toneForChange`. `notEnoughPaceText(type)` builds the R7 fallback text and replaces `missingWindowText`. `runsNotShownText(n: number): string | null` builds the R8 note.
  - Delete `buildWorkoutPaceOptions`, `comparablePaceTypes`, `PREVIOUS_SERIES`, `CURRENT_SERIES` and `missingWindowText` (dead after this change).

### Files expected to change
Backend
- `backend/src/main/kotlin/app/stravadashboard/domain/WorkoutPaceComparison.kt`
- `backend/src/main/kotlin/app/stravadashboard/domain/HeartRateTrend.kt` (make `WINDOW_MONTHS` public)
- `backend/src/main/kotlin/app/stravadashboard/api/RunningDashboardDtos.kt` (`PacePointDto`, new fields)
- `backend/src/main/kotlin/app/stravadashboard/api/RunningDashboardMappers.kt`
- `backend/src/main/kotlin/app/stravadashboard/domain/trainingplan/PlanHeaderParser.kt`, `TrainingPlan.kt`, `TrainingPlanParser.kt`, `BuildCalendarBuilder.kt`
- `backend/src/main/kotlin/app/stravadashboard/api/TrainingPlanDtos.kt`, `TrainingPlanMappers.kt`
- Tests: `backend/src/test/kotlin/app/stravadashboard/domain/AggregationsTest.kt` (or a new `WorkoutPacePickerTest.kt`), `OlderRunsRegressionTest.kt`, `api/RunningDashboardContractTest.kt`, `api/RoutesTest.kt`, `api/TrainingPlanRoutesTest.kt`, `domain/trainingplan/TrainingPlanParserTest.kt`

Frontend
- `frontend/src/app/core/api/running-dashboard.models.ts`
- `frontend/src/app/core/api/training-plan.models.ts`
- `frontend/src/app/features/running-dashboard/workout-pace-comparison-chart/workout-pace-comparison-chart.ts`
- `frontend/src/app/features/running-dashboard/chart-options/workout-pace.options.ts`
- `frontend/src/app/features/running-dashboard/running-dashboard-page.ts`, `running-dashboard-page.html`
- `frontend/src/app/features/calendar-like-build/target-paces/target-paces.ts`
- `frontend/src/app/features/calendar-like-build/build-calendar-section/build-calendar-section.ts`
- `frontend/src/app/features/calendar-like-build/plan-view.ts`
- Tests and fixtures: `features/running-dashboard/dashboard-cards.spec.ts`, `chart-options/chart-options.spec.ts`, `chart-options/chart-theme.spec.ts`, `features/calendar-like-build/plan-sections.spec.ts`, `features/running-dashboard/running-dashboard-page.plan.spec.ts`, `testing/dashboard-fixtures.ts`, `testing/training-plan-fixtures.ts`

## Edge cases
| Case | Expected behaviour |
|---|---|
| No activities in range | `types` is empty, and the card shows `No workouts (e.g. 5x6min) in the last 5 months`. The page-level "No runs in the last 5 months" still applies when all cards are empty. |
| Only easy/long runs, no workouts | Same as above: `types` is empty. |
| Manual activity (no GPS, no laps/streams) classified as a workout | Counted in `runCount`. No rep pace, so no point, and it counts in `runsWithoutPace` (R8 note). |
| Activity without heart rate | Not relevant to pace. It is included normally. |
| Treadmill run, no description or no valid pace line | No point, counted in `runsWithoutPace`. |
| Treadmill pace token outside 2:00–12:00 /km | The parser rejects the line → no point (existing 008 rule). |
| Outdoor workout still waiting for a stream (pending sync) | No point yet, counted in `runsWithoutPace`. It appears after a later sync. |
| Type with runs only older than 60 days | Listed with points. Summary: `...no paced runs in either period`. |
| Type with exactly 1 paced run | Chart with a single marker and no visible line. |
| Two runs of the same type on one day | Two points with the same date. |
| Selected type disappears after reload | Falls back to the first type. |
| Run near midnight / time zones / DST | Bucketed by `start_date_local` date. Window bounds are local dates, inclusive. DST does not affect day-based windows. |
| Window start on 31st (e.g. today 2026-07-31 → 2026-02-28) | `LocalDate.minusMonths` clamps to month end, same as `HeartRateTrend`. |
| Non-Run sport types (Ride, Walk, VirtualRun) | Excluded (`isRun`). |
| Plan sheet without a NOTES column | No effect. Paces are parsed as before. |

## Privacy
- Data fields & sensitivity: local run dates, average rep pace (s/km), the treadmill flag, workout type labels derived from activity names (not the raw names), and run counts. Pace and dates are personal activity data but not special-category data. No heart rate, GPS or free text is added to any response. The activity description is read only in the backend to parse the pace line and is never returned. Plan notes (user-written plan text) are no longer returned.
- Storage & retention: no new storage. It uses existing cached runs (pruned at 6 months + 41 days by `SyncPolicy`).
- Scopes: unchanged (`read`, `activity:read`).
- Applicable privacy-check items:
  - 2 ✅ No tokens involved. The response holds aggregated values only.
  - 5 ✅ No new scope.
  - 6 ✅ No new stored fields.
  - 7 ✅ No GPS used or returned.
  - 8 ✅ No new logging. If any is added, log type keys and counts only, never descriptions.
  - 10 ✅ Error shapes unchanged.
  - 11 ✅ Both endpoints keep the session check.
  - 13 ✅ Tooltips and texts use only dates, numbers and the derived type label (from `RunType.key`, `^\d+x\d+min$`). There is no `[innerHTML]`, and no ECharts formatter builds HTML from Strava text.
  - 15 ✅ Only chart-ready fields are returned, and `notes` is removed because the UI no longer needs it.
  - 16 ✅ No new dependencies or external calls.
  - 21 ✅ Fixtures use invented dates, paces and descriptions.
  - 1, 3, 4, 9, 12, 14, 17–20: n/a (unchanged by this feature).

## Test plan
| AC | Test type | Test name |
|---|---|---|
| AC1.1 | backend unit | `010 R1 - type with runs older than 60 days is listed` |
| AC1.2, AC1.3 | backend unit | `010 R1 - five month window is inclusive of its start date` |
| AC1.4 | backend unit | `010 R1 - types ordered by most recent run` |
| AC1.5 | backend unit | `010 R1 - run count includes runs without pace` |
| AC1.6 | backend unit | `010 R1 - only workout runs are listed` |
| AC1.7 | backend route | `010 R1 - pace window matches heart rate window` |
| AC2.1, AC2.6 | backend unit | `010 R2 - points ordered by date with one per run` |
| AC2.2 | backend unit | `010 R2 - outdoor point uses stream rep pace` |
| AC2.3 | backend unit | `010 R2 - treadmill point uses description pace line` |
| AC2.4, AC2.5, AC2.7 | backend unit | `010 R2 - runs without rep pace are excluded and counted` |
| AC2.8 | backend unit | `010 R2 - point date uses start date local` |
| AC3.1–AC3.3 | backend unit (update existing 001 pace tests) | `010 R3 - thirty day comparison unchanged` |
| AC3.4 | backend unit | `010 R3 - old-only type has no comparison but has points` |
| JSON shape | backend route | `010 R2 - workoutPaceComparison JSON contract` (RunningDashboardContractTest) |
| AC4.1, AC4.5 | frontend component | `010 R4 - selector lists workout types with run counts` |
| AC4.2 | frontend component | `010 R4 - first type preselected` |
| AC4.3 | frontend component | `010 R4 - picking a type emits and switches the chart` |
| AC4.4 | frontend component (page) | `010 R4 - selection kept after reload, falls back when gone` |
| AC5.1, AC5.2 | frontend unit (options) | `010 R5 - one point per run on a time axis with pace units` |
| AC5.3 | frontend unit | `010 R5 - point tooltip shows date and pace` |
| AC5.4 | frontend component | `010 R5 - single point still draws the chart` |
| AC5.5 | frontend component | `010 R5 - switching type replaces the points` |
| AC6.1 | frontend unit (options) | `010 R6 - treadmill points are triangles in treadmill colour` |
| AC6.2 | frontend unit (options) | `010 R6 - legend only lists series with points` |
| AC7.1, AC7.2 | frontend unit + component | `010 R7 - summary line shows thirty day change with tone` |
| AC7.3 | frontend unit | `010 R7 - not enough data text per missing window` |
| AC7.4 | frontend component | `010 R7 - summary follows the selected type` |
| AC8.1, AC8.2 | frontend unit + component | `010 R8 - runs without pace note` |
| AC9.1 | frontend component | `010 R9 - empty state when no workout types` |
| AC9.2 | frontend component | `010 R9 - no points state keeps selector and summary` |
| AC9.3 | frontend component (page) | existing `running-dashboard-page.load.spec.ts` tests stay green |
| AC9.4 | frontend component | `010 R9 - card heading` |
| AC10.1 | frontend component | `010 R10 - build calendar has no plan notes panel` |
| AC10.2 | backend route | `010 R10 - training plan response has no notes` (update TrainingPlanRoutesTest keys) |
| AC10.3 | backend unit | existing `004` pace-boundary test in TrainingPlanParserTest stays green; remove `004 R3 - sheet notes from column after NOTES` |
| AC10.4 | frontend unit | `010 R10 - plan shape check accepts body without notes` |

## Out of scope
- No change to "Heart rate by run type".
- No new workout types or detection rules. No change to how rep pace is computed (`RepPaceCalculator`, `TreadmillPaceParser`).
- No replacement for the Plan notes content.
- No other Build calendar changes (target paces, progress and calendar stay as they are).
- No new Strava calls, scopes or storage.

## Suggested follow-ups
- A least-squares trend line on the pace chart (like the HR card), to show direction at a glance.
- Fix the x-axis to the full 5-month window so sparse types don't look zoomed in (both cards).
- Let the HR card use a different marker shape for treadmill runs too, for consistency.
- Click a point to open the activity on Strava.

## Risks & decisions to confirm
- **Open question 1 (default type), decided:** the type with the most recent run is preselected (the first in the list, which keeps today's ordering rule and matches how the HR picker defaults to its first entry). Confirm, or choose "most runs".
- **Open question 2 (runs without a rep pace), decided:** they are left out of the chart and a muted line says how many are not shown (R8, a "Should"). They are not listed one by one. Confirm, or drop R8 to skip them silently.
- **Plan notes removed end-to-end:** besides hiding the panel, `notes` is dropped from `GET /api/training-plan` and its parsing is deleted (dead code, and privacy item 15). The NOTES column detection stays because the pace parser uses it as a boundary. The simpler alternative is a frontend-only removal that leaves the API untouched. Confirm which you prefer.
- **`runsWithoutPace` meaning changes** from "within the 60-day comparison span" to "within the 5-month window". No current UI reads it, so the risk is low.
- **Card title** changes from "Workout pace: last 30 days vs previous 30 days" to "Workout pace by type", to mirror "Heart rate by run type". Confirm the wording.
- **"Same rule as 008" for treadmill:** both use `TreadmillPaceParser.parsePaceLine` (the first line of `m:ss` tokens within 2:00–12:00, averaged). The 008 VO2max extractor additionally needs matching rep laps, but this card does not (rep pace only needs the pace line, as the existing pace card already does). Confirm this is acceptable.
- The y-axis is not inverted (lower = faster, stated in the axis name), consistent with the current pace card.
