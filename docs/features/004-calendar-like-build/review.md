<!-- Revision 1 review below; Revision 2 review appended after the divider. -->
# Review: 004-calendar-like-build
**Base:** d009a34 → **Head:** d009a34 (all changes are uncommitted in the working tree)
**Verdict:** APPROVE WITH CHANGES

## Checks
- Backend: ktlint pass · detekt pass (0 issues) · tests 149 passed, 0 failed (Gradle said up to date; counts come from `backend/build/test-results/test/*.xml`)
- Frontend: lint pass · tests 105 passed, 0 failed (20 files) · build pass

## Spec coverage
| Req | Implemented | Tested | Notes |
|---|---|---|---|
| R1 | ✅ | ✅ | AppConfigTest covers AC1.1–AC1.3. AC1.4 checked by review: `.env.example` lines 18/20 have no values; `.gitignore` adds `/*.csv` and `/backend/*.csv`; the test fixture is not ignored |
| R2 | ✅ | ✅ | AC2.1 is tested at the file source (no caching layer exists above it). The spec lists `plan-16-weeks.csv`, `plan-missing-volume.csv` and `plan-cp1252.csv`, but these are generated in code or inline instead (see Nit) |
| R3 | ✅ | ✅ | Note text that contains "pace" is read as a pace header (see Minor) |
| R4 | ✅ | ✅ | No test rounds minutes half-up from a non-integer cell. This is not critical |
| R5 | ✅ | ✅ | |
| R6 | ✅ | ✅ | |
| R7 | ✅ | ✅ | All the AC7.x examples are asserted, including the Lisbon zone case |
| R8 | ✅ | ✅ | The route test checks keys for progress, the first pace, the first week and the first day. The `race` object is only checked through a substring |
| R9 | ✅ | ✅ | `running-dashboard-page.plan.spec.ts` |
| R10 | ✅ | ✅ | AC10.3 checked by review |
| R11 | ✅ | ✅ | |
| R12 | ✅ | ✅ | For AC12.8, only the pure function is tested; the `afterNextRender` scroll is a manual check per the test plan |
| R13 | ✅ | ⚠️ | AC13.5 is only tested for a 500. A network error and an unexpected 200 body are not tested, and an unexpected body is not handled (see Major) |
| R14 | ✅ | n/a | Review check. Two badges/tags use accent instead of muted (see Minor) |
| R15 | ✅ | ✅ | AC15.1 cannot be checked against d009a34, because the 001–003 files are also uncommitted (staged as intent-to-add). A grep of those files and of `strava/*.kt` finds no plan or training references. AC15.2: both page specs call `stubPlanSection()` before `createComponent` |

## Findings
### [Major] An unexpected 200 body breaks the section instead of showing the "other" error (AC13.5)
- **Where:** `frontend/src/app/features/calendar-like-build/calendar-like-build.ts:53` (`next: (data) => this.state.set({ kind: 'loaded', data })`), used at `build-calendar/build-calendar.ts:77` (`weekRows(this.weeks())`) and `build-progress/build-progress-bar.ts:8` (`progress().label`)
- **Problem:** Any 200 JSON body is accepted as `TrainingPlanResponse` with no check. AC13.5 says "an unexpected body" must show "Couldn't load your training plan." with Retry. The spec test `004 R13 - other error` only flushes a 500. There is no test for a network error (`ProgressEvent`) or for an unexpected body.
- **Failure scenario:** `/api/training-plan` returns `200 {}`, for example from a stale backend or a proxy returning a different JSON. The state becomes `loaded`, `data.progress` is `undefined`, and `progress().label` / `weeks.map` throw a TypeError during change detection. The section renders broken with no alert and no Retry, and the error is reported through Angular's ErrorHandler.
- **Suggested fix:** Narrow the body in `next` with a small shape guard (`weeks` is an array, `progress` is an object) and set `{kind:'error', reason:'other'}` when it fails. Add tests for a network error (`req.error(new ProgressEvent('error'))`) and for a `200 {}` body.

