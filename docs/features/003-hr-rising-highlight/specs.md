# Spec: Highlight rising heart rate on the HR chart

**Feature ID:** 003 · **Status:** Approved · **Intent:** [intent.md](./intent.md) · **Date:** 2026-10-30

## Summary
For each run type on the "Heart rate by run type" chart, the backend works out a 3-run rolling average of the per-run average heart rate. It marks each point as part of a rising stretch or not, using a 1 bpm threshold and a two-step confirmation so one odd run can't flip it. The frontend draws the line in the accent green and switches it to red-orange `#ff6b4a` (`palette.worse`) for the rising stretches. The API change is one new boolean per HR point. Strava sync, storage and how per-run heart rate is computed stay the same.

## Requirements
| ID | Requirement | Priority (Must/Should/Could) |
|---|---|---|
| R1 | Backend computes a 3-run rolling average of average HR per run type, in date order, over that type's plotted points | Must |
| R2 | Backend marks each point `rising` true/false with a hysteresis rule: the state flips only after two consecutive moves of the average in the same direction that add up to at least 1 bpm from the extreme of the current stretch. The flip is backdated to the run where the move began | Must |
| R3 | `GET /api/dashboard/running` exposes `rising: boolean` on every `heartRateTrend.types[].points[]` entry, with no other contract change | Must |
| R4 | The HR chart draws each line segment in `palette.accent` or `palette.worse`, depending on whether the run at the end of the segment is `rising` | Must |
| R5 | Non-treadmill dots on rising points use `palette.worse`. Treadmill dots stay purple | Should |
| R6 | Colouring is computed and shown separately for each run type | Must |
| R7 | 002's colour rule AC8.4 is amended: `palette.worse` may also be used for the rising stretches of the HR chart, and for nothing else new | Must |

## Acceptance criteria
Examples use invented bpm values. The points list is the type's existing `points`: runs with HR in the 5-month window, sorted by `startLocal`. `A[i]` is the rolling average at point `i` (0-based), rounded to one decimal.

### R1 — Rolling average
- **AC1.1** Given a type with points of unrounded bpm `y[0..n-1]`, when the trend is computed, then for every `i ≥ 2`, `A[i] = roundOneDecimal((y[i-2] + y[i-1] + y[i]) / 3)`. `A[0]` and `A[1]` don't exist.
- **AC1.2** Given bpm `150, 150, 150, 151, 152, 153, 154, 148, 146, 146` (Example A), then `A[2..9] = 150.0, 150.3, 151.0, 152.0, 153.0, 151.7, 149.3, 146.7`.
- **AC1.3** Given a type has runs without HR, then those runs are not part of the window. The average is taken over consecutive *points* only, and `runsWithoutHeartRate` doesn't change.
- **AC1.4** Given a type has older runs before the 5-month window, then they are not used. The first two points in the window have no average.

