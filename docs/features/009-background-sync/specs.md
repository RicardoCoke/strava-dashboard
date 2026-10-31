# Spec: Background sync (fast dashboard loading)

**Feature ID:** 009 · **Status:** Draft · **Intent:** [intent.md](./intent.md) · **Date:** 2026-10-31

## Summary
The three read endpoints (`/api/dashboard/running`, `/api/training-load`, `/api/vo2max`) stop calling Strava and answer from the local SQLite store only. Two new endpoints, `GET /api/sync` (status) and `POST /api/sync` (start if due), run the existing `SyncService` as a background job in the backend. While the dashboard is open, a page-scoped Angular `SyncStore` starts, polls and schedules syncs, drives the sync banner, and tells the three cards to refresh quietly after each sync finishes. A new budget limits Strava calls to 80 per Strava 15-minute window, so a large backlog keeps importing batch after batch without hitting the rate limit.

## Requirements
| ID | Requirement | Priority |
|---|---|---|
| R1 | The read endpoints answer from stored data only and never call Strava or wait for a sync | Must |
| R2 | `GET /api/sync` reports sync status; `POST /api/sync` starts a background sync if one is due and returns at once; only one sync runs at a time | Must |
| R3 | One pure rule decides when the next sync is due: 15 minutes after the last successful sync, straight away while there is a backlog (if budget allows), and later after failures | Must |
| R4 | Strava calls are limited to 80 per Strava 15-minute window, and a backlog carries on in the next window | Must |
| R5 | A failed background sync (429, Strava unreachable, revoked token, unexpected error) never affects stored data or the read endpoints, and is reported in the status | Must |
| R6 | `POST /api/sync` is CSRF-protected (XSRF double-submit cookie) | Must |
| R7 | While the dashboard page is open, the frontend starts, polls and schedules syncs from the status, and stops when the page is left | Must |
| R8 | The sync banner shows the current sync status at the top of the dashboard, including "Syncing with Strava…" and failure messages | Must |
| R9 | After each finished sync, the running cards, training load and VO2max refresh without a page reload and without showing a loading state | Must |

## Acceptance criteria

### R1 — Read endpoints never wait for Strava
- **AC1.1** Given a connected session, stored runs and a sync that is due, when `GET /api/dashboard/running`, `GET /api/training-load` or `GET /api/vo2max` is called, then it returns 200 built from the stored runs and the fake `ActivitySource` gets zero calls.
- **AC1.2** Given a background sync is running (the fake source is suspended mid-call), when any of the three read endpoints is called, then it returns before the sync finishes, using the runs stored so far.
- **AC1.3** Given `GET /api/dashboard/running` succeeds, then the response has no `sync` key. Its other top-level keys are exactly `weeklyDistance`, `heartRateTrend`, `workoutPaceComparison`, `weekComparison`.
- **AC1.4** Given no session, or a session whose athlete doesn't match the stored tokens, when a read endpoint is called, then it returns 401 `not_connected`, as before.
- **AC1.5** Given the dashboard page is loading, then the loading state shows "Loading your runs…" without the note "The first import can take a minute."

### R2 — Sync status and trigger API
- **AC2.1** Given a connected session and no running job, when `GET /api/sync` is called, then it returns 200 with the `SyncStatusResponse` shape below, starts no sync and makes no Strava calls.
- **AC2.2** Given a sync is due and no job is running, when `POST /api/sync` is called, then it returns 200 with `status: "syncing"` and `nextSyncInSeconds: null` before any Strava call completes, and a background job starts.
- **AC2.3** Given a job is running, when `POST /api/sync` is called again (same or another tab), then it returns `status: "syncing"` and no second job starts (the fake source sees one set of calls).
- **AC2.4** Given no sync is due, when `POST /api/sync` is called, then no job starts and the response equals what `GET /api/sync` returns at that moment.
- **AC2.5** Given a job is running, when `GET /api/sync` is called, then `status` is `"syncing"` and `runsPendingDetail` is the stored pending count at that moment. When the job finishes, the next `GET` returns the finished status (`ok`, `partial`, `rateLimited` or `stravaUnavailable`).
- **AC2.6** Given no session, when `GET` or `POST /api/sync` is called, then it returns 401 `not_connected`. If the last finished job ended because the Strava connection was revoked, it returns 401 `strava_reauth_required` instead.
- **AC2.7** Given the application stops while a job is running, then the job is cancelled with the application scope and nothing outlives it.

