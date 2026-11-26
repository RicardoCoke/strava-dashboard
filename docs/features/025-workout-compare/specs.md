# Spec: Workout compare

**Feature ID:** 025 · **Status:** Approved · **Intent:** [intent.md](./intent.md) · **Date:** 2026-11-26

## Summary
A new **Compare** page (`/compare`, a third entry in the 023 side menu) lists the person's 30 latest runs of the chosen type (all / workouts / long / easy), using `RunClassifier`. When two runs are picked, `GET /api/workout-compare` returns chart-ready data for both: one chart that overlays pace and heart rate, a side-by-side stats block and, when both runs are interval workouts, a rep table. That rep table pairs rep N with rep N, or cuts the longer session's reps into pieces of the shorter rep length. Per-second data is fetched on demand through the existing `ActivitySource` port (Garmin FIT file, existing cache) and is charged to the person's existing 80-calls-per-15-minutes budget. When either run has no per-second data (Strava-era run, treadmill run, no file, budget used up, rate limited), both runs fall back to their stored per-km splits and the page says why. Nothing new is stored.

## Requirements
| ID | Requirement | Priority |
|---|---|---|
| R1 | A "Compare" entry in the side menu opens `/compare`. The page has the shared header, follows the person switch and keeps its run list fresh after a sync | Must |
| R2 | `GET /api/workout-compare/runs?type=all\|workouts\|long\|easy` returns the 30 latest runs of that type, newest first, classified by `RunClassifier`, from the current person's store only | Must |
| R3 | The page shows a type filter and the run list. Up to two runs can be selected, and the list has loading, error, empty and normal states | Must |
| R4 | `GET /api/workout-compare?a=&b=` returns both runs ordered earlier/later, with stats, chart points and rep comparison. Bad or unknown ids are rejected | Must |
| R5 | Per-second data is fetched on demand only when both runs can have it, within the shared call budget, and never while the source is rate limited. Otherwise both runs use per-km splits, with a reason | Must |
| R6 | Chart points are built in the domain: 10-second buckets from the per-second data, or one point per stored per-km split. Units are km, seconds, s/km and bpm | Must |
| R7 | One chart overlays pace and HR for both whole runs, tells the runs apart, switches its x-axis between distance and time, pinch-zooms as in 024 and states when it is coarser | Must |
| R8 | A stats block shows both runs side by side: distance, moving time, average pace, average HR, elevation gain, efficiency factor and HR drift | Must |
| R9 | Two workouts with the same rep length: rep N is paired with rep N, with pace and HR differences, and extra reps are listed on their own | Must |
| R10 | Two workouts with different rep lengths: the longer session's reps are cut into pieces of the shorter length (leftover ignored), piece N is paired with rep N, and both get an all-pieces vs all-reps average | Must |
| R11 | When reps can't be cut (no per-second data, or reps not found), the rep comparison says why and shows whole-session averages instead | Must |
| R12 | The rep section renders the table, the average row, the piece caveat and the unavailable note | Must |
| R13 | The whole page works at 360 px and on desktop, in the 002 dark style | Must |

## Acceptance criteria

**Conventions.** "Earlier" and "later" mean `runs[0]` and `runs[1]` of the compare response, ordered by `startUtc`. A run's label is `Earlier · <formatDayMonth(date)>` or `Later · <formatDayMonth(date)>`, for example `Earlier · 10 Nov`, and it is used the same way in the legend, tooltip, notes and table headers. "Cut-over" is the person's `garminActivitiesFrom` (021). All test data is invented.

### R1: Compare tab
- **AC1.1** Given the nav menu is open, then it lists exactly three links in this order: "Performance" (`/`), "Recovery" (`/recovery`) and "Compare" (`/compare`). Given the URL is `/compare`, then only "Compare" has `aria-current="page"` and the active style. This updates 023's two-link test.
- **AC1.2** Given the browser opens or refreshes `/compare`, then the Compare page renders. The route uses `loadComponent`, and in production the request goes through Ktor's existing SPA fallback, so no backend change is needed. An unknown path still redirects to `/`.
- **AC1.3** Given `/compare`, then the page shows, in order: the header (`<app-nav-menu>`, `<h1>Compare</h1>`, `<app-person-switch>`, `<app-weekly-summary-bell>`, same markup as Recovery), the type filter, the run list and the compare area.
- **AC1.4** Given the person switch is flipped, then the page is recreated (`WorkoutCompareShell` is keyed on `PersonStore.person()`, as `RecoveryShell` is). Pending requests are cancelled, `GET /api/workout-compare/runs?type=all` is sent with the new `X-Person`, nothing is selected, and nothing from the previous person is shown at any point.
- **AC1.5** Given the page opens, then it provides `SyncStore` and calls `start()` (023 pattern). Given `SyncStore.dataVersion` goes up, then the list reloads quietly with the current filter. A failed reload keeps the current list. The selection and the shown comparison stay unchanged.

### R2: Run list endpoint
- **AC2.1** Given 34 stored runs, 32 of them workouts, when `GET /api/workout-compare/runs?type=workouts` is called, then the response is 200 with exactly 30 runs, all workouts, ordered by `startUtc` descending (ties broken by id descending). The 2 oldest workouts are left out.
- **AC2.2** Given four runs: "Sunday long" (22 km), "4x10min" (22 km), "10x3" (8 km) and "Easy" (8 km), then their `typeKey`s are `long`, `4x10min`, `10x3min` and `easy` (`RunClassifier.classify(run)`). `?type=long` returns only the first run, `?type=easy` only the last, `?type=workouts` the middle two and `?type=all` all four.
- **AC2.3** Given a run with distance 10 400 m, moving time 3 036 s, `startLocal` 2026-11-24T07:30 and `trainer = true`, then its item is `{"id":…, "date":"2026-11-24", "name":…, "typeKey":…, "distanceKm":10.4, "averagePaceSecondsPerKm":292, "treadmill":true}`. Given a moving time that is null or 0, or a distance of 0, then `averagePaceSecondsPerKm` is `null`.
- **AC2.4** Given `type` is missing or not exactly one of `all`, `workouts`, `long` or `easy` (for example `Workouts` or `x`), then the response is 400 `{"error":"invalid_type","message":"type must be all, workouts, long or easy."}`.
- **AC2.5** Given no stored runs, then the response is 200 `{"runs":[]}`.
- **AC2.6** Given `X-Person: marina`, then only Marina's store is read. Given Ricardo (or no header), only his is. Tested with different runs per person.
- **AC2.7** The list endpoint makes no Garmin request: the fake Garmin engine receives zero requests.

### R3: Filter and run list UI
- **AC3.1** The filter is a `role="group"` with `aria-label="Run type"` and four buttons, in order "All", "Workouts", "Long" and "Easy". Only the selected button has `aria-pressed="true"`. Given the page opens, then "All" is pressed and the request is `?type=all`.
- **AC3.2** Given "Workouts" is clicked, then the list shows its loading state, `?type=workouts` is requested, the selection is cleared and the compare area goes back to its prompt. Given another filter change while a request is pending, only the latest response is shown (the older request is cancelled).
- **AC3.3** Each row is a `<label>` holding an `<input type="checkbox">`. Its first line is `formatShortDate(date) · <typeLabel> · 10.4 km · 4:52 /km`, with `— /km` when the pace is null. The second line is the run name by plain interpolation, and it is left out when the name is empty. A "Treadmill" tag appears when `treadmill` is true. `typeLabel` is `easy` → "Easy", `long` → "Long", and otherwise the key itself (for example "5x6min").
- **AC3.4** Checking a row selects it, and unchecking deselects it. Given two runs are selected, when a third is checked, then the run selected first is deselected. Given exactly two are selected, then `GET /api/workout-compare?a=<id>&b=<id>` is sent, and any older pending compare request is cancelled.
- **AC3.5** List states:
  - **Loading:** `<app-ring-spinner>` and "Loading runs…" in a `role="status"` region.
  - **Error** (HTTP error, or a 200 body that fails `isCompareRunsResponse`): "Couldn't load runs." and a Retry button that reloads the current filter.
  - **Empty:** "No runs yet." / "No workouts yet." / "No long runs yet." / "No easy runs yet.", depending on the filter.
  - **Normal:** the rows.
