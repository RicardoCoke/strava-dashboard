# Spec: Normalized VO2max (hill- and drift-adjusted)

**Feature ID:** 016 · **Status:** Approved · **Intent:** [intent.md](./intent.md) · **Date:** 2026-11-01

## Summary
The detail call the sync already makes (`GET /activities/{id}`) also stores the run's per-km splits (`splits_metric`): distance, moving time, elevation difference, average heart rate and Strava's grade-adjusted speed. Stored outdoor runs with heart rate whose detail came from before this feature get one more detail call, under the existing 009 budget.

A new read-only endpoint, `GET /api/vo2max/normalized`, computes a second estimate from the store. It keeps 008's method (Daniels oxygen cost ÷ Swain %VO2max from %HRmax, same max HR), with three changes:
- Outdoor easy and long runs are scored on grade-adjusted pace, using only their first 60 minutes. Runs shorter than 60 minutes use the whole run.
- Each run counts once in a mean (no duration weighting).
- Each week also carries the original 008 value, for a faint comparison line.

The "VO2max" card gets two tabs: "Vo2" (the 008 view, unchanged, the default) and "Normalized Vo2" (same layout, its own value, tiles and chart, plus a short note).

## Requirements
| ID | Requirement | Priority (Must/Should/Could) |
|---|---|---|
| R1 | Sync: the detail call also stores the per-km splits of each run. Stored outdoor runs with heart rate whose detail predates this feature get one detail call again, under the shared budget, and count as pending until then | Must |
| R2 | Grade-adjusted speed per split: Strava's grade-adjusted speed, else a correction from the split's elevation difference, else plain speed | Must |
| R3 | Normalized segment per run: outdoor easy/long runs use the first 60 min of their splits (the whole run if shorter), at grade-adjusted pace. Workouts, treadmill runs and distance-rep runs follow 008 unchanged | Must |
| R4 | Per-segment VO2max uses 008's R5 formula and the same configured max HR | Must |
| R5 | Current value (last 7 days, else last 28 days) and weekly series (6 months, Mon–Sun) as plain means, one vote per run. Each week also carries the original 008 weekly value | Must |
| R6 | Race predictions (5K, 10K, half, marathon) from the normalized current value, with 008's VDOT method and marathon note | Must |
| R7 | `GET /api/vo2max/normalized` returns the exact JSON below, from the store only, with 008's auth and config errors | Must |
| R8 | The VO2max card has two tabs, "Vo2" (default) and "Normalized Vo2". Users can switch between them, and the normalized data loads the first time its tab opens | Must |
| R9 | The "Vo2" tab and `GET /api/vo2max` are unchanged | Must |
| R10 | The "Normalized Vo2" tab shows current value and caption, four tiles, chart, footnote and the "hill- and drift-adjusted" note, laid out like the "Vo2" tab | Must |
| R11 | The normalized chart is a weekly line with gaps for empty weeks, plus the original Vo2 series as a faint comparison line | Must |
| R12 | Normalized tab states: loading, invalid config, error, empty (with a still-importing variant), no current estimate, normal | Must |
| R13 | No regression elsewhere: no new scope, no new Strava endpoint, read endpoints never call Strava, other cards unchanged | Must |

## Acceptance criteria

**Conventions.** Same as 008:
- `today = LocalDate.now(clock.withZone(zone))`. A run's date is the local date of `start_date_local` (`RunActivity.localDate`). Weeks run Monday to Sunday.
- "Run" means a stored activity with `sport_type == "Run"`. "Outdoor" means `trainer = false`. Classification uses `RunClassifier.classify` (unchanged).
- Examples use `today = 2026-11-01` (Sunday) and max HR 194 unless stated otherwise.
- All fixture numbers are invented. Values are unrounded in the domain and rounded half-up to 1 decimal only in the API.

### R1 — Splits from the detail call
`Split(index: Int, distance: Meters, movingTime: Seconds, elevationDifference: Meters?, averageHeartRateBpm: Double?, gradeAdjustedSpeed: MetersPerSecond?)`. `RunActivity.splits: List<Split>?`. `null` means the detail was stored before this feature; an empty list means the detail had no splits.
- **AC1.1** Given a detail response with `splits_metric` entries (`split`, `distance`, `moving_time`, `elevation_difference`, `average_heartrate`, `average_grade_adjusted_speed`), when it is saved, then one `run_splits` row is stored per entry with those values (each optional field null when absent), and `runs.splits_saved = true`.
- **AC1.2** Given a detail response without `splits_metric` (e.g. a manual run), then no split rows are stored, `runs.splits_saved = true`, and the run reads back with `splits = []`.
- **AC1.3** Given a run whose detail is saved again (7-day recheck or refetch), then its split rows are replaced, not appended.
- **AC1.4** `upsertSummaries` never writes `splits_saved` or split rows. Only `saveDetail` does.
- **AC1.5 (one-time refetch)** `needsSplits(run) = run.averageHeartRateBpm != null && !run.trainer && run.detailFetchedAt != null && run.splits == null`. Given such a run, when a sync runs, then `MISSING_DETAILS` plans one detail call for it, in this order:
  1. runs with `detailFetchedAt == null`;
  2. `needsLapHeartRate` runs (008);
  3. `needsSplits` runs.

  Each group is newest first. A run in more than one group is planned once. All of these share `MAX_ACTIVITY_CALLS_PER_SYNC` and the 009 budget (80 per window).
- **AC1.6** Given a treadmill run, or a run without heart rate, with an old detail, then no extra detail call is planned for it.
- **AC1.7** Given runs that need splits, then `SyncPolicy.pendingCount` includes them. So a sync is due straight away while budget remains (009 AC3.3), and the backlog fills in without user action.
- **AC1.8 (migration)** Given a DB file without `runs.splits_saved` or `run_splits`, when the app starts, then:
  - `splits_saved` is added as a nullable column (`addColumnIfMissing`);
  - `run_splits` is created;
  - existing rows are kept, and existing runs read back with `splits = null`.
- **AC1.9 (deletion)** Given a run is deleted (404), pruned or removed by `deleteAll`, then its `run_splits` rows are deleted too.
- **AC1.10 (errors)** 429, 401, 5xx/network failures and 404 on these detail calls behave exactly like the existing detail calls (008 AC2.8, 009 R5):
  - 429 stops the sync and saves `rateLimitedUntil`;
  - 401 refreshes once, then ends with `ReauthRequired`;
  - 5xx or a network failure gives `stravaUnavailable`;
  - 404 deletes the run.

  Nothing is written for the failed run.

### R2 — Grade-adjusted speed per split (`GradeAdjustment.equivalentSpeed(split): MetersPerSecond?`)
`plainSpeed = distance / movingTime` (m/s). `MAX_FALLBACK_GRADE = 0.10`. Minetti's energy cost of running at grade `i` is `C(i) = 155.4·i⁵ − 30.4·i⁴ − 43.3·i³ + 46.3·i² + 19.5·i + 3.6` (J/kg/m), so `C(0) = 3.6`.

