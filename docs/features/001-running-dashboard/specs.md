# Spec: Running dashboard (weekly km, HR fitness trend, workout speed comparison)

**Feature ID:** 001 · **Status:** Approved · **Intent:** [intent.md](./intent.md) · **Date:** 2026-10-30

## Summary
Replace the Angular placeholder with a running dashboard that has three charts: weekly running km for the last 9 Monday-Sunday weeks, average heart rate per run compared within each run type over the last 5 months, and average work-rep pace per workout type for the last 30 days against the 30 days before that. The backend handles Strava OAuth (`activity:read_all`) and stores Run activities in a local SQLite database. The 5-month history is imported once; after that, each sync only pulls runs newer than the newest stored run, plus a 7-day re-check. The backend exposes one chart-ready endpoint, `GET /api/dashboard/running`. The repo has no OAuth, Strava client or persistence yet (only `/api/health`), so this feature also builds the minimum of that infrastructure needed to show the data.

## Requirements
| ID | Requirement | Priority (Must/Should/Could) |
|---|---|---|
| R1 | Opening the app at `/` shows the running dashboard instead of the Angular placeholder page | Must |
| R2 | Connect with Strava via a backend OAuth flow (`activity:read_all`, `state` check, HttpOnly session cookie); every `/api` route needs a valid session | Must |
| R3 | Strava tokens are refreshed before expiry and once on a 401; revoked tokens are deleted and the user is asked to reconnect | Must |
| R4 | Run activities are stored in a local SQLite DB: a one-time 5-month import, then incremental syncs that only pull runs after the newest stored run (plus a 7-day re-check), a detail call per run, a speed-stream call per outdoor workout, and pruning | Must |
| R5 | Strava rate limits (429) and outages stop the sync cleanly; the dashboard is served from the DB and shows the sync status | Must |
| R6 | Only activities with `sport_type == "Run"` count (outdoor, treadmill, private and manual runs); walks, rides, hikes, TrailRun, VirtualRun and other types do not | Must |
| R7 | Each run gets a type: a workout `NxMmin` (tolerant of spacing, case and spelling) taken from the name, or the description if the name has none; every other run is `easy` | Must |
| R8 | Weekly running km for the last 9 Monday-Sunday weeks, bucketed by `start_date_local` | Must |
| R9 | For each run type, the whole-run average HR of every run in the last 5 months, plus a trend line | Must |
| R10 | Average work-rep pace per workout run. Treadmill (`trainer == true`): from the description pace line. Outdoor: the N fastest non-overlapping M-minute windows in the speed stream, or fast km laps if streams aren't available | Must |
| R11 | For each workout type, average rep pace for the last 30 days next to the 30 days before | Must |
| R12 | A run type without enough data for a comparison is shown with a clear "not enough data" message, not as an empty or broken chart | Must |
| R13 | The dashboard page has loading, error, empty and normal states, and a sync-status banner | Must |

## Acceptance criteria

Examples use a fixed clock: **today = Thursday 2026-10-29** in the server time zone.

### R1 — Dashboard replaces the placeholder
- **AC1.1** Given a valid session, when the app is opened at `/`, then the running dashboard page (heading "Running") is rendered, and none of the Angular placeholder content ("Hello, frontend", the Angular docs/links block) is in the DOM.
- **AC1.2** Given any route, when the app shell renders, then a footer with the text "Powered by Strava" is visible.
- **AC1.3** Given an unknown path (e.g. `/foo`), when it is opened, then the router redirects to `/`.

### R2 — Connect with Strava and session guard
- **AC2.1** Given a request with no session cookie, or with a session whose athlete has no stored tokens, when `GET /api/dashboard/running` is called, then the response is `401` with body `{"error":"not_connected","message":"Connect your Strava account."}`.
- **AC2.2** Given the frontend gets a `401` from any `/api` call, when the error interceptor handles it, then the app navigates to `/connect`, which shows a "Connect with Strava" link with `href="/auth/strava/login"` (a full-page link, not an `HttpClient` call).
- **AC2.3** When `GET /auth/strava/login` is called, then the response is `302` to `https://www.strava.com/oauth/authorize` with `client_id`, `redirect_uri` = `STRAVA_REDIRECT_URI`, `response_type=code`, `approval_prompt=auto`, `scope=activity:read_all` and a random `state` (at least 32 bytes, base64url). It also sets a signed `sd_oauth_state` cookie (HttpOnly, SameSite=Lax, Max-Age 600) that holds the same state.
- **AC2.4** Given a callback `GET /auth/strava/callback` whose `state` is missing or doesn't match the `sd_oauth_state` cookie, when it is handled, then no token exchange happens and the response is `302` to `/connect?error=state_mismatch`.
- **AC2.5** Given a callback with `error=access_denied`, then the response is `302` to `/connect?error=access_denied`.
- **AC2.6** Given a callback with a valid state whose `scope` query param doesn't contain `activity:read_all`, then no tokens are stored and the response is `302` to `/connect?error=scope_missing`.
- **AC2.7** Given a valid callback and a successful token exchange, then the access token, refresh token, `expires_at`, granted scope and athlete id are stored in `strava_tokens`. The `sd_session` cookie (signed with `SESSION_SECRET`, HttpOnly, SameSite=Lax, `Secure` when `STRAVA_REDIRECT_URI` is https) is set, `sd_oauth_state` is cleared, and the response is `302` to `/`. No token value appears in the response headers, cookies, body or redirect URL.
- **AC2.8** Given a valid callback whose token exchange fails (non-2xx or network error), then nothing is stored and the response is `302` to `/connect?error=token_exchange_failed`.
- **AC2.9** Given a successful callback for an athlete id different from the one the local store belongs to (`sync_state.owner_athlete_id`, or the stored tokens if that is not set yet), then all stored runs, laps, stream paces and sync state are deleted before the new tokens are stored. This also applies when the previous athlete's tokens were already deleted by a revoke (review finding).
- **AC2.10** Given `/connect?error=<code>`, when the page renders, then it shows a fixed message for each known code (`access_denied`, `scope_missing`, `state_mismatch`, `token_exchange_failed`) or a generic "Connecting to Strava failed. Please try again." for anything else. The raw query value is never rendered.
- **AC2.11** Given a successful dashboard response, when its JSON is inspected, then it has no token, athlete id, activity name, description, coordinate or stream field (contract test against the exact shape below).

### R3 — Token lifecycle
- **AC3.1** Given a stored access token whose `expires_at` is less than 5 minutes from now, when a sync is about to call the Strava API, then it first calls `POST /oauth/token` with `grant_type=refresh_token` and stores the returned access token, refresh token and `expires_at`. The refresh token is replaced too, because Strava may rotate it.
- **AC3.2** Given the Strava API returns `401` to an API call, when the client handles it, then it refreshes the token once and retries the call once. If the retry succeeds, the sync continues.
- **AC3.3** Given the refresh call returns `400`/`401`, or the retried call is still `401`, then the stored tokens are deleted, `GET /api/dashboard/running` returns `401` `{"error":"strava_reauth_required","message":"Your Strava connection expired. Please connect again."}`, and the frontend navigates to `/connect?reason=reauth`, which shows that message.
- **AC3.4** Given a full sync with token refresh and a stream call runs (MockEngine), when the captured log output is inspected, then it contains none of the access token, refresh token, OAuth code, `Authorization` header, activity names, descriptions or stream values.

