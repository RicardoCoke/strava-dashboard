# Spec: Week vs last week headline tiles

**Feature ID:** 007 · **Status:** Approved · **Intent:** [intent.md](./intent.md) · **Date:** 2026-10-31

## Summary
The backend adds a `weekComparison` block to `GET /api/dashboard/running`. It compares this Monday–Sunday week (so far) with all of last week: km difference, mean easy-run HR and mean workout rep pace. The three headline tiles use it. The "This week" tile keeps its big km number and run count and its detail line becomes "last → this km · difference · runs", like the HR tile. The easy-run HR tile and the workout pace tile show this week vs last week instead of the 5-month and 30-day comparisons. Charts, sync and Strava usage do not change.

## Requirements
| ID | Requirement | Priority |
|---|---|---|
| R1 | The "This week" tile keeps its current value (`distanceKm` of the current week, one decimal), unit "km" and run-count detail | Must |
| R2 | Backend: `weekComparison` gives this week's and last week's Monday–Sunday date ranges and the km difference (this week so far minus all of last week) | Must |
| R3 | "This week" tile detail line: "last → this km · difference · N runs" (like the HR tile). The difference is green "+X.X km" when ahead, muted grey "−X.X km" when behind, muted grey "±0.0 km" when equal. Never red | Must |
| R4 | Backend: mean of per-run average HR over easy runs with HR, for each week, plus the change. Long runs and workouts are excluded | Must |
| R5 | Easy-run HR tile: shows the change in bpm and the detail "last → this bpm". Lower is green, higher is red-orange, 0.0 is neutral | Must |
| R6 | Backend: mean of per-workout rep pace over all workouts with a measured pace, for each week, plus the change and the workout type labels per week | Must |
| R7 | Workout pace tile: shows the change in s/km and the detail "last types → this types · last pace → this pace /km". Faster is green, slower is red-orange, same is neutral | Must |
| R8 | If either week has no qualifying run, the HR tile or pace tile shows "—" and a short note naming the week(s) missing | Must |
| R9 | Weeks are Monday–Sunday and use `start_date_local`, matching the weekly distance chart | Must |
| R10 | The three charts, the other response fields and the single page request are unchanged | Must |

## Acceptance criteria
Examples use today = Saturday 2026-10-31 (server zone): this week 2026-10-26..2026-11-01, last week 2026-10-19..2026-10-25. All data is invented.

### R1 — "This week" tile value unchanged
- **AC1.1** Given the current week entry in `weeklyDistance.weeks` has `distanceKm = 32.4` and `runCount = 4`, when the tiles render, then the "This week" tile shows value "32.4", unit "km", detail "4 runs". The value uses the same element, size and `tone-neutral` class as today.
- **AC1.2** Given any response, then `weekComparison.distance.thisWeekKm` equals the `distanceKm` of the `isCurrentWeek` entry, and `lastWeekKm` equals the `distanceKm` of the entry right before it.

### R2 — Week ranges and km difference (backend)
- **AC2.1** Given today = 2026-10-31, when the dashboard is built, then `weekComparison.thisWeek` is `{ "start": "2026-10-26", "end": "2026-11-01" }` and `lastWeek` is `{ "start": "2026-10-19", "end": "2026-10-25" }`.
- **AC2.2** Given runs this week totalling 32 400 m and last week totalling 29 000 m, then `distance` is `{ "thisWeekKm": 32.4, "lastWeekKm": 29.0, "differenceKm": 3.4 }`.
- **AC2.3** Given this week 20.0 km and last week 24.2 km, then `differenceKm = -4.2`.
- **AC2.4** Given this week and last week both 0 runs, then `differenceKm = 0.0`, never `-0.0`.
- **AC2.5** Given today = Monday 2026-10-26 with no run yet today and 30.0 km last week, then `thisWeekKm = 0.0` and `differenceKm = -30.0`. This week so far is compared with all of last week.
- **AC2.6** Given rounded values whose plain floating-point difference is not exact (e.g. 32.4 − 29.0), then `differenceKm` is `roundOneDecimal(thisWeekKm − lastWeekKm)`, so it always equals the difference of the two shown numbers.