### R3 — When a sync is due
`SyncPolicy.nextDueAt(state, pending, budget, now)` returns the instant the next sync may start. A sync is due exactly when `now >= nextDueAt`. `nextSyncInSeconds` is computed from the same function, so they can't disagree.
- **AC3.1** Given the last successful sync was 14 minutes ago, no runs are pending and the last attempt succeeded, when the status is read, then a sync is not due and `nextSyncInSeconds` is 60. At 15 minutes it is due.
- **AC3.2** Given no successful sync yet (`listCursor` null), when the status is read, then a sync is due (`nextSyncInSeconds: 0`), provided the budget allows (AC4.3).
- **AC3.3** Given runs are pending and budget remains in the current window, then a sync is due straight away even if the last sync was under 15 minutes ago.
- **AC3.4** Given the last attempt ended `STRAVA_UNAVAILABLE` (or an unexpected error) at time T, then the next sync is not due before T + 15 minutes, even with runs pending.
- **AC3.5** Given `rateLimitedUntil` is in the future, then the next sync is not due before `rateLimitedUntil`, and `nextSyncInSeconds` counts down to it.
- **AC3.6** `nextSyncInSeconds` is rounded up to whole seconds, is never negative, and is `null` only while `status` is `"syncing"`.

### R4 — Strava call budget
- **AC4.1** Every Strava call the sync makes (list page, activity detail, speed streams, heart-rate streams) uses 1 unit of a budget of 80 per window. Windows are fixed 15-minute blocks of UTC epoch time (`floor(epochSecond / 900)`), the same blocks `RateLimitHeaders` uses.
- **AC4.2** Given 200 runs pending and a full budget, when one sync runs, then it makes at most 80 Strava calls in total (list pages included), in the existing priority order, and ends with status `partial`.
- **AC4.3** Given the budget for the current window is used up and runs are pending, then `nextDueAt` is the start of the next window. At that instant a sync is due and makes up to 80 more calls.
- **AC4.4** Given two syncs in the same window (for example a backlog batch at :02 that used 30 calls because the backlog ran out, then a 15-minute check), then together they make at most 80 calls in that window.
- **AC4.5** Given the budget runs out during a list pass, then the sync stops with status `partial`, the list cursor is not moved (existing rule: the cursor is only set once every page has been read), and the next window lists again.

### R5 — Failures
- **AC5.1** Given Strava returns 429 during a background sync, then the job stops, `rateLimitedUntil` is saved from the rate-limit headers (existing `RateLimitHeaders`), the status becomes `rateLimited` with `retryAfter` set, and runs already saved in that batch stay stored.
- **AC5.2** Given Strava is unreachable or returns 5xx, then the job stops, `lastStatus = STRAVA_UNAVAILABLE` and `lastAttemptAt = now` are saved, and the status becomes `stravaUnavailable`.
- **AC5.3** Given the token refresh fails (the connection was revoked), then the existing client deletes the tokens, the job ends with `ReauthRequired`, and AC2.6 applies to the next request.
- **AC5.4** Given the sync throws an unexpected exception, then the job catches it (not `CancellationException`), logs only the exception class name, saves `lastStatus = STRAVA_UNAVAILABLE` and `lastAttemptAt`, clears the running flag, and the application keeps serving requests.
- **AC5.5** Given any of AC5.1 to AC5.4 happened, when a read endpoint is called, then it returns 200 with the stored data.

### R6 — CSRF protection for `POST /api/sync`
- **AC6.1** Given a request to any `/api/*` path without an `XSRF-TOKEN` cookie, then the response sets `XSRF-TOKEN` to a random value (32 bytes from `SecureRandom`, base64url), with `Path=/`, `SameSite=Strict`, **not** HttpOnly, and `Secure` when `config.secureCookies`.
- **AC6.2** Given `POST /api/sync` without an `X-XSRF-TOKEN` header, or with a header that doesn't match the cookie (checked in constant time), then it returns 403 `xsrf_invalid` and starts no sync.
- **AC6.3** Given a matching header and cookie and a valid session, then `POST /api/sync` behaves as in R2.
- **AC6.4** Angular's existing `withXsrfConfiguration({})` sends the header. No frontend code reads the cookie itself.

### R7 — Frontend sync orchestration (`SyncStore`)
- **AC7.1** Given the dashboard page opens, then `SyncStore` calls `GET /api/sync` once, at the same time as the three card requests and without blocking them.
- **AC7.2** Given a status with `nextSyncInSeconds: n` (not syncing), then the store waits `max(n, 1)` seconds and calls `POST /api/sync`. With `n = 0` it posts after 1 s. No `POST` is sent before then.
- **AC7.3** Given a status of `"syncing"` (from `GET` or `POST`), then the store calls `GET /api/sync` every 5 seconds until the status isn't `"syncing"`, and then schedules as in AC7.2.
- **AC7.4** Given the last sync was 5 minutes ago and nothing is pending (`nextSyncInSeconds: 600`), when the page is reloaded, then no `POST` is sent within the first 600 s (S4).
- **AC7.5** Given the page stays open with nothing pending, then a `POST /api/sync` is sent about every 15 minutes, each one scheduled from the previous finished status (S5).
- **AC7.6** Given a `GET` or `POST /api/sync` fails with a non-401 error (network or 5xx), then the store sets an error flag and calls `GET /api/sync` again after 60 s. A 401 goes to the existing interceptor, which navigates to `/connect`, and the store stops.
- **AC7.7** Given the user leaves the dashboard page, then all pending timers and requests of the store are cancelled and no more `/api/sync` requests are sent.

