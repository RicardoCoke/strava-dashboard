# Spec: Easy-run efficiency factor

**Feature ID:** 014 · **Status:** Approved · **Intent:** [intent.md](./intent.md) · **Date:** 2026-10-31

## Summary
A new read-only endpoint, `GET /api/efficiency-factor`, works only from the SQLite cache. For every qualifying easy or long run in the last 6 months it computes an efficiency factor (EF): average speed in metres per minute divided by average heart rate. "Qualifying" means outdoor, with average HR, and at least 20 minutes of moving time. It also returns a 4-week rolling average at each run, the current value and the percentage change since the start of the window. A self-contained "Efficiency factor" card sits directly after the "Heart rate by run type" card. It has a headline tile (current EF and change) and a chart in the "Workout pace by type" style: one point per run, with easy and long runs in different colours, plus one rolling-average line.

## Requirements
| ID | Requirement | Priority (Must/Should/Could) |
|---|---|---|
| R1 | Qualifying runs: `RunType.Easy` or `RunType.LongRun`, not treadmill, with average HR, moving time of at least 20 min and distance > 0, dated in the last 6 months | Must |
| R2 | Per-run EF = (distance m ÷ moving time min) ÷ average HR, in m/min/beat | Must |
| R3 | 4-week rolling average at each plotted run: the mean EF of qualifying runs in the 28 days ending on that run's date, null when there are fewer than 3 | Must |
| R4 | Current EF = the rolling value at the latest plotted run, but only if that run is in the last 28 days | Must |
| R5 | Change since start of window = % change from the first non-null rolling value to the current value. Shown only when the two are at least 28 days apart | Must |
| R6 | Runs that may qualify but have no moving time stored yet are counted as `runsPendingDetail` | Should |
| R7 | `GET /api/efficiency-factor` returns the exact JSON below from the cache only. It needs a connected session and never calls Strava | Must |
| R8 | Placement: the card comes directly after "Heart rate by run type" on the running dashboard. It makes its own single request, has its own states and reloads quietly after each sync | Must |
| R9 | Chart: one point per run on a time axis, easy and long runs in different colours, a 4-week rolling-average line, the y axis in m/min/beat, and a per-run tooltip | Must |
| R10 | Headline tile: current EF (2 decimals, "m/min/beat") and change ("+4.2% since 3 May") with tone. A short note replaces each figure when there is too little data | Must |
| R11 | Loading, error, empty (with a still-importing variant) and normal states | Must |
| R12 | No regression: existing endpoints, JSON and cards are unchanged, with no new OAuth scope and no new Strava call | Must |

## Acceptance criteria

**Conventions.** `today = LocalDate.now(clock.withZone(zone))` (server zone, as in 001, 005 and 008). A run's date is `RunActivity.localDate` (the local date of `start_date_local`). "Run" means a stored activity with `isRun == true` (private runs included). `type = RunClassifier.classify(run)` (unchanged: a workout pattern wins; otherwise a non-workout run over 20 000 m is `LongRun`, else `Easy`). Treadmill means `run.trainer == true`, as everywhere else. `windowStart = today.minusMonths(6)`, `windowEnd = today`. Constants: `WINDOW_MONTHS = 6L`, `ROLLING_DAYS = 28L`, `MIN_RUNS_FOR_ROLLING = 3`, `MIN_MOVING_SECONDS = 1200`. Examples use `today = 2026-10-31`, so `windowStart = 2026-04-30`. All fixture numbers are invented.

### R1 — Qualifying runs
A run *qualifies* when all of these hold: `isRun`; `type` is `Easy` or `LongRun`; `!trainer`; `averageHeartRateBpm != null && > 0`; `movingTime != null && movingTime.value >= 1200`; `distance.value > 0`; `localDate <= today`.
- **AC1.1** Given an easy run (8 km) and a long run (24 km) on 2026-10-20, both outdoor, with HR and 40+ min moving time, when the EF is computed, then both appear in `points`, with `runType` `"easy"` and `"long"`.
- **AC1.2** Given a `5x6min` workout run with HR and moving time, then it does not appear in `points`.
- **AC1.3** Given an otherwise qualifying run with `trainer = true`, then it does not appear in `points` and is not counted in `runsPendingDetail`.
- **AC1.4** Given an otherwise qualifying run with `averageHeartRateBpm = null`, then it does not appear in `points` and is not counted in `runsPendingDetail`.
- **AC1.5** Given otherwise qualifying runs with `movingTime` of 1199 s and 1200 s, then only the 1200 s run appears.
- **AC1.6** Given an otherwise qualifying run with `distance = 0` (e.g. a manual entry) or `movingTime = 0`, then it does not appear and nothing divides by zero.
- **AC1.7** Given qualifying runs dated 2026-04-29, 2026-04-30 and 2026-10-31, then `points` holds only the 2026-04-30 and 2026-10-31 runs. The 2026-04-29 run may still count towards a rolling value (R3).
- **AC1.8** Given a run whose `start_date_local` is 2026-05-01T23:30 while its UTC start is 2026-05-02, then its point date is `"2026-05-01"`.
- **AC1.9** Given two qualifying runs on the same date, then both appear as separate points, ordered by `startLocal`.

