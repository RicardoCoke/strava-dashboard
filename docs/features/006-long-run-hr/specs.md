# Spec: Long run type on the HR chart

**Feature ID:** 006 · **Status:** Approved · **Intent:** [intent.md](./intent.md) · **Date:** 2026-10-31

## Summary
The "Heart rate by run type" chart gets a new run type, "Long runs" (`typeKey = "long"`, `kind = "long"`). It holds every non-workout run whose recorded distance is more than 20,000 m, so those runs no longer count as "Easy runs". The work is mostly in the backend: one new `RunType` variant, one extra rule in `RunClassifier`, and a new sort order in `HeartRateTrend`. The JSON shape of `GET /api/dashboard/running` stays the same; the only contract change is that `kind` can now also be `"long"`. On the frontend the TypeScript union is widened and tests are added. The existing chart component already renders any type.

## Requirements
| ID | Requirement | Priority |
|---|---|---|
| R1 | Classify a non-workout Run with recorded distance > 20,000 m as the "Long runs" type; workout classification takes priority. | Must |
| R2 | Runs of 20,000 m or less that are not workouts stay "Easy runs"; the easy entry never contains a run over 20,000 m. | Must |
| R3 | The HR trend `types` list is ordered: Easy runs, then Long runs, then workout types by most recent run (newest first). | Must |
| R4 | Long runs get the same HR treatment as every other type: 5-month window, average HR point per run, treadmill flag, rising-HR flags (feature 003), trend line, and "not enough data" below 3 points. | Must |
| R5 | The API contract adds the `"long"` value to `heartRateTrend.types[].kind`; the TS interface mirrors it. The rest of the response is unchanged. | Must |
| R6 | The HR card lists and renders "Long runs" like any other type, including its "not enough data" state. | Must |
| R7 | The workout pace comparison and the Strava sync behave the same as before. | Must |

## Acceptance criteria

### R1 — Long run classification
- **AC1.1** Given a Run named "Sunday long run" with no description and `distance = 21,100 m`, when it is classified, then the result is `RunType.Long` (`key = "long"`, `label = "Long runs"`).
- **AC1.2** Given a Run of `20,000.1 m` with no workout pattern, when it is classified, then it is `RunType.Long`. The threshold uses raw metres, not the 1-decimal km value.
- **AC1.3** Given a Run of `24,000 m` named "3x20min" (or named "Morning Run" with description "3x20min tempo"), when it is classified, then it is `RunType.Workout(3, 20)`, not Long.
- **AC1.4** Given a treadmill Run (`trainer = true`) of `22,000 m` with no workout pattern, when it is classified, then it is `RunType.Long`.
- **AC1.5** Given a manual Run (no GPS, no HR) of `25,000 m`, when the HR trend is computed, then it counts in the `long` entry's `runCount` and `runsWithoutHeartRate`, and is not in its `points`.

### R2 — Easy runs exclude runs over 20 km
- **AC2.1** Given a Run of exactly `20,000.0 m` with no workout pattern, when it is classified, then it is `RunType.Easy`.
- **AC2.2** Given a Run of `19,950 m` (shown as 20.0 km), when it is classified, then it is `RunType.Easy`.
- **AC2.3** Given runs in the window of 8,000 m, 12,000 m, 20,000 m, 21,000 m and 30,000 m, none of them workouts, when the HR trend is computed, then the `easy` entry has `runCount = 3`, and the `long` entry has `runCount = 2` and the two long-run dates in its points.
- **AC2.4** Given a run that was stored before this feature and is over 20,000 m, when the dashboard is next loaded, then it appears under `long`, not `easy`. No re-sync or migration is needed, because classification happens on read.

