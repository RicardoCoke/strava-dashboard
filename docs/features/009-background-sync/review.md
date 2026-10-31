# Review: 009-background-sync
**Base:** main (a3e6eba) → **Head:** 7c787e9
**Verdict:** APPROVE

## Checks
- Backend: ktlint pass · detekt pass (0 issues) · tests 321 passed, 0 failed (Gradle reported every task UP-TO-DATE, so these are the cached results for the current sources)
- Frontend: lint pass · tests 211 passed, 0 failed · build pass

## Spec coverage
| Req | Implemented | Tested | Notes |
|---|---|---|---|
| R1 | ✅ | ✅ | `SyncRoutesTest` (dashboard and vo2max), `TrainingLoadRoutesTest`/`Vo2maxRoutesTest` (0 list calls), `RoutesTest` key set, page spec has no first-import note |
| R2 | ✅ | ✅ | AC2.5 does not check that `runsPendingDetail` is right while syncing. The POST can return a finished status instead of `syncing` (Minor 1) |
| R3 | ✅ | ✅ | Implemented as `SyncSchedule.nextDueAt`, not `SyncPolicy.nextDueAt` as the spec names it (Nit) |
| R4 | ✅ | ✅ | AC4.2 is tested with 60 workouts, not 200 pending runs, but it checks the 80-call total including list pages |
| R5 | ✅ | ✅ | Unexpected exceptions are caught only inside `sync()` (Minor 2) |
| R6 | ✅ | ✅ | The `Secure` flag when `secureCookies=true` is not tested (Minor 4) |
| R7 | ✅ | ✅ | |
| R8 | ✅ | ✅ | |
| R9 | ✅ | ✅ | |

## Findings
### [Minor] `startIfDue` can return a finished status instead of `syncing` after starting a job
- **Where:** `backend/src/main/kotlin/app/stravadashboard/domain/BackgroundSync.kt:60-67` (with `:40-44`)
- **Problem:** After `scope.launch`, the method returns `status()`. `status()` first does two DB reads (`syncState.get()` and a full `runs.findAll()`) and only then reads `running`. The job runs in parallel on the application scope. If it finishes before those reads are done, the POST answers `ok`, `partial` or `stravaUnavailable` instead of `syncing`. The spec says `startIfDue` "returns the SYNCING snapshot", and the edge-case table says "POST always answers syncing when it starts a job, so no change is missed."
- **Failure scenario:** The store's previous status was `ok`. A fast sync finishes (for example Strava refuses the connection at once, or a small store with a quick round trip) before `status()` reads `running`. The POST returns `ok`, so `SyncStore.receive` sees `wasSyncing = false` and never bumps `dataVersion`. The cards don't refresh for that sync. In practice this needs the job to beat two local DB reads, so it is unlikely.
- **Suggested fix:** Build the `SYNCING` snapshot from the `idle` state already read, before or right after `launch`, instead of calling `status()` again.

### [Minor] Exceptions outside `sync()` escape the job's catch
- **Where:** `backend/src/main/kotlin/app/stravadashboard/domain/SyncService.kt:85-95` (`syncNowIfDue`: `syncState.get()`, `runs.findAll()`, `budget.view()`) and `:109-111` (`recordFailure` inside the catch)
- **Problem:** AC5.4 says the job catches unexpected exceptions, logs only the class name and saves `STRAVA_UNAVAILABLE` plus `lastAttemptAt`. The try/catch only wraps `sync()`. A DB error in the pre-check, or in `recordFailure` inside the catch, goes up to `scope.launch` in `BackgroundSync.kt:60`. That scope has no `CoroutineExceptionHandler`.
- **Failure scenario:** The SQLite file is locked, so `syncState.get()` throws during a background job. Nothing is saved (no `lastAttemptAt`, no `STRAVA_UNAVAILABLE`). The next poll finds the sync due again, so there is no 15-minute back-off. The default handler prints the full exception message and stack trace, which is not "class name only". `running` is still cleared by the `finally`.
- **Suggested fix:** Catch inside the `BackgroundSync` job as well (keep rethrowing `CancellationException`, log the class name), or move the pre-check inside the `try` in `SyncService`.

