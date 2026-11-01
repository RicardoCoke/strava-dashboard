# Spec: Resting HR & HRV (from Garmin via Intervals.icu)

**Feature ID:** 015 · **Status:** Approved · **Intent:** [intent.md](./intent.md) · **Date:** 2026-10-31

## Summary
The backend fetches daily resting heart rate and overnight HRV (rMSSD) from the Intervals.icu wellness endpoint, using a personal API key from an environment variable. The fetch runs inside the existing background sync job (feature 009), and the values are cached in two new SQLite tables. A new endpoint, `GET /api/wellness`, returns chart-ready data for the last 12 weeks: daily values, 7-day baselines, flags for the latest values, and a facts-only readiness note that reads the existing training load result without changing it. A new Angular card, "Resting HR & HRV", sits directly under the training load card. It shows one dual-axis line chart, two "latest" tiles and the readiness note.

## Requirements
| ID | Requirement | Priority |
|---|---|---|
| R1 | An optional `INTERVALS_ICU_API_KEY` env var turns the feature on. When it is unset, no Intervals.icu call is made, `GET /api/wellness` answers `wellness_not_configured`, and the rest of the app behaves exactly as before | Must |
| R2 | One Intervals.icu client calls the wellness endpoint with basic auth, asks only for `id,restingHR,hrv`, and maps every outcome to a result value. The key and the values are never logged | Must |
| R3 | The wellness refresh runs inside the background sync job, at most once every 15 minutes. The first refresh backfills 91 days. Later refreshes re-fetch from 6 days before the last successful fetch up to today, then prune anything older | Must |
| R4 | Intervals.icu failures (key rejected, 429, unreachable, bad JSON, unexpected error) keep the cached data, never change the Strava sync status or Strava data, and are reported to the card | Must |
| R5 | A pure domain function builds 84 daily points (today and the 83 days before it), with nulls for missing days and a 7-day baseline per metric | Must |
| R6 | The latest resting HR is flagged when it is ≥ 5 bpm above its baseline. The latest HRV is flagged when it is ≥ 15 % below its baseline | Must |
| R7 | A facts-only readiness note is built from the flags, the flag streaks and a "load is high" fact taken from the existing training load result | Must |
| R8 | `GET /api/wellness` returns the exact JSON contract below and needs a connected session | Must |
| R9 | The "Resting HR & HRV" card sits under the training load card. It shows loading, not connected, error, empty and normal states, a chart with solid daily lines, dashed baselines and gaps, flagged latest tiles and the readiness note. It refreshes quietly after each finished sync | Must |
| R10 | The card follows the dark theme of feature 002 and reads well at desktop and mobile widths (down to 360 px) | Must |
| R11 | The training load endpoint and its numbers don't change | Must |

## Acceptance criteria

### R1 — Configuration and "not connected"
- **AC1.1** Given `INTERVALS_ICU_API_KEY` is unset, empty or whitespace, when `AppConfig.fromEnvironment` runs, then `intervalsApiKey` is `null` and startup succeeds. Given the key is set, the value is trimmed and stored.
- **AC1.2** Given any config, when `AppConfig.toString()` is called, then the output contains `intervalsApiKey=<set>` or `intervalsApiKey=<unset>` and never the key itself.
- **AC1.3** Given the key is unset and a connected session, when `GET /api/wellness` is called, then it returns 404 `{"error":"wellness_not_configured","message":"Intervals.icu is not connected."}`.
- **AC1.4** Given the key is unset, when a background sync job runs, then the fake Intervals engine gets zero requests and the job's Strava behaviour and `GET /api/sync` status are identical to a run without this feature.
- **AC1.5** Given the 404 above, when the card renders, then it shows "Intervals.icu not connected." and "Set INTERVALS_ICU_API_KEY in .env, then restart the backend." with no chart. All other dashboard cards still render.
- **AC1.6** Given `.env.example`, then it lists `INTERVALS_ICU_API_KEY=` with no value and the comment "Personal Intervals.icu API key (Settings > Developer). Used for resting HR and HRV. Restart the backend after changing it."

### R2 — Intervals.icu client
- **AC2.1** Given a key and the range 2026-08-01 to 2026-10-31, when the client fetches, then it sends exactly one `GET https://intervals.icu/api/v1/athlete/0/wellness?oldest=2026-08-01&newest=2026-10-31&fields=id,restingHR,hrv` with the header `Authorization: Basic base64("API_KEY:<key>")` and `Accept: application/json`.
- **AC2.2** Given a 200 response with the array fixture, then the result is `Success` with one entry per array element: the `id` parsed as a `LocalDate`, `restingHR` as an integer bpm or null, and `hrv` as a decimal ms or null. Unknown JSON keys are ignored. An element whose `id` isn't a valid ISO date is skipped.
- **AC2.3** Given a 401 or 403 response, then the result is `KeyRejected`.
- **AC2.4** Given a 429 response with `Retry-After: 120`, then the result is `RateLimited(until = now + 120 s)`. With no `Retry-After` header, or one that isn't a whole number from 1 to 86400, it is `RateLimited(until = now + 15 min)`.
- **AC2.5** Given another non-2xx status, a connection error, a timeout (connect 5 s, request 15 s) or a body that isn't the expected JSON, then the result is `Unavailable`.
- **AC2.6** Given any of AC2.1 to AC2.5, then the captured log output contains no key, no `Authorization` header, no URL query string and no resting HR or HRV value. Only the status code or the exception class name is logged. The Ktor client `Logging` plugin isn't installed.