### R3 — km comparison on the "This week" tile detail line (Revision 2)
- **AC3.1** Given `lastWeekKm = 29.0`, this week 32.4 km, 4 runs and `differenceKm = 3.4`, then the detail line reads "29.0 → 32.4 km · +3.4 km · 4 runs". The "+3.4 km" part has class `tone-better` (green, `--color-accent`); the rest is muted like the other tiles' detail lines.
- **AC3.2** Given `differenceKm = -4.2`, then the difference part shows "−4.2 km" (U+2212 minus) with class `tone-muted` (`--color-text-muted`). It never has `tone-worse`.
- **AC3.3** Given `differenceKm = 0` (or `-0`), then the difference part shows "±0.0 km" with class `tone-muted`.
- **AC3.4** Given any difference, then it is on the detail line at the detail line's font size (bold, weight 600), not in the headline. The headline holds only the value and unit, and the value "32.4" stays exactly as in AC1.1.
- **AC3.5** Given the "This week" tile is unavailable (no `isCurrentWeek` entry, existing 002 behaviour), then no comparison or difference is rendered.

### R4 — Easy-run HR per week (backend)
- **AC4.1** Given easy runs last week with average HR 140.0 and 142.0, and this week 138.4 and 139.4, then `easyHeartRate` is `{ "thisWeek": { "averageHeartRateBpm": 138.9, "runCount": 2 }, "lastWeek": { "averageHeartRateBpm": 141.0, "runCount": 2 }, "changeBpm": -2.1, "missingIn": null }`.
- **AC4.2** Given this week has one easy run of 5 km at 150 bpm and one of 15 km at 140 bpm, then `thisWeek.averageHeartRateBpm = 145.0`. Each run counts equally, with no weighting by distance or time.
- **AC4.3** Given this week also has a 22 km run with no workout pattern (a long run, feature 006) at 155 bpm and a "5x6min" workout at 165 bpm, then neither is included and `runCount` counts easy runs only.
- **AC4.4** Given an easy run this week with no average HR, then it is left out of the mean and of `runCount`.
- **AC4.5** Given treadmill easy runs (`trainer = true`) with HR, then they are included, the same as in the HR chart.
- **AC4.6** Given week means are rounded half-up to one decimal first, then `changeBpm = roundOneDecimal(thisWeek − lastWeek)` of the rounded means, and it is `0.0` (not `-0.0`) when they are equal.
- **AC4.7** Given no easy run with HR this week but some last week, then `thisWeek = null`, `changeBpm = null`, `missingIn = "thisWeek"`. Given the opposite, `lastWeek = null` and `missingIn = "lastWeek"`. Given neither, both are null and `missingIn = "bothWeeks"`.

### R5 — Easy-run HR tile
- **AC5.1** Given AC4.1's data, then the second tile has label "Easy-run HR", value "−2.1", unit "bpm", detail "141.0 → 138.9 bpm" (last week → this week) and tone `better`.
- **AC5.2** Given `changeBpm = 1.5` (last 139.0, this 140.5), then value "+1.5", detail "139.0 → 140.5 bpm", tone `worse` (red-orange).
- **AC5.3** Given `changeBpm = 0.0`, then value "0.0", tone `neutral`.
- **AC5.4** Given the HR card's type selector is switched, then the tile does not change.

### R6 — Workout pace per week (backend)
- **AC6.1** Given last week one "5x6min" workout with rep pace 253 s/km and this week one "4x8min" with 248 s/km, then `workoutPace` is `{ "thisWeek": { "averageRepPaceSecondsPerKm": 248, "runCount": 1, "typeLabels": ["4x8min"] }, "lastWeek": { "averageRepPaceSecondsPerKm": 253, "runCount": 1, "typeLabels": ["5x6min"] }, "changeSecondsPerKm": -5, "missingIn": null }`.
- **AC6.2** Given last week "5x6min" at 250 and "3x10min" at 257, then `lastWeek.averageRepPaceSecondsPerKm = 254` (mean 253.5, rounded half-up). Each workout counts equally, with no weighting by rep count or distance. `typeLabels = ["5x6min", "3x10min"]`, ordered by each type's first run in the week.
- **AC6.3** Given two "5x6min" workouts this week, then `typeLabels = ["5x6min"]` (each type once) and `runCount = 2`.
- **AC6.4** Given a treadmill workout whose description has a pace line (feature 001), then its rep pace comes from the description through `RepPaceCalculator`, the same as in the pace chart.
- **AC6.5** Given a workout with no measured pace (no description pace line on a treadmill, or an outdoor workout with no stream or lap result yet), then it is left out of the mean, `runCount` and `typeLabels`.
- **AC6.6** Given easy runs and long runs, then they are never included.
- **AC6.7** Given `changeSecondsPerKm = thisWeek − lastWeek` of the rounded integer means, then a negative value means faster.
- **AC6.8** Given no workout with a measured pace in one week or both, then the matching side is `null`, `changeSecondsPerKm = null` and `missingIn` is `"thisWeek"`, `"lastWeek"` or `"bothWeeks"`, as in AC4.7.