### R8 — Sync banner
The banner sits directly under the `<h1>Running</h1>` heading and does not depend on the running-cards state. Texts (exact):

| Store state | Banner text |
|---|---|
| No status received yet | *(no banner)* |
| `syncing` | `Syncing with Strava…` |
| `partial` | `Still importing runs from Strava ({n} remaining). This continues automatically.` |
| `rateLimited` | `Strava rate limit reached. Showing saved data; syncing resumes after {HH:mm}.` (`retryAfter` in browser local time; `a while` if null) |
| `stravaUnavailable` | `Couldn't sync with Strava. Showing data saved at {d MMM y, HH:mm}.` (`lastSyncedAt` in browser local time) or `Couldn't sync with Strava. No data saved yet.` |
| Status request failed (AC7.6) | `Couldn't check the Strava sync. Showing saved data.` |
| `ok` | *(no banner)* |

- **AC8.1** Given the store's status is `syncing`, then the banner shows exactly `Syncing with Strava…` with `role="status"`.
- **AC8.2** Given each row of the table above, then the banner shows that exact text, or no banner.
- **AC8.3** Given the running-cards request is still loading or has failed, then the banner still shows the sync status.
- **AC8.4** Given the status changes from `syncing` to `ok`, then the banner disappears without a reload.

### R9 — Quiet refresh after a sync
- **AC9.1** Given the store sees the status change from `syncing` to anything else, then it increments `dataVersion` once.
- **AC9.2** Given `dataVersion` increments, then the running dashboard page, `TrainingLoadSection` and `Vo2maxSection` each re-request their endpoint once, keep showing their current content while the request runs (no spinner, no `loading` state), and replace it when the response arrives.
- **AC9.3** Given a quiet refresh fails (non-401), then the card keeps its previous content. If it had no content yet (it was in `error` or `empty` state), it shows the new result or stays as it was.
- **AC9.4** Given the user selected a heart-rate run type, when a quiet refresh completes, then the selection is kept.
- **AC9.5** The "still importing" texts no longer say "Reload in a while". VO2max: `Lap heart-rate data is still importing: {runs} to go.` Training load: `Heart-rate data is still importing: {runs} to go.`
- **AC9.6** Given the first ever import (no stored runs), then the cards show their empty states and the banner shows `Syncing with Strava…`. When the first batch finishes, the cards fill in without a reload.

## Traceability
| Success criterion (intent) | Covered by |
|---|---|
| S1 Stored data appears at once, even during a sync or backlog | R1, R2 (AC2.2), R7 (AC7.1) |
| S2 Banner shows "Syncing with Strava…" | R8 (AC8.1), R2 (AC2.5) |
| S3 Cards update and the banner goes away, no reload | R9, R8 (AC8.4), R7 (AC7.3) |
| S4 No new sync within 15 minutes of the last one | R3 (AC3.1), R7 (AC7.4) |
| S5 Checks every 15 minutes while open | R7 (AC7.5), R3 (AC3.1) |
| S6 Backlog continues batch after batch within the rate limit | R4, R3 (AC3.3), R7 (AC7.2, AC7.3) |
| S7 Failure keeps data usable and shows a message | R5, R8 (AC8.2), R9 (AC9.3) |

## Design

### Data from Strava
- **Endpoints (unchanged):** `GET /athlete/activities` (`after`, `page`, `per_page=200`), `GET /activities/{id}` (`include_all_efforts=false`), `GET /activities/{id}/streams` (`keys=time,velocity_smooth` or `keys=time,heartrate`, `key_by_type=true`). What is synced doesn't change (non-goal).
- **Scopes:** unchanged, `activity:read_all` (set by feature 001). Nothing new requested.
- **Fields used:** unchanged.
- **Sync & caching:** unchanged incremental logic in `SyncService`/`SyncPolicy` (list after the cursor or 7 days ago, detail/stream phases, prune). What changes is *who triggers* a sync: only `POST /api/sync` → `BackgroundSync`. The read endpoints no longer do. Per-sync activity-call cap = `min(MAX_ACTIVITY_CALLS_PER_SYNC = 80, budget left after list pages)`.
- **Rate-limit budget:** `StravaCallBudget` (domain, in memory, injected `Clock`), 80 calls per UTC quarter-hour window. That leaves 20 of Strava's 100 for token refreshes and the client's retries of idempotent GETs, which the budget doesn't count.
- **Errors:**
  - 401 → the existing client refreshes once and retries. If the refresh fails, the tokens are deleted, the job ends `ReauthRequired`, and the next `/api/sync` call returns 401 `strava_reauth_required` (AC2.6).
  - 429 → the job stops at once (no retry, no hot loop), `rateLimitedUntil` is saved, and the next sync is due at that time (AC3.5).
  - Network failure or 5xx → `stravaUnavailable`, retried after 15 minutes (AC3.4).
  - Unexpected exception → treated like Strava being unavailable (AC5.4).