- **AC3.6** Compare-area prompt: with nothing selected it shows "Pick two runs to compare.", and with one run selected "Pick one more run to compare."

### R4: Compare endpoint
- **AC4.1** Given stored runs X (2026-11-10) and Y (2026-11-24), when `?a=Y&b=X` and `?a=X&b=Y` are requested, then both bodies are identical and `runs[0]` is X: the earlier `startUtc` comes first, and an equal `startUtc` puts the smaller id first.
- **AC4.2** Given `a` or `b` is missing or not a whole number, or `a == b`, then the response is 400 `{"error":"invalid_runs","message":"a and b must be two different run ids."}`.
- **AC4.3** Given either id is not a stored run in the current person's store, then the response is 404 `{"error":"run_not_found","message":"Run not found."}`. This includes an id that exists only in the other person's store.
- **AC4.4** Each `runs[i]` has `id`, `date` (the `startLocal` date), `name`, `typeKey` (classifier), `treadmill`, `stats` (R8) and `points` (R6). The top level has `resolution`, `coarse` (R5) and `reps` (R9–R11).

### R5: Per-second data on demand
- **AC5.1** Given both runs are on or after the cut-over, neither has `trainer = true`, the person has a Garmin key and the sync state's `rateLimitedUntil` is null or not after now, then for each run, earlier first, one budget unit is taken (`CallBudget.tryConsume()`) and one port call is made: `driftStream(id)` when the run's `averageHeartRateBpm` is not null, else `speedStream(id)`. A `driftStream` `NotFound` is followed by one `speedStream` call, which takes another unit. Given both calls succeed, then `resolution` is `"stream"` and `coarse` is `null`.
- **AC5.2** Up-front checks, made before any Garmin call, for the earlier run and then the later run. The first match wins, no port call is made, and `resolution` is `"splits"`:
  1. no Garmin key → `coarse = {"reason":"notConfigured","runId":null,"retryAfter":null}`
  2. `localDate` before the cut-over → `beforeGarmin` with that run's id
  3. `trainer = true` → `treadmill` with that run's id
  4. sync state `rateLimitedUntil` after now → `rateLimited`, with `retryAfter` = that instant