### R7 — Workout pace tile
- **AC7.1** Given AC6.1's data, then the third tile has label "Workout pace", value "5", unit "s/km faster", detail "5x6min → 4x8min · 4:13 → 4:08 /km" and tone `better`.
- **AC7.2** Given last week `typeLabels = ["5x6min", "3x10min"]` and this week `["4x8min"]`, then the detail starts with "5x6min, 3x10min → 4x8min · ".
- **AC7.3** Given `changeSecondsPerKm = 3`, then value "3", unit "s/km slower", tone `worse`.
- **AC7.4** Given `changeSecondsPerKm = 0`, then value "Same", unit "pace", tone `neutral` (the same wording as today).

### R8 — Not available
- **AC8.1** Given `easyHeartRate.missingIn = "thisWeek"`, then the HR tile shows value "—", no unit, tone `neutral` and detail "No easy run with heart rate this week yet".
- **AC8.2** Given `"lastWeek"`, then detail "No easy run with heart rate last week". Given `"bothWeeks"`, then detail "No easy run with heart rate this week or last week".
- **AC8.3** Given `workoutPace.missingIn = "thisWeek"`, then the pace tile shows "—" and detail "No workout with a measured pace this week yet". `"lastWeek"` → "No workout with a measured pace last week". `"bothWeeks"` → "No workout with a measured pace this week or last week".
- **AC8.4** Given one tile is unavailable, then the other tiles still show their values.

### R9 — Monday–Sunday weeks by local start date
- **AC9.1** Given a run with `start_date_local` Sunday 2026-10-25 23:30, then it counts in last week. Given a run at Monday 2026-10-26 00:10 local, then it counts in this week. This applies to km, HR and pace, whatever the UTC instant is.
- **AC9.2** Given today is Sunday 2026-11-01, then this week is still 2026-10-26..2026-11-01. Given today is Monday 2026-11-02, then this week is 2026-11-02..2026-11-08 and last week is 2026-10-26..2026-11-01.
- **AC9.3** Given any response, then `weekComparison.thisWeek.start` equals the `weekStart` of the `isCurrentWeek` entry in `weeklyDistance.weeks`.

### R10 — No change elsewhere
- **AC10.1** Given the same stored runs, then `weeklyDistance`, `heartRateTrend`, `workoutPaceComparison` and `sync` are the same JSON as before this feature (existing contract test extended, not rewritten).
- **AC10.2** Given the page loads, then exactly one request is made, `GET /api/dashboard/running`. The tiles add none.

## Traceability
| Success criterion (intent) | Covered by |
|---|---|
| S1 | R1 |
| S2 | R2, R3 |
| S3 | R2, R3 |
| S4 | R4, R5 |
| S5 | R5 |
| S6 | R4 (AC4.3) |
| S7 | R6, R7 |
| S8 | R7 |
| S9 | R4 (AC4.7), R6 (AC6.8), R8 |
| S10 | R2 (AC2.1), R9 |

## Design

### Data from Strava
- **No change.** No new endpoint, scope, field or call. The data comes from the local SQLite cache filled by the existing sync (`GET /athlete/activities` incremental with `after`, detail/streams for workouts, as in 001).
- Scopes: unchanged (`activity:read_all`, already granted).
- Fields used (already stored): `sport_type`, `start_date_local`, `distance` (m), `average_heartrate`, `trainer`, `name`, `description` (classification and treadmill pace), stored stream/lap rep-pace results.
- Errors: unchanged. 401 → existing token refresh, then `401 reauthRequired` → Angular redirects to Connect. 429 / outage → `200` with `sync.status` `rateLimited` / `stravaUnavailable`. The banner shows it, and the tiles show the cached data. Workouts whose stream is still pending have no pace yet and are left out (AC6.5). `sync.runsPendingDetail` already reports them in the banner.

