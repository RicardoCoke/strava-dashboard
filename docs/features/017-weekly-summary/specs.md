# Spec: Weekly summary notification

**Feature ID:** 017 · **Status:** Approved · **Intent:** [intent.md](./intent.md) · **Date:** 2026-11-03

## Summary
A new read-only endpoint, `GET /api/weekly-summaries`, uses only the local cache. For every Monday-Sunday week in the last 6 months with at least 5 runs, it builds up to 6 short, rule-based notes. Each note compares the week with last week and/or the average of the previous 4 weeks, and covers distance, time, longest run, pace, training load, HR drift, efficiency factor, resting HR and HRV wherever the data exists. Each summary has a `revision` hash. A bell in the running dashboard's header shows a red "1" badge while the newest summary's revision differs from the one last opened in this browser (localStorage). Clicking the bell opens a fixed, right-anchored overlay panel with ‹ › navigation through older qualifying weeks and a ✕ to close it.

## Requirements
| ID | Requirement | Priority (Must/Should/Could) |
|---|---|---|
| R1 | Qualifying weeks: Monday-Sunday weeks (bucketed by `start_date_local`) with ≥ 5 runs, from the last 6 months, newest first, with the current week flagged as in progress | Must |
| R2 | Comparison baselines: "last week" and "previous 4 weeks average" (weeks with no runs count as zero). A missing baseline drops its comparison, and a note with no comparison left is skipped | Must |
| R3 | Volume notes: distance (matching the 007 tiles' km rule, with a "watch load" flag above +10% vs the 4-week average) and time on feet | Must |
| R4 | Longest-run note: "Longest run of the block" when it beats every run of the previous 4 weeks, otherwise compared with the previous 4 weeks' longest | Must |
| R5 | Pace note: pooled average pace vs last week and the previous 4 weeks | Must |
| R6 | Training-load note: weekly sum of the 005 daily loads, flagged above +10% vs the 4-week average. Skipped without a valid LTHR or while the week's HR streams are pending | Must |
| R7 | Heart-response notes: long-run HR drift (013 rules and zones) and easy-run efficiency factor (014 rules) | Must |
| R8 | Wellness notes: resting HR and HRV weekly means from the 015 cache, flagged at the 015 thresholds | Must |
| R9 | Ranking and determinism: notes sorted by tone and then a fixed metric order, capped at 6. Exact text templates, and a SHA-256 `revision` over the week, run count and note texts | Must |
| R10 | `GET /api/weekly-summaries` returns the exact JSON below from the cache only, needs a connected session and never calls Strava or Garmin | Must |
| R11 | Bell with badge in the dashboard header: shows "1" only while the newest summary's revision ≠ the revision stored in localStorage. Opening the panel stores it | Must |
| R12 | Panel: fixed, right-anchored overlay that doesn't move or resize the page. Closes with ✕ or Escape and is keyboard accessible | Must |
| R13 | ‹ › navigation between qualifying weeks, newest shown by default | Must |
| R14 | Panel states (loading, error, empty, normal) and a quiet reload after each finished sync | Must |
| R15 | No regression: existing endpoints, JSON and cards are unchanged, with no new OAuth scope or external call | Must |

## Acceptance criteria

**Conventions.** `today = LocalDate.now(clock.withZone(zone))` (server zone, as in 001/005/014). A run is a stored activity with `isRun == true`, and its date is `RunActivity.localDate` (from `start_date_local`). `weekStartOf` = `WeeklyDistance.weekStartOf` (Monday). For a summary week starting `M`: **last week** = `M − 7 .. M − 1`, and **previous 4 weeks** = the four weeks starting `M − 28`, `M − 21`, `M − 14` and `M − 7`. Constants: `MIN_RUNS = 5`, `MAX_NOTES = 6`, `WINDOW_MONTHS = 6L`, `WATCH_PERCENT = RampRate.RAMP_THRESHOLD_PERCENT` (10), `EF_NOTABLE_PERCENT = 2.0`, `RHR_WATCH_BPM = WellnessPolicy.RHR_FLAG_BPM` (5.0), `HRV_WATCH_PERCENT = WellnessPolicy.HRV_FLAG_FRACTION × 100` (15), `MIN_WELLNESS_VALUES = WellnessPolicy.MIN_BASELINE_VALUES` (3). Rounding is half-up (`roundOneDecimal`, `roundHalfUpToInt`), and comparisons use unrounded values unless a rule says otherwise. Examples use `today = 2026-11-08` (Sunday), with summary week `2026-11-02..2026-11-08`. All numbers are invented.

**Shared text pieces** (used by R3–R8; the minus sign is ASCII `-`):
- `pct(p, ref)`: `p > 0` gives `up p% vs ref`, `p < 0` gives `down |p|% vs ref`, and `p == 0` gives `level with ref`.
- `delta(d, unit, ref)`: `d > 0` gives `+d unit vs ref`, `d < 0` gives `-|d| unit vs ref`, and `d == 0` gives `same as ref`.
- `ref` is `your 4-week average` or `last week`. Segments are joined with `, ` inside one pair of brackets, the 4-week segment first. Notes have no trailing full stop.
- Percent vs a baseline `b`: `(value − b) / b × 100`. It is missing when `b == 0`.

### R1 — Qualifying weeks
- **AC1.1** Given a week with 4 runs, when summaries are built, then that week has no summary.
- **AC1.2** Given the current week with 4 runs, when a 5th run dated in the same week is stored and summaries are rebuilt, then the list's first entry is that week, with `runCount = 5` and `inProgress = true`.
- **AC1.3** Given a run with `start_date_local` 2026-11-01T23:30 (Sunday) whose UTC start is 2026-11-02, then it counts in the week of 2026-10-26, not 2026-11-02.
- **AC1.4** Given 5 non-run activities (e.g. rides) plus 4 runs in a week, then the week does not qualify, and the rides count in no figure of any note.
- **AC1.5** Given qualifying weeks starting 2026-11-02, 2026-10-19 and 2026-05-04, and `today.minusMonths(6) = 2026-05-08`, then the list is `2026-11-02`, `2026-10-19`, `2026-05-04` (newest first). The included weeks are those whose `weekStart >= weekStartOf(today.minusMonths(6))`. A qualifying week starting 2026-04-27 is not listed.
- **AC1.6** Given a past qualifying week, then `inProgress = false`, and `weekEnd = weekStart + 6`.
- **AC1.7** Given a manual run without GPS, HR or moving time (distance only), then it counts towards `runCount` and distance, and it is left out of the metrics that need the missing fields (R3 time, R5, R6, R7).

### R2 — Comparison baselines
- **AC2.1** Given previous 4 weeks of 50.0, 0.0 (no runs), 52.0 and 58.3 km, then the distance 4-week average is 40.075 km (the empty week counts as 0).
- **AC2.2** Given a metric whose 4-week baseline is missing but whose last-week value exists, then the note shows only the last-week segment.
- **AC2.3** Given a metric with neither baseline available, then that metric has no note (it is not shown empty).
- **AC2.4** Given the same summary week, then its baselines don't depend on whether the comparison weeks themselves qualify (≥ 5 runs).

### R3 — Distance and time notes
Week km = `Meters(sum of run distances).toKmOneDecimal()` (the `WeeklyDistance` rule, so it equals the weekly chart and the 007 tiles). The last-week delta is `roundOneDecimal(thisKm − lastKm)` with no negative zero, as in `WeekComparison`. The 4-week average is the mean of the four rounded week km values. The percent is computed against the unrounded mean.
- **AC3.1** Given this week 62.4 km over 5 runs, last week 58.3 km, and previous 4 weeks 50.0, 51.3, 52.0 and 58.3 km (average 52.9), then the distance note is `Volume 62.4 km over 5 runs (up 18% vs your 4-week average, +4.1 km vs last week), watch load`, with tone `worse`.
- **AC3.2** Given a 4-week percent of exactly +10, then there is no `, watch load` suffix and the tone is `neutral`. Given +11, the suffix is there and the tone is `worse` (strictly above `WATCH_PERCENT`, as in `RampRate`).
- **AC3.3** Given the current week of 2026-11-02, then the note's `62.4` and `+4.1` equal the 007 tiles' `thisWeekKm` and `differenceKm` for the same data.
- **AC3.4** Time = the sum of `movingTime` over the week's runs. Given every run in the week has `movingTime`, 18 720 s in total, last week 17 220 s and a 4-week average of 17 100 s, then the note is `Time on feet 5 h 12 min (up 9% vs your 4-week average, +25 min vs last week)`, with tone `neutral`. Durations are whole minutes (half-up): `H h MM min` from 60 min upwards, else `M min`. Deltas use the same format with a sign.
- **AC3.5** Given any run in the summary week with `movingTime == null`, then there is no time note. Given a comparison week (or any of the previous 4 weeks) with such a run, then that comparison is missing (R2).

### R4 — Longest-run note
- **AC4.1** Given this week's longest run is 32 000 m and the longest run in the previous 4 weeks is 30 500 m, then the note is `Longest run of the block: 32.0 km`, with tone `better`.
- **AC4.2** Given this week's longest is 24 100 m and the previous 4 weeks' longest is 32 000 m, then the note is `Longest run 24.1 km (longest in the previous 4 weeks: 32.0 km)`, with tone `neutral`.
- **AC4.3** Given this week's longest equals the previous 4 weeks' longest (in metres), then AC4.2's form is used (strictly greater is needed for "of the block").
- **AC4.4** Given no runs at all in the previous 4 weeks, then there is no longest-run note (R2).

### R5 — Pace note
Pace = `paceOf(sum movingTime, sum distance)` over the runs with `movingTime > 0` and `distance > 0`, formatted `m:ss /km`. The difference is whole seconds per km (`this − other`). Negative means faster.
- **AC5.1** Given this week's pace is 321 s/km, the 4-week pooled pace is 325 s/km and last week's is 321 s/km, then the note is `Average pace 5:21 /km (4 s/km faster than your 4-week average, same pace as last week)`, with tone `neutral`. Slower reads `N s/km slower than …`.
- **AC5.2** Given no run in the summary week with moving time, then there is no pace note. Given a comparison period with no such run, then that segment is missing.

### R6 — Training-load note
Load = the sum, over the week's dates, of `TrainingLoadCalculator.dailyLoads(runs, HrZones.of(lthr), today)` (005 TRIMP; days without counted runs are 0). It is shown as a whole number. The percentages are integers.
- **AC6.1** Given a week load of 520.4, last week 480.0 and a 4-week average of 450.0, then the note is `Training load 520 (up 16% vs your 4-week average, up 8% vs last week), watch load`, with tone `worse`. (Last week uses `pct` too.)
- **AC6.2** Given `LTHR_BPM` not configured or invalid, then there is no load note, and the endpoint still returns 200.
- **AC6.3** Given a run in the summary week with `averageHeartRateBpm != null` and `hrHistogram == null` (HR stream still pending), then there is no load note.
- **AC6.4** Given runs without HR, then they add 0 load (as in 005), and the note is still built from the other runs.

### R7 — HR drift and efficiency factor notes
Drift runs: `isRun`, `RunClassifier.classify == LongRun`, `!trainer`, `hrDrift.status == COMPUTED` (the 013 POINT bucket). The week value is the mean of unrounded `driftPct`, rounded to 1 decimal. Its zone is `LongRunDriftReport.zoneOf(rounded)`. Zone words: GOOD `good`, WATCH `worth watching`, POOR `high`. Tones: GOOD `better`, WATCH `neutral`, POOR `worse`. The baseline is the mean drift of drift runs in the previous 4 weeks only.
- **AC7.1** Given one drift run this week at 6.24% and two in the previous 4 weeks at 4.6% and 5.0%, then the note is `Long-run HR drift 6.2%, worth watching (previous 4 weeks: 4.8%)`, with tone `neutral`.
- **AC7.2** Given no drift run this week, or none in the previous 4 weeks, then there is no drift note.
- **AC7.3** Given a treadmill long run, or a long run whose drift is `TOO_SHORT`, `NO_DATA` or not fetched yet, then it is not used.

EF runs: the runs that qualify under 014 R1 (easy or long, outdoor, HR > 0, `movingTime >= 1200 s`, distance > 0). Per-run EF is from the 014 formula, and the week value is the unweighted mean. Baselines are the unweighted per-run mean over last week and over the previous 4 weeks (pooled). The value is shown to 2 decimals, and the percentages to 1 decimal. Tone (from the 4-week percent, rounded): `≥ +2.0` gives `better`, `≤ -2.0` gives `worse`, otherwise `neutral`. Without a 4-week baseline the tone is `neutral`.
- **AC7.4** Given a week EF mean of 1.652, a 4-week mean of 1.613 and a last-week mean of 1.636, then the note is `Easy-run efficiency 1.65 m/min/beat (up 2.4% vs your 4-week average, up 1.0% vs last week)`, with tone `better`.
- **AC7.5** Given no qualifying EF run in the summary week, then there is no EF note.

### R8 — Resting HR and HRV notes
From `WellnessRepository.findBetween(earliest M − 28, latest Sunday)`. A period's value is the mean of the days with a value. It is missing when the period has fewer than 3 values. The 4-week period is the 28 days `M − 28 .. M − 1`.
- **AC8.1** Given a resting HR week mean of 48.3, a 4-week mean of 42.9 and last week 46.3, then the note is `Resting HR averaged 48.3 bpm (+5.4 bpm vs your 4-week average, +2.0 bpm vs last week)`, with tone `worse` (because the rounded 4-week delta is ≥ 5.0). At +4.9 the tone is `neutral`.
- **AC8.2** Given an HRV week mean of 52.4 ms, a 4-week mean of 63.9 ms and last week 55.8 ms, then the note is `HRV averaged 52 ms (down 18% vs your 4-week average, down 6% vs last week)`, with tone `worse` (because the 4-week percent is ≤ -15). At -14 the tone is `neutral`.
- **AC8.3** Given Garmin is not configured (no rows), or fewer than 3 values in the summary week, then there is no note for that metric.
- **AC8.4** Given a summary week older than the 90-day wellness retention, then its wellness notes are skipped by AC8.3, and the other notes are unaffected.

### R9 — Ranking, cap, determinism
- **AC9.1** Given more than 6 candidate notes, then `notes` holds 6: they are sorted by tone (`worse`, then `better`, then `neutral`), then by the fixed metric order `distance`, `longestRun`, `trainingLoad`, `hrDrift`, `restingHr`, `hrv`, `efficiency`, `time`, `pace`. The rest are dropped.
- **AC9.2** Given the same stored runs, wellness rows, LTHR and `today`, when the endpoint is called twice, then the two bodies are byte-identical.
- **AC9.3** `revision` = lowercase hex SHA-256 of `"{weekStart}|{runCount}|" + notes.joinToString("\n") { it.text }`. Given a 6th run added to the week with identical note texts, then the revision changes (because of the run count). Given only `inProgress` changing from true to false (on Monday), then the revision is unchanged.
- **AC9.4** Given any qualifying week, then it has at least one note (the distance note always has a last-week comparison, because a last week with no runs is 0.0 km).

### R10 — Endpoint
- **AC10.1** Given a connected session, when `GET /api/weekly-summaries` is called, then the response is 200 with exactly the JSON shape in *Backend API*.
- **AC10.2** Given no session cookie, or a session but no stored token, then the response is 401 `{"error":"not_connected","message":"Connect your Strava account."}`.
- **AC10.3** Given the request is served, then neither the Strava nor the Garmin `MockEngine` records a request.
- **AC10.4** Given no qualifying weeks (including no runs at all), then the response is 200 `{"summaries":[]}`.
- **AC10.5** Given the response, then it contains no activity names, descriptions, ids, coordinates or tokens.

### R11 — Bell and badge
localStorage key `sd.weeklySummary.seenRevision` holds one string: the revision last opened. "Newest" means `summaries[0]`.
- **AC11.1** Given the dashboard header, then a bell button (`aria-label="Weekly summary"`) sits at the right of the `Running` heading.
- **AC11.2** Given `summaries[0].weekStart` is the current or the previous Monday (browser local date) and `summaries[0].revision` ≠ the stored value (or nothing is stored), then a red badge with the text `1` shows, and the button's label is `Weekly summary, 1 new`.
- **AC11.3** Given 12 qualifying weeks on first use, then the badge shows `1`, never `12`.
- **AC11.4** Given the badge shows, when the bell is clicked, then the panel opens, `summaries[0].revision` is written to the key and the badge disappears.
- **AC11.5** Given the stored value equals `summaries[0].revision`, when a sync finishes and the reloaded `summaries[0].revision` differs (a run was added to the week), then the badge shows again (S6). If the panel is open at that moment, the new revision is stored straight away and no badge shows.
- **AC11.6** Given `summaries: []`, loading, or an error, then no badge shows.
- **AC11.7** Given localStorage throws (disabled storage), then the page doesn't break, the badge treats the summary as unseen, and opening still works.
- **AC11.8** Given the newest qualifying week is last week (this week has < 5 runs) and it has already been opened, then no badge shows. Older weeks never light the badge.
- **AC11.9** Given the newest qualifying week started two or more weeks before the current Monday, then no badge shows (it stays browsable in the panel).

### R12 — Panel overlay and closing
- **AC12.1** Given the panel is open, then it is anchored to the right edge of the viewport with `position: fixed`, and the `.cards` container keeps the same parent, siblings and size as when the panel is closed. (Component test: no element is inserted into the page flow. Manual check: charts don't move or resize.)
- **AC12.2** Given the panel is open, when ✕ (`aria-label="Close weekly summary"`) is clicked or Escape is pressed, then the panel closes and focus returns to the bell.
- **AC12.3** Given the panel opens, then it has `role="dialog"` and `aria-labelledby` pointing to its week heading. The bell has `aria-expanded` set to match and `aria-controls` set to the panel id. Focus moves to the panel heading.
- **AC12.4** Given the bell is clicked while the panel is open, then the panel closes (toggle).

### R13 — Week navigation
- **AC13.1** Given the panel opens, then it shows `summaries[0]`, even if an older week was showing when it was last closed.
- **AC13.2** Given 3 summaries showing index 0, when ‹ (`aria-label="Older week"`) is clicked, then index 1 shows. › (`aria-label="Newer week"`) goes back. ‹ is disabled on the oldest week, and › on the newest.
- **AC13.3** Given a week is shown, then its heading reads `27 Oct – 2 Nov 2026` (from `formatDayMonth(weekStart)` and `formatDayMonthYear(weekEnd)`), followed by `5 runs`. `In progress` shows when `inProgress` is true.
- **AC13.4** Given a quiet reload while an older week is shown, then the panel keeps that week (matched by `weekStart`) if it is still in the list, and otherwise shows `summaries[0]`.

### R14 — Panel states and reload
- **AC14.1 Loading:** given the panel is open before the response arrives, then it shows `app-ring-spinner` with `Loading weekly summary…` (`role="status"`).
- **AC14.2 Error:** given a non-401 failure, or a body that fails `isWeeklySummariesResponse`, then the panel shows `Couldn't load your weekly summary.` and a `Retry` button (`role="alert"`). Retry makes a new request. A 401 is left to the interceptor.
- **AC14.3 Empty:** given `summaries: []`, then the panel shows `No week with 5 or more runs yet. A summary appears once a Monday–Sunday week has 5 runs.` and no arrows.
- **AC14.4 Normal:** given summaries, then the panel shows the heading (AC13.3), the arrows, and the notes as a list in order. Each item has a tone class `tone-worse`, `tone-better` or `tone-neutral`, and its text is rendered by plain interpolation.
- **AC14.5** Given the bell is created, then it makes exactly one `GET /api/weekly-summaries` request, whether or not the panel is open.
- **AC14.6** Given `SyncStore.dataVersion` changes, then the data reloads quietly: current content is kept while loading and if the reload fails.

### R15 — No regression
- **AC15.1** Given the existing backend and frontend tests, then they pass. The page specs only gain a stub for the bell, like the other sections. Exposing `EfficiencyFactorCalculator.efficiencyOf` leaves the 014 output unchanged.
- **AC15.2** Given the OAuth scopes, the Strava client, the sync policy and the Garmin client, then they are unchanged.

## Traceability
| Success criterion (intent) | Covered by |
|---|---|
| S1 no summary under 5 runs; badge 1 when the 5th run syncs | R1, R11, R14 |
| S2 panel overlays on the right; charts don't move | R12 |
| S3 notes from own numbers vs last week / 4-week average | R2, R3, R4, R5, R6, R7, R8 |
| S4 distance, time, longest run, pace, load, drift, EF, RHR/HRV where data exists | R3, R4, R5, R6, R7, R8 |
| S5 opening clears the badge; ✕ closes | R11, R12 |
| S6 badge returns when the summary changes | R9 (revision), R11, R14 |
| S7 ‹ › to older qualifying weeks | R1, R13 |
| S8 same data, same notes; no randomness or AI | R9 |
| Decision 1 (4-week average includes all weeks) | R2 |
| Decision 2 (only newest week badges) | R11 |
| Decision 3 (cap of about 6, most notable first) | R9 |

## Design

### Data from Strava
- Endpoints: **none new.** All inputs are already cached by the existing sync (`GET /athlete/activities`, `GET /activities/{id}`, and the streams calls of 005 and 013).
- Scopes: unchanged.
- Fields used (via `RunActivity`): `sport_type`, `start_date_local`, `distance` (m), `moving_time` (s), `average_heartrate`, `trainer`, the stored HR histogram and drift result, and `name`/`description` only through `RunClassifier` (never returned).
- Sync & caching: unchanged. The endpoint reads `RunRepository.findAll()` and `WellnessRepository.findBetween(...)` per request. The run cache keeps 6 months + 41 days, which covers the oldest summary week's previous 4 weeks (≤ 6 months + 34 days).
- Errors: the endpoint makes no external call, so Strava 401/429 and network failures can't happen here. They stay with the 009 sync banner. A missing token gives 401 `not_connected`.

### Domain model & logic
New package `app.stravadashboard.domain.weeklysummary` (pure apart from the service):
```kotlin
enum class NoteMetric { DISTANCE, LONGEST_RUN, TRAINING_LOAD, HR_DRIFT, RESTING_HR, HRV, EFFICIENCY, TIME, PACE } // ranking order
enum class NoteTone { WORSE, BETTER, NEUTRAL } // ranking order

data class SummaryNote(val metric: NoteMetric, val tone: NoteTone, val text: String)

data class WeeklySummary(
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val inProgress: Boolean,
    val runCount: Int,
    val notes: List<SummaryNote>,
    val revision: String,
)
```
- `WeeklySummaryBuilder.build(runs, wellnessDays, zones: HrZones?, today): List<WeeklySummary>`: picks the qualifying weeks (R1) and, for each, the per-metric figures and baselines (R2). It calls `WeeklySummaryNotes` and computes `revision` with `java.security.MessageDigest`.
- `WeeklySummaryNotes`: one small function per metric. Each returns `SummaryNote?` from the figures, using the shared text pieces (R3–R8). `rank(notes)` sorts by `tone.ordinal`, then `metric.ordinal`, and takes `MAX_NOTES`.
- Reuse, no duplication:
  - `WeeklyDistance.weekStartOf` and `Meters.toKmOneDecimal`, `paceOf`, `roundOneDecimal`, `roundHalfUpToInt`.
  - `TrainingLoadCalculator.dailyLoads`.
  - `RampRate.RAMP_THRESHOLD_PERCENT`.
  - `LongRunDriftReport.zoneOf`.
  - `WellnessPolicy` constants.
  - `EfficiencyFactorCalculator`: make the per-run qualification and EF reusable as `internal fun efficiencyOf(run: RunActivity, today: LocalDate): Double?` (it wraps the existing private `sampleOf`, with no behaviour change).
- `WeeklySummaryService(lthr: LthrSetting, tokens, runs, wellness: WellnessRepository, clock, zone)`: returns `NotConnected` when there is no token, else `Ready(list)`. `zones` is `HrZones.of(lthr)` only for `LthrSetting.Valid`, else null (no load note). Wellness rows come from `findBetween(weekStartOf(today.minusMonths(6)).minusDays(28), today)`.
- Time zone: weeks are bucketed only by `localDate`, so DST and travel don't shift runs.

### Persistence
No changes. Nothing new is stored on the backend.

### Backend API
- `GET /api/weekly-summaries`: no query params, needs a connected session (`hasConnectedSession`).
- Response (JSON, exact):
```json
{
  "summaries": [
    {
      "weekStart": "2026-11-02",
      "weekEnd": "2026-11-08",
      "inProgress": true,
      "runCount": 5,
      "revision": "3f1c0e9b6a...64 lowercase hex chars",
      "notes": [
        { "metric": "distance", "tone": "worse", "text": "Volume 62.4 km over 5 runs (up 18% vs your 4-week average, +4.1 km vs last week), watch load" },
        { "metric": "longestRun", "tone": "better", "text": "Longest run of the block: 32.0 km" }
      ]
    }
  ]
}
```
  - `weekStart`, `weekEnd`: ISO date strings (`weekEnd = weekStart + 6`).
  - `inProgress`: boolean. `runCount`: int ≥ 5. `revision`: string (64 lowercase hex).
  - `notes`: 1–6 items, already ranked. `metric` ∈ `"distance" | "time" | "longestRun" | "pace" | "trainingLoad" | "hrDrift" | "efficiency" | "restingHr" | "hrv"`. `tone` ∈ `"worse" | "better" | "neutral"`. `text`: display string with units in it.
  - `summaries`: newest first, and may be empty.
- Errors: 401 `not_connected`. Unexpected failures give 500 `internal_error` through the existing StatusPages.

### Frontend (Angular)
- Route / page: no new route. The bell lives in the running dashboard page header (that page provides `SyncStore`). `running-dashboard-page.html` wraps `<h1>Running</h1>` in `<header class="page-header">` together with `<app-weekly-summary-bell />`, aligned to the right.
- Components:
  - `WeeklySummaryBell` (container, `features/weekly-summary/weekly-summary-bell.ts`): owns the request, the state signal, `open`, the selected `weekStart`, the badge `computed` and the quiet reload on `SyncStore.dataVersion` (same pattern as `EfficiencyFactorSection`, with optional `SyncStore` injection). It renders the bell button with an inline SVG icon (no icon font or CDN) and the badge. When `open`, it renders the panel. It handles Escape (`(document:keydown.escape)`) and focus return.
  - `WeeklySummaryPanel` (presentational, `features/weekly-summary/weekly-summary-panel/`). Inputs: `state` (loading | error | loaded), `summary: WeeklySummary | null`, `hasOlder`, `hasNewer`. Outputs: `closed`, `older`, `newer`, `retry`. Styles: `position: fixed; top: 0; right: 0; height: 100vh; width: min(360px, 100vw); overflow-y: auto; z-index` above the cards, plus a shadow. No backdrop, and the page isn't blocked.
  - `seen-revision.ts`: `readSeenRevision(): string | null` and `writeSeenRevision(rev: string): void`. Both wrap `localStorage` in try/catch.
- Service & TS interfaces (`core/api/weekly-summary.models.ts`, `core/api/weekly-summary.service.ts`):
```ts
export type NoteMetric = 'distance' | 'time' | 'longestRun' | 'pace' | 'trainingLoad' | 'hrDrift' | 'efficiency' | 'restingHr' | 'hrv';
export type NoteTone = 'worse' | 'better' | 'neutral';
export interface SummaryNote { metric: NoteMetric; tone: NoteTone; text: string; }
export interface WeeklySummary { weekStart: string; weekEnd: string; inProgress: boolean; runCount: number; revision: string; notes: SummaryNote[]; }
export interface WeeklySummariesResponse { summaries: WeeklySummary[]; }
export function isWeeklySummariesResponse(body: unknown): body is WeeklySummariesResponse;
```
  `WeeklySummaryService.getSummaries()` calls `GET /api/weekly-summaries`.
- States (inside the panel): loading, error, empty and normal, as in R14. The bell itself is always visible, and the badge shows only in the loaded state (R11).
- Chart: none.

### Files expected to change
- `backend/src/main/kotlin/app/stravadashboard/domain/weeklysummary/WeeklySummary.kt` (new: models)
- `backend/src/main/kotlin/app/stravadashboard/domain/weeklysummary/WeeklySummaryBuilder.kt` (new)
- `backend/src/main/kotlin/app/stravadashboard/domain/weeklysummary/WeeklySummaryNotes.kt` (new)
- `backend/src/main/kotlin/app/stravadashboard/domain/weeklysummary/WeeklySummaryService.kt` (new)
- `backend/src/main/kotlin/app/stravadashboard/domain/efficiency/EfficiencyFactorCalculator.kt` (expose `efficiencyOf`)
- `backend/src/main/kotlin/app/stravadashboard/api/WeeklySummaryDtos.kt`, `WeeklySummaryMappers.kt`, `WeeklySummaryRoutes.kt` (new)
- `backend/src/main/kotlin/app/stravadashboard/Application.kt` (wire the service and route)
- `backend/src/test/kotlin/app/stravadashboard/domain/weeklysummary/WeeklySummaryBuilderTest.kt`, `WeeklySummaryNotesTest.kt` (new)
- `backend/src/test/kotlin/app/stravadashboard/api/WeeklySummaryRoutesTest.kt` (new)
- `frontend/src/app/core/api/weekly-summary.models.ts`, `weekly-summary.service.ts`, `weekly-summary.service.spec.ts` (new)
- `frontend/src/app/features/weekly-summary/weekly-summary-bell.ts|.html|.scss|.spec.ts` (new)
- `frontend/src/app/features/weekly-summary/weekly-summary-panel/weekly-summary-panel.ts|.html|.scss|.spec.ts` (new)
- `frontend/src/app/features/weekly-summary/seen-revision.ts`, `seen-revision.spec.ts` (new)
- `frontend/src/app/features/running-dashboard/running-dashboard-page.html|.ts|.scss` (header and import)
- `frontend/src/app/testing/weekly-summary-stub.ts` (new) and the `running-dashboard-page*.spec.ts` files (add the stub override)

## Edge cases
| Case | Expected behaviour |
|---|---|
| No activities at all | `{"summaries":[]}`, no badge, and the panel shows the empty state |
| Week with exactly 4 runs, then a 5th syncs | No summary, then a summary with a badge after the quiet reload |
| Manual activity (no GPS, no HR, maybe no moving time) | Counts towards runs, distance and longest run. A missing moving time drops the time note (R3) and is left out of pace. No HR means no load, drift or EF contribution |
| Activity without heart rate | 0 load, left out of EF and drift. The other notes are unaffected |
| Treadmill run | Counts for distance, time, pace and load. Left out of drift and EF (013/014 rules) |
| Non-run activities | Ignored everywhere (`isRun`) |
| Fewer than 4 prior weeks of history | Can't happen inside the 6-month window, because the cache holds 6 months + 41 days. Empty prior weeks count as 0 (Decision 1) |
| 4-week baseline is 0 (all four weeks empty) | Percent segment missing. The last-week segment can still show |
| Garmin not configured / no wellness rows | No resting HR or HRV notes |
| LTHR missing or invalid | No load note. The other notes still show, with status 200 |
| Data still importing (details, HR streams, drift streams) | Notes appear or change as data arrives, so the revision changes and the badge returns |
| Week spans DST change / time zones | Bucketed by `start_date_local` date only, so runs never move week |
| Run at 23:30 local Sunday that is Monday UTC | Counts in the Sunday's week (AC1.3) |
| localStorage disabled or cleared | Treated as unseen, so the badge shows once |
| Two runs on the same day | Both count, and the longest-run note uses the longer one |
| Monday rollover with an already opened summary | The revision excludes `inProgress`, so no new badge |

## Privacy
- **Data fields & sensitivity:** distances, times, pace and run counts (personal). Training load, HR drift, efficiency factor, resting HR and HRV are derived from heart rate, which is **health data (GDPR Art. 9)**. No GPS, polylines, activity names or descriptions are returned.
- **Storage & retention:** nothing new is stored on the backend, and notes are computed per request from the existing caches (runs: 6 months + 41 days; wellness: 90 days). The browser stores only `sd.weeklySummary.seenRevision`, an opaque SHA-256 hex string with no readable personal data. It isn't cleared by "disconnect" (it is harmless, and a new connection simply shows the badge once).
- **Scopes:** unchanged. No new Strava or Garmin call.
- **Applicable privacy-check items:**
  - 1 ✅ No secrets. Fixtures are invented.
  - 2 ✅ No tokens in responses or localStorage.
  - 5 ✅ No new scopes.
  - 6 ✅ No new stored fields.
  - 7 ✅ No GPS used.
  - 8 ✅ Note texts and HR values are not logged. The route logs nothing beyond the existing call logging (path only).
  - 9 ✅ No client logging change.
  - 10 ✅ Errors use the standard shape.
  - 11 ✅ The route checks the session.
  - 13 ✅ Notes are rendered by interpolation, and the text holds only numbers and fixed words (no Strava free text). No `[innerHTML]`.
  - 14 n/a (GET only, and the read state is client-side).
  - 15 ✅ Only the fields the UI needs.
  - 16 ✅ The bell icon is an inline SVG, with no CDN or font.
  - 18 ✅ Disconnect still deletes runs. Wellness behaves as in 015.
  - 21 ✅ Invented fixture data.
  - The B4 rule "no personal data in localStorage" is met because only a hash is stored. Confirm below.

## Test plan
| AC | Test type | Test name |
|---|---|---|
| AC1.1–AC1.7 | backend unit (builder) | `R1 - week with 4 runs has no summary`, `R1 - fifth run creates in-progress summary`, `R1 - buckets by start_date_local`, `R1 - non-runs ignored`, `R1 - six-month window newest first`, `R1 - past week not in progress`, `R1 - manual run counts for distance only` |
| AC2.1–AC2.4 | backend unit | `R2 - empty prior week counts as zero`, `R2 - missing baseline drops segment`, `R2 - note skipped without any baseline`, `R2 - baselines ignore qualification` |
| AC3.1–AC3.5 | backend unit (notes + builder) | `R3 - distance note text and watch load`, `R3 - watch threshold strictly above 10`, `R3 - distance matches week comparison`, `R3 - time note format`, `R3 - missing moving time skips time` |
| AC4.1–AC4.4 | backend unit | `R4 - longest of the block`, `R4 - longest compared with previous 4 weeks`, `R4 - tie is not of the block`, `R4 - no prior runs no note` |
| AC5.1–AC5.2 | backend unit | `R5 - pace note faster and same`, `R5 - no moving time no pace note` |
| AC6.1–AC6.4 | backend unit | `R6 - load note with watch load`, `R6 - no LTHR no load note`, `R6 - pending HR stream skips load`, `R6 - runs without HR add zero` |
| AC7.1–AC7.5 | backend unit | `R7 - drift note with zone`, `R7 - no drift baseline no note`, `R7 - excluded drift runs`, `R7 - EF note with tone`, `R7 - no EF run no note` |
| AC8.1–AC8.4 | backend unit | `R8 - resting HR note and threshold`, `R8 - HRV note and threshold`, `R8 - too few wellness values no note`, `R8 - old week skips wellness only` |
| AC9.1–AC9.4 | backend unit | `R9 - ranking and cap of six`, `R9 - deterministic output`, `R9 - revision rules`, `R9 - every summary has a note` |
| AC10.1–AC10.5 | backend route (`testApplication`) | `R10 - returns summaries json`, `R10 - 401 without session or token`, `R10 - no external calls`, `R10 - empty list`, `R10 - no names ids or tokens` |
| AC11.1–AC11.8 | frontend component (bell) + unit (seen-revision) | `R11 - bell in header`, `R11 - badge when revision unseen`, `R11 - badge never above one`, `R11 - opening clears badge`, `R11 - badge returns after sync change`, `R11 - no badge when empty loading or error`, `R11 - survives storage errors`, `R11 - older weeks never badge` |
| AC12.1–AC12.4 | frontend component + manual | `R12 - panel is fixed overlay outside flow`, `R12 - close with cross and Escape`, `R12 - dialog aria and focus`, `R12 - bell toggles panel` |
| AC13.1–AC13.4 | frontend component | `R13 - opens on newest week`, `R13 - older and newer arrows`, `R13 - week heading and in progress`, `R13 - keeps week on reload` |
| AC14.1–AC14.6 | frontend component + service | `R14 - loading state`, `R14 - error state and retry`, `R14 - empty state`, `R14 - notes with tone classes`, `R14 - single request`, `R14 - quiet reload after sync` |
| AC15.1–AC15.2 | existing suites | all existing backend and frontend checks pass |

## Out of scope
- Email, push or phone notifications.
- Sharing or exporting the summary.
- Maps or GPS content.
- User-editable notes.
- LLM or external text services.
- Non-running activities in counts or comparisons.
- A dashboard card for the summary.
- Server-side read state, and syncing read state across browsers.
- Closing the panel by clicking outside it.

## Suggested follow-ups
- Make the thresholds (10%, 2%, 5 bpm, 15%) configurable if they turn out to be noisy.
- A "better" tone for a falling resting HR.
- Clear `sd.weeklySummary.seenRevision` on disconnect.

## Risks & decisions to confirm
- **"Header" placement:** the app has no global header (only `<main>` and a footer). The bell goes next to the dashboard page's `Running` heading, so it doesn't appear on `/connect`. Confirm.
- **Badge source (decided 2026-11-03):** `summaries[0]` lights the badge only when it is the current or last week (AC11.2, AC11.9).
- **Thresholds are reused, not invented where possible:**
  - 10% (005 ramp) for "watch load" on distance and load.
  - 5 bpm and −15% (015 daily flags) for weekly resting HR and HRV means. These are strict for weekly means and may rarely fire.
  - EF ±2% is new.
- **In-progress weeks compare partial totals with full weeks,** so a mid-week summary will often read "down X% vs your 4-week average". This is as the intent requires, labelled "In progress".
- **Strict time rule:** any run without a stored moving time removes the time note, or the comparison. Old no-HR runs from before 008 may never get one.
- **Revision includes run count,** so a 6th run re-lights the badge even if no note text changes. This matches "notes or underlying numbers differ".
- **localStorage use:** the hash only. Confirm that this satisfies the "no personal data in localStorage" rule.
- **Overlay test:** the "charts don't move" check is partly manual, because the test runner may not apply component styles.