### [Minor] Note cells containing "pace" are read as pace headers, and their text is logged
- **Where:** `backend/src/main/kotlin/app/stravadashboard/domain/trainingplan/PlanHeaderParser.kt:17-20` (scans every header-block cell, including the NOTES column) and `:45` (`log.warn("Training plan pace could not be read: {}", label)`)
- **Problem:** `paces()` runs `PACE_HEADER` on every cell of the header block, so the notes column at *n+1* is scanned too. A user-written note such as `4x8' at HM pace` uppercases to `4X8' AT HM PACE`, which matches `^(.+?)\s+PACE\b` with group `4X8' AT HM`.
- **Failure scenario:** If the cell below that note is empty or another note, the "pace" fails to parse and a WARN logs `Training plan pace could not be read: 4X8' AT HM`. That is a cell value in the logs, which goes against the intent of AC2.6. If the cell below happens to hold an `m:ss` value, a bogus target pace appears in the panel.
- **Suggested fix:** Exclude the NOTES column (and the cells after it) from pace detection, or only accept pace headers that start a row's pace columns. Log only the column index, not the matched text.

### [Minor] The race badge and the "This week" tag use the accent colour, but AC14.2 says small labels are muted
- **Where:** `frontend/src/app/features/calendar-like-build/build-calendar/build-calendar.scss:43-45` (`.tag { color: var(--color-accent); }`) and `:117-119` (`.race-badge { color: var(--color-accent); }`)
- **Problem:** AC14.2 says small labels (week labels, the Target paces title, badges) use `--color-text-muted`, with the marathon cell as the only exception.
- **Failure scenario:** On a 5K race day, the "5K race" badge renders in accent green instead of muted, and the "This week" tag is accent too.
- **Suggested fix:** Use `--color-text-muted` for these, or get the spec amended if the accent was intended.

### [Minor] AC13.5 network-error path has no test
- **Where:** `frontend/src/app/features/calendar-like-build/calendar-like-build.spec.ts:75-81`
- **Problem:** Only the 500 case is tested. `planErrorState` (`plan-view.ts:31-48`) handles a status-0 `HttpErrorResponse` as `other`, but nothing asserts it, so a regression would go unnoticed. This is covered by the fix suggested in the Major finding.
- **Suggested fix:** Add `http.expectOne(...).error(new ProgressEvent('error'))` and assert the "Couldn't load your training plan." alert.

### [Nit] File layout differs from the spec's "Files expected to change"
- **Where:** `frontend/src/app/features/calendar-like-build/build-progress/build-progress-bar.ts` (spec: `build-progress/build-progress.ts`), `plan-view.ts` (spec: separate `scroll-target.ts` and spec file), and `backend/src/test/resources/trainingplan/` (only `plan-sample.csv`; the 16-week, missing-volume and cp1252 fixtures are generated in `testsupport/PlanFixtures.kt` or inline)
- **Problem:** It works, but it does not match the spec's file list.
- **Suggested fix:** Update the spec or rename the files.

### [Nit] Invisible BOM literal
- **Where:** `backend/src/main/kotlin/app/stravadashboard/persistence/TrainingPlanFileSource.kt:66` (`const val BYTE_ORDER_MARK = "﻿"`)
- **Problem:** The constant holds a raw U+FEFF character, which looks like an empty string when reading the code.
- **Suggested fix:** Write it as `"﻿"`.

### [Nit] Duplicate constant and uppercased pace labels
- **Where:** `backend/src/main/kotlin/app/stravadashboard/domain/trainingplan/PlanHeaderParser.kt:13` (private `SECONDS_PER_MINUTE`, which already exists in `domain/model/Units.kt:7`) and `:34-35` (the label comes from the uppercased title)
- **Problem:** AC3.1 says the label is shown "as written". A header `Hm pace` would show as `HM`.
- **Suggested fix:** Reuse `domain.model.SECONDS_PER_MINUTE`, and take the label from the original-case cell.