### Domain model & logic
New file `domain/WeekComparison.kt` (pure, no Ktor/Exposed):

```kotlin
// Implemented with the existing DateWindow (start, end) instead of a new WeekRange type.
data class WeekRange(val start: LocalDate, val end: LocalDate)        // Monday..Sunday
data class DistanceDifference(val thisWeekKm: Double, val lastWeekKm: Double, val differenceKm: Double)
data class WeekHrStat(val averageHeartRateBpm: Double, val runCount: Int)
data class WeekPaceStat(val averageRepPace: SecondsPerKm, val runCount: Int, val types: List<RunType.Workout>)
enum class MissingIn { THIS_WEEK, LAST_WEEK, BOTH_WEEKS }
data class EasyHrComparison(val thisWeek: WeekHrStat?, val lastWeek: WeekHrStat?, val changeBpm: Double?, val missingIn: MissingIn?)
data class WorkoutPaceWeekComparison(val thisWeek: WeekPaceStat?, val lastWeek: WeekPaceStat?, val changeSecondsPerKm: Int?, val missingIn: MissingIn?)
data class WeekComparisonResult(val thisWeek: WeekRange, val lastWeek: WeekRange, val distance: DistanceDifference,
                                val easyHeartRate: EasyHrComparison, val workoutPace: WorkoutPaceWeekComparison)

object WeekComparison {
    fun compute(runs: List<RunActivity>, weeks: List<WeekTotal>, today: LocalDate): WeekComparisonResult
}
```

Rules:
- **Weeks:** `thisWeekStart = today.with(previousOrSame(MONDAY))`, `lastWeekStart = thisWeekStart − 7 days`, end = start + 6 days. `today` is the service's existing `LocalDate.now(clock.withZone(zone))`. A run belongs to the week containing `run.localDate` (from `start_date_local`). Extract `fun weekStartOf(date: LocalDate): LocalDate` (shared with `WeeklyDistance`, which then uses it too) so both use the same rule.
- **Distance:** taken from the `weeks` list (the last two entries of `WeeklyDistance.compute`, `WEEK_COUNT = 9`), so the numbers always match the chart. `differenceKm = roundOneDecimal(thisWeekKm − lastWeekKm)`, with `-0.0` normalised to `0.0`.
- **Easy HR:** runs where `isRun`, `RunClassifier.classify(run) == RunType.Easy` and `averageHeartRateBpm != null`. Per week: the unrounded mean of per-run averages, then `roundOneDecimal`. `changeBpm = roundOneDecimal(this − last)` of the rounded means, `-0.0` → `0.0`.
- **Workout pace:** runs where `isRun`, `classify(run) is RunType.Workout` and `RepPaceCalculator.repPace(run, type) != null`. Per week: `SecondsPerKm(roundHalfUpToInt(mean of per-workout paces))`. `types` are the distinct types ordered by their earliest `startLocal` in the week. `changeSecondsPerKm = this − last`.
- `missingIn`: null when both sides are present; otherwise as in AC4.7.
- Units: km with 1 decimal, bpm with 1 decimal, pace in whole s/km. Conversion happens only here and in existing helpers (`toKmOneDecimal`, `roundOneDecimal`, `roundHalfUpToInt`).
- `RunningDashboardService.get()` adds `weekComparison = WeekComparison.compute(stored, weeks, today)` to `DashboardResult.Ready` (compute `weeks` once and pass it in).

### Persistence
No change. No new tables, columns or migration.

### Backend API
`GET /api/dashboard/running`: auth, status codes and errors as today (`401 notConnected` / `reauthRequired`, `500` via StatusPages). The response gains one top-level field. Existing fields are unchanged.

