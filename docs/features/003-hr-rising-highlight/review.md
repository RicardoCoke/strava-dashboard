# Review: 003-hr-rising-highlight
**Base:** d009a34 → **Head:** d009a34 (all changes are uncommitted in the working tree; `HrRisingTrend.kt`, `HrRisingTrendTest.kt` and `heart-rate-rising.options.spec.ts` are untracked; the working tree also holds 001/002/004 work, and only the files that belong to 003 were reviewed)
**Verdict:** APPROVE

## Checks
- Backend: ktlint pass · detekt pass (0 issues) · tests 149 passed, 0 failed (Gradle reported every task UP-TO-DATE, so these are cached results from `build/test-results`, including `HrRisingTrendTest`)
- Frontend: lint pass · tests 105 passed, 0 failed (20 files) · build pass

## Spec coverage
| Req | Implemented | Tested | Notes |
|---|---|---|---|
| R1 | ✅ | ⚠️ | Tested only through the flags. No test asserts the A[i] values of AC1.2 (see Minor 1). AC1.3 and AC1.4 are tested in `AggregationsTest.kt:201,209` |
| R2 | ✅ | ✅ | `HrRisingTrendTest` covers AC2.1–AC2.10. I checked the state machine by hand against Example A, AC2.6–AC2.9 and the flat/single-fall case |
| R3 | ✅ | ✅ | `RoutesTest.kt:297` checks the exact key set and a JSON boolean. `RunningDashboardContractTest.kt:79` covers the full contract |
| R4 | ✅ | ✅ | `heart-rate-rising.options.spec.ts` covers AC4.1–AC4.5. `chart-theme.spec.ts:52-55` was updated |
| R5 | ✅ | ✅ | `heart-rate-rising.options.spec.ts:77` |
| R6 | ✅ | ✅ | Backend `AggregationsTest.kt:216`. Frontend `dashboard-cards.spec.ts:77` |
| R7 | ✅ | ✅ (review check) | `palette.worse` appears only in `palette.ts`, the `.tone-worse` / `--color-worse` styles and `heart-rate-trend.options.ts:39,56`. The 002 AC8.4 note is at `docs/features/002-dark-dashboard-redesign/specs.md:85`. The 001 note is at `docs/features/001-running-dashboard/specs.md:99` |

## Findings
### [Minor] The R1 test does not check the rolling averages, and its comment claims something the test can't show
- **Where:** `backend/src/test/kotlin/app/stravadashboard/domain/HrRisingTrendTest.kt:15-21`
- **Problem:** The test named for AC1.1/AC1.2 makes the same assertion as the Example A test at line 34: `pattern("FFFTTTTFFF")` on `exampleA`. The comment says the turn at point 4 "only holds when the averages are rounded to one decimal". That is not true: the unrounded mean of 151, 152, 150 is exactly 151.0, so the check 151.0 − 150.0 ≥ 1.0 passes either way. The 150.04 case only proves that small noise doesn't flip the state. No test checks `A[2..9] = 150.0, 150.3, …, 146.7`.
- **Failure scenario:** Change `rollingTenths` (`HrRisingTrend.kt:42-43`) so it no longer rounds to one decimal first, for example `roundHalfUpToInt(avg * 10)` with a different rounding step, or a window off by one that happens to give the same flags for Example A. AC1.2 would then be broken and this test would still pass.
- **Suggested fix:** Make `rollingTenths` `internal` and assert the exact AC1.2 list `[1500, 1503, 1510, 1520, 1530, 1517, 1493, 1467]`. Drop the wrong comment, or drop the duplicate assertion.

### [Nit] Unnamed loop start index
- **Where:** `backend/src/main/kotlin/app/stravadashboard/domain/HrRisingTrend.kt:28`
- **Problem:** `for (k in 2 until averages.size)` uses a bare `2`, which means "a flip needs two previous steps". `offset` is derived from `WINDOW_RUNS`, but this number has no name (A2: no magic numbers). It works correctly.
- **Suggested fix:** `private const val STEPS_TO_CONFIRM = 2`, and loop from it.

### [Nit] Tests for AC1.3 and AC1.4 can't fail through the code they target
- **Where:** `backend/src/test/kotlin/app/stravadashboard/domain/AggregationsTest.kt:201-213`
- **Problem:** `typeTrend` already filters out runs without HR (`HeartRateTrend.kt:71`) and runs outside the window (`HeartRateTrend.kt:53`) before it calls `HrRisingTrend.flags`. If those filters regressed, the 001 tests would catch it first. These tests are still fine as regression guards. Optional.

## Privacy checklist
1. ✅ No secrets in the 003 files or fixtures
2. n/a
3. n/a
4. n/a
5. ✅ No scope change
6. ✅ Nothing new stored. The flags are computed per request in `HeartRateTrend.kt:72`
7. n/a, no GPS used
8. ✅ No logging in `HrRisingTrend.kt` or in the changed `HeartRateTrend.kt`
9. ✅ No client logging change
10. ✅ No new error paths
11. ✅ Same session-guarded route
12. n/a
13. ✅ The tooltip formatter is unchanged. The stretch series are `silent: true` (`heart-rate-trend.options.ts:64`), so no new HTML is built from Strava text
14. n/a, GET only
15. ✅ Only `rising: Boolean` is added (`RunningDashboardDtos.kt:65`). The averages are not exposed
16. ✅ No new dependencies
17. n/a
18. n/a
19. n/a
20. n/a
21. ✅ Fixtures use the invented bpm examples from the spec

## Summary
The implementation matches the spec closely. The hysteresis state machine in `HrRisingTrend.kt` is correct: I traced it by hand for Example A, AC2.6–AC2.9 and the flat/single-fall case. The Kotlin DTO and the TypeScript interface agree, and the stretch rendering shares joining points as AC4.2 requires. The most useful fix is to make the AC1.1/AC1.2 test assert the actual rolling averages instead of repeating the Example A flag check.

## Resolution
Checks after the fixes: backend `ktlintCheck detekt test` pass.

| Finding | Outcome |
|---|---|
| [Minor] R1 test doesn't check the rolling averages | Fixed: `rollingTenths` is `internal` and the R1 test asserts the AC1.2 list `[1500, 1503, 1510, 1520, 1530, 1517, 1493, 1467]`. The wrong comment and the duplicate Example A assertion are gone. |
| [Nit] Unnamed loop start index | Fixed: `STEPS_TO_CONFIRM = 2`. |
| [Nit] AC1.3/AC1.4 tests can't fail through the code they target | Kept as regression guards: they pin the spec's promise that the rolling window only spans plotted, in-window points, which would matter if the filtering ever moved. |
