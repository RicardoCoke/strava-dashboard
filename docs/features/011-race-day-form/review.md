# Review: 011-race-day-form
**Base:** bb7f0ee → **Head:** eab1446
**Verdict:** APPROVE WITH CHANGES

## Checks
- Backend: ktlint pass · detekt pass (0 issues) · tests 363 passed, 0 failed (Gradle reported all tasks UP-TO-DATE, so these are the cached results for the current sources)
- Frontend: lint pass · tests 249 passed, 0 failed · build pass

## Spec coverage
| Req | Implemented | Tested | Notes |
|---|---|---|---|
| R1 | ✅ | ⚠ | AC1.1 tested. AC1.2: the after-race-day case is tested, but `raceDate < chartStart → raceDay null` is not. AC1.3 (another race in the plan is a normal planned day) has no test. |
| R2 | ✅ | ⚠ | AC2.1 is tested against `TrainingLoadCalculator`. AC2.2 (a past planned session that wasn't run does not change actual load) has no test. AC2.3 is only covered indirectly, by the run-today-without-HR case. |
| R3 | ✅ | ✅ | |
| R4 | ✅ | ✅ | |
| R5 | ✅ | ✅ | The race day being actual (AC5.4, second half) is only covered indirectly by the after-race-day test. |
| R6 | ✅ | ✅ | |
| R7 | ✅ | ✅ | |
| R8 | ✅ | ✅ | |
| R9 | ✅ | ✅ | |
| R10 | ✅ | ✅ | AC10.6 is checked in the 200 test (`stravaRequests.isEmpty()`). |
| R11 | ✅ | ⚠ | Only AC11.2 is tested (`raceDate` changes). AC11.1 (planned loads change after a plan edit) is not checked directly. |
| R12 | ✅ | ⚠ | AC12.1, AC12.4 and AC12.5 are tested. AC12.2 (exactly one request from the page) and AC12.3 (fails independently) have no tests, because every page spec stubs the section. |
| R13 | ✅ | ✅ | |
| R14 | ✅ | ✅ | |
| R15 | ✅ | ✅ | |
| R16 | ✅ | ⚠ | The advice-word test (AC16.7) skips the invalid-config and error states. Of the AC16.2 texts, only the LTHR one is checked. |
| R17 | ✅ | ✅ | The palette is tested; the layout needs a manual check. |
| R18 | ✅ | ✅ | `rollingAverage` is only made public. MarkLine is registered. The stub is used in page specs. None of the protected files changed. |

## Findings
### [Major] Several acceptance criteria have no test
- **Where:** `backend/src/test/kotlin/app/stravadashboard/domain/racedayform/RaceDayFormProjectorTest.kt` (no tests for AC1.2-null, AC1.3 or AC2.2); `frontend/src/app/testing/race-day-form-stub.ts:11` (every page spec replaces the section, so AC12.2 and AC12.3 are never exercised)
- **Problem:** The spec's test plan names `R1 - other races are normal planned days`, `R2 - skipped sessions use actual load`, `R12 - one race-day request` and `R12 - fails independently`. None of these exist. The `raceDate < chartStart` branch of AC1.2 is also untested.
- **Failure scenario:** Suppose a later change makes `plannedLoads` or `combined` read plan days for dates up to `lastActual`. For example, someone builds the plan map over the whole chart instead of from `lastActual + 1` (`RaceDayFormProjector.kt:31-32`). Past skipped sessions would then add planned load to actual days, and no test would fail. In the same way, if the page wired the card so that its error blocked other cards, or it made two requests, the suite would still pass.
- **Suggested fix:** Add projector tests for AC1.2-null, AC1.3 and AC2.2. Add one page spec that uses the real `RaceDayFormSection`, expects a single `/api/race-day-form` request, fails it, and asserts that the other cards still render.

### [Minor] AC11.1 is only proven through the race date
- **Where:** `backend/src/test/kotlin/app/stravadashboard/api/RaceDayFormRoutesTest.kt:282-303`
- **Problem:** The R11 test swaps to a different plan and checks only `raceDate`. Nothing checks that a changed day's planned `load` / `fitness` change on the next request, as AC11.1 requires.
- **Failure scenario:** If the planned loads were cached while the calendar end date was not, the test would still pass.
- **Suggested fix:** Change one day's easy minutes between two requests and assert that that day's `load` changes.

### [Minor] Planned `RaceDayFormServiceTest.kt` missing
- **Where:** `backend/src/test/kotlin/app/stravadashboard/domain/racedayform/` (file is absent; the spec's "Files expected to change" lists it)
- **Problem:** The precedence rules in `problemOf` (`RaceDayFormService.kt:56-66`) are tested only through the route tests. The "LTHR missing + plan invalid" and "LTHR invalid + plan unreadable" combinations are never exercised.
- **Failure scenario:** If the `when` branches were reordered so the plan is checked before the LTHR, "LTHR invalid + plan unavailable" would return 503 instead of 422 (AC10.4). No test would catch it.
- **Suggested fix:** Add a small service unit test with fakes that covers the full matrix of missing, invalid and unavailable settings.

### [Minor] The advice-word test does not cover every state
- **Where:** `frontend/src/app/features/race-day-form/race-day-form-section.spec.ts:185-199`
- **Problem:** AC16.7 says one test checks all states. This one collects loading, not-configured (both missing), projected and notEnoughRuns. It leaves out invalid config, error, and the "plan" and "lthr" not-configured texts. The AC16.2 texts for `plan` and `both` are never asserted.
- **Failure scenario:** An edit to `notConfiguredText('plan')` or the error text that added "should" would pass the suite.
- **Suggested fix:** Add the 422 and 503 states and all three not-configured codes to the loop, and assert each AC16.2 string.

### [Minor] Pace passed as a raw `Double` instead of `SecondsPerKm`
- **Where:** `backend/src/main/kotlin/app/stravadashboard/domain/racedayform/RaceDayForm.kt:22`, `PlannedLoad.kt:18`, `PlannedLoad.kt:31`
- **Problem:** The spec's design uses `easyPace: SecondsPerKm?`, and code-standards A2 asks for value classes for units. The code passes an untyped `Double?` between `LoadRateEstimator`, `PlannedLoad` and `RaceDayFormProjector`.
- **Failure scenario:** A caller could pass min/km or s/m, and nothing would flag it at compile time.
- **Suggested fix:** Use `SecondsPerKm`, or a dedicated value class if fractional seconds have to be kept.

### [Nit] Duplicate `METERS_PER_KM` constant
- **Where:** `backend/src/main/kotlin/app/stravadashboard/domain/racedayform/LoadRateEstimator.kt:20`
- **Problem:** This repeats `const val METERS_PER_KM` from `domain/model/Units.kt:6`.
- **Suggested fix:** Import the shared constant.

## Privacy checklist
1. ✅ No secrets; fixtures are invented (`RaceDayFormFixtures.kt`, `race-day-form-fixtures.ts`).
2. ✅ No token handling changed; the response has no tokens.
3. n/a (unchanged)
4. n/a (unchanged)
5. ✅ No new scope.
6. ✅ No new stored fields or tables.
7. ✅ No GPS used.
8. ✅ No new log lines (the only logging is the existing `TrainingPlanService.invalid`).
9. ✅ No client logging changes.
10. ✅ Fixed error texts; the 422 for an invalid plan does not echo the parser reason (`ErrorResponse.kt`).
11. ✅ The route checks `hasConnectedSession` (`RaceDayFormRoutes.kt:17`); the service also checks tokens.
12. n/a (unchanged)
13. ✅ The tooltip is built only from dates and numbers (`race-day-form.options.ts:23-33`); plain interpolation, no `[innerHTML]`.
14. n/a (GET only)
15. ✅ The response has only chart fields; rates, names, bpm and IDs are left out (verified by the key-set assertions in `RaceDayFormRoutesTest.kt`).
16. ✅ No new dependency or external call.
17. ✅ Unchanged.
18. ❌ Deferred: still no disconnect/delete action (pre-existing, recorded in the spec).
19. n/a
20. ⚠ Unchanged: DB backups contain HR-derived data.
21. ✅ Invented test data.

## Summary
The implementation closely follows the spec. The backend projector, estimator, planned-load and mapper logic match the acceptance criteria, the Kotlin DTOs and TypeScript interfaces match, and all backend and frontend checks pass. No correctness or privacy defects were found. The most important fix is to add the missing tests for AC1.2-null, AC1.3, AC2.2, AC12.2 and AC12.3, so the actual/planned split and the card's independence are protected against regressions.

---

## Resolution (after review)

No Blockers, so the reviewer was not re-run. After the fixes: backend ktlint, detekt and 369 tests pass; frontend lint, 253 tests and the build pass.

| Finding | Outcome |
|---|---|
| [Major] Acceptance criteria without tests | **Fixed.** `RaceDayFormProjectorTest`: `R1 - race long before the chart has no race day` (AC1.2), `R1 - other races are normal planned days` (AC1.3), `R2 - skipped sessions use actual load` (AC2.2). New `running-dashboard-page.race-day-form.spec.ts` with the real card: `R12 - one race-day request` (AC12.2), `R12 - fails independently` and `R12 - card renders its own state while the dashboard fails` (AC12.3). |
| [Minor] AC11.1 only proven through the race date | **Fixed.** `RaceDayFormRoutesTest` › `R11 - plan edit changes projection on next request`: race week Wednesday goes from 40 to 30 easy minutes in the CSV and that day's load goes from 40.0 to 30.0 on the next request. |
| [Minor] `RaceDayFormServiceTest.kt` missing | **Fixed.** Covers not connected, and the full missing / invalid / unreadable matrix (11 combinations), including LTHR invalid + plan unreadable → invalid LTHR. |
| [Minor] Advice-word test misses states | **Fixed.** `R16 - no advice words in any state` now covers loading, all three not-configured codes, both 422 texts, 503, projected with the SubT note, and not-enough-runs. `R16 - not configured texts for each missing setting` asserts each AC16.2 string. |
| [Minor] Pace as a raw `Double` | **Fixed.** New value class `EasyPace(secondsPerKm: Double)` between the estimator, `PlannedLoad` and the projector. It stays fractional because it is an average or a range midpoint (`SecondsPerKm` is whole seconds). |
| [Nit] Duplicate `METERS_PER_KM` | **Fixed.** Uses the shared constant from `domain/model/Units.kt`. |