```json
{
  "sync": { "...": "unchanged" },
  "weeklyDistance": { "...": "unchanged" },
  "heartRateTrend": { "...": "unchanged" },
  "workoutPaceComparison": { "...": "unchanged" },
  "weekComparison": {
    "thisWeek": { "start": "2026-10-26", "end": "2026-11-01" },
    "lastWeek": { "start": "2026-10-19", "end": "2026-10-25" },
    "distance": {
      "thisWeekKm": 32.4,
      "lastWeekKm": 29.0,
      "differenceKm": 3.4
    },
    "easyHeartRate": {
      "thisWeek": { "averageHeartRateBpm": 138.9, "runCount": 2 },
      "lastWeek": { "averageHeartRateBpm": 141.0, "runCount": 2 },
      "changeBpm": -2.1,
      "missingIn": null
    },
    "workoutPace": {
      "thisWeek": { "averageRepPaceSecondsPerKm": 248, "runCount": 1, "typeLabels": ["4x8min"] },
      "lastWeek": { "averageRepPaceSecondsPerKm": 253, "runCount": 1, "typeLabels": ["5x6min"] },
      "changeSecondsPerKm": -5,
      "missingIn": null
    }
  }
}
```

| Field | Type | Notes |
|---|---|---|
| `thisWeek`, `lastWeek` | `DateWindowDto` (existing) `{start, end}` | ISO local dates, Monday and Sunday |
| `distance.thisWeekKm`, `lastWeekKm`, `differenceKm` | number | km, 1 decimal; `differenceKm` this − last, never `-0.0` |
| `easyHeartRate.thisWeek` / `lastWeek` | `{ averageHeartRateBpm: number (bpm, 1 decimal), runCount: int }` or `null` | |
| `easyHeartRate.changeBpm` | number or `null` | this − last, 1 decimal; null if any side is null |
| `workoutPace.thisWeek` / `lastWeek` | `{ averageRepPaceSecondsPerKm: int, runCount: int, typeLabels: string[] }` or `null` | `typeLabels` are generated `NxMmin` labels (`RunType.Workout.label`), never activity names |
| `workoutPace.changeSecondsPerKm` | int or `null` | this − last; negative = faster |
| `missingIn` (both blocks) | `"thisWeek" \| "lastWeek" \| "bothWeeks" \| null` | null when both sides are present |

New DTOs in `api/RunningDashboardDtos.kt`: `WeekComparisonDto`, `DistanceDifferenceDto`, `EasyHrWeekComparisonDto`, `WeekHrStatDto`, `WorkoutPaceWeekComparisonDto`, `WeekPaceStatDto`. Mapping in `RunningDashboardMappers.kt`. Nulls are serialised explicitly (the existing config already does this for `trend`).

### Frontend (Angular)
- **Route / page:** unchanged (`features/running-dashboard`, `RunningDashboardPage`). No new component.
- **Interfaces** (`core/api/running-dashboard.models.ts`), matching the JSON:
```ts
export type WeekMissing = 'thisWeek' | 'lastWeek' | 'bothWeeks';
export interface DistanceDifference { thisWeekKm: number; lastWeekKm: number; differenceKm: number; }
export interface WeekHrStat { averageHeartRateBpm: number; runCount: number; }
export interface EasyHrWeekComparison { thisWeek: WeekHrStat | null; lastWeek: WeekHrStat | null; changeBpm: number | null; missingIn: WeekMissing | null; }
export interface WeekPaceStat { averageRepPaceSecondsPerKm: number; runCount: number; typeLabels: string[]; }
export interface WorkoutPaceWeekComparison { thisWeek: WeekPaceStat | null; lastWeek: WeekPaceStat | null; changeSecondsPerKm: number | null; missingIn: WeekMissing | null; }
export interface WeekComparison { thisWeek: DateWindow; lastWeek: DateWindow; distance: DistanceDifference; easyHeartRate: EasyHrWeekComparison; workoutPace: WorkoutPaceWeekComparison; }
// RunningDashboardResponse gains: weekComparison: WeekComparison;
```
- **`headline-tiles.model.ts`:**
  - `HeadlineTile` gains `delta: { text: string; tone: 'better' | 'muted' } | null` (null on the HR and pace tiles and on an unavailable "This week" tile).
  - `thisWeekTile` keeps its value, unit and detail from `weeklyDistance.weeks`. It adds `delta` from `weekComparison.distance.differenceKm`: `> 0` → `better`, otherwise `muted`.
  - `easyHrTile` and `workoutPaceTile` are rewritten to read only `weekComparison.easyHeartRate` / `.workoutPace`. They pick and format values and do no arithmetic. The label changes to "Easy-run HR". The id `easyHrTrend` stays to keep the change small.
  - `comparablePaceTypes` is no longer imported by the tiles. It stays in use by the pace chart.