Rules, first match wins:
1. `movingTime <= 0` or `distance <= 0` → `null`. The split is unusable.
2. `gradeAdjustedSpeed != null && gradeAdjustedSpeed > 0` → Strava's value.
3. `elevationDifference != null` → `plainSpeed × C(i) / C(0)`, with `i = clamp(elevationDifference / distance, −0.10, 0.10)`.
4. Otherwise → `plainSpeed` (decision 3: scored at plain pace, not skipped).

- **AC2.1** Given a 1000 m / 300 s split with `gradeAdjustedSpeed = 3.5`, then the result is 3.5 m/s, whatever `elevationDifference` says.
- **AC2.2** Given a 1000 m / 300 s split, no grade-adjusted speed and `elevationDifference = +20` (grade 0.02), then the result is `3.3333 × 1.11338 = 3.7113` m/s (equivalent pace 269.4 s/km).
- **AC2.3** Given the same split with `elevationDifference = −50` (grade −0.05), then the factor is `0.76276` and the result is 2.5425 m/s.
- **AC2.4** Given `elevationDifference = +150` on 1000 m (grade 0.15), then the grade is clamped to 0.10, so the result equals the result for `+100`.
- **AC2.5** Given `gradeAdjustedSpeed = 0.0` or `null` and `elevationDifference = null`, then the result is the plain speed (3.3333 m/s for 1000 m / 300 s).
- **AC2.6** Given `movingTime = 0` or `distance = 0`, then the result is `null`.

### R3 — Normalized segment (`NormalizedSegmentExtractor.segmentOf(run): Segment?`)
`EARLY_PORTION_SECONDS = 3600`. 008 constants are reused: `MIN_RUN_MOVING_SECONDS = 1200`, pace limits 120–720 s/km.

Rules, applied top to bottom; the first matching row decides:

| # | Run | Result |
|---|---|---|
| 1 | Name matches `DistanceRepPattern` | Excluded (as 008) |
| 2 | `movingTime == null` (008 refetch pending) | Not yet; pending |
| 3 | `RunClassifier.classify(run)` is a `Workout`, or `trainer = true` | Exactly `SegmentExtractor.segmentOf(run)` (008 rows 3–8: rep laps, treadmill pace line, or excluded) |
| 4 | Outdoor `Easy`/`LongRun` failing 008's whole-run checks (`averageHeartRateBpm == null`, `movingTime < 1200 s`, or `distance <= 0`) | Excluded |
| 5 | Outdoor `Easy`/`LongRun` with `splits == null` | Not yet; pending (AC7.6) |
| 6 | Outdoor `Easy`/`LongRun` with `splits` empty, or no usable split piece with HR (below) | Exactly `SegmentExtractor.segmentOf(run)` (008 whole run, plain pace) |
| 7 | Outdoor `Easy`/`LongRun` with splits | Early-split segment (below) |

**Early-split segment.** Walk the splits in `index` order, skipping splits where `equivalentSpeed` is `null`. Keep `before` = the summed `movingTime` of the splits already walked, and stop when `before >= 3600`. Each walked split contributes a piece:
- `t = min(movingTime, 3600 − before)`, so the split that crosses 60 minutes is prorated;
- `g = equivalentSpeed(split)`;
- `hr = averageHeartRateBpm`.

Pieces with `hr == null` or `hr <= 0` are dropped. From the remaining pieces:
- `duration = Σt`;
- `pace = Σt / (Σ(g × t) / 1000)` in s/km;
- `heartRate = Σ(hr × t) / Σt`.

The run is excluded if `duration < 1200 s` or `pace` is outside 120–720 s/km. Otherwise the segment is `Segment(localDate, pace, heartRate, Seconds(duration), SegmentMethod.EARLY_SPLITS)`.

- **AC3.1 (drift, S5)** Given an outdoor long run with 30 splits of 1000 m / 330 s each (moving time 9900 s, grade-adjusted speed 3.0303 m/s each), where splits 1–11 have HR 145 and splits 12–30 have HR 160:
  - The segment covers splits 1–10 plus 300 s of split 11, so it has duration 3600 s, pace 330.0 s/km and HR 145.0.
  - Its VO2max (R4) is 54.2.
  - The same run in the original 008 estimate (whole-run HR 154.5) scores 48.0.
- **AC3.2 (S5)** Given an outdoor easy run of 10 splits of 1000 m / 330 s with HR 145 and the same grade-adjusted speed, then its segment is pace 330.0, HR 145.0 and duration 3300 s, and its VO2max is 54.2, equal to AC3.1.
- **AC3.3 (hills, S4)** Given two outdoor 15 km easy runs, both HR 150:
  - a flat one with 15 splits of 1000 m / 300 s and grade-adjusted speed 3.3333 m/s;
  - a hilly one with 15 splits of 1000 m / 315 s and grade-adjusted speed 3.3333 m/s.

  Then both segments have pace 300.0 s/km and VO2max 57.2. The original 008 estimate gives 57.2 and 53.8.
- **AC3.4 (shorter than cut-off, decision 5)** Given an outdoor run with splits summing to 2700 s, then every usable split is used in full (duration 2700 s).
- **AC3.5 (exactly at cut-off)** Given 12 splits of 300 s, then splits 1–12 are used in full (3600 s) and nothing after.
- **AC3.6 (no grade data, decision 3)** Given an outdoor run whose splits have no grade-adjusted speed and no elevation difference, then it is scored at plain split pace over the early portion, not skipped.
- **AC3.7 (no splits)** Given an outdoor easy run with HR, 3000 s moving time and `splits = []` (e.g. manual run with HR), then its segment equals the 008 whole-run segment (pace = moving time / distance, whole-run HR, duration = moving time).
- **AC3.8 (split HR missing)** Given an outdoor run whose early splits all lack `average_heartrate`, then rule 6 applies (008 whole-run segment). Given only splits 3 and 4 lacking HR, then those pieces are dropped and the rest are used.
- **AC3.9 (workouts and treadmill unchanged)** Given an outdoor `5x6min` workout, a treadmill "5x6min" run (008 AC4.4) and a treadmill easy run with pace line "5:30" lasting 5400 s, then each segment equals `SegmentExtractor.segmentOf(run)`. The treadmill easy run uses its whole 5400 s.
- **AC3.10** Given a run named "6x1km", then it is excluded. Given an outdoor easy run without HR, or with 1199 s moving time, then it is excluded.
- **AC3.11** Given early pieces that sum to less than 1200 s after HR-less pieces are dropped, or a resulting pace above 720 s/km, then the run is excluded.