### R3 — Refresh on the background sync schedule
- **AC3.1** Given a key and a background sync job that starts (feature 009 `startIfDue`), then after the Strava step finishes, with any outcome (`Done`, `ReauthRequired` or a caught exception), the job calls `WellnessSync.refreshIfDue()` before it clears the running flag. `GET /api/sync` shows `syncing` until both steps are done.
- **AC3.2** Given `lastAttemptAt` is null, the refresh is due. Given `lastAttemptAt` was 14 minutes ago, it isn't due and makes no request. At 15 minutes it is due. Given `retryAfter` is later than now, it isn't due, whatever `lastAttemptAt` says.
- **AC3.3** Given no successful fetch yet and today is 2026-10-31 (server zone), when the refresh runs, then it requests `oldest=2026-08-02&newest=2026-10-31` (today − 90 to today, 91 days; corrected during build from 2026-08-01, which is today − 91).
- **AC3.4** Given the last successful fetch was on 2026-10-30 (local date of `lastSuccessAt`) and today is 2026-10-31, then it requests `oldest=2026-10-24&newest=2026-10-31`. Given the last success was on 2026-09-01, it requests `oldest=2026-08-26`. The value of `oldest` is never earlier than today − 90.
- **AC3.5** Given a `Success`, then in one transaction the stored rows with dates in `[oldest, newest]` are replaced by the returned entries in that range. An entry is stored only if it has at least one usable value: a resting HR or HRV ≤ 0 is stored as null, and an entry with both values null isn't stored. Entries outside the range are ignored. Then rows dated before today − 90 are deleted. The state is saved as `lastStatus = OK`, `lastSuccessAt = now`, `lastAttemptAt = now`, `retryAfter = null`.
- **AC3.6** Given a stored day 2026-10-29 with HRV null, when a later refresh returns HRV 55.0 for 2026-10-29 (a late Garmin sync), then the stored row has HRV 55.0.
- **AC3.7** Given a refresh finished, then one info log line says `Wellness refresh finished: status=OK, days=<count>`, with counts only.
- **AC3.8** Given a refresh finishes and the page's `SyncStore` sees the job end (`dataVersion` goes up), then the card reloads `GET /api/wellness` quietly (R9) and shows the new days. No separate frontend timer exists for wellness.

### R4 — Failures
- **AC4.1** Given `KeyRejected`, then no stored rows change, the state saves `lastStatus = KEY_REJECTED` and `lastAttemptAt = now`, and the next attempt is due 15 minutes later.
- **AC4.2** Given `RateLimited(until)`, then no stored rows change and the state saves `lastStatus = RATE_LIMITED`, `retryAfter = until` and `lastAttemptAt = now`.
- **AC4.3** Given `Unavailable`, then no stored rows change and the state saves `lastStatus = UNAVAILABLE` and `lastAttemptAt = now`.
- **AC4.4** Given `refreshIfDue` throws an unexpected exception (not `CancellationException`), then it is caught inside `WellnessSync`, only the class name is logged, `UNAVAILABLE` is saved if possible, and the background job still clears its running flag.
- **AC4.5** Given any of AC4.1 to AC4.4, then `SyncState`, the stored runs, `BackgroundSync.reauthRequired` and the `GET /api/sync` response are unchanged by the wellness step.
- **AC4.6** Given any of AC4.1 to AC4.4 and cached rows, when `GET /api/wellness` is called, then it returns 200 with the cached days and `source.status` set to `keyRejected`, `rateLimited` or `unavailable`.

### R5 — Daily series and baselines
Terms: `today` = `LocalDate.now(clock.withZone(zone))`, using the same server zone as the other features. Window = `today − 83` to `today` (84 days). The baseline for day D averages the values that exist on days D − 7 to D − 1 (the previous 7 calendar days, D itself excluded). It needs at least 3 values, otherwise it is null.
- **AC5.1** Given stored rows, then `days` has exactly 84 entries in ascending date order, from `windowStart` to `windowEnd` with no date skipped.
- **AC5.2** Given a day with no stored row, or with a null field, then that day's `restingHrBpm` or `hrvMs` is `null`, never `0`.
- **AC5.3** Given resting HR values 50, 52, 51, 49, 50, 48, 50 on D − 7 to D − 1, then `restingHrBaselineBpm` for D is `50.0`. Given only 2 values in D − 7 to D − 1, it is `null`. Given 3 values 50, 51, 53 and 4 missing days, it is `51.3`.
- **AC5.4** Given the first window day (`today − 83`), then its baseline uses stored days `today − 90` to `today − 84`, so the dashed line starts at the left edge when data exists.
- **AC5.5** Given a day with no value but a baseline (≥ 3 values in the previous 7 days), then the baseline is still reported for that day.
- **AC5.6** Rounding: `restingHrBpm` is an integer (stored integer as-is). `hrvMs` is rounded half-up to an integer. Baselines are rounded half-up to one decimal. Flags and deltas use unrounded values.

### R6 — Flags on the latest values
For each metric, "latest" is the most recent day in the window with a value for that metric (it may be earlier than today).
- **AC6.1** Given the latest resting HR is 53 with an unrounded baseline of 48.0 (delta +5.0), then `latest.restingHr.flagged` is `true`. Given a delta of +4.9, it is `false`. Given a negative delta, it is `false`.
- **AC6.2** Given the latest HRV is 51 with a baseline of 60.0 (−15.0 %), then `latest.hrv.flagged` is `true`. At −14.9 % it is `false`. When HRV is above its baseline it is `false`.
- **AC6.3** Given the latest value's baseline is null, then `baselineBpm`/`baselineMs`, `deltaBpm`/`deltaPercent` and `deltaLabel` are `null` and `flagged` is `false`.
- **AC6.4** Given no value for a metric anywhere in the window, then `latest.restingHr` (or `latest.hrv`) is `null`.
- **AC6.5** Labels (exact): `deltaBpm` has one decimal, and the label is `"+5.6 bpm vs baseline"`, `"−1.2 bpm vs baseline"` (U+2212 minus) or `"0.0 bpm vs baseline"`. `deltaPercent` is `(value − baseline) / baseline × 100`, rounded half-up to an integer, and the label is `"−19% vs baseline"`, `"+4% vs baseline"` or `"0% vs baseline"`.
- **AC6.6** Given `flagged: true`, then the tile shows the value with the text "Above baseline" (resting HR) or "Below baseline" (HRV) and the warning colour. Given `flagged: false`, no flag text or warning colour appears.