### Domain model & logic
- `SyncStatus` enum gains `SYNCING`. It is never persisted as `lastStatus`, only reported while a job runs.
- `SyncState` gains `lastAttemptAt: Instant?`. Every finished attempt (success or failure) writes it.
- `SyncPolicy.nextDueAt(state: SyncState, pending: Int, budget: BudgetView, now: Instant): Instant`, pure:
  1. `base` =
     - `lastAttemptAt + 15 min` if `lastStatus == STRAVA_UNAVAILABLE` (or `now` if `lastAttemptAt` is null);
     - else `now` if `listCursor == null` or `pending > 0`;
     - else `lastSuccessfulSyncAt + 15 min` (or `now` if null).
  2. If `rateLimitedUntil > now`: `base = max(base, rateLimitedUntil)`.
  3. If budget left in `now`'s window is 0: `base = max(base, windowEnd(now))`.
  - `isDue(...) = now >= nextDueAt(...)`. It replaces the current `isDue`.
- `SyncPolicy.idleInfo` precedence: `RATE_LIMITED` (until is in the future) → `STRAVA_UNAVAILABLE` (`lastStatus`) → `PARTIAL` (pending > 0) → `OK`.
- `StravaCallBudget(clock, callsPerWindow = 80)`: `remaining(): Int`, `tryConsume(): Boolean`, `windowEnd(): Instant`. Window = `floor(epochSecond / 900) * 900`. The count resets when the window changes. `SyncService` calls `tryConsume()` before each `ActivitySource` call. If that returns false, the sync stops with a new internal outcome `BudgetExhausted`, which is recorded as a successful, `PARTIAL` sync (cursor rule unchanged) and does **not** set `STRAVA_UNAVAILABLE`.
- `SyncService.syncIfDue()` keeps its mutex and outcome reuse. `sync()` also wraps unexpected exceptions (AC5.4) and writes `lastAttemptAt`.
- **New `BackgroundSync`** (domain; depends on `SyncService`, `RunRepository`, `SyncStateRepository`, `StravaCallBudget`, `Clock`, and a `CoroutineScope` passed in):
  - `status(): SyncSnapshot` returns `SyncSnapshot(info: SyncInfo, nextSyncIn: Duration?)`. While a job runs: `status = SYNCING`, `nextSyncIn = null`.
  - `startIfDue(): SyncSnapshot` uses an `AtomicBoolean running`. If already running → `status()`. If not due → `status()`. Otherwise it sets `running`, clears `reauthRequired`, `scope.launch { try { outcome = syncService.syncIfDue() } finally { running = false } }`, and returns the `SYNCING` snapshot.
  - `reauthRequired: Boolean` is set when a job ends with `SyncOutcome.ReauthRequired`.
- Read services: `RunningDashboardService`, `TrainingLoadService` and `Vo2maxService` drop the `SyncService` dependency and the `ReauthRequired` result branch. `DashboardResult.Ready` drops `sync`.
- Units, rounding and time zones: no change to metrics. `nextSyncInSeconds = ceil(max(0, nextDueAt − now) / 1 s)`. Window boundaries are in UTC epoch time. Day bucketing still uses `start_date_local`.

### Persistence
- `sync_state`: new nullable column `last_attempt_at INTEGER` (epoch seconds), added in `DatabaseFactory.connect` with the existing `addColumnIfMissing`. Existing rows read it as null, which counts as "no failed attempt to wait for".
- `ExposedSyncStateRepository` reads and writes `lastAttemptAt`.
- The call budget and the running flag are in memory only (see risks). No other table changes.

### Backend API