### R2 — Rising / improving state
Definitions: `d[i] = A[i] − A[i−1]` for `i ≥ 3`. All comparisons use integer tenths of a bpm (`round(A × 10)`) so there are no floating-point edge cases. The threshold is 10 tenths (1.0 bpm). The state starts as *improving* at point 2, and the current stretch starts at index `t = 2`.
- **AC2.1 (start)** Given any type, then points 0, 1 and 2 are `rising = false`, and point 3 takes the current state (`false`), because a flip needs `d[i−1]` and `d[i]`.
- **AC2.2 (turn to rising)** Given the state is *improving* at point `i ≥ 4`, when `d[i−1] > 0`, `d[i] > 0` and `A[i] − min(A[t..i]) ≥ 1.0`, then the state becomes *rising*. Let `m` be the **last** index in `[t..i]` where `A` equals that minimum. Points `m+1..i` become `rising = true` (backdated), and the new stretch starts at `t = m+1`.
- **AC2.3 (turn back to improving)** Given the state is *rising* at point `i`, when `d[i−1] < 0`, `d[i] < 0` and `max(A[t..i]) − A[i] ≥ 1.0`, then the state becomes *improving*. Let `m` be the last index of that maximum. Points `m+1..i` become `rising = false`, and `t = m+1`.
- **AC2.4 (otherwise)** Given neither AC2.2 nor AC2.3 applies at point `i`, then point `i` takes the current state. A flat average (`d = 0`) keeps the state.
- **AC2.5 (Example A)** Given Example A, then `rising = [F, F, F, T, T, T, T, F, F, F]`. It turns at i=4 with `d[3]=+0.3`, `d[4]=+0.7`, `151.0 − 150.0 = 1.0`, backdated to point 3. It stays rising at i=7, where only one step fell. It turns back at i=8 with `d[7]=−1.3`, `d[8]=−2.4`, `153.0 − 149.3 = 3.7`, backdated to point 7.
- **AC2.6 (single high run, S3)** Given bpm `150, 150, 150, 150, 159, 150, 150, 150, 150`, then every point is `rising = false`. The averages are `150, 150, 153, 153, 153, 150, 150`, so there are never two rises in a row.
- **AC2.7 (single low run, S3)** Given bpm `150, 150, 150, 150, 141, 150, 150, 150, 150`, then every point is `rising = false`.
- **AC2.8 (small wobble, decision 3)** Given bpm `150, 150, 150, 150.3, 150.6, 150.0`, then every point is `rising = false`. The averages are `150.0, 150.1, 150.3, 150.3`: two rises, but only +0.3 bpm.
- **AC2.9 (slow creep)** Given bpm `150, 150, 150, 150.4, 150.8, 151.2, 151.6, 152.0`, then `rising = [F, F, F, T, T, T, T, T]`. It turns at i=6, where `151.2 − 150.0 = 1.2`, backdated to point 3.
- **AC2.10 (few points)** Given a type with 0, 1, 2 or 3 points, then every point is `rising = false` and nothing fails.

### R3 — API contract
- **AC3.1** Given the dashboard response, then every object in `heartRateTrend.types[].points[]` has exactly the keys `date`, `averageHeartRateBpm`, `distanceKm`, `treadmill`, `rising`. `rising` is a JSON boolean and is never null or missing.
- **AC3.2** Given the dashboard response, then all other fields and their values are the same as before this feature (001 contract).

### R4 — Line colour
- **AC4.1** Given `buildHeartRateTrendOptions(type)`, then segment `j−1 → j` (for `j ≥ 1`) is drawn in `palette.worse` when `points[j].rising` is true, and otherwise in `palette.accent`.
- **AC4.2** Given Example A's points, when the options are built, then the line series are, in order: an accent stretch with points 0–2, a worse stretch with points 2–6 and an accent stretch with points 6–9. Neighbouring stretches share their joining point, so the line has no gaps.
- **AC4.3** Given a type where no point is rising, then there is exactly one accent line stretch covering all points, and the chart looks the same as before this feature.
- **AC4.4** Given a type with a single point, then there are no line stretch series. The dot is still shown.
- **AC4.5** Given the line stretch series, then they are `silent` (no tooltip or hover). Hovering a dot still shows the existing tooltip `"12 Oct 2026 · 142.3 bpm · 8.2 km"` (plus `" · Treadmill"`). The dashed least-squares trend line and the trend note text and tone stay as they are.

### R5 — Dot colour
- **AC5.1** Given a point with `rising = true` and `treadmill = false`, then its `itemStyle` is `{ color: palette.worse, shadowBlur: 10, shadowColor: palette.worse }`.
- **AC5.2** Given a point with `treadmill = true`, then its `itemStyle` is `{ color: palette.treadmill, shadowBlur: 10, shadowColor: palette.treadmill }`, whether or not it is rising.
- **AC5.3** Given a point with `rising = false` and `treadmill = false`, then its `itemStyle` is the accent style, as before.

