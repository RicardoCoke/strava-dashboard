# Spec: Long-run HR drift (aerobic decoupling)

**Feature ID:** 013 · **Status:** Approved · **Intent:** [intent.md](./intent.md) · **Date:** 2026-10-31

## Summary
A new card, "Long-run HR drift", sits directly under the "Heart rate by run type" card. For each long run in the last 5 months it plots one Pa:Hr drift % (first-half vs second-half speed÷HR efficiency over usable moving time). The chart has good / watch / poor zone bands, and the card shows a header with the latest value and the average of the last 4, an excluded-runs line and a "still syncing" line. The backend adds one low-priority sync phase after the existing ones: one Strava streams call per long run (`time,velocity_smooth,heartrate,moving`), drawn from the shared 009 budget. The stream is reduced in memory to a single stored drift value. A new read-only endpoint `GET /api/long-run-drift` serves the card from SQLite only.

## Requirements
| ID | Requirement | Priority |
|---|---|---|
| R1 | Pure drift calculation from a stream: usable moving intervals, split into halves by usable time, Pa:Hr drift %, at least 60 minutes usable | Must |
| R2 | A new sync phase, `MISSING_DRIFT_STREAMS`, runs after all existing phases. It makes one streams call per eligible long run, uses the shared 80-per-window budget, and stores only the derived result | Must |
| R3 | Drift backlog counts as pending sync work, so older long runs fill in over later syncs without user action | Must |
| R4 | `GET /api/long-run-drift` returns chart-ready data from the local store only and never calls Strava | Must |
| R5 | Report logic: long runs in the 5-month window (006 rule), one point per computed run, zone per value, latest, last-4 average, excluded counts by reason, pending count | Must |
| R6 | Frontend card directly under the HR-by-type card, with loading, empty, error and normal states, header, excluded and pending lines, and a quiet refresh after each sync | Must |
| R7 | Chart: drift % over time, with good (< 5%), watch (5–10%) and poor (> 10%) zone bands, and each point coloured by its zone | Must |
| R8 | Persistence: a new `run_hr_drifts` table holding only status + drift %, removed together with its run | Must |

## Acceptance criteria

### R1 — Drift calculation (`HrDriftCalculator.compute(stream): HrDriftResult`)
Definitions. Interval *i* runs from sample *i* to *i+1*, with `dt = t[i+1] − t[i]`, and it takes the values of sample *i*. An interval is **usable** when all of these hold: `1 ≤ dt ≤ 30` (reusing `HrHistogramBuilder.MAX_SAMPLE_GAP_SECONDS`; longer gaps are pauses), `moving[i] == true` (when the stream has no `moving`, every interval counts as moving), and `bpm[i] > 0`. `U` = the sum of `dt` over usable intervals. A usable interval belongs to the **first half** when the usable time accumulated *before* it is `< U / 2`; otherwise it belongs to the second half. For each half, `avgSpeed = Σ(speed·dt)/Σdt`, `avgHr = Σ(bpm·dt)/Σdt`, `EF = avgSpeed / avgHr`. `driftPct = (EF1 − EF2) / EF1 × 100`, stored unrounded.
- **AC1.1** Given 7,200 one-second samples, all moving, with speed 3.0 m/s and HR 140 for the first 3,600 and speed 2.9 m/s and HR 145 for the rest, when computed, then status is `COMPUTED` and `driftPct` rounds (half-up, 1 decimal) to `6.7`.
- **AC1.2** Given constant speed 3.0 m/s with HR 140 then 147 (equal halves), then `driftPct` rounds to `4.8`.
- **AC1.3** Given the second half more efficient than the first (HR 147 then 140 at constant speed), then `driftPct` is negative (rounds to `−5.0`) and is returned as is.
- **AC1.4** Given a stream with exactly 3,600 s usable, then status is `COMPUTED`. Given 3,599 s usable, then status is `TOO_SHORT` and `driftPct` is null.
- **AC1.5** Given a stream where a 120-s gap separates two samples, then that interval adds no time to `U` or to either half.
- **AC1.6** Given a stream where `moving` is false for 600 s in the middle, then those intervals are excluded, and the halves split the remaining usable time equally.
- **AC1.7** Given a stream without a `moving` array, then every interval with `1 ≤ dt ≤ 30` and `bpm > 0` counts as usable.
- **AC1.8** Given intervals with `bpm = 0` (HR dropouts), then those intervals are excluded from `U` and from both halves.
- **AC1.9** Given a stream where every `bpm` is 0, or every speed is 0, or the first half has `avgSpeed = 0`, then status is `NO_DATA` and `driftPct` is null. These checks run before the 60-minute check.

