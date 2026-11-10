# Spec: Sync at startup

**Feature ID:** 022 · **Status:** Approved · **Intent:** [intent.md](./intent.md) · **Date:** 2026-11-10

## Summary
When the application module starts, it starts one background sync for each connected person (Ricardo and, if configured, Marina), with no HTTP request needed. It reuses `OtherPersonSync.startIfConnected()`, so the existing due/budget rules, the "connected only" check and the error handling stay as they are. Nothing else changes: later syncs still only start from `POST /api/sync` on the existing 15-minute schedule.

## Requirements
| ID | Requirement | Priority |
|---|---|---|
| R1 | At startup, start a sync for each configured person whose Strava tokens are stored, without any HTTP request to the app. | Must |
| R2 | At startup, skip a person with no stored tokens, silently (no Strava call, no error). | Must |
| R3 | The startup sync uses the existing `BackgroundSync.startIfDue()` rules unchanged: it counts as the last sync for the 15-minute schedule, and it is not retried on its own if it fails or is rate limited. | Must |
| R4 | The startup call does not block or fail application startup. | Must |

## Acceptance criteria
### R1 — Startup sync for connected people
- **AC1.1** Given Ricardo's tokens are stored and his sync is due, when the application starts and no HTTP request is made to it, then a Strava `GET /athlete/activities` call is made and Ricardo's `lastSuccessfulSyncAt` is set.
- **AC1.2** Given Marina is configured and her tokens are stored, when the application starts, then a Strava activities call is made for Marina too and her `lastSuccessfulSyncAt` is set.

### R2 — Not-connected people are skipped
- **AC2.1** Given no tokens are stored for anyone, when the application starts, then no request reaches the Strava mock engine and nothing is logged at warn/error level.
- **AC2.2** Given Ricardo is connected and Marina is configured but not connected, when the application starts, then only Ricardo's sync runs.

### R3 — Existing schedule applies
- **AC3.1** Given the startup sync succeeded, when `POST /api/sync` is called within 15 minutes, then no new sync job starts (response status is idle, not `SYNCING`) and no further Strava activities call is made.
- **AC3.2** Given the startup sync failed (Strava 5xx or 429), when no request is made, then no further Strava call happens; the next `POST /api/sync` follows the existing schedule (covered by feature 009's existing tests; no new test).

### R4 — Startup is not blocked
- **AC4.1** Given the token store throws when read at startup, when the application starts, then it still serves `GET /api/health` with 200 (the exception is caught and logged by class name only in `startIfConnected()`).

## Traceability
| Success criterion (intent) | Covered by |
|---|---|
| S1 | R1 (AC1.1) |
| S2 | R1 (AC1.2), R2 (AC2.2) |
| S3 | R1 |
| S4 | R3 (AC3.2), no new scheduler is added |
| S5 | R3 (AC3.1) |

## Design

### Data from Strava
- Endpoints, scopes, fields, pagination, incremental `after`, budget: unchanged; the startup sync is the same `SyncService.syncIfDue()` job as feature 009.
- Errors: 401 / revoked → existing behaviour (job sets `reauthRequired`; the page then shows "reconnect") · 429 → existing budget/back-off; no extra retry · network failure → existing job handling; the next page visit retries on schedule.

### Domain model & logic
No change.

### Persistence
No change. `sync_state.lastSuccessfulSyncAt` is written by the existing job.

### Backend API
No new or changed endpoints.

### Wiring
At the end of `Application.module(...)`, after both `PersonStack`s are built, launch one coroutine in the application scope:

```kotlin
// Feature 022: sync each connected person once at boot; after that, only an open page starts syncs.
launch {
    ricardo.otherSync.startIfConnected()
    marina?.otherSync?.startIfConnected()
}
```

`startIfConnected()` checks tokens, calls `startIfDue()` (which launches the job in the background scope and returns at once) and catches/logs exceptions, so no new class or error handling is needed. Running in `Application`'s own scope means it is cancelled on `ApplicationStopped`.

Note: `startIfConnected()`'s warn log says "the other person's sync". Optionally reword to "Could not start a sync" so the startup failure log reads correctly; no other change to the class.

### Frontend (Angular)
No change. The page's first `GET /api/sync` / `POST /api/sync` simply finds a fresh `lastSuccessfulSyncAt` or a running job (which it already displays).

### Files expected to change
- `backend/src/main/kotlin/app/stravadashboard/Application.kt` (the `launch` above)
- `backend/src/main/kotlin/app/stravadashboard/api/SyncRoutes.kt` (optional log wording only)
- `backend/src/test/kotlin/app/stravadashboard/api/StartupSyncTest.kt` (new)

## Edge cases
| Case | Expected behaviour |
|---|---|
| No activities in range | Existing sync behaviour; sync succeeds with nothing new. |
| Manual activity (no GPS) / no HR / time zones | Unchanged; handled by the existing sync. |
| Marina not configured (`config.marina == null`) | Only Ricardo is considered. |
| Person not connected | Skipped silently (R2). |
| Page opened while the startup job is still running | `POST /api/sync` sees the job running and does not start another (existing `running` guard); the banner shows "Syncing..." until it ends. |
| Startup within 15 minutes of a previous sync (fast restart) | `startIfDue()` returns without a job; no Strava call. |
| Startup sync hits 429 or fails | No own retry; next page visit retries per existing schedule. |
| Token refresh needed at startup | Existing `StravaTokenProvider` refreshes as on any sync. |

## Privacy
- Data fields & sensitivity: same Strava activity data (including HR) and tokens as today; nothing new collected.
- Storage & retention: unchanged.
- Scopes: unchanged (no new scope).
- Applicable privacy-check items: tokens stay in the backend (no browser involved at all); nothing new logged; the only possible log line is the exception class name from `startIfConnected()`, never tokens or coordinates.

## Test plan
`testApplication` with `module(config, stravaEngine = MockEngine { ... })`, temp SQLite files, and `startApplication()` to start the module without sending any request to the app. Assertions wait (bounded poll, e.g. up to 5 s) for the background job.

| AC | Test type | Test name |
|---|---|---|
| AC1.1 | backend route (testApplication + MockEngine) | `R1 - with stored tokens a Strava activities call happens at startup without any request to the app` |
| AC1.2 | backend route | `R1 - Marina's stored tokens also trigger a startup sync` |
| AC2.1 | backend route | `R2 - without stored tokens no Strava call happens at startup` |
| AC2.2 | backend route | `R2 - only the connected person syncs at startup` |
| AC3.1 | backend route | `R3 - a POST to api sync right after the startup sync starts no new job` |
| AC3.2 | existing 009 tests | (no new test) |
| AC4.1 | backend route | `R4 - a failing token store at startup does not stop the app serving health` (Could; drop if the store cannot be made to throw without a new seam) |

## Out of scope
- Periodic syncing on the server while no page is open.
- Any change to sync rules, rate limits or the request budget.
- Any frontend change.
- Startup sync for a person who is not connected.
- Retrying a failed startup sync on its own.

## Suggested follow-ups
- None needed for the intent.

## Risks & decisions to confirm
- The startup `launch` runs when the module loads, before Netty is accepting connections; this is harmless because the job only calls Strava, not the app.
- If the Pi's clock is not yet set at boot (no RTC, NTP not synced), the "15 minutes since last sync" check uses a wrong time. Usually it errs towards syncing; worth knowing, not addressed here.
- AC4.1 needs a way to make the token store throw; if that requires a new test seam, drop the test (the catch already exists and is covered by 019's tests).
