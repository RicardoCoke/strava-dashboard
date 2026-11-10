# Spec: Move activity data from Strava to Garmin Connect

**Feature ID:** 021 · **Status:** Approved · **Intent:** [intent.md](./intent.md) · **Date:** 2026-11-09

## Summary
A `GarminActivitySource` adapter replaces `StravaActivitySource` behind the existing `ActivitySource` port. It lists runs from the Garmin Connect Personal API (same key and token flow as feature 015). Description and moving time come from the activity JSON. Laps, per-km splits and the per-second speed/HR samples come from the activity's original FIT file, parsed with the official FIT SDK, and position fields are never read. Runs starting before a per-person cut-over date are ignored, and the sync never touches cached runs from before the cut-over. Strava OAuth, sessions, connect page and every "not connected" guard are deleted, so the dashboard opens in any browser without a login.

## Requirements
| ID | Requirement | Priority |
|---|---|---|
| R1 | Every `/api` endpoint answers without a session cookie or stored token. Sessions, OAuth routes, session guards and every `NotConnected` result are removed. | Must |
| R2 | The frontend has no connect page, no `/connect` route and no 401 redirect. Opening `/` in a new browser loads the dashboard straight away. | Must |
| R3 | The background sync lists running activities (running, treadmill_running, trail_running) from Garmin Connect and stores them as runs, on or after the person's cut-over date only. | Must |
| R4 | For each Garmin run, the detail step stores description, moving time, laps and per-km splits. Laps and splits come from the FIT file. | Must |
| R5 | Speed, heart-rate and drift streams for Garmin runs come from the FIT file's records. Position fields are never read, stored or logged. | Must |
| R6 | Runs cached before the cut-over date (Strava history) are never listed, re-fetched, updated or deleted by the sync, and no run is stored twice. | Must |
| R7 | Cut-over date per person (`GARMIN_ACTIVITIES_FROM`, `MARINA_GARMIN_ACTIVITIES_FROM`, default 2026-11-10). A person without a Garmin key gets status `notConfigured` and their cached history read-only. | Must |
| R8 | Garmin failures (429, key rejected, network, 5xx) map onto the existing sync statuses. The sync banner names Garmin Connect. | Must |
| R9 | No Strava code, OAuth routes, Strava env vars or Strava dependencies remain. | Must |

## Acceptance criteria

### R1: No authentication
- **AC1.1** Given a request with no cookies, when it calls any of `GET /api/dashboard/running`, `/api/sync`, `/api/training-plan`, `/api/training-load`, `/api/race-day-form`, `/api/vo2max`, `/api/vo2max/normalized`, `/api/long-run-drift`, `/api/efficiency-factor`, `/api/marathon-pace`, `/api/wellness`, `/api/weekly-summary` or `/api/run-weather`, then the status is not 401. It is the same status and body as before for the same stored data and config.
- **AC1.2** Given an empty store (no runs, no tokens), when `GET /api/dashboard/running` is called, then it returns 200 with the normal empty-weeks shape and not `not_connected`.
- **AC1.3** Given the app is running, when `GET /auth/strava/login` or `/auth/strava/callback` is requested, then the response is 404 and no `Set-Cookie` for `sd_session` or `sd_oauth_state` is sent.
- **AC1.4** Given `X-Person: marina` and no Marina config (`MARINA_DB_PATH` unset), when any `/api/...` route is called, then the response is 404 `{"error":"person_not_configured","message":"This person is not set up."}`.

### R2: Frontend opens without login
- **AC2.1** Given the app routes, when navigating to `/connect`, then the router redirects to `/` (the `**` fallback) and no connect page exists.
- **AC2.2** Given an API call that fails with 401 (or any status), when the interceptor chain runs, then no navigation happens and the card shows its normal error state.
- **AC2.3** Given `proxy.conf.json`, when read, then it proxies only `/api`.

### R3: Garmin activity list sync
- **AC3.1** Given a Garmin list response with a `running` activity started 2026-11-10 07:30 local, when a sync runs, then a run is stored with `id = activityId`, `sportType = "Run"`, `name = activityName`, `startLocal = startTimeLocal`, `startUtc = startTimeGMT` (as UTC), `distance = distance` m, `averageHeartRateBpm = averageHR`, `trainer = false`.
- **AC3.2** Given a `treadmill_running` activity, when synced, then it is stored with `trainer = true`. Given `trail_running`, then `trainer = false`.
- **AC3.3** Given the list call, when the adapter sends it, then the query has `sortOrder=asc` (no `activityType`, see Design), `start=(page-1)*perPage`, `limit=perPage`, `startDate` (the local date of `after`) and `endDate` (today + 1 day, app zone), with `Authorization: Bearer <token>`.
- **AC3.4** Given a list response containing an activity whose `startTimeLocal` date is before the cut-over date, when synced, then it is not stored.
- **AC3.5** Given a new run uploaded to Garmin after the last sync, when the next scheduled sync runs (no manual step), then the run appears in `GET /api/dashboard/running` weekly totals and in the calendar.
- **AC3.6** Given an activity with `activityName` null, when stored, then `name` is `""`.