### R7 — Readiness note
`flagStreak` for a metric = the number of consecutive calendar days, ending at that metric's latest day, on which the metric has a value, a baseline and the flag condition of R6. A missing day ends the streak. `loadHigh` = `null` when `TrainingLoadService.get()` isn't `Ready`. Otherwise it is `true` when the latest unrounded form ≤ −10.0, or `ramp.thisWeek.warning`, or `ramp.lastWeek.warning`, and `false` otherwise. A metric is "evaluable" when its latest entry has a non-null baseline.
- **AC7.1** Given HRV flagged with a streak of 3, resting HR evaluable and not flagged, and `loadHigh = true`, then `readiness.note` is `"HRV below baseline 3 days running while load is high"`.
- **AC7.2** Given HRV flagged with a streak of 1 and resting HR flagged with a streak of 2, and `loadHigh = false`, then the note is `"HRV below baseline and resting HR above baseline 2 days running"`. The HRV part always comes first. When the resting HR part comes second, its first letter is lowercase.
- **AC7.3** Given only resting HR flagged (streak 1), HRV not evaluable and `loadHigh = null`, then the note is `"Resting HR above baseline"`.
- **AC7.4** Given both metrics evaluable and none flagged, then the note is `"Resting HR and HRV within baseline"` (plus `" while load is high"` when `loadHigh = true`). Given only one metric evaluable and not flagged, it is `"Resting HR within baseline"` or `"HRV within baseline"`.
- **AC7.5** Given no metric evaluable, then `readiness.note` is `null` and the card shows no note.
- **AC7.6** Given any input, then the note contains none of the words "rest", "should", "recommend", "easy" or "skip" (checked over a table of inputs in the unit test). It states facts only.
- **AC7.7** `readiness.loadHigh` in the response equals the computed `loadHigh` (`true`, `false` or `null`).

### R8 — `GET /api/wellness`
- **AC8.1** Given a connected session, a key and stored rows, then it returns 200 with exactly the top-level keys `windowStart`, `windowEnd`, `baselineDays`, `daysWithData`, `days`, `latest`, `readiness`, `source`, with the types in the contract below.
- **AC8.2** Given no session, or a session that doesn't match the stored Strava tokens, then it returns 401 `not_connected`, before the key check.
- **AC8.3** Given a key, no stored rows and no fetch attempt yet, then it returns 200 with 84 days of nulls, `daysWithData: 0`, both `latest` entries `null`, `readiness.note: null` and `source.status: "pending"`.
- **AC8.4** The endpoint never calls Intervals.icu or Strava. It answers from SQLite and the training load computation only. The test checks that the fake engines get zero requests.
- **AC8.5** `source.lastFetchedAt` is the ISO-8601 UTC instant of `lastSuccessAt`, or `null`. `source.status` is `"ok"` when `lastStatus = OK`, `"pending"` when there was no attempt yet, and `"keyRejected"`, `"rateLimited"` or `"unavailable"` for the other statuses.

### R9 — The card
- **AC9.1** Given the dashboard page, then `<app-wellness-section />` is rendered directly after `<app-training-load-section />`, with the heading "Resting HR & HRV" (`h2`).
- **AC9.2** Loading: until the first response arrives, it shows "Loading resting HR & HRV…" (`role="status"`).
- **AC9.3** Error: given a non-401, non-404 error, or a 200 body that fails `isWellnessResponse`, it shows "Couldn't load resting HR & HRV." and a Retry button that reloads. A 401 shows nothing new (the interceptor navigates to `/connect`).
- **AC9.4** Empty: given `daysWithData === 0`, it shows no chart and one line based on `source.status`. `pending`: "Waiting for the first sync from Intervals.icu." `ok`: "No resting HR or HRV in the last 12 weeks." `keyRejected`: "Intervals.icu rejected the API key. Check INTERVALS_ICU_API_KEY, then restart the backend." `rateLimited` or `unavailable`: "Couldn't reach Intervals.icu. It will retry at the next sync."
- **AC9.5** Normal: given `daysWithData > 0`, it shows two latest tiles ("Resting HR" `<bpm> bpm`, "HRV" `<ms> ms`), each with its `deltaLabel` and the caption `on <Sat 31 Oct>` (`formatShortDate`). It shows the readiness note when it isn't null, then the chart, then the footnote "From Garmin via Intervals.icu · dashed line = average of the previous 7 days". A tile whose `latest` entry is null shows "No reading".
- **AC9.6** Normal with a failed last fetch: given `daysWithData > 0` and `source.status` not `ok`, the cached data shows, plus one muted line: "Showing saved data: Intervals.icu couldn't be reached at the last sync." For `keyRejected`, the line is "Showing saved data: Intervals.icu rejected the API key."
- **AC9.7** Chart: `buildWellnessOptions(data)` returns a line chart with a category x-axis of 84 labels (`formatDayMonth`), a left y-axis named "Resting HR (bpm)", a right y-axis named "HRV (ms)", and 4 series in this order: "Resting HR" (solid, left axis), "Resting HR 7-day baseline" (dashed, left axis, same colour), "HRV" (solid, right axis), "HRV 7-day baseline" (dashed, right axis, same colour). Every series has `connectNulls: false`, and null values stay `null` in `data` (never 0). Both y-axes have `scale: true`, so the lines aren't squashed against 0.
- **AC9.8** Tooltip: axis trigger. Its lines are built by a pure `wellnessTooltipLines(day)`: `"Sat 31 Oct"`, `"Resting HR 52 bpm (baseline 48.3)"`, `"HRV 61 ms (baseline 58.0)"`. A missing value reads `"Resting HR no data"`, and a missing baseline drops the brackets. Only numbers and dates are used, no user-written text.
- **AC9.9** Given the page's `SyncStore.dataVersion` goes up, then the card reloads without showing the loading state and keeps its current content if the reload fails (the same pattern as `TrainingLoadSection`).
- **AC9.10** The chart has `aria-label` "Resting heart rate and HRV per day with 7-day baselines, last 12 weeks". Flags use text as well as colour.

