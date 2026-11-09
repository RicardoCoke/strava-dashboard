# Review: 020-whoop-rings
**Base:** HEAD (c5a4bf9), uncommitted working tree → **Head:** c5a4bf9 + working tree
**Verdict:** APPROVE WITH CHANGES

## Checks
- Backend: ktlint pass · detekt pass (0 issues) · tests 720 passed, 0 failed (Gradle reported every task up-to-date, so these are the cached results for the current sources)
- Frontend: lint pass · tests 452 passed, 0 failed (77 files) · build pass

## Spec coverage
| Req | Implemented | Tested | Notes |
|---|---|---|---|
| R1 | ✅ | ✅ | Per the build deviation, sleep comes from `dailySleep`. Mapping is tested in `GarminRingDayTest`. AC1.3 and AC1.4 are void under the deviation. |
| R2 | ✅ | ⚠️ | Days, weights, cap, gaps, 204 and no-LTHR are tested. No log-capture test for AC2.9. |
| R3 | ✅ | ❌ | `RingsSync` is tested on its own. Nothing tests the `WellnessSync` wiring: `lastStatus` staying OK (AC3.1), `retryAfter` saved to `wellness_state` (AC3.2), and no HR calls when summaries fail (AC3.3). |
| R4 | ✅ | ✅ | |
| R5 | ✅ | ✅ | |
| R6 | ✅ | ✅ | |
| R7 | ✅ | ⚠️ | No per-person values test (AC7.4). AC7.5 checks the HTTP engines only, not "run repository never called". |
| R8 | ✅ | ✅ | AC8.1 order is asserted in `running-dashboard-page.plan.spec.ts`. |
| R9 | ✅ | ✅ | Tokens and colours are tested. Layout is a manual check per the spec. |
| R10 | ✅ | ✅ | `WellnessRoutesTest` compares the bodies with and without a key. No `domain/trainingload/` changes. |

## Findings
### [Major] A day cached part-way through stays partial or empty forever
- **Where:** `backend/src/main/kotlin/app/stravadashboard/domain/rings/RingsSync.kt:30`
- **Problem:** A cached day is re-fetched only while it is today or yesterday. Sync runs only while a dashboard is open (`api/SyncRoutes.kt:37` comment, `OtherPersonSync`). `fetched_at` is written (`ExposedRingDayRepository.kt:42`) but nothing ever reads it.
- **Failure scenario:** The dashboard is opened on day D at 07:00, before the watch has synced, and not again until D+2. Day D is stored with the morning's partial strain (say 2.1 instead of 12) and/or `sleep_s = null`. On D+2, D is already in `cached`, so it is never fetched again. That wrong value then feeds D+1's sleep need (strain extra), the sleep-debt sum, and 30 days of strain, sleep and recovery baselines. This contradicts AC6.5 ("Past days' strains are full days").
- **Suggested fix:** Also re-fetch any day whose `fetched_at` is before the end of that day (`fetchedAt < date+1 at start of day in zone`). Return `fetched_at` from `findBetween` for this check.

### [Major] Missing tests for acceptance criteria
- **Where:** `backend/src/main/kotlin/app/stravadashboard/domain/wellness/WellnessSync.kt:54` (untested), `backend/src/test/kotlin/app/stravadashboard/api/RingsRoutesTest.kt`, `backend/src/test/kotlin/app/stravadashboard/domain/rings/RingsSyncTest.kt`
- **Problem:** The spec's test plan names tests that don't exist:
  - `R3 - no HR calls when summaries fail` (AC3.3).
  - A `WellnessSync` test that a ring 429 saves `retryAfter` while `lastStatus` stays OK (AC3.1, AC3.2). `WellnessSyncTest` has no ring case.
  - `R7 - rings follow X-Person` with different stored values per person (AC7.4). `PersonIsolationTest` only adds `/api/rings` to a 401 route list.
  - `R2 - strain logs counts and status only` (AC2.9).
  - The "run repository never called" half of AC7.5.
  - No persistence test for `ExposedRingDayRepository.pruneBefore`.
