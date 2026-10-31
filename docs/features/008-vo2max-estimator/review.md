# Review: 008-vo2max-estimator
**Base:** 0374050 → **Head:** 0374050 (the feature is uncommitted: I reviewed the working tree of `C:\Users\itsri\strava-dashboard-008` on `feature/008-vo2max-estimator` against the base)
**Verdict:** APPROVE WITH CHANGES

## Checks
- Backend: ktlint pass · detekt pass (0 issues) · tests 274 passed, 0 failed (Gradle reported `test` UP-TO-DATE against the current sources)
- Frontend: lint pass · tests 182 passed, 0 failed (32 files) · build pass

## Spec coverage
| Req | Implemented | Tested | Notes |
|---|---|---|---|
| R1 | ✅ | ✅ | AC1.1–AC1.4 in AppConfigTest. AC1.5 is in `.env.example` with the exact comment |
| R2 | ✅ | ✅ | Client, persistence, migration, refetch order, pending count and 429 are all tested |
| R3 | ✅ | ✅ | AC3.8 (private runs) has no test, but the domain model has no private flag, so there is no code path to test |
| R4 | ✅ | ⚠️ | AC4.4 and AC4.5 examples pass. The AC4.2 tie-break (lower lap index) has no test |
| R5 | ✅ | ✅ | |
| R6 | ✅ | ⚠️ | No calculator test for AC6.6 (a rep segment weighted by rep time rather than moving time) |
| R7 | ✅ | ✅ | |
| R8 | ⚠️ | ❌ | The test tolerance is wider than AC8.1/AC8.2 allow. The marathon result is outside ±3 s (see Major 1) |
| R9 | ✅ | ⚠️ | No test for AC9.5 "rate-limited / Strava unavailable → 200 from stored data" |
| R10 | ✅ | ⚠️ | AC10.1–AC10.4 tested. The planned `R10 - card uses theme tokens and palette` test (AC10.5) is missing |
| R11 | ✅ | ✅ | |
| R12 | ✅ | ✅ | |
| R13 | ✅ | ✅ | |
| R14 | ✅ | ⚠️ | With CR/EOL differences ignored, the AC14.1 files are unchanged and `git diff --stat` shows nothing for them. The planned `R14 - 001 rep pace and classification unchanged with lap heart rate stored` test is missing |

## Findings
### [Major] Race-time test tolerance widened beyond AC8.1/AC8.2, so the marathon prediction misses the spec
- **Where:** `backend/src/test/kotlin/app/stravadashboard/domain/vo2max/Vo2maxFormulasTest.kt:19-36` (`maxOf(3.0, expected * TABLE_TOLERANCE)` with `TABLE_TOLERANCE = 0.001`)
- **Problem:** AC8.1 and AC8.2 require every time to be within ±3 s of Daniels' table. The test allows 0.1 % instead: about 11 s for the marathon and 5–6 s for the half. Its own comment says the equation runs "up to ~0.08 %, i.e. 9 s on a marathon" away from the table. The implementation (`DanielsFormulas.raceTime`) follows the spec's formula exactly. So either the spec's expected values or its tolerance is wrong, and the test resolves that conflict quietly instead of in the spec.
- **Failure scenario:** For VDOT 50, solving `oxygenCost(42195/t)/pctMax(t) = 50` by hand gives t ≈ 190.66 min ≈ 11 440 s. That is about 9 s off the specified 11 449 s, so AC8.1 as written fails. The test still passes. A real regression of up to about 11 s would also pass unnoticed.
- **Suggested fix:** Correct AC8.1/AC8.2 in `specs.md` (expected values from the equation, or a percentage tolerance) and get the change approved. Then make the test match the spec exactly.

### [Major] Acceptance criteria with no test
- **Where:**
  - AC9.5: `backend/src/test/kotlin/app/stravadashboard/api/Vo2maxRoutesTest.kt` has no rate-limited or unavailable case.
  - AC6.6: `backend/src/test/kotlin/app/stravadashboard/domain/vo2max/Vo2maxCalculatorTest.kt` has only whole-run segments.
  - AC14.3: no test matches `R14 - 001 rep pace…`.
  - AC10.5: no test matches `R10 - card uses theme tokens and palette`.
- **Problem:** The test plan lists these tests, but they don't exist.
  - The `is SyncOutcome.Done` branch in `Vo2maxService.kt:48` is only exercised for OK syncs.
  - Calculator weighting is never checked for a REP_LAPS segment, where weighting by moving time instead of rep time would be a plausible bug.
  - No regression test stores laps with `averageHeartRateBpm` set and checks the outputs of `RepPaceCalculator` and `FastLapPaceFinder`.
- **Failure scenario:** Suppose a later change maps rate-limited syncs to an error, or weights rep segments by `movingTime`. All current tests would still pass.
- **Suggested fix:** Add four tests:
  - a route test where the Strava list call returns 429 or 500 and the response is still 200 with stored data;
  - a calculator test that mixes a whole-run segment with a 4x8min rep segment and checks the weighting uses 1920 s;
  - a 001 rep-pace test with HR on the laps;
  - a component test that checks `section.card` and the `palette.accent` series colour.

### [Minor] AC4.2 tie-break untested
- **Where:** `backend/src/main/kotlin/app/stravadashboard/domain/vo2max/RepLapFinder.kt` (`strongestFirst.thenBy { it.index }`)
- **Problem:** No test has two candidate laps with equal speed (outdoor) or equal HR (treadmill) when there are more than N candidates.
- **Failure scenario:** If `thenBy { it.index }` is dropped, the result depends on sort stability or input order, and no test fails.
- **Suggested fix:** Add one tie case to the `R4 - keeps N highest HR treadmill laps` test.