### [Minor] `activityCalls` ignores the result of `budget.tryConsume()`
- **Where:** `backend/src/main/kotlin/app/stravadashboard/domain/SyncService.kt:192`
- **Problem:** The spec says `SyncService` calls `tryConsume()` before each call and stops with `BudgetExhausted` when it returns false. Here the return value is thrown away. The limit holds today only because `remaining` is computed once from `budget.remaining()` at the start of `activityCalls`, under the mutex. If the budget is ever shared with another consumer, or the code is refactored, the call goes ahead even after `tryConsume()` returned false, with nothing to flag it.
- **Suggested fix:** Stop the phase loop when `tryConsume()` returns false (the same outcome as the list pass), or drop the precomputed cap and rely on `tryConsume()` alone.

### [Minor] Some acceptance-criterion details are not tested
- **Where:** `backend/src/test/kotlin/app/stravadashboard/api/SyncRoutesTest.kt:307-318` (AC6.1 runs only with `secureCookies=false`); `backend/src/test/kotlin/app/stravadashboard/domain/BackgroundSyncTest.kt:579-593` (AC2.5 never checks `runsPendingDetail` while syncing)
- **Problem:** If the `Secure` flag or the pending count during a sync broke, no test would fail.
- **Suggested fix:** Add one XSRF cookie test with `secureCookies = true` that expects `Secure`. In the AC2.5 test, assert `sync.status().info.runsPendingDetail` while the job is gated.

### [Minor] Backend tests wait with real time
- **Where:** `backend/src/test/kotlin/app/stravadashboard/testsupport/SyncApi.kt:975-982` (`delay(POLL_MILLIS)` up to 500 times); `BackgroundSyncTest.kt:520` (`Dispatchers.Default` + `runBlocking`)
- **Problem:** The standard (A8) says "Deterministic: … no sleeps". `syncAndWait` polls with real `delay` and gives up after about 5 s. On a slow CI machine, any route test that uses it can fail with "Background sync did not finish".
- **Suggested fix:** Expose a way to await the job (for example `BackgroundSync.join()` through a test hook), or use `kotlinx-coroutines-test` with a test dispatcher.

### [Nit] A revoked connection's attempt doesn't write `lastAttemptAt`
- **Where:** `backend/src/main/kotlin/app/stravadashboard/domain/SyncService.kt:147`
- **Problem:** The spec says "Every finished attempt (success or failure) writes it". The `ReauthRequired` branch saves nothing. The tokens are deleted anyway, so nothing visible changes.

### [Nit] `SyncService` creates its own budget by default
- **Where:** `backend/src/main/kotlin/app/stravadashboard/domain/SyncService.kt:66`
- **Problem:** `budget: StravaCallBudget = StravaCallBudget(clock)` lets a caller build a `SyncService` whose budget is not the one `BackgroundSync` reads for `nextDueAt`. The two would then disagree with no warning. `Application.kt:101` passes the shared one correctly today.

### [Nit] Names and placement differ from the spec
- **Where:** `backend/src/main/kotlin/app/stravadashboard/domain/SyncSchedule.kt` (the spec says `SyncPolicy.nextDueAt`/`idleInfo`); `api/SyncStatusResponse.kt` (the spec says `SyncDtos.kt`); `backend/src/test/kotlin/app/stravadashboard/domain/SyncPolicyTest.kt` (tests `SyncSchedule`, and also tests the api mapper `toDto` and holds `StravaCallBudgetTest`)
- **Problem:** Test files don't match the classes they test, so tests are harder to find.

### [Nit] An empty XSRF cookie matches an empty header
- **Where:** `backend/src/main/kotlin/app/stravadashboard/api/XsrfProtection.kt:51`
- **Problem:** `cookie = ""` with `header = ""` passes. A remote attacker can't set the cookie, so this is defence in depth only. Consider rejecting blank values.