- **Failure scenario:** Line 54 could be deleted or changed so it overwrites `lastStatus`, or the service could be wired to the other person's `ringDays`, and every test would still pass.
- **Suggested fix:** Add the named tests: a `WellnessSyncTest` case with a fake `RingDaySource` returning `RateLimited`, plus a two-person seeded `/api/rings` test.

### [Minor] An unexpected error in the ring step flips the 015 card to "unavailable"
- **Where:** `backend/src/main/kotlin/app/stravadashboard/domain/wellness/WellnessSync.kt:54` together with lines `32-34`
- **Problem:** `rings?.refresh(today)` runs inside the `try` of `refreshIfDue`. Any exception it throws (for example a locked SQLite file during `upsert`) reaches the generic catch, which calls `recordUnavailable()` and sets `lastStatus = UNAVAILABLE`.
- **Failure scenario:** Summaries succeed and the OK state is saved. Then a DB exception in `ExposedRingDayRepository.upsert` overwrites the state with UNAVAILABLE, and `GET /api/wellness` shows `"status":"unavailable"`. R3 says ring failures must never change the 015 card's status.
- **Suggested fix:** Catch around the rings call (rethrowing `CancellationException`), log the class name, and leave the wellness state as it is.

### [Minor] `fill` is computed from the unrounded value, which doesn't match AC7.6 and the contract example
- **Where:** `backend/src/main/kotlin/app/stravadashboard/api/RingsMappers.kt:32` and `:38`; the test pins it at `RingsRoutesTest.kt:113-115`
- **Problem:** AC7.6 says `fill` = sleep percent / 100 (and recovery / 100). The contract example pairs `"percent": 82` with `"fill": 0.82`, and `"value": 12.9` with `"fill": 0.614`. The code divides the unrounded value instead.
- **Failure scenario:** For sleep 81.55 the API returns `percent: 82, fill: 0.816`, and for strain 12.94 it returns `value: 12.9, fill: 0.616`. The test hard-codes these off-spec values. The visual difference is negligible, but the contract is wrong.
- **Suggested fix:** Build `fill` from the rounded `percent` and `value`, or amend AC7.6 and the example in `specs.md`.

### [Minor] Unrelated deployment changes are bundled into this feature
- **Where:** `backend/src/main/kotlin/app/stravadashboard/api/FrontendRoutes.kt`, `backend/src/main/kotlin/app/stravadashboard/Application.kt:88`, `backend/src/main/kotlin/app/stravadashboard/config/AppConfig.kt:35,115` (`FRONTEND_DIR`), `deploy/strava-dashboard.service`, `README.md:27-41`, `.run/*.xml`
- **Problem:** None of this is in `specs.md` for 020. The README deploy steps set `HOST=0.0.0.0` and serve over plain HTTP to the LAN. That bears on privacy items 11 and 12 (exposure, cookie `Secure`) and deserves its own spec and review. I could not check whether `.env.example` lists `FRONTEND_DIR`, because reading it was denied.
- **Failure scenario:** The serving and LAN-exposure change merges under a "rings" feature with no spec, no privacy section and no decision on record.
- **Suggested fix:** Split these into their own feature folder or PR with a spec and privacy check. Add `FRONTEND_DIR` to `.env.example`.

### [Nit] A zero-value ring draws a dot
- **Where:** `frontend/src/app/features/rings/rings-section.html:21-29`, `rings-section.scss:44`
- **Problem:** When `valueText` is non-null and `fill` is 0 (strain `0.0`, recovery `0`), the arc is still drawn. With `stroke-linecap: round`, a zero-length dash renders as a dot at 12 o'clock. This may be intended.

### [Nit] The no-LTHR null strain has no `shortcut:` comment
- **Where:** `backend/src/main/kotlin/app/stravadashboard/domain/rings/RingsSync.kt:55`
- **Problem:** The spec's build deviation says this limit gets a `shortcut:` code comment (days fetched before LTHR is set keep a null strain until pruned). The code has no such comment.