- **Formatters** (pure, `shared/format/`):
  - new `distance.ts` → `formatKmDifference(km: number): string`: `3.4` → "+3.4 km", `-4.2` → "−4.2 km", `0`/`-0` → "±0.0 km".
  - reuse `formatBpmChange`, `formatPace`, `toneForChange`. Pace value and unit keep today's rule ("Same"/"pace", or `|Δ|` with "s/km faster|slower").
- **`headline-tiles.ts`:** *(Revision 2)* the headline holds only value and unit. The detail line renders `comparison`, then the coloured `delta`, then the run count, separated by " · ". Styles: `.delta { font-weight: 600; }`, `.tone-muted { color: var(--color-text-muted); }` (component-scoped). `tone-better` is the existing global class. `HeadlineTile` gains `comparison: string | null` ("29.0 → 32.4 km"). Plain interpolation only.
- **States:**

| View | Loading | Empty | Error | Normal |
|---|---|---|---|---|
| Dashboard page | unchanged: spinner + "Loading your runs…"; no tiles | unchanged: "No runs in the last 5 months"; no tiles | unchanged: "Couldn't load your dashboard." + Retry; no tiles | banner (if any) → 3 tiles → 3 cards |
| "This week" tile | not rendered | 0 runs this week → "0.0 km", detail "X.X → 0.0 km · −X.X km · 0 runs" or "0.0 → 0.0 km · ±0.0 km · 0 runs" (difference muted) | not rendered | value + unit; detail "last → this km · difference · runs" |
| Easy-run HR tile | not rendered | "—" + the R8 note | not rendered | signed bpm change, tone, "last → this bpm" |
| Workout pace tile | not rendered | "—" + the R8 note | not rendered | s/km change, tone, "types → types · pace → pace /km" |

- **Chart:** none changed.

### Files expected to change
Backend
- `backend/src/main/kotlin/app/stravadashboard/domain/WeekComparison.kt` (new)
- `backend/src/main/kotlin/app/stravadashboard/domain/WeeklyDistance.kt` (extract and use `weekStartOf`)
- `backend/src/main/kotlin/app/stravadashboard/domain/RunningDashboardService.kt` (`Ready.weekComparison`)
- `backend/src/main/kotlin/app/stravadashboard/api/RunningDashboardDtos.kt`, `RunningDashboardMappers.kt`
- `backend/src/test/kotlin/app/stravadashboard/domain/WeekComparisonTest.kt` (new)
- `backend/src/test/kotlin/app/stravadashboard/api/RunningDashboardContractTest.kt`, `RoutesTest.kt` if it builds `Ready`; `testsupport/TestData.kt` (helpers)

Frontend
- `frontend/src/app/core/api/running-dashboard.models.ts`
- `frontend/src/app/shared/format/distance.ts` + `distance.spec.ts` (new)
- `frontend/src/app/features/running-dashboard/headline-tiles/headline-tiles.model.ts`, `headline-tiles.ts`
- `frontend/src/app/features/running-dashboard/headline-tiles/headline-tiles.model.spec.ts` (002 R6/R7 tile tests replaced by 007 tests; 002 AC6.6 and AC7.5 "tile agrees with chart" no longer apply)
- `frontend/src/app/testing/dashboard-fixtures.ts` (default `weekComparison` + builders)
- `frontend/src/app/features/running-dashboard/dashboard-cards.spec.ts` / `running-dashboard-page.redesign.spec.ts` (fixture shape, delta rendering)

