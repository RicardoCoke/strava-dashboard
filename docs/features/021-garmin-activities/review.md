# Review: 020-garmin-activities
**Base:** origin/main → **Head:** e8ef782 (plus uncommitted working-tree changes)
**Verdict:** APPROVE WITH CHANGES

## Checks
- Backend: ktlint pass · detekt pass (0 issues) · tests 648 passed, 0 failed
- Frontend: lint pass · tests 420 passed, 0 failed · build pass

## Spec coverage
| Req | Implemented | Tested | Notes |
|---|---|---|---|
| R1 | ✅ | ✅ | AC1.1–AC1.4 covered in RoutesTest / PersonRoutesTest |
| R2 | ✅ | ✅ | AC2.1, AC2.2 tested; proxy.conf.json now has only `/api` |
| R3 | ✅ | ✅ | AC3.1–AC3.6 covered |
| R4 | ✅ | ⚠️ | AC4.1 classifier parity has no test (see Major) |
| R5 | ✅ | ⚠️ | AC5.4 integration test is missing (see Major) |
| R6 | ⚠️ | ✅ | listAfter clamp leaves fresh stores without an incremental cursor (see Major) |
| R7 | ✅ | ✅ | AC7.1–AC7.6 covered |
| R8 | ✅ | ✅ | AC8.1–AC8.4 covered |
| R9 | ⚠️ | ✅ | `.env.example` not updated (see Major); only comments mention Strava in main code |

## Findings
### [Major] Fresh store never gets an incremental list cursor and re-lists everything since the cut-over on every sync
- **Where:** `backend/src/main/kotlin/app/stravadashboard/domain/SyncPolicy.kt:105-109` with `backend/src/main/kotlin/app/stravadashboard/domain/SyncService.kt:183`
- **Problem:** `listAfter` now returns `maxOf(after, cutOverStart)`. On a store where `historyStart` is null, the rule returns `required` (backfillStart), and the clamp lifts it to the cut-over. `listPass` only records `historyStart` when `after == backfillStart`, which never holds while the cut-over is later than the backfill start (about 7 months). So `historyImported` stays false and the cursor or 7-day branch is never used.
- **Failure scenario:** Start with a new `DB_PATH` file, or a new `MARINA_DB_PATH` (AC6.4 says "fresh or existing store"). Every 15-minute sync for the next ~7 months lists every Garmin run since 2026-11-10, not just the last 7 days, and upserts all of them again. Traffic grows with every run. Spec Risks also notes that Garmin may cap `limit` at 100 while `PAGE_SIZE` is 200. If so, `listed.size == PAGE_SIZE` is false after the first page. With `sortOrder=asc`, once there are more than 100 runs since the cut-over, newer runs are never listed (S2 broken).
- **Suggested fix:** In `listPass`, treat a pass whose `after` was clamped to the cut-over as a full history import. For example, set `historyStart` when `after == maxOf(backfillStart, cutOverStart)`. Add a two-sync test on an empty store that asserts the second list call uses the cursor or 7-day window.

### [Major] `.env.example` not updated
- **Where:** `.env.example` (not in `git diff origin/main --stat`)
- **Problem:** The spec's "Docs/config" section says to remove the Strava/session keys, add `GARMIN_ACTIVITIES_FROM` and `MARINA_GARMIN_ACTIVITIES_FROM` with no values, and note `HOST` for LAN use. The file is unchanged, so the two new keys introduced by this feature cannot be in it. AC9.3 also requires no `strava` matches in `.env.example`. My permissions don't allow reading the file, so I could not check this.
- **Failure scenario:** A user setting up from `.env.example` does not find the cut-over keys and may still be told to fill in `STRAVA_*`/`SESSION_SECRET` values that the app now ignores.
- **Suggested fix:** Update `.env.example` as the spec lists.