### R3 — Type order
- **AC3.1** Given easy runs, long runs, and runs of `5x6min` (most recent 2026-10-20) and `4x8min` (most recent 2026-10-25), when the HR trend is computed, then `types[].typeKey` is `["easy", "long", "4x8min", "5x6min"]`.
- **AC3.2** Given long runs and workouts but no easy runs in the window, when the HR trend is computed, then `types` starts with `long`. With no long runs, no `long` entry appears (a type appears only if it has at least one run in the window, as in 001 AC9.6).
- **AC3.3** Given a long run whose start is more recent than every workout, when the HR trend is computed, then `long` is still second, after `easy`. The order is fixed and does not depend on recency.

### R4 — Same HR treatment for long runs
- **AC4.1** Given today 2026-10-31 and long runs on 2026-05-30 and 2026-05-31 (local dates), when the HR trend is computed, then the 2026-05-30 run is left out and the 2026-05-31 run is included (window `[today − 5 months, today]`, bucketed by `start_date_local`).
- **AC4.2** Given long runs with average HR 151.26 and 148.0 bpm and distances 21,149 m and 32,000 m, when the HR trend is computed, then their points have `averageHeartRateBpm` 151.3 / 148.0 and `distanceKm` 21.1 / 32.0, sorted by `startLocal` ascending.
- **AC4.3** Given a treadmill long run, when the HR trend is computed, then its point has `treadmill = true` and stays in the `long` entry.
- **AC4.4** Given long-run HRs (in date order) 140, 141, 142, 150, the `rising` flags are those `HrRisingTrend.flags` returns for that sequence, computed over long runs only. Easy-run values are not mixed in.
- **AC4.5** Given 3 long runs with HR (2026-06-01, 150), (2026-07-01, 146), (2026-08-01, 142), when the HR trend is computed, then the `long` entry has `hasEnoughData = true` and `trend` = startBpm 150.0, endBpm 142.0, changeBpm −8.0.
- **AC4.6** Given 2 long runs with HR, when the HR trend is computed, then the `long` entry has `hasEnoughData = false`, `trend = null` and 2 points.

### R5 — API contract
- **AC5.1** Given a `HrTypeTrend` with type `RunType.Long`, when it is mapped to DTO, then the JSON entry has `"typeKey": "long"`, `"label": "Long runs"`, `"kind": "long"`. Easy still maps to `"easy"`/`"Easy runs"`/`"easy"` and workouts to `"workout"`.
- **AC5.2** Given the frontend model, then `HrRunType.kind` is typed `'easy' | 'long' | 'workout'`, and the project builds under strict TypeScript.

### R6 — HR card shows Long runs
- **AC6.1** Given `heartRateTrend.types` = easy (12 runs), long (5 runs), 5x6min (4 runs), when the card renders, then the selector options read, in order, "Easy runs (12 runs)", "Long runs (5 runs)", "5x6min (4 runs)", and "Easy runs" is selected by default.
- **AC6.2** Given the user picks "Long runs", when the card re-renders, then the chart shows the long-run points with the same axes, rising colours, treadmill dot colour (`palette.treadmill`), dashed trend line and tooltip format ("12 Oct 2026 · 148.0 bpm · 32.0 km", plus " · Treadmill" for treadmill runs). The trend line under it reads "Trend: −8.0 bpm over the period (lower = fitter)".
- **AC6.3** Given a `long` entry with `hasEnoughData = false` and 1 point, when "Long runs" is selected, then the card shows "Not enough data for a comparison yet: 1 run with heart rate, at least 3 needed." and no chart.
- **AC6.4** Given a `long` entry with `runCount = 1` and 0 points (a run without HR), when it is selected, then the card shows "Not enough data for a comparison yet: 0 runs with heart rate, at least 3 needed." and no chart or empty graph.

### R7 — No change elsewhere
- **AC7.1** Given a 24,000 m "3x20min" run in the last 30 days, when the workout pace comparison is computed, then it still counts under `3x20min`, as before.
- **AC7.2** Given a 25,000 m non-workout run, when the workout pace comparison is computed, then it appears in no entry (as before). When the sync policy plans activity calls, it gets one detail call and no stream call (same as an easy run before).
- **AC7.3** Given the same stored runs, when the weekly distance aggregate is computed, then its output is the same as before this feature.