### [Nit] `SegmentMethod` is set but never read in production code
- **Where:** `backend/src/main/kotlin/app/stravadashboard/domain/vo2max/Vo2maxModels.kt` (`enum class SegmentMethod`), set in `SegmentExtractor.kt:44,62`
- **Problem:** The spec defines it, but no production code reads `Segment.method`. Only tests assert on it. Optional to keep.

### [Nit] `isVo2maxResponse` checks only the top-level shape
- **Where:** `frontend/src/app/core/api/vo2max.models.ts:53-66`
- **Problem:** It doesn't check the items of `predictions` or `weeks`. The spec says "a light shape check", and the backend contract is fixed, so this is acceptable.

## Privacy checklist
1. ✅ No secrets. `.env.example` has `MAX_HR_BPM=` with no value. Fixtures are invented (`activity-detail.json`, `vo2max-fixtures.ts`).
2. ✅ Token handling unchanged. The new response has no token fields.
3. ✅ The refetch reuses the existing detail path (`SyncService.fetchDetail`), with the existing refresh, revoke and 404 handling.
4. n/a OAuth flow unchanged.
5. ✅ No new scope. No new Strava endpoint.
6. ✅ Only `moving_time` and lap `average_heartrate` are added. No raw response is stored.
7. ✅ No GPS or streams are requested or stored.
8. ✅ No new log statements. `AppConfig.toString` masks max HR as `<default>`, `<set>` or `<invalid>`, and `Configured.toString()` is redacted.
9. ✅ Client logging unchanged.
10. ✅ The 422 message is fixed text without the value (`ErrorResponse.VO2MAX_INVALID_CONFIG`).
11. ✅ `/api/vo2max` checks `hasConnectedSession` (`Vo2maxRoutes.kt`).
12. n/a Cookie config unchanged.
13. ✅ No `[innerHTML]`. The tooltip formatter builds `<br/>`-joined text from dates and numbers only. Activity names and descriptions never reach the browser.
14. n/a GET only.
15. ✅ The response has only chart and tile fields (the route test checks the key sets).
16. ✅ No new dependency. No external calls.
17. n/a No change.
18. ❌ No disconnect/delete action yet. This was deferred before this feature and isn't caused by it.
19. n/a No webhook.
20. ⚠️ The DB now also holds per-lap HR, which is health data. Backups should be treated accordingly, as the spec notes.
21. ✅ Fixtures are invented.

## Summary
The implementation is clean, follows the spec closely and stays well layered. All backend and frontend checks pass, and the R3/R4 segment rules, sync refetch, migration and UI states are correct and well tested. The most important fix is the race-prediction tolerance. The spec's formula and its ±3 s table check disagree, by about 9 s on the marathon, and the test hides that by widening the tolerance; settle it in `specs.md`. After that, add the missing AC9.5, AC6.6, AC14.3 and AC10.5 tests. Note that the whole feature is still uncommitted on the branch (HEAD equals the base).

---

## Resolution (after review)

No Blockers, so the reviewer was not re-run. Backend checks: ktlint, detekt and 278 tests pass. Frontend checks: lint, 183 tests and the build pass.

| Finding | Outcome |
|---|---|
| [Major] Race-time tolerance wider than AC8.1/AC8.2 | **Fixed, spec updated with the user's approval.** `Vo2maxFormulasTest` now checks the solved Daniels equation within **±3 s** (VDOT 50: 1196 / 2480 / 5491 / 11 440 s; VDOT 40: 1446 / 3001 / 6654 / 13 777 s), so a regression of more than 3 s fails. It also checks Daniels' printed table within max(3 s, 0.1 %). The implementation follows the spec's formula exactly; the spec's table values are 1–9 s slower because Daniels' tables are smoothed. AC8.1/AC8.2 in `specs.md` now list the equation values with ±3 s and the table values with max(3 s, 0.1 %), matching the test (Note 9 updated too). |
| [Major] AC9.5 untested | **Fixed.** `Vo2maxRoutesTest` › `R9 - rate limited or unavailable sync still serves stored data`: Strava 429 and 500 both give 200, with the stored run's VO2max 57.2. |
| [Major] AC6.6 untested | **Fixed.** `Vo2maxCalculatorTest` › `R6 - rep segment weighted by rep time`: a 4x8min run with 3300 s moving time is weighted by its 1920 s of reps. |
| [Major] AC14.3 untested | **Fixed.** `LapHeartRateRegressionTest` › `008 R14 - 001 rep pace and classification unchanged with lap heart rate stored`. |
| [Major] AC10.5 untested | **Fixed.** `vo2max-section.spec` › `R10 - card uses theme tokens and palette`: the section has `.card`, and the card's component styles use `var(--color-*)` with no hex/rgb literals. The chart's `palette.accent` is covered by `R11 - weekly line in accent colour`. The 375 px / 1280 px layout still needs a manual check in a real browser. |
| [Minor] AC4.2 tie-break untested | **Fixed.** The treadmill test already had a 170 bpm tie (now named in the test), and `R4 - outdoor speed ties go to the lower lap index` was added. |
| [Nit] `SegmentMethod` never read in production | **Kept.** The spec defines it, and it documents which rule produced a segment (useful for the "why excluded" follow-up). |
| [Nit] Light shape check | **Kept as is,** per the spec's "light shape check". |

Spec deviation found during the build (R8's ±3 s against Daniels' table) is resolved: the spec now matches the formula it prescribes (see the first row).