## Privacy checklist
1. ✅ No secrets. `.env.example` keys have no values. The fixtures are invented.
2. ✅ No token change. The route does not touch tokens beyond `hasConnectedSession`.
3. ✅ unchanged
4. ✅ unchanged
5. ✅ No new scopes. The route makes no Strava call (tested).
6. ✅ Nothing is persisted. The DTOs hold only display fields.
7. n/a: no GPS.
8. ⚠️ No tokens, path or HR data in logs. `TrainingPlanFileSource` logs only the exception class (tested), and `AppConfig.toString` hides the path (tested). However, note text can reach a WARN (see the Minor on pace header detection).
9. ✅ unchanged
10. ✅ Standard error shape. The 422 reason contains only week labels, day names and row names (`PlanDayParser.kt:33,41-43`, `TrainingPlanParser.kt:43,68`).
11. ✅ The route checks the session (`TrainingPlanRoutes.kt:17`). Tested.
12. ✅ unchanged
13. ✅ Interpolation only, with no `[innerHTML]`. The XSS test is present.
14. n/a: GET only.
15. ✅ The response has no path and no raw rows. Exact key sets are tested.
16. ✅ No new dependencies or external assets.
17. ✅ unchanged
18. n/a: nothing new is stored (item still deferred from 001).
19. n/a
20. n/a: no DB change.
21. ✅ Fixtures are invented (`plan-sample.csv`, `training-plan-fixtures.ts`).

## Summary
The implementation is solid and close to the spec. Parsing, dating, progress labels, the error mapping and the UI states are clean, well layered and well tested, and all backend and frontend checks pass. The most important fix is AC13.5: an unexpected 200 body currently breaks the section instead of showing the "other" error with Retry, and that criterion has no tests beyond the 500 case. After that, stop scanning the notes column for pace headers so user note text cannot reach the logs.

## Resolution
Checks after the fixes: backend `ktlintCheck detekt test` pass (151 tests); frontend `lint`, `test` (107 tests) and `build` pass. No Blockers, so no re-review.

| Finding | Outcome |
|---|---|
| [Major] Unexpected 200 body breaks the section | Fixed: `isTrainingPlanResponse` in `plan-view.ts` checks the body shape; anything else sets the "other" error with Retry. New tests `004 R13 - unexpected body is other error` and `004 R13 - network error is other error`. |
| [Minor] Note cells containing "pace" read as pace headers and logged | Fixed: pace detection only scans columns left of the NOTES column, and the WARN logs the column index only. New test `004 R3 - note text mentioning pace is not a pace`. |
| [Minor] Race badge and "This week" tag use the accent colour | Fixed: both use the muted label colour (the shared badge style), as AC14.2 says. The race cell keeps its accent border and the marathon cell stays the only accent-filled cell. |
| [Minor] AC13.5 network-error path has no test | Fixed with the Major (see above). |
| [Nit] File layout differs from the spec | Spec updated: `build-progress-bar.ts` (`BuildProgressBar`, to avoid clashing with the `BuildProgress` model), `plan-view.ts` holds `scrollTargetWeekIndex`, and the extra CSV cases are generated from `plan-sample.csv`. |
| [Nit] Invisible BOM literal | Fixed: built from its code point (`BYTE_ORDER_MARK_CODE = 0xFEFF`), so the source has no invisible character. |
| [Nit] Duplicate constant and uppercased pace labels | Fixed: reuses `domain.model.SECONDS_PER_MINUTE`; the pace label keeps the header's own case (`Easy` stays special-cased). New test `004 R3 - pace label as written`. |

---

# Review: 004-calendar-like-build
**Base:** d009a34 → **Head:** d009a34 (all changes uncommitted in the working tree)
**Verdict:** APPROVE WITH CHANGES

## Checks
- Backend: ktlint pass · detekt pass (0 issues) · tests 152 passed, 0 failed (Gradle reported the tasks UP-TO-DATE, so these are the results of the last run on unchanged inputs)
- Frontend: lint pass · tests 114 passed, 0 failed (21 files) · build pass