## Traceability
| Success criterion (intent) | Covered by |
|---|---|
| S1 | R1 (AC1.1, AC1.2), R2 (AC2.3) |
| S2 | R2 (AC2.1, AC2.2) |
| S3 | R1 (AC1.3), R7 (AC7.1) |
| S4 | R1 (AC1.4), R4 (AC4.3), R6 (AC6.2) |
| S5 | R3, R6 (AC6.1) |
| S6 | R4, R6 (AC6.2) |
| S7 | R2 (AC2.3, AC2.4) |
| S8 | R4 (AC4.6), R6 (AC6.3, AC6.4) |

## Design

### Data from Strava
- **No change.** No new endpoints, fields, scopes or sync calls. The feature uses `distance` (m), which is already stored in `runs.distance_m` from `GET /athlete/activities`.
- Scopes: unchanged (`activity:read_all`, from feature 001).
- Sync & caching: unchanged. Classification is a pure function applied on read, so already-cached runs are reclassified when the page next loads (AC2.4).
- Errors (401 → refresh once, then `strava_reauth_required`; 429 → `rateLimited` status, served from the DB; network failure → `stravaUnavailable`, served from the DB): unchanged, as specified in 001 specs. The new type is computed from the local DB, so it shows during rate limits and outages too.

### Domain model & logic
- **`RunType`** (`domain/model/RunType.kt`): add
  ```kotlin
  data object Long : RunType { key = "long"; label = "Long runs" }
  ```
  If `Long` clashes with `kotlin.Long` inside the file, name it `LongRun` with the same key and label.
- **`RunClassifier`** (`domain/RunClassifier.kt`):
  - `const val LONG_RUN_MIN_EXCLUSIVE_METERS = 20_000.0`.
  - `classify(run: RunActivity): RunType` does this:
    1. Find a workout from the name, else the description (existing regex and ranges). If found, return `Workout`.
    2. Else, if `run.distance.value > LONG_RUN_MIN_EXCLUSIVE_METERS`, return `Long`.
    3. Else return `Easy`.
  - The text-only overload `classify(name, description)` keeps returning only `Workout` or `Easy`. Update its KDoc to say it ignores distance.
  - Other callers (`WorkoutPaceComparison`, `SyncPolicy`, `RepPaceCalculator`) only look for `RunType.Workout` with `as?`/`is`, so `Long` acts like `Easy` for them (R7). No change in those files.
- **`HeartRateTrend.compute`** (`domain/HeartRateTrend.kt`): replace the `easyFirstThenMostRecent` comparator with a rank and then recency.
  - Rank: `Easy` = 0, `Long` = 1, `Workout` = 2.
  - Then `thenByDescending` the most recent `startUtc`. This only matters among workouts.
  - Grouping still uses `RunClassifier.classify(it)`, so the window, points, rising flags, trend and `MIN_POINTS_FOR_COMPARISON = 3` apply to `Long` unchanged (R4).
- **Units:** the threshold compares raw `Meters`. `distanceKm` in points stays `toKmOneDecimal()` (half-up). Time zone: the window and point dates use `startLocal`, unchanged.

### Persistence
No change. There are no new tables or columns and no migration.

### Backend API

