# Spec: Training load (fitness / fatigue / form)

**Feature ID:** 005 · **Status:** Approved · **Intent:** [intent.md](./intent.md) · **Date:** 2026-10-31

## Summary
The sync also fetches the heart-rate stream (`time`, `heartrate`) of every run that has heart rate. It reduces each stream in memory to a per-run histogram (seconds spent at each bpm) and stores only that. Per request, the backend turns the histograms into a load score per run (zone-weighted TRIMP with zones from `LTHR_BPM`), daily load, fitness (42-day average), fatigue (7-day average) and form (fitness − fatigue) over the last 6 months, plus a weekly ramp-rate check. It returns this ready to display from `GET /api/training-load`. A new "Training load" card directly under the "Marathon build" progress bar shows the three lines with a zero line for form, the latest values, and a facts-only ramp warning ("+14% vs last week"). The card loads and fails independently of the rest of the page.

## Requirements
| ID | Requirement | Priority (Must/Should/Could) |
|---|---|---|
| R1 | Config: optional `LTHR_BPM` (whole number, 100–220). Missing or invalid does not stop the app. The value is never logged | Must |
| R2 | History: the sync keeps runs from `today − 6 months − 41 days` (warm-up for the 42-day average). Existing databases re-list once to fetch the older runs | Must |
| R3 | Sync fetches `time,heartrate` streams for runs with heart rate that have no stored histogram, under the existing per-sync call cap. Only the derived histogram is stored | Must |
| R4 | Pure histogram builder: seconds per bpm from a heart-rate stream, skipping pauses and invalid samples | Must |
| R5 | Zones from LTHR (easy < ceil(87 % LTHR), subT up to LTHR, hard above) and a per-run load score: minutes in easy × 1 + subT × 2 + hard × 3 | Must |
| R6 | Daily load, fitness (42-day simple average), fatigue (7-day simple average) and form (fitness − fatigue) for every day from `today − 6 months` to today. Rest days are 0. No projection after today | Must |
| R7 | Ramp rate for this week so far and for last week, each against the week before it. Warning when the change is more than 10 %, except when the previous week was light (below half the average of the 6 weeks before it) | Must |
| R8 | Run counts for the window: runs counted, runs skipped for no heart rate, runs still waiting for their heart-rate stream | Must |
| R9 | `GET /api/training-load` returns the exact JSON below. It needs a connected session. Not configured → 404, invalid config → 422 | Must |
| R10 | Placement: the "Training load" card sits directly after the "Marathon build" card. It makes its own single request and shows and fails independently | Must |
| R11 | Chart: three distinguishable lines (fitness, fatigue, form), a zero line, a legend, a per-day tooltip, and the latest values above the chart | Must |
| R12 | Ramp warning UI: shown only when `warning` is true, with numbers and flags only. No advice text anywhere in the section | Must |
| R13 | Loading, not configured, invalid config, error, empty and normal states | Must |
| R14 | 002 dark theme tokens only. Readable at 375 px and desktop widths, no horizontal page scroll | Must |
| R15 | No regression: 001–004 behaviour and JSON unchanged, no new OAuth scope | Must |

## Acceptance criteria

**Conventions.** `today = LocalDate.now(clock.withZone(zone))` (server zone, as in 001 and 004). Runs are bucketed by the local date of `start_date_local`. Weeks are Monday to Sunday. "Run" means a stored activity with `sport_type == "Run"` (this includes treadmill/`trainer` runs and private runs). All examples use `today = 2026-10-31` (Saturday) and `LTHR_BPM = 175` unless stated otherwise. All numbers in fixtures are invented.

### R1 — Configuration
- **AC1.1** Given `LTHR_BPM` is unset or blank, when the app starts, then it starts normally and `AppConfig.lthr` is `LthrSetting.NotConfigured`.
- **AC1.2** Given `LTHR_BPM=175` (surrounding whitespace allowed), then `AppConfig.lthr` is `LthrSetting.Valid(Bpm(175))`.
- **AC1.3** Given `LTHR_BPM` is `abc`, `175.5`, `99` or `221`, then the app still starts and `AppConfig.lthr` is `LthrSetting.Invalid`. Given `100` and `220`, it is `Valid`.
- **AC1.4** Given any config, when `AppConfig.toString()` is called, then it contains `lthr=<set>`, `lthr=<unset>` or `lthr=<invalid>`, and never the number.
- **AC1.5** Given `.env.example`, then it lists `LTHR_BPM=` with no value and a comment: "Lactate-threshold heart rate in bpm (whole number, 100-220). Used by the training load chart. Restart the backend after changing it."

### R2 — History window
`historyStart(today) = today.minusMonths(6).minusDays(41)`. For today = 2026-10-31 that is 2026-03-20 (the window start 2026-04-30 minus 41 days).
- **AC2.1** Given an empty DB (first import), when a sync runs, then the first list call uses `after` = start of `historyStart − 1 day` in the server zone (2026-03-19T00:00 local). The old 5-month `BACKFILL_MONTHS` start is no longer used.
- **AC2.2** Given a sync finishes, then `pruneBefore(historyStart(today))` is called: runs with a local date before 2026-03-20 are deleted with their laps, stream paces and histograms. Runs on or after 2026-03-20 are kept.
- **AC2.3** Given a DB from before this feature (`sync_state.history_start` is null) with a set `listCursor`, when the next sync runs, then the list pass uses `after = historyStart − 1 day` (as on a first import), reads every page and upserts the summaries (keeping stored details, laps and stream paces). Once every page is read, it sets `history_start` to that `after` value and sets `listCursor` as the existing list pass does (newest stored start).
- **AC2.4** Given `history_start` is set and is on or before the current required `after`, then the list pass uses the existing incremental rule (`listAfter`, unchanged).
- **AC2.5** Given the DB file exists without the `history_start` column or the `run_hr_histograms` table, when the app starts, then the column and table are added and existing rows are kept.