### [Major] No test for AC4.1 (Garmin description classifies like a Strava description)
- **Where:** `backend/src/test/kotlin/app/stravadashboard/garmin/GarminActivitySourceTest.kt:127-132`
- **Problem:** The test only checks that the description string and the moving time are carried through. No test runs `RunClassifier`, the workout/treadmill description lines or the marathon-pace counting on a Garmin-sourced run. The planned test `R4 - Garmin description classifies like a Strava description` does not exist.
- **Failure scenario:** A future change to how Garmin runs are mapped (for example `trainer` or `sportType`) changes classification, and no test fails.
- **Suggested fix:** Add the planned test: build a run from the Garmin adapter's summary and detail, and compare the `RunClassifier` result with an equivalent run built from the same fields.

### [Major] No integration test for AC5.4 (long outdoor workout gets stream pace, HR histogram and drift)
- **Where:** `backend/src/test/kotlin/app/stravadashboard/` (no test combines `SyncService` + `GarminActivitySource` + MockEngine + SQLite. `testsupport/GarminFake.kt:43` always answers the FIT download with 404)
- **Problem:** The planned test `R5 - a Garmin long workout gets stream pace histogram and drift` is missing. The only app-level Garmin fake has no FIT files, so no route test ever stores `streamPace`/`hrHistogram`/`hrDrift` from Garmin data.
- **Failure scenario:** A regression in the FIT path into the stored stream metrics (for example drift with `moving = null`) leaves `/api/long-run-drift`, `/api/vo2max` and `/api/efficiency-factor` empty for Garmin runs, and every test still passes.
- **Suggested fix:** Let `GarminFake` serve a `FitFixtures` zip for one long workout. Add the integration test asserting that the stored run has the three fields and appears in those endpoints.

### [Minor] A 404 from the token endpoint is no longer logged
- **Where:** `backend/src/main/kotlin/app/stravadashboard/garmin/GarminConnect.kt:137`
- **Problem:** `map` returns `NotFound` without logging, and `accessToken` turns it silently into `Unavailable`. Before the refactor, the wellness source's `else` branch logged `Garmin Connect token returned status 404`.
- **Failure scenario:** If Garmin moves the token endpoint, the sync shows `garminUnavailable` and leaves no log line saying why.
- **Suggested fix:** Log `"Garmin Connect token returned status 404"` in that branch.

### [Nit] Leftover test-only scaffolding
- **Where:** `backend/src/main/kotlin/app/stravadashboard/garmin/GarminWellnessSource.kt:25` (secondary constructor used only by tests); `frontend/src/app/core/sync/sync.store.spec.ts:24` (Router mock, no longer needed now the store never navigates)
- **Problem:** Both are dead weight now that the interceptor is gone and `GarminConnect` is injected.
- **Suggested fix:** Optional: delete them and build `GarminConnect` in the wellness tests.

### [Nit] "Strava" in main-source comments and one variable name
- **Where:** `backend/src/main/kotlin/app/stravadashboard/domain/vo2max/normalized/GradeAdjustment.kt:27` (`stravaSpeed`); comments in `config/AppConfig.kt:26,79`, `domain/CallBudget.kt:13`, `garmin/GarminFit.kt:56`
- **Problem:** Read strictly, AC9.3 says no matches outside the listed exemptions. These are historical references, not behaviour.
- **Suggested fix:** Optional: rename to `gradeAdjusted` and reword the comments as "cached history".

## Privacy checklist
1. ✅ No keys or secrets in the diff. Fixtures use invented keys and tokens (`invented-garmin-key`, `invented-access-token`).
2. ✅ The Garmin token is kept in memory in `GarminConnect` and is never in a DTO.
3. n/a (no OAuth)
4. n/a
5. n/a (Personal API key)
6. ✅ DTOs declare only the fields used (`GarminActivityDtos.kt`). No raw responses are stored.
7. ✅ `FitSample` has no position field. `GarminFit.samples` reads only timestamp, distance, speed, HR and altitude. Raw bytes are not persisted.
8. ✅ Logs carry the call name plus the status or exception class (`GarminConnect.kt:182,191,206`, `GarminFit.kt:125`).
9. ✅ No Ktor `Logging` plugin on the Garmin client.
10. ✅ `StatusPages` is unchanged and logs the path only.
11. ❌ by documented decision (no auth, spec Privacy item 11). `HOST` still defaults to 127.0.0.1 (`AppConfig.kt`).
12. n/a (no session cookie)
13. ✅ No `innerHTML` or `bypassSecurityTrust` was added.
14. ✅ `POST /api/sync` still checks XSRF (`SyncRoutes.kt:27`).
15. ✅ API DTOs are unchanged. CORS is not enabled.
16. ✅ `com.garmin:fit` is a bundled Maven dependency.
17. ✅ Data stays local.
18. ❌/n/a, as the spec says: no in-app delete.
19. n/a
20. ✅ unchanged
21. ✅ Fixtures are invented. FIT files are built in tests (`testsupport/FitFixtures.kt`).

