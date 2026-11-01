# Review: 016-normalized-vo2max
**Base:** 3400ea0 → **Head:** deac8ef
**Verdict:** APPROVE WITH CHANGES

## Checks
- Backend: ktlint pass · detekt pass (0 issues) · tests 561 passed, 0 failed (Gradle reported the tasks UP-TO-DATE, so the counts come from `backend/build/test-results/test`)
- Frontend: lint pass · tests 377 passed, 0 failed (66 files) · build pass

## Spec coverage
| Req | Implemented | Tested | Notes |
|---|---|---|---|
| R1 | ✅ | ⚠️ | AC1.1–1.9 are tested. AC1.10 only tests 429 for the splits refetch (no 401, 5xx or 404 case). AC1.5 does not test that the lap-HR group comes before the splits group. |
| R2 | ✅ | ✅ | AC2.1–2.6 are covered in `GradeAdjustmentTest`. |
| R3 | ✅ | ✅ | All 7 rows and the pro-rating match the spec. AC3.1–3.11 are covered. |
| R4 | ✅ | ✅ | |
| R5 | ✅ | ✅ | Plain mean. `originalVo2max` is taken from `Vo2maxCalculator.compute`. AC5.1–5.7 are covered. |
| R6 | ✅ | ⚠️ | AC6.2 only checks the first and last notes. The 10K and half notes are not asserted to be null. |
| R7 | ✅ | ⚠️ | AC7.2 has no test for "a session for another athlete". |
| R8 | ⚠️ | ✅ | Tabs cannot be reached with the keyboard (see the Major finding). |
| R9 | ✅ | ✅ | Byte-identical check compares runs with and without stored splits. The Vo2 panel markup is unchanged. |
| R10 | ✅ | ✅ | |
| R11 | ✅ | ✅ | |
| R12 | ✅ | ✅ | |
| R13 | ✅ | ⚠️ | No test named `R13 - other reports unchanged with splits stored`. Coverage is indirect: existing suites now run with `splits = []` by default. |

## Findings
### [Major] Inactive tab can't be reached or opened with the keyboard
- **Where:** `frontend/src/app/features/vo2max/vo2max-section.html:11`, `:23` (also `vo2max-section.ts`, which has no key handler)
- **Problem:** The template uses roving tabindex: the inactive tab gets `tabindex="-1"`. But nothing implements the arrow-key half of that pattern. The only handler is `(click)`, and there is no `(keydown)` or host listener anywhere in `features/vo2max/`.
- **Failure scenario:** A keyboard or screen-reader user tabs into the card. Focus lands on "Vo2". Tab skips "Normalized Vo2" (`tabindex=-1`), and ArrowRight does nothing. That user can never open the normalized view, which breaks R8 ("Users can switch between them").
- **Suggested fix:** Add `(keydown)` on the tablist so ArrowLeft/ArrowRight/Home/End call `selectTab` and move focus. Or drop the `tabindex` bindings so both tabs are in the tab order. Add a spec test for it.

### [Minor] AC1.10 error paths not tested for the splits refetch
- **Where:** `backend/src/test/kotlin/app/stravadashboard/domain/SyncServiceTest.kt` (the new `016 R1 - …` tests)
- **Problem:** The spec's test plan and AC1.10 list 429, 401, 5xx/network and 404 for these detail calls. Only 429 is tested (`splits refetch counts as pending and 429 stops sync writing nothing`).
- **Failure scenario:** Suppose a future change in the `needsSplits` group's handling of 404 (run deleted) or 401 (`ReauthRequired`). No test covering this feature would fail.
- **Suggested fix:** Add 404 and 401 cases that start from a pre-016 stored run, i.e. use `storePre016Details`.