### R4 — Per-segment VO2max
- **AC4.1** Each normalized segment is valued with `Vo2maxEstimator.estimate(segment, maxHr)` (008 R5, unchanged). This includes the 65 % HR floor and the 100 % HR cap.
- **AC4.2** Given `MAX_HR_BPM=185`, then both endpoints use 185. Given it unset, both use 194. No Strava call is needed after a change (values are computed per request).

### R5 — Current value and weekly series, one vote per run
`mean(values) = Σ vo2max / count`: plain mean, no duration weighting. The window, weeks, basis periods, `inProgress` and the 7-day / 28-day fallback are exactly 008 R6/R7.
- **AC5.1 (S6)** Given two qualifying runs in `last7` whose segment values are 50.0 (segment duration 3600 s) and 56.0 (1800 s), then `current.vo2max = 53.0` and `runCount = 2`. The 008 weighted mean would be 52.0.
- **AC5.2 (S6)** Given a week with a 10 800 s long run and a 1800 s easy run, then that week's `runCount = 2`, and its `vo2max` is the plain mean of their two values.
- **AC5.3** Given no segment in `last7` but some in `last28`, then `basis = "last28Days"`, `periodStart = 2026-10-05` and `periodEnd = 2026-11-01`. Given none in `last28`, then `current = null` and `predictions = []`.
- **AC5.4** Given today = 2026-11-01, then `windowStart = 2026-04-27`, `windowEnd = 2026-11-01`, and `weeks` has 27 ascending entries. Only the last one has `inProgress = true`.
- **AC5.5 (S8)** Given a week without a normalized segment, then `vo2max = null` and `runCount = 0`, never 0.0 and never interpolated.
- **AC5.6 (comparison, decision 2)** For every week, `originalVo2max` equals that week's unrounded 008 `vo2max` from `Vo2maxCalculator.compute` for the same runs, max HR and today, after the same rounding. It is `null` exactly when the 008 week is null.
- **AC5.7 (time zone)** Given a run with `start_date = 2026-10-25T23:30:00Z` and `start_date_local = 2026-10-26T00:30:00Z`, then it falls in the week of 2026-10-26 and in `last7`.

### R6 — Race predictions
- **AC6.1** Predictions use `DanielsFormulas.raceTime(roundOneDecimal(current.vo2max), meters)` for 5K, 10K, half and marathon, in that order. For vdot 50.0, that gives 1196 / 2480 / 5491 / 11 440 s (±3 s, as 008 AC8.1).
- **AC6.2** Only the marathon has a `note`, with exactly 008's text: "Marathon predictions from VO2max tend to be optimistic."
- **AC6.3** Given `current = null`, then `predictions = []`.

### R7 — API `GET /api/vo2max/normalized`
- **AC7.1** Given a connected session and a valid or default max HR, then `200` with exactly the fields in the Design section. There are no activity ids, names, descriptions, laps, splits or per-run values.
- **AC7.2** Given no session, or a session for another athlete, then `401 not_connected`.
- **AC7.3** Given `MAX_HR_BPM` is invalid, then `422 {"error":"vo2max_invalid_config","message":"MAX_HR_BPM is not a whole number between 120 and 230."}`.
- **AC7.4 (store only)** Given a connected session and a sync that is due, when the endpoint is called, then it answers from stored runs and the fake `ActivitySource` gets zero calls.
- **AC7.5** Rounding: `current.vo2max`, `weeks[].vo2max` and `weeks[].originalVo2max` are rounded half-up to 1 decimal. `timeSeconds` are whole numbers.
- **AC7.6** `runsPendingDetail` counts runs that meet all of these:
  - dated in `windowStart … today`;
  - `averageHeartRateBpm != null`;
  - the name doesn't match the distance-rep pattern;
  - `detailFetchedAt == null`, or `needsLapHeartRate(run)`, or `needsSplits(run)`.

