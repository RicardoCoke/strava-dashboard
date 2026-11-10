# Review: 022-sync-at-startup
**Base:** origin/main → **Head:** d0603a7 (plus uncommitted working-tree changes)
**Verdict:** APPROVE

## Checks
- Backend: ktlint pass · detekt pass (0 issues) · tests 732 passed, 0 failed (Gradle reported every task UP-TO-DATE, so these are the cached results from this working tree. StartupSyncTest ran its 4 tests.)
- Frontend: not run, because the diff does not touch `frontend/`

## Spec coverage
| Req | Implemented | Tested | Notes |
|---|---|---|---|
| R1 | ✅ | ✅ | AC1.1 and AC1.2 are covered together in one test (`StartupSyncTest.kt:95`). No separate test is named after AC1.2. |
| R2 | ✅ | ⚠️ | AC2.2 is covered. AC2.1 never checks the "nothing logged at warn/error" part (see Minor). |
| R3 | ✅ | ✅ | AC3.1 is covered (`StartupSyncTest.kt:132`). AC3.2 relies on the existing 009 tests, as the spec says. |
| R4 | ✅ | ❌ | There is no AC4.1 test. The spec marks it as Could and allows dropping it when no test seam exists. `launch` does not block and `startIfConnected()` already catches errors (`SyncRoutes.kt:43-51`). |

## Findings
### [Minor] AC2.1 does not check that nothing is logged at warn/error
- **Where:** `backend/src/test/kotlin/app/stravadashboard/api/StartupSyncTest.kt:118-122`
- **Problem:** The test only checks that `requests` is empty. AC2.1 also requires that nothing is logged at warn/error level.
- **Failure scenario:** Say a regression made `tokens.find()` throw at startup for a person who is not connected. `startIfConnected()` would catch the exception and write a warn line, but no Strava request would happen, so the test would still pass.
- **Suggested fix:** Attach a list appender to the `OtherPersonSync` logger and assert that it has no WARN or higher events. Or note in `review.md` that this part of AC2.1 is not tested.

### [Minor] A fixed sleep before moving the clock can make PersonRoutesTest flaky
- **Where:** `backend/src/test/kotlin/app/stravadashboard/api/PersonRoutesTest.kt:371-374`
- **Problem:** `passStartupSync` waits for the Strava call, sleeps `delay(100)`, then moves the clock forward 20 minutes. It assumes the startup job has written `lastSuccessfulSyncAt` within 100 ms. code-standards A8 says "no sleeps".
- **Failure scenario:** On a slow CI machine or a loaded Pi, the job may not have recorded its finish time when `clock.advance` runs. The job then records the advanced time, so the test's next `POST /api/sync` finds the sync not due and the R6 assertions fail now and then.
- **Suggested fix:** Use a bounded poll instead of the sleep. Wait until `ExposedSyncStateRepository(...).get().lastSuccessfulSyncAt != null` for each seeded person, as StartupSyncTest does, and only then move the clock.

### [Nit] Spec test names are not matched one-to-one
- **Where:** `backend/src/test/kotlin/app/stravadashboard/api/StartupSyncTest.kt:95`
- **Problem:** The spec lists two tests, `R1 - with stored tokens...` (Ricardo) and `R1 - Marina's stored tokens also trigger a startup sync`. The code merges them into one test that seeds both people. Coverage is equivalent, but tracing an AC to its test is harder.

## Privacy checklist
1. ✅ No secrets in the diff. The test tokens are invented strings.
2. ✅ No browser is involved. Tokens stay in the backend.
3. n/a. The refresh path is unchanged.
4. n/a
5. ✅ Scopes are unchanged.
6. ✅ No new stored fields.
7. ✅ No GPS data.
8. ✅ The only log line is the exception class name (`SyncRoutes.kt:49`).
9. ✅ Client logging config is unchanged.
10. ✅ No new error responses.
11. ✅ No new routes.
12. n/a
13. n/a
14. ✅ No new state-changing endpoints. The startup job is internal.
15. ✅ Responses are unchanged.
16. ✅ No new third parties. Only Strava is called.
17. ✅
18. n/a. Unchanged.
19. n/a
20. n/a
21. ✅ Fixtures are invented (athletes 7/8, "ricardo-access", "marina-secret").

## Summary
The change is the smallest that meets the spec. It is one line in `Application.kt:88` that calls the existing `startIfConnected()` for each person stack, which keeps the existing due rules and error handling. R1–R3 have meaningful `testApplication` tests, and AC4.1 was dropped as the spec allows. Nothing blocks merging. The most useful fix is replacing the `delay(100)` in `PersonRoutesTest.passStartupSync` with a poll so the R6 tests can't fail at random.

I did not re-run the backend tests from scratch (Gradle used cached up-to-date results) and did not run the frontend checks. Other test classes that seed tokens (`RoutesTest`, `PersonIsolationTest`, `NormalizedVo2maxRoutesTest`) now also trigger a startup sync. They pass, but I did not check them for timing races.

## Responses (2026-11-10)
- **[Minor] Fixed sleep in `passStartupSync`:** fixed. It now polls each seeded person's `lastSuccessfulSyncAt` (bounded) before moving the clock.
- **[Minor] AC2.1 log check:** not added. With no tokens, `startIfConnected()` only reads the token table and returns, and its catch path is already covered by 019's "other failure keeps response" test. A log appender here would test logback more than this feature.
- **[Nit] Merged R1 tests:** kept as one test. Ricardo and Marina are started by the same line, so one test seeding both covers AC1.1 and AC1.2.
- **Not checked (cached run):** the full backend suite was also run from scratch with `--rerun-tasks` (732 passed) before this review, and again after the fix.