## Summary
The Strava-to-Garmin swap is clean and well layered. `GarminConnect` is properly shared, the FIT parsing ignores positions, the 401/connect plumbing is fully removed, and all checks pass. The most important fix is the cut-over clamp in `SyncPolicy.listAfter`: on a fresh store it never lets the incremental cursor take over, so each sync re-lists every run since the cut-over and may stop seeing new runs if Garmin caps page size. After that, update `.env.example` and add the missing AC4.1 and AC5.4 tests.

---
Not checked: I could not read `.env.example` (permission denied), so its contents are inferred from it being absent from the diff. I did not verify which exceptions the FIT SDK throws on malformed input beyond the test cases. If it throws anything other than `IOException`/`FitRuntimeException`, one bad file would make every sync end as unavailable.

## Responses (2026-11-09)
- **[Major] Fresh store never gets an incremental cursor:** fixed. `SyncService.listPass` now records the import as complete (`historyStart = backfillStart`) when the pass started at `max(backfillStart, cut-over)`. Test: `021 R6 - a fresh store imports from the cut-over once, then lists incrementally` (it fails without the fix).
- **[Major] `.env.example` not updated:** not changed here. A deny rule in the user's settings blocks reading the file, so it was left alone and the changes are handed to Ricardo: remove `STRAVA_CLIENT_ID`, `STRAVA_CLIENT_SECRET`, `STRAVA_REDIRECT_URI`, `SESSION_SECRET`, `MARINA_STRAVA_CLIENT_ID` and `MARINA_STRAVA_CLIENT_SECRET`; add empty `GARMIN_ACTIVITIES_FROM=` and `MARINA_GARMIN_ACTIVITIES_FROM=` (default 2026-11-10); note that `HOST=0.0.0.0` serves on the LAN.
- **[Major] No AC4.1 test:** added `GarminSyncIntegrationTest.R4 - Garmin description classifies like a Strava description`. A run synced through the Garmin adapter classifies the same as one built from the same fields.
- **[Major] No AC5.4 integration test:** added `GarminSyncIntegrationTest.R5 - a Garmin workout and long run get stream pace, histograms and drift`. It covers Garmin JSON plus FIT through the real `SyncService` into SQLite. The workout ends up with stream pace, an HR histogram and 10 splits; the long run with a histogram and drift.
- **[Minor] Token 404 not logged:** fixed. It now logs `Garmin Connect token returned status 404`.
- **Not checked (FIT SDK exceptions):** fixed. `GarminFit.parse` treats any runtime exception from the decoder as an unreadable file, so one bad file can't fail every sync.
- **[Nit] Test-only scaffolding:** removed the secondary constructor (the wellness tests build `GarminConnect`) and the Router mock in `sync.store.spec.ts`.
- **[Nit] "Strava" in comments and a name:** renamed `stravaSpeed` to `gradeAdjusted`. The remaining comments describe the cached Strava history on purpose, so they stay.
- Re-review not needed: there were no Blockers. After the fixes: backend ktlint, detekt and tests pass (651 tests); frontend lint, tests (420) and build pass.
- **Ponytail review:** dropped the server-side `activityType=running` filter. Whether Garmin's parent-type filter includes treadmill and trail runs is undocumented, so the adapter filters types itself.
- **Renumbered to 021** when rebasing onto main, because 020 went to Whoop rings (PR #22). The review above was written under the old number.
