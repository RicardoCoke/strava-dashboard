# Review: 007-week-vs-week-tiles
**Base:** origin/main (0374050) → **Head:** 0374050 + uncommitted working tree
**Verdict:** APPROVE

## Checks
- Backend: ktlint pass · detekt pass (0 issues) · tests 240 passed, 0 failed (includes 16 in WeekComparisonTest). Gradle reported the tasks as UP-TO-DATE, so the results come from a run on these same inputs.
- Frontend: lint pass · tests 163 passed, 0 failed (28 files) · build pass

## Spec coverage
| Req | Implemented | Tested | Notes |
|---|---|---|---|
| R1 | ✅ | ✅ | AC1.1 in model spec plus the 002 tone-neutral component test. AC1.2/AC9.3 in `R1 - week comparison distance matches weekly distance entries` |
| R2 | ✅ | ✅ | AC2.1–AC2.6 covered, including the `-0.0` bit check |
| R3 | ✅ | ⚠️ | AC3.1–AC3.3 tested at formatter and model level. AC3.4 font size is not asserted (see Minor). AC3.5 is tested only at model level, not as the component test the plan names |
| R4 | ✅ | ✅ | AC4.1–AC4.7 covered |
| R5 | ✅ | ✅ | AC5.1–AC5.4 covered |
| R6 | ✅ | ✅ | AC6.1 is covered with different numbers (250/257 vs 248), and the AC6.2 rounding is checked too |
| R7 | ✅ | ✅ | AC7.1–AC7.4 covered |
| R8 | ✅ | ✅ | AC8.1–AC8.4 covered |
| R9 | ✅ | ⚠️ | AC9.1 is checked for km and HR only, not pace. AC9.2 covered |
| R10 | ✅ | ✅ | Contract test extended with a null side and `missingIn`. The existing "tiles make no extra request" test still passes |

## Findings
### [Minor] AC3.4 test name says "smaller than value" but nothing checks size
- **Where:** `frontend/src/app/features/running-dashboard/running-dashboard-page.redesign.spec.ts:103-113`
- **Problem:** The test `007 R3 - delta renders after unit and smaller than value` checks element order, text and tone class. It never checks the 0.95rem / 600 styling, and it never checks that the value element stays unchanged.
- **Failure scenario:** If someone removes or changes the `.delta` rule in `frontend/src/app/features/running-dashboard/headline-tiles/headline-tiles.ts:72-75` (for example `font-size: 2rem`), the delta becomes as large as the value, and the test still passes.
- **Suggested fix:** Either assert the computed `font-size`/`font-weight` of `.delta` against `.unit`, or rename the test so it doesn't promise a size check.

### [Minor] AC3.5 and the muted tone are not tested in the rendered component
- **Where:** `frontend/src/app/features/running-dashboard/headline-tiles/headline-tiles.model.spec.ts:117-121`. No matching test exists in `running-dashboard-page.redesign.spec.ts`.
- **Problem:** The test plan asks for a component test that no delta is rendered when the "This week" tile is unavailable. Only the model is tested. The `tone-muted` class and its component-scoped style are also never rendered in any test.
- **Failure scenario:** If the template stops reading `tile.delta` correctly, or the `.tone-muted` rule at `headline-tiles.ts:77-79` is dropped, the DOM is wrong and no test fails.
- **Suggested fix:** Add one component test that renders a negative difference (expect `tone-muted`) and one with no `isCurrentWeek` entry (expect no `.delta`).

### [Minor] Second copy of the DateWindow mapper, in a file the spec didn't name
- **Where:** `backend/src/main/kotlin/app/stravadashboard/api/WeekComparisonMappers.kt:31`
- **Problem:** `toWindowDto` is a copy of `private fun DateWindow.toDto()` at `backend/src/main/kotlin/app/stravadashboard/api/RunningDashboardMappers.kt:105`. The spec says the mapping goes in `RunningDashboardMappers.kt`. Putting it in a new file is what forced the copy.
- **Failure scenario:** If the date format of the existing windows changes (say, to a different ISO form), `weekComparison.thisWeek/lastWeek` keep the old format, and the two windows in the same response disagree.
- **Suggested fix:** Move the mapping into `RunningDashboardMappers.kt`, or make the existing `DateWindow.toDto()` `internal` and reuse it.