#### `GET /api/dashboard/running` (existing, auth: valid `sd_session`)
- Query params: none. Response shape unchanged from 001 specs plus feature 003's `rising`. The only change is that a `heartRateTrend.types[]` entry can now be the long type.
```json
{
  "heartRateTrend": {
    "windowStart": "2026-05-31",
    "windowEnd": "2026-10-31",
    "minRunsForComparison": 3,
    "types": [
      {
        "typeKey": "easy",
        "label": "Easy runs",
        "kind": "easy",
        "runCount": 38,
        "runsWithoutHeartRate": 1,
        "hasEnoughData": true,
        "points": [
          { "date": "2026-06-02", "averageHeartRateBpm": 141.8, "distanceKm": 9.1, "treadmill": false, "rising": false }
        ],
        "trend": { "startDate": "2026-06-02", "startBpm": 143.0, "endDate": "2026-10-30", "endBpm": 139.4, "changeBpm": -3.6 }
      },
      {
        "typeKey": "long",
        "label": "Long runs",
        "kind": "long",
        "runCount": 6,
        "runsWithoutHeartRate": 0,
        "hasEnoughData": true,
        "points": [
          { "date": "2026-06-07", "averageHeartRateBpm": 151.3, "distanceKm": 21.1, "treadmill": false, "rising": false },
          { "date": "2026-07-12", "averageHeartRateBpm": 149.0, "distanceKm": 24.0, "treadmill": true, "rising": false }
        ],
        "trend": { "startDate": "2026-06-07", "startBpm": 152.0, "endDate": "2026-10-25", "endBpm": 147.5, "changeBpm": -4.5 }
      },
      {
        "typeKey": "5x6min",
        "label": "5x6min",
        "kind": "workout",
        "runCount": 2,
        "runsWithoutHeartRate": 0,
        "hasEnoughData": false,
        "points": [
          { "date": "2026-10-20", "averageHeartRateBpm": 158.2, "distanceKm": 12.4, "treadmill": false, "rising": false },
          { "date": "2026-10-27", "averageHeartRateBpm": 157.9, "distanceKm": 12.5, "treadmill": false, "rising": false }
        ],
        "trend": null
      }
    ]
  }
}
```
(`sync`, `weeklyDistance` and `workoutPaceComparison` are unchanged and left out above.)
- Field types (changed field only): `types[].kind`: `"easy" | "long" | "workout"`. Others: `typeKey` string (`"easy"`, `"long"` or `"NxMmin"`); `label` string; `runCount`, `runsWithoutHeartRate` int; `hasEnoughData` boolean; `points[].date` ISO local date; `averageHeartRateBpm` bpm, 1 decimal; `distanceKm` km, 1 decimal; `treadmill`, `rising` boolean; `trend` object or `null`.
- **Mapper** (`api/RunningDashboardMappers.kt`): `kind = when (type) { RunType.Easy -> "easy"; RunType.Long -> "long"; is RunType.Workout -> "workout" }`. The `when` is exhaustive, so a future variant fails to compile.
- Errors: unchanged (`401 not_connected`, `401 strava_reauth_required`, `500 internal_error`).

### Frontend (Angular)
- Route / page: unchanged (`''` → `features/running-dashboard/running-dashboard-page`).
- Components: `HeartRateTrendChart` is unchanged. It already lists types in backend order, defaults to the first type, and renders any type's points, trend and "not enough data" text.
- Service & TS interfaces: `core/api/running-dashboard.models.ts` changes to `kind: 'easy' | 'long' | 'workout'`. `RunningDashboardService` is unchanged.
- Chart options: `buildHeartRateTrendOptions` is unchanged. Colours come from the existing palette per feature 002: accent line, `palette.worse` for rising, and `palette.treadmill` for treadmill dots.
- Headline tile "Easy-run HR trend" is unchanged in code. It reads the `easy` entry, so it now excludes long runs automatically (see Risks).
- States (HR card):
  - **Loading:** page-level "Loading your runs…" (001 AC13.1).
  - **Error:** page-level "Couldn't load your dashboard." + Retry (001 AC13.2).
  - **Empty:** "No runs in the last 5 months" when `types` is empty.
  - **Not enough data** for the selected type, including Long runs: AC6.3 / AC6.4.
  - **Normal:** the chart, as in AC6.2.
- Test fixtures: add a `longHrType()` helper (or use `hrType({ typeKey: 'long', label: 'Long runs', kind: 'long', ... })`) in `testing/dashboard-fixtures.ts` with invented data.

