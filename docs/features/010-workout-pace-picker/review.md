# Review: 010-workout-pace-picker
**Base:** 2d4a2a4 → **Head:** e2ed54f
**Verdict:** APPROVE

Note: the local `main` ref is stale (a3e6eba, before the 009 merge), so `git diff main...HEAD` also pulls in all of 009. I diffed against the stated base, `2d4a2a4...HEAD`: 39 files, all of them 010 work.

## Checks
- Backend: ktlint pass · detekt pass (0 issues) · tests 334 passed, 0 failed (counted from `build/test-results`; Gradle reported the tasks UP-TO-DATE, so the results match the current sources)
- Frontend: lint pass · tests 227 passed, 0 failed (38 files) · build pass

## Spec coverage
| Req | Implemented | Tested | Notes |
|---|---|---|---|
| R1 | ✅ | ✅ | `WorkoutPaceComparison.kt:66-77` uses `HeartRateTrend.WINDOW_MONTHS` (now public). AC1.1–1.6 are in `WorkoutPacePickerTest`. AC1.7 is in `RoutesTest` (`010 R1 - pace window matches heart rate window and types carry points`). |
| R2 | ✅ | ✅ | Points sorted by `startLocal` and keep only runs with a rep pace. `runsWithoutPace` is now `runCount - points.size`. AC2.1–2.8 are covered, including the same-day pair, the treadmill description line and the local-date case near midnight. The contract test updates the JSON shape. |
| R3 | ✅ | ✅ | The 30-day window rules are unchanged. AC3.2 and AC3.4 are tested. AC3.1 still relies on the existing 001 window tests. |
| R4 | ✅ | ✅ | Selector, singular/plural run counts, first type preselected, emit and switch, page keeps the choice on reload and falls back when the type is gone. |
| R5 | ✅ | ✅ | Time x-axis, y-axis name and `m:ss` labels, tooltip text, single point, switching type. |
| R6 | ✅ | ✅ | Outdoor is an accent circle, treadmill is a treadmill-coloured triangle, and a series with no points is left out of the series and the legend. |
| R7 | ✅ | ✅ | Summary line with tone (better/worse/neutral). The "not enough data" text is tested for each missing window. |
| R8 | ✅ | ✅ | Singular and plural text, and no note when the count is 0. |
| R9 | ✅ | ✅ | Empty state, no-points state, heading. Page-level loading and error behaviour is unchanged. |
| R10 | ✅ | ✅ | `notes` removed from the domain, the DTO, the TS model, `isTrainingPlanResponse` and `TargetPaces`. `notesColumn()` is kept as the pace boundary (`004 R3 - note text mentioning pace is not a pace` still passes). The JSON key test is updated. |

## Findings
### [Nit] Spec helper `paceSummaryText` was split into two functions
- **Where:** `frontend/src/app/features/running-dashboard/chart-options/workout-pace.options.ts:28-41`
- **Problem:** The spec names `paceSummaryText(type)`. The code has `paceSummaryPrefix` plus `paceChangeText` instead, so the template can wrap the change in a tone span. The behaviour matches AC7.1–7.2.
- **Failure scenario:** None at runtime. Someone looking for the function the spec names won't find it.
- **Suggested fix:** Keep the split and note the change in `review.md`.

### [Nit] Duplicate and redundant styles
- **Where:** `frontend/src/app/features/running-dashboard/workout-pace-comparison-chart/workout-pace-comparison-chart.ts:59-69`
- **Problem:** The `.type-picker` block copies `heart-rate-trend-chart.ts:46-51` line for line. The local `.muted` rule repeats the global `.note { color: var(--color-text-muted) }` in `frontend/src/styles.scss:101-104`.
- **Failure scenario:** If the picker style changes in one card, the two cards stop matching.
- **Suggested fix:** Move `.type-picker` to the global styles, and drop `.muted` along with the `muted` class on the note.

### [Nit] AC4.4 page test never makes a load fail
- **Where:** `frontend/src/app/features/running-dashboard/running-dashboard-page.spec.ts:443-446`
- **Problem:** The comment says "A failed load, then Retry", but the test calls the protected `retry()` through an `unknown` cast without any failed request first. It does prove the selection survives a reload, which is what AC4.4 asks for.
- **Failure scenario:** If the error path ever reset `selectedPaceTypeKey`, this test would not catch it.
- **Suggested fix:** Flush an error response first, then click the Retry button in the DOM.

### [Nit] Repeated null checks in the component
- **Where:** `frontend/src/app/features/running-dashboard/workout-pace-comparison-chart/workout-pace-comparison-chart.ts:84-107`
- **Problem:** Five `computed`s each repeat the `const type = this.selected(); return type ? … : …` check, but the template only reads them inside `@if (selected(); as type)`.
- **Failure scenario:** None. It is just extra code.
- **Suggested fix:** Optional: one `computed` that returns a view object, or nothing.

## Privacy checklist
1. ✅ No secrets or tokens in the diff or fixtures.
2. ✅ The response holds only aggregated values; no tokens involved.
3. n/a
4. n/a
5. ✅ No scope change.
6. ✅ No new stored fields. Plan notes are no longer parsed.
7. ✅ No GPS is used or returned.
8. ✅ No logging added.
9. n/a
10. ✅ Error shapes unchanged.
11. ✅ Both routes keep the session check (routes are unchanged).
12. n/a
13. ✅ The tooltip formatter (`workout-pace.options.ts:21-24`, `:121-126`) builds text only from the date, the pace number and a fixed "Treadmill" label. The option labels use interpolation of the derived type label. No `[innerHTML]`.
14. n/a
15. ✅ Only chart-ready fields are added, and `notes` is removed from `/api/training-plan`. The description is read only in the backend.
16. ✅ No new dependencies.
17–20. n/a
21. ✅ Fixtures use invented dates, paces and descriptions.

## Summary
The implementation is small and closely follows the spec. Every R# is implemented, every acceptance criterion has a named test, and the TypeScript interfaces match the Kotlin DTOs (`PacePointDto`, `runCount`, `points`, `windowStart`/`windowEnd`, and the removed `notes`). All checks pass. The only findings are Nits; the most useful one is moving the duplicated `.type-picker` style into the shared stylesheet. Separately, the local `main` ref is out of date and should be updated before comparing against `main` again.

---

## Resolution (after review)

Only Nits, so the reviewer was not re-run. After the fixes: frontend lint, 227 tests and the build pass; the backend is unchanged (ktlint, detekt and 334 tests pass).

| Finding | Outcome |
|---|---|
| [Nit] `paceSummaryText` split in two | **Kept as is.** `paceSummaryPrefix` + `paceChangeText` let the template wrap the change in its tone span; the spec's text and tone rules (AC7.1–7.2) are unchanged and tested. |
| [Nit] Duplicate and redundant styles | **Fixed.** `.type-picker` moved to `styles.scss` and removed from both "by type" cards; the local `.muted` rule and class are gone (the global `.note` is already muted). |
| [Nit] AC4.4 page test never makes a load fail | **Fixed differently.** Since 009, reloads after a sync are quiet and a failed one keeps the current content, so once data is on screen there is no Retry state to reach. The test now reloads the way the page does (after a finished sync): a failed reload keeps `4x8min` selected, the next reload keeps it, and a reload without that type falls back to the first type. |
| [Nit] Repeated null checks in the component | **Kept.** Each `computed` stays a one-liner that mirrors `HeartRateTrendChart`; a view object would make the two cards diverge in shape for no behaviour change. |