### R2 — Per-run efficiency factor
`EF = (distanceMeters / (movingTimeSeconds / 60.0)) / averageHeartRateBpm`. The response rounds it half-up to 3 decimals. All further maths (R3–R5) uses the unrounded values.
- **AC2.1** Given 10 000 m in 3000 s (200 m/min) at 125.0 bpm, then `efficiency = 1.6`.
- **AC2.2** Given 12 345 m in 3725 s at 141.3 bpm, then `efficiency = 1.407` (198.85… m/min ÷ 141.3 = 1.4073…).
- **AC2.3** Given a point, then it also carries `distanceKm` (1 decimal, half-up), `movingTimeSeconds`, `paceSecondsPerKm` (`paceOf(movingTime, distance)`, whole seconds) and `averageHeartRateBpm` (1 decimal). For 10 000 m in 3000 s that is `distanceKm = 10.0`, `paceSecondsPerKm = 300`.

### R3 — 4-week rolling average
For each point dated `d`, the rolling set is every qualifying run (R1 rules) with `localDate` in `d − 27 .. d` and `startLocal <=` this run's `startLocal`. This may include qualifying runs from before `windowStart`, which are not plotted. `rollingEfficiency` = the unweighted mean of their EF, rounded to 3 decimals, or `null` when the set has fewer than 3 runs.
- **AC3.1** Given qualifying runs with EF 1.50 (2026-10-01), 1.60 (2026-10-10) and 1.70 (2026-10-20), then the 2026-10-20 point has `rollingEfficiency = 1.6`, and the 2026-10-01 and 2026-10-10 points have `null` (assuming no earlier runs).
- **AC3.2** Given qualifying runs on 2026-09-20 (EF 1.40), 2026-10-10 (1.60), 2026-10-15 (1.60) and 2026-10-18 (1.70), then the 2026-10-18 point's set is 2026-09-21..2026-10-18. That excludes 09-20, leaving 3 runs, so `rollingEfficiency = 1.633`.
- **AC3.3** Given qualifying runs on 2026-04-10, 2026-04-20 and 2026-04-29 and one on 2026-05-01, then the 2026-05-01 point (the first plotted) has a non-null `rollingEfficiency` that includes the three April runs.
- **AC3.4** Given a non-qualifying run (treadmill, no HR, under 20 min, workout) inside a 28-day span, then it never counts towards a rolling value.
- **AC3.5** Given two runs on the same day, then the earlier one's rolling set does not include the later one, and the later one's set does include the earlier one.

### R4 — Current EF
`current` is built from the last point in `points` (latest `startLocal`). It is `null` when there are no points, when that point's date is before `today − 27`, or when that point's `rollingEfficiency` is null.
- **AC4.1** Given the last point is dated 2026-10-29 with `rollingEfficiency = 1.652` from 6 runs, then `current = { "efficiency": 1.652, "date": "2026-10-29", "runCount": 6 }`.
- **AC4.2** Given the last point is dated 2026-10-03 (= today − 28), then `current = null`.
- **AC4.3** Given the last point is dated 2026-10-04 (= today − 27) and has a non-null rolling value, then `current` is not null.
- **AC4.4** Given the last point has `rollingEfficiency = null` (fewer than 3 runs in its 28 days), then `current = null`.

### R5 — Change since start of window
`from` = the first point (in date order) whose `rollingEfficiency` is not null. `to` = `current`. `change` is not null only when both exist and `to.date − from.date >= 28` days. `percent = (toUnrounded − fromUnrounded) / fromUnrounded × 100`, rounded half-up to 1 decimal.
- **AC5.1** Given `from` = 2026-05-03 with rolling 1.585 (unrounded 1.5850) and `current` = 2026-10-29 with rolling 1.652 (unrounded 1.6520), then `change = { "percent": 4.2, "fromDate": "2026-05-03", "fromEfficiency": 1.585, "toDate": "2026-10-29", "toEfficiency": 1.652 }`.
- **AC5.2** Given `current = null`, then `change = null`.
- **AC5.3** Given `from.date` = 2026-10-05 and `current.date` = 2026-10-29 (24 days apart), then `change = null`.
- **AC5.4** Given exactly 28 days between them, then `change` is not null.
- **AC5.5** Given a falling EF (from 1.700 to 1.615), then `percent = -5.0`.