### R2 — Sync phase
`needsDriftStream(run)` is true when **all** of these hold: `RunClassifier.classify(run) == RunType.LongRun`, `!run.trainer`, `run.averageHeartRateBpm != null`, `run.detailFetchedAt != null`, `run.hrDrift == null`.
- **AC2.1** `SyncPhase` gains `MISSING_DRIFT_STREAMS` as its **last** entry (after `RECHECKS`). `SyncPolicy.plan(MISSING_DRIFT_STREAMS, …)` returns one call per run where `needsDriftStream` is true, newest `startUtc` first.
- **AC2.2** Given a long run that needs a drift stream, when the sync makes the call, then `ActivitySource.driftStream(id)` is called once, the result of `HrDriftCalculator.compute` is saved with `saveHrDrift`, and the next sync plans no call for that run.
- **AC2.3** Given `driftStream` returns `NotFound` (404, or a stream missing `time`, `velocity_smooth` or `heartrate`), then `HrDriftResult(NO_DATA, null)` is saved and the run is never fetched again.
- **AC2.4** Given a treadmill long run (`trainer = true`), a long run without average HR, a run of 20,000 m or less, a workout over 20 km, or a long run without a fetched detail, then no drift call is planned for it.
- **AC2.5** Given 2 runs missing details, 1 outdoor workout missing a speed stream and 3 long runs needing drift, and a budget with 4 calls left after the list page, when one sync runs, then it makes the 2 detail calls, the 1 speed-stream call and 1 drift call (newest long run), and the other 2 long runs stay pending.
- **AC2.6** Given Strava returns 429 on a drift call, then the sync stops as in 009 AC5.1 (`rateLimitedUntil` saved), no drift row is written for that run, and it is planned again in a later sync.
- **AC2.7** Given the drift call ends `ReauthRequired` or `Unavailable`, then the sync stops with the existing 009 outcome and no drift row is written.
- **AC2.8** Given any drift call, then no stream values, no HR series and no coordinates are logged or stored. Only the `HrDriftResult` is persisted.

### R3 — Backlog fills in automatically
- **AC3.1** `SyncPolicy.pendingCount` also counts runs where `needsDriftStream` is true. Given 5 long runs needing drift and nothing else pending, then `pendingCount = 5` and a sync is due straight away when the budget allows (009 AC3.3).
- **AC3.2** Given 30 long runs needing drift and a full budget, when syncs run window after window, then each window makes at most 80 Strava calls in total (list pages included), and all 30 are computed without user action.
- **AC3.3** Given every drift call is done and nothing else is pending, then `pendingCount = 0`, the status is `ok` and the 15-minute interval applies (009 AC3.1).

### R4 — Read endpoint never calls Strava
- **AC4.1** Given a connected session and a sync that is due, when `GET /api/long-run-drift` is called, then it returns 200 built from stored runs and the fake `ActivitySource` gets zero calls.
- **AC4.2** Given no session, or no stored tokens, when it is called, then it returns 401 `not_connected`.
- **AC4.3** Given the response, then its top-level keys are exactly `windowStart`, `windowEnd`, `thresholds`, `latest`, `lastFourAverage`, `points`, `excluded`, `pendingCount`, and it contains no activity name, ID, token or stream data.

### R5 — Report logic (`LongRunDriftReport.compute(runs, today)`)
In scope: runs with `isRun` and `RunClassifier.classify(run) == RunType.LongRun`, whose `localDate` (from `start_date_local`) falls in `[today − 5 months, today]`. `HeartRateTrend.WINDOW_MONTHS` is reused. Each run in scope lands in exactly one bucket, in this order:
1. `trainer` → excluded **treadmill**.
2. `averageHeartRateBpm == null`, or `hrDrift.status == NO_DATA` → excluded **missingData**.
3. `hrDrift.status == TOO_SHORT` → excluded **tooShort**.
4. `hrDrift == null` → **pending**.
5. `hrDrift.status == COMPUTED` → **point**.
- **AC5.1** Given today 2026-10-31 and computed long runs on 2026-05-30 and 2026-05-31 (local dates), then only the 2026-05-31 run is a point, and `windowStart = "2026-05-31"`, `windowEnd = "2026-10-31"`.
- **AC5.2** Given computed long runs, then `points` are sorted by `startLocal` ascending. Each has `date` (local date), `driftPct` (half-up, 1 decimal), `distanceKm` (`toKmOneDecimal`) and `zone`.
- **AC5.3** Zone is derived from the **rounded** value: `good` if `< 5.0`, `poor` if `> 10.0`, otherwise `watch`. So 4.9 → good, 5.0 → watch, 10.0 → watch, 10.1 → poor, −3.2 → good.
- **AC5.4** Given points with drift 3.0, 6.0, 4.0, 5.0, 2.0 (date order), then `latest` = the 2.0 point (`date`, `driftPct 2.0`, `zone good`), and `lastFourAverage` = `{driftPct: 4.3, zone: "good", runCount: 4}` (mean of the unrounded values of the last 4 points, then rounded).
- **AC5.5** Given 2 points, then `lastFourAverage.runCount = 2` and it averages those 2. Given 0 points, then `latest` and `lastFourAverage` are null.
- **AC5.6** Given in scope: 1 treadmill long run, 1 long run without HR, 1 `NO_DATA`, 1 `TOO_SHORT`, 2 without a drift row and 3 computed, then `excluded = {total: 4, treadmill: 1, tooShort: 1, missingData: 2}`, `pendingCount = 2` and there are 3 points.
- **AC5.7** Given a treadmill long run without HR, then it is counted once, as `treadmill`.
- **AC5.8** Given a run with a drift row that is now classified as a workout (its name or description changed), then it is not in scope and not counted anywhere.
- **AC5.9** `thresholds` is always `{goodBelowPct: 5.0, poorAbovePct: 10.0}`.