## Spec coverage
| Req | Implemented | Tested | Notes |
|---|---|---|---|
| R1 | ✅ | ✅ | `AppConfigTest` covers AC1.1–AC1.3. AC1.4: `.gitignore` verified (`/*.csv` and `/backend/*.csv` are ignored, `plan-sample.csv` is not). I could not check `.env.example` because a permission rule blocks reading it. |
| R2 | ✅ | ✅ | Re-read, BOM, Windows-1252, size limit, unreadable log, parse-failure log |
| R3 | ✅ | ✅ | The AC3.4 WARN logs the column index, not the label (see Nit) |
| R4 | ✅ | ✅ | |
| R5 | ✅ | ✅ | |
| R6 | ✅ | ✅ | |
| R7 | ✅ | ✅ | `isPast` tested in `004 R7 - days before today are past` |
| R8 | ✅ | ✅ | Key-set test includes `isPast`. The "session for another athlete" part of AC8.2 is not tested in the new route test (it reuses `hasConnectedSession`). |
| R9 | ✅ | ✅ | First and last position, one plan request, independence in both directions |
| R10 | ✅ | ✅ | |
| R11 | ✅ | ✅ | AC11.4 is covered inside `004 R9 - build calendar card is last…`, not by the named test |
| R12 | ✅ | ✅ | |
| R13 | ✅ | ✅ | |
| R14 | ✅ | n/a (review check) | Tokens only. `color-mix` uses tokens only. No `opacity`, no `--color-worse`. Square tiles from 720 px. |
| R15 | ✅ | ✅ | The listed files are new relative to d009a34 (001–003 are also uncommitted), so AC15.1 can't be checked by diff. A grep finds no plan or `isPast` references in them or in `strava/`. Both stubs are applied in the 001/002 page specs. |
| R16 | ✅ | ⚠️ | `ensureLoaded`, `retry` and the one-request rule are tested. AC16.3 (cancel on destroy) and AC16.4 (new store per visit) have no test. |
| R17 | ✅ | ✅ | Past hover on the marathon tile is wrong (Major below) |
| R18 | ⚠️ | ✅ | `004 R18 - day tiles are not interactive` passes. The hover rules break AC18.1 for a past marathon tile. |

## Findings
### [Major] Hovering a past marathon tile brings back the accent background
- **Where:** `frontend/src/app/features/calendar-like-build/build-calendar/build-calendar.scss:194` (`.day.marathon:hover`), which comes after `:185` (`.day.past:hover`)
- **Problem:** `.day.past:hover` and `.day.marathon:hover` have the same specificity (0,3,0), so the later rule wins. A past marathon tile on hover gets `background-color: var(--color-accent)` and `border-color: var(--color-text)` instead of the past hover style (`--color-surface` background, `--color-text-muted` border) required by AC18.1. AC17.2 says past styling replaces the marathon accent background.
- **Failure scenario:** today = 2026-12-07 or later (`phase = "finished"`, every tile has `past`, AC17.5). Moving the mouse over the 6 Dec tile switches it to the bright accent background, with the past `--color-text-muted` text and the diagonal cross drawn on top. The finished marathon flashes back to "most prominent" with poor-contrast text.
- **Suggested fix:** use `.day.marathon:not(.past):hover`, or move `.day.past:hover` below the marathon hover rule (it needs `border-color` too).

### [Minor] Some test-plan names are missing or merged, and AC16.3 and AC16.4 are untested
- **Where:** `backend/src/test/kotlin/app/stravadashboard/domain/trainingplan/BuildCalendarBuilderTest.kt:123`, `frontend/src/app/features/calendar-like-build/training-plan.store.spec.ts:19-44`, `frontend/src/app/features/running-dashboard/running-dashboard-page.plan.spec.ts:89,114`
- **Problem:** The spec's test plan names `004 R7 - past days are those before today` / `… no past days before start and all past after race`, `004 R16 - 401 keeps state`, `004 R16 - unexpected body is other error`, `004 R11 - paces and notes in the calendar card` and `004 R13 - single retry button on error`. Their assertions exist but are folded into other tests under other names. The 401 and unexpected-body cases are in `calendar-like-build.spec.ts`, not in the store spec. No test covers AC16.3 (pending request cancelled when the page is destroyed) or AC16.4 (new store and new request on revisit).
- **Failure scenario:** If `takeUntilDestroyed(this.destroyRef)` were removed from `training-plan.store.ts:43`, or the store were changed to `providedIn: 'root'` (cached across visits), every test would still pass.
- **Suggested fix:** Rename or split the tests to match the test plan. Add a store test that destroys the TestBed injector before flushing, and a page test that creates the page twice and expects two plan requests.