#### `GET /api/sync`
- Auth: session required (`hasConnectedSession`). No query params. No side effects except the XSRF cookie (R6).
- Response 200 (`SyncStatusResponse`):
```json
{
  "status": "syncing",
  "lastSyncedAt": "2026-10-31T08:15:00Z",
  "retryAfter": null,
  "runsPendingDetail": 120,
  "nextSyncInSeconds": null
}
```
| Field | Type | Meaning |
|---|---|---|
| `status` | `"syncing" \| "ok" \| "partial" \| "rateLimited" \| "stravaUnavailable"` | `syncing` while a job runs, otherwise the `idleInfo` precedence |
| `lastSyncedAt` | ISO-8601 UTC instant (seconds) or `null` | Last successful sync (`lastSuccessfulSyncAt`) |
| `retryAfter` | ISO-8601 UTC instant or `null` | Set only when `status == "rateLimited"` |
| `runsPendingDetail` | int ≥ 0 | `SyncPolicy.pendingCount` of the stored runs |
| `nextSyncInSeconds` | int ≥ 0 or `null` | Seconds until a `POST` would start a sync. `0` = due now. `null` only while `syncing` |
- Errors: 401 `not_connected` · 401 `strava_reauth_required` (AC2.6) · 500 `internal_error`.

#### `POST /api/sync`
- Auth: session required, plus header `X-XSRF-TOKEN` equal to the `XSRF-TOKEN` cookie. Empty request body.
- Response 200: same `SyncStatusResponse`. `status: "syncing"` if a job was started or was already running, otherwise the idle status (AC2.4).
- Errors: 403 `xsrf_invalid` (`"Request could not be verified. Reload the page."`) · 401 `not_connected` / `strava_reauth_required` · 500 `internal_error`.
- Order of checks: XSRF first (403), then session (401).

#### `GET /api/dashboard/running` (changed)
- Same as features 001 and 007, **minus the `sync` key**. The other keys are unchanged.

#### `GET /api/training-load`, `GET /api/vo2max` (behaviour only)
- Same JSON. They no longer sync, so they can no longer return 401 `strava_reauth_required` from a sync (the session check still returns 401 `not_connected`).

### Frontend (Angular)
- **Route / page:** unchanged (`RunningDashboardPage`, root route).
- **Service & interfaces (`core/api/`):**
  - `sync.models.ts`:
    ```ts
    export type SyncStatus = 'syncing' | 'ok' | 'partial' | 'rateLimited' | 'stravaUnavailable';
    export interface SyncStatusResponse {
      status: SyncStatus;
      /** ISO-8601 UTC instant. */
      lastSyncedAt: string | null;
      /** ISO-8601 UTC instant. */
      retryAfter: string | null;
      runsPendingDetail: number;
      /** Seconds until a sync may start; null while syncing. */
      nextSyncInSeconds: number | null;
    }
    ```
    plus an `isSyncStatusResponse(value: unknown)` guard, following the existing `isVo2maxResponse` pattern.
  - `sync.service.ts`: `getStatus(): Observable<unknown>` (`GET /api/sync`) and `start(): Observable<unknown>` (`POST /api/sync`, body `{}`).
  - `running-dashboard.models.ts`: remove `sync` from `RunningDashboardResponse` and move `SyncStatus`/`SyncInfo` out (they are replaced by `sync.models.ts`).
- **`SyncStore` (`core/sync/sync.store.ts`)**, `@Injectable()` provided in `RunningDashboardPage.providers`, so it lives exactly as long as the page:
  - Signals: `status: Signal<SyncStatusResponse | null>`, `failed: Signal<boolean>`, `dataVersion: Signal<number>` (starts at 0).
  - Behaviour as in R7. Constants: `POLL_INTERVAL_MS = 5000`, `ERROR_RETRY_MS = 60000`, `MIN_DELAY_MS = 1000`. Timers use RxJS `timer`, cleaned up with `DestroyRef`.
  - `bannerState: Signal<SyncBannerState>` (`'none' | 'failed' | SyncStatusResponse`) feeds the banner.
- **Components:**
  - `SyncBanner`: input changes to `state: SyncBannerState`. `syncBannerText()` stays a pure, exported function with the texts from R8.
  - `RunningDashboardPage`: provides `SyncStore`, renders `<app-sync-banner>` right under `<h1>`, drops the `loading-note` line, and quietly reloads on `dataVersion` changes after the first (AC9.2). The state type gains no new kinds: a quiet reload just doesn't set `loading`.
  - `TrainingLoadSection`, `Vo2maxSection`: inject `SyncStore` and reload quietly on `dataVersion` changes (skip the initial value). Update the pending texts (AC9.5).
- **States per view:**
  - Banner: none (no status yet / `ok`) · normal (texts in R8) · error (`Couldn't check the Strava sync. Showing saved data.`). It has no loading or empty state of its own.
  - Running cards, training load, VO2max: loading / empty / error / normal as today. Only the first load shows loading. Quiet refreshes keep the current state until the response arrives (AC9.2, AC9.3).
- **Charts:** no change.