### [Minor] AC1.5 group order only partly tested
- **Where:** `backend/src/main/kotlin/app/stravadashboard/domain/SyncPolicy.kt:72-76`, `SyncServiceTest.kt` (`016 R1 - old outdoor details with HR are refetched once for splits`)
- **Problem:** The tests show that group 1 (no detail) comes before group 3 (splits), and that a run is planned once. Nothing checks that group 2 (`needsLapHeartRate`) comes before group 3.
- **Failure scenario:** If the order of the `needsLapHeartRate` and `needsSplits` filters were swapped, all tests would still pass, even though the spec requires that order under the shared budget.
- **Suggested fix:** Add a test with one lap-HR-pending run and one splits-pending newer run, and assert the call order.

### [Minor] Pace limits duplicated instead of reused
- **Where:** `backend/src/main/kotlin/app/stravadashboard/domain/vo2max/normalized/NormalizedSegmentExtractor.kt:20-21`
- **Problem:** The spec says "008 constants are reused" (pace limits 120–720 s/km). Instead, `MIN_PACE_SECONDS` and `MAX_PACE_SECONDS` are re-declared, because they are `private` in `SegmentExtractor`.
- **Failure scenario:** Someone tunes the 008 limits. The normalized estimate keeps the old bounds without notice, so the two tabs exclude different runs.
- **Suggested fix:** Make the `SegmentExtractor` constants `internal` (or public) and reference them.

### [Minor] Period, prediction and pending rules duplicated from 008
- **Where:** `backend/src/main/kotlin/app/stravadashboard/domain/vo2max/normalized/NormalizedVo2maxCalculator.kt:65`, `:79`, `:88`
- **Problem:** `current()`, `predictions()` and `isPending()` copy `Vo2maxCalculator`'s logic almost line for line. Only the mean and one extra `needsSplits` term differ. The spec explicitly allows a shared internal helper.
- **Failure scenario:** A change to the 7/28-day fallback or the rounding of predictions in 008 is not mirrored. The tabs then silently use different rules, while the spec says they are "exactly 008 R6/R7".
- **Suggested fix:** Extract the period selection and predictions into an internal helper that takes the mean function as a parameter.

### [Minor] Missing tests for AC7.2 (other athlete), AC6.2 (non-marathon notes) and AC13.2
- **Where:** `backend/src/test/kotlin/app/stravadashboard/api/NormalizedVo2maxRoutesTest.kt:139`, `:175-176`
- **Problem:**
  - The 401 test only covers "no session", not a session for a different athlete.
  - Only `predictions.first()` and `last()` notes are checked.
  - There is no `R13 - other reports unchanged with splits stored` test.
- **Failure scenario:** A wrong note on 10K or half, or a 200 response for a stale session from another athlete, would not be caught.
- **Suggested fix:**
  - Assert the notes for all four predictions.
  - Add an other-athlete session case, as in `Vo2maxRoutesTest`.
  - Add one R13 test that compares another report with non-empty splits stored.

### [Nit] Awkward line breaks left by the formatter
- **Where:**
  - `backend/src/main/kotlin/app/stravadashboard/persistence/ExposedRunRepository.kt:137` (`if (row[RunsTable.splitsSaved] ==\n true\n)`)
  - `NormalizedSegmentExtractor.kt:38` (`earlyPieces(\n splits,\n )`)
  - `NormalizedSegmentExtractor.kt:58` (`split to\n it`)
- **Problem:** Hard to read.
- **Failure scenario:** None functional.
- **Suggested fix:** Pull the expressions into local vals, e.g. `val runSplits = splits[id].orEmpty().sortedBy { it.index }.takeIf { row[RunsTable.splitsSaved] == true }`.

### [Nit] Spec status changed in the implementation commit
- **Where:** `docs/features/016-normalized-vo2max/specs.md:3`
- **Problem:** The build commit flips `Status: Draft` to `Approved`. Spec approval is the user's step, so it should not be bundled with the code change.
- **Failure scenario:** None functional.
- **Suggested fix:** Record approval separately, or leave it to the user.

