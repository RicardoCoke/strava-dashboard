# Spec: VO2max estimator and race time predictions

**Feature ID:** 008 · **Status:** Approved · **Intent:** [intent.md](./intent.md) · **Date:** 2026-10-31

## Summary
The detail call the sync already makes (`GET /activities/{id}`) now also stores each lap's average heart rate and the run's moving time. Runs fetched before this feature get one more detail call to fill these in. Per request, the backend picks a pace + heart-rate "segment" for each qualifying run:
- Easy and long outdoor runs use the whole run.
- Treadmill runs with a description pace line but no `NxMmin` pattern also use the whole run, with the pace from that line.
- Interval workouts (outdoor and treadmill) use only the rep laps of at least 4 minutes. Treadmill reps take their pace from the description pace line.
- Runs named like distance-rep workouts (e.g. "6x1km") are excluded.

Each segment gets a Runalyze-style VO2max: Daniels' oxygen cost of the pace divided by the %VO2max implied by %HRmax. The backend then returns, from `GET /api/vo2max`:
- a duration-weighted current value (last 7 days, else last 4 weeks);
- one value per Monday–Sunday week for the last 6 months (null for weeks without data);
- Daniels VDOT race predictions for 5K, 10K, half marathon and marathon.

A new "VO2max" card directly under the "Training load" card shows the current value, four prediction tiles (the marathon tile notes that the prediction tends to be optimistic) and a weekly line chart with gaps for empty weeks.

## Requirements
| ID | Requirement | Priority (Must/Should/Could) |
|---|---|---|
| R1 | Config: optional `MAX_HR_BPM` (whole number, 120–230). Unset means the default of 194 bpm. Invalid values don't stop the app but give a 422 for this card. The value is never logged | Must |
| R2 | Sync: the detail call also stores `moving_time` per run and `average_heartrate` per lap. Stored runs with heart rate whose detail came from before this feature get one detail call again, under the existing cap | Must |
| R3 | Qualifying runs and method: each run is either excluded or gives one segment (whole run, or rep laps), following the rules below. This includes treadmill whole runs with a pace line and the exclusion of distance-rep workouts | Must |
| R4 | Rep segment from laps: rep laps are found by the workout's `M` minutes (at least 4 min each). Pace comes from the laps (outdoor) or from the description pace line (treadmill), and heart rate is the duration-weighted lap heart rate | Must |
| R5 | Per-segment VO2max = Daniels' oxygen cost of the pace ÷ %VO2max from %HRmax (Swain: `(%HRmax − 0.37) / 0.64`) | Must |
| R6 | Current estimate: duration-weighted mean of the segments in the last 7 days. If there are none, the last 28 days. If there are none there either, no current value. The basis period is reported | Must |
| R7 | Weekly series: one entry per Monday–Sunday week from the week containing `today − 6 months` up to the current week. Each is the duration-weighted mean of that week's segments, or null when the week has none | Must |
| R8 | Race predictions: 5K, 10K, half marathon and marathon times from Daniels' VDOT equation for the current VO2max (rounded to 1 decimal). The marathon carries the "optimistic" note | Must |
| R9 | `GET /api/vo2max` returns the exact JSON below. It needs a connected session. Invalid `MAX_HR_BPM` → 422 | Must |
| R10 | Placement: a "VO2max" card directly after the "Training load" card, with its own single request, states and failure handling, in the existing card look | Must |
| R11 | Chart: line of weekly VO2max, with a gap and no point for null weeks, plus a tooltip that names empty weeks | Must |
| R12 | Current value + basis caption, four prediction tiles (h:mm:ss / m:ss), marathon note, and a footnote with the max HR used and its source | Must |
| R13 | Loading, invalid config, error, empty (with a still-importing variant), "no current estimate" and normal states | Must |
| R14 | No regression: 001–007 behaviour and JSON unchanged, no new OAuth scope, no new Strava endpoint | Must |

## Acceptance criteria

**Conventions.** `today = LocalDate.now(clock.withZone(zone))` (server zone, as in 001 and 005). A run's date is the local date of `start_date_local` (`RunActivity.localDate`). Weeks run Monday to Sunday. "Run" means a stored activity with `sport_type == "Run"` (this includes treadmill/`trainer` and private runs). Examples use `today = 2026-10-31` (Saturday) and max HR 194 unless stated otherwise. All fixture numbers are invented.

### R1 — Configuration
`MaxHrSetting`: `Default` (194), `Configured(Bpm)`, `Invalid`. `DEFAULT_MAX_HR_BPM = 194`, `MIN_MAX_HR_BPM = 120`, `MAX_MAX_HR_BPM = 230`.
- **AC1.1** Given `MAX_HR_BPM` is unset or blank, when the app starts, then `AppConfig.maxHr` is `MaxHrSetting.Default`, and its bpm is 194.
- **AC1.2** Given `MAX_HR_BPM= 190 ` (surrounding whitespace allowed), then `AppConfig.maxHr` is `Configured(Bpm(190))`. Given `120` and `230`, it is `Configured`.
- **AC1.3** Given `abc`, `190.5`, `119` or `231`, then the app still starts and `AppConfig.maxHr` is `Invalid`.
- **AC1.4** Given any config, then `AppConfig.toString()` contains `maxHr=<default>`, `maxHr=<set>` or `maxHr=<invalid>`, and never the number. `Configured.toString()` is `Configured(<redacted>)`.
- **AC1.5** Given `.env.example`, then it lists `MAX_HR_BPM=` with no value and the comment: "Maximum heart rate in bpm (whole number, 120-230). Used by the VO2max estimate. Leave empty for the estimate of 194. Restart the backend after changing it."

### R2 — Lap heart rate and moving time from the detail call
- **AC2.1** Given a `GET /activities/{id}?include_all_efforts=false` response with `moving_time: 3000` and laps with `average_heartrate`, when the detail is saved, then `runs.moving_time_s = 3000`, and each `run_laps.average_heartrate` holds its lap value (null when the lap has none).
- **AC2.2** Given a detail response without `moving_time`, then `moving_time_s = 0` is stored (never null after a detail call made by this version), so the run isn't fetched again.
- **AC2.3** The list pass (`upsertSummaries`) never writes `moving_time_s`. Only `saveDetail` sets it.
- **AC2.4 (one-time refetch)** `needsLapHeartRate(run) = run.averageHeartRateBpm != null && run.detailFetchedAt != null && run.movingTime == null`. Given such a run, when a sync runs, then the `MISSING_DETAILS` phase plans a detail call for it. Runs with `detailFetchedAt == null` come first, and each group is newest first. It shares `MAX_ACTIVITY_CALLS_PER_SYNC = 80` with the other phases.
- **AC2.5** Given a run with `averageHeartRateBpm == null` and an old detail, then no extra detail call is made for it.
- **AC2.6** Given runs that need the refetch, then `SyncPolicy.pendingCount` includes them, so the sync stays due and the 001 sync banner shows them as still importing until they are done.
- **AC2.7 (migration)** Given a DB file without `runs.moving_time_s` (INTEGER) or `run_laps.average_heartrate` (REAL), when the app starts, then both columns are added as nullable with `addColumnIfMissing`, and existing rows are kept.
- **AC2.8 (errors)** 429, 401 and network failures on these detail calls behave exactly like the existing detail calls (001 AC5.x, 005 AC3.8–AC3.10). 429 stops the sync and saves `rateLimitedUntil`. 401 refreshes once, then gives `ReauthRequired`. 404 deletes the run.