### R4 — Local database and sync
- **AC4.1 (one-time import)** Given an empty DB (no list pass has ever finished), when the dashboard is requested, then the backend calls `GET /athlete/activities` with `after` = the epoch second at 00:00 (server zone) of today − 5 months − 1 day (2026-05-28), `per_page=200`, and `page=1,2,…`. It stops after the first page that returns fewer than 200 items. Only when every page has been read is the list pass recorded as complete (`sync_state.list_cursor` set). If the import is interrupted (429 or outage), the next sync restarts the list from the same backfill `after`; runs already stored are upserted, not duplicated.
- **AC4.2 (incremental)** Given a completed list pass and stored runs whose newest `start_date` is `N` (e.g. 2026-10-27T06:00Z), when a sync is due, then the list call uses `after = min(N, now − 7 days)`. Runs before that point are never listed or downloaded again. Example: with now = 2026-10-29T10:00Z, `after` = 2026-10-22T10:00Z. If the newest stored run is older than 7 days (e.g. 2026-10-01), then `after` = that run's start. Given a completed list pass with no stored runs, then `after` = the backfill start from AC4.1.
- **AC4.3 (throttle)** Given the last successful sync was under 15 minutes ago and nothing is pending (no run waiting for a detail or stream call), when the dashboard is requested, then no Strava call is made and the response is built from the DB.
- **AC4.4 (persistence across restarts)** Given a completed import, when the backend is restarted and the dashboard is requested, then the stored runs are used and the next list call follows AC4.2, not AC4.1.
- **AC4.5 (details)** Given listed runs with no detail fetched yet, when the sync runs, then `GET /activities/{id}?include_all_efforts=false` is called one at a time, newest `start_date` first. For each run, `description` and laps (`lap_index`, `elapsed_time`, `distance`) are stored and `detail_fetched_at` is set.
- **AC4.6 (streams)** Given an outdoor workout run (`trainer == false`, type `NxMmin`, detail fetched) with no stored stream result for its current type key, when the sync runs, then `GET /activities/{id}/streams?keys=time,velocity_smooth&key_by_type=true` is called once. The derived rep pace (R10) is computed in memory, and only `run_id`, `type_key`, `rep_pace_s_per_km` (nullable), `status` and `computed_at` are stored in `run_stream_paces`. The raw `time`/`velocity_smooth` arrays are not persisted or logged. `latlng`, `heartrate`, `altitude` and every other stream key are never requested.
- **AC4.7 (streams not available)** Given the streams call returns `404`, or a body without both `time` and `velocity_smooth` arrays of equal length ≥ 2, then `run_stream_paces.status = "unavailable"` is stored, and the run is not asked for again unless its type key changes.
- **AC4.8 (no stream calls for others)** Given treadmill runs (`trainer == true`) and easy runs, then no streams call is ever made for them.
- **AC4.9 (call cap and order)** Within one sync, detail calls and stream calls together are capped at `MAX_ACTIVITY_CALLS_PER_SYNC = 80` (list and token calls not counted). The order is: (1) missing details, newest first; (2) missing stream results for outdoor workouts, newest first; (3) 7-day detail re-checks (AC4.10).
- **AC4.10 (recent edits)** Given a stored run that started within the last 7 days and whose detail was fetched more than 1 hour ago, when the sync runs, then its detail is fetched again and the stored description and laps are replaced. If the re-check changes the run's type key, then its stored stream result no longer matches, and it gets a stream call in a later step or sync.
- **AC4.11 (non-runs)** Given the listed activities include non-Run types (Walk, Ride, Hike, TrailRun, VirtualRun, …), then those are not stored. Given a stored run comes back in the list with a `sport_type` other than `Run`, then it is deleted along with its laps and stream pace.
- **AC4.12 (pruning)** Given stored runs whose `start_date_local` date is earlier than today − 6 months, when a sync finishes, then those runs and their laps and stream paces are deleted. Pruning never changes `list_cursor`, so no re-import is triggered.
- **AC4.13 (concurrency)** Given two dashboard requests arrive at the same time while a sync is due, then only one sync runs (a `Mutex`), and both responses are built after it finishes.
- **AC4.14 (partial)** Given detail or stream calls still pending after the cap, then the response has `sync.status = "partial"` and `sync.runsPendingDetail` = the number of runs still waiting for a detail or stream call. The next request triggers a sync even if under 15 minutes have passed.

### R5 — Rate limits and Strava failures
- **AC5.1** Given Strava returns `429` to any list, detail, stream or token call, then the sync stops at once with no further Strava calls. Data already fetched in that sync stays stored, and the response is `200` with `sync.status = "rateLimited"` and `sync.retryAfter` set. `retryAfter` is the next quarter-hour boundary in UTC (:00/:15/:30/:45). If the daily usage in the rate-limit headers is at or above the daily limit, it is the next 00:00 UTC instead.
- **AC5.2** Given `rateLimitedUntil` is in the future, when the dashboard is requested, then no Strava call is made and the response is served from the DB with `sync.status = "rateLimited"` and the same `retryAfter`.
- **AC5.3** Given a network error, a timeout (connect 5 s, request 15 s) or a Strava `5xx`, then the sync stops, the response is `200` from the DB with `sync.status = "stravaUnavailable"`, and `sync.lastSyncedAt` is unchanged. A `5xx` on a streams call counts as an outage, not as "unavailable" streams.
- **AC5.4** Given `sync.status` is `rateLimited`, `stravaUnavailable` or `partial`, when the dashboard renders, then a banner above the charts shows, in order: "Strava rate limit reached. Showing saved data; it will refresh after HH:mm." (local time of `retryAfter`), "Couldn't reach Strava. Showing data saved at <lastSyncedAt local date-time>." ("No data saved yet." if `lastSyncedAt` is null), or "Still importing runs from Strava (N remaining). Reload in a while to see the rest." Given `status = "ok"`, no banner is shown.

### R6 — Run filter
- **AC6.1** Given a domain input with an outdoor Run, a treadmill Run (`trainer = true`), a private Run, a Walk, a Ride, a Hike, a TrailRun and a VirtualRun, when the weekly, HR and pace aggregations run, then only the three `Run` activities count.
- **AC6.2** Given a manual Run with distance, no laps, no streams and no heart rate, then its distance counts in weekly km and it is left out of the HR points (counted in `runsWithoutHeartRate`). If it is a workout, it gets no rep pace and is counted in `runsWithoutPace`.