### R8 — Tabs
- **AC8.1 (S1, decision 1)** Given the card renders, then under the `h2` "VO2max" there is a `role="tablist"` (`aria-label="VO2max estimate"`) with two `button role="tab"`: "Vo2" then "Normalized Vo2". "Vo2" has `aria-selected="true"` and its panel (`role="tabpanel"`, `aria-labelledby` = the tab's id) is shown.
- **AC8.2** Given the user clicks "Normalized Vo2", then:
  - that tab gets `aria-selected="true"` and the other gets `"false"`;
  - the normalized panel is shown and the Vo2 panel is not rendered.

  Clicking "Vo2" switches back. The tab bar is visible in every state of either panel.
- **AC8.3 (lazy load)** Given the page loads, then exactly one `GET /api/vo2max` is made, and no `GET /api/vo2max/normalized`. Given the user opens "Normalized Vo2" the first time, then one `GET /api/vo2max/normalized` is made. Switching away and back makes no further request.
- **AC8.4 (quiet refresh)** Given `SyncStore.dataVersion` increments, then the Vo2 data reloads quietly (as now). The normalized data also reloads quietly if it has been loaded before; otherwise no normalized request is made.
- **AC8.5** Given the page is reloaded, then "Vo2" is selected again. The tab choice is not stored anywhere (no `localStorage`/`sessionStorage`).
- **AC8.6** The tabs use `--color-*` tokens only: the active tab is marked with the existing accent token, and no new colour values are added. At 375 px both tabs fit on one row without horizontal scroll.

### R9 — "Vo2" tab and `/api/vo2max` unchanged (S2)
- **AC9.1** Given the same stored runs (with or without splits), then `GET /api/vo2max` returns byte-identical JSON before and after this feature. `Vo2maxCalculator` and `SegmentExtractor` don't read `splits`.
- **AC9.2** The existing `Vo2maxRoutesTest`, `Vo2maxCalculatorTest`, `SegmentExtractorTest`, `Vo2maxFormulasTest`, `vo2max.options.spec.ts` and `vo2max.service.spec.ts` pass without changes to their expectations.
- **AC9.3** Given the "Vo2" tab is selected, then its panel shows the same states, texts, current value, caption, tiles, chart (`buildVo2maxOptions`, unchanged) and footnote as 008 R11–R13. The existing `vo2max-section.spec.ts` expectations pass, with selectors scoped to the Vo2 panel where needed.

### R10 — "Normalized Vo2" tab content (S3, S7)
- **AC10.1 (S7)** Given the normalized panel in any state, then it starts with a muted note: "Hill- and drift-adjusted: grade-adjusted pace, only the first 60 minutes of easy and long runs, and each run counts once."
- **AC10.2 (S3)** Given `current` is set, then the panel shows these, in the same elements and classes as the Vo2 tab:
  - the label "Normalized VO2max", the value (e.g. "54.2") and the unit "ml/kg/min";
  - the caption from `basisCaption` (e.g. "Based on 4 runs in the last 7 days (26 Oct – 1 Nov)");
  - `<app-race-prediction-tiles>` with the normalized predictions (marathon note included);
  - the normalized chart (R11);
  - the footnote.
- **AC10.3** The footnote shows the same max-HR line as the Vo2 tab ("Max HR 194 bpm (estimate; set MAX_HR_BPM to change)" or "Max HR 190 bpm (from MAX_HR_BPM)"). Given `runsPendingDetail > 0`, it adds "12 runs still importing split data; numbers will update." ("1 run" singular).

### R11 — Normalized chart (`buildNormalizedVo2maxOptions(weeks): EChartsOption`)
- **AC11.1** It builds a category x axis of `weekStart` ("27 Apr") and a value y axis "VO2max (ml/kg/min)" with `scale: true`, like 008 AC11.1.
- **AC11.2** Series 1, "Normalized Vo2":
  - `palette.accent`, `showSymbol: true`, `connectNulls: false`;
  - data = `weeks[].vo2max`, with `null` for empty weeks (S8: the line breaks and no point is drawn).
- **AC11.3 (decision 2)** Series 2, "Vo2":
  - `palette.textMuted`, line width 1, `lineStyle.opacity` 0.5, `showSymbol: false`, `connectNulls: false`;
  - data = `weeks[].originalVo2max`;
  - drawn beneath series 1 (lower `z`).
- **AC11.4** A legend lists "Normalized Vo2" and "Vo2" in `palette.textMuted` text.
- **AC11.5** The tooltip (`trigger: 'axis'`) shows these lines, built only from dates and numbers:
  - "Week of 27 Apr", with " (so far)" for the in-progress week;
  - "Normalized VO2max 54.2 · 3 runs", or "No qualifying runs";
  - "Vo2 50.1", or "Vo2: no qualifying runs".
- **AC11.6** The `aria-label` is "Normalized VO2max per week, last 6 months".

### R12 — Normalized tab states
- **AC12.1 (loading)** Given the request is pending, then "Loading normalized VO2max…" in `role="status"`, and no chart.
- **AC12.2 (invalid config)** Given `422 vo2max_invalid_config` with message `M`, then the same text and hint as 008 AC13.2 ("VO2max settings have a problem: M" + the `MAX_HR_BPM` hint). No Retry.
- **AC12.3 (error)** Given a 500, a network error or a body that fails `isNormalizedVo2maxResponse`, then "Couldn't load normalized VO2max." in `role="alert"` with a Retry button. Retry sets loading and requests `/api/vo2max/normalized` again (not `/api/vo2max`).
- **AC12.4 (401)** Given 401, then the shared interceptor redirects and the panel shows no error text.
- **AC12.5 (empty)** Given `current == null`, every week `vo2max == null` and `runsPendingDetail = 0`, then "No qualifying runs in the last 6 months" plus 008's hint. No tiles, no chart.
- **AC12.6 (empty, importing)** Given the same but `runsPendingDetail > 0`, then "Split data is still importing: 12 runs to go." No tiles, no chart.
- **AC12.7 (no current)** Given `current == null` and at least one week with a normalized value, then "No qualifying runs in the last 4 weeks, so there is no current estimate or race prediction.", then the chart and the footnote.
- **AC12.8 (normal)** Given `current != null`, then: note → current value and caption → tiles → chart → footnote.
- **AC12.9 (independence)** Given `/api/vo2max/normalized` fails, then the Vo2 tab still shows its own data, and vice versa.

### R13 — No regression
- **AC13.1** `REQUIRED_SCOPE` stays `activity:read_all`. No new Strava endpoint or stream key is requested. The detail request URL is unchanged.
- **AC13.2** For the same stored data, the outputs of 001 (rep paces, classification), 005, 006, 007, 013 and 014 are unchanged with the new `splits` field present.
- **AC13.3** The dashboard page specs pass unchanged (they stub `Vo2maxSection`).
- **AC13.4** The backend and frontend check commands pass.

## Traceability
| Success criterion (intent) | Covered by |
|---|---|
| S1 Two tabs "Vo2" / "Normalized Vo2", switchable | R8 (AC8.1, AC8.2) |
| S2 Vo2 tab exactly as before | R9 (AC9.1–AC9.3), R8 (AC8.3) |
| S3 Normalized tab: own current value, weekly series, 4 predictions, same layout | R5, R6, R7, R10 (AC10.2), R12 |
| S4 Hilly vs flat at same effort ≈ same score | R2, R3 (AC3.3, AC3.6) |
| S5 Strong-drift long run judged on early portion, no longer far below easy runs | R1, R3 (AC3.1, AC3.2) |
| S6 3-hour long run = one vote | R5 (AC5.1, AC5.2) |
| S7 Note "hill- and drift-adjusted" | R10 (AC10.1) |
| S8 Weeks without data distinguishable | R5 (AC5.5), R11 (AC11.2, AC11.5), R12 (AC12.7) |
| Constraint: GAP where available, else per-km elevation correction | R2 |
| Constraint: same configured max HR | R4 (AC4.2) |
| Constraint: metric, Mon–Sun, 6 months | R5 (AC5.4) |
| Decision 1 (Vo2 default) | R8 (AC8.1, AC8.5) |
| Decision 2 (faint original line) | R5 (AC5.6), R11 (AC11.3) |
| Decision 3 (no grade data → plain pace) | R2 (AC2.5), R3 (AC3.6, AC3.7) |
| Decision 4 (fixed 60-min cut-off) | R3 (AC3.1, AC3.5) |
| Decision 5 (shorter runs use whole run) | R3 (AC3.2, AC3.4) |
| Non-goals (008 untouched, no heat, treadmill unchanged, no new scopes, no drill-down) | R9, R3 (AC3.9), R13 (AC13.1), R7 (AC7.1) |
| Related 009 (no Strava calls on dashboard open, shared budget) | R1 (AC1.5, AC1.7), R7 (AC7.4) |

## Design

### Data from Strava
- **Endpoints:** no new endpoint.
  - `GET /activities/{id}?include_all_efforts=false`: the request is unchanged, and `splits_metric` is now also read from the response. It is called once more for stored outdoor runs with HR whose detail predates this feature (AC1.5).
  - `GET /athlete/activities` and the stream endpoints are unchanged.
- **Scopes:** no change (`activity:read_all`).
- **Fields used (new):** `splits_metric[]`:
  - `split` (int, 1-based);
  - `distance` (m);
  - `moving_time` (s);
  - `elevation_difference` (m, optional);
  - `average_heartrate` (bpm, optional);
  - `average_grade_adjusted_speed` (m/s, optional).

  `elapsed_time`, `average_speed` and `pace_zone` are not read. `DetailedActivityDto` gains `@SerialName("splits_metric") val splitsMetric: List<SplitDto> = emptyList()`.
- **Sync & caching:** sync is triggered only by the 009 background sync (`POST /api/sync`).
  - Splits ride on detail calls that are already made. The one-time refetch is a third group in `MISSING_DETAILS` (AC1.5), within the 80-per-window budget.
  - Rollout cost: about 150–200 detail calls (stored outdoor runs with HR, 6 months + 41 days), spread over 2–3 windows (~30–45 min). After that, a new run costs nothing extra.
  - All values are computed per request from the store.
- **Pagination:** unchanged.
- **Errors:** 401 → one refresh, then `ReauthRequired` and tokens deleted. 429 → the sync stops and `rateLimitedUntil` is saved. 5xx/network → `stravaUnavailable`. 404 → the run is deleted (AC1.10). The read endpoint never calls Strava, so it always serves stored data.

### Domain model & logic
- `domain/model/RunActivity.kt`: new `data class Split` (R1). `RunActivity` gains `val splits: List<Split>? = null`.
- `domain/ports/ActivitySource.kt`: `ActivityDetail` gains `val splits: List<Split> = emptyList()`.
- `domain/SyncPolicy.kt`: `needsSplits(run)`; `MISSING_DETAILS` = the three groups, `distinctBy { it.id }`; `pendingCount` ORs `needsSplits`.
- `domain/vo2max/Vo2maxModels.kt`: `SegmentMethod` gains `EARLY_SPLITS` (additive; no exhaustive `when` uses it). Nothing else in 008 code changes.
- New package `domain/vo2max/normalized/` (pure, unit-tested):
  - `GradeAdjustment.kt`: `equivalentSpeed(split): MetersPerSecond?`, `minettiCost(grade)`, `MAX_FALLBACK_GRADE = 0.10`, `FLAT_COST = 3.6` (R2).
  - `NormalizedSegmentExtractor.kt`: `segmentOf(run): Segment?` (R3), `EARLY_PORTION_SECONDS = 3600`. It delegates to `SegmentExtractor` for rows 3 and 6.
  - `NormalizedVo2maxModels.kt`: `NormalizedWeek(weekStart, weekEnd, vo2max: Double?, runCount, inProgress, originalVo2max: Double?)` and `NormalizedVo2maxReport(maxHr, maxHrConfigured, current: CurrentVo2max?, predictions, weeks: List<NormalizedWeek>, windowStart, windowEnd, runsPendingDetail)`. `CurrentVo2max`, `RacePrediction` and `RaceDistance` from 008 are reused.
  - `NormalizedVo2maxCalculator.kt`: `compute(runs, maxHr, maxHrConfigured, today): NormalizedVo2maxReport`. It uses a plain `mean`, 008's period and window rules, and `Vo2maxCalculator.compute(...)` for `originalVo2max`. The window and week rules may be extracted into a shared internal helper, as long as AC9.2 holds.
  - `NormalizedVo2maxService.kt`: `(maxHr: MaxHrSetting, tokens, runs, clock, zone)`, with `suspend fun get(): NormalizedVo2maxResult` (`NotConnected | InvalidConfig | Ready(report)`). It mirrors `Vo2maxService` and reads from the store only.
- **Units:**
  - Split distance is in m, time in s and speed in m/s.
  - Segment pace is s/km (`Double`, unrounded). Conversion to m/min happens only in `DanielsFormulas`.
  - Grades are unitless fractions.
  - Rounding happens only in the API mapper.
- **Time zones:** `LocalDate` from `start_date_local` only. Split times are relative seconds, so DST is irrelevant.

### Persistence
- `runs` gains `splits_saved` BOOLEAN, nullable (`RunsTable.splitsSaved`). `null` = detail from before 016. Only `saveDetail` sets it to `true`.
- New table `run_splits` (`RunSplitsTable`):
  - `run_id` INTEGER, FK → `runs.id`, `ON DELETE CASCADE`;
  - `split_index` INTEGER;
  - `distance_m` REAL;
  - `moving_time_s` INTEGER;
  - `elevation_difference_m` REAL NULL;
  - `average_heartrate` REAL NULL;
  - `grade_adjusted_speed_mps` REAL NULL;
  - PK (`run_id`, `split_index`).
- `DatabaseFactory.connect`: `run_splits` is added to `SchemaUtils.create`, plus `addColumnIfMissing(runs, splits_saved, "BOOLEAN")`.
- `ExposedRunRepository`:
  - `saveDetail` deletes and re-inserts the splits and sets `splitsSaved = true`;
  - `findAll` groups the splits per run, giving `null` when `splitsSaved` is null;
  - `deleteRunsWhere` and `deleteAll` delete `run_splits` rows explicitly.

  `RunRowMappers` gains `toSplit()`, and `toRun` gains a `splits` argument.
- Retention: splits follow their run (6 months + 41 days prune).

### Backend API
#### `GET /api/vo2max/normalized`
- Query params: none. Auth: `hasConnectedSession(tokens)`, else `401 not_connected`. Read-only, store only.
- `200` response (exact shape). The values are invented and `weeks` is shortened here; the real list has 27 entries for today = 2026-11-01:
```json
{
  "maxHr": { "bpm": 194, "source": "default" },
  "current": {
    "vo2max": 54.2,
    "basis": "last7Days",
    "periodStart": "2026-10-26",
    "periodEnd": "2026-11-01",
    "runCount": 4
  },
  "predictions": [
    { "key": "5k", "label": "5K", "timeSeconds": 1117, "note": null },
    { "key": "10k", "label": "10K", "timeSeconds": 2318, "note": null },
    { "key": "half", "label": "Half marathon", "timeSeconds": 5134, "note": null },
    { "key": "marathon", "label": "Marathon", "timeSeconds": 10701, "note": "Marathon predictions from VO2max tend to be optimistic." }
  ],
  "windowStart": "2026-04-27",
  "windowEnd": "2026-11-01",
  "weeks": [
    { "weekStart": "2026-04-27", "weekEnd": "2026-05-03", "vo2max": 53.1, "runCount": 3, "inProgress": false, "originalVo2max": 51.8 },
    { "weekStart": "2026-05-04", "weekEnd": "2026-05-10", "vo2max": null, "runCount": 0, "inProgress": false, "originalVo2max": null },
    { "weekStart": "2026-10-26", "weekEnd": "2026-11-01", "vo2max": 54.6, "runCount": 4, "inProgress": true, "originalVo2max": 50.2 }
  ],
  "runsPendingDetail": 0
}
```
- **Field rules:**
  - The top-level fields `maxHr`, `current`, `predictions`, `windowStart`, `windowEnd` and `runsPendingDetail` have exactly the types and rules of `GET /api/vo2max` (008 Design).
  - `weeks[]` has the 008 fields (`weekStart`, `weekEnd` as `YYYY-MM-DD`; `vo2max` number with 1 decimal or `null`; `runCount` int ≥ 0; `inProgress` boolean) plus `originalVo2max` (number with 1 decimal, or `null`; ml/kg/min).
  - `vo2max` is `null` exactly when `runCount == 0`.
  - `runCount` counts runs (one per run), not seconds.
- **Errors** (`{"error","message"}`, existing constants):

| Status | `error` | `message` |
|---|---|---|
| 401 | `not_connected` | existing text |
| 422 | `vo2max_invalid_config` | "MAX_HR_BPM is not a whole number between 120 and 230." |
| 500 | `internal_error` | existing StatusPages handler |

- **Files:**
  - `api/NormalizedVo2maxDtos.kt`: `NormalizedVo2maxResponse` and `NormalizedWeekDto`; it reuses `MaxHrDto`, `CurrentVo2maxDto` and `RacePredictionDto`.
  - `api/NormalizedVo2maxMappers.kt`: reuses the 008 private mappers, which are made `internal` if needed, with identical output.
  - `api/NormalizedVo2maxRoutes.kt`: `fun Route.normalizedVo2maxRoutes(tokens, service)`.
  - `Application.kt`: wiring.

### Frontend (Angular)
- **Route / page:** unchanged. The tabs live inside `Vo2maxSection`, which keeps its place after the Training load card.
- **Service & interfaces (`core/api/`):**
  ```ts
  // normalized-vo2max.models.ts
  import { CurrentVo2max, MaxHr, RacePrediction, WeekVo2max } from './vo2max.models';
  export interface NormalizedWeekVo2max extends WeekVo2max { originalVo2max: number | null; }
  export interface NormalizedVo2maxResponse {
    maxHr: MaxHr; current: CurrentVo2max | null; predictions: RacePrediction[];
    windowStart: string; windowEnd: string; weeks: NormalizedWeekVo2max[]; runsPendingDetail: number;
  }
  export function isNormalizedVo2maxResponse(value: unknown): value is NormalizedVo2maxResponse;
  // = the 008 checks + every week has originalVo2max number | null
  ```
  `vo2max.service.ts` gains `getNormalizedVo2max(): Observable<unknown>` → `GET /api/vo2max/normalized`. `getVo2max` and `vo2max.models.ts` are unchanged.
- **Components (`features/vo2max/`, standalone, `OnPush`, interpolation only):**
  - `vo2max-section.ts/.html/.scss` (container):
    - It adds `activeTab = signal<'vo2' | 'normalized'>('vo2')` and `normalizedState = signal<NormalizedVo2maxState>({ kind: 'idle' })`, with states `idle | loading | invalidConfig | error | loaded`.
    - It has `selectTab(tab)`, which loads the normalized data on the first switch, and `retryNormalized()`.
    - The quiet reload on `dataVersion` (AC8.4) covers both datasets.
    - The template has the tab bar, the existing Vo2 markup unchanged inside the Vo2 tabpanel, and `<app-normalized-vo2max-panel>` inside the normalized tabpanel.
  - `normalized-vo2max-panel/normalized-vo2max-panel.ts/.html` (presentational, new):
    - Inputs: `state = input.required<NormalizedVo2maxState>()`. Output: `retry = output<void>()`.
    - It renders R10/R12 and reuses `RacePredictionTiles`, `basisCaption`, `emptyKind` (the response is assignable to `Vo2maxResponse`) and `formatOneDecimal`.
  - `normalized-vo2max-chart/normalized-vo2max-chart.ts` (new): `weeks = input.required<NormalizedWeekVo2max[]>()`, wrapping `<app-chart>` with `buildNormalizedVo2maxOptions`.
  - `normalized-vo2max.options.ts` (new): `buildNormalizedVo2maxOptions` and `normalizedWeekTooltipLines` (R11).
  - `vo2max.view.ts`: adds `NormalizedVo2maxState`, `normalizedStateFromError`, `normalizedFootnoteText` and `NORMALIZED_NOTE`. Existing exports are unchanged.
- **States:**

| View | Loading | Empty | Error | Normal |
|---|---|---|---|---|
| Tab bar | always shown | always shown | always shown | always shown |
| Vo2 panel | as 008 AC13.1 | as 008 AC13.4/13.5 | as 008 AC13.2/13.3 | as 008 AC13.6/13.7 |
| Normalized panel | note + "Loading normalized VO2max…" (`role="status"`) | note + AC12.5 or AC12.6; no tiles or chart | note + AC12.2 (no Retry) or AC12.3 (Retry) | note → value + caption → tiles → chart → footnote; or AC12.7 |
| Normalized chart | not rendered | not rendered | not rendered | two lines, gaps for null weeks, legend, tooltip |

- **Chart:** a line chart. x = week start ("27 Apr"), y = "VO2max (ml/kg/min)" (`scale: true`). The main series is in `palette.accent`; the faint "Vo2" comparison is in `palette.textMuted` at opacity 0.5. It uses the shared 300 px chart height and the shared theme helpers.

### Files expected to change
Backend (`backend/src/main/kotlin/app/stravadashboard/`):
- `strava/StravaDtos.kt` (`SplitDto`, `DetailedActivityDto.splitsMetric`), `strava/StravaMappers.kt` (`toDetail` maps splits)
- `domain/model/RunActivity.kt` (`Split`, `RunActivity.splits`), `domain/ports/ActivitySource.kt` (`ActivityDetail.splits`)
- `domain/SyncPolicy.kt` (`needsSplits`, plan groups, `pendingCount`)
- `domain/vo2max/Vo2maxModels.kt` (`SegmentMethod.EARLY_SPLITS` only)
- New: `domain/vo2max/normalized/GradeAdjustment.kt`, `NormalizedSegmentExtractor.kt`, `NormalizedVo2maxModels.kt`, `NormalizedVo2maxCalculator.kt`, `NormalizedVo2maxService.kt`
- `persistence/Tables.kt` (`RunsTable.splitsSaved`, `RunSplitsTable`), `persistence/DatabaseFactory.kt`, `persistence/ExposedRunRepository.kt`, `persistence/RunRowMappers.kt`
- New: `api/NormalizedVo2maxDtos.kt`, `api/NormalizedVo2maxMappers.kt`, `api/NormalizedVo2maxRoutes.kt`; `api/Vo2maxMappers.kt` (visibility only, if reused)
- `Application.kt` (service + route)
- Tests:
  - new `domain/vo2max/normalized/GradeAdjustmentTest.kt`, `NormalizedSegmentExtractorTest.kt`, `NormalizedVo2maxCalculatorTest.kt`, `api/NormalizedVo2maxRoutesTest.kt`;
  - extend `domain/SyncPolicyTest.kt`, `domain/SyncServiceTest.kt`, `strava/StravaClientTest.kt`, `persistence/ExposedRunRepositoryTest.kt`;
  - `testsupport/TestData.kt` (default `splits` so existing tests don't see runs as pending) and `testsupport/Fakes.kt`.
- Fixture: `src/test/resources/strava/activity-detail.json` gains `splits_metric` (invented values)

Frontend (`frontend/src/app/`):
- New: `core/api/normalized-vo2max.models.ts` (+ spec for the guard)
- `core/api/vo2max.service.ts` (+ spec): `getNormalizedVo2max`
- `features/vo2max/vo2max-section.ts`, `.html`, `vo2max.scss`, `vo2max-section.spec.ts` (tabs, scoped selectors)
- New: `features/vo2max/normalized-vo2max-panel/*`, `features/vo2max/normalized-vo2max-chart/*`, `features/vo2max/normalized-vo2max.options.ts` (+ specs)
- `features/vo2max/vo2max.view.ts` (+ spec): additions only
- `testing/vo2max-fixtures.ts`: normalized fixtures

## Edge cases
| Case | Expected behaviour |
|---|---|
| No activities in range | Both tabs show the empty state. Normalized: AC12.5. |
| No qualifying run in the last 28 days, older ones exist | Normalized: AC12.7, chart only. |
| Manual activity (no GPS) | No `splits_metric` → `splits = []` → the 008 whole-run segment if it has HR, ≥ 20 min and distance (AC3.7). Usually no HR → excluded. No GPS is read. |
| Activity without heart rate | Excluded. No refetch planned (AC1.6). |
| Splits present but without HR | Pieces without HR are dropped. If none remain → 008 whole-run segment (AC3.8). |
| Splits without grade-adjusted speed | Elevation correction, else plain pace (R2, decision 3). |
| GPS elevation spike (e.g. +150 m in 1 km) | Grade clamped to ±10 % (AC2.4). Strava GAP, when present, is used as is. |
| Auto-pause / stops | Split `moving_time` is used, so stops don't count toward the 60 minutes. |
| Split that crosses 60 min | Prorated by time (AC3.1). |
| Run shorter than 60 min | Whole run (AC3.4). |
| Easy run 60–90 min | Also cut at 60 min (constraint applies to easy and long runs). |
| Long run with a fast finish or embedded MP block | Only the first 60 min count. If the name matches a distance-rep pattern (e.g. "3x5km"), it is excluded as in 008. |
| Treadmill run (any length) | 008 handling, whole run with the pace-line pace (AC3.9, non-goal). |
| Outdoor interval workout | 008 rep laps, plain lap pace, no grade adjustment (AC3.9). |
| Detail stored before 016 | Pending: not counted yet, shown in `runsPendingDetail` (AC7.6) → footnote or importing state. |
| HR above max / very low HR | 008 cap and 65 % floor (AC4.1). |
| Unit conversion | Metres and seconds → m/s in `GradeAdjustment`, s/km in the extractor, m/min in `DanielsFormulas`. The API sends ml/kg/min and whole seconds. The frontend only formats. |
| Week spans DST change / time zones | Dates from `start_date_local`; `LocalDate` maths only (AC5.7). |
| Today is Monday | The current week holds only today's runs and is `inProgress` (as 008). |
| `MAX_HR_BPM` invalid | 422 in both tabs. The rest of the page works. |
| Strava 429 during the refetch | The sync stops and the remaining runs stay pending until the next window (AC1.10). |
| Expired / revoked token | Refresh once. If revoked → `ReauthRequired` → the next request gets 401 → `/connect`. |
| Normalized request fails, Vo2 succeeds | Each tab shows its own state (AC12.9). |

## Privacy
- **Data fields & sensitivity:**
  - Per-km split average heart rate: **health data (GDPR Art. 9)**, newly stored.
  - Per-km elevation difference, grade-adjusted speed, moving time and distance: activity data. Elevation difference is a relative number per km, not a coordinate.
  - Derived normalized VO2max and race predictions: derived health data, computed per request, not stored.
  - `MAX_HR_BPM`: unchanged handling (008).
  - No `latlng`, polylines, altitude streams or other streams are requested or stored.
- **Storage & retention:** `run_splits` and `runs.splits_saved` live in the existing SQLite DB at `DB_PATH`. They are pruned with their run (6 months + 41 days) and deleted by `delete`/`deleteAll`. No browser storage, and the tab choice is not persisted.
- **Scopes:** no change (`activity:read_all`).
- **Applicable privacy-check items:**

| # | Status | How it's satisfied |
|---|---|---|
| 1 | ✅ | No new secrets; the fixture has invented splits. |
| 2 | ✅ | Token handling unchanged; the response has no token. |
| 3 | ✅ | The refetch uses the existing refresh/revoke flow (AC1.10). |
| 4 | n/a | OAuth unchanged. |
| 5 | ✅ | No new scope (AC13.1). |
| 6 | ✅ | Only six split fields are stored; no raw detail response. |
| 7 | ✅ | No GPS or streams requested or stored. |
| 8 | ✅ | Logs keep the existing counts only; no split values, HR, VO2max, names or descriptions. |
| 9 | ✅ | Client logging unchanged. |
| 10 | ✅ | Standard error shape; the 422 text has no value. |
| 11 | ✅ | The new route checks the session (AC7.2). |
| 12 | n/a | No cookie change. |
| 13 | ✅ | Tooltip and texts come from numbers, dates and fixed strings. No `[innerHTML]`. |
| 14 | n/a | GET only, no side effects. |
| 15 | ✅ | Response holds chart and tile fields only (AC7.1). |
| 16 | ✅ | No new dependency or external call. |
| 17 | ✅ | Unchanged. |
| 18 | ❌ deferred | Still no disconnect/delete action (carried from 001). New rows are covered by `deleteAll`. |
| 19 | n/a | No webhook. |
| 20 | ⚠ | The DB now also holds per-km HR. Treat backups as health data. |
| 21 | ✅ | Invented data in all fixtures and tests. |

## Test plan
| AC | Test type | Test name |
|---|---|---|
| AC1.1–AC1.2 | backend client (MockEngine) + persistence | `R1 - detail stores per-km splits`, `R1 - detail without splits stores empty list` |
| AC1.3–AC1.4 | backend persistence | `R1 - saving detail again replaces splits`, `R1 - summary upsert keeps splits` |
| AC1.5–AC1.7 | backend unit (SyncPolicy / SyncService) | `R1 - old outdoor details with HR are refetched once for splits`, `R1 - no splits refetch for treadmill or no HR`, `R1 - splits refetch counts as pending` |
| AC1.8 | backend persistence | `R1 - migration adds splits_saved and run_splits keeping rows` |
| AC1.9 | backend persistence | `R1 - splits deleted with run, prune and deleteAll` |
| AC1.10 | backend unit | `R1 - 429 during splits refetch stops sync and writes nothing` |
| AC2.1–AC2.6 | backend unit | `R2 - Strava grade adjusted speed wins`, `R2 - uphill elevation fallback`, `R2 - downhill elevation fallback`, `R2 - grade clamped to 10 percent`, `R2 - no grade data uses plain speed`, `R2 - zero time or distance unusable` |
| AC3.1–AC3.2 | backend unit | `R3 - long run judged on first 60 minutes`, `R3 - easy run at same effort scores the same` |
| AC3.3 | backend unit | `R3 - hilly and flat runs at same effort score the same` |
| AC3.4–AC3.5 | backend unit | `R3 - run under 60 minutes uses all splits`, `R3 - exactly 60 minutes takes nothing after` |
| AC3.6–AC3.8 | backend unit | `R3 - no grade data scored at plain pace`, `R3 - no splits falls back to whole run`, `R3 - splits without HR dropped or fall back` |
| AC3.9–AC3.11 | backend unit | `R3 - workouts and treadmill follow 008`, `R3 - 008 exclusions still apply`, `R3 - too short or implausible early portion excluded` |
| AC4.1–AC4.2 | backend unit | `R4 - uses 008 estimator and configured max HR` |
| AC5.1–AC5.7 | backend unit (NormalizedVo2maxCalculator) | `R5 - current is plain mean per run`, `R5 - long run is one vote in its week`, `R5 - falls back to 28 days or none`, `R5 - 27 Monday weeks`, `R5 - empty week is null not zero`, `R5 - original weekly value matches 008`, `R5 - buckets by local start date` |
| AC6.1–AC6.3 | backend unit | `R6 - predictions from rounded normalized current`, `R6 - marathon note only`, `R6 - no predictions without current` |
| AC7.1–AC7.6 | backend route (`testApplication`) | `R7 - 200 exact JSON shape`, `R7 - 401 without session`, `R7 - 422 invalid config`, `R7 - no Strava calls`, `R7 - rounding`, `R7 - pending counts splits refetch` |
| AC8.1–AC8.2 | frontend component | `R8 - Vo2 tab selected by default`, `R8 - switches between tabs` |
| AC8.3–AC8.4 | frontend component (`HttpTestingController`) | `R8 - normalized loads on first open only`, `R8 - quiet refresh reloads loaded tabs only` |
| AC8.5–AC8.6 | frontend component + manual check at 375 px | `R8 - tab choice not persisted`, `R8 - tabs use theme tokens` |
| AC9.1–AC9.3 | backend route + existing suites | `R9 - vo2max JSON unchanged with splits stored`, existing 008 specs unchanged |
| AC10.1–AC10.3 | frontend component + view unit | `R10 - normalized note always shown`, `R10 - normalized value caption tiles chart footnote`, `R10 - footnote with split import pending` |
| AC11.1–AC11.6 | frontend unit (options) | `R11 - axes like Vo2 chart`, `R11 - null weeks break the normalized line`, `R11 - faint Vo2 comparison series`, `R11 - legend`, `R11 - tooltip lines incl. empty weeks`, `R11 - aria label` |
| AC12.1–AC12.9 | frontend component | `R12 - loading`, `R12 - invalid config without retry`, `R12 - error with retry requests normalized`, `R12 - 401 shows no error`, `R12 - empty`, `R12 - empty importing`, `R12 - no current shows chart`, `R12 - normal order`, `R12 - tabs fail independently` |
| service / guard | frontend service + unit | `R7 - getNormalizedVo2max calls /api/vo2max/normalized`, `R7 - isNormalizedVo2maxResponse rejects missing originalVo2max` |
| AC13.1–AC13.4 | review + existing suites | `R13 - other reports unchanged with splits stored` |

## Out of scope
- Any change to the existing "Vo2" estimate, its chart or its tiles.
- Heat or temperature correction.
- Changes to treadmill or hot-gym handling (treadmill runs keep 008's whole-run or rep handling, at any length).
- New Strava OAuth scopes, new Strava endpoints or streams.
- Per-run drill-down, lists of runs used/excluded, and writing back to Strava.
- Grade adjustment of interval rep laps.
- Persisting the selected tab.

## Suggested follow-ups
- Grade-adjust outdoor rep laps (e.g. from the splits that overlap each lap).
- An HR-drift-based cut-off ("until drift begins") instead of a fixed 60 min, reusing 013's ideas.
- Show the race date (6 Dec 2026) on both charts.
- A short "why the two differ" line comparing the current values of both tabs.
- Remember the last selected tab for the session (backend-side or in-memory only).

## Risks & decisions to confirm
1. **Data source: per-km splits from the existing detail call, not streams.** `splits_metric` gives per-km HR, moving time, elevation difference and Strava's grade-adjusted speed with no new endpoint. But `average_heartrate` and `average_grade_adjusted_speed` are returned by Strava without being listed in its API reference. If Strava drops them:
   - without grade-adjusted speed, the elevation fallback takes over;
   - without split HR, runs fall back to the 008 whole-run segment, which brings drift back.

   The alternative is the 013-style streams call, which costs one more call per run.
2. **One-time detail refetch.** About 150–200 outdoor runs with HR get one more detail call (2–3 windows, ~30–45 min). Meanwhile the 009 banner shows "still importing", and the Normalized tab shows a pending count. It sits in `MISSING_DETAILS`, so it goes ahead of HR-stream and drift backlogs.
3. **Fallback hill model = Minetti's cost curve on the per-km net grade, clamped to ±10 %.** It is stronger than Strava's own GAP (about +11 % equivalent speed at 2 % grade vs Strava's smaller adjustment). It is only used when Strava's GAP is missing, which should be rare for outdoor GPS runs.
4. **Cut-off rule details.** It counts moving time, the crossing split is prorated, and easy runs of 60–90 min are cut too. Pieces without split HR are dropped.
5. **Interval workouts are not grade-adjusted.** They keep 008's rep laps at plain lap pace, because laps carry no GAP. Hilly rep sessions will still read low.
6. **Treadmill runs are unchanged, including treadmill long runs over 60 min** (whole run, pace-line pace), per the non-goal. A long treadmill run with drift is therefore not cut.
7. **Runs with no splits** (manual runs with HR, or a detail without `splits_metric`) fall back to the 008 whole-run segment rather than being excluded (decision 3 spirit).
8. **The normalized tab loads lazily**, on first open. This keeps page-load requests unchanged (AC8.3). The cost is a short loading state the first time the tab opens each visit.
9. **Endpoint `GET /api/vo2max/normalized` is separate from `/api/vo2max`**, and carries `originalVo2max` per week for the comparison line. So `/api/vo2max` stays byte-identical (S2).
10. **Expect the normalized value to sit above the Vo2 value**, so its race predictions are faster. The marathon "optimistic" note is kept, and it matters even more here.