### R4: Detail from activity JSON + FIT
- **AC4.1** Given `GET /activity-service/activity/{id}` returns `description: "6x800m @ 3:45"`, when the detail step runs, then the stored run's `description` equals that text exactly, and `RunClassifier`, the workout/treadmill description lines and the marathon-pace counting give the same result they give for an identical Strava run.
- **AC4.2** Given `summaryDTO.movingDuration = 2712.6`, when the detail is stored, then `movingTime = 2713` s (half-up). Given `movingDuration` null, then `summaryDTO.duration` is used instead. Given both null, then `0` is used.
- **AC4.3** Given a FIT file with 3 lap messages, when the detail is stored, then 3 laps are saved, `index` 1..3 in file order, `elapsed = round(total_elapsed_time)` s, `distance = total_distance` m (0 when absent), `averageHeartRateBpm = avg_heart_rate` (null when absent).
- **AC4.4** Given FIT records covering 2.5 km with cumulative `distance`, when the detail is stored, then 3 splits are saved (index 1..3; the third one is about 500 m). Each has `distance` = cumulative distance at its closing record minus at the previous boundary. `movingTime` is the sum of record-to-record time deltas where distance increased. `averageHeartRateBpm` is the time-weighted mean over those deltas (null when no HR). `elevationDifference` = altitude at the closing record minus at the previous boundary (`enhanced_altitude`, else `altitude`; null when either is missing), `gradeAdjustedSpeed = null`, so feature 016's hill adjustment applies (approved with the spec, 2026-11-09).
- **AC4.5** Given the FIT download returns 404 (e.g. a manual activity) or a file that fails to decode, when the detail is stored, then laps and splits are empty lists, description and moving time are still saved, and the run is no longer pending detail.
- **AC4.6** Given the activity JSON returns 404, when the detail step runs, then the run is deleted locally (existing behaviour for a deleted activity).

### R5: Streams from FIT records
- **AC5.1** Given FIT records with `timestamp` and `enhanced_speed` (or `speed` when enhanced is absent), when `speedStream` is called, then `times` are whole seconds since the first record and `speeds` are m/s. Records without speed are skipped.
- **AC5.2** Given records with `heart_rate`, when `heartRateStream` is called, then it returns those samples. Given fewer than 2 samples with HR, then it returns `NotFound`.
- **AC5.3** Given records with both speed and HR, when `driftStream` is called, then it returns those samples with `moving = null`. Given fewer than 2, then `NotFound`.
- **AC5.4** Given a Garmin run that is a long run, an outdoor workout and has HR, when syncs complete, then the stored run has `streamPace`, `hrHistogram` and `hrDrift` filled in, and `GET /api/long-run-drift`, `/api/vo2max`, `/api/vo2max/normalized` and `/api/efficiency-factor` include it.
- **AC5.5** Given a FIT file whose records carry `position_lat`/`position_long`, when it is parsed, then the parsed model has no position values (the type has no such field) and no log line contains coordinates.
- **AC5.6** Given the detail, speed, HR and drift calls for the same activity within one process, when they run, then the FIT file is downloaded once (in-memory cache of parsed FIT data, at most 8 activities).

### R6: Strava history preserved, no duplicates
- **AC6.1** Given stored runs dated before the cut-over that still look pending (no detail, no HR histogram, etc.) or fall in the 7-day recheck window, when a sync runs, then no source call is made for them and their rows (including laps, splits, histograms) are unchanged.
- **AC6.2** Given only pre-cut-over runs are pending, when `GET /api/sync` is called, then `runsPendingDetail` counts only runs on or after the cut-over (0 here) and the status is not `partial`.
- **AC6.3** Given Garmin returns an activity before the cut-over that duplicates a stored Strava run, when synced, then it is not stored and the run count does not change.
- **AC6.4** Given a fresh or existing store, when `listAfter` is computed, then it is never earlier than the cut-over date's start of day in the app zone.