### R6 — Runs pending detail
`runsPendingDetail` counts runs with `isRun`, `localDate` in `windowStart..today`, `type` in {Easy, LongRun}, `!trainer`, `averageHeartRateBpm != null` and `movingTime == null`. The existing sync already fetches details for these runs (008 `needsLapHeartRate` / `detailFetchedAt == null`). No sync change.
- **AC6.1** Given two easy outdoor runs with HR in the window, one with `detailFetchedAt == null` and one with an old detail and `movingTime == null`, then `runsPendingDetail = 2`, and neither appears in `points`.
- **AC6.2** Given a treadmill run, a run without HR, or a workout run with `movingTime == null`, then it is not counted.

### R7 — Endpoint
- **AC7.1** Given a connected session and stored runs, when `GET /api/efficiency-factor` is called, then the response is 200 with exactly the JSON shape in *Backend API* (field names, types and nullability).
- **AC7.2** Given no session cookie, or a session but no stored token, then the response is 401 `{"error":"not_connected","message":"Connect your Strava account."}`.
- **AC7.3** Given the request is served, then no Strava HTTP call is made (Ktor `MockEngine` records zero requests).
- **AC7.4** Given no qualifying runs, then the response is 200 with `points: []`, `current: null`, `change: null`.
- **AC7.5** Given the response, then it contains no activity names, descriptions, ids, coordinates or tokens.