### R3 — Heart-rate stream sync
- **AC3.1** Given a stored run with `averageHeartRateBpm != null` and no histogram row, when a sync runs and the call cap allows, then exactly one `GET /activities/{id}/streams?keys=time,heartrate&key_by_type=true` is made for it.
- **AC3.2** Given a run with `averageHeartRateBpm == null`, then no heart-rate stream call is made for it.
- **AC3.3** Given a run that already has a histogram row (status `COMPUTED` or `UNAVAILABLE`), then no heart-rate stream call is made for it again.
- **AC3.4 (priority and cap)** Given the phases run in the order `MISSING_DETAILS`, `MISSING_STREAMS`, `MISSING_HR_STREAMS`, `RECHECKS`, all sharing `MAX_ACTIVITY_CALLS_PER_SYNC = 80`, when 100 runs need a heart-rate stream and nothing else is pending, then one sync makes 80 heart-rate stream calls, newest run first, and the next sync makes the other 20.
- **AC3.5** Given runs still waiting for a heart-rate stream, then `SyncPolicy.pendingCount` includes them, so the sync stays due and the status is `PARTIAL` (the 001 sync banner shows the remaining count).
- **AC3.6 (success)** Given a `200` stream response with `time` and `heartrate` of equal length (at least 2 samples), then the histogram (R4) is stored with status `COMPUTED`. The stream itself is not stored.
- **AC3.7 (no usable data)** Given a `404`, or a `200` without a `heartrate` key, or with arrays of different lengths or fewer than 2 samples, then a row with status `UNAVAILABLE` and no histogram is stored.
- **AC3.8 (429)** Given a heart-rate stream call returns `429`, then the sync stops, `rateLimitedUntil` is saved as for the other calls (001 AC5.1), no row is stored for that run, and the next sync retries it after the limit.
- **AC3.9 (401)** Given a heart-rate stream call returns `401`, then the existing client refreshes the token once and retries. A second `401` deletes the tokens and the sync ends with `ReauthRequired`.
- **AC3.10 (network)** Given a timeout or 5xx, then the sync stops with `STRAVA_UNAVAILABLE`, nothing is stored for that run, and it is retried on the next sync.
- **AC3.11 (privacy)** Given the stream request, then its `keys` parameter is exactly `time,heartrate` (never `latlng`), and the log lines of the sync contain only the activity counts, never bpm values.

### R4 — Histogram builder
`HrHistogramBuilder.build(stream: HeartRateStream): HrHistogram`, with `MAX_SAMPLE_GAP_SECONDS = 30`.
- **AC4.1** Given samples `i` and `i+1`, then `dt = time[i+1] − time[i]` seconds is added to the bucket of `heartrate[i]` (whole bpm). The last sample adds nothing.
- **AC4.2** Given `time = [0, 1, 2, 3]` and `heartrate = [140, 140, 160, 180]`, then the histogram is `{140: 2, 160: 1}` (total 3 s).
- **AC4.3 (pauses)** Given a gap `dt > 30` (e.g. `time = [0, 1, 200, 201]`), then that interval is skipped. Given `dt <= 0`, it is skipped too.
- **AC4.4 (invalid bpm)** Given `heartrate[i] <= 0`, then that interval is skipped.
- **AC4.5** Given every interval is skipped, then the histogram is empty and the status is still `COMPUTED` (the run counts with load 0).

### R5 — Zones and per-run load
`HrZones.of(lthr)`: `easyBelowBpm = ceil(lthr × 87 / 100)` in integer maths (`(lthr × 87 + 99) / 100`), `hardAboveBpm = lthr`. Easy: `bpm < easyBelowBpm`. SubT: `easyBelowBpm <= bpm <= lthr`. Hard: `bpm > lthr`.
- **AC5.1** Given LTHR 175, then `easyBelowBpm = 153` and `hardAboveBpm = 175`: 152 is easy, 153 and 175 are subT, 176 is hard. Given LTHR 160, then `easyBelowBpm = 140`.
- **AC5.2** Given a histogram with 1800 s at 140 bpm, 1200 s at 160 bpm and 600 s at 180 bpm, and LTHR 175, then the run's load is `30 × 1 + 20 × 2 + 10 × 3 = 100.0`.
- **AC5.3 (S7)** Given the same histogram and LTHR 160, then 140 and 160 are subT and 180 is hard, so the load is `30 × 2 + 20 × 2 + 10 × 3 = 130.0`. No stream call is needed for the change: the load is recomputed from stored histograms on every request.
- **AC5.4** Given an empty histogram, then the load is `0.0`.
- **AC5.5** Loads are kept unrounded inside the domain. Only API output is rounded (R9).