### Files expected to change
Backend (`backend/src/main/kotlin/app/stravadashboard/`):
- `domain/SyncService.kt`: budget use, `lastAttemptAt`, exception wrapping
- `domain/SyncPolicy.kt`: `nextDueAt`, new `isDue`, `idleInfo` precedence
- `domain/StravaCallBudget.kt` *(new)*
- `domain/BackgroundSync.kt` *(new)*
- `domain/ports/SyncStateRepository.kt`: `lastAttemptAt`, `SyncStatus.SYNCING`
- `domain/RunningDashboardService.kt`, `domain/trainingload/TrainingLoadService.kt`, `domain/vo2max/Vo2maxService.kt`: drop sync
- `persistence/Tables.kt`, `persistence/DatabaseFactory.kt`, `persistence/ExposedSyncStateRepository.kt`
- `api/SyncRoutes.kt`, `api/SyncDtos.kt`, `api/SyncMappers.kt` *(new)*
- `api/XsrfProtection.kt` *(new, route-scoped Ktor plugin)*
- `api/ErrorResponse.kt`: `XSRF_INVALID`
- `api/RunningDashboardDtos.kt`, `api/RunningDashboardMappers.kt`, `api/RunningDashboardRoutes.kt`, `api/TrainingLoadRoutes.kt`, `api/Vo2maxRoutes.kt`: remove `sync` / reauth branches
- `Application.kt`: budget, a `BackgroundSync` scope (child of the `Application` scope with a `SupervisorJob`, cancelled on `ApplicationStopped`), XSRF plugin, `syncRoutes`
- Tests: `domain/SyncServiceTest.kt`, `domain/SyncPolicyTest.kt`, new `domain/BackgroundSyncTest.kt`, `domain/StravaCallBudgetTest.kt`, new `api/SyncRoutesTest.kt`, `api/RoutesTest.kt`, `api/RunningDashboardContractTest.kt`, persistence test for the new column

Frontend (`frontend/src/app/`):
- `core/api/sync.models.ts`, `core/api/sync.service.ts`, `core/api/sync.service.spec.ts` *(new)*
- `core/sync/sync.store.ts`, `core/sync/sync.store.spec.ts` *(new)*
- `core/api/running-dashboard.models.ts`
- `features/running-dashboard/sync-banner/sync-banner.ts` (+ spec)
- `features/running-dashboard/running-dashboard-page.ts`, `.html` (+ `*.spec.ts` files that use `sync`)
- `features/training-load/training-load-section.ts`, `training-load.view.ts` (+ spec)
- `features/vo2max/vo2max-section.ts` (+ spec)
- `testing/dashboard-fixtures.ts`, new `testing/sync-store-stub.ts`

## Edge cases
| Case | Expected behaviour |
|---|---|
| No activities stored (first connect) | Cards show empty states at once. The banner shows `Syncing with Strava…`. Cards fill in after the first batch (AC9.6). |
| No runs at all on Strava | Sync finishes `ok`. The banner disappears. Cards stay empty. Next check in 15 minutes. |
| Manual activity (no GPS) | Unchanged sync rules: stored if `sport_type = Run`, no stream call when not an outdoor workout. Location is never requested. |
| Activity without heart rate | Unchanged: no HR stream call, not counted as pending. |
| Units / time zones | No metric changes. Banner times are formatted in the browser's local time from UTC instants. Scheduling uses the relative `nextSyncInSeconds`, so clock differences between browser and server don't matter. The budget window uses UTC epoch quarter-hours. |
| Several tabs open | Each tab polls and posts. The backend runs one job (AC2.3). All tabs see `syncing` and refresh when it ends. |
| Sync finishes between two polls | The next poll sees a non-`syncing` status and triggers the refresh (AC9.1). `POST` always answers `syncing` when it starts a job, so no change is missed. |
| Page closed during a job | The browser stops polling (AC7.7). The batch in progress (≤ 80 calls) finishes in the backend, but no new batch starts until the dashboard is open again. |
| Laptop asleep / tab in background | Timers fire late. The `POST` then goes out and the backend decides whether a sync is due. Nothing hot-loops. |
| Backend restart during a job | The job is lost. Runs written so far stay stored. The cursor only moves after a full list pass. The budget restarts at 0 used (see risks: a 429 is possible and handled by AC5.1). |
| Strava 429 mid-backlog | `rateLimited`, banner with the time. The `POST` is scheduled for `retryAfter`. |
| Backlog of ~400 lap-HR refetches (008) | About 80 calls per window, about 5 windows (≈ 75 min) while the dashboard is open. The banner alternates between `Syncing…` and `Still importing … (n remaining)`. VO2max refreshes after each batch. |
| Reopen within 15 minutes, no backlog | `nextSyncInSeconds > 0`, so no `POST` and no Strava call (S4). With a backlog, S6 applies and a batch may start (see risks). |
| Token revoked on Strava | The job ends `ReauthRequired`. The next poll gets 401 `strava_reauth_required`, and the interceptor goes to `/connect?reason=reauth`. |
| Missing or stale XSRF cookie | `POST` gets 403. The store shows the status-failed banner and retries with `GET` after 60 s, which sets a fresh cookie. |