## Privacy checklist
1. ✅ No secrets. The fixture `activity-detail.json` has invented split values.
2. ✅ No tokens in the new DTO or response. `NormalizedVo2maxResponse` has no ids or per-run data (tested).
3. ✅ Uses the existing detail-call refresh/revoke path.
4. n/a OAuth unchanged.
5. ✅ No scope change. The detail request is unchanged.
6. ✅ Only the six split fields are stored (`RunSplitsTable`). `elapsed_time`, `average_speed` and `pace_zone` are ignored.
7. ✅ No GPS, polylines or streams are requested or stored.
8. ✅ No new logging of HR, splits or VO2max.
9. ✅ Client logging unchanged.
10. ✅ Standard error shape (`ErrorResponse.NOT_CONNECTED` / `VO2MAX_INVALID_CONFIG`).
11. ✅ The route checks `hasConnectedSession` (`NormalizedVo2maxRoutes.kt:16`). The other-athlete case is untested (see Minor).
12. n/a No cookie change.
13. ✅ Tooltip lines are built only from dates and numbers. Joining with `<br/>` adds no Strava text, and no `[innerHTML]` is used.
14. n/a GET only.
15. ✅ The response holds chart and tile fields only.
16. ✅ No new dependency or external call.
17. ✅ Unchanged.
18. ❌ (deferred, carried from 001) There is still no disconnect/delete action. The new `run_splits` rows are covered by `deleteAll` (tested).
19. n/a No webhook.
20. ⚠️ The DB now holds per-km heart rate. Treat backups as health data.
21. ✅ Invented data in all new fixtures and tests.

## Summary
The backend matches the spec closely: grade adjustment, the 60-minute early-split segment, the one-vote mean, migration, refetch planning and deletion are all correct, and every check passes. The most important fix is keyboard access to the tabs: the inactive tab has `tabindex="-1"` and there is no arrow-key handler, so keyboard users can't reach "Normalized Vo2". The rest is test gaps (AC1.10, AC1.5 group order, AC7.2) and small duplications of 008 logic.

---

## Resolution (after review)
No Blockers, so the reviewer was not re-run. Checks after the fixes: backend ktlint, detekt and tests pass (567 tests, 0 failures); frontend lint, tests (378 passed) and build pass.

| Finding | Status | What changed |
|---|---|---|
| [Major] Keyboard access to tabs | Fixed | Tab buttons handle ArrowLeft/ArrowRight/Home/End: they select the tab and move focus (roving tabindex now complete). `aria-controls` only points at the rendered panel. Test: `R8 - tabs work with the keyboard`. |
| [Minor] AC1.10 error paths | Fixed | Added `016 R1 - 404 on splits refetch deletes the run`, `… revoked access … requires reauth and writes nothing`, `… unavailable … leaves the run pending` (429 was already covered). |
| [Minor] AC1.5 group order | Fixed | Added `016 R1 - lap heart rate refetch comes before splits refetch` (an older lap-HR run is fetched before a newer splits-only run). |
| [Minor] Pace limits duplicated | Fixed | `SegmentExtractor.MIN_PACE_SECONDS` / `MAX_PACE_SECONDS` are now public and reused by `NormalizedSegmentExtractor`. |
| [Minor] Period, prediction and pending rules duplicated | Fixed | `Vo2maxCalculator` exposes internal `currentOf` (takes the mean function), `predictions` and `isPending` (takes an extra pending rule); the normalized calculator uses them. 008 output and tests unchanged. |
| [Minor] Missing tests for AC7.2, AC6.2, AC13.2 | Fixed | Added `R7 - 401 for a session of another athlete`; the 200 test asserts all four notes; added `R13 - other reports unchanged with splits stored` (running dashboard, training load, efficiency factor, long-run drift, marathon pace: identical JSON with and without stored splits). |
| [Nit] Awkward line breaks | Fixed | Extracted local vals / a small helper (`earlySegmentOrWholeRun`) so the formatter keeps them on one line. |
| [Nit] Spec status in implementation commit | Not changed | The user approved the spec in the conversation ("Yes to all") before the build started; the status line only records that approval. |