### R6 — Card
- **AC6.1** Given the dashboard has loaded with runs, then `<app-long-run-drift-section>` renders inside `.cards`, directly after `<app-heart-rate-trend-chart>` and before `<app-workout-pace-comparison-chart>`. The heading is "Long-run HR drift". The HR-by-type card is unchanged.
- **AC6.2** Given the request is in flight (first load), then the card shows "Loading heart-rate drift…" with `role="status"`.
- **AC6.3** Given the request fails with a non-401 error, or the body fails `isLongRunDriftResponse`, then the card shows "Couldn't load heart-rate drift." and a Retry button with `role="alert"`, and Retry requests again. A 401 is left to the existing interceptor.
- **AC6.4** Given `points`, `excluded.total` and `pendingCount` are all 0, then the card shows "No long runs in the last 5 months" and no chart.
- **AC6.5** Given 0 points but `pendingCount > 0` or `excluded.total > 0`, then the card shows "No long-run drift yet", followed by the pending line and/or excluded line (AC6.7, AC6.8), and no chart or header.
- **AC6.6** Given points, then the header shows `Latest: {signed}% · {d MMM}` and `Avg last {runCount}: {signed}%`, each with CSS class `zone-good|zone-watch|zone-poor` from its zone. `{signed}` is `+3.8` / `−1.2` / `0.0` (U+2212 minus). Example: "Latest: +3.8% · 25 Oct" and "Avg last 4: +4.6%".
- **AC6.7** Given `excluded.total > 0`, then a line reads `{n} long run(s) left out: ` followed by only the non-zero reasons, joined with ", ", in this order: `{a} treadmill`, `{b} under 60 min usable`, `{c} missing HR or speed data`, ending with ".". Example: "3 long runs left out: 1 treadmill, 2 missing HR or speed data." With total 0, there is no line.
- **AC6.8** Given `pendingCount > 0`, then a line reads "{n} long runs still syncing" ("1 long run still syncing" for 1). With 0, there is no line.
- **AC6.9** Given points, then a zone legend reads "Good < 5% · Watch 5–10% · Poor > 10%" (values from `thresholds`), plus "Lower is better. Plain pace, not adjusted for hills or heat."
- **AC6.10** Given `SyncStore.dataVersion` increments, then the card requests again quietly: no loading state, and current content is kept until the response arrives or if the request fails (same pattern as `Vo2maxSection`). Outside the dashboard page (no `SyncStore`), it loads once.

### R7 — Chart (`buildLongRunDriftOptions(data): EChartsOption`)
- **AC7.1** The x axis is `time` (local dates). The y axis is `value`, named "Drift (%)", with `min = min(−5, floor(minDrift) − 1)` and `max = max(15, ceil(maxDrift) + 1)`.
- **AC7.2** Three `markArea` bands on the main series: good `[yMin, 5]` in `palette.accent`, watch `[5, 10]` in `palette.caution`, poor `[10, yMax]` in `palette.worse`, each at opacity 0.08. They are drawn whatever the data.
- **AC7.3** One line series with `symbol: 'circle'` and a thin `palette.textMuted` line. Each point's `itemStyle.color` comes from its zone (good → `accent`, watch → `caution`, poor → `worse`).
- **AC7.4** The tooltip for a point reads `{d MMM y} · Drift {signed}% ({Good|Watch|Poor}) · {distanceKm} km`, for example "25 Oct 2026 · Drift +3.8% (Good) · 28.1 km". It is built from numbers and dates only.
- **AC7.5** Given 1 point, then the chart renders that single dot with the bands.

### R8 — Persistence
- **AC8.1** Given a fresh or existing DB file, when the app starts, then `run_hr_drifts` exists (`SchemaUtils.create`) and existing tables are untouched.
- **AC8.2** Given `saveHrDrift(id, result, at)` is called twice for one run, then one row holds the latest values (upsert).
- **AC8.3** Given a run is deleted, pruned or `deleteAll` runs, then its `run_hr_drifts` row is deleted too.
- **AC8.4** Given `findAll()`, then each `RunActivity.hrDrift` holds the stored result, or null if there is no row.