## Privacy
- **Data fields & sensitivity:** no new Strava fields. The sync status exposes only counts, instants and a status code. The stored data includes heart rate (health data, GDPR Art. 9), as before.
- **Storage & retention:** one new column `sync_state.last_attempt_at` (a timestamp, not personal data). Retention is unchanged (6 months + 41 days, pruned on sync).
- **Scopes:** unchanged (`activity:read_all`). Nothing new requested.
- **Applicable privacy-check items:**

| # | Status | How it's satisfied |
|---|---|---|
| 1 | ✅ | No new secrets. Fixtures use invented data. |
| 2 | ✅ | Tokens stay in the backend. `SyncStatusResponse` holds no token or athlete ID (route test checks the key set). |
| 3 | ✅ | Existing refresh-and-revoke flow. A revoke during a background job leads to `strava_reauth_required` (AC2.6, AC5.3). |
| 4 | n/a | OAuth flow unchanged. |
| 5 | ✅ | No scope change. |
| 6 | ✅ | Only `last_attempt_at` is added. |
| 7 | ✅ | No GPS requested or stored. |
| 8 | ✅ | Background-job logs hold only status, counts and the exception class name (AC5.4). Never messages, URLs, tokens or HR. |
| 9 | ✅ | Ktor client logging unchanged (not installed). |
| 10 | ✅ | Errors use the standard `{error, message}` shape. Exceptions in the job never reach a response. |
| 11 | ✅ | `/api/sync` checks the session like every other `/api` route. |
| 12 | ✅ | Session cookie unchanged. The new `XSRF-TOKEN` cookie holds only a random value: `SameSite=Strict`, `Secure` on https, not HttpOnly (Angular has to read it). |
| 13 | ✅ | The banner uses interpolation only, and no Strava text is shown. |
| 14 | ✅ | `POST /api/sync` is the only state-changing endpoint and is XSRF-protected (R6). `GET /api/sync` has no side effects. The read endpoints no longer sync as a side effect, which closes the item-14 risk noted in feature 001. |
| 15 | ✅ | Only the fields the UI needs. No CORS. |
| 16 | ✅ | No new dependencies or external calls. |
| 17 | ✅ | Unchanged. |
| 18 | ❌ deferred | Still no disconnect/delete action (as in 001). |
| 19 | n/a | No webhook. |
| 20 | ✅ | Unchanged. |
| 21 | ✅ | New tests use invented runs and IDs. |