### [Nit] The pace WARN names the column instead of the label
- **Where:** `backend/src/main/kotlin/app/stravadashboard/domain/trainingplan/PlanHeaderParser.kt:52`
- **Problem:** AC3.4 asks for "WARN with the label only". The code logs `column {}` instead. It is privacy-safe, but it differs from the spec. Either log the parsed label (e.g. `15K`) or note the deviation in `review.md`.

## Privacy checklist
1. ✅ No secrets in the diff. The fixture CSV is invented. `.gitignore` guards `/*.csv` and `/backend/*.csv`. (`.env.example` could not be read because a permission rule blocks it.)
2. ✅ No token change. Tokens are not in the plan DTOs.
3. n/a (unchanged)
4. n/a (unchanged)
5. ✅ No new scopes. `004 R8 - no Strava call` asserts zero Strava requests.
6. ✅ Nothing is persisted. The DTOs hold only display fields.
7. n/a: no GPS
8. ✅ The unreadable log holds the exception class only (`TrainingPlanFileSource.kt:44`). The invalid log holds the safe reason only (`TrainingPlanService.kt:50`). `AppConfig.toString` hides the path. No `console` calls.
9. ✅ unchanged
10. ✅ 404/503/422 use the standard shape with fixed or safe text
11. ✅ `hasConnectedSession` check in `TrainingPlanRoutes.kt:17`
12. ✅ unchanged
13. ✅ Interpolation only, no `[innerHTML]` or `bypassSecurityTrust*`. The XSS test is present.
14. n/a: GET only
15. ✅ No path or raw rows in the response. The key-set test pins the exact fields.
16. ✅ No external assets or new dependencies
17. ✅ unchanged
18. n/a: nothing new stored (still deferred, as in 001)
19. n/a
20. n/a
21. ✅ Fixtures are invented (`plan-sample.csv`, `training-plan-fixtures.ts`)

## Summary
The implementation follows the spec closely: the parser, date and progress logic, past-day flags, the store-shared single request, and the top/bottom card split are all correct and well tested, and every check passes. The one fix needed before merge is the CSS order bug that turns a past marathon tile back to the accent style on hover once the build has finished. After that, line the test names up with the test plan and add tests for store cancellation and the new store per visit.

## Resolution (Revision 2)
Checks after the fixes: backend `ktlintCheck detekt test` pass (153 tests); frontend `lint`, `test` (119 tests) and `build` pass. No Blockers, so no re-review.

| Finding | Outcome |
|---|---|
| [Major] Hovering a past marathon tile brings back the accent background | Fixed: the rule is now `.day.marathon:not(.past):hover`, so a past marathon keeps the past hover style (`--color-surface` background, `--color-text-muted` border from the base hover rule). |
| [Minor] Test-plan names merged; AC16.3/AC16.4 untested | Fixed: backend split into `004 R7 - past days are those before today` and `004 R7 - no past days before start and all past after race`. Store spec adds `004 R16 - 401 keeps state`, `004 R16 - unexpected body is other error` and `004 R16 - pending request is cancelled when the page is destroyed` (AC16.3). The page spec adds `004 R11 - paces and notes in the calendar card`, `004 R13 - single retry button on error` and `004 R16 - a new visit loads the plan again` (AC16.4). |
| [Nit] Pace WARN names the column, not the label | Kept on purpose: the header text is user-written, and the first 004 review flagged logging it (privacy item 8). The column index is enough to find the cell. AC3.4 reads "label only" to mean "no cell values"; logging the column is stricter. |