## Traceability
| Success criterion (intent) | Covered by |
|---|---|
| S1 One drift % per qualifying long run in the last 5 months, over time | R1, R5 (AC5.1, AC5.2), R7 (AC7.1, AC7.3) |
| S2 Good / watch / poor zones visible, runs coloured by zone | R5 (AC5.3), R7 (AC7.2, AC7.3), R6 (AC6.9) |
| S3 Header with latest drift and last-4 average | R5 (AC5.4, AC5.5), R6 (AC6.6) |
| S4 Count and reason of left-out runs | R5 (AC5.6, AC5.7), R6 (AC6.7), R1 (AC1.4, AC1.9) |
| S5 Older runs fill in automatically within rate limits; pending shown | R2, R3, R6 (AC6.8, AC6.10) |
| S6 Opening the dashboard never calls Strava | R4 (AC4.1) |

## Design

### Data from Strava
- **Endpoint (new use):** `GET /activities/{id}/streams?keys=time,velocity_smooth,heartrate,moving&key_by_type=true`. No `latlng`, `altitude` or `grade_smooth`. Strava may also return `distance`; it is ignored (it isn't declared in the DTO). There is no pagination; one call per run.
- **Scopes:** unchanged (`activity:read_all`, from 001). Nothing new is requested.
- **Fields used:** `time.data` (s), `velocity_smooth.data` (m/s), `heartrate.data` (bpm), `moving.data` (boolean). Existing summary fields `distance`, `average_heartrate`, `trainer` and `start_date_local`, plus the detail `description` (for classification).
- **Sync & caching:** a new phase, `MISSING_DRIFT_STREAMS`, after `RECHECKS`, sharing `MAX_ACTIVITY_CALLS_PER_SYNC` and `StravaCallBudget` (80 per UTC quarter-hour). It runs only inside the background sync (`POST /api/sync`). Each run is fetched once. The derived result is cached in `run_hr_drifts` and never refreshed (edits made later in Strava are not re-read; see Risks).
- **Errors:** 401 → the existing client refreshes once and retries; if that fails, the tokens are deleted and the job ends `ReauthRequired` (009 AC5.3). 429 → the sync stops, `rateLimitedUntil` is saved and there is no retry loop (009 AC5.1). Network failure or 5xx → `stravaUnavailable`, retried after 15 min (009 AC5.2). 404 or a stream missing data → `NO_DATA` is stored (AC2.3). No drift row is written on 401, 429 or 5xx.

### Domain model & logic
- `domain/model/HrDrift.kt` *(new)*: `enum class HrDriftStatus { COMPUTED, TOO_SHORT, NO_DATA }`, `data class HrDriftResult(val status: HrDriftStatus, val driftPct: Double?)` (null unless `COMPUTED`), and `data class DriftStream(val times: List<Seconds>, val speeds: List<MetersPerSecond>, val bpm: List<Bpm>, val moving: List<Boolean>?)`, used only in memory.
- `RunActivity` gains `val hrDrift: HrDriftResult? = null`.
- `ActivitySource` gains `suspend fun driftStream(id: ActivityId): SourceResult<DriftStream>` (no usable data → `NotFound`).
- `domain/longrundrift/` *(new package)*:
  - `HrDriftCalculator` (R1). Constants: `MIN_USABLE_SECONDS = 3600`; it reuses `HrHistogramBuilder.MAX_SAMPLE_GAP_SECONDS`.
  - `needsDriftStream(run)` (R2), used by `SyncPolicy`.
  - `LongRunDriftReport.compute(runs, today)` (R5). Constants: `GOOD_BELOW_PCT = 5.0`, `POOR_ABOVE_PCT = 10.0`, `AVERAGE_RUNS = 4`. `enum DriftZone { GOOD, WATCH, POOR }`.
  - `LongRunDriftService(tokens, runs, clock, zone)`: `get(): LongRunDriftResult` (`NotConnected` | `Ready(report)`), with `today = LocalDate.now(clock.withZone(zone))`.
- `SyncPolicy`: new phase entry, a `plan` branch, and `pendingCount` OR-ing `needsDriftStream`. `SyncService.activityCalls` gets a `call.drift` branch → `fetchDriftStream` (same shape as `fetchHrStream`). `ActivityCall` gains `val drift: Boolean = false`.
- **Units and rounding:** speed stays in m/s inside the calculation (the ratio has no units). Output `driftPct` is %, rounded half-up to 1 decimal (`roundOneDecimal`). `distanceKm` uses `toKmOneDecimal`. Rounding happens in `domain`/mapper, never in the frontend.
- **Time zones:** the window and point dates use `start_date_local` (`localDate`), and "today" is in the server zone, as in 006.

### Persistence
- New table `run_hr_drifts`, defined in `persistence/Tables.kt` as `RunHrDriftsTable`: `run_id INTEGER PK` (FK → `runs.id`, `ON DELETE CASCADE`), `status TEXT` (`computed` / `too_short` / `no_data`), `drift_pct REAL NULL`, `computed_at INTEGER` (epoch s).
- Added to `SchemaUtils.create` in `DatabaseFactory.connect`. No migration is needed for existing tables. Existing long runs simply have no row and become pending.
- `ExposedRunRepository`: `saveHrDrift` (upsert), `findAll` joins the drift rows, and `deleteRunsWhere` / `deleteAll` delete drift rows explicitly (matching the existing pattern). Mappers go in `RunRowMappers.kt`.
- Retention: follows its run (6 months + 41 days prune).

### Backend API

#### `GET /api/long-run-drift`
- Auth: a valid `sd_session` (`hasConnectedSession`). No query params. Read-only: no Strava calls, no writes.
- Response 200:
```json
{
  "windowStart": "2026-05-31",
  "windowEnd": "2026-10-31",
  "thresholds": { "goodBelowPct": 5.0, "poorAbovePct": 10.0 },
  "latest": { "date": "2026-10-25", "driftPct": 3.8, "zone": "good" },
  "lastFourAverage": { "driftPct": 4.6, "zone": "good", "runCount": 4 },
  "points": [
    { "date": "2026-06-07", "driftPct": 7.2, "zone": "watch", "distanceKm": 21.1 },
    { "date": "2026-10-25", "driftPct": 3.8, "zone": "good", "distanceKm": 28.1 }
  ],
  "excluded": { "total": 3, "treadmill": 1, "tooShort": 0, "missingData": 2 },
  "pendingCount": 2
}
```
| Field | Type | Meaning |
|---|---|---|
| `windowStart`, `windowEnd` | ISO local date | `today − 5 months`, `today` |
| `thresholds.goodBelowPct`, `.poorAbovePct` | number (%) | 5.0 and 10.0 |
| `latest` | object or `null` | The most recent point (`date`, `driftPct`, `zone`); null with no points |
| `lastFourAverage` | object or `null` | Mean of the last ≤ 4 points; `runCount` 1–4; null with no points |
| `points[]` | array, ascending by date | `date` ISO local date; `driftPct` number, % 1 decimal, may be negative; `zone` `"good" \| "watch" \| "poor"`; `distanceKm` km 1 decimal |
| `excluded` | object | Ints ≥ 0; `total = treadmill + tooShort + missingData` |
| `pendingCount` | int ≥ 0 | Long runs in the window still waiting for their drift call (or for a detail first) |
- Errors: 401 `not_connected` · 500 `internal_error` (the standard `{error, message}` shape).
- Files: `api/LongRunDriftDtos.kt`, `api/LongRunDriftMappers.kt`, `api/LongRunDriftRoutes.kt` (`fun Route.longRunDriftRoutes(tokens, service)`).

### Frontend (Angular)
- **Route / page:** unchanged (root route, `RunningDashboardPage`). The card is a self-contained section, like `Vo2maxSection`.
- **Service & interfaces (`core/api/`):**
  ```ts
  // long-run-drift.models.ts
  export type DriftZone = 'good' | 'watch' | 'poor';
  export interface DriftThresholds { goodBelowPct: number; poorAbovePct: number; }
  export interface LatestDrift { date: string; driftPct: number; zone: DriftZone; }
  export interface DriftAverage { driftPct: number; zone: DriftZone; runCount: number; }
  export interface DriftPoint { date: string; driftPct: number; zone: DriftZone; distanceKm: number; }
  export interface DriftExcluded { total: number; treadmill: number; tooShort: number; missingData: number; }
  export interface LongRunDriftResponse {
    windowStart: string; windowEnd: string; thresholds: DriftThresholds;
    latest: LatestDrift | null; lastFourAverage: DriftAverage | null;
    points: DriftPoint[]; excluded: DriftExcluded; pendingCount: number;
  }
  export function isLongRunDriftResponse(value: unknown): value is LongRunDriftResponse;
  ```
  `long-run-drift.service.ts`: `getLongRunDrift(): Observable<unknown>` → `GET /api/long-run-drift`.
- **Components (`features/long-run-drift/`):**
  - `long-run-drift-section.ts/.html/.scss`: the container; it fetches, holds `state` (`loading | error | loaded`), and reloads quietly on `SyncStore.dataVersion` (skip the first value). Selector `app-long-run-drift-section`.
  - `long-run-drift-chart/long-run-drift-chart.ts`: presentational; `points = input.required<DriftPoint[]>()` and `thresholds = input.required<DriftThresholds>()`; wraps the shared `ChartComponent`.
  - `long-run-drift.options.ts`: `buildLongRunDriftOptions`, `driftTooltip`.
  - `long-run-drift.view.ts`: pure text helpers `formatSignedPct`, `headerTexts`, `excludedText`, `pendingText`, `zoneLabel`, `emptyKind`.
- **States:** loading (AC6.2) · error + Retry (AC6.3) · empty: "No long runs in the last 5 months" (AC6.4) or "No long-run drift yet" + lines (AC6.5) · normal: header, chart, legend, excluded line, pending line (AC6.6–AC6.9).
- **Chart:** see R7. Colours come from `shared/chart/palette.ts`, which gains `caution: '#ffc857'` (watch zone). A matching `--color-caution` token is added to `styles.scss` for the header's `zone-watch` class.

### Files expected to change
Backend (`backend/src/main/kotlin/app/stravadashboard/`):
- *New:* `domain/model/HrDrift.kt`, `domain/longrundrift/HrDriftCalculator.kt`, `domain/longrundrift/LongRunDriftReport.kt`, `domain/longrundrift/LongRunDriftService.kt`, `api/LongRunDriftDtos.kt`, `api/LongRunDriftMappers.kt`, `api/LongRunDriftRoutes.kt`
- *Shared, additive edits:* `domain/model/RunActivity.kt` (+1 field), `domain/ports/ActivitySource.kt` (+1 method), `domain/ports/RunRepository.kt` (+`saveHrDrift`), `domain/SyncPolicy.kt` (phase, plan branch, pending), `domain/SyncService.kt` (+`fetchDriftStream` branch), `strava/StravaClient.kt` (+`driftStreams`), `strava/StravaDtos.kt` (+`moving: BooleanStreamDto?`), `strava/StravaMappers.kt` (+`toDriftStream`), `strava/StravaActivitySource.kt` (+`driftStream`), `persistence/Tables.kt` (+`RunHrDriftsTable`), `persistence/DatabaseFactory.kt` (+1 table in create), `persistence/ExposedRunRepository.kt`, `persistence/RunRowMappers.kt`, `Application.kt` (service + route, 2 lines)
- *Tests:* new `domain/longrundrift/HrDriftCalculatorTest.kt`, `LongRunDriftReportTest.kt`, `api/LongRunDriftRoutesTest.kt`; extend `domain/SyncServiceTest.kt`, `domain/SyncPolicyTest.kt`, the persistence repository test and the strava client/mapper test (fixture `src/test/resources/strava/drift-streams.json`, invented data); update `testsupport/Fakes.kt` (`FakeActivitySource.driftStream`) and `testsupport/TestData.kt`

Frontend (`frontend/src/app/`):
- *New:* `core/api/long-run-drift.models.ts`, `core/api/long-run-drift.service.ts` (+ spec), `features/long-run-drift/**` (section, chart, options, view + specs), `testing/long-run-drift-stub.ts`, `testing/long-run-drift-fixtures.ts`
- *Shared, minimal edits:* `features/running-dashboard/running-dashboard-page.html` (+1 element) and `.ts` (+1 import), `shared/chart/palette.ts` (+`caution`), `src/styles.scss` (+`--color-caution`), and the four page specs (`running-dashboard-page.spec.ts`, `.load.spec.ts`, `.plan.spec.ts`, `.redesign.spec.ts`) gain a `stubLongRunDriftSection()` call

## Edge cases
| Case | Expected behaviour |
|---|---|
| No activities / no long runs in range | "No long runs in the last 5 months" (AC6.4). No Strava calls. |
| Manual activity (no GPS) > 20 km | No avg HR → `missingData`, no call. If it has HR, the streams call returns 404 or no velocity → `NO_DATA` → `missingData`. |
| Activity without heart rate | `missingData`, no Strava call (AC2.4, AC5.6). |
| HR strap dropouts (bpm 0) | Those intervals are excluded (AC1.8). If that leaves < 60 min → `tooShort`. |
| Treadmill (`trainer = true`) | `treadmill`, no call, never plotted (AC2.4, AC5.7). |
| Indoor run not flagged `trainer` | Treated as outdoor. A stream with no velocity → `missingData`, otherwise computed (see Risks). |
| Auto-pause / long stops | Gaps > 30 s and `moving = false` add no time (AC1.5, AC1.6). |
| Run under 60 min usable (e.g. many stops) | `tooShort` (AC1.4). |
| Negative drift | Shown as is, zone good (AC1.3, AC5.3). |
| Value at exactly 5.0 or 10.0 | Watch (AC5.3). |
| Fewer than 4 computed runs | Average of what exists, labelled "Avg last N" (AC5.5). |
| Run still waiting for detail or drift call | Counted in `pendingCount`, no point (AC5.6, AC6.8). |
| Run renamed to a workout after its drift was stored | Leaves scope (AC5.8). The row stays until the run is pruned. |
| Run edited or cropped on Strava after drift stored | Not re-fetched; the stored value stays (see Risks). |
| Long runs older than 5 months but still stored | Their drift is fetched once (newest first) but not shown (see Risks). |
| Time zones / DST | Window and dates use `start_date_local`. Drift uses relative stream seconds, so DST doesn't matter. |
| Rate limit (429) mid-backlog | Sync stops, the remaining runs stay pending, and the card shows "N long runs still syncing" (AC2.6). |

## Privacy
- **Data fields & sensitivity:** HR stream, speed stream and moving flag (HR is GDPR Art. 9 health data), handled **in memory only** during the sync call. Stored: one derived drift % and a status per run. Shown: drift %, date, distance km. No GPS is requested (`latlng` is never in `keys`), stored or logged.
- **Storage & retention:** `run_hr_drifts` in the local SQLite DB, deleted with its run (prune at 6 months + 41 days, delete, `deleteAll`). No raw streams are kept.
- **Scopes:** unchanged, `activity:read_all`.
- **Applicable privacy-check items:**

| # | Status | How it's satisfied |
|---|---|---|
| 1 | ✅ | No secrets. Fixtures use invented streams. |
| 2 | ✅ | Tokens stay in the backend. The response has no token, athlete or activity ID (AC4.3). |
| 3 | ✅ | Existing refresh/revoke flow (AC2.7). |
| 4 | n/a | OAuth unchanged. |
| 5 | ✅ | No new scope. |
| 6 | ✅ | Only status + drift % stored (AC2.8). |
| 7 | ✅ | No location streams requested or stored. |
| 8 | ✅ | Logs keep the existing status/count lines only. No stream values, HR or drift per run are logged. |
| 9 | ✅ | Client logging unchanged. |
| 10 | ✅ | Standard error shape. |
| 11 | ✅ | Session check on the new route (AC4.2). |
| 12 | n/a | No cookie change. |
| 13 | ✅ | No Strava text rendered. Tooltip built from numbers and dates. Interpolation only. |
| 14 | ✅ | The new endpoint is GET with no side effects. The sync is still triggered only by XSRF-protected `POST /api/sync`. |
| 15 | ✅ | Only fields the card needs. |
| 16 | ✅ | No new dependencies or external calls. |
| 17 | ✅ | Unchanged. |
| 18 | ❌ deferred | No disconnect/delete action yet (carried from 001). The new rows are covered by `deleteAll`. |
| 19 | n/a | No webhook. |
| 20 | ✅ | DB backups already treated as personal data. |
| 21 | ✅ | Invented data in all tests. |

## Test plan
| AC | Test type | Test name |
|---|---|---|
| AC1.1 | backend unit | `R1 - drift is efficiency loss from first to second half` |
| AC1.2 | backend unit | `R1 - rising HR at constant speed gives positive drift` |
| AC1.3 | backend unit | `R1 - negative drift is kept as is` |
| AC1.4 | backend unit | `R1 - needs 60 minutes usable moving time` |
| AC1.5 | backend unit | `R1 - gaps over 30 seconds add no time` |
| AC1.6 | backend unit | `R1 - non moving intervals are excluded and halves split usable time` |
| AC1.7 | backend unit | `R1 - missing moving stream counts every short interval as moving` |
| AC1.8 | backend unit | `R1 - zero heart rate samples are excluded` |
| AC1.9 | backend unit | `R1 - no heart rate or no speed gives no data` |
| AC2.1 | backend unit (SyncPolicy) | `R2 - drift phase is last and plans newest long runs first` |
| AC2.2 | backend unit (SyncService) | `R2 - drift call stores derived result once` |
| AC2.3 | backend unit | `R2 - missing stream stores no data and is not refetched` |
| AC2.4 | backend unit | `R2 - no drift call for treadmill, no HR, short, workout or detail-less runs` |
| AC2.5 | backend unit | `R2 - drift calls come after existing phases within the budget` |
| AC2.6 | backend unit | `R2 - 429 on drift call stops sync and keeps run pending` |
| AC2.7 | backend unit | `R2 - reauth or unavailable on drift call writes no row` |
| AC2.8 | backend unit (log capture) + client test | `R2 - drift sync logs no stream data`, `R2 - streams request never asks for latlng` |
| AC3.1 | backend unit | `R3 - drift backlog counts as pending and makes sync due` |
| AC3.2 | backend unit | `R3 - drift backlog fills in over windows within 80 calls each` |
| AC3.3 | backend unit | `R3 - no pending drift returns to 15 minute interval` |
| AC4.1 | backend route | `R4 - long run drift endpoint makes no Strava calls` |
| AC4.2 | backend route | `R4 - long run drift without session returns not_connected` |
| AC4.3 | backend route/contract | `R4 - long run drift response has exact keys` |
| AC5.1 | backend unit | `R5 - window is last five months by local date` |
| AC5.2 | backend unit | `R5 - points sorted and rounded` |
| AC5.3 | backend unit | `R5 - zone from rounded value with inclusive watch band` |
| AC5.4 | backend unit | `R5 - latest and last four average` |
| AC5.5 | backend unit | `R5 - average uses fewer runs and is null with none` |
| AC5.6 | backend unit | `R5 - excluded counts by reason and pending count` |
| AC5.7 | backend unit | `R5 - treadmill without HR counted once as treadmill` |
| AC5.8 | backend unit | `R5 - run reclassified as workout leaves scope` |
| AC5.9 | backend route/contract | `R5 - thresholds are five and ten` |
| AC8.1–AC8.4 | backend persistence (in-memory SQLite) | `R8 - drift table created`, `R8 - drift upsert keeps one row`, `R8 - drift rows deleted with run`, `R8 - findAll loads drift` |
| — | backend strava (MockEngine) | `R2 - drift stream maps time speed heartrate moving`, `R2 - drift stream without velocity is not found` |
| — | frontend service | `R4 - getLongRunDrift calls api long-run-drift` |
| AC6.1 | frontend component (page) | `R6 - drift card renders right after HR card` |
| AC6.2 | frontend component | `R6 - shows loading state` |
| AC6.3 | frontend component | `R6 - shows error with retry` |
| AC6.4 | frontend component | `R6 - shows no long runs empty state` |
| AC6.5 | frontend component | `R6 - shows no drift yet with pending and excluded lines` |
| AC6.6 | frontend unit (view) + component | `R6 - header shows latest and average with zone class` |
| AC6.7 | frontend unit (view) | `R6 - excluded text lists non zero reasons` |
| AC6.8 | frontend unit (view) | `R6 - pending text singular and plural` |
| AC6.9 | frontend component | `R6 - shows zone legend` |
| AC6.10 | frontend component | `R6 - refreshes quietly after sync` |
| AC7.1–AC7.5 | frontend unit (options) | `R7 - y axis bounds`, `R7 - zone bands`, `R7 - points coloured by zone`, `R7 - tooltip text`, `R7 - single point renders` |

## Out of scope
- Per-second or per-km drift curves, raw stream display, GPS or maps.
- Drift for non-long runs, workouts or races.
- Coaching advice or race-day prediction.
- Changes to the long-run rule (006) or the long-run HR chart.
- Warm-up exclusion, grade-adjusted pace, and heat or terrain correction (decided).
- Re-fetching drift after a run is edited on Strava.

## Suggested follow-ups
- Re-fetch drift when a run's detail recheck shows a changed moving time.
- An HR-drift headline tile.
- A trend line over drift values (needs its own intent: "is the trend improving" is now judged by eye).
- Optional warm-up exclusion as a toggle, if the plain method proves noisy.

## Risks & decisions to confirm
- **Treadmill = `trainer` flag only.** "Missing GPS" is not checked, to avoid requesting or storing location data. An indoor run without the flag that still has a velocity stream (e.g. a footpod) would be computed as if it were outdoors. Please confirm.
- **Drift backlog counts in the sync `pendingCount`.** This is what makes backfill continue automatically (S5). Side effect: while it runs, the 009 banner shows "Still importing runs from Strava (n remaining)", with n including long runs waiting for drift.
- **Stored long runs older than the 5-month window** (up to 6 months + 41 days) also get one drift call each, newest first, so the sync rules don't need a date window. That is about 5–10 extra calls once, never repeated. They are not shown.
- **"Avg last 4" with fewer than 4 computed runs** averages what exists and says "Avg last N". The alternative is showing nothing until 4 exist.
- **Zone uses the rounded value**, so the colour always matches the displayed number (4.96 shows as 5.0 and is Watch).
- **Halves split at whole intervals** (no fractional split of the midpoint interval). With 1-s samples, the error is negligible.
- **No re-fetch after edits:** a run cropped or corrected on Strava after its drift was computed keeps the old value.
- **Shared-file edits** (needed to follow the existing patterns): `SyncPolicy`, `SyncService`, `ActivitySource`, `RunActivity`, `ExposedRunRepository`, `RunRowMappers`, `Tables`, `DatabaseFactory`, the Strava client/DTO/mappers, `Application.kt`, `palette.ts`, `styles.scss`, the dashboard page html/ts, and four page specs. All edits are additive, but they are conflict risks with 011, 012, 014 and 015.
- **New palette colour** `caution` (`#ffc857`) for the watch zone. Please confirm the colour.