- **AC5.3** Given the budget has exactly 1 unit left, then the earlier run's call is made and the later run's call is not. The result is `"splits"` with `coarse = {"reason":"callLimit","runId":null,"retryAfter":<CallBudget.windowEnd()>}`.
- **AC5.4** Given a port call returns `RateLimited(until)` (Garmin 429), then no further call is made in that request. The result is `"splits"` with `coarse = {"reason":"rateLimited","runId":null,"retryAfter":<until>}`. The sync state is not written.
- **AC5.5** Given a port call returns `Unavailable` (network failure, timeout, 5xx, or a key still rejected after `GarminConnect`'s one token refresh), then the result is `"splits"` with `coarse.reason = "garminUnavailable"`, `runId` null and `retryAfter` null.
- **AC5.6** Given `speedStream` returns `NotFound` (no FIT file, a file that doesn't decode, or no speed records), then the result is `"splits"` with `coarse.reason = "noDetailFile"` and that run's id.
- **AC5.7** Given any fallback happens after one run's stream was already fetched, then that stream is not used: both runs get split points, so the resolution is always the same for both.
- **AC5.8** Nothing is stored: after a compare, the fake run repository has had no `save*`, `upsert*`, `delete` or `prune*` call, and the sync state is unchanged.
- **AC5.9** Given a sync `activityDetail(id)` and a compare `driftStream(id)` running at the same time for the same activity, then `GarminActivitySource` downloads the FIT file once and both calls succeed, because the FIT cache is used under a `Mutex`.
- **AC5.10** A compare request logs at most one line: the coarse reason code, when there is one. Logs never include pace or HR values, the key, the token or URLs.

### R6: Chart points (domain)
- **AC6.1** Stream points:
  - The distance curve `D(t)` is the trapezoid integration of the speed samples that `StreamRepPaceFinder` already uses, linearly interpolated between samples.
  - Buckets are `[10k, min(10(k+1), tLast)]` for k = 0, 1, … while `10k < tLast`, with t in seconds since the first sample. Each bucket gives one point.
  - `timeSeconds` = the bucket end.
  - `distanceKm` = `roundHalfUpToInt(D(end)) / 1000.0`.
  - `paceSecondsPerKm` = `paceOf(bucketSeconds, D(end) − D(start))`.
  - `heartRateBpm` = the bucket's HR (AC6.3).
  - Example: given 1 Hz samples from 0 to 60 s at a constant 4.0 m/s and 150 bpm, then there are 6 points: `(0.04 km, 10 s, 250, 150)`, `(0.08, 20, 250, 150)` … `(0.24, 60, 250, 150)`.
- **AC6.2** Gaps:
  - Given a bucket with no sample in `[10k, 10(k+1))` (for example a watch pause), then its pace and HR are `null`.
  - Given a bucket pace above 600 s/km (slower than 10:00 /km), or no distance gained, then its pace is `null` and its HR is kept.
  - The same 600 s/km rule applies to split points.
- **AC6.3** HR over any time window (buckets here, reps and pieces in R9 and R10) is the time-weighted mean of the bpm samples. Each sample's value holds until the next sample, clipped to the window. Intervals longer than 30 s (`HrHistogramBuilder.MAX_SAMPLE_GAP_SECONDS`) and samples ≤ 0 bpm are skipped. The result is rounded half-up, and it is `null` when nothing is left or the run was fetched with `speedStream` (no HR).
- **AC6.4** Split points: one point per stored split, in index order, with `distanceKm` = cumulative split distance (rounded to the metre, /1000), `timeSeconds` = cumulative split moving time, `paceSecondsPerKm` = `paceOf(movingTime, distance)` (null when either is 0) and `heartRateBpm` = `roundHalfUpToInt(averageHeartRateBpm)` or null. Example: given splits (1000 m, 300 s, 150.4), (1000 m, 290 s, 155.6) and (400 m, 118 s, null), then the points are `(1.0, 300, 300, 150)`, `(2.0, 590, 290, 156)` and `(2.4, 708, 295, null)`.
- **AC6.5** Given a run whose `splits` is null or empty, then its split points are `[]`.

### R7: Chart UI
- **AC7.1** `buildCompareOptions(data, axis)` returns:
  - 4 `line` series, in this order: earlier pace, later pace, earlier HR, later HR. Both series of one run share its label as `name`, so the legend has exactly two entries (`legend.data = [earlierLabel, laterLabel]`), and clicking one toggles that run's pace and HR together.
  - Colours: earlier `palette.secondary`, later `palette.accent`.
  - Pace series: solid, width 2, on `yAxisIndex` 0. HR series: dashed, width 1.5, on `yAxisIndex` 1.
  - `connectNulls: false` on every series. `showSymbol` is `true` only when `resolution` is `"splits"`.
  - y-axis 0: name `Pace (/km)`, `inverse: true`, `scale: true`, labels from `formatPace`. y-axis 1: name `HR (bpm)`, `scale: true`, no split lines.
- **AC7.2** The x-axis toggle is a `role="group"` with `aria-label="Chart x-axis"` and two buttons, "Distance" (the default) and "Time", each with `aria-pressed`. With "Distance", the x-axis is `type: 'value'`, `min: 0`, name `Distance (km)` and data x = `distanceKm`. With "Time", the name is `Time (min)` and x = `timeSeconds / 60`. Switching the axis sends no request.
- **AC7.3** The tooltip (`trigger: 'axis'`) shows a first line with the x value (`2.40 km` or `formatRaceTime(seconds)`, for example `12:30`), then one line per visible run, for example `Earlier · 10 Nov: 4:10 /km · 152 bpm`. The pace shows as `— /km` when null, and the HR part is left out when null. The tooltip is built only from labels, dates and numbers, never from activity names.
- **AC7.4** The chart renders through `<app-chart>` with a single x-axis (index 0), so 024's pinch zoom applies unchanged.
- **AC7.5** The chart's `label` is `Pace and heart rate of two runs by distance`, or `… by time` after the toggle.
- **AC7.6** Given `coarse` is not null, then a note above the chart reads "Showing per-km splits, which are coarser than second-by-second data: " followed by:
  - `beforeGarmin`: "the <label> run was synced from Strava, which kept no detailed data."
  - `treadmill`: "the <label> run is a treadmill run."
  - `noDetailFile`: "Garmin Connect has no detailed data for the <label> run."
  - `callLimit`: "the Garmin Connect call limit is used up. Try again after HH:mm."
  - `rateLimited`: "Garmin Connect is limiting requests. Try again after HH:mm."
  - `garminUnavailable`: "couldn't reach Garmin Connect."
  - `notConfigured`: "Garmin Connect isn't set up for this person."

  `<label>` is the label of the run whose id is `coarse.runId`. HH:mm is `formatDate(retryAfter, 'HH:mm', 'en-US')`, as in the sync banner, and "later" replaces "after HH:mm" when `retryAfter` is null.
- **AC7.7** Given one run has `points: []`, then the chart shows the other run and the note "No per-km data for the <label> run." Given both runs have none, then the chart is replaced by "No pace data for these runs." The stats and reps still render.
- **AC7.8** A caption under the chart reads "Solid line: pace · dashed line: heart rate".

### R8: Stats block
- **AC8.1** Per run:
  - `distanceKm` = `distance.toKmOneDecimal()`
  - `movingTimeSeconds` = stored moving time
  - `averagePaceSecondsPerKm` = `paceOf(movingTime, distance)`
  - `averageHeartRateBpm` = `roundHalfUpToInt(averageHeartRateBpm)`
  - `elevationGainMeters` = `roundHalfUpToInt` of the sum of the positive split `elevationDifference`s
  - `efficiencyFactor` = (distance m ÷ (moving s ÷ 60)) ÷ unrounded average HR, rounded to 3 decimals with `roundEfficiency` (014's formula)

  Example: given 10 000 m, 3 000 s and 149.6 bpm, then distance 10.0, pace 300, HR 150 and EF 1.337. Given splits with elevation differences +12.4, −8.0, +5.3 and null, then the elevation gain is 18.
- **AC8.2** Nulls:
  - A moving time that is null or 0, or a distance of 0: pace and EF are null.
  - No average HR: HR and EF are null.
  - Splits null or empty, or none with an elevation difference: elevation gain is null.
- **AC8.3** `hrDriftPct` = the stored `hrDrift.driftPct` when its status is `COMPUTED` (013 long runs). Otherwise it is `HrDriftCalculator.compute(driftStream)` when this request fetched a drift stream and the status is `COMPUTED`, and otherwise null. It is rounded to 1 decimal. Example: a 45-minute drift stream gives `TOO_SHORT`, so the value is null.
- **AC8.4** The UI is a table with the row labels Distance, Moving time, Avg pace, Avg HR, Elevation gain, Efficiency factor and HR drift, and the column headers `<earlier label>` and `<later label>`, each with the run name on a second line. The formats are `10.4 km`, `formatRaceTime` (`52:10`, `1:05:20`), `4:52 /km`, `150 bpm`, `18 m`, `formatTwoDecimals` (`1.34`) and `formatSignedOneDecimal` + `%` (`+3.8%`). A null value shows `—`. Given any HR drift is null, a footnote reads "HR drift needs at least 60 minutes of detailed pace and heart-rate data."

### R9: Reps with the same rep length
- **AC9.1** Given both runs classify as `Workout` and have the same `minutes` M, then `reps.mode = "reps"`:
  - Each run's reps are the windows `StreamRepPaceFinder` picks (the N fastest non-overlapping M-minute windows), numbered by start time.
  - Rep pace = `paceOf(M × 60, window distance)`. Rep HR follows AC6.3.
  - Example: given a 1 Hz stream with reps at a constant 4.0 m/s and 170 bpm and recoveries at 2.5 m/s and 140 bpm, then every rep is 250 s/km and 170 bpm.
  - The average pace over all of a run's reps equals what `StreamRepPaceFinder.find` returns for that stream.
- **AC9.2** (S8) Given 5x6min vs 5x6min, then `pairs` has 5 rows. Row i pairs earlier rep i with later rep i, with `paceDiffSecondsPerKm` = later − earlier and `heartRateDiffBpm` = later − earlier, both computed from the rounded values.
- **AC9.3** (S9) Given an earlier 5x3min and a later 8x3min, then `pairs` has 8 rows. Rows 1–5 have both sides and differences. Rows 6–8 have `earlier: null` and null differences. The case with the sides swapped mirrors this.
- **AC9.4** `average` holds each run's average over all of its own reps (5 and 8 here): pace = `paceOf(total seconds, total distance)` and time-weighted HR, plus the differences.
- **AC9.5** Given one run has no HR (fetched with `speedStream`), then its `heartRateBpm` cells and every `heartRateDiffBpm` are null. The pace differences are still set.
- **AC9.6** Given either run doesn't classify as `Workout`, then `reps` is `null`.

### R10: Pieces with different rep lengths
- **AC10.1** (S10, S11) Given both runs are workouts and M_long > M_short, then each of the longer session's reps (found as in AC9.1 with M_long) is cut into `M_long / M_short` (integer division) consecutive pieces of M_short minutes, starting at the rep's start. Any leftover is ignored. Pieces are numbered by start time. Examples: 5x6min gives 10 pieces of 3 min (2 per rep). 4x5min vs 6x3min gives 4 pieces, each the first 3 minutes of a rep: given 5-min reps run at 4.0 m/s for 3 min and 3.0 m/s for 2 min, every piece is 250 s/km.
- **AC10.2** Piece pace = `paceOf(M_short × 60, D(end) − D(start))`. Piece HR follows AC6.3.
- **AC10.3** The response has `mode: "pieces"`, `pieceMinutes` = M_short and `piecesRunId` = the longer session's id. `pairs` has max(piece count, N_short) rows: row i pairs piece i with rep i, and the extra rows have one side null. Example: an earlier 5x6min vs a later 8x3min gives 10 rows, rows 1–8 paired and rows 9–10 with `later: null`. "Earlier" and "later" still mean the run dates, whichever run is cut.
- **AC10.4** `average` compares all pieces with all of the shorter session's reps: pace = `paceOf(count × M_short × 60, total distance)` per side, HR time-weighted over the union of the windows, and later − earlier differences.

### R11: Reps that can't be cut
- **AC11.1** (S12) Given both runs are workouts and `resolution` is `"splits"`, then `reps = {"mode":"unavailable","unavailableReason":"noDetailedData","unavailableRunId":null,"pieceMinutes":null,"piecesRunId":null,"average":null,"pairs":[]}`.
- **AC11.2** Given `resolution` is `"stream"` but `StreamRepPaceFinder` finds fewer than N windows in a run (earlier run checked first), then `mode` is `"unavailable"`, `unavailableReason` is `"repsNotFound"` and `unavailableRunId` is that run's id.

### R12: Rep section UI
- **AC12.1** Given `reps` is null, then no rep section is rendered. Otherwise a card titled "Reps" follows the stats.
- **AC12.2** In `reps` and `pieces` mode, the table has the columns `#`, `<earlier label>`, `<later label>` and `Difference`. Each run header carries the run's `typeKey` on a second line, plus ` as 3-min pieces` for `piecesRunId`. The first row is labelled "All" (`average`), then rows `1…n` follow `pairs`. A cell reads `4:12 /km · 166 bpm` (no HR part when it is null), and a null cell is `—`. The difference reads `formatPaceChange(d)` + ` · ` + `formatBpmChange(h)` + ` bpm` (for example `8 s/km faster · −3.0 bpm`), only the pace part when the HR difference is null, and empty when the pace difference is null.
- **AC12.3** (S13) In `pieces` mode, this text follows the table: "Pieces are cut from the longer reps. Longer reps are usually run a little slower, and heart rate keeps rising through each rep, so pieces tend to look slower with a higher heart rate. This shows whether you held short-rep pace inside the longer reps, not purely whether you're fitter."
- **AC12.4** In `unavailable` mode, the card shows "Reps can't be compared without detailed data for both runs." (`noDetailedData`) or "Couldn't find the <typeKey> reps in the <label> run." (`repsNotFound`). Then follows "Whole-session averages:" and one line per run, `<label>: 5:11 /km · 149 bpm`, taken from `stats` (`—` for nulls).

### R13: Mobile and desktop
- **AC13.1** At 360 px wide, nothing scrolls horizontally. The four filter buttons fit on one row. The run list is its own scroll box (`max-height: 45vh`) above the compare area. The x-axis toggle, chart, stats and rep tables are full width, and the cells wrap. Each row, checkbox label and button is at least 44 × 44 px. Manual check.
- **AC13.2** At ≥ 900 px, the list is a 320 px left column (`max-height: 75vh`, scrolling), with the compare area to its right, both aligned to the top. Manual check.
- **AC13.3** Only existing tokens are used (`--color-surface`, `--color-border`, `--color-text`, `--color-text-muted`, `--color-accent`). A selected row has an accent border. Buttons and checkboxes have a visible focus ring.

## Traceability
| Success criterion (intent) | Covered by |
|---|---|
| S1: new tab, latest 30 runs of the type, newest first, list beside/above chart | R1, R2 (AC2.1), R3, R13 |
| S2: filter all / workouts / long / easy, as the app classifies | R2 (AC2.2), R3 (AC3.1, AC3.2) |
| S3: two runs overlaid, pace and HR, runs distinguishable, no rep alignment | R4, R6, R7 (AC7.1, AC7.3) |
| S4: x-axis distance / time | R7 (AC7.2) |
| S5: per-km fallback with a "coarser" note | R5, R6 (AC6.4), R7 (AC7.6) |
| S6: side-by-side stats | R8 |
| S7: rep table for interval sessions | R9, R12 |
| S8: same length and count, rep N vs rep N with differences | R9 (AC9.2) |
| S9: same length, different count, extras listed alone | R9 (AC9.3) |
| S10: different lengths, pieces, averages and piece N vs rep N | R10 |
| S11: uneven division, one piece per rep, leftover ignored | R10 (AC10.1) |
| S12: no detailed data → whole-session averages with a note | R11, R12 (AC12.4) |
| S13: piece caveat | R12 (AC12.3) |
| S14: usable on a phone | R13, R7 (AC7.4) |
| Constraint: own runs per `X-Person` | AC2.6, AC4.3, AC1.4 |
| Constraint: 80 calls / 15 min budget | AC5.1, AC5.3 |
| Assumption: same N and M, no tolerance | AC9.1, AC10.1 (mode chosen by exact M) |
| Assumption: 30-run limit after the type filter | AC2.1 |
| Assumption: exhausted budget → per-km with a visible note | AC5.3, AC7.6 |
| Assumption: no HR → pace only, no error | AC6.3, AC8.2, AC9.5 |
| Assumption: pinch zoom as 024 | AC7.4 |

## Design

### Data from Strava
None. Strava was removed in 021. Strava-era runs are read from the local store only (summary, laps, splits and stored drift).

### Data from Garmin Connect (no new endpoint)
- **Endpoint:** existing `GET /download-service/files/activity/{id}` (the FIT zip), reached only through the existing port methods `ActivitySource.driftStream(id)` and `speedStream(id)` → `GarminActivitySource`. No new fields are read. Positions are still never read (021 R5).
- **Auth / scopes:** the existing per-person Personal API key → Bearer token (`GarminConnect`). There are no OAuth scopes. 401/403 gets one token refresh and retry (existing), then `KeyRejected` → `Unavailable` → `garminUnavailable`.
- **Pagination:** none (one file per activity). The list endpoint reads the store only.
- **Caching:** the existing in-memory parsed-FIT LRU (8 activities), now shared by the sync and compare, so a second compare of the same pair doesn't download again. The compare result itself isn't cached, and nothing is persisted.
- **Budget:** the person's single `CallBudget` (80 units per 15-minute block), shared with the sync. Each port call takes one unit first, and `tryConsume() == false` → `callLimit`. At most 4 units per request, typically 2.
- **429:** `SourceResult.RateLimited(until)` (Retry-After, else 15 min, existing mapping) → `rateLimited` note. There is no retry and no further call in that request. No call is made while `SyncState.rateLimitedUntil` is after now. Compare never writes the sync state: the sync discovers its own 429.
- **Network / 5xx / timeout:** `Unavailable` → `garminUnavailable`. Cached split data is still shown.
- **Concurrency:** compare requests can now overlap a person's sync job. `GarminActivitySource.fit()` (cache lookup + download + put) runs under a `kotlinx.coroutines.sync.Mutex`. The `GarminConnect` token cache stays unlocked. The worst case of a race is one extra token request, which the existing one-retry handles. Its KDoc ("calls run one at a time") is updated.

### Domain model & logic (`domain/workoutcompare/`, pure except the service)
- **`CompareFilter { ALL, WORKOUTS, LONG, EASY }`**, matched on `RunClassifier.classify(run)`: `Workout` / `LongRun` / `Easy`. Only `run.isRun`.
- **`WorkoutCompareService(runs: RunRepository, source: ActivitySource?, budget: CallBudget, syncState: SyncStateRepository, activitiesFrom: LocalDate, clock: Clock)`:**
  - `list(filter): List<RunActivity>`: `findAll()`, filtered, sorted by `startUtc` then id, descending, `take(30)`.
  - `compare(a, b): CompareOutcome`: `sealed interface CompareOutcome { data class Found(val comparison: WorkoutComparison); data object RunNotFound }`. It orders the two runs, runs the R5 checks and fetches, then calls the pure functions below.
  - `source` is null when the person has no Garmin key.
- **`RunStream(speed: SpeedStream, bpm: List<Bpm>?, drift: DriftStream?)`:** built from a `DriftStream` (times + speeds, bpm, itself) or a `SpeedStream` (bpm and drift null). Transient, never stored.
- **`StreamRepPaceFinder` refactor (no behaviour change):**
  - It exposes `internal fun windows(stream, workout): List<RepWindow>?` with `RepWindow(startSeconds: Double, distanceMeters: Double)`, sorted by start, null when fewer than N windows are found or the total is ≤ 0.
  - It also exposes `internal class DistanceCurve(stream) { fun at(seconds: Double): Double }`, the existing trapezoid and interpolation code moved as-is.
  - `find` becomes `windows(...)?.let { paceOf(reps × M × 60, Σ distance) }`. The existing `RepPaceTest` must pass unchanged.
- **Pure functions (`CompareCalculator`, `RepComparer`):**
  - `streamPoints(RunStream): List<ComparePoint>` (AC6.1–6.3)
  - `splitPoints(List<Split>?): List<ComparePoint>` (AC6.4, 6.5)
  - `averageBpm(times, bpm, from, to): Double?` (AC6.3)
  - `stats(run, drift: DriftStream?): CompareStats` (R8)
  - `compareReps(earlier, later, earlierStream?, laterStream?): RepComparison?` (R9–R11)
- **Models:**
  - `ComparePoint(distance: Meters, time: Seconds, pace: SecondsPerKm?, heartRate: Double?)`
  - `CompareStats(distance: Meters, movingTime: Seconds?, pace: SecondsPerKm?, averageHeartRateBpm: Double?, elevationGain: Meters?, efficiency: Double?, driftPct: Double?)`
  - `RepCell(pace: SecondsPerKm, heartRate: Double?)`, `RepRow(earlier: RepCell?, later: RepCell?)`
  - `RepComparison(mode, unavailableReason, unavailableRunId, pieceMinutes, piecesRunId, average: RepRow?, pairs: List<RepRow>)`
  - `Coarse(reason: CoarseReason, runId: ActivityId?, retryAfter: Instant?)`
  - `WorkoutComparison(resolution, coarse, earlier: CompareSide, later: CompareSide, reps)`
  - Differences and rounding to display units happen in the API mappers, so the domain keeps unrounded values plus units.
- **Constants:** `LIST_SIZE = 30`, `CHART_BUCKET_SECONDS = 10`, `SLOWEST_PLOTTED_PACE_SECONDS_PER_KM = 600`.
- **Units and time:**
  - Metres → km: `toKmOneDecimal` for stats, metre-rounded `/1000.0` for chart points.
  - m/s → s/km: `paceOf` (half-up). HR is rounded half-up to whole bpm, EF to 3 decimals, drift to 1 decimal.
  - `date` comes from `startLocal`. Chart x values count from each run's first sample (stream) or 0 (splits), so time zones and DST don't affect them. The order uses `startUtc`.
- **Mode selection (R9–R11):** `null` unless both are `Workout`. `resolution == SPLITS` → unavailable/noDetailedData. Then same M → reps, and different M → pieces. Missing windows → unavailable/repsNotFound.

### Persistence
None. No tables, columns or migrations. Per-second samples live only in the existing 8-entry FIT cache and for the length of one request.

### Backend API
`X-Person` picks the store (019). There is no session guard (none since 021). Both routes are thin: parse, call `WorkoutCompareService`, map.

**`GET /api/workout-compare/runs?type=<all|workouts|long|easy>`**, response 200:
```json
{
  "runs": [
    {
      "id": 20811000002,
      "date": "2026-11-24",
      "name": "8x3min hills",
      "typeKey": "8x3min",
      "distanceKm": 10.1,
      "averagePaceSecondsPerKm": 306,
      "treadmill": false
    },
    {
      "id": 20811000001,
      "date": "2026-11-10",
      "name": "5x6min threshold",
      "typeKey": "5x6min",
      "distanceKm": 11.2,
      "averagePaceSecondsPerKm": 311,
      "treadmill": false
    }
  ]
}
```
- `runs`: 0–30 items, newest first.
- `id`: integer (activity id, below 2^53).
- `date`: ISO local date `YYYY-MM-DD`.
- `name`: string (user text, may be `""`).
- `typeKey`: `"easy"`, `"long"` or `"<N>x<M>min"`.
- `distanceKm`: number with 1 decimal.
- `averagePaceSecondsPerKm`: int or null.
- `treadmill`: boolean.

Errors: 400 `invalid_type` ("type must be all, workouts, long or easy.") · 404 `person_not_configured` (existing) · 500 `internal_error` (existing).

**`GET /api/workout-compare?a=<id>&b=<id>`**, response 200 (example abbreviated: 2 points per run and 3 of the 10 pairs):
```json
{
  "resolution": "stream",
  "coarse": null,
  "runs": [
    {
      "id": 20811000001,
      "date": "2026-11-10",
      "name": "5x6min threshold",
      "typeKey": "5x6min",
      "treadmill": false,
      "stats": {
        "distanceKm": 11.2,
        "movingTimeSeconds": 3480,
        "averagePaceSecondsPerKm": 311,
        "averageHeartRateBpm": 149,
        "elevationGainMeters": 38,
        "efficiencyFactor": 1.296,
        "hrDriftPct": null
      },
      "points": [
        { "distanceKm": 0.027, "timeSeconds": 10, "paceSecondsPerKm": 370, "heartRateBpm": 101 },
        { "distanceKm": 0.055, "timeSeconds": 20, "paceSecondsPerKm": 357, "heartRateBpm": null }
      ]
    },
    {
      "id": 20811000002,
      "date": "2026-11-24",
      "name": "8x3min hills",
      "typeKey": "8x3min",
      "treadmill": false,
      "stats": {
        "distanceKm": 10.1,
        "movingTimeSeconds": 3090,
        "averagePaceSecondsPerKm": 306,
        "averageHeartRateBpm": 147,
        "elevationGainMeters": 25,
        "efficiencyFactor": 1.334,
        "hrDriftPct": null
      },
      "points": [
        { "distanceKm": 0.029, "timeSeconds": 10, "paceSecondsPerKm": 345, "heartRateBpm": 98 },
        { "distanceKm": 0.058, "timeSeconds": 20, "paceSecondsPerKm": null, "heartRateBpm": 103 }
      ]
    }
  ],
  "reps": {
    "mode": "pieces",
    "unavailableReason": null,
    "unavailableRunId": null,
    "pieceMinutes": 3,
    "piecesRunId": 20811000001,
    "average": {
      "earlier": { "paceSecondsPerKm": 252, "heartRateBpm": 166 },
      "later": { "paceSecondsPerKm": 244, "heartRateBpm": 163 },
      "paceDiffSecondsPerKm": -8,
      "heartRateDiffBpm": -3
    },
    "pairs": [
      {
        "earlier": { "paceSecondsPerKm": 255, "heartRateBpm": 160 },
        "later": { "paceSecondsPerKm": 246, "heartRateBpm": 158 },
        "paceDiffSecondsPerKm": -9,
        "heartRateDiffBpm": -2
      },
      {
        "earlier": { "paceSecondsPerKm": 251, "heartRateBpm": 168 },
        "later": { "paceSecondsPerKm": 243, "heartRateBpm": null },
        "paceDiffSecondsPerKm": -8,
        "heartRateDiffBpm": null
      },
      {
        "earlier": { "paceSecondsPerKm": 250, "heartRateBpm": 171 },
        "later": null,
        "paceDiffSecondsPerKm": null,
        "heartRateDiffBpm": null
      }
    ]
  }
}
```
Fallback example (fragment): `"resolution": "splits", "coarse": { "reason": "callLimit", "runId": null, "retryAfter": "2026-11-26T08:15:00Z" }`.

Types:
- **Top level:**
  - `resolution`: `"stream" | "splits"`, the same for both runs.
  - `coarse`: null exactly when `resolution` is `"stream"`, otherwise `{ reason, runId, retryAfter }`:
    - `reason`: `"notConfigured" | "beforeGarmin" | "treadmill" | "noDetailFile" | "callLimit" | "rateLimited" | "garminUnavailable"`.
    - `runId`: int for `beforeGarmin`, `treadmill` and `noDetailFile`, else null.
    - `retryAfter`: ISO-8601 UTC string for `callLimit` and `rateLimited`, else null.
  - `runs`: exactly 2, `[earlier, later]`.
- **`runs[i]`:**
  - `id`, `date`, `name`, `typeKey`, `treadmill`: as in the list.
  - `stats.distanceKm`: number with 1 decimal.
  - `stats.movingTimeSeconds`: int or null.
  - `stats.averagePaceSecondsPerKm`: int or null.
  - `stats.averageHeartRateBpm`: int or null.
  - `stats.elevationGainMeters`: int or null.
  - `stats.efficiencyFactor`: number with 3 decimals (m/min per bpm) or null.
  - `stats.hrDriftPct`: number with 1 decimal or null.
  - `points`: array, possibly empty, in x order.
- **`points[j]`:**
  - `distanceKm`: number with 3 decimals.
  - `timeSeconds`: int.
  - `paceSecondsPerKm`: int or null.
  - `heartRateBpm`: int or null.
- **`reps`:** null unless both runs are workouts.
  - `mode`: `"reps" | "pieces" | "unavailable"`.
  - `unavailableReason`: `"noDetailedData" | "repsNotFound"`, or null unless `mode` is `"unavailable"`.
  - `unavailableRunId`: int only for `repsNotFound`.
  - `pieceMinutes`, `piecesRunId`: ints only in `pieces` mode.
  - `average`: a row, or null in `unavailable` mode.
  - `pairs`: rows, where row i is number i + 1, and `[]` in `unavailable` mode.
- **Row:** `{ earlier: Cell | null, later: Cell | null, paceDiffSecondsPerKm: int | null, heartRateDiffBpm: int | null }`. The differences are later − earlier (negative = later faster / lower HR), and null when either side or HR is missing.
- **Cell:** `{ paceSecondsPerKm: int, heartRateBpm: int | null }`.

Errors (`{"error","message"}`): 400 `invalid_runs` ("a and b must be two different run ids.") · 404 `run_not_found` ("Run not found.") · 404 `person_not_configured` · 500 `internal_error`. Garmin failures never produce an error status. They are reported in `coarse`.

### Frontend (Angular)
- **Route:** `{ path: 'compare', loadComponent: () => import('./features/workout-compare/workout-compare-shell').then((m) => m.WorkoutCompareShell) }`, placed before `**`.
- **Nav menu:** add a third `routerLink="/compare"` "Compare" link with `routerLinkActive="active"`, `ariaCurrentWhenActive="page"` and `(click)="close()"`.
- **`WorkoutCompareShell`:** a copy of `RecoveryShell`, rendering `<app-workout-compare-page />`.
- **`WorkoutComparePage` (container):**
  - Standalone, OnPush, `providers: [SyncStore]`. It holds the state as signals:
    - `filter = signal<CompareFilter>('all')`
    - `list` state `loading | error | loaded(runs)`
    - `selected = signal<number[]>([])` (selection order, max 2)
    - `compare` state `idle | loading | error | loaded(data)`
    - `axis = signal<'distance' | 'time'>('distance')`
  - Two `Subject` + `switchMap` pipelines (latest wins), as in `RecoveryPage`. Each response is checked with its guard.
  - Quiet list reload on `dataVersion` (AC1.5).
  - `computed` view models come from the pure functions below. The template uses `@if`/`@for`/`@switch` only. There are no child components besides `app-chart`, `app-ring-spinner`, the nav menu, the person switch and the bell (the 023 pattern).
- **`workout-compare.options.ts` (pure):** `buildCompareOptions(data, axis)`, `compareTooltipLines(params, data, axis)` and `compareChartLabel(axis)`. They use `baseChartStyle`, `valueAxisStyle`, `tooltipStyle`, `palette`, `formatPace` and `formatRaceTime`.
- **`workout-compare.view.ts` (pure):** `runLabel(role, date)`, `typeLabel(typeKey)`, `listRowText(run)`, `emptyListText(filter)`, `promptText(count)`, `coarseNote(data)`, `missingPointsNote(data)`, `statsRows(data)`, `repHeaders(data)`, `repRows(data)`, `repsNote(data)`, `PIECES_CAVEAT` and `DRIFT_FOOTNOTE`. They reuse `formatShortDate`, `formatDayMonth`, `formatPace`, `formatPaceChange`, `formatBpmChange`, `formatRaceTime`, `formatTwoDecimals`, `formatSignedOneDecimal` and Angular `formatDate`.
- **Service & TS interfaces:**
  - `core/api/workout-compare.models.ts` mirrors the JSON above: `CompareFilter`, `CompareRunItem`, `CompareRunsResponse`, `CoarseReason`, `CoarseInfo`, `ComparePoint`, `CompareStats`, `CompareRun`, `RepCell`, `RepRow`, `RepComparison`, `WorkoutCompareResponse`.
  - It also has light guards: `isCompareRunsResponse` (`runs` is an array of objects with a number `id` and a string `date`) and `isWorkoutCompareResponse` (a string `resolution`, `runs` an array of length 2, each with a `stats` object and a `points` array).
  - `core/api/workout-compare.service.ts`: `getRuns(type): Observable<unknown>` → `GET /api/workout-compare/runs` with `params: { type }`. `compare(a, b): Observable<unknown>` → `GET /api/workout-compare` with `params: { a, b }`.
- **States:**
  - **List:** loading / error + Retry / empty per filter / normal (AC3.5).
  - **Compare area:**
    - Empty: the prompt (AC3.6).
    - Loading: spinner and "Loading comparison…" (`role="status"`).
    - Error: "Couldn't load the comparison." and a Retry button for the same pair. This covers an HTTP error, a failed guard and a 404 when a run was removed meanwhile.
    - Normal: in order, the coarse note, the x-axis toggle, the chart card (or the AC7.7 empty text), the caption, the stats card and the rep card.
- **Chart:** a single grid with x = distance (km) or time (min), y-axis 0 = pace (inverted, mm:ss /km) and y-axis 1 = HR (bpm). There are 4 lines (2 runs × pace solid / HR dashed), 2 legend entries and an axis tooltip (AC7.1–7.3). Pinch zoom comes from `app-chart` (024), at the default height of 300 px.

### Files expected to change
**Backend: new**
- `backend/src/main/kotlin/app/stravadashboard/domain/workoutcompare/WorkoutCompareModels.kt`, `CompareCalculator.kt`, `RepComparer.kt`, `WorkoutCompareService.kt`
- `backend/src/main/kotlin/app/stravadashboard/api/WorkoutCompareDtos.kt`, `WorkoutCompareMappers.kt`, `WorkoutCompareRoutes.kt`
- Tests: `domain/workoutcompare/CompareCalculatorTest.kt`, `RepComparerTest.kt`, `WorkoutCompareServiceTest.kt`, `api/WorkoutCompareRoutesTest.kt`

**Backend: edited**
- `domain/StreamRepPaceFinder.kt`: exposes `windows` and `DistanceCurve`. `find` is unchanged.
- `PersonStack.kt`:
  - creates `CallBudget` and `GarminActivitySource` once per person and passes both to `backgroundSync(...)` and `WorkoutCompareService`
  - passes `settings.garminActivitiesFrom` and `stores.syncState`
  - registers `workoutCompareRoutes(...)`
- `garmin/GarminActivitySource.kt` (Mutex around `fit()`, KDoc) and `garmin/GarminConnect.kt` (KDoc only)
- `api/ErrorResponse.kt`: `INVALID_TYPE`, `INVALID_RUNS`, `RUN_NOT_FOUND`
- Tests:
  - `api/PersonIsolationTest.kt`: add `/api/workout-compare/runs?type=all` to the route list, plus an isolation case
  - `garmin/GarminActivitySourceTest.kt`: concurrent FIT access
  - `domain/RepPaceTest.kt` must pass untouched

**Frontend: new**
- `frontend/src/app/core/api/workout-compare.models.ts`, `workout-compare.service.ts`, `workout-compare.service.spec.ts`
- `frontend/src/app/features/workout-compare/workout-compare-shell.ts`, `workout-compare-shell.spec.ts`
- `frontend/src/app/features/workout-compare/workout-compare-page.ts`, `.html`, `.scss`, `workout-compare-page.spec.ts`
- `frontend/src/app/features/workout-compare/workout-compare.options.ts`, `.spec.ts`
- `frontend/src/app/features/workout-compare/workout-compare.view.ts`, `.spec.ts`
- `frontend/src/app/testing/workout-compare-fixtures.ts`

**Frontend: edited**
- `frontend/src/app/app.routes.ts`, `frontend/src/app/app.spec.ts` (route test)
- `frontend/src/app/shared/nav-menu/nav-menu.html`, `nav-menu.spec.ts` (three links)

No config, `.env.example` or dependency changes.

## Edge cases
| Case | Expected behaviour |
|---|---|
| No activities in range / no runs of the type | 200 `{"runs":[]}`, and the list shows the empty text for that filter. Nothing can be compared |
| Only one run of the type | The list has one row, and the prompt says "Pick one more run to compare." (switching to All widens the choice) |
| Manual activity (no GPS, no FIT) | `speedStream` NotFound → `noDetailFile`, and both runs use splits. A manual run has no splits → `points: []` → AC7.7 note. Stats come from the summary |
| Activity without heart rate | `speedStream` is used, and the HR fields, HR series, EF, drift and rep HR are null (`—`). Pace is still compared. No error |
| Strava-era run (before the cut-over) | No Garmin call (`beforeGarmin`). Its stored Strava splits drive the chart, and its stored drift is used for long runs |
| Strava-era run with detail from before 016 (`splits` null) | `points: []` → AC7.7 note. Stats still shown |
| Treadmill run | No per-second call (`treadmill`). The watch-estimated splits are plotted and reps are unavailable (S12) |
| Garmin run not yet detail-synced | The stream can still be fetched (the file exists). Moving time is null until the detail sync, so pace and EF are null |
| Watch paused mid-run | Empty 10-s buckets have null pace and HR (a gap). The trapezoid can add a little phantom distance over the pause (existing rep-finder limit) |
| Walking or standing recoveries | Bucket pace slower than 10:00 /km → gap. HR keeps plotting |
| Workout shorter than its declared N×M | `repsNotFound` for that run, plus whole-session averages |
| One workout, one easy/long run | `reps: null`, so no rep section. Chart and stats as normal |
| Same run picked twice | Impossible in the UI. API → 400 `invalid_runs` |
| Run deleted between list and compare | 404 `run_not_found` → compare error state with Retry |
| Id from the other person | 404 `run_not_found` (per-person store) |
| Budget used up / Garmin 429 / Garmin down | Splits for both runs, a note with the reason and the time where known. The page still works |
| Person without a Garmin key | `notConfigured`. Splits for both runs, and Strava-era and stored data still compare |
| Sync running at the same time | FIT downloads are serialised by the Mutex, so the request may wait for the sync's download. No double download |
| Week spans DST / time zones | `date` = the `startLocal` date. Chart x counts from each run's start, so the zone doesn't matter. Ordering uses `startUtc` |
| Unit conversion | m → km (1 decimal for stats, metre precision for points), m/s → s/km via `paceOf` half-up, both in the domain/mappers. The frontend only formats |
| Same-day double run | Labels stay unique ("Earlier · 24 Nov" / "Later · 24 Nov"), and the order goes by `startUtc` |
| 2.5 h long run | About 900 points per run. Compression is already installed. No sampling needed |

## Privacy
- **Data fields & sensitivity:**
  - Shown per run: activity id, date, name and description-derived type (user text), distance, moving time, average HR, elevation from splits, EF and drift.
  - Chart: a 10-second pace and HR series per run, which is health data under GDPR Art. 9.
  - Rep pace and HR.
  - The per-second speed and HR samples are read from the FIT file in memory only.
  - No GPS or location: FIT positions are never read (021).
- **Storage & retention:**
  - Nothing new is stored server-side. Samples live in the existing 8-entry in-memory FIT cache and for the length of the request.
  - The browser keeps the response in memory only. Filter, selection and axis aren't written to `localStorage`/`sessionStorage`.
  - Existing run retention (6 months + 41 days) is unchanged.
- **Scopes:** no Strava scopes. The existing Garmin Personal API key, read-only, with no new Garmin endpoint.
- **Applicable privacy-check items:**
  - **1** ✅ No secrets. Fixtures (JSON, generated FIT/streams) are invented.
  - **2** ✅ The Garmin key and token stay in the backend and are never in responses, URLs or logs.
  - **3** ✅ The existing one-refresh token handling. A rejected key → `garminUnavailable`.
  - **4, 5** n/a: no OAuth and no scopes.
  - **6** ✅ Nothing stored. The response holds only chart-ready fields.
  - **7** ✅ No GPS read, stored or sent.
  - **8** ✅ At most one log line per request with the reason code. No HR or pace series, coordinates, key or URL. No `console` output.
  - **9** ✅ No client logging plugin change.
  - **10** ✅ Fixed error texts. Garmin failures are reported as reason codes only.
  - **11** ⚠ Existing (021): no auth, home network only. New: a GET can now trigger outbound Garmin calls, so anyone on the LAN could spend the person's call budget. The impact is bounded by the budget: at worst, syncs become partial.
  - **12** n/a: no cookie change.
  - **13** ✅ Run names render by interpolation only. The ECharts legend and tooltip HTML use only "Earlier/Later · d MMM" labels and numbers, never activity names. No `[innerHTML]`.
  - **14** n/a: GET only. No stored state changes (budget and FIT cache are in memory).
  - **15** ✅ Only the fields the page needs, no raw Garmin objects. Same origin.
  - **16** ✅ No new dependency, CDN or third party.
  - **17** ✅ Data stays local. Strava-era runs are shown only to their owner.
  - **18** ❌ Existing gap: no in-app delete (delete the DB file).
  - **19** n/a.
  - **20** ✅ Unchanged (no new data in the DB).
  - **21** ✅ Invented fixtures only.

## Test plan
| AC | Test type | Test name |
|---|---|---|
| AC1.1 | frontend component (NavMenu) | `R1 - menu lists Performance, Recovery and Compare` |
| AC1.2 | frontend (app routes) | `R1 - /compare shows the Compare page` |
| AC1.3 | frontend component (page) | `R1 - header, filter, list and compare area in order` |
| AC1.4 | frontend component (shell) | `R1 - flip recreates compare page with new person` |
| AC1.5 | frontend component | `R1 - reloads list quietly when dataVersion changes` |
| AC2.1 | backend route | `R2 - list returns the 30 latest runs of the type newest first` |
| AC2.2 | backend unit (service, fake repo) | `R2 - filter uses the run classifier` |
| AC2.3 | backend route | `R2 - list item fields and units` |
| AC2.4 | backend route | `R2 - invalid type gives 400` |
| AC2.5 | backend route | `R2 - empty store gives empty list` |
| AC2.6 | backend route (PersonIsolationTest) | `R2 - list follows X-Person` |
| AC2.7 | backend route | `R2 - list makes no Garmin calls` |
| AC3.1, AC3.2 | frontend component | `R3 - filter defaults to all and clears selection on change` |
| AC3.3 | frontend view unit + component | `R3 - row text, name and treadmill tag` |
| AC3.4 | frontend component | `R3 - third pick replaces the first and two picks request a comparison` |
| AC3.5 | frontend component | `R3 - list loading, error with retry, empty and normal` |
| AC3.6 | frontend view unit | `R3 - prompt for zero and one selected runs` |
| (service) | frontend service | `R2 - getRuns and compare call the endpoints with params` |
| AC4.1 | backend route | `R4 - runs are ordered earlier then later whatever a and b` |
| AC4.2 | backend route | `R4 - missing, malformed or equal ids give 400` |
| AC4.3 | backend route | `R4 - unknown or other person's run gives 404` |
| AC4.4 | backend route | `R4 - response shape` |
| AC5.1 | backend unit (service, fake source + budget) | `R5 - both outdoor Garmin runs fetch one stream each` |
| AC5.2 | backend unit | `R5 - up-front checks fall back to splits without calls` |
| AC5.3 | backend unit | `R5 - exhausted budget falls back with call limit and window end` |
| AC5.4 | backend unit | `R5 - rate limit stops calls and leaves sync state alone` |
| AC5.5, AC5.6 | backend unit | `R5 - unavailable and missing file fall back with reason` |
| AC5.7 | backend unit | `R5 - both runs share one resolution` |
| AC5.8 | backend unit | `R5 - compare stores nothing` |
| AC5.9 | backend client (MockEngine, two coroutines) | `R5 - concurrent detail and stream download the FIT once` |
| AC5.10 | backend unit + log capture | `R5 - compare logs only the reason code` |
| AC6.1–AC6.3 | backend unit (CompareCalculator) | `R6 - stream points in 10 second buckets`, `R6 - gaps for empty buckets and slow pace`, `R6 - time weighted HR skips long gaps` |
| AC6.4, AC6.5 | backend unit | `R6 - split points are cumulative`, `R6 - no splits gives no points` |
| AC7.1 | frontend options unit | `R7 - four series, two legend entries, run colours and styles` |
| AC7.2 | frontend options unit + component | `R7 - x axis switches between distance and time without a request` |
| AC7.3 | frontend options unit | `R7 - tooltip lines use labels and numbers only` |
| AC7.4 | frontend options unit | `R7 - single x axis so touch zoom applies` |
| AC7.5 | frontend options unit | `R7 - chart label follows the axis` |
| AC7.6 | frontend view unit | `R7 - coarse note text per reason` |
| AC7.7, AC7.8 | frontend component | `R7 - missing points notes and caption` |
| AC8.1–AC8.3 | backend unit (CompareCalculator) | `R8 - stats definitions and rounding`, `R8 - nulls for missing HR, time or elevation`, `R8 - drift from stored value or stream` |
| AC8.4 | frontend view unit + component | `R8 - stats table formats and drift footnote` |
| AC9.1 | backend unit (RepComparer) | `R9 - reps are the finder windows in time order` |
| AC9.2 | backend unit | `R9 - same count pairs rep N with rep N` |
| AC9.3 | backend unit | `R9 - extra reps listed alone` |
| AC9.4 | backend unit | `R9 - average over each run's own reps` |
| AC9.5 | backend unit | `R9 - no HR keeps pace differences` |
| AC9.6 | backend unit | `R9 - no rep comparison unless both are workouts` |
| AC10.1, AC10.2 | backend unit | `R10 - longer reps cut into pieces from the rep start`, `R10 - uneven length gives one piece per rep` |
| AC10.3 | backend unit | `R10 - piece N pairs with rep N and extras listed` |
| AC10.4 | backend unit | `R10 - all pieces against all reps average` |
| AC11.1, AC11.2 | backend unit | `R11 - splits resolution gives noDetailedData`, `R11 - missing windows give repsNotFound` |
| regression | backend unit | existing `RepPaceTest` passes after the `StreamRepPaceFinder` refactor |
| AC12.1, AC12.2 | frontend view unit + component | `R12 - rep table rows, all row and differences` |
| AC12.3 | frontend component | `R12 - pieces caveat shown only in pieces mode` |
| AC12.4 | frontend view unit | `R12 - unavailable note with whole-session averages` |
| AC13.1–AC13.3 | manual at 360 px and 1280 px + SCSS review | `R13 - compare page on mobile and desktop` |

## Out of scope
- Maps or GPS display.
- Comparing three or more runs, or comparing against the other person's runs.
- Non-run activities.
- The "gap" line (ahead/behind per km). Deferred by the intent.
- Lining up reps on the chart.
- Distance-named workouts (for example "6x1000m"). Time-based `NxMmin` only.
- Storing anything from the comparison (streams, rep results, drift for non-long runs).
- Remembering the filter, selection or axis after a reload.

## Suggested follow-ups
- Show one run's reps when only one of the two is a workout.
- For treadmill and Strava-era workouts, show the stored average rep pace (010: treadmill description pace, stored stream pace, fast laps) instead of only whole-session averages.
- A difference column in the stats block.
- Use Garmin's own record distance for the chart's distance axis (a port method that exposes FIT `distance`) instead of integrated speed.
- Store Garmin's `elevationGain` per run for a true total ascent.
- The deferred gap line.

## Risks & decisions to confirm
- **Like-for-like resolution:** if either run lacks per-second data, both runs use per-km splits, and no Garmin call is made when that is known up front. The alternative is one smooth line next to one per-km line. Please confirm.
- **Treadmill runs never fetch per-second data**, even though Garmin has a FIT file for them, because the intent (S12) treats them as having no detailed data and watch pace on a treadmill is unreliable.
- **A GET endpoint now calls Garmin during the request.** It's a new pattern: since 009, every read is store-only. It uses the same 80 / 15-min budget as the sync, typically 2 units and at most 4 per comparison. A re-opened pair still costs units, even though the FIT cache avoids a second download. Heavy use of Compare can make the next sync `partial`. It catches up in the next window.
- **HR drift keeps 013's 60-minute minimum,** so most interval sessions show `—` with a footnote. Please confirm that's acceptable, or say if drift should use a shorter minimum here.
- **Efficiency factor** uses 014's formula for every run, including workouts and treadmill runs, without 014's 20-minute and outdoor-only eligibility.
- **Elevation gain** is the sum of the positive per-km elevation differences. It undercounts rolling terrain within a kilometre, because no total-ascent field is stored.
- **Pieces are cut from the start of each longer rep** (the first M_short minutes, then the next), and the leftover at the end is dropped. Please confirm, or say if they should be centred or end-aligned instead.
- **The "All" average row is shown in every mode,** not only for pieces, so the table has one layout. Say if it should be hidden in same-length mode.
- **Plotting rules:** 10-second buckets, and pace slower than 10:00 /km becomes a gap (walking or standing recoveries).
- **Chart distance** is integrated from speed, the same way rep detection does it. It can differ from Garmin's total by around 1 %, while the stats show Garmin's distance.
- **Selection:** the default filter is All. A third pick replaces the first, and changing the filter clears the selection.
- **Compare starts the background sync** (the 023 Recovery decision), so a run just finished shows up without visiting Performance first.
- **Concurrency:** compare can overlap a sync on the same Garmin connection. The FIT cache gets a Mutex. The token cache stays unlocked, since a race at most costs one extra token request.

## Amendment (2026-11-26): zoom presets and narrow header
From the intent amendment (S14, S15).

- **AC7.9 (new, S15)** Next to the x-axis toggle, in the same wrapping row, a `role="group"` with `aria-label="Chart zoom"` holds three buttons, "1x" (the default), "2x" and "4x", each with `aria-pressed`.
  - 1x: no `dataZoom`; the chart shows the whole run, as before.
  - 2x / 4x: one `slider` dataZoom on x-axis 0 with `start: 0`, `end: 50` / `25`, `zoomLock: true` (the window keeps its width, so dragging only scrolls) and `filterMode: 'none'`. The grid's bottom grows to 72 px to fit it. The pace and HR axes stay in place while scrolling.
  - Changing the zoom or the x-axis sends no request and starts at the left edge again.
- **AC7.4 (changed)** 024's pinch zoom applies at 1x. At 2x / 4x the chart's own scroll bar replaces it: `withTouchZoom` keeps a chart's own `dataZoom`.
- **AC7.1 (changed)** A legend click doesn't rebuild the chart options, because a rebuild would reset the scroll bar. The page keeps the selection in a plain object: the tooltip reads it on each hover, and options rebuilt for an axis or zoom change start from it.
- **AC13.1 (fixed)** The shared `.page-header` wraps: when the person switch and bell don't fit beside the page title, they move to a second row, right-aligned. This also fixes the Recovery page, which scrolled sideways below about 400 px. Checked at 360 px (page 360 px wide) and 1280 px (one row).
- `echarts-setup.ts` registers `DataZoomSliderComponent`; a new palette entry `zoomWindow` (the accent at 12 %) colours the scroll window.

| AC | Test type | Test name |
|---|---|---|
| AC7.9 | frontend options unit | `R7 - 2x and 4x show part of the x axis with a scroll bar` |
| AC7.9 | frontend component | `R7 - zoom buttons pick 1x, 2x or 4x without a request` |
| AC7.4 | shared unit (touch-zoom) | `025 - a chart with its own dataZoom keeps it on touch` |
| AC7.1 | frontend options unit + component | `R7 - tooltip reads the legend selection on each hover`, `R7 - a legend click survives an axis switch` |
| AC13.1 | manual at 360 px and 1280 px | browser check (header wraps, no sideways scroll) |