### R3 — Qualifying runs and method
`MIN_RUN_MOVING_SECONDS = 1200` (20 min). `type = RunClassifier.classify(run)` (001, unchanged). Pace limits are `TreadmillPaceParser`'s 120–720 s/km.

**Distance-rep pattern** (`DistanceRepPattern.matches(name)`): case-insensitive regex `(?<![\d.])(\d{1,2})\s*[x×]\s*\d+(?:[.,]\d+)?\s*(?:km|k|m)(?![a-z])`, checked on the run **name only**. It matches "6x1km", "10x400m", "5 x 1000m", "3x2k" and "8×800m". It does not match "6x10min", "4x8min", "5x6'" or "10k tempo". It is checked first: a name that matches is excluded even if the name or description also has an `NxMmin` pattern.

Rules are applied top to bottom; the first matching row decides.

| # | Run | Method | Qualifies when |
|---|---|---|---|
| 1 | Name matches the distance-rep pattern (outdoor or treadmill) | — | Never (approved decision, until a rule to find their rep laps exists) |
| 2 | `movingTime == null` (detail not refetched yet) | — | Not yet; counted as pending (R9 `runsPendingDetail`) |
| 3 | Any `Workout(N, M)` with `M < 4` | — | Never (decision 5) |
| 4 | Outdoor (`trainer = false`) `Workout(N, M)` with `M >= 4` | Rep laps (R4) | `movingTime >= 1200 s` and R4 finds at least one rep lap |
| 5 | Treadmill `Workout(N, M)` with `M >= 4` and a description pace line | Rep laps + description pace (R4) | `movingTime >= 1200 s` and R4 finds at least one rep lap |
| 6 | Treadmill `Workout(N, M)` without a pace line | — | Never (decision 1) |
| 7 | Treadmill `Easy`/`LongRun` with a description pace line | Whole run, pace = unrounded mean of the pace line values | `averageHeartRateBpm != null`, `movingTime >= 1200 s` (approved decision) |
| 8 | Treadmill `Easy`/`LongRun` without a pace line | — | Never (decision 1) |
| 9 | Outdoor `Easy` or `LongRun` | Whole run, pace = `movingTime / distanceKm` | `averageHeartRateBpm != null`, `movingTime >= 1200 s`, `distance > 0`, pace within 120–720 s/km |