### R7: Per-person cut-over and not-configured state
- **AC7.1** Given `GARMIN_ACTIVITIES_FROM` unset, when config loads, then Ricardo's cut-over is 2026-11-10. Given `MARINA_GARMIN_ACTIVITIES_FROM=2026-11-12`, then Marina's is 2026-11-12. The two are independent.
- **AC7.2** Given `GARMIN_ACTIVITIES_FROM=11/09/2026` (not ISO), when config loads, then startup fails with `GARMIN_ACTIVITIES_FROM must be an ISO date (YYYY-MM-DD)`.
- **AC7.3** Given a person without a Garmin API key, when `GET /api/sync` or `POST /api/sync` is called, then the response is 200 `{"status":"notConfigured","lastSyncedAt":null,"retryAfter":null,"runsPendingDetail":0,"nextSyncInSeconds":null}`, no Garmin call is made, and the dashboard endpoints still return their cached runs.
- **AC7.4** Given the sync banner receives `notConfigured`, when rendered, then it shows "Garmin Connect isn't set up for this person. Showing saved runs." and the sync store schedules no further requests.
- **AC7.5** Given Marina is selected and configured with her own key, when the dashboard loads, then her runs come from her Garmin account and her store, and Ricardo's are not shown (019 behaviour).
- **AC7.6** Given `MARINA_DB_PATH` is set and no `MARINA_STRAVA_*` keys are set, when config loads, then Marina is configured.

### R8: Errors
- **AC8.1** Given any Garmin call returns 429 with `Retry-After: 120`, when the sync runs, then it stops, the status is `rateLimited` with `retryAfter` = now + 120 s, and no further call is made before then. Given no or invalid `Retry-After`, then the wait is 15 minutes.
- **AC8.2** Given Garmin returns 401/403 with a cached token, when a call is made, then the token is refreshed once and the call retried. If it is rejected again, then the sync records `garminUnavailable`. The key is never logged.
- **AC8.3** Given a network failure, timeout or 5xx, when the sync runs, then status `garminUnavailable`, cached data is still served, and only the call name and status or exception class are logged.
- **AC8.4** Given each status, when the banner renders, then the texts are: `syncing` "Syncing with Garmin Connect…", `rateLimited` "Garmin Connect rate limit reached. Showing saved data; syncing resumes after HH:mm.", `garminUnavailable` "Couldn't sync with Garmin Connect. Showing data saved at d MMM y, HH:mm." (or "… No data saved yet."), `partial` "Still importing runs from Garmin Connect (N remaining). This continues automatically.", failed check "Couldn't check the Garmin Connect sync. Showing saved data.", `ok` no banner.

### R9: Strava removed
- **AC9.1** Given the repo, when searched, then `backend/src/main/kotlin/app/stravadashboard/strava/` and `auth/` do not exist, and no main source reads `STRAVA_CLIENT_ID`, `STRAVA_CLIENT_SECRET`, `STRAVA_REDIRECT_URI`, `SESSION_SECRET`, `MARINA_STRAVA_CLIENT_ID` or `MARINA_STRAVA_CLIENT_SECRET`, or contains `strava.com`.
- **AC9.2** Given the env has only `DB_PATH` (plus optional keys), when the app starts, then it starts. Leftover `STRAVA_*` and `SESSION_SECRET` values are ignored.
- **AC9.3** Given the code, when searched case-insensitively for `strava` outside the package/app name `stravadashboard`, the page title "Strava Dashboard" and the footer (see Risks), then there are no matches in `backend/src/main`, `frontend/src/app` (non-test), `.env.example` or `README.md` setup steps.
- **AC9.4** Given an existing DB file with a `strava_tokens` row and `sync_state.owner_athlete_id`, when the app starts and syncs, then the app neither reads nor modifies these, and the file keeps its tables.

## Traceability
| Success criterion (intent) | Covered by |
|---|---|
| S1 (opens without login in a new browser) | R1, R2 |
| S2 (new Garmin run appears automatically) | R3, R4, R8 |
| S3 (Strava history unchanged, no duplicates) | R6, R3 (AC3.4) |
| S4 (description drives parsing as before) | R4 (AC4.1) |
| S5 (drift, VO2max, rep pace, EF, normalized VO2max) | R4, R5 |
| S6 (person switch, own Garmin data) | R7 |
| S7 (no Strava code/routes/env) | R9, R1 |

## Design

### Data from Garmin Connect
Base `https://personal.connectapi.garmin.com`, per-person API key exchanged for a Bearer token exactly as in `GarminWellnessSource` (same token endpoint, 24 h lifetime minus 5 min, one retry with a fresh token on 401/403).