### Files expected to change
- `backend/src/main/kotlin/app/stravadashboard/domain/model/RunType.kt`: add `Long`
- `backend/src/main/kotlin/app/stravadashboard/domain/RunClassifier.kt`: distance rule and constant
- `backend/src/main/kotlin/app/stravadashboard/domain/HeartRateTrend.kt`: rank-based ordering
- `backend/src/main/kotlin/app/stravadashboard/api/RunningDashboardMappers.kt`: exhaustive `kind` mapping
- `backend/src/main/kotlin/app/stravadashboard/api/RunningDashboardDtos.kt`: comment only, documenting `kind` values
- `backend/src/test/kotlin/app/stravadashboard/domain/RunClassifierTest.kt`
- `backend/src/test/kotlin/app/stravadashboard/domain/AggregationsTest.kt`
- `backend/src/test/kotlin/app/stravadashboard/api/RunningDashboardContractTest.kt`
- `backend/src/test/kotlin/app/stravadashboard/domain/SyncServiceTest.kt` (or the existing SyncPolicy test location) for AC7.2
- `frontend/src/app/core/api/running-dashboard.models.ts`
- `frontend/src/app/testing/dashboard-fixtures.ts`
- `frontend/src/app/features/running-dashboard/dashboard-cards.spec.ts`
- `frontend/src/app/features/running-dashboard/chart-options/chart-options.spec.ts`

## Edge cases
| Case | Expected behaviour |
|---|---|
| No activities in range | `types` is empty and the card shows "No runs in the last 5 months" (unchanged). |
| No long runs in range | No `long` entry; the selector doesn't list it (AC3.2). |
| Exactly 20,000.0 m | Easy (AC2.1). |
| 20,000.1–20,049 m (shown as "20.0 km") | Long; the tooltip shows "20.0 km" (AC1.2). See Risks. |
| 19,950–19,999 m (shown as "20.0 km") | Easy (AC2.2). |
| Workout pattern on a > 20 km run | Workout; never Long (AC1.3). |
| Manual activity (no GPS, often no HR) > 20 km | Long; counted in `runCount` and `runsWithoutHeartRate`, not plotted (AC1.5). |
| Activity without heart rate | Counted, not plotted. If it leaves Long runs with fewer than 3 points, the card shows "not enough data" (AC6.4). |
| Treadmill long run | Long, `treadmill = true`, treadmill dot colour (AC1.4, AC4.3). |
| Run edited in Strava (distance or name changed) | Reclassified on the next read after sync updates the stored row. No extra logic. |
| Time zones / DST | Window and dates use `start_date_local` (unchanged). Distance is zone-independent. |
| Non-Run sport types (e.g. Ride > 20 km) | Already excluded by `isRun`; never Long. |

## Privacy
- **Data fields & sensitivity:** `distance` (m), plus the per-run average HR already in use. HR is GDPR Art. 9 health data. No new fields, no GPS.
- **Storage & retention:** no change. This uses the existing `runs` table and the existing 6-month prune. Nothing new is persisted.
- **Scopes:** unchanged (`activity:read_all`); no new scope requested.
- **Applicable privacy-check items:**
  - 1 ✅ No secrets or config changes.
  - 2, 3, 4 n/a: no token or OAuth changes.
  - 5 ✅ Same scope.
  - 6 ✅ No new stored fields.
  - 7 ✅ No GPS.
  - 8 ✅ No new logging. Implementers must not add logs of HR values or per-run data; log counts only if anything.
  - 9 n/a.
  - 10 ✅ Error responses unchanged.
  - 11 ✅ Existing session check on `/api/dashboard/running`.
  - 12, 14 n/a: no new cookies or state-changing endpoints.
  - 13 ✅ The label "Long runs" is a server constant. Activity names are still not rendered in the HR chart, and the tooltip formatter builds text from numbers and dates only.
  - 15 ✅ Only one enum value added; no raw Strava data.
  - 16, 17 ✅ No new third parties or libraries.
  - 18, 19, 20 ✅ Unchanged: disconnect still deletes all data.
  - 21 ✅ New fixtures use invented distances, HR and dates.