- **AC3.1** Given an outdoor easy run of 10 000 m, 3000 s moving and average HR 150, then it gives a whole-run segment with pace 300 s/km, HR 150 and duration 3000 s.
- **AC3.2** Given an outdoor easy run with `averageHeartRateBpm == null` (including a manual run), then it is excluded.
- **AC3.3** Given an outdoor run with 1199 s moving time, then it is excluded. Given 1200 s, it qualifies (if the other rules pass).
- **AC3.4** Given a treadmill run with heart rate but no pace line (e.g. an easy treadmill run with nothing in the description), then it is excluded, whatever its laps or Strava distance say.
- **AC3.5** Given an outdoor `10x1min` workout, then it is excluded entirely (it doesn't fall back to the whole run).
- **AC3.6** Given an outdoor `5x6min` workout where no lap matches the rep duration (e.g. only 1 km auto-laps), then it is excluded (no fallback to the whole run or to the speed stream).
- **AC3.7** Given an outdoor easy run with pace slower than 720 s/km (walk breaks) or `distance = 0`, then it is excluded.
- **AC3.8** Given a private run, then it is treated like any other run.
- **AC3.9 (distance reps excluded)** Given outdoor runs named "6x1km", "10x400m", "5 x 1000m" or "Track 8×800m", each with heart rate and 3000 s moving time, then each is excluded and adds nothing to the current value, any week or `runCount`. Given a treadmill run named "6x1km" with a pace line, it is excluded too. Given a run named "6x10min" or "10k easy", the distance-rep rule doesn't apply.
- **AC3.10 (distance reps beat NxMmin)** Given a run named "2x10min + 4x1km", then it is excluded (the distance-rep check comes first).
- **AC3.11 (treadmill whole run with pace line)** Given a treadmill run named "Easy treadmill" (no `NxMmin`, no distance-rep pattern), description "5:30", average HR 140 and moving time 2700 s, then it gives a whole-run segment with pace 330.0 s/km, HR 140 and duration 2700 s. Its Strava distance and laps are not used.
- **AC3.12** Given the same treadmill run with description "5:30 5:20", then the pace is 325.0 s/km (unrounded mean). Given moving time 1199 s, or `averageHeartRateBpm == null`, then it is excluded.

### R4 — Rep segment from laps
`REP_DURATION_TOLERANCE = 0.15`, `MIN_REP_SECONDS = 240`.
- **AC4.1 (candidate laps)** A lap is a rep candidate when all of these hold: `|elapsed − M × 60| <= 0.15 × M × 60`, `elapsed >= 240 s`, and `averageHeartRateBpm != null`. Outdoor laps also need `distance > 0`. For `M = 6`, that is 306–414 s.
- **AC4.2 (too many candidates)** Given more than `N` candidates, then outdoor runs keep the `N` fastest (highest `distance / elapsed`), and treadmill runs keep the `N` with the highest lap heart rate. Ties go to the lower lap index. Given `N` or fewer, all are used.
- **AC4.3 (segment values)** Duration = Σ elapsed of the kept laps. HR = Σ(lap HR × elapsed) / Σ elapsed. Outdoor pace = Σ elapsed / (Σ distance / 1000). Treadmill pace = the unrounded mean of the pace line values (`TreadmillPaceParser.parsePaceLine`). Lap distance is never used for treadmill runs.
- **AC4.4 (treadmill example, S5)** Given a treadmill run with:
  - name "5x6min", moving time 3600 s and whole-run average HR 150;
  - description "4:15 4:10 4:05 4:10 4:00";
  - laps: a 600 s warm-up (HR 135), five 360 s rep laps (HR 168, 170, 172, 172, 173) with 120 s recovery laps (HR 150) between them, and a 600 s cooldown (HR 140).

  Then the segment has pace 248.0 s/km, HR 171.0 and duration 1800 s, and its VO2max is 57.0 (R5). The whole-run HR of 150 is not used.
- **AC4.5 (outdoor example)** Given an outdoor "4x8min" run with a 900 s / 2500 m warm-up lap (HR 140), four 480 s / 1950 m rep laps (HR 170) with 120 s jog laps, and moving time 3300 s, then the segment has pace 246.2 s/km (243.75 m/min), HR 170.0 and duration 1920 s, and its VO2max is 58.2.
- **AC4.6** Given a 4x4min workout whose 4-min recovery laps are slower, then AC4.2 keeps the 4 rep laps, not the recoveries.
- **AC4.7** Given a treadmill workout whose rep laps have no `average_heartrate` (no HR strap), then there are no candidates and the run is excluded.

### R5 — Per-segment VO2max
With `v` = speed in m/min (`60 000 / paceSecondsPerKm`):
- `oxygenCost(v) = −4.60 + 0.182258 × v + 0.000104 × v²` (ml/kg/min, Daniels & Gilbert).
- `hrFraction = min(hr / maxHr, 1.0)`. `vo2Fraction = (hrFraction − 0.37) / 0.64`.
- `vo2max = oxygenCost(v) / vo2Fraction`. `MIN_HR_FRACTION = 0.65`: segments with `hrFraction < 0.65` are excluded (the formula is unstable at very low heart rates).
- **AC5.1** Given pace 300 s/km (v = 200 m/min), HR 150 and max HR 194, then `oxygenCost = 36.0116`, `vo2Fraction = 0.62999` and `vo2max = 57.16` (57.2 after API rounding).
- **AC5.2 (max HR change)** Given the same segment with max HR 185, then `vo2max` is computed with 185 (HR fraction 0.8108, VO2max 52.3). No Strava call is needed: values are computed per request from stored data.
- **AC5.3** Given HR 120 with max HR 194 (fraction 0.619), then the segment is excluded.
- **AC5.4** Given HR 200 with max HR 194, then `hrFraction` is capped at 1.0.
- **AC5.5** Values stay unrounded in the domain. Only the API rounds them (R9).

### R6 — Current estimate
`last7 = today − 6 … today`, `last28 = today − 27 … today`. `weightedMean(segments) = Σ(vo2max × durationSeconds) / Σ durationSeconds`.
- **AC6.1** Given segments in `last7`, then:
  - `current.vo2max = weightedMean(segments in last7)`;
  - `basis = "last7Days"`, `periodStart = 2026-10-25`, `periodEnd = 2026-10-31`;
  - `runCount` is the number of those segments.
- **AC6.2 (weighting, decision 6)** Given two segments in `last7`, 50.0 for 3600 s and 56.0 for 1800 s, then `current.vo2max = 52.0`.
- **AC6.3 (fallback, S1)** Given no segment in `last7` but some in `last28`, then `basis = "last28Days"`, `periodStart = 2026-10-04`, and the value is the weighted mean over `last28`.
- **AC6.4** Given no segment in `last28`, then `current` is `null` and `predictions` is `[]`.
- **AC6.5 (time zone)** Given a run with `start_date = 2026-10-24T23:30:00Z` and `start_date_local = 2026-10-25T00:30:00Z`, then it is in `last7` (date 2026-10-25).
- **AC6.6** The duration used for weighting is the segment duration: moving time for whole runs (outdoor and treadmill), and Σ rep-lap elapsed for rep segments.

### R7 — Weekly series
`windowStart` = the Monday on or before `today.minusMonths(6)`. `windowEnd` = the Sunday of the current week.
- **AC7.1** Given today = 2026-10-31, then:
  - `windowStart = 2026-04-27` (the Monday before Thu 2026-04-30) and `windowEnd = 2026-11-01`;
  - `weeks` has 27 entries in ascending order;
  - the last entry is 2026-10-26 … 2026-11-01 with `inProgress = true`, and all others have `inProgress = false`.
- **AC7.2** Each week's `vo2max` = `weightedMean` of the segments dated in that week (up to today), with `runCount` = their number.
- **AC7.3 (S6)** Given a week with no segment, then `vo2max = null` and `runCount = 0`. It is never `0.0` and never interpolated.
- **AC7.4** Given today is a Monday, then the current week holds only today's segments and is `inProgress`.
- **AC7.5** The runs history kept by 005 (`today − 6 months − 41 days`) already covers `windowStart`, so no sync change is needed for history.

### R8 — Race predictions
Daniels' VDOT, for a race of `d` metres in `t` minutes:
- `v = d / t`, and `pctMax(t) = 0.8 + 0.1894393 × e^(−0.012778 × t) + 0.2989558 × e^(−0.1932605 × t)`.
- The predicted time solves `oxygenCost(d / t) / pctMax(t) = vdot`. It is found by bisection on `t` in 1–1000 min until the interval is under 0.01 s, then rounded half-up to whole seconds.
- Distances: 5000, 10 000, 21 097.5, 42 195 m.
- `vdot` = `current.vo2max` rounded to 1 decimal (the shown value).

- **AC8.1** Given vdot 50.0, then the times are within ±3 s of the solved equation: 5K 1196 s (19:56), 10K 2480 s (41:20), half 5491 s (1:31:31), marathon 11 440 s (3:10:40). They are also within max(3 s, 0.1 %) of Daniels' printed table: 1197 / 2481 / 5495 / 11 449 s.
- **AC8.2** Given vdot 40.0, then the times are within ±3 s of the solved equation: 5K 1446 s (24:06), 10K 3001 s (50:01), half 6654 s (1:50:54), marathon 13 777 s (3:49:37). They are also within max(3 s, 0.1 %) of Daniels' printed table: 1448 / 3003 / 6659 / 13 785 s.
- **AC8.3** Predictions are in the order 5K, 10K, half, marathon. Only the marathon has a non-null `note`: "Marathon predictions from VO2max tend to be optimistic." (S4).
- **AC8.4** No mileage-based adjustment is applied (intent non-goal).

### R9 — API
- **AC9.1** Given a connected session and a valid or default max HR, when `GET /api/vo2max` is called, then the response is `200` with exactly the fields in the Design section. It has no activity ids, names, descriptions, laps or per-run values.
- **AC9.2** Given no session, or a session for another athlete, then `401 {"error":"not_connected",…}`.
- **AC9.3** Given `MAX_HR_BPM` is invalid, then `422 {"error":"vo2max_invalid_config","message":"MAX_HR_BPM is not a whole number between 120 and 230."}` and no sync is started. The message never contains the configured value.
- **AC9.4** Given a valid config, then the service calls `SyncService.syncIfDue()` first and computes from the store afterwards. A sync started by another card's request at the same time is reused (shared mutex, as in 005 AC9.5).
- **AC9.5** Given the sync ends with `ReauthRequired`, then `401 strava_reauth_required`. Given it ends rate-limited or with Strava unavailable, then `200` from the stored data.
- **AC9.6** `runsPendingDetail` counts runs that meet all of these:
  - dated in `windowStart … today`;
  - `averageHeartRateBpm != null`;
  - `detailFetchedAt == null`, or `needsLapHeartRate`;
  - the name doesn't match the distance-rep pattern.
- **AC9.7** Rounding: every `vo2max` is rounded half-up to 1 decimal from the unrounded weighted mean. `timeSeconds` are whole numbers.

### R10 — Placement and independence
- **AC10.1** Given the dashboard page in any state, then `app-vo2max-section` is rendered directly after `app-training-load-section` and before the running dashboard's `@switch` output (S7).
- **AC10.2** Given the page loads, then exactly one `GET /api/vo2max` is made, alongside the existing `/api/dashboard/running`, `/api/training-plan` and `/api/training-load`. None waits for another.
- **AC10.3** Given `/api/vo2max` fails (422, 500, network), then the other cards still render their own states, and vice versa.
- **AC10.4** Given `/api/vo2max` returns 401, then the shared interceptor redirects to `/connect` and the card shows no error text.
- **AC10.5** The card uses the existing card look:
  - `section.card`, `--color-*` tokens only, and the same gap as the other cards;
  - chart colours from `shared/chart/palette.ts` (`palette.accent` for the line, no new colour values);
  - at 375 px the tiles wrap to two per row and the page has no horizontal scroll;
  - at 720 px and above, the four tiles sit in one row.

### R11 — Chart
- **AC11.1** `buildVo2maxOptions(data)` returns an ECharts line chart:
  - a category x axis of `weeks[].weekStart`, labelled like "27 Apr";
  - a value y axis named "VO2max (ml/kg/min)" with `scale: true` (no forced zero);
  - one series "VO2max" in `palette.accent`, with `showSymbol: true`, `connectNulls: false` and one data point per week (null for empty weeks).
- **AC11.2 (S2, S6)** Given weeks with `vo2max = null`, then the series data holds `null` there, so the line breaks and no point is drawn. No zero is plotted.
- **AC11.3** The tooltip (`trigger: 'axis'`) shows "Week of 27 Apr", then "VO2max 52.3 · 3 runs" (or "1 run"), or "No qualifying runs" for a null week. The in-progress week adds "(so far)" after the date. It is built only from dates and numbers.
- **AC11.4** The chart's `aria-label` is "VO2max per week, last 6 months".

### R12 — Current value, tiles and footnote
- **AC12.1 (S1)** Given `current` is set, then the card shows:
  - `h2` "VO2max";
  - the value "52.3" in the large-number style, with the label "VO2max" and the unit "ml/kg/min";
  - the caption "Based on 4 runs in the last 7 days (25 Oct – 31 Oct)", or "… in the last 4 weeks (4 Oct – 31 Oct)" for `last28Days`. "1 run" is singular.
- **AC12.2 (S3)** Below it, four tiles in order, labelled "5K", "10K", "Half marathon" and "Marathon". Times come from `formatRaceTime`: under 1 hour as `m:ss` ("19:57"), otherwise `h:mm:ss` ("1:31:35", "3:10:49").
- **AC12.3 (S4)** The marathon tile shows its `note` in muted small text inside the tile. The other tiles show no note.
- **AC12.4** A muted footnote shows "Max HR 194 bpm (estimate; set MAX_HR_BPM to change)" when `maxHr.source = "default"`, or "Max HR 190 bpm (from MAX_HR_BPM)" when `"configured"`.
- **AC12.5** Given `runsPendingDetail > 0`, then the footnote adds "12 runs still importing lap heart rate; numbers will update."

### R13 — States
- **AC13.1 (loading)** Given the request is pending, then the card shows `h2` "VO2max" and "Loading VO2max…" in `role="status"`, and no chart.
- **AC13.2 (invalid config)** Given `422 vo2max_invalid_config` with message `M`, then the card shows "VO2max settings have a problem: M" in `role="alert"`, and the muted hint "Set MAX_HR_BPM in .env (for example 194), or leave it empty, then restart the backend." No Retry.
- **AC13.3 (error)** Given a 500, a network error or a body that fails `isVo2maxResponse`, then "Couldn't load VO2max." in `role="alert"` with a Retry button. Retry sets loading and sends the request again.
- **AC13.4 (empty)** Given `current == null`, every week `vo2max == null` and `runsPendingDetail = 0`, then the card shows "No qualifying runs in the last 6 months" and the muted hint "Runs count with heart rate and at least 20 minutes. Treadmill runs need paces in the description. Distance-rep workouts (e.g. 6x1km) are not counted." No tiles, no chart.
- **AC13.5 (empty, still importing)** Given the same but `runsPendingDetail > 0`, then "Lap heart-rate data is still importing: 12 runs to go. Reload in a while." No tiles, no chart.
- **AC13.6 (no current estimate)** Given `current == null` but at least one week has a value, then the card shows "No qualifying runs in the last 4 weeks, so there is no current estimate or race prediction." in place of the value and tiles, then the chart and the footnote.
- **AC13.7 (normal)** Given `current != null`, then: heading → current value and caption → tiles → chart → footnote.

### R14 — No regression
- **AC14.1** Given the diff, then this feature leaves these unchanged: `RunningDashboardRoutes.kt`, `RunningDashboardDtos.kt`, `TrainingLoadRoutes.kt`, `TrainingLoadDtos.kt`, `RunClassifier.kt`, `running-dashboard.models.ts`, `training-load.models.ts` and `api-error.interceptor.ts`. `REQUIRED_SCOPE` stays `activity:read_all`.
- **AC14.2** Given the existing page specs, then they pass with only a setup change: `Vo2maxSection` is stubbed through `TestBed.overrideComponent(RunningDashboardPage, …)` (like `stubTrainingLoadSection`), so their request counts are unchanged.
- **AC14.3** Given 001 rep paces and classification (`RunClassifier`, `RepPaceCalculator`, `FastLapPaceFinder`), 005 training load and 007 week comparison, then their outputs for the same stored data are unchanged. This holds with the new `Lap.averageHeartRateBpm` and `RunActivity.movingTime` fields, and the distance-rep rule applies only inside `domain/vo2max/`.
- **AC14.4** Given the backend and frontend check commands, then everything passes.

## Traceability
| Success criterion (intent) | Covered by |
|---|---|
| S1 (current VO2max and its basis period: 7 days, else 4 weeks) | R6 (AC6.1, AC6.3, AC6.4), R9, R12 (AC12.1), R13 (AC13.6) |
| S2 (one point per Mon–Sun week, last 6 months, trend readable) | R7 (AC7.1, AC7.2), R11 |
| S3 (four tiles: 5K, 10K, half, marathon) | R8, R12 (AC12.2) |
| S4 (marathon "optimistic" note) | R8 (AC8.3), R12 (AC12.3) |
| S5 (treadmill rep paces from the description + HR during reps, not whole-run average) | R3, R4 (AC4.3, AC4.4), R2 (lap HR) |
| S6 (weeks without data clearly distinguished, no zero or invented point) | R7 (AC7.3), R11 (AC11.2, AC11.3) |
| S7 (card under "Training load") | R10 (AC10.1) |
| Constraint: Daniels oxygen cost ÷ %VO2max from max HR, VDOT race times | R5, R8 |
| Constraint: max HR default ~194, configurable | R1, R5 (AC5.2), R12 (AC12.4) |
| Constraint: 20-minute minimum, treadmill and private runs count | R3 (AC3.3, AC3.8, AC3.12) |
| Decision 1 (treadmill without paces excluded) | R3 (AC3.4, rows 6 and 8) |
| Decision 2 (outdoor intervals use reps only, easy/long use whole run) | R3, R4 (AC4.5) |
| Decision 3 (reps from Strava laps with lap HR; no HR stream stored) | R2, R4 |
| Decision 4 (max HR in `.env`) | R1 |
| Decision 5 (reps of at least 4 minutes) | R3 (AC3.5), R4 (AC4.1) |
| Decision 6 (duration weighting) | R6 (AC6.2, AC6.6), R7 (AC7.2) |
| Spec approval: distance-rep workouts excluded | R3 (AC3.9, AC3.10), R9 (AC9.6) |
| Spec approval: treadmill run with pace line but no `NxMmin` counts as a whole run | R3 (AC3.11, AC3.12) |
| Non-goals (no mileage adjustment, no per-run drill-down, no writes) | R8 (AC8.4), R9 (AC9.1) |

## Design

### Data from Strava
- **Endpoints:** no new endpoint.
  - `GET /athlete/activities?after=<epoch>&page=<n>&per_page=200`: unchanged.
  - `GET /activities/{id}?include_all_efforts=false`: the request is unchanged, and two more fields are read from the response. It is also called once more for stored runs with heart rate whose detail predates this feature (AC2.4).
  - Stream endpoints: unchanged. No stream is requested for this feature.
- **Scopes:** no change (`activity:read_all`, already granted; needed for private runs).
- **Fields used:** existing summary fields (`start_date_local`, `distance`, `average_heartrate`, `trainer`, `name`) and detail fields (`description`, `laps[].lap_index`, `laps[].elapsed_time`, `laps[].distance`). **New:** `moving_time` (int s) on the detailed activity, and `laps[].average_heartrate` (double bpm, optional) on each lap. `DetailedActivityDto` gains `@SerialName("moving_time") val movingTime: Int = 0`. `LapDto` gains `@SerialName("average_heartrate") val averageHeartrate: Double? = null`.
- **Sync & caching:** the sync is still triggered by API requests when due. The one-time refetch rides in `MISSING_DETAILS` and shares the 80-call cap.
  - Rollout cost: about 150–200 detail calls (stored runs with HR in 6 months + 41 days), spread over 2–3 syncs. This is within Strava's default limits of 100 requests / 15 min and 1,000 / day.
  - After that, a new run costs nothing extra: the fields come with the detail call it already gets.
  - All VO2max values are computed per request from the store (about 200 runs, cheap).
- **Pagination:** unchanged (`per_page=200`, stop on a short page).
- **Errors:**
  - 401 → one refresh and retry, then tokens deleted and `401 strava_reauth_required`.
  - 429 → the sync stops, `rateLimitedUntil` is saved, and stored data is served with the pending count.
  - Network failure / 5xx → `STRAVA_UNAVAILABLE`, stored data served.
  - 404 on a detail → the run is deleted (existing rule).

### Domain model & logic
Model changes (`domain/model/`):
```kotlin
data class Lap(val index: Int, val elapsed: Seconds, val distance: Meters, val averageHeartRateBpm: Double? = null)
// RunActivity gains: val movingTime: Seconds? = null   (null = detail from before 008, or not fetched)
// ActivityDetail (ports) gains: val movingTime: Seconds
```
`SyncPolicy`: adds `needsLapHeartRate(run)` (AC2.4). `MISSING_DETAILS` plans the `detailFetchedAt == null` runs first, then the `needsLapHeartRate` runs, newest first in each group. `pendingCount` adds `needsLapHeartRate`.

Config (`config/MaxHrSetting.kt`): `sealed interface MaxHrSetting { data object Default; data object Invalid; data class Configured(val bpm: Bpm) }` with `parse(text: String?)`, `DEFAULT_MAX_HR_BPM = 194`, `MIN_MAX_HR_BPM = 120` and `MAX_MAX_HR_BPM = 230`.

Package `domain/vo2max/` (pure, no Ktor/Exposed/Strava types, each unit-tested):
- `Vo2maxModels.kt`:
  ```kotlin
  enum class SegmentMethod { WHOLE_RUN, REP_LAPS }
  data class Segment(val date: LocalDate, val pace: Double /* s per km, unrounded */, val heartRateBpm: Double, val duration: Seconds, val method: SegmentMethod)
  enum class CurrentBasis { LAST_7_DAYS, LAST_28_DAYS }
  data class CurrentVo2max(val vo2max: Double, val basis: CurrentBasis, val periodStart: LocalDate, val periodEnd: LocalDate, val runCount: Int)
  data class WeekVo2max(val weekStart: LocalDate, val weekEnd: LocalDate, val vo2max: Double?, val runCount: Int, val inProgress: Boolean)
  enum class RaceDistance(val key: String, val label: String, val meters: Double) { FIVE_K("5k","5K",5000.0), TEN_K("10k","10K",10000.0), HALF("half","Half marathon",21097.5), MARATHON("marathon","Marathon",42195.0) }
  data class RacePrediction(val distance: RaceDistance, val time: Seconds)
  data class Vo2maxReport(val maxHr: Bpm, val maxHrConfigured: Boolean, val current: CurrentVo2max?, val predictions: List<RacePrediction>, val weeks: List<WeekVo2max>, val windowStart: LocalDate, val windowEnd: LocalDate, val runsPendingDetail: Int)
  ```
- `DistanceRepPattern.matches(name): Boolean`: the R3 regex, on the name only.
- `SegmentExtractor.segmentOf(run): Segment?`: the R3 table in order, plus R4. It reuses `RunClassifier.classify`, `TreadmillPaceParser.parsePaceLine` and the pace limits.
- `RepLapFinder.find(laps, workout, treadmill): List<Lap>`: AC4.1–AC4.2.
- `DanielsFormulas`: `oxygenCost(metersPerMinute)`, `percentMaxForMinutes(minutes)`, and `raceTime(vdot, meters): Seconds` (bisection, R8).
- `Vo2maxEstimator.estimate(segment, maxHr): Double?`: R5 (null below `MIN_HR_FRACTION`).
- `Vo2maxCalculator.compute(runs, maxHr, today): Vo2maxReport`: R6, R7 and AC9.6. It uses `LocalDate` only, so DST can't shift a day. The current and weekly values share `weightedMean`.
- `Vo2maxService(maxHr: MaxHrSetting, tokens, syncService, runs, clock, zone)`: `suspend fun get(): Vo2maxResult`, with `sealed interface Vo2maxResult { NotConnected; InvalidConfig; ReauthRequired; data class Ready(val report: Vo2maxReport) }`. It checks the config, then the tokens, then calls `syncIfDue()`, then computes.

Units:
- Speeds are converted to m/min only inside `DanielsFormulas`.
- Paces are s/km as `Double` inside the segment (unrounded) and don't cross into `api`.
- Durations are whole seconds.
- API rounding uses `roundOneDecimal`, and race times are whole seconds (`roundHalfUpToInt`).

### Persistence
- `runs` gains `moving_time_s` INTEGER, nullable (`RunsTable.movingTimeS`). Only `saveDetail` writes it, and it is read into `RunActivity.movingTime`.
- `run_laps` gains `average_heartrate` REAL, nullable (`RunLapsTable.averageHeartrate`). `saveDetail` writes it and `toLap()` reads it.
- Migration in `DatabaseFactory.connect`: two more `addColumnIfMissing` calls (AC2.7). No new table.
- Pruning, `delete` and `deleteAll` are unchanged (the columns live on existing rows).

### Backend API
#### `GET /api/vo2max`
- Query params: none. Auth: `hasConnectedSession(tokens)`, else `401 not_connected`.
- `200` response (exact shape; invented values, today = 2026-10-31; `weeks` is shortened here, but the real list always has 27 entries for this date):
```json
{
  "maxHr": { "bpm": 194, "source": "default" },
  "current": {
    "vo2max": 52.3,
    "basis": "last7Days",
    "periodStart": "2026-10-25",
    "periodEnd": "2026-10-31",
    "runCount": 4
  },
  "predictions": [
    { "key": "5k", "label": "5K", "timeSeconds": 1157, "note": null },
    { "key": "10k", "label": "10K", "timeSeconds": 2399, "note": null },
    { "key": "half", "label": "Half marathon", "timeSeconds": 5311, "note": null },
    { "key": "marathon", "label": "Marathon", "timeSeconds": 11058, "note": "Marathon predictions from VO2max tend to be optimistic." }
  ],
  "windowStart": "2026-04-27",
  "windowEnd": "2026-11-01",
  "weeks": [
    { "weekStart": "2026-04-27", "weekEnd": "2026-05-03", "vo2max": 50.1, "runCount": 3, "inProgress": false },
    { "weekStart": "2026-05-04", "weekEnd": "2026-05-10", "vo2max": null, "runCount": 0, "inProgress": false },
    { "weekStart": "2026-10-26", "weekEnd": "2026-11-01", "vo2max": 52.6, "runCount": 3, "inProgress": true }
  ],
  "runsPendingDetail": 0
}
```
Field rules:
- `maxHr.bpm` is an integer (bpm). `maxHr.source` is `"default"` or `"configured"`.
- `current` is an object or `null`. `basis` is `"last7Days"` or `"last28Days"`. `vo2max` is in ml/kg/min with 1 decimal. Dates are `YYYY-MM-DD`. `runCount >= 1`.
- `predictions` has exactly 4 entries, in the order above, when `current` is set, and is `[]` when it is null. `timeSeconds` is an integer. `note` is a string only for `marathon`.
- `weeks` is never empty and is ascending and contiguous. The last entry is the only one with `inProgress: true`. `vo2max` is a number (1 decimal) or `null`, and it is `null` exactly when `runCount == 0`.
- `runsPendingDetail` is a non-negative integer.
- Errors (`{"error","message"}`, constants in `ErrorResponse`):

| Status | `error` | `message` |
|---|---|---|
| 401 | `not_connected` | existing text |
| 401 | `strava_reauth_required` | existing text |
| 422 | `vo2max_invalid_config` | "MAX_HR_BPM is not a whole number between 120 and 230." |
| 500 | `internal_error` | existing StatusPages handler |

DTOs live in `api/Vo2maxDtos.kt` (`Vo2maxResponse`, `MaxHrDto`, `CurrentVo2maxDto`, `RacePredictionDto`, `WeekVo2maxDto`). The mapping (rounding, basis strings, marathon note) lives in `api/Vo2maxMappers.kt`, and the thin route in `api/Vo2maxRoutes.kt`.

### Frontend (Angular)
- **Route / page:** no new route. `RunningDashboardPage` imports `Vo2maxSection`. In `running-dashboard-page.html`, `<app-vo2max-section />` goes on the line after `<app-training-load-section />`.
- **Service & interfaces** (`core/api/`):
  - `vo2max.models.ts`: `Vo2maxResponse`, `MaxHr` (`source: 'default' | 'configured'`), `CurrentVo2max` (`basis: 'last7Days' | 'last28Days'`), `RacePrediction` (`key: '5k' | '10k' | 'half' | 'marathon'`, `note: string | null`) and `WeekVo2max` (`vo2max: number | null`), matching the JSON field for field. Plus `isVo2maxResponse(value: unknown): value is Vo2maxResponse`.
  - `vo2max.service.ts`: `getVo2max(): Observable<Vo2maxResponse>` → `GET /api/vo2max`, `providedIn: 'root'`.
- **Components** (`features/vo2max/`, standalone, `OnPush`, plain interpolation only):
  - `vo2max-section.ts` (`Vo2maxSection`, `app-vo2max-section`) is the container:
    - It holds `signal<Vo2maxState>` (`loading | invalidConfig(message) | error | loaded(data)`).
    - It loads in `ngOnInit` with `takeUntilDestroyed`, leaves the state unchanged on 401, and has `retry()`.
    - It renders `section.card` with `h2` "VO2max" and the R13 states. When loaded, it shows the current block, `<app-race-prediction-tiles>`, `<app-vo2max-chart>` and the footnote.
  - `race-prediction-tiles/race-prediction-tiles.ts` (`predictions = input.required<RacePrediction[]>()`): four tiles (R12).
  - `vo2max-chart/vo2max-chart.ts` (`weeks = input.required<WeekVo2max[]>()`): `<app-chart>` with `buildVo2maxOptions`.
  - `vo2max.view.ts` (pure, unit-tested): `vo2maxStateFromError(error)`, `basisCaption(current)`, `footnoteText(data)`, and `emptyKind(data): 'none' | 'importing' | 'noCurrent' | null`.
  - `vo2max.options.ts`: `buildVo2maxOptions(weeks): EChartsCoreOption` (R11), using the shared chart theme helpers.
- **Formatting** (`shared/format/`): a new `duration.ts` with `formatRaceTime(seconds)`: `1197 → "19:57"`, `3599 → "59:59"`, `3600 → "1:00:00"`, `11449 → "3:10:49"`. The existing `formatOneDecimal`, `formatDayMonth` and `formatShortDate` are reused.
- **States:**

| View | Loading | Empty | Error | Normal |
|---|---|---|---|---|
| VO2max card | heading + "Loading VO2max…" (`role="status"`) | AC13.4 (none) or AC13.5 (importing); no tiles, no chart | 422 → settings problem + hint, no Retry. Other → "Couldn't load VO2max." + Retry (`role="alert"`) | value + caption → tiles → chart → footnote; or AC13.6 (no current) → chart → footnote |
| Prediction tiles | not rendered | not rendered | not rendered | 4 tiles; marathon with note |
| Chart | not rendered | not rendered | not rendered | weekly line with gaps for null weeks, tooltip |
| Rest of page | its own states, unaffected | same | same | same |

- **Chart:** a line chart. x = week start ("27 Apr"), y = "VO2max (ml/kg/min)" (`scale: true`). One series in `palette.accent`, symbols on, `connectNulls: false`, the tooltip from AC11.3, and the shared 300 px chart height.

### Files expected to change
Backend (`backend/`):
- `src/main/kotlin/app/stravadashboard/config/MaxHrSetting.kt` (new), `config/AppConfig.kt` (`maxHr`, parsing, `toString`)
- `src/main/kotlin/app/stravadashboard/strava/StravaDtos.kt` (`DetailedActivityDto.movingTime`, `LapDto.averageHeartrate`), `strava/StravaMappers.kt` (`toDetail`)
- `src/main/kotlin/app/stravadashboard/domain/model/RunActivity.kt` (`Lap.averageHeartRateBpm`, `RunActivity.movingTime`), `domain/ports/ActivitySource.kt` (`ActivityDetail.movingTime`)
- `src/main/kotlin/app/stravadashboard/domain/SyncPolicy.kt` (`needsLapHeartRate`, plan order, `pendingCount`)
- `src/main/kotlin/app/stravadashboard/domain/vo2max/` (new): `Vo2maxModels.kt`, `DistanceRepPattern.kt`, `SegmentExtractor.kt`, `RepLapFinder.kt`, `DanielsFormulas.kt`, `Vo2maxEstimator.kt`, `Vo2maxCalculator.kt`, `Vo2maxService.kt`
- `src/main/kotlin/app/stravadashboard/persistence/Tables.kt`, `DatabaseFactory.kt`, `ExposedRunRepository.kt` (`saveDetail`), `RunRowMappers.kt` (`toLap`, `toRun`)
- `src/main/kotlin/app/stravadashboard/api/Vo2maxRoutes.kt`, `Vo2maxDtos.kt`, `Vo2maxMappers.kt` (new), `api/ErrorResponse.kt` (`VO2MAX_INVALID_CONFIG`)
- `src/main/kotlin/app/stravadashboard/Application.kt` (wiring)
- Tests: `src/test/kotlin/app/stravadashboard/domain/vo2max/*Test.kt` (new), `domain/SyncServiceTest.kt`, `strava/StravaClientTest.kt`, `persistence/ExposedRunRepositoryTest.kt`, `api/Vo2maxRoutesTest.kt` (new), `AppConfigTest.kt`, `testsupport/TestData.kt` (a default `movingTime`, so existing tests don't see runs as pending), `testsupport/Fakes.kt`
- Fixture: `src/test/resources/strava/activity-detail.json` gains `moving_time` and lap `average_heartrate` (invented values)
- `.env.example` (repo root): `MAX_HR_BPM=`

Frontend (`frontend/src/app/`):
- `core/api/vo2max.models.ts`, `vo2max.service.ts` (+ `.spec.ts`) (new)
- `features/vo2max/vo2max-section.ts`, `.html`, `vo2max.scss`, `vo2max.view.ts`, `vo2max.options.ts`, `race-prediction-tiles/race-prediction-tiles.ts`, `vo2max-chart/vo2max-chart.ts` (+ specs) (new)
- `features/running-dashboard/running-dashboard-page.ts`, `.html`
- `features/running-dashboard/*.spec.ts` page specs: add `stubVo2maxSection()` to their setup (AC14.2)
- `shared/format/duration.ts` (+ spec) (new)
- `testing/vo2max-fixtures.ts`, `testing/vo2max-stub.ts` (new)

## Edge cases
| Case | Expected behaviour |
|---|---|
| No activities in range | All weeks null, `current = null`, `predictions = []` → empty state AC13.4. |
| No qualifying run in the last 28 days, but older ones | `current = null` → AC13.6: chart only, no tiles. |
| Manual activity (no GPS) | Usually no HR → excluded (AC3.2). With HR but no laps and an `NxMmin` name → no rep laps → excluded. As an easy run with HR, distance and ≥ 20 min, it counts as a whole run (Strava's own values). No GPS is ever read. |
| Activity without heart rate | Excluded. No extra detail call for it (AC2.5). |
| Treadmill workout without HR strap | Lap HR missing → excluded (AC4.7). |
| Treadmill run without pace line | Excluded (AC3.4). |
| Treadmill easy run with a pace line (e.g. "5:30") | Counts as a whole run: mean pace of the line, the run's average HR and moving time, with the 20-min and HR rules (AC3.11, AC3.12). |
| Treadmill pace line count ≠ number of rep laps | The mean of all pace values is used with the rep laps found (AC4.3). |
| Distance-rep workout ("6x1km", "10x400m", "5 x 1000m", "3x2k") | Excluded entirely, outdoor and treadmill, even if an `NxMmin` pattern is also present. Not counted as pending (AC3.9, AC3.10, AC9.6). The 001 dashboard still shows it as before. |
| "10k easy" or "6x10min" | Not a distance-rep name. Handled by the normal rules (AC3.9). |
| Short time reps (e.g. 8x2min) | `M < 4` → excluded (AC3.5). |
| Outdoor workout with only 1 km auto-laps | No rep laps → excluded (AC3.6). |
| Recovery laps as long as reps | The fastest (outdoor) or highest-HR (treadmill) `N` laps are kept (AC4.2). |
| Unit conversion | `SegmentExtractor` converts distance m → km and time s → s/km. `DanielsFormulas` converts s/km → m/min. The API sends VO2max (ml/kg/min) and race times (s). The frontend only formats. |
| HR above configured max | Capped at 100 % (AC5.4). |
| Very low HR (e.g. recovery jog) | Below 65 % of max HR → excluded (AC5.3). |
| Week spans DST change / time zones | Dates come from `start_date_local`, and the maths uses `LocalDate` only (AC6.5). |
| Today is Monday | The current week has only today's runs and is marked in progress (AC7.4). |
| Run before the lap-HR refetch | Not used yet. Counted in `runsPendingDetail`, shown as a footnote line (AC12.5) or the importing state (AC13.5). |
| Description pace line added later | Picked up by the existing 7-day recheck (001 AC4.10). |
| `MAX_HR_BPM` changed | After a restart, all values are recomputed from the store. No Strava calls (AC5.2). |
| `MAX_HR_BPM` invalid | 422 in this card only. The rest of the page works (AC10.3). |
| Strava 429 during the refetch | The sync stops and stored data is served. The remaining runs are retried after the limit (AC2.8). |
| Expired / revoked token | Refresh once. If revoked → `401 strava_reauth_required` → `/connect`. |

## Privacy
- **Data fields & sensitivity:**
  - Lap average heart rate (`laps[].average_heartrate`): **health data (GDPR Art. 9)**, newly stored per lap.
  - Run moving time: activity data (personal).
  - `MAX_HR_BPM`: a body-related config value, only in `.env`. It is returned in the response (`maxHr.bpm`) to the owner's session only, so the user can see what the estimate is based on.
  - Derived VO2max and race predictions: derived health data, computed per request and not stored.
  - Activity names and descriptions (user-written) are read in the backend, for the workout and distance-rep patterns and pace lines. They are never sent to the browser or logged.
  - No GPS, `latlng`, polylines or streams are used or requested.
- **Storage & retention:** the two new columns live in the existing `runs` and `run_laps` tables in the SQLite file at `DB_PATH`. They follow the existing retention (6 months + 41 days, pruned with the run) and are deleted by `deleteAll` when a different athlete connects. No browser storage is used.
- **Scopes:** no change (`activity:read_all`).
- **Applicable privacy-check items:**

| # | Status | How it's satisfied |
|---|---|---|
| 1 | ✅ | `MAX_HR_BPM` only from env; `.env.example` has no value; fixtures are invented. |
| 2 | ✅ | No change to token handling. |
| 3 | ✅ | The refetch uses the existing refresh/revoke flow (AC2.8). |
| 5 | ✅ | No new scope. |
| 6 | ✅ | Only the two fields needed are added. No raw detail response and no HR stream is stored. |
| 7 | ✅ | No GPS or streams requested or stored. |
| 8 | ✅ | Logs hold counts only: no bpm values, max HR, VO2max, names or descriptions. `AppConfig.toString` masks max HR (AC1.4). |
| 9 | ✅ | No change to client logging. |
| 10 | ✅ | Single error shape. The 422 message is fixed text without the value (AC9.3). |
| 11 | ✅ | The new route checks the session (AC9.2). |
| 13 | ✅ | Tooltip and texts are built from numbers, dates and backend labels only. No `[innerHTML]`. Names and descriptions never reach the browser. |
| 14 | n/a | GET only (it triggers the existing read-cache sync). |
| 15 | ✅ | The response has only chart and tile fields: no ids, names, descriptions or laps (AC9.1). |
| 16 | ✅ | No new dependency, no external calls. |
| 18 | ❌ deferred | Still no disconnect/delete action (as since 001). |
| 20 | ⚠ | The DB now also holds per-lap HR. Treat backups as health data. |
| 21 | ✅ | Detail fixture and test data use invented values. |

## Test plan
| AC | Test type | Test name |
|---|---|---|
| AC1.1–AC1.4 | backend unit | `R1 - MAX_HR_BPM unset gives default 194`, `R1 - MAX_HR_BPM valid range`, `R1 - MAX_HR_BPM invalid values do not stop startup`, `R1 - toString masks max HR` |
| AC1.5 | review | `.env.example` diff |
| AC2.1–AC2.3 | backend client (MockEngine) + persistence | `R2 - detail stores moving time and lap heart rate`, `R2 - missing moving_time stored as zero`, `R2 - summary upsert keeps moving time` |
| AC2.4–AC2.6 | backend unit (SyncPolicy / SyncService) | `R2 - old details with HR are refetched once after missing details`, `R2 - no refetch without HR`, `R2 - refetch counts as pending` |
| AC2.7 | backend persistence | `R2 - migration adds moving_time_s and lap average_heartrate keeping rows` |
| AC2.8 | backend unit | `R2 - 429 during refetch stops sync` |
| AC3.1–AC3.8 | backend unit (SegmentExtractor) | `R3 - easy outdoor run uses whole run`, `R3 - run without HR excluded`, `R3 - 20 minute minimum`, `R3 - treadmill without pace line excluded`, `R3 - reps under 4 minutes excluded`, `R3 - workout without rep laps excluded`, `R3 - implausible pace excluded`, `R3 - private run counts` |
| AC3.9 | backend unit (DistanceRepPattern + SegmentExtractor) | `R3 - distance rep names are excluded`, `R3 - time reps and 10k names are not distance reps` |
| AC3.10 | backend unit | `R3 - distance rep check wins over NxMmin` |
| AC3.11, AC3.12 | backend unit | `R3 - treadmill run with pace line counts as whole run`, `R3 - treadmill whole run uses mean pace and usual rules` |
| AC4.1–AC4.7 | backend unit (RepLapFinder / SegmentExtractor) | `R4 - rep lap duration tolerance`, `R4 - keeps N fastest outdoor laps`, `R4 - keeps N highest HR treadmill laps`, `R4 - treadmill segment uses description pace and rep HR`, `R4 - outdoor rep segment`, `R4 - recovery laps not taken`, `R4 - treadmill laps without HR excluded` |
| AC5.1–AC5.5 | backend unit | `R5 - oxygen cost and HR fraction example`, `R5 - max HR change changes VO2max`, `R5 - low HR excluded`, `R5 - HR above max capped` |
| AC6.1–AC6.6 | backend unit (Vo2maxCalculator) | `R6 - current from last 7 days`, `R6 - duration weighted mean`, `R6 - falls back to last 28 days`, `R6 - no current without runs in 28 days`, `R6 - buckets by local start date`, `R6 - rep segment weighted by rep time` |
| AC7.1–AC7.4 | backend unit | `R7 - 27 Monday weeks for 6 months`, `R7 - weekly weighted mean`, `R7 - empty week is null not zero`, `R7 - Monday current week in progress` |
| AC8.1–AC8.4 | backend unit (DanielsFormulas) | `R8 - VDOT 50 race times`, `R8 - VDOT 40 race times`, `R8 - order and marathon note` |
| AC9.1–AC9.7 | backend route (`testApplication`) + unit | `R9 - 200 exact JSON shape`, `R9 - 401 without session`, `R9 - 422 invalid config without sync`, `R9 - reuses concurrent sync`, `R9 - reauth required maps to 401`, `R9 - pending detail count excludes distance reps`, `R9 - rounding` |
| AC10.1–AC10.4 | frontend component (page) | `R10 - vo2max card directly after training load`, `R10 - one vo2max request`, `R10 - fails independently`, `R10 - 401 shows no error text` |
| AC10.5 | frontend component + manual check at 375 px and 1280 px | `R10 - card uses theme tokens and palette` |
| AC11.1–AC11.4 | frontend unit (options builder) | `R11 - weekly line in accent colour`, `R11 - null weeks break the line`, `R11 - tooltip lines incl. empty week`, `R11 - aria label` |
| AC12.1–AC12.5 | frontend component + view unit | `R12 - current value and basis caption`, `R12 - four tiles with race times`, `R12 - marathon note only on marathon`, `R12 - max HR footnote by source`, `R12 - pending note` |
| AC13.1–AC13.7 | frontend component | `R13 - loading`, `R13 - invalid config without retry`, `R13 - error with retry`, `R13 - empty`, `R13 - empty still importing`, `R13 - no current estimate shows chart only`, `R13 - normal order` |
| service | frontend service (`HttpTestingController`) | `R9 - getVo2max calls /api/vo2max` |
| formatter | frontend unit | `R12 - formatRaceTime under and over one hour` |
| AC14.1–AC14.4 | review + existing suites | existing specs pass with the stub, `R14 - 001 rep pace and classification unchanged with lap heart rate stored` |

## Out of scope
- Runalyze's mileage-based marathon adjustment (plain prediction only).
- Training-plan comparison, pacing or split advice.
- VO2max values from Garmin or other devices.
- Editing or writing back to Strava.
- Per-run VO2max drill-down or a list of runs used/excluded.
- Heart-rate streams for this feature (decision 3), and speed-stream rep detection for VO2max.
- Finding rep laps for distance-rep workouts (these are excluded for now).
- Elevation, temperature or treadmill-incline corrections.
- Using `LTHR_BPM` in the calculation (it only informed the default of 194).

## Suggested follow-ups
- A rule to find rep laps for distance-rep workouts (e.g. laps matching the rep distance), so they can count again.
- Show why runs were excluded (no HR, too short, no rep laps, treadmill without paces, distance reps), as counts in the footnote.
- An elevation correction for hilly outdoor runs (Runalyze has one).
- Per-rep pairing of treadmill paces with rep laps when the counts match, instead of the mean pace.
- A target-time marker on the marathon tile once plan-vs-actual exists (that feature should drop its own race predictor, see intent).
- Mark the race date (Sun 6 Dec 2026) on the chart.

## Risks & decisions to confirm
Approved with the spec on 2026-10-31; items 1–3 and 5–9 stand as written.

1. **One-time detail refetch.** Lap heart rate and moving time weren't stored before, so about 150–200 runs with HR get one more detail call (2–3 syncs, roughly 30–45 min). Meanwhile the 001 sync banner shows "still importing" and this card shows the pending count.
2. **HR → %VO2max uses Swain's relation `(%HRmax − 0.37) / 0.64`**, as Runalyze does, with two guards: segments under 65 % of max HR are dropped, and HR above max is capped at 100 %. The absolute values depend strongly on max HR. With the 194 estimate, every value moves by about 1 point per 2–3 bpm of max HR error.
3. **Treadmill reps use the mean of the pace line with the duration-weighted HR of the rep laps** (one combined segment), not per-rep pairs.
4. **Decided: distance-rep workouts are excluded.** Rep laps are found by duration (`M` min ± 15 %) from the `NxMmin` name. Runs whose name matches the distance-rep pattern ("6x1km", "10x400m", "5 x 1000m") are excluded entirely, outdoor and treadmill, until a rule to find their rep laps exists (AC3.9, AC3.10). The pattern is checked on the name only, so a distance-rep session whose name has no such pattern (e.g. "Track session" with "6x1km" only in the description) is still treated by the normal rules.
5. **"Weighted by duration" uses the segment's duration**: moving time for whole runs, but only the rep time for interval runs.
6. **Predictions use the current value rounded to 1 decimal**, so the tiles match the number shown.
7. **Decided: a treadmill run with a pace line but no `NxMmin` pattern counts as a whole run.** It uses the mean of the pace line, the run's average HR and its moving time, with the usual 20-minute and heart-rate rules (AC3.11, AC3.12). Max HR default stays a fixed 194, not derived from `LTHR_BPM`. An unset `MAX_HR_BPM` uses 194 with an "estimate" footnote, and only an invalid value gives the 422.
8. **No terrain or heat correction.** Hilly or hot runs raise HR for a given pace and so lower the estimate.
9. **Race times are checked against the solved equation (±3 s), not Daniels' printed table.** The printed tables are smoothed and run 1–9 s slower than the equation (9 s on a marathon), so they are only a 0.1 % sanity check (AC8.1, AC8.2). Changed after review with the user's approval.