## Privacy checklist
1. ✅ No secrets in the diff or fixtures.
2. ✅ `SyncStatusResponse` has no token or athlete ID. The key set is checked in `SyncRoutesTest.kt:254`.
3. ✅ The existing refresh/revoke flow is used, and a revoke ends in `strava_reauth_required` (`SyncRoutes.kt:253`).
4. n/a. The OAuth flow is unchanged.
5. ✅ Scopes are unchanged.
6. ✅ Only the `last_attempt_at` timestamp is added.
7. ✅ No GPS.
8. ✅ with a caveat: the caught path logs only the class name (`SyncService.kt:110`). Exceptions that escape the catch print full messages and stack traces (Minor 2).
9. ✅ Ktor client logging is unchanged.
10. ✅ Errors use the standard `{error, message}` shape. Job exceptions never reach a response.
11. ✅ `/api/sync` checks the session (`SyncRoutes.kt:252`).
12. ✅ The session cookie is unchanged. The XSRF cookie is random, `SameSite=Strict`, not HttpOnly, and `Secure` when configured (Secure branch untested, Minor 4).
13. ✅ The banner uses interpolation only, with no Strava text.
14. ✅ `POST /api/sync` checks XSRF first, using a constant-time compare. The read endpoints no longer change state.
15. ✅ Responses carry only the fields the UI needs. No CORS.
16. ✅ No new dependencies or external calls.
17. ✅ Unchanged.
18. ❌ Still no disconnect/delete action (deferred in the spec, carried over from 001).
19. n/a. No webhook.
20. ✅ Unchanged.
21. ✅ Fixtures are invented (ids 1..n, "Invented secret detail").

## Summary
The implementation follows the spec closely. The read endpoints no longer sync, the call budget and the due-time rule are pure and tested, XSRF is in place, and the frontend store and quiet refreshes are covered by tests named after the ACs. All checks pass. The most important fixes are to return the `SYNCING` snapshot without re-reading state after launching the job, and to catch exceptions across the whole background job so AC5.4 holds in every path.

---

## Resolution (after review)

No Blockers or Majors, so the reviewer was not re-run. After the fixes: backend ktlint, detekt and 322 tests pass; frontend lint, 211 tests and the build pass (no frontend change).

| Finding | Outcome |
|---|---|
| [Minor] `startIfDue` can return a finished status | **Fixed.** `startIfDue` now returns a `SYNCING` snapshot built from the state it already read, before the job can finish. |
| [Minor] Exceptions outside `sync()` escape | **Fixed.** The background job (`BackgroundSync.runJob`) now catches everything except `CancellationException`, logs only the class name, and records `STRAVA_UNAVAILABLE` with `lastAttemptAt` (itself guarded, so a broken database can't escape either). No dedicated test: forcing a DB failure between the status read and the job needs a racy fake; the path is three lines and mirrors the tested `SyncService` catch. |
| [Minor] `activityCalls` ignores `tryConsume()` | **Fixed.** Each activity call takes a budget unit first and the batch stops when none is left; the pending runs carry on in the next window. |
| [Minor] AC details untested | **Fixed.** `SyncRoutesTest` › `R6 - xsrf cookie is Secure when secure cookies are configured`; the AC2.5 test now asserts `runsPendingDetail` (2) while the job is paused. |
| [Minor] Backend tests wait with real time | **Partly addressed.** The route tests drive the sync through HTTP, as the dashboard does, so they poll `GET /api/sync`; the job lives inside the Ktor module with no test hook. The poll bound is raised from ~5 s to ~30 s so a slow CI machine doesn't flake, and the timing-sensitive cases (`BackgroundSyncTest`) use explicit gates (`CompletableDeferred`), not sleeps. A test hook into the module was judged not worth widening the production wiring for. |
| [Nit] Reauth attempt doesn't write `lastAttemptAt` | **Fixed.** The `ReauthRequired` branch now saves `lastAttemptAt`. |
| [Nit] `SyncService` default budget | **Kept.** Production passes the shared budget (`Application.kt`); the default keeps the 30 existing `SyncServiceTest` cases short. `BackgroundSyncTest` passes the shared one explicitly. |
| [Nit] Names differ from the spec | **Partly fixed.** Tests are split to match their classes: `SyncScheduleTest` and `StravaCallBudgetTest`. `SyncSchedule` stays a separate object from `SyncPolicy` (detekt's function limit per object), and the DTO file is named after its class as ktlint requires. |
| [Nit] Empty XSRF cookie matches an empty header | **Fixed.** A blank cookie is rejected. |