## Edge cases
| Case | Expected behaviour |
|---|---|
| No activities at all | Page-level empty state as today; no tiles. |
| Runs exist but none this week or last week | "This week": "0.0 km", "0 runs", delta "±0.0 km" (muted). HR and pace tiles: "—" with the "this week or last week" notes. |
| Monday morning, nothing run yet | Detail "X.X → 0.0 km · −X.X km · 0 runs" (difference muted). HR/pace tiles: "—" with "… this week yet". |
| Manual activity (no GPS) | Counts in km as today. In HR only if it is easy and has `average_heartrate` (usually not → left out). No pace (no streams/laps, not treadmill) → left out of pace. |
| Activity without heart rate | Left out of the easy-HR mean and `runCount`. Still counts in km. |
| Long run > 20 km (no workout pattern) | Counts in km. Left out of easy HR and pace (S6). |
| Workout without measured pace (stream pending, unavailable and no fast laps, treadmill without a pace line) | Left out of the mean and `typeLabels`. If none is left → "—" + pace note. |
| Same workout type twice in a week | Listed once in `typeLabels`. Both runs count in the mean. |
| Non-run activities (ride, swim) | Ignored everywhere (`isRun`). |
| Unit conversion | m → km 1 decimal (`toKmOneDecimal`); bpm 1 decimal; pace in s/km int → "m:ss" in the browser with `formatPace`. |
| Floating-point noise / −0 | Differences computed from rounded values then rounded again; `-0.0` normalised to `0.0`; formatters treat `-0` as 0. |
| Week spans DST change / time zones | Bucketing uses the local date of `start_date_local`, so DST does not matter. "Today" uses the server zone, the same as the weekly chart, so the tile weeks always match the highlighted bar (AC9.3). |
| Sync `partial` / `rateLimited` / `stravaUnavailable` | Banner as today; tiles from cached data. |
| Equal HR or pace | "0.0" bpm neutral / "Same pace" neutral. |

## Privacy
- **Data fields & sensitivity:** weekly km and run count (personal activity data); weekly mean easy-run heart rate (**health data, GDPR Art. 9**); weekly mean workout rep pace and generated workout type labels (personal activity data). All are derived from data already stored and already shown by the charts. No GPS, names, descriptions, activity ids or tokens are in the new block.
- **Storage & retention:** nothing new is stored. The values are computed per request from the existing SQLite cache. The existing disconnect/delete covers them.
- **Scopes:** unchanged (`activity:read_all`, required since 001 because private activities are included). No new scope.
- **Applicable privacy-check items:**

| # | Status | How |
|---|---|---|
| 1 | ✅ | No secrets. Fixtures use invented data. |
| 2 | ✅ | No token in the new DTO. |
| 3, 4 | n/a | Auth flow unchanged. |
| 5 | ✅ | No new scope. |
| 6 | ✅ | Nothing new persisted. |
| 7 | ✅ | No GPS used. |
| 8 | ✅ | No new logging. If any is added, it logs counts only, never HR values. |
| 9, 10 | ✅ | Unchanged; errors keep the `{error, message}` shape. |
| 11 | ✅ | Same session-checked route. |
| 12, 14 | n/a | No cookie or state-changing endpoint change. |
| 13 | ✅ | Tile text uses plain interpolation. `typeLabels` are backend-generated `NxMmin` labels, not user text. No `[innerHTML]`. |
| 15 | ✅ | The block carries only aggregated numbers, dates and generated labels the tiles show. |
| 16, 17 | ✅ | No third parties. |
| 18–20 | ✅ | Unchanged; nothing new stored. |
| 21 | ✅ | Invented HR/pace/dates in all fixtures. |