### R8 — Placement and loading
- **AC8.1** Given the dashboard has loaded with runs, then `app-efficiency-factor-section` is the next sibling element after `app-long-run-drift-section` inside `.cards`, which itself directly follows `app-heart-rate-trend-chart`. (Amended at build time: feature 013 merged first and put its drift card directly after the HR card, so the EF card follows it to keep the HR cards grouped without breaking 013's placement.)
- **AC8.2** Given the card is created, then it makes exactly one `GET /api/efficiency-factor` request. Its loading, failure or empty state does not affect the other cards.
- **AC8.3** Given `SyncStore.dataVersion` changes after a finished sync, then the card reloads quietly: it keeps its current content while loading and if the reload fails.

### R9 — Chart
- **AC9.1** Given points of both types, then the options have a silent `line` series named `4-week average` and two `scatter` series named `Easy` (colour `palette.accent`) and `Long run` (colour `palette.longRun`). The legend lists the series that have data.
- **AC9.2** Given only easy points, then there is no `Long run` scatter series and the legend has no `Long run` entry (and the same for only long points).
- **AC9.3** Given points whose `rollingEfficiency` is null, then the line data has `null` at those points (a gap, `connectNulls: false`), and the scatter still shows them.
- **AC9.4** Given the chart, then the x axis is `type: 'time'`, and the y axis is `type: 'value'`, `scale: true`, named `EF (m/min per bpm), higher is better`, with labels to 2 decimals.
- **AC9.5** Given a hover on a scatter point, then the tooltip reads, e.g., `29 Oct 2026 · Long run · EF 1.62 · 22.4 km · 5:31 /km · 142 bpm · 4-week avg 1.65`. The `· 4-week avg …` part is left out when `rollingEfficiency` is null. The text contains only dates and numbers (no Strava free text).

### R10 — Headline tile
- **AC10.1** Given `current.efficiency = 1.652`, then the tile shows `1.65` with the unit `m/min/beat` and the caption `4-week average of 6 runs to 29 Oct`.
- **AC10.2** Given `change.percent = 4.2` and `fromDate = "2026-05-03"`, then the tile shows `+4.2% since 3 May` with tone `better`. Given `-5.0`, it shows `-5.0% since 3 May` with tone `worse`. Given `0.0`, it shows `0.0% since 3 May` with tone `neutral`. (Higher EF is better, so tone = `toneForChange(-percent)`.)
- **AC10.3** Given `current = null` and points exist, then the tile shows the note `Not enough recent runs for a current value (needs 3 in the last 4 weeks)` instead of a number, and no change text.
- **AC10.4** Given `current` is set but `change = null`, then the tile shows the current value and the note `Not enough data yet for a change since the start of the window` instead of a percentage.
- **AC10.5** Given the normal state, then a footnote reads `EF = average speed (m/min) ÷ average heart rate. Easy and long outdoor runs of 20 min or more with heart rate; treadmill runs are left out.` When `runsPendingDetail > 0`, it adds `N runs still importing moving time; numbers will update.` (with `1 run` for N = 1).

### R11 — States
- **AC11.1 Loading:** before the response arrives, the card shows its heading and `app-ring-spinner` with `Loading efficiency factor…` (`role="status"`).
- **AC11.2 Error:** given a non-401 failure, or a 200 body that fails `isEfficiencyFactorResponse`, then the card shows `Couldn't load the efficiency factor.` and a `Retry` button (`role="alert"`). Retry makes a new request. A 401 leaves the card unchanged (the interceptor redirects to `/connect`).
- **AC11.3 Empty:** given `points: []` and `runsPendingDetail = 0`, then the card shows `No easy or long runs with heart rate in the last 6 months`. Given `points: []` and `runsPendingDetail > 0`, it shows `Moving time is still importing for N runs. The chart will appear when it's done.` Neither shows a tile or chart.
- **AC11.4 Normal:** given at least one point, then the card shows the heading `Efficiency factor`, the tile (R10), the chart (R9) and the footnote.

### R12 — No regression
- **AC12.1** Given the existing backend contract tests and the frontend page specs, then they pass unchanged in behaviour. Page specs only gain a stub for the new section, as they already have for the VO2max section.
- **AC12.2** Given the OAuth scopes and the Strava client, then they are unchanged. No new Strava endpoint and no sync-policy change.

## Traceability
| Success criterion (intent) | Covered by |
|---|---|
| S1 one point per qualifying run over 6 months + 4-week rolling line, 010 style | R1, R2, R3, R9 |
| S2 headline tile with current EF and % change since window start | R4, R5, R10 |
| S3 easy + long included; treadmill, no-HR, < 20 min excluded | R1 |
| S4 value in m/min/beat | R2, R9, R10 |
| S5 card next to "Heart rate by run type" | R8 |
| S6 short note instead of a misleading number with too little data | R3, R4, R5, R10, R11 |
| Non-goal: no Strava calls from read endpoint | R7, R12 |

## Design

### Data from Strava
- Endpoints: **none new.** The data already comes from the existing sync: `GET /athlete/activities` (summary: `distance`, `average_heartrate`, `trainer`, `sport_type`, `start_date_local`) and `GET /activities/{id}` (detail: `moving_time`, stored since 008).
- Scopes: unchanged (whatever 001 requests). Nothing new.
- Fields used: `distance` (m), `moving_time` (s), `average_heartrate` (bpm), `trainer`, `sport_type`, `start_date_local`, plus `name`/`description` only through `RunClassifier` (never returned).
- Sync & caching: unchanged. The endpoint reads `RunRepository.findAll()` on each request. The cache keeps 6 months + 41 days (`SyncPolicy.historyStart`), so the 27-day rolling lookback before `windowStart` is covered.
- Errors: the endpoint makes no Strava call, so 401, 429 and network failures from Strava cannot happen here. They stay with the existing sync (009 banner). A missing token gives 401 `not_connected`.

### Domain model & logic
New package `app.stravadashboard.domain.efficiency` (pure, no Ktor or Exposed):
```kotlin
enum class EfRunType { EASY, LONG }

data class EfPoint(
    val date: LocalDate,
    val startLocal: LocalDateTime,
    val runType: EfRunType,
    val efficiency: Double,          // unrounded m/min/beat
    val rollingEfficiency: Double?,  // unrounded, null when < MIN_RUNS_FOR_ROLLING
    val rollingRunCount: Int,
    val distance: Meters,
    val movingTime: Seconds,
    val pace: SecondsPerKm,
    val averageHeartRateBpm: Double,
)
data class EfCurrent(val efficiency: Double, val date: LocalDate, val runCount: Int)
data class EfChange(val percent: Double, val from: EfCurrent, val to: EfCurrent)
data class EfficiencyFactorReport(
    val windowStart: LocalDate, val windowEnd: LocalDate,
    val current: EfCurrent?, val change: EfChange?,
    val points: List<EfPoint>, val runsPendingDetail: Int,
)

object EfficiencyFactorCalculator {
    const val WINDOW_MONTHS = 6L
    const val ROLLING_DAYS = 28L
    const val MIN_RUNS_FOR_ROLLING = 3
    const val MIN_MOVING_SECONDS = 1200
    fun compute(runs: List<RunActivity>, today: LocalDate): EfficiencyFactorReport
}

sealed interface EfficiencyFactorResult {
    data object NotConnected : EfficiencyFactorResult
    data class Ready(val report: EfficiencyFactorReport) : EfficiencyFactorResult
}
class EfficiencyFactorService(tokens: TokenRepository, runs: RunRepository, clock: Clock, zone: ZoneId) {
    suspend fun get(): EfficiencyFactorResult   // NotConnected when tokens.find() == null
}
```
- Units: EF in m/min/beat as a raw `Double` that stays inside `domain`. Rounding to 3 decimals happens in the API mapper (half-up via `BigDecimal`). `percent` is computed in `domain` and rounded to 1 decimal in the mapper. Pace uses the existing `paceOf`, distance the existing `toKmOneDecimal`.
- Time zones: day bucketing and the 28-day window use `localDate` and `startLocal` only. `today` comes from the injected `Clock` + `zone`.
- Change percent sign: positive means EF went up (improvement).

### Persistence
No schema change. Uses existing `runs` columns: `distance_m`, `moving_time_s`, `average_heartrate`, `trainer`, `start_date_local`, `name`, `description`, `sport_type`.

### Backend API
- `GET /api/efficiency-factor`: no query params. Needs a connected session (`hasConnectedSession(tokens)`).
- Response 200 (JSON, exact):
```json
{
  "windowStart": "2026-04-30",
  "windowEnd": "2026-10-31",
  "rollingDays": 28,
  "minRunsForRolling": 3,
  "current": { "efficiency": 1.652, "date": "2026-10-29", "runCount": 6 },
  "change": {
    "percent": 4.2,
    "fromDate": "2026-05-03",
    "fromEfficiency": 1.585,
    "toDate": "2026-10-29",
    "toEfficiency": 1.652
  },
  "points": [
    {
      "date": "2026-05-03",
      "runType": "easy",
      "efficiency": 1.598,
      "rollingEfficiency": 1.585,
      "distanceKm": 10.2,
      "movingTimeSeconds": 3310,
      "paceSecondsPerKm": 325,
      "averageHeartRateBpm": 139.4
    }
  ],
  "runsPendingDetail": 0
}
```
| Field | Type | Notes |
|---|---|---|
| `windowStart`, `windowEnd` | ISO date string | `today − 6 months`, `today` |
| `rollingDays` | int | always 28 |
| `minRunsForRolling` | int | always 3 |
| `current` | object \| null | R4. `efficiency` 3 decimals, m/min/beat. `runCount` = runs in the rolling set |
| `change` | object \| null | R5. `percent` 1 decimal (positive = EF improved). `fromEfficiency`/`toEfficiency` 3 decimals |
| `points` | array | oldest first (by `startLocal`). Empty when none |
| `points[].runType` | `"easy"` \| `"long"` | same keys as 001's `typeKey` |
| `points[].efficiency` | number | 3 decimals, m/min/beat |
| `points[].rollingEfficiency` | number \| null | 3 decimals |
| `points[].distanceKm` | number | 1 decimal |
| `points[].movingTimeSeconds` | int | seconds |
| `points[].paceSecondsPerKm` | int | whole seconds per km |
| `points[].averageHeartRateBpm` | number | 1 decimal |
| `runsPendingDetail` | int | R6 |

- Errors: 401 `{"error":"not_connected","message":"Connect your Strava account."}` (no session or no token). 500 `internal_error` comes from the existing `StatusPages`. There is no 422 (no config).

### Frontend (Angular)
- Route / page: no new route. The card is rendered inside the existing running dashboard page (`/`) in `.cards`, directly after `<app-heart-rate-trend-chart>`.
- Components (new folder `features/efficiency-factor/`):
  - `EfficiencyFactorSection` (`app-efficiency-factor-section`). Container: injects `EfficiencyFactorService`, holds a `signal<EfficiencyFactorState>`, and quietly reloads on `SyncStore.dataVersion` (optional inject, same pattern as `Vo2maxSection`). Renders the tile, chart and footnote.
  - `EfficiencyFactorChart` (`app-efficiency-factor-chart`). Presentational: `input.required<EfPoint[]>()` (the points only), renders the shared `ChartComponent` with `buildEfficiencyFactorOptions(points)`.
  - `efficiency-factor.options.ts`: pure `buildEfficiencyFactorOptions(points): EChartsOption` and `efPointTooltip(point): string`.
  - `efficiency-factor.view.ts`: pure helpers `efStateFromError`, `currentCaption`, `changeText`, `changeTone`, `footnoteText`, `emptyText`. Formatting reuses `shared/format` (`formatTwoDecimals`, `formatSignedOneDecimal`, `formatDayMonth`, `formatDayMonthYear`, `formatPace`, `toneForChange`).
- Service & TS interfaces: `core/api/efficiency-factor.service.ts` (`getEfficiencyFactor(): Observable<EfficiencyFactorResponse>` → `GET /api/efficiency-factor`) and `core/api/efficiency-factor.models.ts`:
```ts
export interface EfCurrent { efficiency: number; date: string; runCount: number; }
export interface EfChange {
  percent: number; fromDate: string; fromEfficiency: number; toDate: string; toEfficiency: number;
}
export interface EfPoint {
  date: string; runType: 'easy' | 'long'; efficiency: number; rollingEfficiency: number | null;
  distanceKm: number; movingTimeSeconds: number; paceSecondsPerKm: number; averageHeartRateBpm: number;
}
export interface EfficiencyFactorResponse {
  windowStart: string; windowEnd: string; rollingDays: number; minRunsForRolling: number;
  current: EfCurrent | null; change: EfChange | null; points: EfPoint[]; runsPendingDetail: number;
}
export function isEfficiencyFactorResponse(value: unknown): value is EfficiencyFactorResponse;
```
- States: **loading** (spinner + "Loading efficiency factor…") · **error** ("Couldn't load the efficiency factor." + Retry) · **empty** ("No easy or long runs with heart rate in the last 6 months", or the still-importing text) · **normal** (tile + chart + footnote, with notes in place of numbers where R10 says so).
- Chart: ECharts. x = time (run date). y = EF in m/min per bpm (2-decimal labels, `scale: true`). Series, in order: silent `line` "4-week average" (`palette.text`, width 2, no symbols, `connectNulls: false`), then `scatter` "Easy" (circle, size 7, `palette.accent`) and `scatter` "Long run" (circle, size 8, `palette.longRun`). Grid, legend and tooltip styles match `workout-pace.options.ts` (`baseChartStyle`, `categoryAxisStyle`, `valueAxisStyle`, `tooltipStyle`). The tooltip is item-triggered and built from numbers and dates only (AC9.5).

### Files expected to change
New (backend):
- `backend/src/main/kotlin/app/stravadashboard/domain/efficiency/EfficiencyFactorCalculator.kt` (models + calculator)
- `backend/src/main/kotlin/app/stravadashboard/domain/efficiency/EfficiencyFactorService.kt`
- `backend/src/main/kotlin/app/stravadashboard/api/EfficiencyFactorDtos.kt`
- `backend/src/main/kotlin/app/stravadashboard/api/EfficiencyFactorMappers.kt`
- `backend/src/main/kotlin/app/stravadashboard/api/EfficiencyFactorRoutes.kt`
- `backend/src/test/kotlin/app/stravadashboard/domain/efficiency/EfficiencyFactorCalculatorTest.kt`
- `backend/src/test/kotlin/app/stravadashboard/api/EfficiencyFactorRoutesTest.kt`

Edited (backend, shared, keep minimal):
- `backend/src/main/kotlin/app/stravadashboard/Application.kt`: construct `EfficiencyFactorService` and register `efficiencyFactorRoutes(tokens, service)` (two lines plus imports).

New (frontend):
- `frontend/src/app/core/api/efficiency-factor.models.ts`, `efficiency-factor.service.ts`, `efficiency-factor.service.spec.ts`
- `frontend/src/app/features/efficiency-factor/efficiency-factor-section.ts|.html|.scss|.spec.ts`
- `frontend/src/app/features/efficiency-factor/efficiency-factor-chart/efficiency-factor-chart.ts`
- `frontend/src/app/features/efficiency-factor/efficiency-factor.options.ts|.spec.ts`
- `frontend/src/app/features/efficiency-factor/efficiency-factor.view.ts|.spec.ts`
- `frontend/src/app/testing/efficiency-factor-fixtures.ts`, `frontend/src/app/testing/efficiency-factor-stub.ts`

Edited (frontend, shared, keep minimal):
- `frontend/src/app/features/running-dashboard/running-dashboard-page.ts`: one import in `imports`.
- `frontend/src/app/features/running-dashboard/running-dashboard-page.html`: one `<app-efficiency-factor-section />` line after the HR card.
- `frontend/src/app/shared/chart/palette.ts`: add one key `longRun: '#f5b942'` (amber), so colours stay in the shared palette.
- `frontend/src/app/features/running-dashboard/running-dashboard-page*.spec.ts` (existing page specs): call `stubEfficiencyFactorSection()` next to `stubVo2maxSection()`, so their `HttpTestingController.verify()` doesn't see an unexpected request.

## Edge cases
| Case | Expected behaviour |
|---|---|
| No activities in range | `points: []`, `current: null`, `change: null`. The card shows the empty text (AC11.3) |
| Manual activity (no GPS) | Usually no HR or zero distance, so it's excluded (AC1.4, AC1.6). If it has HR, distance and moving time ≥ 20 min, it counts like any run. No GPS is ever read |
| Activity without heart rate | Excluded, not counted as pending (AC1.4, AC6.2) |
| Week spans DST change / time zones | Only `start_date_local` dates are used. 28 days = 28 calendar dates, so DST cannot shift a run's day (AC1.8) |
| Treadmill run | Excluded even if easy/long with HR (AC1.3) |
| Run under 20 min moving time | Excluded (AC1.5) |
| Moving time not yet stored (detail pending) | Excluded from points, counted in `runsPendingDetail`, footnote/empty text says importing (R6) |
| Workout run (`NxMmin`) | Excluded (AC1.2) |
| Long run (> 20 km, non-workout) | Included, `runType: "long"`, different colour. It shares the rolling line with easy runs |
| Very high HR on an "easy" run | Still counted. The classifier decides the type and there is no HR-based exclusion (intent decision 4) |
| Fewer than 3 runs in a 28-day span | That point's `rollingEfficiency` is null (gap in the line). `current`/`change` may be null with notes |
| Last qualifying run older than 28 days | `current = null`, note shown (AC4.2, AC10.3) |
| Rolling history shorter than 28 days | `change = null`, note shown (AC5.3, AC10.4) |
| Two runs on one day | Two points, in `startLocal` order (AC1.9, AC3.5) |
| Run dated after today (clock skew) | Ignored (`localDate <= today`) |
| Dashboard itself empty ("No runs in the last 5 months") | The EF card is inside the dashboard's normal branch, so it isn't shown. Accepted (see Risks) |

## Privacy
- Data fields & sensitivity: average heart rate (health data, GDPR Art. 9), distance, moving time, pace and run dates. Activity names/descriptions are read only by the classifier and never returned. No GPS, no tokens.
- Storage & retention: no new storage. Values are computed per request from the existing `runs` table, under the existing 6 months + 41 days retention and pruning (009). Disconnect/delete (001) removes them as before.
- Scopes: no change. No new Strava call.
- Applicable privacy-check items:
  - 1 ✅ no secrets; fixtures invented.
  - 2 ✅ no tokens in the response.
  - 3, 4 n/a (OAuth unchanged).
  - 5 ✅ no new scope.
  - 6 ✅ no new stored fields.
  - 7 ✅ no GPS read or stored.
  - 8 ✅ no logging of HR values or series. Log nothing beyond existing call logging (path only).
  - 9 ✅ client logging unchanged.
  - 10 ✅ standard error shape, no stack traces.
  - 11 ✅ route checks the session (AC7.2).
  - 12 ✅ cookies unchanged.
  - 13 ✅ tooltip and template use only numbers/dates. No `[innerHTML]`, no Strava text in ECharts formatters.
  - 14 n/a (GET only).
  - 15 ✅ only the fields the UI needs (AC7.5).
  - 16 ✅ no third-party calls.
  - 17 ✅ unchanged.
  - 18–20 ✅ unchanged (existing disconnect/delete covers the data).
  - 21 ✅ fixtures use invented numbers.

## Test plan
| AC | Test type | Test name |
|---|---|---|
| AC1.1 | backend unit | `R1 - easy and long outdoor runs with HR and 20+ min are included` |
| AC1.2 | backend unit | `R1 - workout runs are excluded` |
| AC1.3 | backend unit | `R1 - treadmill runs are excluded and not pending` |
| AC1.4 | backend unit | `R1 - runs without average HR are excluded and not pending` |
| AC1.5 | backend unit | `R1 - moving time 1199 s excluded, 1200 s included` |
| AC1.6 | backend unit | `R1 - zero distance or zero moving time is excluded` |
| AC1.7 | backend unit | `R1 - only runs from today minus 6 months to today are plotted` |
| AC1.8 | backend unit | `R1 - point date uses start_date_local` |
| AC1.9 | backend unit | `R1 - two runs on one day are two points in start order` |
| AC2.1, AC2.2 | backend unit | `R2 - efficiency is metres per minute per beat rounded to 3 decimals` |
| AC2.3 | backend unit | `R2 - point carries distance km, moving time, pace and HR` |
| AC3.1 | backend unit | `R3 - rolling average needs 3 runs` |
| AC3.2 | backend unit | `R3 - rolling set is the 28 days ending on the run date` |
| AC3.3 | backend unit | `R3 - runs before the window count towards the first rolling value` |
| AC3.4 | backend unit | `R3 - non-qualifying runs never count towards rolling` |
| AC3.5 | backend unit | `R3 - same-day runs roll in start order` |
| AC4.1–AC4.4 | backend unit | `R4 - current is the latest rolling value within 28 days` |
| AC5.1, AC5.5 | backend unit | `R5 - change percent from first rolling value to current` |
| AC5.2–AC5.4 | backend unit | `R5 - no change without current or with under 28 days between` |
| AC6.1, AC6.2 | backend unit | `R6 - pending counts easy and long outdoor runs with HR but no moving time` |
| AC7.1, AC7.4, AC7.5 | backend route | `R7 - efficiency factor JSON shape` |
| AC7.2 | backend route | `R7 - 401 not_connected without session or token` |
| AC7.3 | backend route | `R7 - no Strava request is made` |
| AC8.1, AC8.2 | frontend component | `014 R8 - efficiency card follows the HR cards and makes one request` |
| AC8.2 | frontend component | `014 R8 - one efficiency-factor request` |
| AC8.3 | frontend component | `014 R8 - reloads quietly after a sync` |
| — | frontend service | `014 R7 - getEfficiencyFactor calls GET /api/efficiency-factor` |
| AC9.1, AC9.2 | frontend unit (options) | `014 R9 - easy and long scatter series with legend for present types` |
| AC9.3 | frontend unit (options) | `014 R9 - null rolling values leave gaps in the line` |
| AC9.4 | frontend unit (options) | `014 R9 - time x axis and EF y axis with units` |
| AC9.5 | frontend unit (options) | `014 R9 - tooltip shows date, type, EF, km, pace, HR and rolling` |
| AC10.1 | frontend component | `014 R10 - tile shows current EF with unit and caption` |
| AC10.2 | frontend unit (view) | `014 R10 - change text and tone for up, down and flat` |
| AC10.3, AC10.4 | frontend component | `014 R10 - notes replace current and change when data is short` |
| AC10.5 | frontend unit (view) | `014 R10 - footnote with pending line` |
| AC11.1 | frontend component | `014 R11 - shows loading state` |
| AC11.2 | frontend component | `014 R11 - shows error state with retry; ignores 401; rejects bad body` |
| AC11.3 | frontend component | `014 R11 - shows empty and still-importing states` |
| AC11.4 | frontend component | `014 R11 - normal state shows tile, chart and footnote` |
| AC12.1, AC12.2 | backend + frontend suites | existing suites pass. `RunningDashboardContractTest` unchanged |

## Out of scope
- Heat, hill or wind correction.
- Per-km HR streams; only the whole-run average HR is used.
- Garmin HRV / resting HR (feature 015).
- Workout/interval runs and treadmill runs.
- Any new Strava API call, scope or sync change; the read endpoint never calls Strava.
- HR-based exclusion of "too hard" easy runs (intent decision 4).

## Suggested follow-ups
- A separate rolling line (or a toggle) for long runs only, since cardiac drift makes long runs look less efficient.
- Distance-weighted (or time-weighted) rolling average instead of the simple mean.
- Showing the EF card even when the main dashboard has no runs in its 5-month window.
- Make the 6-month window and 28-day rolling period configurable.

## Risks & decisions to confirm
- **Rolling definition:** the 28 days ending on each run's date (inclusive). The mean is unweighted per run, needs at least 3 runs, and may use cached runs from up to 27 days before the window start, so the first value is a full 4-week average.
- **Current EF staleness:** `current` is null if the latest qualifying run is older than 28 days, so the tile never shows an old value as "current".
- **Change guard:** the % change is shown only when the first and current rolling values are at least 28 days apart (non-overlapping windows). Otherwise a note is shown. This is my reading of S6.
- **Change label:** uses the day and month of the first rolling value (`+4.2% since 3 May`), not just the month (`since May`) as in the intent example. The existing `formatDayMonth` is reused, so shared date utilities stay untouched.
- **New palette colour:** `palette.longRun = '#f5b942'` (amber) is a one-line edit to the shared `palette.ts`. Purple is already used for treadmill, so it isn't reused for long runs. There is a small merge risk with parallel features 011–013/015.
- **"Next to" placement:** the `.cards` grid is a single column, so "next to" means directly below the HR card. The card lives inside the dashboard's loaded, non-empty branch, so it is hidden when the 5-month dashboard has no runs.
- **Existing page specs:** they must gain a `stubEfficiencyFactorSection()` call (shared test files). Otherwise `HttpTestingController.verify()` fails on the new request.
- **Moving time dependency:** EF needs `moving_time_s`, which is filled only by the detail call (008). Runs still waiting for it are excluded and counted as pending. No sync change is needed because the existing sync already fetches them.