## Test plan
| AC | Test type | Test name |
|---|---|---|
| AC1.1 | backend route (fake source) | `R1 - read endpoints make no Strava calls when sync is due` |
| AC1.2 | backend route (coroutines-test, suspended fake source) | `R1 - read endpoints answer while a sync is running` |
| AC1.3 | backend route | `R1 - dashboard response has no sync key` |
| AC1.4 | backend route | `R1 - read endpoints without session return 401 not_connected` |
| AC1.5 | frontend component | `R1 - loading state has no first import note` |
| AC2.1 | backend route | `R2 - get sync returns status without starting a sync` |
| AC2.2 | backend route + unit (BackgroundSync) | `R2 - post sync starts a background job and returns syncing at once` |
| AC2.3 | backend unit (BackgroundSync) | `R2 - second start while running does not start another job` |
| AC2.4 | backend unit | `R2 - start when not due returns idle status` |
| AC2.5 | backend unit | `R2 - status is syncing while running and finished status afterwards` |
| AC2.6 | backend route | `R2 - sync without session returns not_connected`, `R2 - sync after revoked connection returns strava_reauth_required` |
| AC2.7 | backend unit | `R2 - cancelling the scope cancels the running job` |
| AC3.1 | backend unit (SyncPolicy) | `R3 - not due within 15 minutes and next sync in 60 seconds` |
| AC3.2 | backend unit | `R3 - due immediately without a completed list pass` |
| AC3.3 | backend unit | `R3 - due immediately with pending runs and budget left` |
| AC3.4 | backend unit | `R3 - after strava unavailable next sync waits 15 minutes` |
| AC3.5 | backend unit | `R3 - rate limited sync waits until retry after` |
| AC3.6 | backend unit | `R3 - next sync seconds rounded up and never negative` |
| AC4.1 | backend unit (StravaCallBudget) | `R4 - budget resets on quarter hour boundaries` |
| AC4.2 | backend unit (SyncService) | `R4 - one sync makes at most 80 calls including list pages` |
| AC4.3 | backend unit | `R4 - exhausted budget makes next sync due at next window` |
| AC4.4 | backend unit | `R4 - two syncs in one window share the 80 call budget` |
| AC4.5 | backend unit | `R4 - budget running out during list pass keeps cursor` |
| AC5.1 | backend unit | `R5 - 429 in background sync saves rate limit and keeps stored runs` |
| AC5.2 | backend unit | `R5 - unreachable strava records unavailable and attempt time` |
| AC5.3 | backend unit + route | `R5 - revoked token ends job with reauth required` |
| AC5.4 | backend unit (log capture) | `R5 - unexpected exception is caught and logged by class name only` |
| AC5.5 | backend route | `R5 - read endpoints serve stored data after a failed sync` |
| AC6.1 | backend route | `R6 - api response sets xsrf cookie when missing` |
| AC6.2 | backend route | `R6 - post sync without matching xsrf header returns 403` |
| AC6.3 | backend route | `R6 - post sync with matching xsrf header starts sync` |
| AC6.4 | frontend service (HttpTestingController) | `R6 - start posts to api sync` |
| AC7.1 | frontend component (page) | `R7 - status request runs alongside card requests` |
| AC7.2 | frontend service (SyncStore, fake timers) | `R7 - posts after next sync seconds` |
| AC7.3 | frontend service | `R7 - polls every 5 seconds while syncing` |
| AC7.4 | frontend service | `R7 - no post before next sync seconds after reload` |
| AC7.5 | frontend service | `R7 - posts again after each 15 minute interval` |
| AC7.6 | frontend service | `R7 - status error sets failed and retries after 60 seconds` |
| AC7.7 | frontend service | `R7 - destroying the page stops all sync requests` |
| AC8.1 | frontend component | `R8 - shows syncing with strava while syncing` |
| AC8.2 | frontend unit (syncBannerText) | `R8 - banner text per status` |
| AC8.3 | frontend component (page) | `R8 - banner shows while cards are loading or failed` |
| AC8.4 | frontend component (page) | `R8 - banner disappears when sync finishes ok` |
| AC9.1 | frontend service | `R9 - data version increments once when syncing ends` |
| AC9.2 | frontend component (page, sections) | `R9 - cards refresh quietly after sync without loading state` |
| AC9.3 | frontend component | `R9 - failed quiet refresh keeps previous content` |
| AC9.4 | frontend component (page) | `R9 - quiet refresh keeps selected heart rate type` |
| AC9.5 | frontend unit + component | `R9 - pending texts do not ask to reload` |
| AC9.6 | frontend component (page) | `R9 - first import fills empty cards after first batch` |

## Out of scope
- Syncing while the dashboard is closed (no scheduler, no always-on sync).
- Any change to what is synced or how metrics are calculated.
- A manual "Sync now" button.
- Server push (SSE/WebSocket). Polling is used instead.
- Persisting the call budget across backend restarts.

## Suggested follow-ups
- Read Strava's `X-ReadRateLimit-Usage` header to know the real 15-minute usage instead of counting calls ourselves (this would also cover usage by other apps).
- Persist the call budget in `sync_state` so a restart can't cause a 429.
- Refresh only when a sync actually changed data (a `dataChangedAt` field), to skip refetches after empty syncs.
- A disconnect/delete-data action (privacy item 18, carried over from 001).

## Risks & decisions to confirm
- **Breaking contract change:** the `sync` key is removed from `GET /api/dashboard/running`, because the banner now reads `/api/sync` and two sources of truth would drift. Confirm. Keeping it would mean either a duplicate source of truth or a dead field.
- **CSRF infrastructure is new:** Ktor gets a small XSRF double-submit plugin (R6), because `POST /api/sync` is the app's first state-changing `/api` endpoint and privacy item 14 requires it. The alternative, letting `GET /api/sync` trigger the sync as a side effect, is simpler but keeps the item-14 exception from feature 001.
- **S4 vs S6:** with a backlog pending, reopening the dashboard within 15 minutes *can* start a batch, if budget remains in the current window. I read S4 as applying when there is no backlog. Confirm.
- **"Next batch right after the previous one"** is read as "as soon as the 80-call budget allows". Since a batch uses all the budget left in the window, the next batch usually starts at the next quarter-hour (for example :07 → :15). About 80 runs per 15 minutes, as the intent says.
- **In-memory budget:** after a backend restart the budget starts fresh, so up to 80 calls may land in a window that already had some. The budget leaves 20 calls of headroom, and a 429 is handled cleanly (AC5.1).
- **An in-flight batch may finish after the tab is closed** (≤ 80 calls, typically under 2 minutes). No new batch starts. I treat this as within "sync only while the dashboard is open". Confirm.
- **Refresh after every finished sync**, even one that found nothing new: the read endpoints are local and cheap, and this avoids change tracking.
- **Polling every 5 s while syncing** adds local requests (no Strava calls). Ktor `CallLogging` will log each one at INFO.