### [Minor] AC9.1 test does not cover pace
- **Where:** `backend/src/test/kotlin/app/stravadashboard/domain/WeekComparisonTest.kt:204-224`
- **Problem:** AC9.1 says the Sunday 23:30 / Monday 00:10 boundary applies to km, HR and pace. The test only checks distance and easy HR.
- **Failure scenario:** If the pace path is later changed to bucket on a different date (for example a UTC date), it moves a late-Sunday workout into the wrong week, and no test fails.
- **Suggested fix:** Add one paced workout on each side of midnight and assert `workoutPace.lastWeek` / `thisWeek`.

### [Nit] `WeekRange` from the spec replaced by the existing `DateWindow`
- **Where:** `backend/src/main/kotlin/app/stravadashboard/domain/WeekComparison.kt:44-45`
- **Problem:** The design names `WeekRange`. The code reuses `DateWindow`, which is defined in `WorkoutPaceComparison.kt:9`. The JSON is the same, and the reuse is arguably simpler, so this is only a note on the difference from the spec.
- **Suggested fix:** None needed; you could add a note to the spec.

### [Nit] Range check repeats `DateWindow.contains`
- **Where:** `backend/src/main/kotlin/app/stravadashboard/domain/WeekComparison.kt:63`
- **Problem:** `it.localDate in window.start..window.end` repeats what `DateWindow.contains` already does (`WorkoutPaceComparison.kt:13`).
- **Suggested fix:** Use `it.localDate in window`.

## Privacy checklist
1. ✅ No secrets in the diff or fixtures
2. ✅ No tokens in the new DTOs
3. n/a
4. n/a
5. ✅ No scope change
6. ✅ Nothing new persisted
7. ✅ No GPS used
8. ✅ No new logging
9. ✅ No client logging change
10. ✅ Error handling unchanged
11. ✅ Same session-checked route
12. n/a
13. ✅ Plain interpolation (`headline-tiles.ts:19`). `typeLabels` come from `RunType.Workout.label` (`WeekComparisonMappers.kt:37`), not from activity names
14. n/a
15. ✅ Only aggregated numbers, dates and generated labels are returned
16. ✅ No third parties
17. ✅
18. ✅ Unchanged
19. ✅ Unchanged
20. ✅ Unchanged
21. ✅ Fixtures use invented HR, pace and dates

## Summary
The feature is implemented cleanly and matches the spec. Domain logic is in a pure function, the frontend only selects and formats, the Kotlin DTOs and the TypeScript interfaces match, and `-0.0` is handled. All checks pass. Nothing blocks merging. What's left is test thoroughness: the AC3.4/AC3.5 component tests don't check what their names say, and the pace part of AC9.1 is untested. There is also one duplicated DateWindow mapper to clean up.

## Resolution
No Blockers or Majors, so no re-review was needed. All findings were fixed:
- **[Minor] AC3.4 size not checked:** fixed. The component test now compares the computed font size and weight of `.delta` with `.unit`, checks they differ from `.value`, and checks the value text stays "32.4".
- **[Minor] AC3.5 / muted tone not rendered in a test:** fixed. Added `007 R3 - fewer km renders a muted delta` (expects `tone-muted`, not `tone-better`) and `007 R3 - no delta when this week unavailable`.
- **[Minor] Duplicated DateWindow mapper:** fixed. `DateWindow.toDto()` in `RunningDashboardMappers.kt` is now `internal` and reused; the copy in `WeekComparisonMappers.kt` is gone. The separate mapper file stays because detekt's TooManyFunctions limit (11) is exceeded otherwise.
- **[Minor] AC9.1 pace not covered:** fixed. Added `R9 - workouts bucket by local start date across midnight`.
- **[Nit] WeekRange vs DateWindow:** noted in the spec's domain design.
- **[Nit] Range check repeats DateWindow.contains:** fixed (`it.localDate in window`).

Checks after fixes: backend ktlint, detekt, 241 tests pass; frontend lint, 165 tests and build pass.

## Revision 2 (after review)
At the user's request the km comparison moved from beside the number to the detail line, like the easy-run HR tile: "29.0 → 32.4 km · +3.4 km · 4 runs" (difference still green, or muted when behind/equal; behind drops " vs last week"). Frontend only; tests updated (`007 R3 - km comparison sits on the detail line like the hr tile`, `007 R3 - this week tile shows last week to this week km like the hr tile`). Not re-reviewed: no Blockers were open and the change is limited to the tile layout and its tests.