### R6 — Per type
- **AC6.1** Given easy runs that rise (Example A) and a `5x6min` type whose bpm falls steadily, when the backend computes the trend, then the easy points include `rising = true` and every `5x6min` point is `rising = false`. Runs of one type never affect another type's averages.
- **AC6.2** Given that response in the HR card, when the user switches the selector from "Easy runs" to "5x6min", then the rendered chart options change from containing a `palette.worse` stretch to containing only accent stretches.

### R7 — Colour rule
- **AC7.1** Given the frontend source, then `palette.worse` / `--color-worse` is used only by the `tone-worse` class and by `heart-rate-trend.options.ts` (rising stretches and dots). It is still not used for errors, banners or other chart series.
- **AC7.2** Given 002 `specs.md`, then AC8.4 has a note: "Amended by 003 R7: the HR chart's rising stretches also use `palette.worse`."

## Traceability
| Success criterion (intent) | Covered by |
|---|---|
| S1 (see where the trend turned) | R2 (AC2.2, AC2.5), R4, R5 |
| S2 (red while rising, back to green when falling) | R2 (AC2.2–AC2.5, AC2.9), R4 |
| S3 (one odd run doesn't flip) | R2 (AC2.6, AC2.7, AC2.8), R1 |
| S4 (each run type on its own) | R6, R1 (per type) |
| S5 (same red as the "getting worse" colour) | R4, R5, R7 |

## Design

### Data from Strava
- No change. This feature reads only data that is already cached (`average_heartrate`, `start_date_local`, `trainer` and the run classification from 001).
- Endpoints, scopes (`activity:read_all`), pagination, incremental sync by `after` and caching: as in 001. No new Strava calls.
- Errors: unchanged from 001. **401** → refresh once and retry once; if that fails, delete the tokens and return `401 strava_reauth_required`. **429** → stop the sync and serve from the DB with `sync.status = "rateLimited"`. **Network/outage** → serve from the DB with `stravaUnavailable`. The rising flags are always computed from whatever is in the DB, so they still work during a rate limit or outage.

### Domain model & logic
- `domain/HeartRateTrend.kt`: `HrPoint` gets `val rising: Boolean`. In `typeTrend`, after building `withHr` (already sorted by `startLocal`), call `HrRisingTrend.flags(withHr.map { requireNotNull(it.averageHeartRateBpm) })` and zip the result into the points. The least-squares trend line and the rounding of `averageHeartRateBpm` don't change.
- New `domain/HrRisingTrend.kt`, a pure object with no Ktor or Exposed imports:
  ```kotlin
  object HrRisingTrend {
      const val WINDOW_RUNS = 3
      const val THRESHOLD_TENTHS_BPM = 10   // 1.0 bpm
      /** One flag per input bpm (same order); true = point is in a rising stretch. */
      fun flags(bpmInDateOrder: List<Double>): List<Boolean>
  }
  ```
  - Averages: `roundOneDecimal(mean of 3)`, then converted to integer tenths with `roundHalfUpToInt(avg * 10)`.
  - State machine as in R2. Implementation note: build a `MutableList<Boolean>` locally, then return it as `List`. Keep the helper functions short (detekt), e.g. `rollingTenths(...)` and `step(...)`.
- Units: bpm stay bpm. There are no distance or pace conversions. Day order comes from `startLocal` (the activity's `start_date_local`), as in 001.

### Persistence
- No changes. The flags are computed on every request from the cached runs and are not stored.

### Backend API
- `GET /api/dashboard/running`: same query (none), same auth (session cookie, `401 not_connected` / `401 strava_reauth_required` as in 001).
- Change: `HrPointDto` gets `val rising: Boolean`. Excerpt (Example A, first and fourth points):
```json
{
  "heartRateTrend": {
    "windowStart": "2026-05-29",
    "windowEnd": "2026-10-29",
    "minRunsForComparison": 3,
    "types": [
      {
        "typeKey": "easy",
        "label": "Easy runs",
        "kind": "easy",
        "runCount": 10,
        "runsWithoutHeartRate": 0,
        "hasEnoughData": true,
        "points": [
          { "date": "2026-06-01", "averageHeartRateBpm": 150.0, "distanceKm": 8.2, "treadmill": false, "rising": false },
          { "date": "2026-06-15", "averageHeartRateBpm": 151.0, "distanceKm": 8.0, "treadmill": false, "rising": true }
        ],
        "trend": { "startDate": "2026-06-01", "startBpm": 151.2, "endDate": "2026-08-30", "endBpm": 149.0, "changeBpm": -2.2 }
      }
    ]
  }
}
```
  | Field | Type | Meaning |
  |---|---|---|
  | `points[].rising` | boolean | `true` when this run is in a stretch where the 3-run average HR is rising (R2). The segment that leads into this point is drawn red-orange. |
- Everything else in the response is unchanged (see 001 specs.md).
- Errors: unchanged (`401 not_connected`, `401 strava_reauth_required`, `500 internal_error` via StatusPages).

### Frontend (Angular)
- Route / page: unchanged (`/`, `features/running-dashboard`, lazy-loaded).
- Components: `HeartRateTrendChart` is unchanged. It already passes the selected `HrRunType` to `buildHeartRateTrendOptions`, so S4 works through the existing selector.
- Service & TS interfaces: `core/api/running-dashboard.models.ts`, where `HrPoint` gets `rising: boolean` with a doc comment. `RunningDashboardService` is unchanged.
- States, all unchanged from 001/002:
  - **loading:** page spinner.
  - **error:** "Couldn't load your dashboard." with Retry.
  - **empty:** "No runs in the last 5 months" in the HR card.
  - **not enough data:** "Not enough data for a comparison yet: N runs with heart rate, at least 3 needed."
  - **normal:** chart with coloured stretches. It only renders with ≥ 3 points, and 3 points are always all accent (AC2.1).
- Chart (`chart-options/heart-rate-trend.options.ts`, `buildHeartRateTrendOptions`):
  - Axes, grid and tooltip formatter are unchanged: time x-axis, y-axis "Avg HR (bpm)" with `scale: true`.
  - `series[0]` (points, `name = type.label`): `type: 'line'`, `symbol: 'circle'`, `symbolSize: 6`, `z: 3`, `lineStyle: { width: 0 }`, so it draws dots only. The dot `itemStyle` follows R5.
  - `series[1]` (only when `trend` exists): the dashed trend line, unchanged.
  - Then one series per stretch: `{ type: 'line', name: 'HR line', symbol: 'none', silent: true, z: 2, lineStyle: { width: 1.5, color }, data: [[date, bpm], ...] }`. A pure helper `hrLineStretches(points): { color: string; data: [string, number][] }[]` groups consecutive segments of the same colour (AC4.1, AC4.2). It is exported for unit tests.
  - The tooltip formatter keeps `seriesIndex === 0 → hrPointTooltip`. The stretch series are silent, so they never reach the formatter.
  - No legend is added (intent assumption).

### Files expected to change
- `backend/src/main/kotlin/app/stravadashboard/domain/HrRisingTrend.kt` (new)
- `backend/src/main/kotlin/app/stravadashboard/domain/HeartRateTrend.kt`
- `backend/src/main/kotlin/app/stravadashboard/api/RunningDashboardDtos.kt`
- `backend/src/main/kotlin/app/stravadashboard/api/RunningDashboardMappers.kt`
- `backend/src/test/kotlin/app/stravadashboard/domain/HrRisingTrendTest.kt` (new)
- `backend/src/test/kotlin/app/stravadashboard/domain/AggregationsTest.kt` (per-type test)
- `backend/src/test/kotlin/app/stravadashboard/api/RunningDashboardContractTest.kt` (add `rising` to the fixture and expected JSON)
- `backend/src/test/kotlin/app/stravadashboard/api/RoutesTest.kt` (point keys)
- `frontend/src/app/core/api/running-dashboard.models.ts`
- `frontend/src/app/features/running-dashboard/chart-options/heart-rate-trend.options.ts`
- `frontend/src/app/features/running-dashboard/chart-options/chart-options.spec.ts` (R12 test: assert no series named "Trend" instead of `series.length === 1`)
- `frontend/src/app/features/running-dashboard/chart-options/chart-theme.spec.ts` (002 R9 HR test: the line style moves to the stretch series)
- `frontend/src/app/features/running-dashboard/dashboard-cards.spec.ts` (AC6.2)
- `frontend/src/app/testing/dashboard-fixtures.ts` (`rising` on fixture points)
- `docs/features/002-dark-dashboard-redesign/specs.md` (AC8.4 note), `docs/features/001-running-dashboard/specs.md` (one-line note that 003 adds `points[].rising`)

## Edge cases
| Case | Expected behaviour |
|---|---|
| No activities in range | `types` is empty. The HR card shows "No runs in the last 5 months" (unchanged). No flags are computed. |
| Type with 1–2 HR points | Not-enough-data note (unchanged). Points are `rising = false`. |
| Type with exactly 3 HR points | Chart shown, all accent (AC2.1). |
| Manual activity (no GPS) | Has no HR, so it isn't a point and isn't part of any average (AC1.3). GPS isn't used at all. |
| Activity without heart rate | Not a point, counted in `runsWithoutHeartRate`. The window moves over the points on either side of it (AC1.3). |
| Treadmill run | Part of the type's average like any other point. Its dot stays purple. The segment into it follows its `rising` flag (AC4.1, AC5.2). |
| Two runs of one type on the same local day | Both are points in `startLocal` order (existing sort), and each counts once in the window. A zero-length segment is drawn in that point's colour. |
| Time zones / DST | Order and day labels use `start_date_local` (`startLocal`), unchanged. No week bucketing is involved. |
| Single outlier run, high or low | No flip (AC2.6, AC2.7). |
| Latest run is the first rise | Stays accent until the next run confirms the rise. The flip then backdates to where the rise began (AC2.2). |
| Average rises by exactly 1.0 bpm over two steps | Flips (threshold is ≥ 1.0, AC2.5). |
| Strava 429 / outage | Flags are computed from cached data. Sync banner as in 001. |
| Expired / revoked token | `401 strava_reauth_required` → Connect page, as in 001. |

## Privacy
- **Data fields & sensitivity:** per-run average heart rate (**health data, GDPR Art. 9**) and dates, both already shown on this chart. The only new output is one derived boolean per point. Raw HR streams, GPS, names and tokens are not used.
- **Storage & retention:** nothing new is stored. The flags are computed per request from the existing `runs` cache, so the existing 001 retention and disconnect/delete flow covers them.
- **Scopes:** no change (`activity:read_all`, from 001). No new scope.
- **Applicable privacy-check items:**
  - #6 ✅ No new stored fields.
  - #8 ✅ The new code must not log bpm values or averages. Log type keys and counts only.
  - #9 ✅ No client logging change.
  - #10 ✅ No new error paths.
  - #11 ✅ Same session-guarded route.
  - #13 ✅ The tooltip formatter is unchanged and the stretch series are silent, so no new HTML is built from Strava text.
  - #15 ✅ Adds only the field the UI needs (`rising`, not the averages).
  - #16 ✅ No new dependencies or external calls.
  - #21 ✅ Fixtures use the invented bpm examples above.
  - #1–#5, #7, #12, #14, #17–#20: n/a, nothing changes.

## Test plan
| AC | Test type | Test name |
|---|---|---|
| AC1.1, AC1.2 | backend unit (`HrRisingTrendTest`) | `R1 - rolling average is mean of last three rounded to one decimal` |
| AC1.3 | backend unit (`AggregationsTest`) | `R1 - runs without HR are skipped by the rolling window` |
| AC1.4 | backend unit (`AggregationsTest`) | `R1 - runs before the window are not used for the average` |
| AC2.1, AC2.10 | backend unit | `R2 - first three points and short series are never rising` |
| AC2.2, AC2.3, AC2.5 | backend unit | `R2 - example A turns rising at point three and back at point seven` |
| AC2.4 | backend unit | `R2 - flat average and a single fall keep the current state` |
| AC2.6 | backend unit | `R2 - single high run does not flip` |
| AC2.7 | backend unit | `R2 - single low run does not flip` |
| AC2.8 | backend unit | `R2 - rise under one bpm does not flip` |
| AC2.9 | backend unit | `R2 - slow creep flips and backdates to where it began` |
| AC3.1, AC3.2 | backend route/contract (`RunningDashboardContractTest`, `RoutesTest`) | `R3 - HR points include rising boolean and contract is otherwise unchanged` |
| AC6.1 | backend unit (`AggregationsTest`) | `R6 - rising flags are computed per run type` |
| AC4.1, AC4.2 | frontend chart options | `R4 - example A gives accent red accent stretches sharing joints` |
| AC4.3 | frontend chart options | `R4 - no rising points gives one accent stretch` |
| AC4.4 | frontend chart options | `R4 - single point has no line stretches` |
| AC4.5 | frontend chart options | `R4 - stretch series are silent and tooltip and trend unchanged` |
| AC5.1–AC5.3 | frontend chart options | `R5 - rising dots are red-orange and treadmill dots stay purple` |
| AC6.2 | frontend component (`dashboard-cards.spec.ts`) | `R6 - switching type switches the line colouring` |
| AC7.1 | review check | `palette.worse` used only by `tone-worse` and the HR options builder |
| AC7.2 | review check | 002 AC8.4 note present |

## Out of scope
- Colouring every single run-to-run rise (option "a", rejected).
- Changing which runs or run types appear, or how per-run HR is computed (whole-run `average_heartrate`).
- Alerts, notifications or written advice about overtraining.
- A separate chart or table for the rolling average, and showing the average in the tooltip.
- A legend or explanation of the colours.
- A configurable window size or threshold.

## Suggested follow-ups
- Show the 3-run average in the point tooltip (e.g. "3-run avg 151.0 bpm"). This would need a `rollingAverageBpm` field.
- A small legend ("red-orange = HR trend rising") if the meaning isn't obvious after use.
- Use the rising state of easy runs in the "Easy-run HR trend" headline tile.

## Risks & decisions to confirm
1. **Two-step confirmation (beyond the intent's literal wording).** With a 3-run window, one run that is 3+ bpm off moves the average by ≥ 1 bpm on its own. A pure "average rose by ≥ 1 bpm" rule would then break S3. The spec adds a rule: the average must move in the same direction on **two consecutive runs**, and the total move from the current stretch's lowest (or highest) average must be ≥ 1 bpm (AC2.2/AC2.3). Please confirm. The alternative is a rolling *median* of 3, which ignores single outliers by construction but isn't an "average".
2. **Backdating.** When a rise is confirmed, the red starts at the run where the rise began (the run after the lowest average), not at the run that confirmed it. That matches "turns red from the point where it starts to rise". The catch: the latest run can't turn red on its own, it needs one more run. Confirm this is acceptable.
3. **The 1 bpm is measured from the stretch's extreme, not per step.** So a slow creep of +0.4 bpm per run still turns red after about 3 runs (AC2.9). The alternative reading, "each step ≥ 1 bpm", would never flag a slow creep.
4. **Dots turn red-orange too (R5, Should).** The intent talks about the line. Red dots on a red line read more clearly. Treadmill dots stay purple. Drop R5 if you want only the line recoloured.
5. **002 AC8.4 is amended (R7).** `palette.worse` was reserved for the `tone-worse` text. Using it on the chart is what S5 asks for, but it changes a rule that 002 approved.
6. **Window resets at the 5-month boundary.** The first two in-window points have no average, even if older runs exist (AC1.4). This is simpler and consistent with what's plotted.
7. **Rendering approach.** The points series becomes dots-only (`lineStyle.width: 0`) and the line is drawn by separate silent stretch series. Two existing frontend tests (`chart-theme.spec.ts` 002 R9 HR, `chart-options.spec.ts` R12) need updating. When nothing is rising, the chart looks the same as now.