### R10 — Look and responsiveness
- **AC10.1** The card uses the shared `.card` styles and `--color-*` tokens of feature 002. The chart colours come from `palette.ts`: `restingHr` and `hrv` entries, with baselines in the same colour and `lineStyle.type: 'dashed'`. The flag uses `palette.worse` / `--color-worse`.
- **AC10.2** At 360 px viewport width, the two tiles stack vertically, the chart fills the card width with no horizontal scroll, and the legend wraps instead of overflowing. At ≥ 768 px, the tiles sit side by side.

### R11 — Training load unchanged
- **AC11.1** Given the same stored runs and LTHR, then the `GET /api/training-load` JSON is byte-identical whether or not `INTERVALS_ICU_API_KEY` is set and whether or not wellness rows exist. The existing training load tests pass unchanged.
- **AC11.2** No file under `domain/trainingload/` or `api/TrainingLoad*` changes. `WellnessService` only calls `TrainingLoadService.get()`.

## Traceability
| Success criterion (intent) | Covered by |
|---|---|
| S1 card with two lines, readable per day | R5, R8, R9 (AC9.1, AC9.5, AC9.7, AC9.8) |
| S2 dashed 7-day baseline | R5 (AC5.3 to AC5.5), R9 (AC9.7), R10 (AC10.1) |
| S3 gaps, never zero | R3 (AC3.5), R5 (AC5.2), R9 (AC9.7) |
| S4 flag when clearly off baseline, nothing otherwise | R6, R9 (AC9.5) |
| S5 facts-only readiness note next to load | R7, R9 (AC9.1, AC9.5) |
| S6 automatic on the sync schedule, incremental | R3, R4, R9 (AC9.9) |
| S7 not connected message, rest works | R1 |
| S8 training load identical | R11 |
| S9 dark look, desktop and mobile | R10 |

## Design

### Data from Strava
- This feature makes **no new Strava calls** and requests no new Strava scopes. The Strava sync (feature 009) is unchanged. Only its background job also runs the wellness step afterwards.
- 401 / 429 / network handling for Strava: unchanged (feature 009).

### Data from Intervals.icu
- Endpoint: `GET https://intervals.icu/api/v1/athlete/0/wellness?oldest=YYYY-MM-DD&newest=YYYY-MM-DD&fields=id,restingHR,hrv`. Athlete `0` means the key owner.
- Auth: HTTP basic, username `API_KEY`, password = `INTERVALS_ICU_API_KEY`. It is set per request with `basicAuth(...)`, not through a logging-capable plugin.
- Fields used: `id` (local date string), `restingHR` (bpm, integer), `hrv` (rMSSD, ms, decimal). Nothing else is requested or stored.
- Pagination: none. The endpoint returns the whole range as one array, and the range is at most 91 days.
- Sync and caching: runs in the 009 background job, at most every 15 minutes (`WELLNESS_INTERVAL = SyncPolicy.SYNC_INTERVAL`). The first refresh fetches 91 days. Later refreshes fetch from `max(today − 90, lastSuccessDate − 6)` to today, so late Garmin uploads in the last week are picked up and gaps after downtime are filled. No retry inside a job: the next schedule is the retry.
- Errors: 401/403 → `KEY_REJECTED` (cached data kept, retried every 15 minutes; a fixed key needs a restart anyway) · 429 → `RATE_LIMITED` until `Retry-After`, or 15 minutes · 5xx, network, timeout or bad JSON → `UNAVAILABLE`, retried after 15 minutes. "Expired token" doesn't apply: an API key doesn't expire, and a revoked key shows up as 401 → `KEY_REJECTED`.