- **List:** `GET /activitylist-service/activities/search/activities?start=&limit=&startDate=&endDate=&sortOrder=asc`. (No `activityType`: whether Garmin's parent-type filter includes treadmill and trail runs is undocumented, so the adapter filters types itself.) `page`/`perPage` from the port map to `start=(page-1)*perPage`, `limit=perPage`. An empty array ends the pass, and so does a page shorter than `perPage` (existing `listPass` loop).
  Fields used: `activityId`, `activityName`, `startTimeLocal`, `startTimeGMT`, `activityType.typeKey`, `distance`, `averageHR`.
  Type mapping: `running`, `treadmill_running`, `trail_running` → `sportType = "Run"` (`trainer` true only for `treadmill_running`). Any other key → `sportType = typeKey` (the existing `listPass` then doesn't store it).
  Dates: Garmin sends `"yyyy-MM-dd HH:mm:ss"` (possibly with fraction). Parse with `LocalDateTime.parse(text.replace(' ', 'T'))`. `startTimeGMT` → `toInstant(ZoneOffset.UTC)`. `startTimeLocal` is used as-is for day bucketing.
- **Detail:** `GET /activity-service/activity/{id}`. Fields: `description`, `summaryDTO.movingDuration`, `summaryDTO.duration`. 404 → `SourceResult.NotFound`.
- **FIT file:** `GET /download-service/files/activity/{id}` (send `Accept: */*` and exclude JSON, as the token call does). The body is a zip. Take the first entry whose name ends in `.fit` (case-insensitive) and stop reading after 32 MB uncompressed (zip-bomb guard → treat as decode failure). Decode with the FIT SDK (`com.garmin:fit`, `FitDecoder`/`Decode`), reading only:
  - `lap`: `total_elapsed_time`, `total_distance`, `avg_heart_rate`
  - `record`: `timestamp`, `distance`, `enhanced_speed`/`speed`, `heart_rate`, `enhanced_altitude`/`altitude`
  Nothing else is read. The raw bytes are discarded after decoding, never written to disk and never logged.
- **Calls per port method:** `activityDetail` = detail JSON + FIT (FIT 404 or decode failure → empty laps/splits). `speedStream`/`heartRateStream`/`driftStream` = FIT only (404 or decode failure → `NotFound`).
- **FIT cache:** the adapter keeps parsed `FitData` (laps, splits, samples, no positions) for the 8 most recently used activity IDs in an access-ordered `LinkedHashMap`. A FIT file doesn't change after upload. This keeps the 4 call types and the hourly 7-day rechecks to one download per run per process. In memory only.
- **Sync & caching:** unchanged `SyncService`/`BackgroundSync`/`SyncSchedule`. Sync every 15 min while a dashboard is open, incremental with the 7-day re-list window, the per-15-min call budget of 80 (one unit per port call), 80 activity calls per sync.
- **Errors:** 429 → `SourceResult.RateLimited(now + Retry-After s, 1..86400, else 15 min)`. 401/403 → refresh the token once, then `Unavailable`. 404 → `NotFound`. IO/timeout/serialization/other status → `Unavailable`. Logs carry the call name (`token`, `list`, `detail`, `fit`) and the status or exception class only.
- **Scopes:** n/a (Personal API key, same as 015).

### Shared Garmin plumbing (garmin package)
Move the token cache plus `request`/`map` logic out of `GarminWellnessSource` into one internal class `GarminConnect(http, apiKey, clock)`. It exposes `suspend fun <T> getJson(name, block): GarminCall<T>` and `suspend fun getBytes(name, block): GarminCall<ByteArray>`. `GarminCall` is a sealed type: `Ok`, `NotFound`, `KeyRejected`, `RateLimited(until)`, `Unavailable`. `GarminWellnessSource` maps it to `WellnessFetchResult` (404 → `Unavailable`, as today), and `GarminActivitySource` maps it to `SourceResult`. Each person gets one `GarminConnect` (one HttpClient, one token) when their key is set. Wellness and activity calls run one after the other inside the same background job, so the token cache needs no lock. Existing wellness tests must pass unchanged.

### Domain model & logic
- **Port `ActivitySource`:** unchanged signatures. Remove `SourceResult.ReauthRequired` and update the KDoc that names Strava stream keys.
- **`SyncService(..., activitiesFrom: LocalDate)`:**
  - `listPass` drops listed summaries with `startLocal.toLocalDate() < activitiesFrom` before `upsertSummaries`/`delete`.
  - `SyncPolicy.listAfter(state, now, zone, activitiesFrom)` returns `maxOf(<current rule>, activitiesFrom.atStartOfDay(zone).toInstant())`.
  - `SyncPolicy.pendingCount(all, activitiesFrom)` and `SyncPolicy.plan(phase, all, now, activitiesFrom)` only consider runs with `localDate >= activitiesFrom`. `BackgroundSync` passes the same date to `pendingCount`.
  - `SyncOutcome.ReauthRequired` and `BackgroundSync.reauthRequired` are removed.
- **Renames (S7):** `StravaCallBudget` → `CallBudget`, `SyncStatus.STRAVA_UNAVAILABLE` → `SOURCE_UNAVAILABLE`. Log texts change "Strava sync" → "Activity sync". A stored `last_status = 'STRAVA_UNAVAILABLE'` reads as null via the existing `statusOf` lookup, which is harmless.
- **Per-km splits (garmin package, pure function `kmSplits(samples)`):** walk records that have a distance. A split closes at the first record whose cumulative distance ≥ `index × 1000 m`. The last split closes at the final record if its distance > 0 and moving time > 0. A delta counts as moving when distance increased over it. HR is time-weighted over moving deltas using the later record's HR. `index` starts at 1.
- **Laps:** FIT lap order, `index` 1-based. Seconds are rounded half-up (`roundHalfUpToInt`).
- **Samples:** `seconds = timestamp − firstRecordTimestamp` (whole seconds), `speed = enhanced_speed ?: speed` m/s, `heart_rate` bpm. At least 2 samples are needed per stream (same rule as the Strava mappers).
- **Services without `NotConnected`:** `RunningDashboardService`, `TrainingLoadService`, `RaceDayFormService`, `Vo2maxService`, `NormalizedVo2maxService`, `LongRunDriftService`, `EfficiencyFactorService`, `MarathonPaceService`, `WeeklySummaryService` lose the `tokens` parameter and the `NotConnected` result variant. Their API mappers and routes drop the 401 branch.
- Units and time zones are unchanged: metres, m/s, seconds in the domain, day bucketing on `startLocal`.

### Persistence
- No new tables or columns. Garmin `activityId` goes into `runs.id` like Strava IDs did.
- `StravaTokensTable` is removed from code and from `SchemaUtils.create`. The existing `strava_tokens` table and rows stay in the file, untouched (Decision 7).
- `SyncState.ownerAthleteId` is removed from the domain and from `SyncStateTable`'s Exposed definition. The column stays in existing files, unread and unwritten, and the upsert doesn't touch it.
- `ExposedTokenRepository`, the `TokenRepository` port and `StoredTokens` are deleted.

### Config
- **Removed:** `STRAVA_CLIENT_ID`, `STRAVA_CLIENT_SECRET`, `STRAVA_REDIRECT_URI`, `SESSION_SECRET`, `MARINA_STRAVA_CLIENT_ID`, `MARINA_STRAVA_CLIENT_SECRET`.
- **Added:** `GARMIN_ACTIVITIES_FROM`, `MARINA_GARMIN_ACTIVITIES_FROM`. Both optional ISO dates of any weekday, default `2026-11-10`. An invalid value fails startup with `<KEY> must be an ISO date (YYYY-MM-DD)`.
- `PersonSettings` drops the Strava fields and gains `garminActivitiesFrom: LocalDate`.
- Marina is configured when `MARINA_DB_PATH` is set (her required group shrinks to that one key).
- `AppConfig.secureCookies` (derived from the redirect URI) is removed. `XsrfProtection` is installed with `secureCookies = false`: the app is served over plain HTTP on the home network.
- `toString` keeps redacting the Garmin keys.

### Backend API
No new endpoints. No route requires a session, and none returns 401.

- **Removed:** `GET /auth/strava/login`, `GET /auth/strava/callback`.
- **`GET /api/sync`, `POST /api/sync`** (POST still needs a valid `X-XSRF-TOKEN`, else 403 `xsrf_invalid`). Header `X-Person` as in 019. Response:
```json
{
  "status": "syncing | ok | partial | rateLimited | garminUnavailable | notConfigured",
  "lastSyncedAt": "2026-11-10T08:15:00Z | null",
  "retryAfter": "2026-11-10T08:30:00Z | null",
  "runsPendingDetail": 0,
  "nextSyncInSeconds": 900
}
```
  `lastSyncedAt` and `retryAfter` are ISO-8601 UTC strings or null. `runsPendingDetail` is an int that counts only runs on or after the cut-over. `nextSyncInSeconds` is an int (seconds) or null; it is null while `syncing` and for `notConfigured`. `stravaUnavailable` is replaced by `garminUnavailable`. `notConfigured` always has the fixed shape in AC7.3.
  POST also starts the other person's sync when that person has a Garmin key (`OtherPersonSync.startIfConfigured`, replacing `startIfConnected`).
- **Every other `/api/...`:** request and response unchanged. The 401 `not_connected` and `strava_reauth_required` errors no longer exist. `ErrorResponse.NOT_CONNECTED` and `REAUTH_REQUIRED` are deleted, and `PERSON_NOT_CONFIGURED = {"error":"person_not_configured","message":"This person is not set up."}` (404) is added for the Marina-unconfigured branch in `Application.kt`.

### Frontend (Angular)
- **Routes:** delete the `connect` route and `features/connect/`. `''` → `DashboardShell`, `**` → `''`.
- **Interceptors:** delete `api-error.interceptor.ts` (+ spec); its only job was 401 → `/connect`. `app.config.ts` keeps `personInterceptor` and `withXsrfConfiguration({})`.
- **401 special cases:** remove the `status === 401` branches in the view helpers and pages listed below, so any HTTP error gives the card's error state.
- **`core/api/sync.models.ts`:** `SyncStatus = 'syncing' | 'ok' | 'partial' | 'rateLimited' | 'garminUnavailable' | 'notConfigured'`. `STATUSES` is updated to match. The interface is otherwise unchanged and matches the JSON above.
- **`SyncStore.receive`:** on `notConfigured`, set the status and return without scheduling anything.
- **States (sync banner, the only UI change):** loading = no banner (`'none'`); error = "Couldn't check the Garmin Connect sync. Showing saved data."; empty/not configured = the `notConfigured` text (AC7.4); normal = the texts in AC8.4. All other cards keep their existing loading/empty/error/normal states. Their empty states now also cover "no runs yet after the cut-over".
- **Charts:** unchanged.
- **`proxy.conf.json`:** only `/api`.

### Files expected to change
Backend (`backend/src/main/kotlin/app/stravadashboard/`)
- **Delete:** `strava/` (all files), `auth/` (all files), `domain/ports/TokenRepository.kt`, `persistence/ExposedTokenRepository.kt`
- **Add:** `garmin/GarminConnect.kt` (token + request plumbing), `garmin/GarminActivitySource.kt`, `garmin/GarminActivityDtos.kt`, `garmin/GarminFit.kt` (unzip + decode → `FitData`, `kmSplits`)
- **Change:**
  - `garmin/GarminWellnessSource.kt` (use `GarminConnect`)
  - `Application.kt` (no sessions/auth routes/Strava client; 404 `person_not_configured`; `XsrfProtection` secure=false)
  - `PersonStack.kt` (wire `GarminConnect` once per person; `SyncService(GarminActivitySource…, activitiesFrom)`; no tokens/owner/connect; `BackgroundSync` null → `notConfigured`)
  - `config/AppConfig.kt`, `config/PersonSettings.kt`
  - `domain/ports/ActivitySource.kt`, `domain/ports/SyncStateRepository.kt`, `domain/SyncService.kt`, `domain/SyncPolicy.kt`, `domain/SyncSchedule.kt`, `domain/BackgroundSync.kt`
  - `domain/StravaCallBudget.kt` → `domain/CallBudget.kt`
  - `domain/longrundrift/DriftStreamFetch.kt`
  - the 9 services named above
  - `api/*Routes.kt` (13 route files: drop `tokens` + guard), `api/*Mappers.kt` where they map `NotConnected`, `api/SyncRoutes.kt`, `api/SyncMappers.kt`, `api/ErrorResponse.kt`
  - `persistence/Tables.kt`, `persistence/DatabaseFactory.kt`, `persistence/ExposedSyncStateRepository.kt`
- **Build:**
  - `backend/build.gradle.kts`, `backend/gradle/libs.versions.toml`: add `com.garmin:fit` (latest release on Maven Central, pinned); remove `ktor-server-sessions`, and `ktor-client-cio` if nothing else uses it.
- **Tests:**
  - delete Strava/auth tests and `src/test/resources/strava/`
  - update every route/service test that seeded tokens or a session
  - add Garmin fixtures under `src/test/resources/garmin/` (invented JSON); FIT fixtures are built in the test with the FIT SDK `FileEncoder`

Frontend (`frontend/`)
- **Delete:** `src/app/features/connect/` (+ spec), `src/app/core/interceptors/api-error.interceptor.ts` (+ spec)
- **Change:** `src/app/app.routes.ts`, `src/app/app.config.ts`, `src/app/core/api/sync.models.ts`, `src/app/core/sync/sync.store.ts` (+ spec), `src/app/features/running-dashboard/sync-banner/sync-banner.ts` (+ spec), `proxy.conf.json`
- **401 branches removed:** `features/marathon-pace/marathon-pace.view.ts`, `features/training-load/training-load.view.ts`, `features/efficiency-factor/efficiency-factor.view.ts`, `features/long-run-drift/long-run-drift.view.ts`, `features/wellness/wellness.view.ts`, `features/race-day-form/race-day-form.view.ts`, `features/calendar-like-build/plan-view.ts`, `features/vo2max/vo2max.view.ts`, `features/weekly-summary/weekly-summary-bell.ts`, `features/running-dashboard/running-dashboard-page.ts` (+ their specs that assert 401 behaviour)

Docs/config
- `.env.example` (remove Strava/session keys, add the two `*_GARMIN_ACTIVITIES_FROM` keys with no values, note `HOST` for LAN use)
- `README.md` (setup without Strava)
- `CLAUDE.md` mentions the Strava OAuth/BFF pattern. Flag to the user; don't edit without approval.

## Edge cases
| Case | Expected behaviour |
|---|---|
| No activities in range | List returns `[]` → pass completes, status `ok`, cards show their existing empty states. |
| No Garmin runs yet after cut-over, Strava history present | Charts show Strava runs; nothing pending; status `ok`. |
| Manual activity (no GPS / no FIT file) | FIT 404 → laps/splits empty, streams `NotFound` (stream pace/HR histogram/drift stored as unavailable/no-data, as for Strava runs without streams). |
| Activity without heart rate | `averageHR` null → no HR stream/drift calls (existing policy). Laps/splits HR null. |
| Treadmill run | `trainer = true`. Speed from the FIT records (watch or foot-pod estimate). Splits stored but not used for outdoor-only metrics (existing rules). |
| FIT without `enhanced_speed` | Falls back to `speed`. Neither present → speed/drift `NotFound`. |
| FIT records without `distance` | Splits empty list. |
| Corrupt FIT / zip without `.fit` / > 32 MB | Treated as decode failure (AC4.5). The class name is logged, no content. |
| Activity changes type off running within the 7-day window | No longer listed (type filter). It stays stored until a detail recheck (shortcut, see Risks). |
| Activity deleted on Garmin | Detail JSON 404 during recheck → deleted locally. |
| Run on the cut-over day also synced from Strava before deploy | Would duplicate (see Risks). The cut-over must be after the last Strava sync. |
| Time zones / DST | Day/week bucketing uses `startTimeLocal`. `startUtc` from `startTimeGMT`. `startDate`/`endDate` of the list are local dates and overlap is harmless (upsert). |
| Garmin 429 mid-sync | Stops. `rateLimited` with retry time. Already stored data is kept. |
| Units | Distance m (km in DTOs via existing mappers), speed m/s (pace via existing domain), durations s rounded half-up. |
| Person without Garmin key | `notConfigured`, cached runs shown, wellness card shows its existing "not connected" state. |
| Marina not configured at all | 404 `person_not_configured` on her `/api` routes → cards show error state. |

## Privacy
- **Data fields & sensitivity:**
  - Garmin activity ID, name, description (user text), start times, distance, average HR, moving time, laps (time/distance/HR), per-km splits (distance/time/HR), per-second speed + HR samples (transient). HR is health data (Art. 9).
  - FIT files contain GPS. Position fields are never read: the decoder output is mapped field-by-field into a model with no position fields, and raw bytes are discarded after decoding.
- **Storage & retention:**
  - Same SQLite tables and pruning as today (6 months + 41 days). Per-second samples live only in memory: in the 8-entry FIT cache and during a sync, never persisted.
  - Old `strava_tokens` rows stay in the DB file (Decision 7). They are still valid Strava credentials until revoked.
- **Scopes:** none (Garmin Personal API key per person, backend env only).
- **Applicable privacy-check items:**
  - **1:** ✅ no keys in repo; test fixtures invented.
  - **2:** ✅ the Garmin key/token stay in the backend and are never sent to the browser.
  - **3, 4, 5:** n/a (no OAuth any more).
  - **6:** ✅ only the listed fields are stored; no raw responses.
  - **7:** ✅ GPS never read or stored.
  - **8:** ✅ logs carry call name + status/exception class, activity counts or IDs; never key, token, URL query, body, HR series or coordinates.
  - **9:** ✅ no Ktor `Logging` plugin on the Garmin client.
  - **10:** ✅ unchanged `StatusPages`.
  - **11:** ❌ by decision: no authentication. Accepted by Ricardo for home-network use only. `HOST` default stays `127.0.0.1`; serving on the LAN needs an explicit `HOST`.
  - **12:** n/a (no session cookie).
  - **13:** ✅ descriptions/names still rendered by interpolation only.
  - **14:** ✅ `POST /api/sync` keeps XSRF double-submit.
  - **15:** ✅ DTOs unchanged; no raw Garmin objects.
  - **16:** ✅ the FIT SDK is a bundled Maven dependency with no network calls.
  - **17:** ✅ Garmin data stays local.
  - **18:** ❌/n/a: no in-app delete. Deleting the DB file is the way, as before.
  - **20:** ✅ unchanged.
  - **21:** ✅ invented fixtures; FIT fixtures generated in tests.

## Test plan
| AC | Test type | Test name |
|---|---|---|
| AC1.1 | backend route (testApplication, no cookies) | `R1 - every api route answers without a session` |
| AC1.2 | backend route | `R1 - empty store returns empty dashboard not 401` |
| AC1.3 | backend route | `R1 - auth routes are gone` |
| AC1.4 | backend route | `R1 - unconfigured Marina gets 404 person_not_configured` |
| AC2.1 | frontend routes | `R2 - /connect redirects to the dashboard` |
| AC2.2 | frontend component (e.g. training-load section) | `R2 - a 401 shows the error state and does not navigate` |
| AC2.3 | n/a (config review) | n/a |
| AC3.1, AC3.2, AC3.6 | backend client (MockEngine) | `R3 - list maps running types to Run summaries` |
| AC3.3 | backend client | `R3 - list query uses start limit dates and running filter` |
| AC3.4, AC6.3 | backend unit (SyncService, fake source) | `R3 - activities before the cut-over are not stored` |
| AC3.5 | backend route + fake source | `R3 - a new Garmin run appears in weekly totals after a sync` |
| AC4.1 | backend unit | `R4 - Garmin description classifies like a Strava description` |
| AC4.2 | backend client | `R4 - moving time from movingDuration with duration fallback` |
| AC4.3 | backend unit (FIT built with FileEncoder) | `R4 - laps from FIT lap messages` |
| AC4.4 | backend unit | `R4 - per-km splits from FIT records` |
| AC4.5 | backend client | `R4 - missing or corrupt FIT gives empty laps and splits` |
| AC4.6 | backend unit | `R4 - detail 404 deletes the run` (existing test, adapted) |
| AC5.1–AC5.3 | backend unit | `R5 - speed HR and drift streams from FIT records` |
| AC5.4 | backend integration (SyncService + GarminActivitySource + MockEngine + in-memory SQLite) | `R5 - a Garmin long workout gets stream pace histogram and drift` |
| AC5.5 | backend unit + log capture | `R5 - positions in FIT are ignored and never logged` |
| AC5.6 | backend client | `R5 - FIT is downloaded once for detail and streams` |
| AC6.1 | backend unit | `R6 - runs before the cut-over get no source calls and stay unchanged` |
| AC6.2 | backend unit | `R6 - pending count ignores runs before the cut-over` |
| AC6.4 | backend unit (SyncPolicy) | `R6 - listAfter is never before the cut-over` |
| AC7.1, AC7.2, AC7.6 | backend unit (AppConfig) | `R7 - cut-over dates per person with default and validation` |
| AC7.3 | backend route | `R7 - sync without Garmin key reports notConfigured` |
| AC7.4 | frontend service + component | `R7 - notConfigured shows banner and stops polling` |
| AC7.5 | backend route | `R7 - each person syncs from their own Garmin key` |
| AC8.1 | backend client + unit | `R8 - 429 maps to rateLimited with Retry-After` |
| AC8.2 | backend client | `R8 - rejected token is refreshed once then unavailable` |
| AC8.3 | backend client | `R8 - network failure maps to garminUnavailable` |
| AC8.4 | frontend pure function | `R8 - sync banner texts name Garmin Connect` |
| AC9.1, AC9.3 | review (grep) | n/a |
| AC9.2 | backend unit (AppConfig) | `R9 - starts with only DB_PATH and ignores Strava keys` |
| AC9.4 | backend persistence (SQLite file with strava_tokens row) | `R9 - legacy strava_tokens and owner column are left untouched` |
| regression | backend | existing `GarminWellnessSource` tests pass on `GarminConnect` |

## Out of scope
- Login page, user accounts or any authentication.
- Deleting or migrating Strava-cached data. Old token rows and the owner column stay.
- GPS, map or route features. GPS from FIT is never stored or logged.
- New charts or metrics; Garmin-only metrics (Garmin VO2max, training effect).
- Strava as a fallback source.
- Computing grade-adjusted speed (Decision 5). Garmin splits carry the elevation difference instead, and 016's existing hill adjustment uses it.

## Suggested follow-ups
- Revoke the dashboard's Strava app at strava.com/settings/apps, and optionally drop the `strava_tokens` table, since the stored tokens remain valid credentials.
- A `SECURE_COOKIES` setting if the app is ever served over HTTPS.
- Rename the project/package away from "strava" (`app.stravadashboard`, page title).

## Risks & decisions to confirm
- **Cut-over day:** default 2026-11-10, the day after the last Strava sync, so no run is imported twice.
- **Grade-adjusted speed:** the FIT profile has none. Splits carry the elevation difference, so 016's hill adjustment applies (approved).
- **Pruning still applies:** "Strava history stays" means "is not touched by the Garmin sync". The existing 6-month + 41-day pruning still removes old runs from either source, as before. Confirm this is intended.
- **Footer "Powered by Strava":** left in place while cached Strava runs are displayed (AC9.3 exempts it). Confirm keep or remove.
- **XSRF cookie** loses the `Secure` flag (no HTTPS signal left). Fine on the home network.
- **Garmin API details not verified live:** whether `limit` above 100 is honoured (perPage is 200, but the 7-day window never gets close), the `startTimeGMT` text format, and whether `activityType=running` includes treadmill and trail children by default. The first test against the real API should confirm all three.
- **Garmin rate limits are unknown.** The existing 80-calls/15-min budget plus the FIT cache keep traffic low (about one list call per sync and one FIT download per new run).
- **ID collision** between a new Garmin ID and a cached Strava ID would overwrite that row. This is very unlikely, so there is no mitigation.
- **CLAUDE.md, code-standards and privacy-check** still describe the Strava OAuth/BFF pattern. Updating them is outside this spec; flagging for Ricardo.
- **New dependency:** `com.garmin:fit` (approved in the intent constraints); the PR needs to give the reason.
- **Shortcut:** an activity whose type changes away from running stays stored until pruning removes it (the detail recheck does not look at the type).