### R7 — Run-type classification
- **AC7.1** Given run names "5x6min", "5 x 6 min", "5X6MIN", "5x6 mins", "5×6 minutes", "5x6'" and "Tuesday 5x6min session", when classified, then each gets `typeKey = "5x6min"` and `label = "5x6min"`.
- **AC7.2** Given a run named "Morning Run" whose description contains "5x6min", then it is `5x6min`. Given a run named "4x8min" whose description contains "5x6min", then it is `4x8min` (the name wins).
- **AC7.3** Given the names "Easy 10k", "Long run", "6x400m", "8x1k" or an empty name, with no `NxMmin` in the description, then each is `typeKey = "easy"`, `label = "Easy runs"` (confirmed: everything that doesn't match the pattern is an easy run).
- **AC7.4** Given the name "2x10min + 4x1min", then the type is `2x10min` (the first match).
- **AC7.5** Given a run whose detail isn't fetched yet, then it is classified from its name alone.
- **AC7.6** Given the name "0x6min", "60x1min" or "5x0min" (N outside 1..50 or M outside 1..120), then it is `easy`.

### R8 — Weekly km
- **AC8.1** Given today 2026-10-29, when weekly distance is computed, then it returns 9 weeks, oldest first, `weekStart` 2026-08-31 to 2026-10-26, each `weekEnd` = `weekStart` + 6 days. Only the last week has `isCurrentWeek = true`.
- **AC8.2** Given a run with `start_date_local` 2026-10-25T23:30:00 (Sunday local) and `start_date` 2026-10-26T03:30:00Z (Monday UTC), then it is counted in the week starting 2026-10-19.
- **AC8.3** Given runs of 5000 m and 10049 m in one week, then that week has `distanceKm = 15.0` (sum in metres, ÷ 1000, rounded half-up to 1 decimal) and `runCount = 2`.
- **AC8.4** Given a week with no runs, then it is still returned with `distanceKm = 0.0` and `runCount = 0`.
- **AC8.5** Given weekly data, when the chart renders, then it is a bar chart with one bar per week. X-axis labels are the `label` values ("31 Aug"), the y-axis is named "km", and the tooltip reads "31 Aug – 6 Sep: 32.4 km · 4 runs". The current-week bar uses the palette's "current" colour and its tooltip ends with "(this week)".
- **AC8.6** Given all 9 weeks have `runCount = 0`, then the weekly card shows "No runs in the last 9 weeks" and no chart.

### R9 — Heart-rate trend per run type
- **AC9.1** Given 5x6min runs in the window with `average_heartrate`, then the `5x6min` entry's `points` are sorted by date ascending, one per run, with `averageHeartRateBpm` rounded to 1 decimal and `distanceKm` rounded to 1 decimal.
- **AC9.2** Given today 2026-10-29, then `windowStart = 2026-05-29` and `windowEnd = 2026-10-29`. A run on 2026-05-28 (local date) is left out and a run on 2026-05-29 is included.
- **AC9.3** Given runs of a type without heart rate (`average_heartrate` missing), then they are not in `points` and are counted in `runsWithoutHeartRate`. `runCount` counts all runs of the type in the window.
- **AC9.4** Given a treadmill and an outdoor run of the same type, then both are in the same `points` list, with `treadmill = true` and `false` respectively. *Feature 003 adds `points[].rising` (see 003 specs.md R3).*
- **AC9.5** Given a type with 3 or more points, then `trend` is the least-squares line of bpm against day number: `startDate`/`endDate` are the first and last point dates, `startBpm`/`endBpm` are the line's values on those dates, and `changeBpm = endBpm − startBpm`, all rounded to 1 decimal. Example: points (2026-06-01, 150), (2026-07-01, 146), (2026-08-01, 142) give startBpm 150.0, endBpm 142.0, changeBpm −8.0.
- **AC9.6** Given easy runs and two workout types, then `types` lists `easy` first (if it has at least one run in the window), then workout types ordered by most recent run, newest first. A type appears only if it has at least one run in the window.
  - *Note (006):* runs over 20 km that are not workouts now form a separate "Long runs" type (`kind = "long"`), always listed second after `easy`. See [006 specs](../006-long-run-hr/specs.md).
- **AC9.7** Given HR data, when the card renders, then a selector lists every type as "<label> (<runCount> runs)", and the first type is selected by default. Picking another type swaps the chart to that type's points. The chart's x-axis is a time axis and its y-axis is "Avg HR (bpm)" (scaled to the data, not from 0). Each run is a point, joined by a line, and the trend is a dashed line. The tooltip reads "12 Oct 2026 · 142.3 bpm · 8.2 km" with " · Treadmill" added for treadmill runs. Under the chart, a line reads "Trend: −8.0 bpm over the period (lower = fitter)".

### R10 — Rep pace per workout run
Treadmill runs (`trainer == true`):
- **AC10.1** Given a `trainer = true` 5x6min run whose description has the line "4:15 4:10 4:05 4:10 4:00", then its rep pace is 248 s/km (the arithmetic mean of 255, 250, 245, 250 and 240). Its laps and streams are never used.
- **AC10.2** A *pace line* is the first line of the description made up only of whitespace-separated `m:ss` tokens (regex `^\s*\d{1,2}:[0-5]\d(\s+\d{1,2}:[0-5]\d)*\s*$`), with every value between 2:00 and 12:00 /km. Given a description "Felt strong at 6:30\n4:15 4:10", then the pace line is "4:15 4:10". Given "Rep 1 4:15, rep 2 4:10", then there is no pace line. All values on the line are averaged, even if their count differs from N.
- **AC10.3** Given a `trainer = true` workout with no pace line, then it has no rep pace (Garmin pace is not used) and counts in `runsWithoutPace`.

Outdoor runs (`trainer == false`):
- **AC10.4** Given a `trainer = false` workout whose description has a valid pace line, then the pace line is ignored and the rep pace comes from streams or laps (AC10.5–AC10.10).
- **AC10.5 (stream windows)** Given an outdoor 5x6min run with a 1-second speed stream: 10 min at 3.0 m/s, then 5 × (6 min at 4.0 m/s + 2 min at 2.5 m/s), then 10 min at 3.0 m/s. When the stream rep pace is computed, then the 5 chosen windows are the 5 work reps and the rep pace is 250 s/km.
- **AC10.6 (stream algorithm)** The algorithm works like this:
  - Build cumulative distance from the stream by trapezoid integration: `D[k] = D[k−1] + (v[k−1] + v[k]) / 2 × (t[k] − t[k−1])`.
  - With `W = M × 60` s, each sample time `t[i]` where `t[i] + W ≤ t[last]` is a candidate window start. The window's distance is `D(t[i] + W) − D[i]`, with `D` interpolated linearly between samples.
  - Pick windows greedily by distance, largest first, skipping any that overlap one already picked, until N are picked.
  - Rep pace = `(N × W) ÷ (Σ window distance ÷ 1000)`, rounded half-up to whole s/km.
  - Given a 2x6min stream with two fast 6-min blocks separated by 1 min of jogging, then the two windows chosen don't overlap and each covers one block.
- **AC10.7 (too short)** Given a stream where fewer than N non-overlapping M-minute windows fit, then the stored result is `status = "computed"` with `rep_pace_s_per_km = null`, the run has no rep pace, and it counts in `runsWithoutPace` (no lap fallback).
- **AC10.8 (fallback: fast km laps)** Given an outdoor workout whose stream result is `unavailable`, then the rep pace comes from fast laps:
  - Only laps with `distance ≥ 500 m` count, and at least 3 of them are needed.
  - Take the median lap speed (distance ÷ elapsed time). A lap is *fast* if its speed is at least `median × FAST_LAP_SPEED_FACTOR` (1.08).
  - Rep pace = Σ elapsed of fast laps ÷ (Σ distance of fast laps ÷ 1000), rounded half-up.
  - Example: nine 1000 m laps with elapsed 330, 330, 330, 250, 300, 255, 300, 252 and 330 s. The median is 300 s/km, so the fast laps are 250, 255 and 252, and the rep pace is 252 s/km.
- **AC10.9** Given the fallback finds no fast lap, or fewer than 3 laps of at least 500 m, then the run has no rep pace and counts in `runsWithoutPace`.
- **AC10.10** Given an outdoor workout whose stream call is still pending (detail missing, or stream call not yet made in this sync because of the cap), then it has no rep pace for now and counts in `runsWithoutPace` and in `sync.runsPendingDetail`.

### R11 — Workout pace: last 30 days vs previous 30 days
- **AC11.1** Given today 2026-10-29, then `currentWindow` is 2026-09-30 to 2026-10-29 and `previousWindow` is 2026-08-31 to 2026-09-29 (inclusive local dates, using the run's `start_date_local` date).
- **AC11.2** Given two 5x6min runs in the current window with rep paces 250 and 255 s/km, then `current.averageRepPaceSecondsPerKm = 253` (the mean of the run paces, rounded half-up to an integer) and `current.runCount = 2`.
- **AC11.3** Given current 253 and previous 260, then `changeSecondsPerKm = −7`. Negative means faster.
- **AC11.4** Given easy runs in the windows, then no `easy` entry appears. Given a workout type with at least one run in the 60 days (even one without pace), then it appears. Types are ordered by most recent run, newest first.
- **AC11.5** Given types with enough data, when the card renders, then it shows a grouped bar chart with one category per type and two series, "Previous 30 days" and "Last 30 days". The y-axis is labelled "Pace (min/km), lower is faster" with tick labels formatted `m:ss`, each bar has a data label like "4:13", and each category shows "7 s/km faster", "3 s/km slower" or "same pace".

### R12 — Not enough data
- **AC12.1** Given a type in the HR section with fewer than 3 points, then `hasEnoughData = false` and `trend = null`. When it is selected, the card shows "Not enough data for a comparison yet: 2 runs with heart rate, at least 3 needed." instead of a chart.
- **AC12.2** Given a workout type with no paced run in one or both windows, then `hasEnoughData = false`, the empty window is `null` and `changeSecondsPerKm = null`. The card lists it below the chart under "Not enough data yet" as "5x6min: no paced runs in the previous 30 days" (or "last 30 days", or "either period"), and it is not drawn as a bar.
- **AC12.3** Given every workout type in the pace section has `hasEnoughData = false`, then no chart is rendered, only the "Not enough data yet" list.

### R13 — Dashboard page states
- **AC13.1** Given the dashboard request is pending, then the page shows "Loading your runs… (the first import can take a minute)" and no charts.
- **AC13.2** Given the request fails with a non-401 error (500, network), then the page shows "Couldn't load your dashboard." and a "Retry" button, and clicking Retry sends the request again.
- **AC13.3** Given a `200` response where the weekly weeks all have `runCount = 0`, `heartRateTrend.types` is empty and `workoutPaceComparison.types` is empty, then the page shows a single empty state, "No runs in the last 5 months", instead of the three cards (the sync banner, if any, still shows).
- **AC13.4** Given a normal response, then three cards render in order: "Weekly distance", "Heart rate by run type" and "Workout pace: last 30 days vs previous 30 days". A card whose own data is empty shows its own empty text: AC8.6, "No runs in the last 5 months" for HR, and "No workouts (e.g. 5x6min) in the last 60 days" for pace.

## Traceability
| Success criterion (intent) | Covered by |
|---|---|
| S1 | R1, R2, R13 |
| S2 | R8, R4 |
| S3 | R6, R2 (scope `activity:read_all` for private runs), R4 (AC4.11) |
| S4 | R9, R12 |
| S5 | R7 |
| S6 | R10, R11 |
| S7 | R10 (AC10.1–AC10.4) |
| S8 | R12, R13 (AC13.4) |

## Design

### Data from Strava
- **Endpoints**
  - `GET https://www.strava.com/oauth/authorize` (browser redirect): `client_id`, `redirect_uri`, `response_type=code`, `approval_prompt=auto`, `scope=activity:read_all`, `state`.
  - `POST https://www.strava.com/oauth/token` (form-encoded): `client_id`, `client_secret`, plus `code` + `grant_type=authorization_code`, or `refresh_token` + `grant_type=refresh_token`. Fields read: `access_token`, `refresh_token`, `expires_at` (epoch seconds), and `athlete.id` (code exchange only).
  - `GET https://www.strava.com/api/v3/athlete/activities?after=<epoch>&page=<n>&per_page=200` with `Authorization: Bearer <access>`.
  - `GET https://www.strava.com/api/v3/activities/{id}?include_all_efforts=false`: the detail call, for `description` and `laps`.
  - `GET https://www.strava.com/api/v3/activities/{id}/streams?keys=time,velocity_smooth&key_by_type=true`: outdoor workout runs only.
- **Scopes:** `activity:read_all` only. It covers private activities and their streams, and is read-only. `read`, `profile:read_all` and write scopes are not requested.
- **Fields used** (Strava DTOs declare only these; `Json { ignoreUnknownKeys = true }`, so `map`, `start_latlng`, `end_latlng`, polylines, any extra stream Strava returns (e.g. `distance`) and every other field are never deserialized):
  - Summary: `id`, `name`, `sport_type`, `start_date` (UTC), `start_date_local`, `distance` (m), `average_heartrate` (nullable), `trainer`.
  - Detail: `description` (nullable), `laps[].lap_index`, `laps[].elapsed_time` (s), `laps[].distance` (m).
  - Streams: `time.data` (int seconds since start), `velocity_smooth.data` (m/s).
  - `start_date_local` comes back with a trailing `Z` but is local wall-clock time. Parse it as a `LocalDateTime` after dropping the `Z`; never convert it with a zone.
- **Sync & caching (local SQLite DB)**
  - **Imported once, then incremental.**
    - The first sync imports the last 5 months (+1 day) of activities.
    - When a list pass reads all its pages, `sync_state.list_cursor` is set to the `start_date` of the newest stored run (or to the backfill start if none). Every later sync lists with `after = min(list_cursor, now − 7 days)`, so it only pulls runs newer than the newest one already in the DB, plus the 7-day re-check window.
    - History is never downloaded again: not on page loads, not after a restart (AC4.1, AC4.2, AC4.4).
  - **When a sync runs.** A sync is triggered inside `GET /api/dashboard/running` when it is *due*: (last successful sync ≥ 15 min ago **or** pending detail/stream calls > 0) **and** `now ≥ rateLimitedUntil`. A `kotlinx.coroutines.sync.Mutex` makes sure only one sync runs at a time.
  - **Steps.**
    1. Make sure the token is fresh (R3).
    2. List pages, upsert Runs, delete re-listed non-Runs, and set `list_cursor` once all pages are read.
    3. Make activity calls, sequentially, capped at `MAX_ACTIVITY_CALLS_PER_SYNC = 80` and in the order of AC4.9: missing details → missing outdoor-workout streams (each computes and stores only the derived pace) → 7-day detail re-checks.
    4. Prune runs older than 6 months.
    5. Save `lastSuccessfulSyncAt`. This happens only when steps 1–3 finish without a 429 or failure; a cap-limited sync still counts as successful, with status `partial`.
  - **API call cost.**
    - Steady state: each new outdoor workout costs 2 calls (detail + streams). Each new easy or treadmill run costs 1 call (detail). Each sync adds about 1 list call, plus re-checks for runs from the last 7 days (at most once per hour each).
    - The one-time import of about 130 runs, about 45 of them outdoor workouts, is roughly 175 activity calls, so about 3 syncs at 80 calls each. That's under Strava's 100 requests / 15 min per sync; any 429 in between is handled by R5.
    - Streams are fetched once per run and type key, never on re-checks unless the type key changes.
  - **Retries.** There are no automatic retries apart from the single 401-refresh retry. The next page load is the retry.
  - **HTTP client.** Ktor client (CIO) with `ContentNegotiation` and `HttpTimeout` (connect 5 s, request 15 s). The Ktor `Logging` plugin is **not** installed.
- **Errors**
  - **401** → refresh once and retry once (AC3.2). If that fails, delete the tokens and return `strava_reauth_required` (AC3.3).
  - **429** → stop the sync and set `rateLimitedUntil` from the headers: `X-ReadRateLimit-Limit`/`X-ReadRateLimit-Usage` if present, else `X-RateLimit-Limit`/`X-RateLimit-Usage`, each in the form `"<15-min>,<daily>"`. The value is the next quarter hour, or the next 00:00 UTC when daily usage ≥ daily limit. Serve from the DB with status `rateLimited` (AC5.1, AC5.2).
  - **Network failure / timeout / 5xx** → stop the sync and serve from the DB with status `stravaUnavailable` (AC5.3).
  - **Streams `404`** → stored as `unavailable`; the lap fallback is used (AC4.7, AC10.8).
  - **Token endpoint `400`/`401`** while refreshing → treated as revoked (AC3.3).
  - Inside the client these are values: `sealed interface StravaResult<out T> { Success, NotFound, Unauthorized, RateLimited(until), Unavailable }`. No exceptions cross layers.

### Domain model & logic
All in `domain/`, pure, with no Ktor, Exposed or Strava types. `Clock` and `ZoneId` (server default zone) are injected; `today = LocalDate.now(clock.withZone(zone))`.

- **Value classes:** `ActivityId(Long)`, `Meters(Double)`, `Seconds(Int)`, `SecondsPerKm(Int)`, `MetersPerSecond(Double)`.
- **`RunActivity`:** `(id, name, startUtc: Instant, startLocal: LocalDateTime, distance: Meters, averageHeartRateBpm: Double?, trainer: Boolean, description: String?, laps: List<Lap>?, streamPace: StreamPaceResult?)`.
  - `laps == null` means the detail hasn't been fetched. `Lap(index: Int, elapsed: Seconds, distance: Meters)`.
  - `StreamPaceResult(typeKey: String, status: Computed | Unavailable, pace: SecondsPerKm?)`.
- **`SpeedStream(times: List<Seconds>, speeds: List<MetersPerSecond>)`:** the domain input for stream analysis. It is mapped from the Strava DTO in `strava/` and thrown away after the pace is computed.
- **`RunType`:** `sealed interface { data object Easy; data class Workout(reps: Int, minutes: Int) }`. `key` is `"easy"` or `"${reps}x${minutes}min"`. `label` is `"Easy runs"` or the same text as `key`.
- **`RunClassifier.classify(run): RunType`:** case-insensitive regex `(?<![\d.])(\d{1,2})\s*[x×]\s*(\d{1,3})\s*(?:min(?:ute)?s?|')(?![a-z])`. It tries the name first, then the description, and takes the first match. Valid ranges: N 1..50, M 1..120.
- **`TreadmillPaceParser.parsePaceLine(description): List<SecondsPerKm>?`:** see AC10.2. Constants: `MIN_PACE = 120 s`, `MAX_PACE = 720 s`.
- **`StreamRepPaceFinder.find(stream, workout): SecondsPerKm?`:** the windowed algorithm in AC10.5–AC10.7; returns `null` when fewer than N windows fit or the distance sum is 0. Running time is O(n log n); a 1-hour run at 1 Hz is about 3,600 samples.
- **`FastLapPaceFinder.find(laps): SecondsPerKm?`:** AC10.8–AC10.9. Constants: `MIN_LAP_DISTANCE_M = 500.0`, `MIN_LAPS_FOR_MEDIAN = 3`, `FAST_LAP_SPEED_FACTOR = 1.08`.
- **`RepPaceCalculator.repPace(run, workout): SecondsPerKm?`:**
  - `trainer == true`: pace line, else `null`.
  - `trainer == false`:
    - Stream result with a matching `typeKey`: `Computed` → its pace; `Unavailable` → `FastLapPaceFinder`.
    - No result yet, or the type key doesn't match → `null` (pending).
- **`WeeklyDistance.compute(runs, today): List<WeekTotal>`:** 9 weeks; the current week starts at `today.with(previousOrSame(MONDAY))`; bucketing uses `startLocal.toLocalDate()`.
- **`HeartRateTrend.compute(runs, today): HrTrendResult`:** window `[today − 5 months, today]`; `MIN_POINTS_FOR_COMPARISON = 3`; least squares over x = days since `windowStart`.
- **`WorkoutPaceComparison.compute(runs, today): PaceComparisonResult`:** windows `[today − 29, today]` and `[today − 59, today − 30]`.
- **`SyncService`:** the steps above, using the `ActivitySource` port (implemented in `strava/`) and the repository ports (implemented in `persistence/`). It calls `StreamRepPaceFinder` right after each streams call and stores only the result.
- **`RunningDashboardService.get(): DashboardResult`:** runs the sync if due, reads the runs, builds the three aggregates plus `SyncInfo(status, lastSyncedAt, retryAfter, runsPendingDetail)`, or returns `NotConnected` / `ReauthRequired`.
- **Units and rounding:** km = m ÷ 1000 rounded half-up to 1 decimal; bpm to 1 decimal; pace to whole seconds per km, half-up. Conversions happen only in `domain`; the frontend formats `m:ss` but never converts.
- **Run filter:** only `sport_type == "Run"` is persisted (in the strava→domain mapper and the sync service). The aggregates also filter on the type defensively for AC6.1.

### Persistence
Exposed + SQLite (`sqlite-jdbc`). The file comes from `DB_PATH` and survives restarts; it is the long-lived local store the incremental sync builds on. Tables are created with `SchemaUtils.create` at startup (this is the first schema, so there's no migration). All access goes through `newSuspendedTransaction(Dispatchers.IO)`.

| Table | Columns |
|---|---|
| `strava_tokens` | `athlete_id BIGINT PK`, `access_token TEXT`, `refresh_token TEXT`, `expires_at BIGINT` (epoch s), `scope TEXT`. At most one row. |
| `runs` | `id BIGINT PK` (Strava id), `name TEXT`, `start_date_utc BIGINT` (epoch s, indexed), `start_date_local TEXT` (ISO `LocalDateTime`), `distance_m DOUBLE`, `average_heartrate DOUBLE NULL`, `trainer BOOLEAN`, `description TEXT NULL`, `detail_fetched_at BIGINT NULL` |
| `run_laps` | `run_id BIGINT FK → runs.id ON DELETE CASCADE`, `lap_index INT`, `elapsed_time_s INT`, `distance_m DOUBLE`; PK (`run_id`, `lap_index`) |
| `run_stream_paces` | `run_id BIGINT PK FK → runs.id ON DELETE CASCADE`, `type_key TEXT`, `status TEXT` (`computed` \| `unavailable`), `rep_pace_s_per_km INT NULL`, `computed_at BIGINT` |
| `sync_state` | `id INT PK` (always 1), `list_cursor BIGINT NULL` (null = import not finished), `last_successful_sync_at BIGINT NULL`, `rate_limited_until BIGINT NULL`, `last_status TEXT NULL`, `owner_athlete_id BIGINT NULL` (the athlete the stored runs belong to; survives a token delete) |

**Decision: raw streams are not stored.** Only the derived per-run rep pace is kept. The stream is needed once to compute it, the result doesn't change unless the type key changes (which triggers a single re-fetch), and keeping thousands of speed samples per run would go against data minimisation (privacy item 6). The trade-off: changing the algorithm later means re-fetching streams. That costs one call per outdoor workout and could be done by clearing `run_stream_paces`.

There is no column for coordinates, polylines, raw streams, HR series, calories, gear or athlete profile.

### Backend API

#### `GET /api/dashboard/running`
- Query params: none. Auth: requires a valid `sd_session` cookie whose athlete id matches the stored tokens.
- May run a Strava sync before responding (see Sync & caching).
- Response `200` (JSON, exact; every field is always present, and nullable fields are `null` rather than missing):
```json
{
  "sync": {
    "status": "ok",
    "lastSyncedAt": "2026-10-29T08:12:03Z",
    "retryAfter": null,
    "runsPendingDetail": 0
  },
  "weeklyDistance": {
    "weeks": [
      {
        "weekStart": "2026-08-31",
        "weekEnd": "2026-09-06",
        "label": "31 Aug",
        "distanceKm": 32.4,
        "runCount": 4,
        "isCurrentWeek": false
      }
    ]
  },
  "heartRateTrend": {
    "windowStart": "2026-05-29",
    "windowEnd": "2026-10-29",
    "minRunsForComparison": 3,
    "types": [
      {
        "typeKey": "easy",
        "label": "Easy runs",
        "kind": "easy",
        "runCount": 41,
        "runsWithoutHeartRate": 2,
        "hasEnoughData": true,
        "points": [
          { "date": "2026-06-01", "averageHeartRateBpm": 142.3, "distanceKm": 8.2, "treadmill": false }
        ],
        "trend": {
          "startDate": "2026-06-01",
          "startBpm": 145.2,
          "endDate": "2026-10-28",
          "endBpm": 140.1,
          "changeBpm": -5.1
        }
      }
    ]
  },
  "workoutPaceComparison": {
    "currentWindow": { "start": "2026-09-30", "end": "2026-10-29" },
    "previousWindow": { "start": "2026-08-31", "end": "2026-09-29" },
    "types": [
      {
        "typeKey": "5x6min",
        "label": "5x6min",
        "current": { "averageRepPaceSecondsPerKm": 253, "runCount": 2 },
        "previous": { "averageRepPaceSecondsPerKm": 260, "runCount": 3 },
        "changeSecondsPerKm": -7,
        "runsWithoutPace": 1,
        "hasEnoughData": true
      }
    ]
  }
}
```
- Field types:
  - `sync.status`: `"ok" | "partial" | "rateLimited" | "stravaUnavailable"`; `lastSyncedAt`, `retryAfter`: ISO-8601 UTC instant or `null`; `runsPendingDetail`: int (runs waiting for a detail or stream call).
  - `weeks`: always 9 entries, oldest first. `weekStart`/`weekEnd`: ISO local dates; `label`: `"d MMM"` (English); `distanceKm`: number (1 decimal); `runCount`: int; `isCurrentWeek`: boolean.
  - `types[].kind`: `"easy" | "workout"`; `points[].date`: ISO local date of `start_date_local`; `averageHeartRateBpm`, `distanceKm`: number (1 decimal); `trend`: object or `null` (null whenever `hasEnoughData` is false).
  - `current`/`previous`: object or `null`; `averageRepPaceSecondsPerKm`: int (s/km); `runCount`: int (runs with a pace in that window); `changeSecondsPerKm`: int or `null` (current − previous); `runsWithoutPace`: int (workouts of that type in the 60 days without a pace).
- Errors (body `{"error": "<code>", "message": "<safe text>"}`):
  - `401 not_connected`: no or invalid session, or no tokens.
  - `401 strava_reauth_required`: tokens revoked or refresh failed (tokens deleted).
  - `500 internal_error`: unexpected (the existing `StatusPages` handler).
  - Strava 429, outages and partial syncs are **not** errors; they come back as `200` with `sync.status`.

#### `GET /auth/strava/login`
- No auth. Returns `302` to Strava authorize and sets `sd_oauth_state` (AC2.3).

#### `GET /auth/strava/callback`
- Query: `code`, `state`, `scope`, `error` (all optional strings from Strava). Returns `302` to `/` (success) or `/connect?error=<access_denied|scope_missing|state_mismatch|token_exchange_failed>` (AC2.4–AC2.9).

### Frontend (Angular)
- **Routes** (`app.routes.ts`): `''` → `loadComponent` `features/running-dashboard/running-dashboard-page`; `'connect'` → `loadComponent` `features/connect/connect-page`; `'**'` → redirect to `''`.
- **App shell**: replace the placeholder `app.html` with `<main><router-outlet /></main><footer>Powered by Strava</footer>`. Remove the placeholder styles and the `title` signal, and update `app.spec.ts`.
- **app.config.ts**: `provideHttpClient(withInterceptors([apiErrorInterceptor]), withXsrfConfiguration({}))` and `provideEchartsCore({ echarts })` (tree-shaken `echarts/core` with Bar, Line and Scatter charts, Grid/Tooltip/Legend/MarkLine components and the Canvas renderer).
- **Service & TS interfaces** (`core/api/`):
  - `running-dashboard.models.ts`: `RunningDashboardResponse`, `SyncInfo`, `SyncStatus`, `WeeklyDistance`, `WeekTotal`, `HeartRateTrend`, `HrRunType`, `HrPoint`, `HrTrendLine`, `WorkoutPaceComparison`, `DateWindow`, `PaceTypeComparison`, `PaceWindowStat`, `ApiError`. These mirror the JSON above field for field (nullable fields typed `T | null`).
  - `running-dashboard.service.ts`: `getDashboard(): Observable<RunningDashboardResponse>` → `GET /api/dashboard/running`.
- **Interceptor** (`core/interceptors/api-error.interceptor.ts`): on a `401` from `/api/*`, navigate to `/connect` (adding `?reason=reauth` when `error == "strava_reauth_required"`) and rethrow; other errors pass through.
- **Components**
  - `ConnectPage` (`features/connect/`): heading, one line of explanation, the `<a href="/auth/strava/login">Connect with Strava</a>` link, and a message mapped from the `error`/`reason` query params to fixed texts (AC2.10, AC3.3).
  - `RunningDashboardPage` (container): fetches through the service; holds a `state` signal (`loading | error | loaded`), a `selectedHrTypeKey` signal (default: the first type key) and `retry()`. Renders `SyncBanner`, then the three cards.
  - `SyncBanner`: `input<SyncInfo>()` → banner text (AC5.4).
  - `WeeklyDistanceChart`: `input<WeeklyDistance>()`.
  - `HeartRateTrendChart`: `input<HeartRateTrend>()`, `input<string>() selectedTypeKey`, `output<string>() typeSelected`. Contains the native `<select>` and the not-enough-data message.
  - `WorkoutPaceComparisonChart`: `input<WorkoutPaceComparison>()`. Shows the chart for types with `hasEnoughData` and the "Not enough data yet" list for the rest.
  - `shared/chart/ChartComponent`: wraps `ngx-echarts` `[options]` and takes `input<EChartsOption>()`.
  - All components are `OnPush`, use signals, and use `@if`/`@for` with `track`.
- **Pure helpers**
  - `shared/format/pace.ts`: `formatPace(secondsPerKm): "4:13"`, `formatPaceChange(delta): "7 s/km faster" | "3 s/km slower" | "same pace"`.
  - `shared/pipes/pace.pipe.ts`: uses `formatPace` and appends " /km".
  - `shared/chart/palette.ts`: shared colours (primary, previous, current-week, trend).
  - Option builders in `features/running-dashboard/chart-options/`: `buildWeeklyDistanceOptions(weeks)`, `buildHeartRateTrendOptions(type)` and `buildWorkoutPaceOptions(types)`, each returning `EChartsOption`.
- **States**
  - loading: AC13.1 text.
  - error: AC13.2 message + Retry.
  - empty: AC13.3 page-level, or the per-card empty texts in AC13.4.
  - normal: banner (if any) + three cards.
  - not-enough-data: AC12.1–AC12.3.
- **Charts**
  - Weekly: bar; x = category `label`; y = "km"; tooltip per AC8.5.
  - HR: x = time axis (dates); y = "Avg HR (bpm)" with `scale: true`; series = a line+symbol series of the points plus a dashed line series for the trend (2 points); tooltip per AC9.7.
  - Pace: grouped bar; x = type labels; y = seconds per km with `axisLabel.formatter` = `formatPace`, named "Pace (min/km), lower is faster"; series "Previous 30 days" (palette.previous) and "Last 30 days" (palette.primary); data labels `formatPace`; the per-type change text is rendered in the HTML below the chart, not inside ECharts.
  - Tooltip and label formatters only see numbers, dates and backend-generated type labels (made from regex digits). No Strava free text reaches the browser.

### Files expected to change
Backend (`backend/`):
- `gradle/libs.versions.toml`, `build.gradle.kts`: add `ktor-server-sessions`, `ktor-client-core`, `ktor-client-cio`, `ktor-client-content-negotiation`, `exposed-core`, `exposed-jdbc`, `exposed-java-time`, `sqlite-jdbc`, `kotlinx-coroutines-core`; test: `ktor-client-mock`, `mockk`, `kotlinx-coroutines-test`.
- `src/main/kotlin/app/stravadashboard/Application.kt`: wire config, DB, sessions, client, services and routes; move `ErrorResponse` to `api/ErrorResponse.kt`.
- `config/AppConfig.kt`: add `stravaClientId`, `stravaClientSecret`, `stravaRedirectUri`, `sessionSecret` (hex, ≥ 32 bytes) and `dbPath`, all required (fail fast).
- `auth/`: `AuthRoutes.kt`, `UserSession.kt`, `OAuthState.kt`, `SessionGuard.kt` (route-scoped check for `/api/dashboard/*`).
- `strava/`: `StravaClient.kt`, `StravaOAuthClient.kt`, `StravaTokenProvider.kt`, `StravaDtos.kt` (incl. `StreamSetDto` with only `time` and `velocity_smooth`), `StravaMappers.kt`, `StravaResult.kt`, `RateLimitHeaders.kt`, `StravaActivitySource.kt` (implements the domain port).
- `domain/`: `model/Units.kt`, `model/RunActivity.kt`, `model/RunType.kt`, `model/SpeedStream.kt`, `RunClassifier.kt`, `TreadmillPaceParser.kt`, `StreamRepPaceFinder.kt`, `FastLapPaceFinder.kt`, `RepPaceCalculator.kt`, `WeeklyDistance.kt`, `HeartRateTrend.kt`, `WorkoutPaceComparison.kt`, `SyncService.kt`, `RunningDashboardService.kt`, `ports/ActivitySource.kt`, `ports/RunRepository.kt`, `ports/TokenRepository.kt`, `ports/SyncStateRepository.kt`.
- `persistence/`: `DatabaseFactory.kt`, `Tables.kt`, `ExposedRunRepository.kt` (runs, laps, stream paces), `ExposedTokenRepository.kt`, `ExposedSyncStateRepository.kt`.
- `api/`: `RunningDashboardRoutes.kt`, `RunningDashboardDtos.kt`, `RunningDashboardMappers.kt`, `ErrorResponse.kt`.
- Tests: `src/test/kotlin/app/stravadashboard/{domain,strava,persistence,api,auth}/…` and `src/test/resources/strava/*.json` (invented fixtures, including a synthetic streams fixture).
- Repo root: `.env.example` (create: `STRAVA_CLIENT_ID=`, `STRAVA_CLIENT_SECRET=`, `STRAVA_REDIRECT_URI=`, `SESSION_SECRET=`, `DB_PATH=`, `HOST=`, `PORT=`); `.gitignore` (the `.env` and `*.db` entries).

Frontend (`frontend/src/app/`):
- `app.html`, `app.scss`, `app.ts`, `app.spec.ts`, `app.routes.ts`, `app.config.ts`.
- `core/api/running-dashboard.models.ts`, `core/api/running-dashboard.service.ts` (+ `.spec.ts`).
- `core/interceptors/api-error.interceptor.ts` (+ `.spec.ts`).
- `features/connect/connect-page.ts|html|scss|spec.ts`.
- `features/running-dashboard/running-dashboard-page.ts|html|scss|spec.ts`.
- `features/running-dashboard/sync-banner/…`, `weekly-distance-chart/…`, `heart-rate-trend-chart/…`, `workout-pace-comparison-chart/…` (each with `.spec.ts`).
- `features/running-dashboard/chart-options/weekly-distance.options.ts`, `heart-rate-trend.options.ts`, `workout-pace.options.ts` (+ `.spec.ts`).
- `shared/chart/chart.ts`, `shared/chart/palette.ts`, `shared/format/pace.ts` (+ spec), `shared/pipes/pace.pipe.ts` (+ spec).

## Edge cases
| Case | Expected behaviour |
|---|---|
| No activities in range | Weekly: 9 zero weeks, and the card shows "No runs in the last 9 weeks". HR/pace: empty `types`, with per-card empty texts. Nothing at all → page-level "No runs in the last 5 months" (AC13.3). |
| First connect | One-time 5-month import. Status is `partial` across about 3 syncs until details and streams are done; a 429 gives `rateLimited`, and the next load after `retryAfter` continues. After that, only runs newer than the newest stored run (plus 7 days) are pulled. |
| Import interrupted mid-list | `list_cursor` stays null, so the next sync re-lists from the backfill start (upsert, no duplicates) (AC4.1). |
| Backend restart | The DB persists, so there is no re-import (AC4.4). |
| Long break (newest run > 7 days old) | `after` = newest stored run's start (AC4.2). |
| Manual activity (no GPS) | Counted in weekly km. No HR → left out of HR points. Workout → no streams (404/empty → `unavailable`) and usually no laps → no pace. No GPS is ever read or stored. |
| Activity without heart rate | Left out of `points` and counted in `runsWithoutHeartRate`. Still counts in km and pace. |
| Private activity | Included (scope `activity:read_all`). |
| Walk / Ride / Hike / TrailRun / VirtualRun | Not stored and not counted (AC4.11). |
| Run changed to another sport type on Strava within 7 days | Deleted from the DB at the next sync (AC4.11). |
| Run deleted on Strava | Detected only when its detail is fetched or re-checked: a `404` deletes it locally. Otherwise it stays in the DB until pruned at 6 months (see follow-ups). |
| Treadmill paces added to the description after upload | Picked up at the next sync if the run is within 7 days and its detail is more than 1 h old (AC4.10). |
| Treadmill workout without a pace line | No pace and counted in `runsWithoutPace`; Garmin pace, laps and streams are never used (AC10.3, AC4.8). |
| Outdoor workout with a pace line in the description | The pace line is ignored (AC10.4). |
| Pace-line count ≠ N reps | All values on the line are averaged (AC10.2). |
| Outdoor workout with only 1 km auto-laps | Stream windows find the reps (AC10.5); fast-km-lap fallback if streams are unavailable (AC10.8). |
| Run shorter than N × M minutes | No pace (AC10.7). |
| Watch auto-pause (gaps in `time` stream) | Trapezoid integration bridges the gap with the average of the two speeds around it. Windows are measured in recorded elapsed time. |
| Workout renamed within 7 days (type key changes) | Stored stream result no longer matches → one new streams call (AC4.10). |
| Distance-based reps ("6x400m", "8x1k") | Classified as easy (confirmed). |
| Week spans DST change / time zones | Bucketing uses the `start_date_local` date only, so DST doesn't matter. A run done while travelling counts on its local date. `today` comes from the server's time zone. |
| Run near midnight local vs UTC | Counted on the local date (AC8.2). |
| Distance 0 (treadmill without footpod) | Counts as 0 km. Treadmill pace comes only from the pace line. |
| Strava 429 | Sync stops and the DB is served with a banner; no calls before `retryAfter` (AC5.1, AC5.2). |
| Strava down / timeout | The DB is served with a banner (AC5.3). |
| Expired access token | Refreshed before the call (AC3.1). |
| Revoked access | Tokens deleted → `/connect?reason=reauth` (AC3.3). |
| User unticks "private activities" on Strava consent | `scope_missing`, and nothing is stored (AC2.6). |
| Different athlete connects | Old data wiped, also after a revoke (AC2.9). |

## Privacy
- **Data fields & sensitivity:**
  - Activity id, name, description, sport type, start times (UTC and local), distance, `trainer` flag, and lap elapsed time/distance: personal data. Names and descriptions are user-written text.
  - Whole-run average heart rate: **health data, GDPR Art. 9**.
  - Speed stream (`time`, `velocity_smooth`): personal activity data. It is fetched transiently for outdoor workouts and only a single derived rep pace per run is stored. It contains no location.
  - Strava athlete id plus OAuth access and refresh tokens: **credentials**.
  - Never requested, stored or deserialized: GPS (`latlng`), polylines, HR streams, altitude, cadence, calories, gear, profile data.
- **Storage & retention:**
  - Everything is stored in the local SQLite file at `DB_PATH` (git-ignored, outside the repo).
  - Only `sport_type == "Run"` activities are stored. Runs and their derived data are pruned when older than 6 months; tokens are kept until revoked or replaced. Raw streams are not retained.
  - The browser gets only aggregated, chart-ready numbers and backend-generated type labels. No names, descriptions, ids, streams or tokens are sent.
  - No browser storage is used.
- **Scopes:** `activity:read_all` (needed for private runs, intent decision 3). Nothing else.
- **Applicable privacy-check items:**

| # | Status | How it's satisfied |
|---|---|---|
| 1 | ✅ | Secrets only from env vars; `.env.example` lists keys without values; fixtures are invented. |
| 2 | ✅ (partial) | Tokens live only in the backend DB and never reach the browser (AC2.7, AC2.11). Stored **unencrypted**: acceptable while the DB never leaves the machine (see risks). |
| 3 | ✅ | Refresh before expiry and on 401; revoked tokens are deleted (R3). |
| 4 | ✅ | Random `state` in a signed HttpOnly cookie, checked on callback (AC2.3, AC2.4). |
| 5 | ✅ | `activity:read_all` only. |
| 6 | ✅ | Only the fields listed above are stored; non-Runs are not stored; streams are reduced to one number per run. |
| 7 | ✅ | Streams are requested with `keys=time,velocity_smooth` only; `latlng` is never requested, and no GPS fields are declared or stored. |
| 8 | ✅ | Logs hold only activity ids and counts; no tokens, codes, headers, names, descriptions, HR values or stream values (AC3.4). |
| 9 | ✅ | Ktor client `Logging` plugin not installed; `logback.xml` unchanged. |
| 10 | ✅ | Single error shape; `StatusPages` hides stack traces; raw Strava bodies are never forwarded. |
| 11 | ✅ | Binds to `127.0.0.1` by default; `/api/dashboard/*` checks the session (AC2.1). |
| 12 | ✅ | `sd_session` is HttpOnly, SameSite=Lax, `Secure` on https, and signed with `SESSION_SECRET` (AC2.7). |
| 13 | ✅ | No Strava free text reaches the frontend. Formatters only see numbers and generated labels. No `[innerHTML]` or `bypassSecurityTrust*`. The connect page maps error codes to fixed texts. |
| 14 | n/a / ⚠ | No state-changing endpoints. `GET /api/dashboard/running` only refreshes the local read cache as a side effect (see risks). XSRF config is added to `provideHttpClient` for future POSTs. |
| 15 | ✅ | DTOs contain only chart fields; no CORS is configured. |
| 16 | ✅ | ECharts is bundled from npm; no CDN, fonts or analytics. |
| 17 | ✅ | "Powered by Strava" footer (AC1.2); data shown only to the owner. |
| 18 | ❌ deferred | No disconnect/delete-data action in this feature (not in the intent). Workaround: revoke the app in Strava settings, which makes the next sync delete the tokens, then delete the `DB_PATH` file. See follow-ups. |
| 19 | n/a | No webhook. |
| 20 | ✅ | Documented here: the DB file and any copy of it hold health data and credentials. |
| 21 | ✅ | `src/test/resources/strava/*.json` (including the synthetic streams fixture) and frontend fixtures use invented ids, names, HR, speeds and dates. |

## Test plan
| AC | Test type | Test name |
|---|---|---|
| AC1.1 | frontend component (router) | `R1 - root route shows running dashboard, not placeholder` |
| AC1.2 | frontend component | `R1 - app shell shows Powered by Strava` |
| AC1.3 | frontend component (router) | `R1 - unknown route redirects to dashboard` |
| AC2.1 | backend route | `R2 - dashboard without session returns 401 not_connected` |
| AC2.2 | frontend service (interceptor) + component | `R2 - 401 from api navigates to connect`, `R2 - connect page links to auth login` |
| AC2.3 | backend route | `R2 - login redirects to Strava with activity read_all scope and state cookie` |
| AC2.4 | backend route | `R2 - callback with mismatched state redirects with state_mismatch` |
| AC2.5 | backend route | `R2 - callback with access_denied redirects with access_denied` |
| AC2.6 | backend route | `R2 - callback without activity read_all scope stores nothing` |
| AC2.7 | backend route + client (MockEngine) | `R2 - successful callback stores tokens sets session and leaks no token` |
| AC2.8 | backend route | `R2 - failed token exchange redirects with token_exchange_failed` |
| AC2.9 | backend persistence + route | `R2 - connecting a different athlete wipes previous data` |
| AC2.10 | frontend component | `R2 - connect page shows fixed message per error code` |
| AC2.11 | backend route | `R2 - dashboard JSON matches contract and has no sensitive fields` |
| AC3.1 | backend client | `R3 - token near expiry is refreshed before api call` |
| AC3.2 | backend client | `R3 - 401 triggers one refresh and one retry` |
| AC3.3 | backend client + route · frontend component | `R3 - failed refresh deletes tokens and returns strava_reauth_required`, `R3 - connect page shows reauth message` |
| AC3.4 | backend client (log capture) | `R3 - sync logs contain no tokens activity text or stream values` |
| AC4.1 | backend unit (SyncService, fake source, fixed Clock) | `R4 - first sync imports five months with pagination`, `R4 - interrupted import restarts from backfill start` |
| AC4.2 | backend unit | `R4 - incremental sync lists after newest stored run or seven days ago` |
| AC4.3 | backend unit | `R4 - fresh DB within 15 minutes makes no Strava calls` |
| AC4.4 | backend persistence (file-backed SQLite in temp dir) | `R4 - restart reuses stored runs without re-import` |
| AC4.5 | backend unit + client | `R4 - details fetched newest first` |
| AC4.6 | backend unit + client + persistence | `R4 - outdoor workout gets one streams call and only derived pace is stored` |
| AC4.7 | backend unit + client | `R4 - missing streams stored as unavailable and not retried` |
| AC4.8 | backend unit | `R4 - no streams calls for treadmill or easy runs` |
| AC4.9 | backend unit | `R4 - detail and stream calls share cap of 80 in priority order` |
| AC4.10 | backend unit | `R4 - recent runs get detail refetched after one hour`, `R4 - type key change triggers new streams call` |
| AC4.11 | backend unit + persistence | `R4 - non runs are not stored and retyped runs are deleted` |
| AC4.12 | backend persistence | `R4 - runs older than six months are pruned with laps and stream paces` |
| AC4.13 | backend unit (coroutines-test) | `R4 - concurrent requests trigger one sync` |
| AC4.14 | backend unit | `R4 - remaining calls report partial status` |
| AC5.1 | backend client + unit | `R5 - 429 stops sync and sets retryAfter from rate limit headers` |
| AC5.2 | backend unit | `R5 - no Strava calls before retryAfter` |
| AC5.3 | backend client + unit | `R5 - network failure serves DB with stravaUnavailable` |
| AC5.4 | frontend component | `R5 - sync banner text per status` |
| AC6.1 | backend unit | `R6 - only Run sport type counts in all aggregates` |
| AC6.2 | backend unit | `R6 - manual run counts in km but not in HR or pace` |
| AC7.1 | backend unit (parameterised) | `R7 - NxMmin variants normalise to one type key` |
| AC7.2 | backend unit | `R7 - description used when name has no pattern and name wins` |
| AC7.3 | backend unit | `R7 - non matching names are easy` |
| AC7.4 | backend unit | `R7 - first pattern in name wins` |
| AC7.5 | backend unit | `R7 - run without detail classified by name` |
| AC7.6 | backend unit | `R7 - out of range reps or minutes are easy` |
| AC8.1 | backend unit | `R8 - nine Monday to Sunday weeks ending in current week` |
| AC8.2 | backend unit | `R8 - weekly totals bucket by local date` |
| AC8.3 | backend unit | `R8 - metres summed then converted to km with one decimal` |
| AC8.4 | backend unit | `R8 - empty weeks are returned as zero` |
| AC8.5 | frontend option builder + component | `R8 - weekly options have km axis, labels, tooltip and current week colour` |
| AC8.6 | frontend component | `R8 - weekly card shows empty state when no runs` |
| AC9.1 | backend unit | `R9 - HR points per type sorted by date and rounded` |
| AC9.2 | backend unit | `R9 - HR window is last five months inclusive` |
| AC9.3 | backend unit | `R9 - runs without HR are counted not plotted` |
| AC9.4 | backend unit | `R9 - treadmill and outdoor runs share a type` |
| AC9.5 | backend unit | `R9 - trend line is least squares over points` |
| AC9.6 | backend unit | `R9 - easy first then workouts by most recent run` |
| AC9.7 | frontend option builder + component | `R9 - selecting a type shows its points and trend` |
| AC10.1 | backend unit | `R10 - treadmill paces from description are averaged` |
| AC10.2 | backend unit (parameterised) | `R10 - pace line must contain only m ss tokens in range` |
| AC10.3 | backend unit | `R10 - treadmill without pace line has no pace` |
| AC10.4 | backend unit | `R10 - pace line ignored on outdoor workout` |
| AC10.5 | backend unit | `R10 - stream windows find work reps` |
| AC10.6 | backend unit | `R10 - stream windows do not overlap` |
| AC10.7 | backend unit | `R10 - stream too short for N windows gives no pace` |
| AC10.8 | backend unit | `R10 - fallback uses km laps faster than median` |
| AC10.9 | backend unit | `R10 - fallback without fast laps gives no pace` |
| AC10.10 | backend unit | `R10 - pending streams give no pace yet` |
| AC11.1 | backend unit | `R11 - rolling 30 day windows` |
| AC11.2 | backend unit | `R11 - window pace is mean of run paces` |
| AC11.3 | backend unit | `R11 - change is current minus previous` |
| AC11.4 | backend unit | `R11 - only workout types with runs in 60 days appear` |
| AC11.5 | frontend option builder + pace format + component | `R11 - pace comparison grouped bars with m ss labels and change text` |
| AC12.1 | backend unit · frontend component | `R12 - HR type with fewer than three points has no trend`, `R12 - HR card shows not enough data message` |
| AC12.2 | backend unit · frontend component | `R12 - pace type missing a window is not enough data`, `R12 - pace card lists types without enough data` |
| AC12.3 | frontend component | `R12 - no pace chart when no type has enough data` |
| AC13.1 | frontend component | `R13 - shows loading state` |
| AC13.2 | frontend component | `R13 - shows error state and retries` |
| AC13.3 | frontend component | `R13 - shows page empty state when no runs` |
| AC13.4 | frontend component + service | `R13 - renders three cards in order`, `R13 - service calls dashboard endpoint` |

## Out of scope
- Other dashboard widgets (only the three graphs).
- HR zones, power, elevation, GPS/maps, routes.
- Comparing different workout types; types other than reps x time workouts and easy runs (distance reps such as "6x400m" count as easy).
- Editing Strava data (read-only).
- Multi-user support.
- Splitting easy runs by distance (intent decision 4).
- Separate treadmill vs outdoor comparison (intent decision 5).
- TrailRun and VirtualRun (confirmed: not counted).
- Storing raw streams.
- Disconnect / delete-my-data action, deletion sync and webhooks (see follow-ups).

## Suggested follow-ups
- "Disconnect Strava" action: `POST /auth/disconnect` (CSRF-protected) that calls `POST https://www.strava.com/oauth/deauthorize` and deletes tokens and the DB contents. This closes privacy item 18.
- Detect activities deleted on Strava (re-list the 5-month window now and then, or use the webhook, which also covers item 19).
- Support distance-based workouts ("6x400m", "8x1k") as their own types, so they don't skew easy-run HR.
- Split easy runs by distance band.
- Manual "Refresh from Strava" button.
- Encrypt tokens at rest if the DB is ever backed up or moved off the machine.
- Treadmill distance correction for weekly km (Garmin treadmill distance can be off, like its pace).
- Show which method produced each rep pace (pace line / stream windows / fast laps) in the UI.

## Risks & decisions to confirm
1. **Infrastructure in scope.** There's no OAuth, Strava client or DB yet, so R2–R5 build them. That makes this feature much larger than "three charts". Confirm it's OK to ship them together.
2. **Stream-window method (new).**
   - The N fastest non-overlapping M-minute windows are treated as the reps. Picking is greedy, so in rare cases with very uneven efforts a window may straddle a rep boundary and give a slightly slower pace than the real rep.
   - Outdoor workout pace depends on GPS speed quality (`velocity_smooth`), which can be noisy in tunnels or under trees.
3. **Fast-km-lap fallback (new).** It is used only when streams are unavailable. "Fast" = at least 8 % faster than the median km lap. Because 1 km laps mix work and recovery, this pace is less accurate than the stream method. The 8 % threshold and 500 m minimum lap length are guesses to tune.
4. **Raw streams not stored (decision).** Changing the stream algorithm later means re-fetching one stream per outdoor workout.
5. **Extra API calls.** One streams call per outdoor workout (about 45 during the import, then 1 per new workout), sharing the 80-call cap per sync. The first import now takes about 3 syncs before `partial` clears.
6. **Thresholds.** The HR comparison needs ≥ 3 runs with HR; the pace comparison needs ≥ 1 paced run in each 30-day window.
7. **Sync on page load.** `GET /api/dashboard/running` refreshes the local DB as a side effect, at most once per 15 min or while the import is incomplete. There's no manual refresh button.
8. **Recent-edit window.** Description and name edits are picked up only for runs from the last 7 days.
9. **Scope `activity:read_all` without `read`.** This should be enough for activities, laps and streams. If Strava rejects it, `read` would need to be added.
10. **Tokens stored unencrypted** in the local SQLite file (privacy item 2 relies on the DB staying on this machine).
11. **No delete-data action** (privacy item 18 is deferred; manual workaround documented).
12. **"Today" uses the server's time zone** for the 9-week, 5-month and 30-day windows. Run dates always use their own `start_date_local`.
13. **Weekly km uses Strava's distance**, including Garmin treadmill distance, which may be inaccurate.