### R6 — Daily load, fitness, fatigue, form
`windowStart = today.minusMonths(6)` (2026-04-30), `windowEnd = today`. `FITNESS_DAYS = 42`, `FATIGUE_DAYS = 7`.
- **AC6.1** Given the window, then `days` has one entry per date from `windowStart` to `today` inclusive, in ascending order: 185 entries for today = 2026-10-31. There is no entry after today.
- **AC6.2** `load(d)` is the sum of the loads of the runs (status `COMPUTED`) whose local date is `d`. A day with no such run has load `0.0`. Two runs on the same date add up.
- **AC6.3** `fitness(d) = (sum of load over d−41 … d) / 42`, `fatigue(d) = (sum of load over d−6 … d) / 7`, and `form(d) = fitness(d) − fatigue(d)`, all from unrounded values. Runs from `historyStart` onwards are used, so `fitness(windowStart)` already includes the 41 days before the window.
- **AC6.4** Given a single run with load 84.0 on day `D` and no other runs, then on `D` fatigue = 12.0, fitness = 2.0 and form = −10.0. On `D+7` fatigue = 0.0, fitness = 2.0 and form = 2.0. On `D+42` fitness = 0.0.
- **AC6.5 (S3)** Given 42 days in a row with load 50.0 followed by 7 days with load 100.0, then on the last day fatigue = 100.0, fitness = 58.3 and form = −41.7, so fatigue rose by 50.0 and fitness by 8.3.
- **AC6.6 (time zones)** Given a run with `start_date = 2026-10-25T23:30:00Z` and `start_date_local = 2026-10-26T00:30:00Z` (Strava's trailing `Z` on local time), then its load counts on 2026-10-26.
- **AC6.7** Given a run whose status is `UNAVAILABLE`, or with `averageHeartRateBpm == null`, or still without a histogram row, then it adds nothing to any day.
- **AC6.8** Given no runs at all, then all 185 days have load, fitness, fatigue and form `0.0`.

### R7 — Ramp rate
Week `W0` is the week containing today (2026-10-26 … 2026-11-01), `W-1` the week before, and so on. `weekLoad(W)` = sum of daily loads in `W` up to today. `RAMP_THRESHOLD_PERCENT = 10`, `LIGHT_WEEK_FACTOR = 0.5`, `BASELINE_WEEKS = 6`.
- **AC7.1** `thisWeek` compares `weekLoad(W0)` (Monday to today, `weekToDate = true`) with `weekLoad(W-1)`. Its baseline is the average of `W-2 … W-7`. `lastWeek` compares `weekLoad(W-1)` (`weekToDate = false`) with `weekLoad(W-2)`. Its baseline is the average of `W-3 … W-8`.
- **AC7.2** `changePercent = roundHalfUp((current − previous) / previous × 100)` as an integer. Given current 512.0 and previous 449.0, then `changePercent = 14`.
- **AC7.3** `warning = changePercent > 10 && !previousWeekLight`. Given `changePercent = 14`, then `warning = true`. Given `changePercent = 10` (e.g. 110.4 vs 100.0), then `warning = false`. Given 11, then `true`.
- **AC7.4 (light previous week)** `previousWeekLight = previous < 0.5 × baseline`. Given previous = 140.0, baseline weeks averaging 300.0 and current = 400.0, then `changePercent = 186`, `previousWeekLight = true` and `warning = false`. Given previous = 150.0 (exactly half), then `previousWeekLight = false`.
- **AC7.5 (zero previous)** Given previous = 0.0, then `changePercent = null`, `changeLabel = null` and `warning = false`.
- **AC7.6** `changeLabel` is `"+14% vs last week"` for `thisWeek` and `"+14% vs the week before"` for `lastWeek`. A drop reads `"-5% vs last week"`, and no change reads `"0% vs last week"`.
- **AC7.7** Given today is a Monday, then `thisWeek` holds only today's load, and `lastWeek` holds the full week just ended.

### R8 — Run counts
Counts cover runs whose local date is in `windowStart … today`. Warm-up runs before `windowStart` are used for the numbers but not counted.
- **AC8.1** `runsCounted` = runs with status `COMPUTED`.
- **AC8.2** `runsSkippedNoHeartRate` = runs with `averageHeartRateBpm == null` plus runs with status `UNAVAILABLE`.
- **AC8.3** `runsPendingHeartRate` = runs with `averageHeartRateBpm != null` and no histogram row yet.
- **AC8.4** Given 10 runs in the window (7 computed, 2 without heart rate, 1 pending) and 3 runs before it, then the counts are 7, 2 and 1.
- **AC8.5** Given rides, walks or other non-Run activities on Strava, then they are not stored (existing 001 rule) and appear in no count or load.

### R9 — API
- **AC9.1** Given a connected session and a valid `LTHR_BPM`, when `GET /api/training-load` is called, then the response is `200` with exactly the fields in the Design section (no extra fields, no activity ids, names or bpm samples).
- **AC9.2** Given no session cookie, or a session for another athlete, then `401 {"error":"not_connected",…}`.
- **AC9.3** Given `LTHR_BPM` is not configured, then `404 {"error":"training_load_not_configured","message":"No threshold heart rate is configured."}` and no sync is started.
- **AC9.4** Given `LTHR_BPM` is invalid, then `422 {"error":"training_load_invalid_config","message":"LTHR_BPM is not a whole number between 100 and 220."}` and no sync is started. The message never contains the configured value.
- **AC9.5** Given valid config, then the route calls `SyncService.syncIfDue()` first (as `GET /api/dashboard/running` does) and computes from the local store afterwards. Given the dashboard request runs a sync at the same time, then this request waits on the same mutex and reuses its outcome, so no second sync starts.
- **AC9.6** Given the sync ends with `ReauthRequired`, then `401 {"error":"strava_reauth_required",…}`. Given it ends rate-limited or with Strava unavailable, then `200` from the stored data.
- **AC9.7** Rounding: `load`, `fitness`, `fatigue`, `form` and the ramp `load`/`previousLoad` are rounded half-up to 1 decimal. `form` is rounded from the unrounded difference (not the difference of rounded values).

### R10 — Placement and independence
- **AC10.1** Given the dashboard page in any state, then `app-training-load-section` is rendered directly after `app-calendar-like-build` and before the running dashboard's `@switch` output.
- **AC10.2** Given the page loads, then exactly three GET requests are made: `/api/dashboard/running`, `/api/training-plan` and `/api/training-load` (once). None waits for another.
- **AC10.3** Given `/api/training-load` fails with 404, 422, 500 or a network error, then the running dashboard and the plan cards still render their own states. Given those fail or are loading, then the training load card still renders its own state.
- **AC10.4** Given `/api/training-load` returns 401, then the shared interceptor handles it (redirect to `/connect`) and the card shows no error text.

### R11 — Chart and latest values
- **AC11.1** Given a normal response, then the card shows `h2` "Training load", then a row of three values from `latest`: "Fitness 52.1", "Fatigue 61.3" and "Form -9.2" (form always signed: `+3.2`, `-9.2`, `0.0`), with the caption "on Sat 31 Oct".
- **AC11.2** Given `days`, then `buildTrainingLoadOptions(response)` returns an ECharts line chart with a category x axis of the dates (labels like "30 Apr") and one y axis named "Load / day". It has three series named "Fitness (42-day)", "Fatigue (7-day)" and "Form", in that order, each with one point per day, coloured `palette.fitness`, `palette.fatigue` and `palette.form`, with no symbols on points.
- **AC11.3 (S4)** The "Form" series has a `markLine` at `y = 0`, dashed, in `palette.textMuted`, labelled "0". The y axis includes negative values when form is negative (no `min: 0`).
- **AC11.4** A legend at the top shows the three series names. Clicking a legend entry hides or shows that line (ECharts default).
- **AC11.5 (S1)** The tooltip (`trigger: 'axis'`) for a day shows, one per line: "Sat 31 Oct", "Fitness 52.1", "Fatigue 61.3", "Form -9.2", "Load 0.0". It is built only from dates and numbers in the response (no Strava text).
- **AC11.6** The chart's `aria-label` is "Training load: fitness, fatigue and form per day, last 6 months".
- **AC11.7** Below the chart, a muted footnote shows `zones.label` and "Based on 96 runs with heart rate · 2 runs without heart rate skipped". The skipped part is left out when `runsSkippedNoHeartRate = 0`. "1 run" is singular.
- **AC11.8** Given `runsPendingHeartRate > 0`, then the footnote adds "5 runs still importing heart rate; numbers will update."

### R12 — Ramp warning and facts only
- **AC12.1** Given `ramp.thisWeek.warning = true`, then the card shows a callout above the chart with `role="status"`: "This week so far: +14% vs last week (load 512.0 vs 449.0)".
- **AC12.2** Given `ramp.lastWeek.warning = true`, then the card shows the callout "Last week: +12% vs the week before (load 449.0 vs 401.0)". Both callouts can show together, with `thisWeek` first.
- **AC12.3** Given both `warning` values are false (including a light previous week or `changePercent = null`), then no callout element is rendered.
- **AC12.4** The callout has a 1px `--color-worse` border and a `--color-surface` background. Its text uses `--color-text`.
- **AC12.5 (S6)** Given each state of the card (loading, not configured, invalid config, error, both empty states, normal with and without callouts), then its rendered text matches none of these whole words, case-insensitive: `rest`, `recover`, `recovery`, `should`, `must`, `reduce`, `overtrained`, `overtraining`, `taper`. (`restart` in the config hint is a different word and is allowed.) A component test checks the rendered text of every state.

### R13 — States
- **AC13.1 (loading)** Given the request is pending, then the card shows `h2` "Training load" and "Loading training load…" in a `role="status"` element, and no chart.
- **AC13.2 (not configured)** Given `404 training_load_not_configured`, then it shows "No threshold heart rate set up yet." and the muted hint "Set LTHR_BPM in .env (for example 175), then restart the backend." No Retry button.
- **AC13.3 (invalid config)** Given `422 training_load_invalid_config` with message `M`, then it shows "Training load settings have a problem: M" (text interpolation) in `role="alert"`, plus the same restart hint. No Retry button.
- **AC13.4 (error)** Given a 500, a network error or a body that fails `isTrainingLoadResponse`, then it shows "Couldn't load training load." in `role="alert"` with a Retry button in the 002 style. Retry sets loading and sends the request again.
- **AC13.5 (empty, nothing pending)** Given `runsCounted = 0` and `runsPendingHeartRate = 0`, then it shows "No runs with heart rate in the last 6 months" and, if `runsSkippedNoHeartRate > 0`, "3 runs without heart rate skipped". No chart, no latest values, no callouts.
- **AC13.6 (empty, still importing)** Given `runsCounted = 0` and `runsPendingHeartRate > 0`, then it shows "Heart-rate data is still importing: 12 runs to go. Reload in a while." No chart.
- **AC13.7 (normal)** Given `runsCounted > 0`, then it shows heading → latest values → callouts (if any) → chart → footnote.

### R14 — Look and layout
- **AC14.1** The card uses the 002 card look (`--color-surface`, 1px `--color-border`, 12px radius, no shadow) and the same vertical gap as the other cards. Every colour in component styles is a `var(--color-*)` token. Chart colours come from `shared/chart/palette.ts`.
- **AC14.2** `palette.ts` gains `fitness` (= the `accent` value), `fatigue` (= the `treadmill` value, `#a78bfa`) and `form` (= the `text` value). No new colour values are introduced.
- **AC14.3** The latest values use the large-number style (at least `1.25rem`, weight 700, `tabular-nums`), with small uppercase muted labels.
- **AC14.4** Given a viewport at least 720 px wide, then the three latest values sit in one row. Given 375 px, then they wrap, the legend wraps, the chart fills the card width at the shared 300 px height, and the page has no horizontal scroll.

### R15 — No regression
- **AC15.1** Given the diff, then `RunningDashboardRoutes.kt`, `RunningDashboardDtos.kt`, `running-dashboard.models.ts`, `TrainingPlanRoutes.kt`, `training-plan.models.ts` and `api-error.interceptor.ts` are unchanged, and `REQUIRED_SCOPE` stays `activity:read_all`.
- **AC15.2** Given the existing page specs (001–004), then they pass with only a setup change: `TrainingLoadSection` is replaced by a stub through `TestBed.overrideComponent(RunningDashboardPage, …)`, so their request counts are unchanged.
- **AC15.3** Given the 001 aggregates (weekly distance 9 weeks, HR trend 5 months, pace comparison windows), when the store now also holds older runs (R2), then their outputs for the same runs in their windows are unchanged.
- **AC15.4** Given the backend and frontend check commands, then everything passes.

## Traceability
| Success criterion (intent) | Covered by |
|---|---|
| S1 (6-month chart, three lines, read values per day) | R6 (AC6.1), R9, R11 (AC11.1, AC11.2, AC11.5) |
| S2 (every run with HR, incl. treadmill and private, contributes, based on zone time; rides/walks don't) | R3, R4, R5, R8 (AC8.5); private runs via the existing `activity:read_all` scope (R15) |
| S3 (42-day fitness, 7-day fatigue, form = difference; hard-week behaviour) | R6 (AC6.3–AC6.5) |
| S4 (form positive/negative clearly shown) | R11 (AC11.1, AC11.3) |
| S5 (warning when weekly load is up > 10 %, none otherwise) | R7, R12 (AC12.1–AC12.3) |
| S6 (facts only, no advice) | R12 (AC12.5), R11, R13 texts |
| S7 (changing thresholds in config changes load, no code change) | R1, R5 (AC5.3). **Threshold pace: see Risk 1** |
| S8 (002 dark look, desktop + mobile) | R14 |
| Decision 1 (runs without HR skipped, count shown) | R6 (AC6.7), R8, R11 (AC11.7), R13 (AC13.5) |
| Decision 2 (no projection, data up to today only) | R6 (AC6.1) |
| Decision 3 (directly under "Marathon build") | R10 (AC10.1) |
| Decision 4 (percentage change shown) | R7 (AC7.6), R12 |
| Decision 5 (no warning after a light week) | R7 (AC7.4) |
| Assumption: config missing/invalid → message, rest keeps working | R1, R9 (AC9.3, AC9.4), R10 (AC10.3), R13 |

## Design

### Data from Strava
- **Endpoints:**
  - `GET /athlete/activities?after=<epoch>&page=<n>&per_page=200`: unchanged, except the first-import / one-time `after` (R2).
  - **New:** `GET /activities/{id}/streams?keys=time,heartrate&key_by_type=true`: one call per run with heart rate, once per run.
  - `GET /activities/{id}` and the speed-stream call: unchanged.
- **Scopes:** no change. The existing `activity:read_all` already covers private runs. The Strava heart-rate zones endpoint is not used.
- **Fields used:** existing summary fields (`average_heartrate` decides whether a stream call is made). From the stream: `time.data` (int seconds) and `heartrate.data` (int bpm). `StreamSetDto` gains `heartrate: IntStreamDto? = null`. Nothing else is declared.
- **Sync & caching:** the sync is still triggered by API requests when due (every 15 min, or while anything is pending). The heart-rate phase `MISSING_HR_STREAMS` comes after `MISSING_STREAMS` and shares the 80-call cap, newest run first. Each run is streamed at most once. After that, the load is computed from the stored histograms on every request (cheap: about 200 runs × about 100 buckets). Rough cost of the first rollout: about 150–200 stream calls plus detail calls for the older runs, spread over 3–4 syncs, within Strava's default read limits of 100 requests / 15 min and 1,000 / day.
- **Pagination:** the existing loop: `per_page=200`, stop when a page has fewer than 200 items.
- **Errors:** 401 → one refresh and retry, then tokens deleted and `401 strava_reauth_required` (AC3.9). 429 → sync stops, `rateLimitedUntil` saved, stored data served (AC3.8). Network failure or 5xx → `STRAVA_UNAVAILABLE`, stored data served (AC3.10). 404 on a stream → `UNAVAILABLE` row (AC3.7).

### Domain model & logic
New domain types (`domain/model/`):
```kotlin
@JvmInline value class Bpm(val value: Int)
data class HeartRateStream(val times: List<Seconds>, val bpm: List<Bpm>)          // equal length, size >= 2
data class HrHistogram(val secondsByBpm: Map<Int, Int>)                             // bpm -> seconds
enum class HrHistogramStatus { COMPUTED, UNAVAILABLE }
data class HrHistogramResult(val status: HrHistogramStatus, val histogram: HrHistogram?)
// RunActivity gains: val hrHistogram: HrHistogramResult?   (null = not fetched yet)
```
`ActivitySource` gains `suspend fun heartRateStream(id: ActivityId): SourceResult<HeartRateStream>` (no usable data → `NotFound`, like `speedStream`). `RunRepository` gains `saveHrHistogram(id, result, computedAt)`.

Config (`config/`): `sealed interface LthrSetting { NotConfigured; Invalid; data class Valid(val lthr: Bpm) }`, with `MIN_LTHR_BPM = 100` and `MAX_LTHR_BPM = 220`.

Package `domain/trainingload/` (pure, no Ktor, Exposed or Strava types, each unit-tested):
- `HrHistogramBuilder.build(stream): HrHistogram` (R4).
- `HrZones(easyBelowBpm, hardAboveBpm)` with `of(lthr: Bpm)` and `EASY_PERCENT_OF_LTHR = 87`, plus `TrimpCalculator.load(histogram, zones): Double` with weights `EASY_WEIGHT = 1`, `SUBT_WEIGHT = 2` and `HARD_WEIGHT = 3` per minute (R5). This is Lucia's three-zone TRIMP with the zones agreed in the intent.
- `TrainingLoadCalculator.compute(runs, zones, today): TrainingLoad` → days (R6) and counts (R8). It uses `LocalDate` only, so DST can't shift a day.
- `RampRate.compute(dailyLoads, today): Ramp` (R7).
- `TrainingLoadService(lthr: LthrSetting, tokens, syncService, runs, clock, zone)`: `suspend fun get(): TrainingLoadResult`, with `sealed interface TrainingLoadResult { NotConnected; NotConfigured; InvalidConfig; ReauthRequired; data class Ready(val load: TrainingLoad, val ramp: Ramp, val zones: HrZones) }`. It checks the config first, then the tokens, then calls `syncIfDue()`, then computes.
- `SyncPolicy`: `HISTORY_MONTHS = 6`, `LOAD_WARMUP_DAYS = 41`, `historyStart(today)`. `backfillStart` → `historyStart − 1 day` at start of day. `pruneCutoff` → `historyStart`. `listAfter` treats `historyStart == null || historyStart > required` like a missing cursor (R2). `BACKFILL_MONTHS` and `RETENTION_MONTHS` are replaced. `needsHrStream(run) = run.averageHeartRateBpm != null && run.hrHistogram == null` is added to `pendingCount`. New `SyncPhase.MISSING_HR_STREAMS`.
- `SyncService.fetchHrStream(run, now)`: same shape as `fetchStream` (success → build histogram → save `COMPUTED`; `NotFound` → save `UNAVAILABLE`; anything else → return the failure).

Units and rounding: bpm are whole numbers. Durations are whole seconds in the domain and are converted to minutes (`/ 60.0`) only inside `TrimpCalculator`. API values are rounded half-up to 1 decimal with the existing `roundOneDecimal`, and percentages to integers with `roundHalfUpToInt`.

### Persistence
- New table `run_hr_histograms` (`RunHrHistogramsTable`): `run_id` long PK, FK → `runs.id` `ON DELETE CASCADE`. `status` text (`computed` / `unavailable`, lowercase like `run_stream_paces`). `histogram` text, nullable: `bpm:seconds` pairs sorted by bpm and joined with `,` (e.g. `"140:1800,160:1200,180:600"`), null when unavailable. `computed_at` long (epoch seconds). Encoding and decoding live in `RunRowMappers.kt`.
- `sync_state` gains `history_start`, long, nullable (epoch seconds of the `after` used by the last full list pass from `historyStart`). `SyncState` gains `historyStart: Instant?`.
- Migration (`DatabaseFactory.connect`): add `RunHrHistogramsTable` to `SchemaUtils.create(...)`. Add the column with `ALTER TABLE sync_state ADD COLUMN history_start INTEGER` only when `PRAGMA table_info(sync_state)` doesn't list it (AC2.5).
- `ExposedRunRepository.findAll` left-joins the histograms. `delete`, `pruneBefore` (via cascade) and `deleteAll` (an explicit `RunHrHistogramsTable.deleteAll()` before `RunsTable`) remove them.

### Backend API
#### `GET /api/training-load`
- Query params: none. Auth: `hasConnectedSession(tokens)`, otherwise `401 not_connected`.
- `200` response (exact shape; invented values, today = 2026-10-31; `days` is shortened here, but the real list always has one entry per day):
```json
{
  "windowStart": "2026-04-30",
  "windowEnd": "2026-10-31",
  "fitnessDays": 42,
  "fatigueDays": 7,
  "zones": {
    "lthrBpm": 175,
    "easyBelowBpm": 153,
    "hardAboveBpm": 175,
    "label": "Zones from LTHR 175 bpm: easy < 153 · subT 153–175 · hard > 175"
  },
  "latest": { "date": "2026-10-31", "fitness": 52.1, "fatigue": 61.3, "form": -9.2 },
  "days": [
    { "date": "2026-04-30", "load": 0.0, "fitness": 41.2, "fatigue": 38.0, "form": 3.2 },
    { "date": "2026-10-31", "load": 0.0, "fitness": 52.1, "fatigue": 61.3, "form": -9.2 }
  ],
  "ramp": {
    "thisWeek": {
      "weekStart": "2026-10-26",
      "weekEnd": "2026-11-01",
      "weekToDate": true,
      "load": 512.0,
      "previousLoad": 449.0,
      "changePercent": 14,
      "previousWeekLight": false,
      "warning": true,
      "changeLabel": "+14% vs last week"
    },
    "lastWeek": {
      "weekStart": "2026-10-19",
      "weekEnd": "2026-10-25",
      "weekToDate": false,
      "load": 449.0,
      "previousLoad": 437.5,
      "changePercent": 3,
      "previousWeekLight": false,
      "warning": false,
      "changeLabel": "+3% vs the week before"
    }
  },
  "runsCounted": 96,
  "runsSkippedNoHeartRate": 2,
  "runsPendingHeartRate": 0
}
```
Field rules: dates are `YYYY-MM-DD`. `load`, `fitness`, `fatigue` and `form` are JSON numbers in load units per day (TRIMP points, 1 decimal). The ramp `load` and `previousLoad` are weekly sums (1 decimal). `latest` equals the last element of `days`, and `days` is never empty. `changePercent` and `changeLabel` are either both null or both set. `warning` implies `changePercent > 10` and `previousWeekLight == false`. `weekEnd` is always the Sunday, even when `weekToDate` is true. Counts are non-negative integers.
- Errors (`{"error","message"}`, constants in `ErrorResponse`):

| Status | `error` | `message` |
|---|---|---|
| 401 | `not_connected` | existing text |
| 401 | `strava_reauth_required` | existing text |
| 404 | `training_load_not_configured` | "No threshold heart rate is configured." |
| 422 | `training_load_invalid_config` | "LTHR_BPM is not a whole number between 100 and 220." |
| 500 | `internal_error` | existing StatusPages handler |

DTOs live in `api/TrainingLoadDtos.kt` (`TrainingLoadResponse`, `LoadZonesDto`, `LoadDayDto`, `RampDto`, `WeekRampDto`), and the mapping (rounding, labels) in `api/TrainingLoadMappers.kt`. `zones.label` and `changeLabel` are built in the mapper from domain numbers. The route `api/TrainingLoadRoutes.kt` stays thin: session check → `service.get()` → `when` → respond.

### Frontend (Angular)
- **Route / page:** no new route. `RunningDashboardPage` imports `TrainingLoadSection`. Its template becomes `<h1>Running</h1>`, `<app-calendar-like-build />`, **`<app-training-load-section />`**, then the unchanged `@switch` and `<app-build-calendar-section />`.
- **Service & interfaces** (`core/api/`):
  - `training-load.models.ts`: `TrainingLoadResponse`, `LoadZones`, `LoadDay`, `LoadRamp` and `WeekRamp` (with `changePercent: number | null` and `changeLabel: string | null`), matching the JSON field for field. Also `isTrainingLoadResponse(value: unknown): value is TrainingLoadResponse`, a shape guard for the top-level fields and arrays.
  - `training-load.service.ts`: `getTrainingLoad(): Observable<TrainingLoadResponse>` → `GET /api/training-load`, `providedIn: 'root'`.
- **Components** (`features/training-load/`, standalone, `OnPush`, plain interpolation only):
  - `training-load-section.ts` (`TrainingLoadSection`, `app-training-load-section`): the container. It holds `signal<LoadState>` (`loading | notConfigured | invalidConfig(message) | error | loaded(data)`), loads in `ngOnInit` with `takeUntilDestroyed`, leaves the state unchanged on 401, and has `retry()`. It renders `section.card` with `h2` "Training load" and the per-state texts from R13. In the loaded state it renders the latest values, `<app-ramp-warning>`, `<app-training-load-chart>` and the footnote.
  - `ramp-warning/ramp-warning.ts` (`ramp = input.required<LoadRamp>()`): the R12 callouts. The text comes from `rampWarningTexts(ramp): string[]` in `training-load.view.ts`.
  - `training-load-chart/training-load-chart.ts` (`load = input.required<TrainingLoadResponse>()`): `<app-chart>` with `buildTrainingLoadOptions`.
  - `training-load.view.ts` (pure, unit-tested): `loadStateFromError(error)`, `rampWarningTexts`, `footnoteText(data)`, `emptyText(data)`, `isEmpty(data)`.
- **Chart options:** `features/training-load/training-load.options.ts`: `buildTrainingLoadOptions(data): EChartsCoreOption` (AC11.2–AC11.6). A line chart, x = `days[].date` shown as "30 Apr", y = "Load / day". Series: Fitness (`palette.fitness`, solid), Fatigue (`palette.fatigue`, solid) and Form (`palette.form`, solid, with the dashed zero `markLine`). Line width 2, `showSymbol: false`, legend at the top, the existing chart theme, and the tooltip from AC11.5.
- **Formatting** (`shared/format/`): `numbers.ts` adds `formatOneDecimal(52.14) → "52.1"` and `formatSignedOneDecimal`: `(-9.2) → "-9.2"`, `(3.2) → "+3.2"`, `(0) → "0.0"`. `dates.ts` adds `formatDayMonth("2026-04-30") → "30 Apr"` and `formatShortDate("2026-10-31") → "Sat 31 Oct"`. Both parse the date as UTC, so the browser's time zone can't shift it.
- **States:**

| View | Loading | Empty | Error | Normal |
|---|---|---|---|---|
| Training load card | heading + "Loading training load…" (`role="status"`) | AC13.5 (no HR runs) or AC13.6 (still importing); no chart | 404 → not configured + hint, no Retry. 422 → settings problem + hint, no Retry. Other → "Couldn't load training load." + Retry (`role="alert"`) | latest values → callouts (if any) → chart → footnote |
| Ramp callouts | not rendered | not rendered | not rendered | only for weeks with `warning = true` |
| Chart | not rendered | not rendered | not rendered | 3 lines + zero line, legend, tooltip |
| Rest of page | its own 001–004 states, unaffected | same | same | same |

### Files expected to change
Backend (`backend/`):
- `src/main/kotlin/app/stravadashboard/config/AppConfig.kt`: `lthr: LthrSetting`, parsing, `toString`
- `src/main/kotlin/app/stravadashboard/config/LthrSetting.kt` (new)
- `src/main/kotlin/app/stravadashboard/strava/StravaClient.kt`: `heartRateStreams(id)`, and the speed-stream comment updated
- `src/main/kotlin/app/stravadashboard/strava/StravaDtos.kt`: `StreamSetDto.heartrate`
- `src/main/kotlin/app/stravadashboard/strava/StravaMappers.kt`: `toHeartRateStream()`
- `src/main/kotlin/app/stravadashboard/strava/StravaActivitySource.kt`: `heartRateStream`
- `src/main/kotlin/app/stravadashboard/domain/model/Units.kt` (`Bpm`), `domain/model/HeartRate.kt` (new: `HeartRateStream`, `HrHistogram`, status/result), `domain/model/RunActivity.kt` (`hrHistogram`)
- `src/main/kotlin/app/stravadashboard/domain/ports/ActivitySource.kt`, `RunRepository.kt`, `SyncStateRepository.kt` (`historyStart`)
- `src/main/kotlin/app/stravadashboard/domain/SyncPolicy.kt`, `SyncService.kt`
- `src/main/kotlin/app/stravadashboard/domain/trainingload/` (new): `HrHistogramBuilder.kt`, `HrZones.kt`, `TrimpCalculator.kt`, `TrainingLoadCalculator.kt`, `RampRate.kt`, `TrainingLoad.kt` (models), `TrainingLoadService.kt`
- `src/main/kotlin/app/stravadashboard/persistence/Tables.kt`, `DatabaseFactory.kt`, `ExposedRunRepository.kt`, `RunRowMappers.kt`, `ExposedSyncStateRepository.kt`
- `src/main/kotlin/app/stravadashboard/api/TrainingLoadRoutes.kt`, `TrainingLoadDtos.kt`, `TrainingLoadMappers.kt` (new), `ErrorResponse.kt`
- `src/main/kotlin/app/stravadashboard/Application.kt`: wiring
- Tests: `src/test/kotlin/app/stravadashboard/domain/trainingload/*Test.kt`, `domain/SyncServiceTest.kt`, `domain/SyncPolicyTest.kt` (new), `strava/StravaClientTest.kt`, `persistence/ExposedRunRepositoryTest.kt`, `api/TrainingLoadRoutesTest.kt`, `AppConfigTest.kt`, `testsupport/Fakes.kt`, `testsupport/TestData.kt`
- Fixtures: `src/test/resources/strava/streams-heartrate.json`, `streams-heartrate-missing.json` (invented data)
- `.env.example` (repo root): `LTHR_BPM=`

Frontend (`frontend/src/app/`):
- `core/api/training-load.models.ts`, `training-load.service.ts` (+ `.spec.ts`) (new)
- `features/training-load/training-load-section.ts`, `training-load.view.ts`, `training-load.options.ts`, `ramp-warning/ramp-warning.ts`, `training-load-chart/training-load-chart.ts`, `training-load.scss` (+ specs) (new)
- `features/running-dashboard/running-dashboard-page.ts`, `.html`
- `features/running-dashboard/running-dashboard-page.spec.ts`, `.redesign.spec.ts`, `.plan.spec.ts`: stub `TrainingLoadSection` (AC15.2)
- `shared/chart/palette.ts` (+ spec), `shared/format/numbers.ts`, `dates.ts` (+ specs)
- `testing/training-load-fixtures.ts` (new)

## Edge cases
| Case | Expected behaviour |
|---|---|
| No activities in range | All days 0.0 and `runsCounted = 0` → empty state AC13.5. Ramp warnings are false (`changePercent = null`). |
| Manual activity (no GPS) | A manual Run has no stream and usually no `average_heartrate` → skipped and counted in `runsSkippedNoHeartRate`. If it has `average_heartrate` but the stream returns 404 or has no heartrate key → `UNAVAILABLE`, also skipped. |
| Activity without heart rate | Skipped (decision 1), counted as skipped, no stream call (AC3.2, AC8.2). |
| Treadmill run with HR strap | Counts like any other run (`trainer` doesn't matter here). |
| Private run | Included: the existing scope is `activity:read_all`. |
| Rides, walks, other sports | Not stored, so no load (AC8.5). |
| Week spans DST change / time zones | Days come from the date of `start_date_local`, and the maths uses `LocalDate` only. `today` uses the server zone (AC6.6). |
| Run just after local midnight | Counts on its local date (AC6.6). |
| Two runs on one day | Loads add up (AC6.2). |
| Paused run (auto-pause gaps) | Gaps over 30 s add no time (AC4.3). |
| HR dropouts (0 bpm) | Those intervals are skipped (AC4.4). |
| Today is Monday | `thisWeek` = today only. `lastWeek` = the full week just ended (AC7.7). |
| Previous week had 0 load | No percentage, no warning (AC7.5). |
| Previous week light (illness, travel) | No warning (AC7.4). |
| History still importing | Numbers come from what is stored, and the footnote says how many runs are pending (AC11.8). Fitness at the start of the window may be low until the warm-up runs are in. |
| LTHR changed in `.env` | After a restart, the next request recomputes all loads from the stored histograms. No Strava calls (AC5.3). |
| `LTHR_BPM` missing / invalid | 404 / 422 message in this card only. The rest of the page works (AC10.3). |
| Strava 429 during the HR backfill | Sync stops, stored data is served, and the remaining runs are retried after the limit (AC3.8). |
| Expired / revoked token | Refresh once. If revoked → 401 `strava_reauth_required` → `/connect` (AC3.9, AC9.6). |
| Run deleted on Strava | The existing detail 404 deletes the run, and the histogram is deleted by the cascade. |
| HR added to a run later on Strava | `average_heartrate` becomes non-null on the next list or recheck pass, and the stream is fetched then. |

## Privacy
- **Data fields & sensitivity:**
  - Heart-rate stream (`time`, `heartrate`): **health data, GDPR Art. 9**. Fetched transiently and reduced in memory to a per-run histogram (seconds per bpm). The histogram has no timestamps, so the HR series can't be rebuilt from it, but it is still health data.
  - `LTHR_BPM`: a body-related config value, only in `.env`. It is returned in the API response (`zones.lthrBpm`), only to the owner's session, so the user can see what the numbers are based on.
  - Derived loads, fitness, fatigue, form: derived health data, computed per request and not stored.
  - This feature doesn't use or send GPS, `latlng`, polylines, names or descriptions.
- **Storage & retention:** histograms are stored in the local SQLite file at `DB_PATH`, in table `run_hr_histograms`. They are pruned with their run (cascade). Retention grows from 6 months to 6 months + 41 days for all run data (R2). They are deleted by `deleteAll` when a different athlete connects. Raw streams are never stored. No browser storage is used.
- **Scopes:** no change (`activity:read_all`, already granted). No new scope.
- **Applicable privacy-check items:**

| # | Status | How it's satisfied |
|---|---|---|
| 1 | ✅ | `LTHR_BPM` only from env; `.env.example` has no value; fixtures are invented. |
| 2 | ✅ | No change to token handling; tokens stay in the backend. |
| 3 | ✅ | The existing refresh/revoke flow is also used for stream calls (AC3.9). |
| 5 | ✅ | No new scope. |
| 6 | ✅ | Only the histogram is stored, not the stream. It is the smallest form that lets an LTHR change take effect without refetching (S7). |
| 7 | ✅ | `keys=time,heartrate` only (AC3.11); `latlng` is never requested or declared. |
| 8 | ✅ | Logs hold activity counts only: no bpm values, histograms, LTHR or loads. `AppConfig.toString` masks LTHR (AC1.4). |
| 9 | ✅ | No change to client logging. |
| 10 | ✅ | Single error shape; the 422 message is fixed text without the value (AC9.4). |
| 11 | ✅ | The new route checks the session (AC9.2). |
| 13 | ✅ | Tooltip and texts use numbers and backend labels only; no `[innerHTML]` and no formatter that builds HTML from Strava text. |
| 14 | n/a | GET only (it triggers the existing read-cache sync, as in 001). |
| 15 | ✅ | The response has only chart fields: no ids, names or raw streams. |
| 16 | ✅ | No new dependency, no external calls. |
| 18 | ❌ deferred | Still no disconnect/delete action (as in 001). The workaround is unchanged. |
| 20 | ⚠ | The DB now also holds HR histograms, so treat backups as health data. |
| 21 | ✅ | Stream fixtures use invented values. |

## Test plan
| AC | Test type | Test name |
|---|---|---|
| AC1.1–AC1.4 | backend unit | `R1 - LTHR_BPM unset gives NotConfigured`, `R1 - LTHR_BPM valid range`, `R1 - LTHR_BPM invalid values do not stop startup`, `R1 - toString masks LTHR` |
| AC1.5 | review | `.env.example` diff |
| AC2.1, AC2.2, AC2.4 | backend unit (SyncPolicy) | `R2 - backfill starts 6 months and 41 days back`, `R2 - prune keeps the warm-up history`, `R2 - incremental list after history import` |
| AC2.3 | backend unit (SyncService) | `R2 - pre-005 database re-lists from history start once` |
| AC2.5 | backend persistence | `R2 - migration adds history_start and histogram table keeping rows` |
| AC3.1–AC3.4 | backend unit (SyncService, fake source) | `R3 - streams heart rate once for runs with HR`, `R3 - no stream call without HR`, `R3 - HR streams share the call cap newest first` |
| AC3.5 | backend unit | `R3 - runs waiting for HR count as pending` |
| AC3.6, AC3.7 | backend client (MockEngine) + unit | `R3 - stores histogram not stream`, `R3 - 404 or missing heartrate stores UNAVAILABLE` |
| AC3.8–AC3.10 | backend unit + client | `R3 - 429 on HR stream stops sync`, `R3 - 401 on HR stream refreshes once`, `R3 - network failure leaves run pending` |
| AC3.11 | backend client | `R3 - HR stream request asks only time and heartrate` |
| AC4.1–AC4.5 | backend unit | `R4 - seconds per bpm from stream`, `R4 - gaps over 30 s skipped`, `R4 - zero bpm skipped`, `R4 - all skipped gives empty histogram` |
| AC5.1–AC5.4 | backend unit | `R5 - zone boundaries from LTHR`, `R5 - zone weighted load`, `R5 - LTHR change changes load`, `R5 - empty histogram load zero` |
| AC6.1–AC6.8 | backend unit | `R6 - one day per date in 6 month window`, `R6 - daily load sums runs`, `R6 - single run decay example`, `R6 - hard week raises fatigue faster than fitness`, `R6 - buckets by local start date`, `R6 - skipped runs add no load`, `R6 - no runs gives zeros` |
| AC7.1–AC7.7 | backend unit | `R7 - this week so far vs last week`, `R7 - change percent rounding`, `R7 - warning only above 10 percent`, `R7 - no warning after light week`, `R7 - zero previous week has no percent`, `R7 - change labels`, `R7 - Monday week to date` |
| AC8.1–AC8.5 | backend unit | `R8 - window run counts` |
| AC9.1–AC9.7 | backend route (`testApplication`) | `R9 - 200 exact JSON shape`, `R9 - 401 without session`, `R9 - 404 not configured without sync`, `R9 - 422 invalid config without value`, `R9 - reuses concurrent sync`, `R9 - reauth required maps to 401`, `R9 - rounding of form from unrounded values` |
| AC10.1–AC10.4 | frontend component (page) | `R10 - training load card directly after marathon build`, `R10 - one training load request`, `R10 - fails independently`, `R10 - 401 shows no error text` |
| AC11.1, AC11.7, AC11.8 | frontend component + view unit | `R11 - shows latest signed values`, `R11 - footnote counts and zones`, `R11 - pending note` |
| AC11.2–AC11.6 | frontend unit (options builder) | `R11 - three named series in palette colours`, `R11 - zero markLine on form`, `R11 - legend`, `R11 - tooltip lines`, `R11 - aria label` |
| AC12.1–AC12.3 | frontend component | `R12 - this week warning callout`, `R12 - last week warning callout`, `R12 - no callout without warning` |
| AC12.4, AC14.1–AC14.4 | frontend component + manual check at 375 px and 1280 px | `R14 - card uses theme tokens`, `R14 - palette has load colours` |
| AC12.5 | frontend component | `R12 - no advice words in any state` |
| AC13.1–AC13.7 | frontend component | `R13 - loading`, `R13 - not configured hint without retry`, `R13 - invalid config message`, `R13 - error with retry`, `R13 - empty no heart rate runs`, `R13 - empty still importing`, `R13 - normal order` |
| service | frontend service (`HttpTestingController`) | `R9 - getTrainingLoad calls /api/training-load` |
| formatters | frontend unit | `R11 - formatSignedOneDecimal`, `R11 - formatShortDate ignores browser zone` |
| AC15.1–AC15.4 | review + existing suites | existing 001–004 specs pass with the stub, `R15 - 001 aggregates unchanged with older runs stored` |

## Out of scope
- Advice or coaching text (intent non-goal).
- Garmin, HRV or any data from outside Strava.
- Rides, walks and other sports in the load.
- Aerobic fitness metrics: efficiency factor, decoupling, long-run progression (feature 007).
- Plan-vs-actual, intensity split vs plan, marathon-pace volume, race predictor (feature 006).
- Strava's heart-rate zones endpoint and any new OAuth scope.
- Load estimated from pace for runs without heart rate (decision 1).
- Projection to race day (decision 2).
- Using `LT_PACE` in this feature (see Risk 1).

## Suggested follow-ups
- A neutral weekly-load line ("This week so far 412 · last week 380") even when there is no warning.
- Shading the weeks that triggered a warning on the chart (`markArea`).
- Form projected from the plan's taper to race day (planned with 006).
- A disconnect / delete-my-data action (privacy item 18, deferred since 001).
- Reading `LT_PACE` in 006, where the intensity split by pace may use it.

## Risks & decisions to confirm
1. **`LT_PACE` has no effect on the load, so S7 is only partly met.** The intent makes the load score HR-based (TRIMP), and decision 1 skips runs without HR instead of estimating their load from pace. That leaves threshold pace with nothing to drive here, so this spec does not read `LT_PACE`. Changing `LTHR_BPM` does change the numbers (AC5.3). Please confirm. The alternatives are to read and validate `LT_PACE` now and only show it in the footnote, or to define a pace-based use, which would need an intent change.
2. **Simple averages, not exponential ones (TrainingPeaks-style CTL/ATL).** "42-day average" and "7-day average" are taken literally as simple rolling means. They are exact with 41 warm-up days and easy to check by hand. The side effect: a hard session drops out abruptly 7 and 42 days later, which causes small steps in the lines. Exponentially weighted averages give smoother lines and match TrainingPeaks and Garmin more closely, but they need a longer warm-up history (about 3 × 42 days) to be accurate at the window start. Please confirm simple averages.
3. **Load score = minutes easy × 1 + subT × 2 + hard × 3 (Lucia TRIMP)**, with the zones from the intent. The absolute numbers won't match Garmin's or TrainingPeaks' load values; only the trends are comparable.
4. **Two ramp checks: "this week so far" vs last week, and last week vs the week before.** With only the current-week check, a big week stops being flagged as soon as it ends: on Monday the new week has almost no load yet. With only the last-completed-week check, a big week isn't flagged until it is over. Please confirm both should show.
5. **The warning uses the rounded percentage** (`changePercent > 10` after rounding, so 10.4 % gives no warning and 10.5 % does). This keeps the number shown and the flag consistent.
6. **Retention grows to 6 months + 41 days for all runs.** After deploy, the first sync re-lists that period once. It then makes about 150–200 extra stream calls plus detail calls for the older runs, spread over 3–4 syncs (about an hour, given the cap and the 15-minute rate-limit window). During that time the 001 sync banner shows "Still importing runs", because runs waiting for an HR stream count as pending (AC3.5).
7. **Heart-rate streams are now requested.** This reverses the 001 privacy note "HR streams never requested". Only one HR histogram per run is stored. It is health data, so DB backups need the same care (privacy item 20).
8. **A change to `LTHR_BPM` needs a backend restart**, because config is read at startup (as for all env values). "The next time the data is calculated" means the first request after the restart.
9. **Gap rule: intervals over 30 s are dropped as pauses.** This is a judgement call for auto-pause recordings. A long stop with the watch running and no auto-pause still counts, at the HR recorded at its start.