## Test plan
| AC | Test type | Test name |
|---|---|---|
| AC1.1 | frontend unit (`buildHeadlineTiles`) | `007 R1 - this week tile keeps km value unit and run count` |
| AC1.2, AC9.3 | backend unit | `R1 - week comparison distance matches weekly distance entries` |
| AC2.1, AC9.2 | backend unit | `R2 - this and last week are Monday to Sunday around today` |
| AC2.2, AC2.3, AC2.6 | backend unit | `R2 - km difference is this week minus last week from rounded values` |
| AC2.4 | backend unit | `R2 - km difference is zero not negative zero` |
| AC2.5 | backend unit | `R2 - monday compares nothing so far with all of last week` |
| AC3.1–AC3.3 | frontend unit (`formatKmDifference` + model) | `007 R3 - ahead shows green plus km`, `007 R3 - behind shows muted minus km`, `007 R3 - this week tile shows last week to this week km like the hr tile`, `007 R3 - equal shows plus-minus zero muted` |
| AC3.4, AC3.5 | frontend component | `007 R3 - km comparison sits on the detail line like the hr tile`, `007 R3 - fewer km renders a muted delta`, `007 R3 - no delta when this week unavailable` |
| AC4.1, AC4.2, AC4.6 | backend unit | `R4 - easy hr is unweighted mean per week with change from rounded means` |
| AC4.3 | backend unit | `R4 - long runs and workouts are excluded from easy hr` |
| AC4.4, AC4.5 | backend unit | `R4 - runs without hr left out and treadmill easy runs included` |
| AC4.7 | backend unit | `R4 - missingIn reports which week has no easy run with hr` |
| AC5.1–AC5.3 | frontend unit | `007 R5 - hr tile shows signed change detail and tone` |
| AC5.4 | frontend component | `007 R5 - hr selector does not change tile` |
| AC6.1, AC6.2, AC6.7 | backend unit | `R6 - workout pace is unweighted mean across types with change` |
| AC6.3 | backend unit | `R6 - repeated type listed once` |
| AC6.4 | backend unit | `R6 - treadmill workout uses description pace` |
| AC6.5, AC6.6 | backend unit | `R6 - unpaced workouts and non-workouts are excluded` |
| AC6.8 | backend unit | `R6 - missingIn reports which week has no paced workout` |
| AC7.1–AC7.4 | frontend unit | `007 R7 - pace tile shows change types and paces`, `007 R7 - slower and same wording` |
| AC8.1–AC8.4 | frontend unit | `007 R8 - unavailable notes for this week last week and both` |
| AC9.1 | backend unit | `R9 - runs bucket by local start date across midnight` |
| AC10.1 | backend route (`testApplication`) | `R2 - dashboard JSON matches the full contract including nested objects and nulls` (extended with `weekComparison`, including null sides) |
| AC10.2 | frontend component (`HttpTestingController`) | `007 R10 - tiles make no extra request` |

## Out of scope
- No change to the three charts or what they compute.
- No new tiles and no other comparison periods (e.g. 4-week average).
- No pace matching per workout type in the tile; it averages across all workout types.
- No red for fewer km.
- No weekly goals or targets.
- No editing of Strava data.

## Suggested follow-ups
- Compare this week so far against last week up to the same weekday, so Monday isn't always grey.
- A tooltip on the tiles listing the runs behind each weekly mean.
- Show how many workouts were left out of the pace mean because their pace isn't measured yet.

## Risks & decisions to confirm
1. **Computed in the backend, not the tiles.** The weekly means need per-run data that is not in the response (rep pace per workout) or would mean arithmetic in Angular (code-standards A6). So this adds a `weekComparison` block to the existing response. 002 kept the tiles as "select only". That is still true: the tiles only select and format.
2. **Note wording is not symmetric (from the intent):** ahead → "+3.4 km" (no "vs last week"); behind → "−4.2 km vs last week"; equal → "±0.0 km", shown muted grey. *(Superseded by Revision 2: behind is now "−4.2 km", since the detail line already shows last → this.)* Confirm "±0.0 km" should be grey and not green.
3. **HR tile label** changes from "Easy-run HR trend" to "Easy-run HR", since it is no longer a 5-month trend. The pace tile keeps "Workout pace".
4. **Pace detail lists only workouts with a measured pace.** A workout still waiting for its stream is left out of both the mean and the type list. If that leaves a week empty, the tile shows "—" until the next sync.
5. **"Not available" note texts** (AC8.1–AC8.3) are new wording. Confirm or adjust.
6. **Type order in the detail** is chronological within each week (first run of each type).
7. **Superseded 002 behaviour:** 002 AC6.x and AC7.x (5-month HR trend and 30-day pace in the tiles, including "tile agrees with chart" AC6.6/AC7.5) are replaced. Their tests are rewritten as 007 tests.

## Revision 2 (2026-10-31)
After the first build, the user asked for the km comparison to sit on the detail line like the easy-run HR tile instead of beside the number. Chosen layout: "29.0 → 32.4 km · +3.4 km · 4 runs", with the difference keeping its green/grey colour. Because "last → this" is now shown, the behind wording drops " vs last week" ("−4.2 km"). R3 and its ACs above are updated to match. No backend change.
