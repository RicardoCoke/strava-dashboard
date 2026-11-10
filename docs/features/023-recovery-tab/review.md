# Review: 023-recovery-tab
**Base:** HEAD (uncommitted working tree, untracked files included) → **Head:** 6b5b6fd + working-tree changes
**Verdict:** APPROVE WITH CHANGES

## Checks
- Backend: ktlint pass · detekt pass (0 issues) · tests 693 passed, 0 failed
- Frontend: lint pass · tests 456 passed, 0 failed · build pass

## Spec coverage
| Req | Implemented | Tested | Notes |
|---|---|---|---|
| R1 | ✅ | ✅ | nav-menu.spec.ts covers AC1.1–AC1.4. The header layout is tested on both pages. |
| R2 | ✅ | ⚠️ | No test for AC2.2 (clicking the current page's own link closes the panel without a reload). No focus containment (see Minor). |
| R3 | ✅ | ✅ | app.spec.ts tests `/recovery` and `/foo`. The backend deep-link SPA test was not re-checked. |
| R4 | ✅ | ⚠️ | AC4.1–AC4.4 policy and sync are tested. No test for AC4.4 persistence (column added to an existing DB), AC4.5 (`historyDays` stays null after a failure) or AC4.6 (old endpoints unchanged by older rows). |
| R5 | ✅ | ⚠️ | AC5.1–AC5.7 and AC5.9 are tested. AC5.8 (different values per person, Marina 404) and AC5.10 (read window from first − 31) have no test. |
| R6 | ✅ | ✅ | |
| R7 | ✅ | ✅ | Zone boundaries 33, 34, 66 and 67 are tested. |
| R8 | ✅ | ✅ | |
| R9 | ✅ | ✅ | Edge case in the quiet reload (see Minor). |
| R10 | ✅ | ✅ | Tested in recovery-page.spec.ts. The spec's separate recovery-shell.spec.ts was not created, which is acceptable. |
| R11 | ✅ | manual | The SCSS uses existing tokens, the 180 ms animation is turned off under reduced motion, and tap targets are 2.75rem. |

## Findings
### [Major] Acceptance criteria without tests (AC5.8, AC5.10, AC4.5, AC4.6, AC4.4 persistence)
- **Where:** `backend/src/test/kotlin/app/stravadashboard/api/PersonIsolationTest.kt:97` (only adds `/api/rings/history?days=30` to the generic `routes` list) and `backend/src/test/kotlin/app/stravadashboard/api/RingsRoutesTest.kt:101-157`. Untested code: `backend/src/main/kotlin/app/stravadashboard/domain/rings/RingsService.kt:30-31` and `backend/src/main/kotlin/app/stravadashboard/domain/wellness/WellnessSync.kt:50-55`.
- **Problem:**
  - AC5.8 asks for a test "with different stored values per person". The `routes` list is only used by `R10 - other cards still load` (status is not 401 or 500) and `R18 - header makes no difference for ricardo`, so no test seeds Marina data and checks that only her values come back. The Marina-not-configured 404 on `/api/rings/history` is not tested either.
  - AC5.10 (`R5 - reads from first day minus 31`) has no test.
  - AC4.5: `R4 - key rejected, rate limited and unavailable keep cached rows` (`WellnessSyncTest.kt:104`) does not check that `historyDays` stays null.
  - AC4.6 (`R4 - wellness and rings responses unchanged by older rows`) and the AC4.4 persistence test (`R4 - history_days column added without changing rows`, via `addColumnIfMissing` on an existing DB) are both missing.
- **Failure scenario:** these regressions would all pass the suite:
  - A change that passes `first` instead of `first.minusDays(BASELINE_DAYS + 1)` to `findBetween`. Recovery for the first ~30 days of a 90-day range would silently become null.
  - A failed backfill that records `historyDays = 120`. The 120-day fetch would never be retried.
  - A per-person DB mix-up that only affects history.
- **Suggested fix:**
  - Add a RingsService unit test with fake repos that records the `from` passed to `findBetween`.
  - Add a PersonIsolation test that seeds different `ring_days` per person and compares the history bodies.
  - Add `assertNull(state().historyDays)` to the failure test.
  - Add a repository test that opens a DB created without the column.

### [Minor] Quiet reload can leave the page stuck on the spinner
- **Where:** `frontend/src/app/features/recovery/recovery-page.ts:89-107`
- **Problem:** `switchMap` cancels whatever request is in flight, including a non-quiet one, and a failed quiet load never changes `state`. A quiet reload fired from `dataVersion` (line 107) while `state` is still `loading` replaces the visible load. If the quiet load then fails, nothing ever leaves `loading`.
- **Failure scenario:** the user clicks "7 days" (state `loading`), and a background sync finishes before that response arrives. The 7-day request is cancelled, the quiet request returns 500, and the spinner "Loading recovery history…" stays up with no Retry button.
- **Suggested fix:** decide "quiet" at the moment the response arrives instead of when the request is sent. In the subscriber, take the error branch whenever the state isn't `loaded` (`else if (!quiet || this.state().kind !== 'loaded')`).

### [Minor] The modal dialog has no focus containment
- **Where:** `frontend/src/app/shared/nav-menu/nav-menu.html:29` and `frontend/src/app/shared/nav-menu/nav-menu.ts:29-40`
- **Problem:** the panel declares `aria-modal="true"` and covers the page with a backdrop, but Tab is not kept inside it. AC2.4 says "Tab moves between the links and the close button".
- **Failure scenario:** a keyboard user opens the menu, and focus lands on "Performance". Pressing Tab twice moves to "Recovery" and then out of the panel to `<app-person-switch>` behind the dimmed backdrop. Focus is now on a control that screen readers were told is inert, and the panel stays open.
- **Suggested fix:** wrap focus between the close button and the last link on Tab / Shift+Tab, or use the CDK `cdkTrapFocus` if `@angular/cdk` is already installed.

### [Minor] No test for AC2.2
- **Where:** `frontend/src/app/shared/nav-menu/nav-menu.spec.ts:259-266`
- **Problem:** only the cross-page navigation is tested. Nothing covers clicking the current page's own link (it should close the panel and not reload).
- **Failure scenario:** if `(click)="close()"` is dropped from the active link, the panel stays open on a same-page click and every test still passes.
- **Suggested fix:** in the R2 test, open the menu on `/`, click "Performance", and assert the panel is null and the URL is still `/`.

### [Nit] Comment arithmetic in RingsPolicy
- **Where:** `backend/src/main/kotlin/app/stravadashboard/domain/rings/RingsPolicy.kt:7`
- **Problem:** "90 graph days plus the 3 nights" adds up to 93, but the value is 92. The value is right: the first graph day is today − 89, and 3 nights before it is today − 92. Only the comment's wording is off.
- **Suggested fix:** "first graph day (today − 89) minus 3 nights of sleep debt".

## Privacy checklist
1. ✅ No secrets in the diff. Fixtures are invented (`frontend/src/app/testing/rings-history-fixtures.ts`).
2. ✅ No Garmin key or token in any response. The history DTO holds only the date, two percentages, the zone and strain.
3. ✅ Unchanged. The existing 401 refresh and `KeyRejected` stop apply to the backfill.
4. n/a
5. ✅ No Strava scopes.
6. ✅ Only the `history_days` integer was added. Retention grows to 92 days (`ring_days`) and 120 days (`wellness_days`), as the spec says.
7. n/a
8. ✅ No new log lines. The Rings log still prints counts only (`RingsSync.kt:52`), and the frontend has no `console` output.
9. ✅ Unchanged.
10. ✅ The 400 uses fixed text (`ErrorResponse.INVALID_DAYS`).
11. ⚠ Existing: there is no session guard since 021, so the new GET is only as exposed as the other routes on the home network.
12. n/a
13. ✅ The tooltip formatter builds HTML from `formatShortDate`, numbers and the backend zone enum only (`recovery-history.options.ts:64-68`). No `[innerHTML]`.
14. n/a (GET only)
15. ✅ Only chart fields. Same origin.
16. ✅ No new third parties.
17. ✅ No Strava data.
18. ❌ Existing gap: no in-app delete (accepted in the spec).
19. n/a
20. ✅ Unchanged. The DB backups simply hold more days now.
21. ✅ Invented data only.

## Summary
The feature matches the spec. The backend reuses 020's calculator and rounding with no formula changes, the backfill logic is small and correct, and the frontend has the loading, error and empty states, cancels older requests, and resets correctly on a person flip. All checks pass. The main gap is test coverage for AC5.8 (per-person values), AC5.10 (read window) and the backfill failure and persistence criteria. After that, fix the quiet-reload path that can leave the spinner up forever.

I did not verify that Garmin accepts one 121-day `dailysummaries` range (spec build step 1), and I did not check AC11 at 360 px. Both need a manual check.

---

## Resolution (after review)
- **[Major] Missing AC tests:** fixed. Added:
  - `RingsServiceTest` `R5 - reads from first day minus 31` (AC5.10)
  - `PersonIsolationTest` `023 R5 - history follows X-Person`, with different stored strain per person (AC5.8)
  - `/api/rings/history?days=30` in `PersonRoutesTest`'s per-person list, so the Marina-not-configured 404 is covered
  - `assertNull(state().historyDays)` in the wellness failure test (AC4.5)
  - `ExposedWellnessRepositoryTest` `R4 - history_days column added without changing rows` (AC4.4 persistence)
  - `RingsRoutesTest` `R4 - rings response unchanged by older rows` (AC4.6, the rings endpoint only; wellness and weekly summaries keep their own windows and were not re-tested)
- **[Minor] Quiet reload stuck on spinner:** fixed. A failed load now shows the error whenever the page isn't `loaded`, so the separate `quiet` flag was removed. Test: `R9 - failed sync reload during a range change shows the error, not a stuck spinner`.
- **[Minor] No focus containment:** fixed with a small Tab / Shift+Tab wrap on the panel (no CDK installed, so none was added). Test: `R2 - tab stays inside the open panel`.
- **[Minor] No test for AC2.2:** fixed. Test: `R2 - picking the current page closes the panel without navigating`.
- **[Nit] RingsPolicy comment:** fixed ("The first graph day (today − 89) minus 3 nights of sleep debt").
- No Blockers, so the reviewer was not re-run. Checks after fixes: backend 697 tests, 0 failures, ktlint and detekt pass; frontend lint pass, 459 tests pass, build pass.
- After ponytail-review: `GarminWellnessSource.fetch` now splits ranges longer than 91 days (the longest proven range) into chunks, oldest first, stopping at the first failure and keeping each chunk's own dates only. The 121-day backfill is 91 + 30 days. Tests: `023 R4 - a 121-day range is fetched in 91 and 30 days`, `023 R4 - a failed chunk stops the split fetch`. Backend now 699 tests, 0 failures.
- Still manual: the 360 px layout (AC11).