## Test plan
| AC | Test type | Test name |
|---|---|---|
| AC1.1 | backend unit | `R1 - non-workout run over 20 km is a long run` |
| AC1.2 | backend unit | `R1 - threshold uses raw metres above 20000` |
| AC1.3 | backend unit | `R1 - workout pattern over 20 km stays a workout` |
| AC1.4 | backend unit | `R1 - treadmill run over 20 km is a long run` |
| AC1.5 | backend unit | `R1 - manual long run without HR is counted not plotted` |
| AC2.1 | backend unit | `R2 - exactly 20000 m is an easy run` |
| AC2.2 | backend unit | `R2 - 19950 m is an easy run` |
| AC2.3 | backend unit | `R2 - easy entry excludes runs over 20 km` |
| AC2.4 | backend unit | `R2 - stored runs are reclassified on read` (compute twice over the same list; no repository write) |
| AC3.1 | backend unit | `R3 - easy then long then workouts by most recent run` |
| AC3.2 | backend unit | `R3 - long first when no easy runs and absent when no long runs` |
| AC3.3 | backend unit | `R3 - long stays second even when most recent` |
| AC4.1 | backend unit | `R4 - long run window is last five months by local date` |
| AC4.2 | backend unit | `R4 - long run points rounded and sorted` |
| AC4.3 | backend unit | `R4 - treadmill long run keeps treadmill flag` |
| AC4.4 | backend unit | `R4 - rising flags computed over long runs only` |
| AC4.5 | backend unit | `R4 - long run trend line is least squares` |
| AC4.6 | backend unit | `R4 - long runs with fewer than three points have no trend` |
| AC5.1 | backend route/contract | `R5 - long type serialises with kind long` |
| AC5.2 | frontend build | covered by `npm run build` (strict TS) + fixture typed with `kind: 'long'` |
| AC6.1 | frontend component | `R6 - selector lists easy then long then workouts` |
| AC6.2 | frontend component + options unit | `R6 - selecting long runs renders its chart`, `R6 - long run options keep treadmill colour and tooltip` |
| AC6.3 | frontend component | `R6 - long runs with one point show not enough data` |
| AC6.4 | frontend component | `R6 - long runs with no HR points show not enough data` |
| AC7.1 | backend unit | `R7 - long workout still counts in pace comparison` |
| AC7.2 | backend unit | `R7 - long easy run absent from pace and gets no stream call` |
| AC7.3 | backend unit | existing `R8` weekly distance tests pass unchanged |

## Out of scope
- Changes to the workout pace comparison or any other chart.
- Other new run types and a configurable threshold.
- Other distance splits for easy runs (e.g. 10 km+).
- Changes to how heart rate per run is computed.
- Editing Strava data.

## Suggested follow-ups
- A "Long-run HR trend" headline tile next to the easy-run one.
- Updating 001 `specs.md` AC9.6 and the `kind` field description to point to this feature, so the older spec doesn't contradict it.

## Risks & decisions to confirm
- **Threshold on raw metres.** A run of 20,001–20,049 m is a Long run but its tooltip says "20.0 km". A 19,950 m run also shows "20.0 km" but is Easy. This follows the intent ("strictly more than 20.0 km, using the distance Strava recorded"). Please confirm. The alternative is to compare the rounded 1-decimal km value (> 20.0).
- **Easy-run headline tile changes value.** The "Easy-run HR trend" tile reads the `easy` entry, so it will exclude long runs too. This matches S7's goal, but the tile's number will shift once this ships. No code change is planned there.
- **Classification lives in `RunClassifier.classify(run)`.** Pace comparison, sync policy and rep pace also call it, but they only act on `Workout`. Their behaviour is unchanged, and AC7.1 and AC7.2 guard that.
- **Supersedes 001 decision 4 / AC9.6** ordering for the HR chart: Long runs is a fixed second slot, not ordered by recency.
- **Naming:** `RunType.Long` may need to be `RunType.LongRun` to avoid clashing with `kotlin.Long`. The JSON values do not change either way.