## Privacy checklist
1. ✅ No secrets in the diff. Fixtures use invented keys and values. `deploy/*.service` references the `.env` path only.
2. ✅ The Garmin key and token stay in the backend and go in the `Authorization` header only.
3. ✅ The existing one-retry-on-401 is reused in `fetchDay`. A rejected key stops the loop.
4. n/a
5. ✅ No Strava scopes change.
6. ✅ Only `sleep_s` and the derived strain are stored (`Tables.kt` `RingDaysTable`). HR samples stay in memory.
7. n/a. No GPS.
8. ✅ Logs carry the call name, status codes, class names and counts only (`RingsSync.kt:48`, `GarminWellnessSource` `map`/`unavailable`). No log-capture test exists (see the Major on missing tests).
9. ✅ No Logging plugin was added.
10. ✅ The route uses the existing error shapes.
11. ✅ for `/api/rings` (`hasConnectedSession`). The out-of-scope LAN deploy needs its own review (see the Minor on deployment changes).
12. ✅ The cookie is unchanged. Plain HTTP on the LAN means no `Secure` flag (see the Minor on deployment changes).
13. ✅ Interpolation only. No `innerHTML` or formatter.
14. n/a. GET only.
15. ✅ The response holds only ring-ready fields.
16. ✅ No new third party in the browser. Garmin is called from the backend.
17. ✅ No Strava data is used.
18. ❌ Existing gap, accepted in the spec: no in-app delete. There is a 90-day prune.
19. n/a
20. ✅ The README notes the SD-card data isn't backed up. Treatment is unchanged.
21. ✅ Invented fixtures, marked "All data here is invented".

## Summary
The domain maths, the API contract (the Kotlin DTOs match `rings.models.ts`) and the frontend rings match the spec. All checks pass. The main thing to fix is that a day cached part-way through (because the app was opened before the day ended or before the watch synced) is never re-fetched, which quietly skews sleep need and every 30-day baseline. The `WellnessSync` wiring and per-person isolation have no tests. Unrelated deployment and serving changes should be split out.

Not checked: I couldn't read `.env.example` (permission denied), and I didn't do the manual 360 px / 1280 px layout check for AC9.3.

---

## Resolution (2026-11-09)
No Blockers, so the reviewer was not re-run. After the fixes: backend ktlint, detekt and tests pass; frontend lint,
tests (77 files) and build pass.

| Finding | Outcome |
|---|---|
| [Major] Partial day stays partial | **Fixed.** `RingDayRepository.fetchedAt` is new. `RingsSync` re-fetches any day last fetched before that day ended (in the server zone), as well as today and yesterday. Test: `R2 - a day fetched before it ended is fetched again`. |
| [Major] Missing tests | **Fixed.** New `WellnessSyncRingsTest`: 429 sets `retryAfter` with status OK, a ring failure keeps OK and stored rows, an unexpected error keeps OK, no HR calls when summaries fail, and prune after 90 days on the real repository. `RingsSyncTest`: `R2 - strain logs counts and status only`. `PersonRoutesTest`: `020 R7 - rings follow X-Person`. **Not added:** "run repository never called" (AC7.5). `RingsService` takes no run repository, so it can't call one; the compiler enforces this. |
| [Minor] Ring error flips the 015 status | **Fixed.** `WellnessSync.refreshRings` catches unexpected exceptions (rethrowing `CancellationException`), logs only the class name, and leaves the state alone. |
| [Minor] `fill` from the unrounded value | **Fixed.** `fill` now comes from the rounded `percent` / `value`, matching AC7.6. Tests are updated to 0.82, 0.75 and 0.614. |
| [Minor] Unrelated deployment changes | **Not changed here.** `FrontendRoutes`, `FRONTEND_DIR`, `deploy/`, `.run/` and the README deploy steps were uncommitted before this feature started and aren't part of 020. They should be committed and reviewed separately, and `FRONTEND_DIR` should be added to `.env.example` there. |
| [Nit] Zero ring draws a dot | **Fixed.** The arc is drawn only when `fill > 0`. |
| [Nit] Missing `shortcut:` comment | **Fixed.** Added in `RingsSync.ringDay`. |

AC9.3 layout at 360 px and 1280 px: still a manual check, not yet done.
