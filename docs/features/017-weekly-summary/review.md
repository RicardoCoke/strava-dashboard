# Review: 017-weekly-summary
**Base:** 2428bf1 → **Head:** 2428bf1 + uncommitted working tree (incl. untracked files)
**Verdict:** APPROVE WITH CHANGES

## Checks
- Backend: ktlint pass · detekt pass (0 issues) · tests 600 passed, 0 failed (Gradle `test` was UP-TO-DATE, so the counts come from the last run's XML results with the same inputs, including WeeklySummaryBuilderTest and WeeklySummaryRoutesTest)
- Frontend: lint pass · tests 395 passed, 0 failed (69 files) · build pass

## Spec coverage
| Req | Implemented | Tested | Notes |
|---|---|---|---|
| R1 | ✅ | ⚠️ | AC1.7 (manual run left out of pace/load/drift) has no test |
| R2 | ✅ | ⚠️ | AC2.3 has no explicit test. AC2.4 is only implied by the 1-run baseline weeks in the R3 tests |
| R3 | ✅ | ⚠️ | AC3.3 (equals 007 tiles / WeekComparison) not tested |
| R4 | ✅ | ✅ | |
| R5 | ✅ | ✅ | |
| R6 | ✅ | ⚠️ | AC6.4 (runs without HR add 0) not tested |
| R7 | ✅ | ⚠️ | AC7.3: only treadmill is tested, not TOO_SHORT / NO_DATA / not fetched |
| R8 | ✅ | ⚠️ | Neutral boundaries (+4.9 bpm, -14%) and AC8.4 not tested |
| R9 | ✅ | ⚠️ | AC9.1 test is tautological. AC9.4 not tested |
| R10 | ✅ | ✅ | Garmin no-call is structural: the service has no Garmin dependency |
| R11 | ✅ | ⚠️ | AC11.1 (bell in header) and AC11.6 (loading/error) not tested |
| R12 | ✅ | ⚠️ | AC12.1 position/flow and AC12.3 focus-to-heading not asserted |
| R13 | ✅ | ✅ | |
| R14 | ✅ | ✅ | |
| R15 | ✅ | ✅ | Existing suites pass. `efficiencyOf` only wraps `sampleOf` |

## Findings
### [Major] Several backend acceptance criteria have no test, and the ranking test can't fail
- **Where:** `backend/src/test/kotlin/app/stravadashboard/domain/weeklysummary/WeeklySummaryBuilderTest.kt:309-318`, plus missing tests
- **Problem:** The test plan names these tests, but they don't exist: `R1 - manual run counts for distance only` (AC1.7), `R2 - note skipped without any baseline` (AC2.3), `R3 - distance matches week comparison` (AC3.3), `R6 - runs without HR add zero` (AC6.4), `R7 - excluded drift runs` for TOO_SHORT/NO_DATA/null drift (AC7.3), `R8 - old week skips wellness only` (AC8.4) and `R9 - every summary has a note` (AC9.4). The R8 test at :292 only checks the flagged side, at exactly +5.0 and -19%. It never checks the spec's neutral cases at +4.9 bpm and -14%. The AC9.1 test at :316 checks `notes == notes.sortedWith(compareBy({ it.tone.ordinal }, { it.metric.ordinal }))`, which is the same comparator the production code uses (`WeeklySummary.kt:113`).
- **Failure scenario:** Suppose `NoteMetric` is reordered (say TIME before DISTANCE). Or a run with `movingTime == null` starts counting in pace. Or `>=` in `HeartNotes.kt:92` becomes `>`. Or `roundHalfUpToInt` is dropped in `HeartNotes.kt:106`. In each case all tests still pass.
- **Suggested fix:** Add the named tests. Make AC9.1 assert an explicit expected metric sequence for a fixture with more than 6 notes. Add the +4.9 and -14 neutral cases.

### [Major] Frontend acceptance criteria not covered, and R12 test name overstates what it checks
- **Where:** `frontend/src/app/features/weekly-summary/weekly-summary-bell.spec.ts:114-120`, `:131-146`. There is no `weekly-summary-panel.spec.ts` (the spec lists it).
- **Problem:**
  - AC11.1: no test checks that the bell sits in the page `header` next to `Running`. The page specs stub the bell and only check that the first tag is `header` (`running-dashboard-page.plan.spec.ts:53`).
  - AC11.6: the test named "no badge when empty or error" only covers the empty case. Loading and error are never checked for "no badge".
  - AC12.1: the test titled "panel is fixed overlay" asserts nothing about `position: fixed` or about `.cards` and the page flow.
  - AC12.3: no test checks that focus moves to the panel heading.
  - AC12.2: no test checks that Escape returns focus to the bell.
- **Failure scenario:** Removing `afterNextRender(() => this.heading().nativeElement.focus())` (`weekly-summary-panel.ts:45`) passes every test. So does a badge that shows during an error-state reload, or a bell moved out of the header.
- **Suggested fix:** Add the missing assertions: header placement in a page spec without the stub (or with an assertion on the stub tag), loading/error with no badge, `document.activeElement` checks for the heading and after Escape, and a computed-style or DOM-parent check for the overlay.

### [Minor] The open panel covers the bell at common viewport widths, so the toggle (AC12.4) can't be clicked
- **Where:** `frontend/src/app/features/weekly-summary/weekly-summary-panel/weekly-summary-panel.scss:3-8` together with `frontend/src/app/app.scss:3-5`
- **Problem:** The page content is centred with `max-width: 1040px`, so the bell sits at the right edge of that 1040px column. The panel is `position: fixed; top: 0; right: 0; width: 360px; z-index: 20`.
- **Failure scenario:** At a 1280px viewport the bell is at about x≈1120 and the panel spans x 920-1280. The panel covers the bell, so a mouse user can't click it to close the panel or see `aria-expanded` and the badge. Only ✕ or Escape work. This happens at any viewport narrower than about 1650px. The cause is the spec's own layout, so it needs a design decision.
- **Suggested fix:** Start the panel below the header (`top` offset), or raise the bell above the panel with its own `z-index`. Otherwise accept and note that ✕/Escape are the close paths.

### [Nit] Badge date window is not reactive
- **Where:** `frontend/src/app/features/weekly-summary/weekly-summary-bell.ts:66`
- **Problem:** `lastWeekMonday(new Date())` is read inside a `computed`. It only re-evaluates when `summaries` or `seen` change. If a tab stays open past the AC11.9 cutoff with no sync, a stale badge stays. Low impact.

### [Nit] File layout differs from the spec's "Files expected to change"
- **Where:** `backend/src/main/kotlin/app/stravadashboard/domain/weeklysummary/` (`WeeklySummary.kt` contains the builder, plus `RunNotes.kt`, `HeartNotes.kt`, `NoteText.kt`). There is no `WeeklySummaryNotesTest.kt`. `frontend/src/app/features/weekly-summary/seen-revision.ts:22` holds `lastWeekMonday`, a date helper that has nothing to do with storage.
- **Problem:** Cohesion and discoverability only. Behaviour is unaffected.

### [Nit] Drift selection differs slightly from the 013 POINT bucket
- **Where:** `backend/src/main/kotlin/app/stravadashboard/domain/weeklysummary/HeartNotes.kt:24-25` vs `backend/src/main/kotlin/app/stravadashboard/domain/longrundrift/LongRunDriftReport.kt:98`
- **Problem:** 013 also excludes runs with `averageHeartRateBpm == null` from POINT, and `driftOf` doesn't. This only matters for a COMPUTED drift with null average HR, which should not happen in practice. Implementation matches the spec's written rule.

## Privacy checklist
1. ✅ No secrets. Fixtures are invented ("Secret Loop", ids 700+, invented HR/wellness).
2. ✅ No tokens in the response. The route test asserts `access_token` is absent. localStorage holds only the SHA-256 revision.
3. n/a (unchanged)
4. n/a (unchanged)
5. ✅ No new scopes.
6. ✅ Nothing new is stored on the backend.
7. ✅ No GPS used.
8. ✅ No logging added in the route, service or bell.
9. ✅ No client logging change.
10. ✅ Errors use `ErrorResponse.NOT_CONNECTED` and the existing StatusPages.
11. ✅ `hasConnectedSession` check (`WeeklySummaryRoutes.kt:17`) and 401 test.
12. n/a (unchanged)
13. ✅ Notes rendered by interpolation (`weekly-summary-panel.html:62`). No `[innerHTML]`. The text holds only numbers and fixed words.
14. n/a (GET only)
15. ✅ The DTO holds only the UI fields. The route test checks the exact keys and that names and ids are absent.
16. ✅ Inline SVG icon. No CDN or font.
17. ✅ Unchanged
18. ✅ Unchanged (disconnect still deletes runs). The seen-revision hash is left behind, as the spec accepts.
19. n/a
20. n/a
21. ✅ Invented fixture data.

## Summary
The implementation matches the spec closely. Note texts, rounding, baselines, ranking, revision, the endpoint contract and the bell/panel behaviour all check out against the code, and every check passes. The main gap is tests: several acceptance criteria have no test that would fail if the behaviour broke (AC1.7, 2.3, 3.3, 6.4, 7.3, 8.4, 9.4, the R8 neutral boundaries, AC11.1, 11.6, 12.1 and 12.3), and the AC9.1 ranking test can't fail. Fix those first, then decide about the panel covering the bell.

Not checked: I did not test the layout in a real browser; the panel/bell overlap is worked out from the CSS. The other files git lists as modified (chart-options, workout-pace and so on) differ only in line endings, so I left them out.

---

## Resolution (2026-11-03)
- **[Major] Backend AC tests:** fixed. Added `R1 - manual run counts for distance only`, `R2 - note skipped without any baseline`, `R3 - distance matches week comparison`, `R6 - runs without HR add zero`, `R7 - excluded drift runs` (TOO_SHORT, NO_DATA, not fetched), `R8 - neutral just below the thresholds` (+4.9 bpm, -14%), `R8 - old week skips wellness only` and `R9 - every summary has a note`. The AC9.1 test now asserts an explicit metric sequence for an 8-candidate fixture.
- **[Major] Frontend AC tests:** fixed. Added loading/error no-badge (AC11.6), overlay `position: fixed` and in-host rendering (AC12.1), focus to heading and back to the bell on Escape (AC12.2, AC12.3), and the header order `h1`, `app-weekly-summary-bell` in the page plan spec (AC11.1). Panel behaviour is tested through the bell spec, not a separate `weekly-summary-panel.spec.ts`.
- **[Minor] Panel covers the bell:** fixed. The panel now starts below the page header (`top: 5.5rem`), so the bell stays clickable as a toggle. While scrolled down a strip of page shows above the panel. Not checked in a real browser.
- **[Nit] Badge date not reactive:** not fixed. The badge re-evaluates on every sync reload (about every 15 min while the dashboard is open), which is enough for a weekly cutoff.
- **[Nit] File layout:** not fixed. Notes were split into `RunNotes`/`HeartNotes`/`NoteText` to stay under detekt's function-count limits. `lastWeekMonday` stays next to the read-state helpers, since the badge rule is its only caller.
- **[Nit] Drift selection vs 013:** fixed. `driftOf` now also requires `averageHeartRateBpm != null`, matching 013's POINT bucket.
