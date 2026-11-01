# Spec: Race-day form projection

**Feature ID:** 011 · **Status:** Draft · **Intent:** [intent.md](./intent.md) · **Date:** 2026-10-31

## Summary
A new endpoint `GET /api/race-day-form` combines the 005 training load (actual daily load from stored heart-rate histograms) with the 004 training plan. Days up to today use actual load. From today (or tomorrow, if a run is already recorded today) to race day (the plan's end date), the load comes from the planned minutes multiplied by load-per-minute rates learned from the user's own easy and SubT runs over the last 6 months. The backend returns fitness, fatigue, form and a "ready" band per day, plus a race-day summary. A new "Race-day form" card directly under "Training load" shows the race-day tile, a chart with solid actual lines and dashed planned lines, a "Race day" marker and the shaded band. There are no new Strava calls and no new stored data.

## Requirements
| ID | Requirement | Priority (Must/Should/Could) |
|---|---|---|
| R1 | Chart range: from `today − 6 weeks` to race day (the plan's `endDate`), one entry per day | Must |
| R2 | Actual days: load, fitness, fatigue and form for every day up to the last actual day are the 005 values (same load, 42-day fitness, 7-day fatigue, form = fitness − fatigue) | Must |
| R3 | Today rule: if a run is stored for today, today is actual; otherwise today is planned. Every later day is planned | Must |
| R4 | Load rates from the last 6 months: easy load per minute from at least 3 easy runs with heart rate; SubT load per minute from at least 3 SubT runs with heart rate, otherwise estimated as 2 × the easy rate | Must |
| R5 | Planned load per day: easy minutes × easy rate + SubT minutes × SubT rate. A day with distance but no minutes uses minutes from distance at the plan's easy target pace, as easy minutes. A day with no session (or no plan entry) has load 0. The race day's own session is not counted | Must |
| R6 | Projection: fitness, fatigue and form continue across planned days with the same 42-day / 7-day formulas, on the combined actual + planned loads | Must |
| R7 | Not enough easy runs (fewer than 3): no projection; only days up to today are returned, all actual, with a status the UI can show | Must |
| R8 | Ready band per day: lower = 5 % and upper = 25 % of that day's fitness | Must |
| R9 | Race-day summary: race-day form and fitness, peak fitness (highest actual fitness over the last 6 months) and the difference, plus a ready-made label | Must |
| R10 | `GET /api/race-day-form` returns the exact JSON below; needs a connected session; distinct 404 codes for missing plan / missing LTHR / both; 422 for invalid config; 503 for an unreadable plan | Must |
| R11 | The plan is read on every request (004 behaviour), so a plan change shows on the next load | Must |
| R12 | Placement: the "Race-day form" card is directly after "Training load" (before the VO2max section). It makes its own request, reloads after a finished background sync, and fails independently | Must |
| R13 | Tile above the chart with the race-day label, e.g. "Form +18 · Fitness 61 (-4 from peak)" | Must |
| R14 | Chart: fitness, fatigue and form lines, solid for actual days and dashed for planned days, a "Race day" vertical marker, the shaded ready band labelled as a rough guide, legend and per-day tooltip | Must |
| R15 | Notes: SubT-rate estimate note; "not enough runs" message | Must |
| R16 | States: loading, not configured (says which setting is missing), invalid config, error, not-enough-runs and normal. Facts-only text | Must |
| R17 | 002 dark theme tokens; readable at 375 px and desktop; no horizontal scroll | Must |
| R18 | No regression: 001–010 JSON and behaviour unchanged (except the 005 zero line now rendering, see Risk 6); no new OAuth scope, Strava call or table | Must |

## Acceptance criteria

**Conventions.** `today = LocalDate.now(clock.withZone(zone))` (server zone, as in 004/005). Runs are bucketed by the local date of `start_date_local` (`RunActivity.localDate`). "Run" means a stored activity with `sport_type == "Run"`. A run "with heart rate" means `averageHeartRateBpm != null` and an HR histogram with status `COMPUTED` (the 005 "counted" rule). `load(run)` is the 005 `TrimpCalculator.load`. Unless stated otherwise: `today = 2026-10-31` (Saturday), `LTHR_BPM = 175`, plan start `2026-08-17`, plan `endDate = raceDate = 2026-12-06`. All numbers are invented.

### R1 — Chart range
- **AC1.1** Given today = 2026-10-31 and raceDate = 2026-12-06, then `chartStart = 2026-09-19` (`today.minusWeeks(6)`), `chartEnd = 2026-12-06`, and `days` has 79 entries, one per date, ascending, with no gaps.
- **AC1.2** Given today is after race day (e.g. 2026-12-10), then `chartEnd = today` and every day is actual. Given `raceDate < chartStart`, then `raceDay` is `null` and no race-day marker is drawn.
- **AC1.3** `raceDate` is always the plan's `endDate`. Given another race (e.g. a 10K on a Sunday in November) in the plan, then it gets no marker and no special treatment: it is projected as a normal planned day.

### R2 — Actual days
- **AC2.1** Given any day `d ≤ lastActualDate`, then its `load` equals the sum of `load(run)` over runs with heart rate on `d` (005 AC6.2), and `fitness`, `fatigue`, `form` equal 005's values for `d` computed from the same stored runs (005 AC6.3). A backend test computes both through `TrainingLoadCalculator` and the new projector on the same fixture and compares them.
- **AC2.2** Given a past day with a planned session that was not run, then its load is the actual load (0.0 if nothing was run). Planned sessions never affect actual days.
- **AC2.3** Given runs without heart rate, or still waiting for their HR histogram, then they add 0 to actual days (005 AC6.7).

### R3 — Today rule
- **AC3.1** Given at least one stored Run with local date = today (with or without heart rate), then today has `kind = "actual"`, its load is the actual load, and `lastActualDate = today`.
- **AC3.2** Given no stored Run on today, then today has `kind = "planned"`, its load is the planned load (R5), and `lastActualDate = today − 1`.
- **AC3.3** Every day after today has `kind = "planned"`. Every day before today has `kind = "actual"`.

### R4 — Load rates
Sample = runs with heart rate whose local date is in `today.minusMonths(6) … today` (2026-04-30 … 2026-10-31) and whose `movingTime` is non-null and > 0. Classification uses the existing `RunClassifier.classify(run)`: `Easy` and `LongRun` are **easy runs**; `Workout(reps, minutes)` is a **SubT run**. `MIN_RATE_RUNS = 3`, `SUBT_FALLBACK_FACTOR = 2.0` (= `TrimpCalculator.SUBT_WEIGHT / EASY_WEIGHT`).
- **AC4.1 (easy rate)** `easyRate = Σ load(easy run) / Σ movingMinutes(easy run)`, where `movingMinutes = movingTime seconds / 60.0`. Given three easy runs with loads 50, 60, 70 and moving times of 50 min each, then `easyRate = 1.2`.
- **AC4.2 (SubT rate)** For each SubT run, `workMinutes = min(reps × minutes, movingMinutes)` and `easyPartMinutes = movingMinutes − workMinutes`. `subtRate = max(0.0, (Σ load − easyRate × Σ easyPartMinutes) / Σ workMinutes)` over SubT runs. Given `easyRate = 1.2` and three `3x10min` runs, each 60 moving minutes with load 90, then `subtRate = (270 − 1.2 × 90) / 90 = 1.8` and `subtEstimated = false`.
- **AC4.3 (SubT fallback)** Given fewer than 3 SubT runs in the sample (0, 1 or 2), then `subtRate = 2.0 × easyRate` and `subtEstimated = true`. Given `easyRate = 1.2`, then `subtRate = 2.4`.
- **AC4.4 (no projection)** Given fewer than 3 easy runs in the sample, then no rate is computed and `projection = "notEnoughRuns"` (R7), whatever the number of SubT runs.
- **AC4.5** Runs without heart rate, with status `UNAVAILABLE`, pending HR, with null or zero `movingTime`, or dated before `today.minusMonths(6)` are not in the sample and do not count towards the 3.
- **AC4.6** Rates are kept unrounded in the domain and are not part of the API response.

### R5 — Planned load
For a planned date `d`, `planDay(d)` is the plan's `DatedDay` for `d` (from `BuildCalendar.weeks[].days[]`), or none if `d` is outside the plan. `easyPace` = the plan target pace with `key == "easy"`: its `target`, or the midpoint of `min` and `max` when `target` is null. If the plan has no usable easy pace, `easyPace` = Σ moving seconds / Σ km of the easy runs in the R4 sample.
- **AC5.1 (minutes)** Given `easyMinutes = 25`, `subtMinutes = 30`, `easyRate = 1.0`, `subtRate = 2.0`, then the planned load is `25 × 1.0 + 30 × 2.0 = 85.0`. Given `60` easy and `0` SubT minutes, then `60.0`.
- **AC5.2 (distance only, S10)** Given `totalMinutes = 0`, `distanceKm = 10.0`, `easyPace = 338 s/km` and `easyRate = 1.0`, then minutes = `10.0 × 338 / 60 = 56.33…` and the load is `56.33…` (shown as `56.3`). The day is not treated as rest.
- **AC5.3 (rest)** Given `totalMinutes = 0` and `distanceKm = 0` (including the plan's rest days), or a date with no plan entry (before the plan start or after its end), then the planned load is `0.0`.
- **AC5.4 (race day)** Given `d = raceDate` and `d` is planned, then its load is `0.0`, whatever the plan says for that day, so race-day values describe arriving at the start line. Given race day is actual (AC3.1 on race day, or after it), then its actual load is used.
- **AC5.5** Given the plan has an `easy` pace with `target = null`, `min = 330` and `max = 346`, then `easyPace = 338`. Given no `easy` pace and easy runs totalling 30,000 s over 90 km, then `easyPace = 333.3…`.

### R6 — Projection
- **AC6.1** Let `combined(d)` = actual load for `d ≤ lastActualDate` (from the stored history, including the warm-up days before `chartStart`) and planned load for `d > lastActualDate`. Then for every chart day, `fitness(d) = Σ combined(d−41 … d) / 42`, `fatigue(d) = Σ combined(d−6 … d) / 7` and `form(d) = fitness(d) − fatigue(d)`, unrounded.
- **AC6.2** Given actual daily load 50.0 for every day up to 2026-10-31 (run today) and planned load 0.0 on every later day, then on 2026-11-07 fatigue = 0.0, fitness = 50.0 × 35 / 42 = 41.7 and form = +41.7.
- **AC6.3** Given the same history but planned load 50.0 on every later day, then every planned day has fitness = fatigue = 50.0 and form = 0.0.

### R7 — Not enough easy runs (S12)
- **AC7.1** Given fewer than 3 easy runs in the sample, then `projection = "notEnoughRuns"`, `days` runs from `chartStart` to `today` (43 entries for today = 2026-10-31), every day has `kind = "actual"` (today too, even with no run), and `subtEstimated = false`.
- **AC7.2** In that case `raceDay` is `null` unless `raceDate ≤ today`, in which case it holds the actual race-day values.
- **AC7.3** Otherwise `projection = "projected"`.

### R8 — Ready band (S5)
`READY_LOW_PERCENT = 5`, `READY_HIGH_PERCENT = 25`.
- **AC8.1** For every day, `readyLow = fitness × 0.05` and `readyHigh = fitness × 0.25`, from the unrounded fitness of the same day, then rounded half-up to 1 decimal. Given fitness 60.0, then `readyLow = 3.0` and `readyHigh = 15.0`.
- **AC8.2** Given fitness 0.0, then both are `0.0`.

### R9 — Race-day summary (S4)
Peak window = 005 window, `today.minusMonths(6) … today`, actual values only (005 `days`).
- **AC9.1** `peakFitness` = the highest unrounded actual fitness in the peak window; `peakDate` is the earliest date with that value. Given no runs, then `peakFitness = 0.0` and `peakDate = today.minusMonths(6)`.
- **AC9.2** `raceDay.form` and `raceDay.fitness` are the race-day entry's values (1 decimal). `raceDay.kind` is that entry's `kind`.
- **AC9.3** `fitnessFromPeak = roundHalfUp(raceFitness) − roundHalfUp(peakFitness)` (integer), so the label always adds up. Given race fitness 61.4 and peak 65.0, then `fitnessFromPeak = -4`.
- **AC9.4 (label)** `label = "Form <signed int> · Fitness <int> (<delta> from peak)"`, with the form rounded half-up to an integer from the unrounded value and always signed (`+18`, `-3`, `0`), and `<delta>` signed (`-4`, `+2`). Given `fitnessFromPeak = 0`, then the bracket reads `(at peak)`. Examples: `"Form +18 · Fitness 61 (-4 from peak)"`, `"Form -3 · Fitness 66 (+1 from peak)"`, `"Form 0 · Fitness 65 (at peak)"`. The minus is ASCII `-`, as in 005.

### R10 — API
- **AC10.1** Given a connected session, a valid `LTHR_BPM` and a valid plan, when `GET /api/race-day-form` is called, then the response is `200` with exactly the fields in the Design section (no session text, notes, rates, activity ids, names or bpm values).
- **AC10.2** Given no session cookie, or a session for another athlete, then `401 not_connected`.
- **AC10.3 (S8)** Given `TRAINING_PLAN_CSV` not configured and LTHR valid, then `404 race_day_form_missing_plan`. Given LTHR not configured and the plan valid, then `404 race_day_form_missing_lthr`. Given both not configured, then `404 race_day_form_missing_plan_and_lthr`. A missing setting wins over an invalid one (e.g. plan missing + LTHR invalid → `404 race_day_form_missing_plan`).
- **AC10.4** Given both are configured and LTHR is invalid, then `422 race_day_form_invalid_config` with message "LTHR_BPM is not a whole number between 100 and 220.". Given LTHR valid and the plan invalid (004 AC8.5), then `422 race_day_form_invalid_config` with message "The training plan file has a problem." (no parser reason, no path, no cell values). LTHR is checked first.
- **AC10.5** Given LTHR valid and the plan file unreadable (004 AC2.4), then `503 race_day_form_plan_unavailable`.
- **AC10.6** Given any request, then no Strava HTTP call is made and no sync is started (data comes from the local store, kept fresh by 009's background sync).
- **AC10.7 (rounding)** `load`, `fitness`, `fatigue`, `form`, `readyLow`, `readyHigh`, `raceDay.form`, `raceDay.fitness` and `raceDay.peakFitness` are rounded half-up to 1 decimal. `form` is rounded from the unrounded difference.

### R11 — Plan changes (S9)
- **AC11.1** Given a fake plan source whose content changes between two requests (e.g. the race week's Wednesday goes from 60 to 30 easy minutes), then the second response's planned loads, fitness, fatigue and form for that day onwards reflect the change. No restart, no cache.
- **AC11.2** Given the plan gains a week (end date moves to 2026-12-13), then `raceDate`, `chartEnd` and the marker move with it on the next request.

### R12 — Placement and independence (S7)
- **AC12.1** Given the dashboard page in any state, then `app-race-day-form-section` is rendered directly after `app-training-load-section` and directly before `app-vo2max-section`.
- **AC12.2** Given the page loads, then exactly one `GET /api/race-day-form` is made, in parallel with the other card requests.
- **AC12.3** Given `/api/race-day-form` fails (404, 422, 503, 500, network), then every other card renders its own state. Given other requests fail or are loading, then this card renders its own state.
- **AC12.4** Given a 401, then the shared interceptor redirects to `/connect` and the card shows no error text.
- **AC12.5** Given `SyncStore.dataVersion` changes (009), then the card reloads quietly: the current content stays while the request runs and if it fails (same pattern as `TrainingLoadSection`).

### R13 — Race-day tile (S4)
- **AC13.1** Given `raceDay` is non-null, then above the chart the card shows `raceDay.label` in the large-number style (at least `1.25rem`, weight 700, `tabular-nums`) and the muted caption "Race day · Sun 6 Dec · projected" (`kind = "planned"`) or "Race day · Sun 6 Dec" (`kind = "actual"`).
- **AC13.2** Given `raceDay` is null, then no tile is rendered.

### R14 — Chart (S1, S2, S3, S5)
`buildRaceDayFormOptions(data): EChartsOption` (pure, in `race-day-form.options.ts`). Let `j` = index of `lastActualDate` in `days`.
- **AC14.1** Category x axis of all `days[].date`, labels like "19 Sep"; one y axis named "Load / day" with no `min: 0`.
- **AC14.2 (solid / dashed, S2)** For each of Fitness (`palette.fitness`), Fatigue (`palette.fatigue`) and Form (`palette.form`) there are two line series with the same name ("Fitness (42-day)", "Fatigue (7-day)", "Form"): an actual series with values at indexes `0…j` and `null` after, `lineStyle.type = 'solid'`; and a planned series with `null` before `j` and values at `j…end` (sharing point `j` so the line is continuous), `lineStyle.type = 'dashed'`. `showSymbol: false`, width 2. Given `projection = "notEnoughRuns"`, the planned series are omitted.
- **AC14.3 (race-day marker, S3)** The first actual Fitness series carries a `markLine` with `data: [{ xAxis: formatDayMonth(raceDate) }]`, `lineStyle` solid 1px `palette.accent`, label "Race day" at the top. Not present when `raceDay` is null or `raceDate` is not in `days`.
- **AC14.4 (ready band, S5)** Two stacked line series (`stack: 'ready'`): a hidden lower series (`readyLow`, line width 0, no area, not in the legend, `silent: true`) and a band series named "Ready band (rough guide)" with values `readyHigh − readyLow`, line width 0 and `areaStyle.color = palette.readyBand`. The band is drawn under the lines (`z` lower than the metric series).
- **AC14.5** Legend at the top with "Fitness (42-day)", "Fatigue (7-day)", "Form", "Ready band (rough guide)". Clicking a name toggles both its actual and planned series (ECharts default for same-name series).
- **AC14.6 (tooltip)** `trigger: 'axis'`; the formatter uses `dataIndex` only and shows, one per line: "Sat 7 Nov · planned" (or just "Sat 31 Oct" for actual days), "Fitness 58.3", "Fatigue 40.1", "Form +18.2", "Load 65.0", "Ready band 2.9–14.6". Built only from dates and numbers in the response.
- **AC14.7** `aria-label`: "Race-day form: fitness, fatigue and form from 6 weeks ago to race day; planned days dashed".
- **AC14.8** Below the chart a muted caption reads "Ready band: form at 5–25% of fitness. A rough guide, not a rule." It is shown in both the `projected` and `notEnoughRuns` states.

### R15 — Notes (S11, S12)
- **AC15.1 (S11)** Given `projection = "projected"` and `subtEstimated = true`, then a muted note under the tile reads "SubT load is an estimate (2× easy): fewer than 3 SubT runs with heart rate in the last 6 months." Given `subtEstimated = false`, then no such note is rendered.
- **AC15.2 (S12)** Given `projection = "notEnoughRuns"`, then the card shows "Not enough runs with heart rate yet to project the plan." in a `role="status"` element above the chart, and the chart shows only the returned (past) days.

### R16 — States
- **AC16.1 (loading)** `h2` "Race-day form" and "Loading race-day projection…" in `role="status"`; no tile or chart.
- **AC16.2 (not configured, S8)** Given `404 race_day_form_missing_plan`, the card shows only the heading and "Race-day form needs a training plan. Set TRAINING_PLAN_CSV in .env." Given `race_day_form_missing_lthr`: "Race-day form needs a threshold heart rate. Set LTHR_BPM in .env, then restart the backend." Given `race_day_form_missing_plan_and_lthr`: "Race-day form needs a training plan and a threshold heart rate. Set TRAINING_PLAN_CSV and LTHR_BPM in .env, then restart the backend." No tile, chart or Retry.
- **AC16.3 (invalid config)** Given `422 race_day_form_invalid_config` with message `M`, then "Race-day form can't be shown: M" (interpolated) in `role="alert"`, no Retry.
- **AC16.4 (error)** Given 503, 500, a network error or a body failing `isRaceDayFormResponse`, then "Couldn't load race-day form." in `role="alert"` with a Retry button in the 002 style. Retry sets loading and resends.
- **AC16.5 (not enough runs)** AC15.2 + the past-only chart + the band caption; tile only per AC7.2.
- **AC16.6 (normal)** Order: heading → tile → SubT note (if any) → chart → band caption.
- **AC16.7 (facts only)** In every state, the rendered text contains none of these whole words, case-insensitive: `should`, `must`, `recommend`, `reduce`, `increase`, `taper`, `rest`. (`restart` is allowed.) One component test checks all states.

### R17 — Look and layout
- **AC17.1** 002 card look (`--color-surface`, 1px `--color-border`, 12px radius, no shadow), same vertical gap as other cards. Component styles use only `var(--color-*)` tokens; chart colours only from `palette.ts`.
- **AC17.2** `palette.ts` gains `readyBand: 'rgba(0, 241, 159, 0.12)'` (the accent at 12 %), the only new colour value.
- **AC17.3** At 375 px the tile text wraps, the legend wraps, the chart fills the card at 300 px height, and the page has no horizontal scroll.

### R18 — No regression
- **AC18.1** `TrainingLoadRoutes.kt`, `TrainingLoadDtos.kt`, `TrainingPlanRoutes.kt`, `TrainingPlanDtos.kt`, `training-load.models.ts`, `training-plan.models.ts`, `api-error.interceptor.ts` are unchanged; `REQUIRED_SCOPE` stays `activity:read_all`; no file in `strava/` or `persistence/Tables.kt` changes.
- **AC18.2** `TrainingLoadCalculator` changes are limited to making the rolling-average helper public (or moving it to a shared function); 005 outputs are unchanged (existing 005 tests pass unchanged).
- **AC18.3** Existing page specs pass with only a setup change: `RaceDayFormSection` replaced by a stub via `TestBed.overrideComponent(RunningDashboardPage, …)`.
- **AC18.4** `echarts-setup.ts` registers `MarkLineComponent` (needed for the race-day marker). Backend and frontend check commands pass.

## Traceability
| Success criterion (intent) | Covered by |
|---|---|
| S1 (chart last 6 weeks → 6 Dec, three lines) | R1, R2, R6, R14 (AC14.1, AC14.2) |
| S2 (past solid, planned dashed) | R3, R14 (AC14.2), R13 (caption "projected") |
| S3 ("Race day" marker on 6 Dec) | R1 (AC1.3), R14 (AC14.3) |
| S4 (tile: race-day form, fitness vs 6-month peak) | R9, R13 |
| S5 (ready band 5–25 %, labelled rough guide) | R8, R14 (AC14.4, AC14.5, AC14.8) |
| S6 (no session = rest; run today = done; planned today not run = planned) | R3, R5 (AC5.3) |
| S7 (directly under Training load) | R12 (AC12.1) |
| S8 (plan or LTHR missing → hidden, says which) | R10 (AC10.3), R16 (AC16.2) |
| S9 (plan change → projection change on next load) | R11 |
| S10 (distance without minutes projected from distance) | R5 (AC5.2, AC5.5) |
| S11 (SubT estimate note) | R4 (AC4.3), R15 (AC15.1) |
| S12 (no easy runs → past only + message) | R4 (AC4.4), R7, R15 (AC15.2) |
| Constraint: same load / fitness / fatigue as 005 | R2 (AC2.1), R6 |
| Constraint: race day = plan end date only | R1 (AC1.3), R11 (AC11.2) |
| Constraint: ≥ 3 runs to learn a rate; SubT fallback 2×; no projection without easy rate | R4 |
| Constraint: peak = highest fitness over 6 months | R9 (AC9.1) |

## Design

### Data from Strava
- **Endpoints:** none new. The route reads only the local store (runs + HR histograms from 005) and the plan CSV (004).
- **Scopes:** no change (`activity:read_all`).
- **Fields used:** stored `startLocal` (local date), `averageHeartRateBpm`, HR histogram, `movingTime`, `distance`, `name`/`description` (only inside `RunClassifier`, never returned).
- **Sync & caching:** unchanged; 009's background sync keeps the store fresh, and the card reloads on `SyncStore.dataVersion` (AC12.5). Nothing new is cached; the plan is read per request.
- **Errors:** 401 / 429 / network failures happen only inside the existing background sync and are handled there (001/005/009). This route never calls Strava, so it never returns `strava_reauth_required`; a revoked token surfaces through the existing sync status and the 401 `not_connected` session check.

### Domain model & logic
New package `domain/racedayform/` (pure, no Ktor/Exposed/Strava types):
```kotlin
enum class DayKind { ACTUAL, PLANNED }
enum class ProjectionStatus { PROJECTED, NOT_ENOUGH_RUNS }

data class LoadRates(val easyPerMinute: Double, val subtPerMinute: Double, val subtEstimated: Boolean,
    val easyPace: SecondsPerKm?)                     // easyPace only from the run sample (fallback)
sealed interface RatesResult { data class Learned(val rates: LoadRates) : RatesResult
                               data object NotEnoughEasyRuns : RatesResult }

data class FormDay(val date: LocalDate, val kind: DayKind, val load: Double,
    val fitness: Double, val fatigue: Double) {
    val form: Double get() = fitness - fatigue
    val readyLow: Double get() = fitness * READY_LOW_PERCENT / 100.0
    val readyHigh: Double get() = fitness * READY_HIGH_PERCENT / 100.0
}
data class RaceDaySummary(val date: LocalDate, val kind: DayKind, val form: Double, val fitness: Double,
    val peakFitness: Double, val peakDate: LocalDate)          // fitnessFromPeak computed in the mapper
data class RaceDayForm(val today: LocalDate, val raceDate: LocalDate, val chartStart: LocalDate,
    val chartEnd: LocalDate, val lastActualDate: LocalDate, val status: ProjectionStatus,
    val subtEstimated: Boolean, val days: List<FormDay>, val raceDay: RaceDaySummary?)
```
Functions (each ≤ ~30 lines, unit-tested):
- `LoadRateEstimator.estimate(runs, zones, today): RatesResult` (R4). Constants `MIN_RATE_RUNS = 3`, `SUBT_FALLBACK_FACTOR = TrimpCalculator.SUBT_WEIGHT.toDouble() / TrimpCalculator.EASY_WEIGHT`.
- `PlannedLoad.of(day: PlanDay?, rates, easyPace: SecondsPerKm?): Double` (R5, minus the race-day rule). `EASY_PACE_KEY = "easy"`; `PlannedLoad.easyPace(paces, rates)` resolves the pace (AC5.5).
- `RaceDayFormProjector.project(runs, zones, calendar: BuildCalendar, today): RaceDayForm` (R1–R3, R5 race-day rule, R6, R7, R9). Uses `TrainingLoadCalculator.dailyLoads` for actual loads and the shared `rollingAverage` (made public in `TrainingLoadCalculator`) for both fitness and fatigue, and `TrainingLoadCalculator.compute(...)` days for the peak.
- `RaceDayFormService(lthr: LthrSetting, tokens, runs, planService: TrainingPlanService, clock, zone)`: `suspend fun get(): RaceDayFormResult` with
  `sealed interface RaceDayFormResult { NotConnected; MissingPlan; MissingLthr; MissingPlanAndLthr; InvalidLthr; InvalidPlan; PlanUnavailable; data class Ready(val form: RaceDayForm) }`. Order: tokens → config (AC10.3–AC10.5) → `runs.findAll()` → project.

Units and rounding: minutes are `Double` (`seconds / 60.0`), pace in whole `SecondsPerKm`, distance from `Kilometres`. Everything unrounded in the domain; the mapper rounds with the existing `roundOneDecimal` / `roundHalfUpToInt`. Dates are `LocalDate` only, so DST cannot shift a day.

### Persistence
None. No tables, columns or migrations. Reuses `RunRepository.findAll()` and `TrainingPlanSource` via `TrainingPlanService`.

### Backend API
#### `GET /api/race-day-form`
- Query params: none. Auth: `hasConnectedSession(tokens)`, otherwise `401 not_connected`.
- `200` response (exact shape; invented values; `days` shortened, the real list has one entry per day):
```json
{
  "today": "2026-10-31",
  "raceDate": "2026-12-06",
  "chartStart": "2026-09-19",
  "chartEnd": "2026-12-06",
  "lastActualDate": "2026-10-30",
  "projection": "projected",
  "subtEstimated": false,
  "readyBand": { "lowPercent": 5, "highPercent": 25 },
  "raceDay": {
    "date": "2026-12-06",
    "kind": "planned",
    "form": 17.8,
    "fitness": 61.4,
    "peakFitness": 65.0,
    "peakDate": "2026-10-18",
    "fitnessFromPeak": -4,
    "label": "Form +18 · Fitness 61 (-4 from peak)"
  },
  "days": [
    { "date": "2026-09-19", "kind": "actual", "load": 82.5, "fitness": 58.1, "fatigue": 64.0, "form": -5.9, "readyLow": 2.9, "readyHigh": 14.5 },
    { "date": "2026-10-30", "kind": "actual", "load": 0.0, "fitness": 63.2, "fatigue": 70.4, "form": -7.2, "readyLow": 3.2, "readyHigh": 15.8 },
    { "date": "2026-10-31", "kind": "planned", "load": 85.0, "fitness": 63.5, "fatigue": 72.1, "form": -8.6, "readyLow": 3.2, "readyHigh": 15.9 },
    { "date": "2026-12-06", "kind": "planned", "load": 0.0, "fitness": 61.4, "fatigue": 43.6, "form": 17.8, "readyLow": 3.1, "readyHigh": 15.4 }
  ]
}
```
Field rules: dates `YYYY-MM-DD`. `projection` ∈ `"projected" | "notEnoughRuns"`. `kind` ∈ `"actual" | "planned"`. Load-type numbers are JSON numbers in 005 load units per day (1 decimal). `fitnessFromPeak` is an integer. `raceDay` is `null` per AC1.2 / AC7.2. `days` is never empty; its first date is `chartStart` and its last is `chartEnd` (`projected`) or `today` (`notEnoughRuns`). Every day up to `lastActualDate` is `actual`, every later day `planned`. In `notEnoughRuns`, `lastActualDate = today` and `subtEstimated = false`. `readyBand` holds the constant percentages for the caption.
- Errors (`{"error","message"}`, constants in `ErrorResponse`):

| Status | `error` | `message` |
|---|---|---|
| 401 | `not_connected` | existing text |
| 404 | `race_day_form_missing_plan` | "No training plan is configured." |
| 404 | `race_day_form_missing_lthr` | "No threshold heart rate is configured." |
| 404 | `race_day_form_missing_plan_and_lthr` | "No training plan or threshold heart rate is configured." |
| 422 | `race_day_form_invalid_config` | "LTHR_BPM is not a whole number between 100 and 220." or "The training plan file has a problem." |
| 503 | `race_day_form_plan_unavailable` | "The training plan file could not be read." |
| 500 | `internal_error` | existing StatusPages handler |

DTOs in `api/RaceDayFormDtos.kt` (`RaceDayFormResponse`, `ReadyBandDto`, `RaceDayDto`, `FormDayDto`); mapping, rounding and `label` in `api/RaceDayFormMappers.kt`; thin route in `api/RaceDayFormRoutes.kt`.

### Frontend (Angular)
- **Route / page:** no new route. `RunningDashboardPage` imports `RaceDayFormSection`; the template gets `<app-race-day-form-section />` between `<app-training-load-section />` and `<app-vo2max-section />`.
- **Service & interfaces** (`core/api/`):
  - `race-day-form.models.ts`: `RaceDayFormResponse`, `RaceDaySummary`, `FormDay` (`kind: 'actual' | 'planned'`), `ReadyBand`, `projection: 'projected' | 'notEnoughRuns'`, `raceDay: RaceDaySummary | null`; plus `isRaceDayFormResponse(value: unknown)` shape guard.
  - `race-day-form.service.ts`: `getRaceDayForm(): Observable<unknown>` → `GET /api/race-day-form`, `providedIn: 'root'`.
- **Components** (`features/race-day-form/`, standalone, `OnPush`, plain interpolation only):
  - `race-day-form-section.ts/.html` (`RaceDayFormSection`, `app-race-day-form-section`): container with `signal<RaceDayFormState>` (`loading | notConfigured(missing: 'plan' | 'lthr' | 'both') | invalidConfig(message) | error | loaded(data)`), loads in `ngOnInit`, quiet reload on `SyncStore.dataVersion` (optional inject, as `TrainingLoadSection`), `retry()`. Renders `section.card` with `h2` "Race-day form" and the R16 states.
  - `race-day-form-chart/race-day-form-chart.ts` (`form = input.required<RaceDayFormResponse>()`): `<app-chart>` with `buildRaceDayFormOptions` and the AC14.7 label.
  - `race-day-form.view.ts` (pure): `stateFromError(error)`, `notConfiguredText(missing)`, `subtNoteText(data)`, `tileCaption(raceDay)`, `BAND_CAPTION`, `NOT_ENOUGH_RUNS_TEXT`.
  - `race-day-form.options.ts` (pure): `buildRaceDayFormOptions`, `formDayTooltipLines(day)`.
- **Formatting:** existing `formatDayMonth`, `formatShortDate`, `formatOneDecimal`, `formatSignedOneDecimal`; the tile label comes ready from the backend.
- **States:**

| View | Loading | Empty / not enough runs | Error | Normal |
|---|---|---|---|---|
| Race-day form card | heading + "Loading race-day projection…" (`role="status"`) | `notEnoughRuns`: message (`role="status"`) + past-only chart + band caption; tile only if race day passed. Not configured: heading + which setting is missing, nothing else | 422: "Race-day form can't be shown: M", no Retry. Other: "Couldn't load race-day form." + Retry (`role="alert"`) | tile → SubT note (if any) → chart → band caption |
| Tile | hidden | per AC7.2 | hidden | race-day label + caption |
| Chart | hidden | actual lines + band, no dashed lines, no marker unless race day in range | hidden | solid + dashed lines, band, race-day marker, legend, tooltip |
| Rest of page | own states, unaffected | same | same | same |

- **Chart:** line chart; x = dates ("19 Sep"); y = "Load / day" (005 load units). Series per AC14.2–AC14.4; colours from `palette` (`fitness`, `fatigue`, `form`, `accent`, new `readyBand`); tooltip per AC14.6.

### Files expected to change
Backend (`backend/`):
- `src/main/kotlin/app/stravadashboard/domain/racedayform/` (new): `RaceDayForm.kt` (models), `LoadRateEstimator.kt`, `PlannedLoad.kt`, `RaceDayFormProjector.kt`, `RaceDayFormService.kt`
- `src/main/kotlin/app/stravadashboard/domain/trainingload/TrainingLoadCalculator.kt`: make `rollingAverage` public (no behaviour change)
- `src/main/kotlin/app/stravadashboard/api/RaceDayFormRoutes.kt`, `RaceDayFormDtos.kt`, `RaceDayFormMappers.kt` (new), `ErrorResponse.kt` (new constants)
- `src/main/kotlin/app/stravadashboard/Application.kt`: wire `RaceDayFormService` (reusing `planService`) and the route
- Tests: `src/test/kotlin/app/stravadashboard/domain/racedayform/LoadRateEstimatorTest.kt`, `PlannedLoadTest.kt`, `RaceDayFormProjectorTest.kt`, `RaceDayFormServiceTest.kt`; `api/RaceDayFormRoutesTest.kt`; `testsupport/TestData.kt` / `Fakes.kt` (invented runs and a fake plan source)

Frontend (`frontend/src/app/`):
- `core/api/race-day-form.models.ts`, `race-day-form.service.ts` (+ `.spec.ts`) (new)
- `features/race-day-form/race-day-form-section.ts`, `.html`, `race-day-form.scss`, `race-day-form.view.ts`, `race-day-form.options.ts`, `race-day-form-chart/race-day-form-chart.ts` (+ specs) (new)
- `features/running-dashboard/running-dashboard-page.ts`, `.html`; existing page specs: stub `RaceDayFormSection`
- `shared/chart/palette.ts` (+ spec), `shared/chart/echarts-setup.ts` (`MarkLineComponent`)
- `testing/race-day-form-fixtures.ts`, `testing/race-day-form-stub.ts` (new)

## Edge cases
| Case | Expected behaviour |
|---|---|
| No activities in range | Fewer than 3 easy runs → `notEnoughRuns`: flat 0.0 actual lines for the past 6 weeks, the S12 message, no tile. |
| Manual activity (no GPS) | Usually no HR → adds 0 to its day, not in the rate sample. It still counts as "run today" for AC3.1 (a run is stored). |
| Activity without heart rate | Adds 0 load (005 rule), excluded from the rate sample. Run today without HR → today is actual with load 0 (AC3.1). |
| Run still waiting for HR histogram | Adds 0 for now and is not sampled; numbers update after the next sync (card reloads, AC12.5). |
| Run with null/0 `movingTime` | Not in the rate sample (AC4.5); its load still counts on its day. |
| Week spans DST change / time zones | Days from the local date of `start_date_local`; all maths on `LocalDate`; `today` in the server zone. |
| Run just after local midnight | Counts on its local date, so it can make "today" actual. |
| Two runs today, plan had one session | Today = actual sum of both. |
| Planned session today, not yet run | Today = planned (AC3.2). Today's line is dashed; the 005 card shows today's actual 0, so the two cards differ for today only. |
| Planned day with distance, no minutes | Easy minutes from distance × easy pace (AC5.2). |
| Plan rest day or day with nothing | Load 0. |
| Days before the plan start or after its end | Load 0 (no plan entry). |
| Race day itself | Planned load 0 (AC5.4), so race-day numbers are "arriving fresh" values. |
| Other races in the plan | Projected like any planned day; no marker. |
| Fewer than 3 SubT runs | SubT rate = 2 × easy, note shown (AC4.3, AC15.1). |
| Fewer than 3 easy runs | No projection, past-only chart, message (R7). |
| Plan has no easy target pace | Fallback to the average pace of the sampled easy runs (AC5.5). |
| Today after race day | All actual, chart ends today, tile shows actual race-day values while race day is within the chart. |
| Plan or LTHR missing / invalid / unreadable | 404 / 422 / 503, card shows its message; rest of the page unaffected. |
| Strava 429 or expired token | Not reached by this route; background sync handles it (009); card shows stored data. |

## Privacy
- **Data fields & sensitivity:** derived load, fitness, fatigue, form and the band (derived from HR histograms: **health data, GDPR Art. 9**); plan minutes/distances (training history). `LTHR_BPM` is used but not returned. No GPS, polylines, names, descriptions, session text, tokens or bpm values in the response.
- **Storage & retention:** nothing new stored; values computed per request from the existing SQLite store (005 retention) and the plan CSV (004). No browser storage.
- **Scopes:** none new (`activity:read_all` unchanged).
- **Applicable privacy-check items:**

| # | Status | How it's satisfied |
|---|---|---|
| 1 | ✅ | No new config or secrets; fixtures invented. |
| 2 | ✅ | No token handling changes. |
| 5 | ✅ | No new scope. |
| 6 | ✅ | No new stored fields. |
| 7 | ✅ | No GPS used. |
| 8 | ✅ | No new log lines with loads, bpm or plan content; error logging stays in 004's service. |
| 10 | ✅ | Fixed error texts; the plan-invalid 422 does not echo the parser reason or path. |
| 11 | ✅ | Route checks the session (AC10.2). |
| 13 | ✅ | Tooltip and texts built from numbers/dates and fixed strings; no `[innerHTML]`; no Strava text reaches the chart. |
| 14 | n/a | GET only, no state change. |
| 15 | ✅ | Only chart fields returned; rates and session text left out. |
| 16 | ✅ | No new dependency or external call. |
| 18 | ❌ deferred | Still no disconnect/delete action (as since 001). |
| 20 | ⚠ | Unchanged: DB backups contain HR-derived data. |
| 21 | ✅ | Invented test data only. |

## Test plan
| AC | Test type | Test name |
|---|---|---|
| AC1.1–AC1.3 | backend unit (projector) | `R1 - chart from six weeks ago to plan end`, `R1 - after race day chart ends today`, `R1 - other races are normal planned days` |
| AC2.1–AC2.3 | backend unit | `R2 - actual days match training load`, `R2 - skipped sessions use actual load`, `R2 - runs without HR add nothing` |
| AC3.1–AC3.3 | backend unit | `R3 - run today makes today actual`, `R3 - no run today makes today planned`, `R3 - kinds split at last actual date` |
| AC4.1–AC4.6 | backend unit (estimator) | `R4 - easy rate per moving minute`, `R4 - subT rate after removing easy part`, `R4 - subT fallback twice easy with flag`, `R4 - fewer than three easy runs no rates`, `R4 - sample excludes runs without HR or moving time` |
| AC5.1–AC5.5 | backend unit (planned load) | `R5 - load from easy and subT minutes`, `R5 - distance only uses easy pace`, `R5 - empty day is zero`, `R5 - race day load excluded`, `R5 - easy pace midpoint and run fallback` |
| AC6.1–AC6.3 | backend unit | `R6 - projection continues rolling averages`, `R6 - zero plan decays fatigue`, `R6 - steady plan keeps form zero` |
| AC7.1–AC7.3 | backend unit | `R7 - not enough easy runs returns past only` |
| AC8.1–AC8.2 | backend unit | `R8 - ready band from same day fitness` |
| AC9.1–AC9.4 | backend unit + mapper | `R9 - peak over six months`, `R9 - from peak uses rounded values`, `R9 - label formats and at peak` |
| AC10.1–AC10.7 | backend route (`testApplication`) | `R10 - 200 exact JSON shape`, `R10 - 401 without session`, `R10 - 404 codes name what is missing`, `R10 - 422 invalid config texts`, `R10 - 503 plan unreadable`, `R10 - no Strava call`, `R10 - rounding` |
| AC11.1–AC11.2 | backend service (fake plan source) | `R11 - plan edit changes projection on next request`, `R11 - plan end date moves race day` |
| AC12.1–AC12.5 | frontend component (page + section) | `R12 - card directly after training load`, `R12 - one race-day request`, `R12 - fails independently`, `R12 - 401 shows no error`, `R12 - quiet reload after sync` |
| AC13.1–AC13.2 | frontend component | `R13 - tile shows race-day label and caption`, `R13 - no tile without race day` |
| AC14.1–AC14.8 | frontend unit (options) | `R14 - actual solid planned dashed`, `R14 - race-day markLine`, `R14 - stacked ready band`, `R14 - legend`, `R14 - tooltip lines`, `R14 - aria label`, `R14 - no planned series when not enough runs` |
| AC15.1–AC15.2 | frontend component | `R15 - subT estimate note`, `R15 - not enough runs message` |
| AC16.1–AC16.7 | frontend component + view unit | `R16 - loading`, `R16 - not configured names missing setting`, `R16 - invalid config`, `R16 - error with retry`, `R16 - normal order`, `R16 - no advice words in any state` |
| AC17.1–AC17.3 | frontend unit + manual 375 px / 1280 px | `R17 - palette has ready band`, manual layout check |
| service | frontend service (`HttpTestingController`) | `R10 - getRaceDayForm calls /api/race-day-form` |
| AC18.1–AC18.4 | review + existing suites | existing 001–010 tests pass with the stub |

## Out of scope
- Recommending or auto-adjusting a taper.
- Treating other races in the plan as race day.
- Penalising or reconstructing past skipped sessions.
- Predicting race time or performance.
- Editing the plan from this card.
- Any new Strava call, scope or stored data.

## Suggested follow-ups
- A footnote showing the learned rates ("easy 1.08 / min from 41 runs · SubT 2.21 / min from 14 runs") for transparency.
- A fact line "race-day form is inside / below / above the ready band".
- Excluding past race efforts (plan race days) from the easy-rate sample.
- Using HR zone minutes of workout reps (from histograms) instead of the reps × minutes split for the SubT rate.
- Disconnect / delete-my-data action (privacy item 18).

## Risks & decisions to confirm
1. **Race day's own session is excluded (AC5.4).** With the marathon's planned load counted, race-day fatigue jumps (≈ +34 for a 42 km day) and form turns negative, which says nothing about arriving fresh. The race-day values therefore describe the start line. Please confirm.
2. **Classification of "easy" and "SubT" runs reuses `RunClassifier` (010/001):** `NxMmin` workouts = SubT, everything else (easy and long runs) = easy. Races and unnamed hard runs are counted as easy and may raise the easy rate. Please confirm, or ask for race days to be excluded (follow-up).
3. **SubT rate = workout load minus the warm-up/cool-down share at the easy rate, divided by `reps × minutes` (AC4.2).** This matches the plan's split of easy and SubT minutes per day. Using the whole workout's load per minute would undercount SubT days. It depends on run names stating the reps (e.g. "3x10min").
4. **Rates are per moving minute** (Strava `moving_time`), the closest match to the plan's minutes. Runs without a stored moving time are left out of the sample.
5. **Easy target pace:** the 004 plan has an `easy` pace, so the intent assumption holds. If it is ever missing, distance-only days fall back to the average pace of the sampled easy runs (AC5.5) rather than being treated as rest.
6. **`MarkLineComponent` is not currently registered in `echarts-setup.ts`.** It is needed for the race-day marker. Registering it will also make 005's dashed zero line on Form appear for the first time (it was specified but never drawn). Please confirm that visible change is wanted.
7. **"Card hidden" in S8 is implemented as a card showing only its heading and a one-line message** naming the missing setting, so the reason is visible where the card would be. Please confirm (the alternative is no card at all and a bare text line).
8. **Today without a run differs from the 005 card for that one day:** 005 shows today's actual (0 so far), this card shows today's plan (S6).
9. **Tile rounding:** the tile shows integers, and the peak difference is computed from the rounded numbers so the label adds up (61 vs 65 → -4). The minus sign is ASCII `-`, as in 005, not the `−` in the intent example.
10. **Fallback uses 2×** (005's SubT weight ÷ easy weight), as the intent states; with mostly-easy-zone runs the easy rate is near 1.0, so this assumes SubT minutes are fully in the SubT HR zone.
