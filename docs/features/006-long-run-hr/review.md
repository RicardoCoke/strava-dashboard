# Review: 006-long-run-hr
**Base:** d058bf5 → **Head:** d058bf5 (uncommitted working tree)
**Verdict:** APPROVE

## Checks
- Backend: ktlint pass · detekt pass (0 issues) · tests 173 passed, 0 failed. Gradle reported every task as UP-TO-DATE, so these results come from a run on the current sources. The results include `LongRunHeartRateTest`: 13 cases, 0 failures.
- Frontend: lint pass · tests 124 passed, 0 failed · build pass

## Spec coverage
| Req | Implemented | Tested | Notes |
|---|---|---|---|
| R1 | ✅ | ✅ | AC1.1–AC1.4 in `RunClassifierTest`, AC1.5 in `LongRunHeartRateTest`. Named `RunType.LongRun`, which the spec allows. |
| R2 | ✅ | ✅ | The AC2.4 test checks `compute == compute`, which is always true. The `keys == ["easy","long"]` assertion is what actually covers it. |
| R3 | ✅ | ✅ | AC3.1–AC3.3 covered. |
| R4 | ✅ | ✅ | The AC4.1 test uses TODAY = 2026-10-29 (boundary 05-28/05-29) instead of the spec's 10-31 example. It tests the same boundary. |
| R5 | ✅ | ⚠️ | The `long` entry is covered by the contract test. HR `kind = "workout"` has no test (see Minor). AC5.2 is covered by the strict build. |
| R6 | ✅ | ✅ | AC6.1–AC6.4 in `long-run-hr.spec.ts`. |
| R7 | ✅ | ✅ | AC7.1/AC7.2 in `LongRunHeartRateTest` and `SyncServiceTest`. AC7.3 relies on the existing weekly-distance tests, which pass. |

## Findings
### [Minor] AC5.1 "workouts map to `workout`" is not tested
- **Where:** `backend/src/main/kotlin/app/stravadashboard/api/RunningDashboardMappers.kt:69`, `backend/src/test/kotlin/app/stravadashboard/api/RunningDashboardContractTest.kt:37-51`
- **Problem:** This change rewrote the `kind` mapping from `if/else` to an exhaustive `when`. The contract test's `heartRateTrend.types` only has an `easy` entry and a `long` entry. No backend test serialises a workout `HrTypeTrend`, so nothing checks the `"workout"` value.
- **Failure scenario:** If the workout branch were changed to return any other string (e.g. `"workouts"`), all backend tests would still pass. The frontend `HrRunType.kind` union would then get a value outside the contract.
- **Suggested fix:** Add a `HrTypeTrend(type = RunType.Workout(5, 6), ...)` entry to the contract fixture and expect `"kind": "workout"`.

### [Minor] Assertion that can never fail in the AC7.2 test
- **Where:** `backend/src/test/kotlin/app/stravadashboard/domain/LongRunHeartRateTest.kt:155`
- **Problem:** `PaceTypeComparison.type` has type `RunType.Workout` (`WorkoutPaceComparison.kt:22`), so `it.type == RunType.LongRun` is always false and `types.none { ... }` always passes. The real check is the `listOf("3x20min")` assertion on the line above.
- **Failure scenario:** None at runtime. It suggests a check exists when it does not, so a future edit could remove the real assertion and the test would still look like it covers AC7.2.
- **Suggested fix:** Remove the line, or replace it with an assertion on the number of runs counted across all entries.

### [Nit] AC2.4 test compares a computation with itself
- **Where:** `backend/src/test/kotlin/app/stravadashboard/domain/LongRunHeartRateTest.kt:58`
- **Problem:** `assertEquals(HeartRateTrend.compute(runs, TODAY), HeartRateTrend.compute(runs, TODAY))` is always true for a pure function. The spec's test plan does ask for this, but it adds nothing.
- **Suggested fix:** Drop the line and keep the `keys` assertion.

### [Nit] AC6.2 tooltip check does not use the long-run chart
- **Where:** `frontend/src/app/features/running-dashboard/long-run-hr.spec.ts:73`
- **Problem:** `hrPointTooltip` is called directly with a hand-built point. It is not called through the formatter of the long-run chart that was rendered. AC6.2's "same axes" is not asserted either. This is low risk, because the spec says the options builder is unchanged.
- **Suggested fix:** Optionally, call the series tooltip formatter from `chart.options()`.

## Privacy checklist
1 ✅ · 2 n/a · 3 n/a · 4 n/a · 5 ✅ (no scope change) · 6 ✅ (no new stored fields) · 7 ✅ (no GPS) · 8 ✅ (no new logging) · 9 n/a · 10 ✅ · 11 ✅ (existing route, no new endpoint) · 12 n/a · 13 ✅ (label is a server constant; no `innerHTML`; the tooltip is built from numbers and dates) · 14 n/a · 15 ✅ (only one enum value added) · 16 ✅ · 17 ✅ · 18 ✅ (unchanged) · 19 ✅ (unchanged) · 20 ✅ (unchanged) · 21 ✅ (invented distances, HR and dates)

## Summary
The change is small and follows the spec. The classifier rule uses raw metres with a strict `>` and lets a workout pattern win over distance. The type order (easy, then long, then workouts) is fixed with an exhaustive `when`, the mapper is exhaustive, and the TypeScript union matches the Kotlin values. Nothing else in the backend or frontend depends on `kind` in a way that would break. All checks pass. The most useful fix is a contract test that pins `kind = "workout"` for HR types, since that mapping was rewritten and nothing tests it.

## Resolution
No Blockers or Majors, so no re-review was needed. All four findings were fixed:
- **[Minor] AC5.1 workout `kind` untested:** fixed. The contract test fixture now has a `5x6min` HR entry and expects `"kind": "workout"`.
- **[Minor] Assertion that can never fail (AC7.2):** fixed. Replaced with a check that the runs counted across all pace entries (current + previous) total 1, so the long easy run is in none.
- **[Nit] AC2.4 compares a computation with itself:** fixed. The line is removed; the `keys == ["easy", "long"]` assertion stays.
- **[Nit] AC6.2 tooltip not from the rendered chart:** fixed. The test now calls the rendered long-run chart's tooltip formatter and asserts its time x-axis and "Avg HR (bpm)" y-axis.

Checks after fixes: backend ktlint, detekt, 173 tests pass; frontend lint, 124 tests and build pass.

### After merging main (005 training load)
- Conflict in `testsupport/TestData.kt`: both branches added a parameter to `summary()`; kept both (`distanceMeters` and `heartRate`).
- 005 makes sync fetch an HR stream for every run with HR, so the AC7.2 sync test now checks that a long run gets the same calls as an easy run and no pace-stream call, instead of exactly one detail call.
- Checks after merge: backend ktlint, detekt, 223 tests pass; frontend lint, 158 tests and build pass.