### Domain model & logic
New package `domain/wellness/` (pure, no Ktor, Exposed or DTO imports):
- `@JvmInline value class HrvMs(val value: Double)`; reuse `Bpm` from `domain/model/Units.kt`.
- `data class WellnessDay(val date: LocalDate, val restingHr: Bpm?, val hrv: HrvMs?)`
- `enum class WellnessFetchStatus { OK, RATE_LIMITED, UNAVAILABLE, KEY_REJECTED }`
- `data class WellnessState(val lastSuccessAt: Instant?, val lastAttemptAt: Instant?, val lastStatus: WellnessFetchStatus?, val retryAfter: Instant?)`
- `sealed interface WellnessFetchResult { Success(days: List<WellnessDay>); KeyRejected; RateLimited(until: Instant); Unavailable }`
- Ports (`domain/ports/`): `WellnessSource.fetch(oldest: LocalDate, newest: LocalDate): WellnessFetchResult`; `WellnessRepository { findBetween(from, to); replaceRange(from, to, days); pruneBefore(date); getState(); saveState(state) }`.
- `WellnessPolicy` (pure): `const val WINDOW_DAYS = 84`, `BASELINE_DAYS = 7`, `MIN_BASELINE_VALUES = 3`, `FETCH_DAYS = 91`, `REFETCH_DAYS = 6`, `RHR_FLAG_BPM = 5.0`, `HRV_FLAG_FRACTION = 0.15`, `HIGH_LOAD_FORM = -10.0`, `RATE_LIMIT_FALLBACK = 15 min`; `isDue(state, now)`, `fetchRange(state, today, zone)`.
- `WellnessSeries.build(stored: List<WellnessDay>, today): WellnessView` (pure). It returns the 84 points with baselines, the latest per metric with delta, flag and streak, and `daysWithData`.
- `ReadinessNote.build(view, loadHigh: Boolean?): String?` (pure, exact strings from R7).
- `LoadLevel.isHigh(result: TrainingLoadResult): Boolean?` (pure, reads `Ready.load.days.last().form` and both `ramp.*.warning`).
- `WellnessSync(source, repo, clock, zone)`: `suspend fun refreshIfDue()` catches its own unexpected exceptions (R4).
- `WellnessService(configured: Boolean, repo, trainingLoad: TrainingLoadService, clock, zone)`: `suspend fun get(): WellnessResult` (`NotConfigured` | `Ready(view, note, loadHigh, state)`).
- Units: bpm (integer) and ms (rMSSD). There are no metres or pace. Day bucketing uses the Intervals.icu `id` date as-is (already the athlete's local date). `today` uses the server zone, as features 005 and 009 do.

### Persistence
`persistence/Tables.kt` (new objects) and `DatabaseFactory.connect` (add both to `SchemaUtils.create`; there are no column migrations because the tables are new):
- `wellness_days`: `date TEXT PRIMARY KEY` (ISO date), `resting_hr_bpm INTEGER NULL`, `hrv_ms REAL NULL`, `fetched_at INTEGER NOT NULL` (epoch seconds).
- `wellness_state`: `id INTEGER PRIMARY KEY` (always 1), `last_success_at INTEGER NULL`, `last_attempt_at INTEGER NULL`, `last_status TEXT NULL`, `retry_after INTEGER NULL`.
- `ExposedWellnessRepository` runs all calls in `newSuspendedTransaction(Dispatchers.IO)` (the existing `Database.query`). `replaceRange` deletes and inserts in one transaction.
- Retention: rows older than today − 90 are pruned after each successful refresh.

### Backend API
`GET /api/wellness`: no query params. It needs a connected session (`hasConnectedSession`).

Response 200 (JSON, exact; example for today = 2026-10-31):
```json
{
  "windowStart": "2026-08-09",
  "windowEnd": "2026-10-31",
  "baselineDays": 7,
  "daysWithData": 80,
  "days": [
    {
      "date": "2026-08-09",
      "restingHrBpm": 47,
      "restingHrBaselineBpm": 46.7,
      "hrvMs": 64,
      "hrvBaselineMs": 61.2
    },
    {
      "date": "2026-08-10",
      "restingHrBpm": null,
      "restingHrBaselineBpm": 46.9,
      "hrvMs": null,
      "hrvBaselineMs": 61.0
    }
  ],
  "latest": {
    "restingHr": {
      "date": "2026-10-31",
      "bpm": 53,
      "baselineBpm": 47.4,
      "deltaBpm": 5.6,
      "deltaLabel": "+5.6 bpm vs baseline",
      "flagged": true
    },
    "hrv": {
      "date": "2026-10-31",
      "ms": 49,
      "baselineMs": 60.2,
      "deltaPercent": -19,
      "deltaLabel": "−19% vs baseline",
      "flagged": true
    }
  },
  "readiness": {
    "note": "HRV below baseline and resting HR above baseline while load is high",
    "loadHigh": true
  },
  "source": {
    "status": "ok",
    "lastFetchedAt": "2026-10-31T06:15:00Z"
  }
}
```
Types:
- `windowStart`, `windowEnd`, `days[].date`, `latest.*.date`: ISO date string.
- `baselineDays`: int (always 7). `daysWithData`: int (days in the window with at least one value).
- `restingHrBpm`, `hrvMs`, `latest.restingHr.bpm`, `latest.hrv.ms`: int or null.
- `restingHrBaselineBpm`, `hrvBaselineMs`, `baselineBpm`, `baselineMs`: number with 1 decimal, or null.
- `deltaBpm`: number with 1 decimal, or null. `deltaPercent`: int or null. `deltaLabel`: string or null. `flagged`: boolean.
- `latest.restingHr`, `latest.hrv`: object or null.
- `readiness.note`: string or null. `readiness.loadHigh`: boolean or null.
- `source.status`: `"ok" | "pending" | "rateLimited" | "unavailable" | "keyRejected"`. `source.lastFetchedAt`: ISO-8601 UTC instant or null.

Errors (shape `{"error","message"}`):
- 401 `not_connected`: "Connect your Strava account." (existing).
- 404 `wellness_not_configured`: "Intervals.icu is not connected." (new `ErrorResponse.WELLNESS_NOT_CONFIGURED`).
- 500 `internal_error` via StatusPages (existing).

DTOs live in `api/WellnessDtos.kt` (`WellnessResponse`, `WellnessDayDto`, `WellnessLatestDto`, `RestingHrLatestDto`, `HrvLatestDto`, `ReadinessDto`, `WellnessSourceDto`). Mapping and labels live in `api/WellnessMappers.kt`, and the route in `api/WellnessRoutes.kt`.

### Frontend (Angular)
- Route / page: no new route. The card is added to the existing dashboard page (`running-dashboard-page.html`, directly after `<app-training-load-section />`, plus one import in `running-dashboard-page.ts`).
- Components (new folder `features/wellness/`):
  - `WellnessSection` (`wellness-section.ts/.html/.scss`): the container. It injects `WellnessService` and optionally `SyncStore` (quiet reload on `dataVersion`, the same as `TrainingLoadSection`) and holds `signal<WellnessState>`.
  - `WellnessLatest` (`wellness-latest/wellness-latest.ts`): `input.required<WellnessTile[]>()`, built by `latestTiles(data)` in `wellness.view.ts`. It renders the two tiles and their flags.
  - `WellnessChart` (`wellness-chart/wellness-chart.ts`): `input.required<WellnessResponse>()`. It renders `<app-chart>` with `buildWellnessOptions`.
  - `wellness.view.ts`: `WellnessState` union (`loading | notConfigured | error | loaded`), `wellnessStateFromError(error)`, `emptyText(source)`, `staleText(source)`.
  - `wellness.options.ts`: `buildWellnessOptions(data): EChartsOption`, `wellnessTooltipLines(day): string[]`, `WELLNESS_CHART_LABEL`.
- Service and TS interfaces: `core/api/wellness.models.ts` (mirrors the JSON above: `WellnessResponse`, `WellnessDay`, `RestingHrLatest`, `HrvLatest`, `WellnessReadiness`, `WellnessSource`, `WellnessSourceStatus`, plus `isWellnessResponse` light shape check: `days` is an array of length 84, `latest`, `readiness` and `source` are objects, `daysWithData` is a number). `core/api/wellness.service.ts`: `getWellness(): Observable<unknown>` → `GET /api/wellness`.
- States: loading · not connected (404) · error (with Retry) · empty (by `source.status`) · normal (with an optional stale line). Exact texts are in AC9.2 to AC9.6.
- Chart: dual-axis line chart. X: 84 days ("9 Aug" labels). Left Y: resting HR in bpm. Right Y: HRV in ms. 4 series: daily lines solid width 2 with no symbols, baselines dashed width 1.5 in the same colour. Legend at the top. The tooltip is built from `wellnessTooltipLines` joined with `<br/>`. The content is numbers and dates only, so there is no escaping concern.
- Palette: add `restingHr: '#a78bfa'` and `hrv: '#00f19f'` to `shared/chart/palette.ts`. These reuse existing values, which keeps the shared-file edit to two lines.
- Tests: add `testing/wellness-stub.ts` (`stubWellnessSection()`, the same pattern as `stubTrainingLoadSection`) and call it in the existing page specs that call `stubTrainingLoadSection()`, so their `HttpTestingController.verify()` doesn't see the new request.

### Files expected to change
Backend (new):
- `backend/src/main/kotlin/app/stravadashboard/intervals/IntervalsHttp.kt` (client factory: timeouts, JSON, no Logging)
- `backend/src/main/kotlin/app/stravadashboard/intervals/IntervalsClient.kt`
- `backend/src/main/kotlin/app/stravadashboard/intervals/IntervalsDtos.kt`
- `backend/src/main/kotlin/app/stravadashboard/intervals/IntervalsWellnessSource.kt` (DTO → domain mapping)
- `backend/src/main/kotlin/app/stravadashboard/domain/wellness/` (`WellnessModels.kt`, `WellnessPolicy.kt`, `WellnessSeries.kt`, `ReadinessNote.kt`, `LoadLevel.kt`, `WellnessSync.kt`, `WellnessService.kt`)
- `backend/src/main/kotlin/app/stravadashboard/domain/ports/WellnessSource.kt`, `WellnessRepository.kt`
- `backend/src/main/kotlin/app/stravadashboard/persistence/ExposedWellnessRepository.kt`
- `backend/src/main/kotlin/app/stravadashboard/api/WellnessDtos.kt`, `WellnessMappers.kt`, `WellnessRoutes.kt`
- Tests: `backend/src/test/kotlin/app/stravadashboard/intervals/IntervalsClientTest.kt`, `domain/wellness/WellnessPolicyTest.kt`, `WellnessSeriesTest.kt`, `ReadinessNoteTest.kt`, `LoadLevelTest.kt`, `WellnessSyncTest.kt`, `persistence/ExposedWellnessRepositoryTest.kt`, `api/WellnessRoutesTest.kt`; fixtures `backend/src/test/resources/intervals/wellness.json`, `wellness-malformed.json` (invented data)

Backend (edited, kept small):
- `backend/src/main/kotlin/app/stravadashboard/config/AppConfig.kt` (+ `intervalsApiKey`, `toString`)
- `backend/src/main/kotlin/app/stravadashboard/domain/BackgroundSync.kt` (+ optional `wellness: WellnessSync?` constructor param, called in `runJob` after the Strava step)
- `backend/src/main/kotlin/app/stravadashboard/persistence/Tables.kt` (+ 2 tables), `DatabaseFactory.kt` (+ 2 names in `SchemaUtils.create`)
- `backend/src/main/kotlin/app/stravadashboard/api/ErrorResponse.kt` (+ `WELLNESS_NOT_CONFIGURED`)
- `backend/src/main/kotlin/app/stravadashboard/Application.kt` (wiring, + `intervalsEngine: HttpClientEngine = CIO.create()` param, `wellnessRoutes(...)`)
- `backend/src/test/kotlin/app/stravadashboard/domain/BackgroundSyncTest.kt`, `AppConfigTest.kt` (new cases)
- `.env.example` (+ `INTERVALS_ICU_API_KEY=`)

Frontend (new):
- `frontend/src/app/core/api/wellness.models.ts`, `wellness.service.ts`, `wellness.service.spec.ts`
- `frontend/src/app/features/wellness/wellness-section.ts`, `.html`, `.scss`, `wellness-section.spec.ts`
- `frontend/src/app/features/wellness/wellness.view.ts`, `wellness.view.spec.ts`
- `frontend/src/app/features/wellness/wellness.options.ts`, `wellness.options.spec.ts`
- `frontend/src/app/features/wellness/wellness-chart/wellness-chart.ts`
- `frontend/src/app/features/wellness/wellness-latest/wellness-latest.ts`
- `frontend/src/app/testing/wellness-fixtures.ts`, `wellness-stub.ts`

Frontend (edited, kept small):
- `frontend/src/app/features/running-dashboard/running-dashboard-page.ts` (+1 import), `.html` (+1 line)
- `frontend/src/app/shared/chart/palette.ts` (+2 entries)
- `frontend/src/app/features/running-dashboard/running-dashboard-page.spec.ts`, `.redesign.spec.ts`, `.plan.spec.ts` (+ `stubWellnessSection()`)

## Edge cases
| Case | Expected behaviour |
|---|---|
| No wellness data at all (new Garmin link, or not yet synced to Intervals.icu) | 84 null days, `daysWithData: 0`. The card shows the empty text for `source.status` (AC9.4) |
| Day with resting HR but no HRV (watch not worn overnight) | `hrvMs: null` for that day, a gap in the HRV line, and resting HR shown normally |
| Value 0 or negative from the source | Stored as null, shown as a gap, never as 0 (AC3.5, AC5.2) |
| Fewer than 3 values in the previous 7 days | Baseline null for that day, a gap in the dashed line, and no flag for a latest value on that day |
| Latest reading is not today (today not synced yet) | Latest = most recent day with a value. Its date is shown in the tile caption. The flag and note are based on that day |
| Late Garmin sync fills yesterday | Picked up by the 7-day re-fetch on the next refresh (AC3.6) |
| Backend down for weeks | The next refresh fetches from `lastSuccessDate − 6`, capped at 91 days |
| Time zones / DST | Dates come from Intervals.icu `id` (local date) and are never converted. `today` uses the server zone. A source date later than `today` is outside the fetch range and ignored |
| Key not configured | 404 `wellness_not_configured`, a "not connected" card, no Intervals.icu calls (R1) |
| Key wrong or revoked | `keyRejected`. Cached data shown with the stale line, or the empty text if there's no cache |
| Intervals.icu 429 / down | Cached data kept. Retry after `Retry-After`, or 15 minutes. The Strava banner is unaffected |
| Training load not configured (no `LTHR_BPM`) | `loadHigh: null`. The note has no load suffix and the card otherwise works |
| Strava sync rate-limited or not due | No job starts, so wellness also waits for the next job (same schedule, by design) |
| Manual activities / activities without GPS or HR | Not relevant: this feature doesn't read activities, apart from the unchanged training load result |
| Unit conversion metres/pace | Not relevant: bpm and ms only |

## Privacy
- **Data fields & sensitivity:** daily resting HR (bpm) and overnight HRV rMSSD (ms) are health data (GDPR Art. 9). The date is not sensitive on its own. The Intervals.icu API key is a credential.
- **Storage & retention:** the SQLite tables `wellness_days` (date + 2 values) and `wellness_state` (timestamps and status only). Rows older than today − 90 are pruned after each successful refresh. The key lives only in the `INTERVALS_ICU_API_KEY` env var, in memory in `AppConfig`, and is never written to the DB.
- **Scopes:** no new Strava scopes. The Intervals.icu key has the owner's full API access (Intervals.icu has no scoped keys). It is used for this one read-only GET.
- **Applicable privacy-check items:**
  - 1 ✅ The key comes only from env, `.env.example` has no value, and fixtures are invented.
  - 2 ✅ (applied to the Intervals.icu key) It is never in API responses, cookies, browser storage, URLs or HTML. It is only sent in the `Authorization` header to `intervals.icu`.
  - 3 n/a There's no OAuth for Intervals.icu. A rejected key is handled as `keyRejected`.
  - 4 n/a No new OAuth flow.
  - 5 ✅ No new Strava scopes.
  - 6 ✅ Only `id,restingHR,hrv` is requested (the `fields` param), and only the date + 2 values are stored. The raw response isn't kept.
  - 7 n/a No GPS.
  - 8 ✅ Logs carry counts, statuses and exception class names only. No key, header, query string or HR/HRV values (AC2.6, AC3.7).
  - 9 ✅ The Ktor `Logging` plugin isn't installed on the Intervals.icu client.
  - 10 ✅ Errors use the fixed `ErrorResponse` texts. Intervals.icu bodies are never passed through.
  - 11 ✅ `GET /api/wellness` checks the session (AC8.2). The app binds to 127.0.0.1 by default.
  - 12 ✅ Unchanged session cookie.
  - 13 ✅ The card shows numbers, dates and fixed backend strings only. No `[innerHTML]`. The tooltip formatter builds lines from numbers and dates only.
  - 14 n/a No new state-changing endpoint (the refresh rides on the existing CSRF-protected `POST /api/sync`).
  - 15 ✅ The response holds only chart-ready fields. No raw Intervals.icu objects.
  - 16 ✅ Intervals.icu is called only by the backend, which the user set up as their own data source. Nothing is added to the frontend bundle from a CDN.
  - 17 ✅ Strava data isn't sent to Intervals.icu. Only the training load result is read, locally.
  - 18 ❌ partial: there's no in-app "delete wellness data". The user can unset the key and delete the DB file, or wait for the 91-day pruning. See Suggested follow-ups.
  - 19 n/a
  - 20 ✅ DB backups now also hold health data from Intervals.icu. This is already treated as personal data.
  - 21 ✅ The fixtures `intervals/*.json` and `wellness-fixtures.ts` use invented values and dates.

## Test plan
| AC | Test type | Test name |
|---|---|---|
| AC1.1, AC1.2 | backend unit | `R1 - intervals key is optional, trimmed and redacted` |
| AC1.3 | backend route | `R1 - wellness returns 404 when key not configured` |
| AC1.4 | backend unit (BackgroundSync) | `R1 - job makes no Intervals calls without a key` |
| AC1.5 | frontend component | `R1 - shows not connected message on 404` |
| AC1.6 | review | `.env.example` diff |
| AC2.1, AC2.2 | backend client (MockEngine) | `R2 - requests wellness with basic auth and three fields`, `R2 - maps wellness array and skips bad dates` |
| AC2.3 | backend client | `R2 - 401 and 403 map to key rejected` |
| AC2.4 | backend client | `R2 - 429 uses Retry-After or 15 minutes` |
| AC2.5 | backend client | `R2 - 5xx, network error and bad JSON map to unavailable` |
| AC2.6 | backend client (log capture) | `R2 - logs never contain key, query or values` |
| AC3.1 | backend unit (BackgroundSync) | `R3 - wellness refresh runs after Strava step with any outcome` |
| AC3.2 | backend unit | `R3 - refresh due after 15 minutes and not before retryAfter` |
| AC3.3, AC3.4 | backend unit | `R3 - first fetch backfills 91 days`, `R3 - later fetch starts 6 days before last success` |
| AC3.5, AC3.6 | backend unit + persistence (in-memory SQLite) | `R3 - success replaces range, nulls non-positive values and prunes`, `R3 - late HRV for a past day is picked up` |
| AC3.7 | backend unit (log capture) | `R3 - finish log has status and count only` |
| AC3.8, AC9.9 | frontend component | `R9 - reloads quietly when dataVersion changes` |
| AC4.1 to AC4.3 | backend unit | `R4 - key rejected, rate limited and unavailable keep cached rows` |
| AC4.4 | backend unit | `R4 - unexpected exception is contained and logged by class name` |
| AC4.5 | backend unit (BackgroundSync) | `R4 - wellness failure leaves Strava sync state untouched` |
| AC4.6 | backend route | `R4 - cached days returned with failed source status` |
| AC5.1, AC5.2 | backend unit | `R5 - 84 ascending days with nulls for missing` |
| AC5.3, AC5.5 | backend unit | `R5 - baseline averages previous 7 days with at least 3 values` |
| AC5.4 | backend unit | `R5 - first window day uses pre-window days for baseline` |
| AC5.6 | backend unit | `R5 - rounding of values and baselines` |
| AC6.1 | backend unit | `R6 - resting HR flagged at 5 bpm above baseline` |
| AC6.2 | backend unit | `R6 - HRV flagged at 15 percent below baseline` |
| AC6.3, AC6.4 | backend unit | `R6 - no baseline or no value means no flag` |
| AC6.5 | backend unit (mapper) | `R6 - delta labels with sign and unit` |
| AC6.6 | frontend component | `R6 - flagged tile shows text and warning colour` |
| AC7.1 to AC7.5 | backend unit | `R7 - readiness note exact text` (table-driven) |
| AC7.6 | backend unit | `R7 - readiness note contains no advice words` |
| AC7.7 | backend unit | `R7 - load high from form, this-week and last-week ramp warning` |
| AC8.1, AC8.5 | backend route | `R8 - wellness response shape` |
| AC8.2 | backend route | `R8 - wellness requires connected session` |
| AC8.3 | backend route | `R8 - pending source with no data` |
| AC8.4 | backend route | `R8 - wellness endpoint makes no external calls` |
| AC9.1 | frontend component (page) | `R9 - wellness card sits after training load` |
| AC9.2, AC9.3 | frontend component | `R9 - shows loading state`, `R9 - shows error with retry` |
| AC9.4 | frontend component + view unit | `R9 - empty text per source status` |
| AC9.5, AC9.6 | frontend component | `R9 - shows tiles, note, chart and footnote`, `R9 - shows stale line when last fetch failed` |
| AC9.7, AC9.10 | frontend option builder unit | `R9 - four series, dual axes, dashed baselines, nulls kept` |
| AC9.8 | frontend option builder unit | `R9 - tooltip lines for a day` |
| (service) | frontend service | `R8 - getWellness calls GET /api/wellness` |
| AC10.1 | frontend option builder unit | `R10 - colours from palette` |
| AC10.2 | manual check at 360 px and 1280 px (devtools) + review of SCSS | `R10 - responsive layout` |
| AC11.1 | backend route | `R11 - training load response identical with wellness configured` |
| AC11.2 | review | diff has no changes under `domain/trainingload/` |

## Out of scope
- Sleep, stress, Body Battery, steps or any other wellness field.
- Garmin login, Garmin passwords, the Garmin API or unofficial Garmin libraries.
- Manual file import or export.
- Writing data back to Intervals.icu.
- Any change to TRIMP, fitness, fatigue or form. HRV and resting HR are not load inputs.
- Advice or coaching text.
- A separate wellness schedule, timer or status banner independent of the Strava sync.
- Configurable thresholds (they are fixed constants: 5 bpm and 15 %).

## Suggested follow-ups
- An in-app "disconnect and delete" action covering Strava data and the wellness cache (privacy item 18). The app doesn't have this for Strava either today.
- Clearing `wellness_days` at startup when `INTERVALS_ICU_API_KEY` is unset (data minimisation).
- Running the wellness refresh even when the Strava sync is rate-limited for a long time (its own due check in `startIfDue`).
- Showing the flagged latest point as a marker on the chart, as well as on the tile.

## Risks & decisions to confirm
- **Baseline definition:** the 7-day baseline averages the *previous* 7 days (excluding the day itself) and needs at least 3 values. Excluding the day keeps a spike from diluting its own baseline. The minimum of 3 avoids a baseline from one reading. Please confirm both.
- **"Load is high":** latest form ≤ −10, or a ramp warning this week or last week (feature 005). Using "form < 0" would be true on most training days of a build, so the note would almost always say "while load is high". Please confirm the −10 cut-off.
- **Streak and "latest":** the latest reading may be from yesterday or earlier if today hasn't synced. The tile shows its date. A missing day breaks a "days running" streak.
- **Readiness note when nothing is flagged:** it shows a neutral fact ("Resting HR and HRV within baseline"), rather than nothing. S4's "nothing flagged" applies to the flags, not the note.
- **Schedule coupling:** wellness only refreshes when a 009 background job runs (Strava sync due). While Strava is rate-limited or the dashboard isn't open, wellness waits too. This matches "same schedule" in the intent.
- **Key rejected:** retried every 15 minutes rather than stopped until restart. It's simpler, and a few 401s per hour are harmless.
- **Intervals.icu response types:** `restingHR` is assumed to be an integer and `hrv` a decimal, from the OpenAPI spec. The client decodes both leniently: a decimal resting HR is rounded half-up.
- **Single chart with dual y-axes:** the alternative is two stacked mini-charts. Dual axes keep the shared `ChartComponent` (300 px) unchanged.
- **Shared-file edits** (to coordinate with 011 to 014): `Application.kt`, `BackgroundSync.kt`, `AppConfig.kt`, `Tables.kt`, `DatabaseFactory.kt`, `ErrorResponse.kt`, `palette.ts`, `running-dashboard-page.ts/.html`, three page specs, `.env.example`. Each edit only adds lines.
