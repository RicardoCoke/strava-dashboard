# Review: 025-workout-compare
**Base:** main → **Head:** 1fff24e
**Verdict:** APPROVE WITH CHANGES

## Checks
- Backend: ktlint pass · detekt pass (0 issues) · tests 749 passed, 0 failed (Gradle reported every task UP-TO-DATE, so these are the results of the last run on identical inputs)
- Frontend: lint pass · tests 494 passed, 0 failed · build pass

## Spec coverage
| Req | Implemented | Tested | Notes |
|---|---|---|---|
| R1 | ✅ | ✅ | nav, route, header order, shell flip and quiet reload all tested |
| R2 | ✅ | ✅ | tie order by id (AC2.1) and pace null for 0 time or 0 distance (AC2.3) are not tested, see Minor |
| R3 | ✅ | ✅ | |
| R4 | ✅ | ✅ | equal-`startUtc` tie (AC4.1) is not tested, see Minor |
| R5 | ✅ | ✅ | the extra budget unit after a drift NotFound is not asserted. More than one log line is possible (Minor) |
| R6 | ✅ | ✅ | the bpm ≤ 0 skip is not tested on its own (Minor) |
| R7 | ❌ | ✅ | AC7.3 fails with real ECharts: the tooltip shows one run only (Major). The test uses hand-built params |
| R8 | ✅ | ✅ | |
| R9 | ✅ | ❌ | rep differences (AC9.2–9.4) are never tested (Major) |
| R10 | ✅ | ❌ | AC10.4 differences and `pieces` JSON are never tested (Major) |
| R11 | ✅ | ✅ | |
| R12 | ✅ | ✅ | |
| R13 | ✅ | n/a | SCSS checked: tokens only, 44 px targets, 45vh / 75vh, 320 px column, focus ring. Manual 360 px check not done |

## Findings
### [Major] Chart tooltip shows only one run on the default distance axis
- **Where:** `frontend/src/app/features/workout-compare/workout-compare.options.ts:38-43` (formatter at `:91`). ECharts behaviour: `frontend/node_modules/echarts/lib/component/axisPointer/axisTrigger.js:199-205`
- **Problem:** `compareTooltipLines` expects ECharts' axis-trigger `params` to hold the nearest point of every visible series. On a `value` x-axis, `buildPayloadsBySeries` keeps only the series whose nearest x is closest to the pointer and drops the rest (`payloadBatch.length = 0`). The two runs' x values almost never match.
- **Failure scenario:** two stream-resolution runs on "Distance" (the default). Earlier points are at 0.027, 0.055 km and later points at 0.029, 0.058 km (the spec's own example). Hovering near 0.056 km gives `params` with only the earlier run's two series, so the tooltip is "0.06 km / Earlier · 10 Nov: …" with no line for the later run. The same happens on "Time" at split resolution, because cumulative split times differ. AC7.3 ("one line per visible run") fails in the main view. The unit test passes only because it builds params for all four series, which ECharts never produces here.
- **Suggested fix:** take `axisValue` from `params[0]` and find each run's nearest point yourself (binary search on that run's x values). Track legend selection (`legendselectchanged`) for visibility instead of relying on `params`.

### [Major] Rep differences and rep/pieces JSON are never tested
- **Where:** `backend/src/main/kotlin/app/stravadashboard/api/WorkoutCompareMappers.kt:91-92` (also `:73` mode, `:41` coarse `retryAfter`)
- **Problem:** AC9.2, 9.3, 9.4 and 10.4 define `paceDiffSecondsPerKm` / `heartRateDiffBpm` as later − earlier from the rounded values, and null when a side or the HR is missing. Only the mapper computes them. `RepComparerTest` checks domain cells only. The one API test with reps (`WorkoutCompareRoutesTest` "R4 - response shape") covers only `unavailable` mode. The frontend tests use fixtures with the differences already filled in.
- **Failure scenario:** change line 91 to `a.paceSecondsPerKm - b.paceSecondsPerKm`, or diff the unrounded values. Every "faster/slower" in the rep table flips, and all 749 backend and 494 frontend tests still pass.
- **Suggested fix:** add a mapper test (like `EfficiencyFactorMappersTest`) that serialises a `REPS` and a `PIECES` `RepComparison`, with one missing side and one missing HR. It also covers `mode`, `pieceMinutes`, `piecesRunId` and the ISO `retryAfter` for `callLimit`.

### [Minor] A compare request can log more than one line
- **Where:** `backend/src/main/kotlin/app/stravadashboard/domain/workoutcompare/WorkoutCompareService.kt:87`, together with `backend/src/main/kotlin/app/stravadashboard/garmin/GarminConnect.kt:194`, `:209` and `backend/src/main/kotlin/app/stravadashboard/garmin/GarminFit.kt:128`
- **Problem:** AC5.10 allows at most one line per compare request. The service adds its info line on top of the existing Garmin warnings.
- **Failure scenario:** a Garmin 5xx or network error during `driftStream` logs "Garmin Connect fit returned status 503" (or "request failed: IOException") plus "Workout compare uses splits: GARMIN_UNAVAILABLE", so two lines. An undecodable FIT file does the same with GarminFit's warning. The content is safe (call name, status, exception class), so there is no privacy leak. The test covers only the fake NotFound path.
- **Suggested fix:** either accept this and note in review.md that the extra lines come from the existing Garmin client, or drop the service line when the Garmin layer has already logged.

### [Minor] List pace rule duplicated in the api layer
- **Where:** `backend/src/main/kotlin/app/stravadashboard/api/WorkoutCompareMappers.kt:20,27` duplicates `backend/src/main/kotlin/app/stravadashboard/domain/workoutcompare/CompareCalculator.kt:85,91`
- **Problem:** the rule "moving > 0 and distance > 0, then `paceOf`" is written twice: once in the domain (stats) and once in the api mapper (list items). Standard A5 puts unit conversion in the domain.
- **Failure scenario:** if one guard changes (for example the zero-distance rule), the list row and the stats block show different paces for the same run.
- **Suggested fix:** use `CompareCalculator.stats(run, null).pace` in the list mapper, or one small domain helper for both.

### [Minor] Spec'd edge cases without a test that would fail
- **Where:** `backend/src/main/kotlin/app/stravadashboard/domain/workoutcompare/WorkoutCompareService.kt:61,75`, `backend/src/main/kotlin/app/stravadashboard/domain/workoutcompare/CompareCalculator.kt:131`, `backend/src/test/kotlin/app/stravadashboard/domain/workoutcompare/WorkoutCompareServiceTest.kt:150`
- **Problem:**
  - AC2.1 (ties by id descending) and AC4.1 (equal `startUtc` puts the smaller id first): no test has two runs with the same `startUtc`.
  - AC2.3: pace null for moving time 0 or distance 0 is not tested, only null moving time.
  - AC6.3: the only 0 bpm sample in "R6 - time weighted HR skips long gaps" starts a 45 s gap, so the bpm rule is never tested on its own.
  - AC5.1: the second budget unit after a drift NotFound is never asserted.
- **Failure scenario:** each of these mutations passes every test:
  - flip `thenByDescending` / `thenBy` at lines 61 and 75
  - remove `bpm > 0` at line 131
  - stop charging the fallback `speedStream`
- **Suggested fix:** add one assertion per case to the existing tests.

### [Nit] Redundant null check in PersonStack
- **Where:** `backend/src/main/kotlin/app/stravadashboard/PersonStack.kt:102-109`
- **Problem:** `activities` is non-null exactly when `garmin` is, so `if (garmin != null && activities != null)` checks the same thing twice, and ktlint has wrapped it awkwardly.
- **Failure scenario:** none. Readability only.
- **Suggested fix:** create `activities` and `sync` together inside one `garmin?.let { … }`.

### [Nit] `camelCase` helper is hard to read
- **Where:** `backend/src/main/kotlin/app/stravadashboard/api/WorkoutCompareMappers.kt:99-111`
- **Problem:** `if (i ==\n 0\n)` wrapping makes a one-line helper hard to scan.
- **Failure scenario:** none.
- **Suggested fix:** give each enum its JSON code, or write a one-line `mapIndexed`.

### [Nit] Concurrency test depends on wall-clock timing
- **Where:** `backend/src/test/kotlin/app/stravadashboard/garmin/GarminActivitySourceTest.kt:255`
- **Problem:** a real `delay(200)` goes against A8 ("no sleeps"). If the detail call takes longer than 200 ms on a slow runner, the FIT download finishes first and the test passes even without the Mutex.
- **Failure scenario:** on a slow CI runner the test stops guarding AC5.9.
- **Suggested fix:** block the FIT response on a `CompletableDeferred` until both calls have started.

## Privacy checklist
1. ✅ No secrets in the diff. Note: the working tree has an uncommitted `.env.example` change outside the reviewed range. I was not allowed to inspect it. The spec says there are no `.env.example` changes.
2. ✅ The Garmin key and token stay in the backend. No DTO carries them.
3. ✅ The existing one-refresh token flow is unchanged.
4. n/a
5. n/a
6. ✅ Nothing is persisted (`ReadOnlyRuns` fails on any write, and the sync state is never saved).
7. ✅ No GPS: FIT positions are not read and no coordinates are in the DTOs.
8. ✅ Logs carry only the reason code and the existing call name / status. Line count is covered in the Minor above.
9. ✅ No client logging change.
10. ✅ Error texts are fixed. Garmin failures appear only as reason codes.
11. ❌ Existing gap (021, accepted in the spec): no auth, LAN only. New: a GET now spends the person's Garmin budget.
12. n/a
13. ✅ The tooltip and legend HTML use only "Earlier/Later · d MMM" labels and numbers. Names are rendered by interpolation. No `[innerHTML]`.
14. n/a (GET only)
15. ✅ Only the fields the UI needs, same origin.
16. ✅ No new dependency or CDN.
17. ✅
18. ❌ Existing gap: no in-app delete.
19. n/a
20. ✅ No new DB data.
21. ✅ Fixtures and streams are invented.

## Summary
The feature is well built and closely follows the spec. The domain is pure and well tested, the API stays thin, the page states are complete, and all checks pass. The most important fix is the chart tooltip: with real ECharts it shows only one run on the default distance axis, which breaks AC7.3. Next, add mapper tests for the rep and piece differences, which nothing tests today. I did not do the 360 px / 1280 px manual layout check (AC13.1–13.2), and I did not inspect the uncommitted `.env.example` change.

---

## Fixes (after this review)
Backend: ktlint, detekt and 754 tests pass. Frontend: lint, 497 tests and the build pass.

- **[Major] Tooltip shows one run only:** fixed. The formatter now takes the hovered x (`axisValue`) and looks up each run's nearest point itself; a run past its last point gets no line. Legend visibility comes from `legendselectchanged`, passed out of the shared `app-chart` as `legendChange`, kept by the page and fed back as `legend.selected`, so an axis switch keeps it too. New tests: `R7 - tooltip shows both runs when ECharts passes only the nearest series`, `R7 - legend selection is kept in rebuilt options`, `R7 - a legend click survives an axis switch`.
- **[Major] Rep differences never tested:** fixed. `WorkoutCompareMappersTest` covers `reps` and `pieces` JSON, later − earlier from the rounded values (166 − 170 = −4, not round(−3.2)), a missing side, a missing HR, `pieceMinutes`, `piecesRunId` and the coarse codes with the ISO `retryAfter`.
- **[Minor] More than one log line:** fixed by deleting the compare service's own log line. A Garmin failure is still logged once by the existing Garmin client; every other fallback is shown on the page, so a compare request now logs at most that one line. The test is now `R5 - compare adds no log lines of its own`.
- **[Minor] List pace rule duplicated:** fixed. `CompareCalculator.averagePace(run)` is the one rule, used by both the stats and the list mapper.
- **[Minor] Edge cases without a failing test:** fixed. New assertions for the list tie by id (AC2.1), equal `startUtc` ordering (AC4.1), pace null for 0 moving time and 0 distance (AC2.3), a 0 bpm sample in a short interval (AC6.3) and the extra budget unit after a drift NotFound (AC5.1).
- **[Nit] Redundant null check in PersonStack:** fixed. The FIT source and the sync are created together in one `garmin?.let`.
- **[Nit] `camelCase` hard to read:** fixed. It is now one regex replace.
- **[Nit] Concurrency test uses wall-clock timing:** not fixed. With the `Mutex`, the second call waits on the lock and makes no request, so nothing outside the source can observe that it started; a gate on the FIT response would still need a timeout to tell the two cases apart. The test can only miss a regression on a very slow runner, never fail when the code is right. Upgrade to a virtual-time engine if it ever runs in CI.
- **Not done:** the manual 360 px / 1280 px layout check (AC13.1–13.2). The uncommitted `.env.example` change belongs to the user's own work and is not part of this feature.

---

# Review: 025-workout-compare
**Base:** main → **Head:** 21c2b16
**Verdict:** APPROVE

## Checks
- Backend: not run. The diff changes no backend files, so ktlint, detekt and tests were skipped.
- Frontend: lint pass · tests 501 passed, 0 failed · build pass

## Spec coverage
The range `main...HEAD` holds only the amendment commit, "zoom presets and narrow header". It touches AC7.1, AC7.4, AC7.9 and AC13.1. R1–R6 and R8–R12 are already on `main` and this diff does not change them.

| Req | Implemented | Tested | Notes |
|---|---|---|---|
| R7 / AC7.9 (new) | ✅ | ✅ | The `role="group"` "Chart zoom" sits in the wrapping `.chart-controls` row with 1x/2x/4x buttons and `aria-pressed`. 1x has no `dataZoom`. 2x/4x use a slider with `start: 0`, `end: 50/25`, `zoomLock` and `filterMode: 'none'`, and the grid bottom is 72. Changing zoom or axis sends no request. The test does not assert `filterMode` (see Minor). |
| R7 / AC7.4 (changed) | ✅ | ✅ | `withTouchZoom` keeps a chart's own `dataZoom` (`touch-zoom.ts:20`). Test: `025 - a chart with its own dataZoom keeps it on touch`. |
| R7 / AC7.1 (changed) | ✅ | ✅ | The selection is a plain object, updated in place by `onLegend`. The tooltip closure reads it on each hover, and rebuilt options copy it into `legend.selected`. Tests: `tooltip reads the legend selection on each hover`, and `a legend click survives an axis switch`, which asserts the options are not rebuilt (`toBe(before)`). |
| R13 / AC13.1 (fixed) | ✅ | n/a (manual) | `.page-header` now wraps (`flex-wrap: wrap`). `.header-actions { margin-left: auto }` keeps the actions right-aligned on the second row. Performance, Recovery and Compare all use the same markup. |
| R1–R6, R8–R12 | — | — | Not changed by this diff. |

## Findings
### [Minor] AC7.9 test does not check `filterMode: 'none'`, the setting that keeps the axes in place
- **Where:** `frontend/src/app/features/workout-compare/workout-compare.options.spec.ts:138-141` (implementation at `frontend/src/app/features/workout-compare/workout-compare.options.ts:80`)
- **Problem:** The 2x/4x test uses `objectContaining({ type, xAxisIndex, start, end, zoomLock })` and never asserts `filterMode`. AC7.9 requires `filterMode: 'none'` so that "the pace and HR axes stay in place while scrolling".
- **Failure scenario:** Someone deletes or changes `filterMode: 'none'`, so ECharts falls back to its slider default, `'filter'`. Both y-axes have `scale: true`, so they then rescale to the visible points on every scroll step and the lines jump. The test still passes.
- **Suggested fix:** Add `filterMode: 'none'` to the `objectContaining` for `bar(2)` (and for `bar(4)`).

### [Nit] At 2x/4x a keyboard user cannot reach the rest of the run
- **Where:** `frontend/src/app/features/workout-compare/workout-compare.options.ts:76-94` and `frontend/src/app/shared/chart/chart.ts:14`
- **Problem:** At 2x/4x the slider is the only way to scroll. It is drawn on the canvas inside a `role="img"` div that cannot take focus, so it does not work with the keyboard. It is also only 20 px tall (`height: 20`), compared with the 44 px used for the page's other controls on phones.
- **Failure scenario:** A keyboard-only user picks 4x and sees only the first 25 % of the x-axis, with no way to scroll. They can still go back to 1x to see the whole run.
- **Suggested fix:** Optional. Accept this, or add small "earlier/later window" buttons, or make the slider taller on `(pointer: coarse)`.

## Privacy checklist
1. ✅ No secrets, keys or `.env` files in the diff. Fixtures are unchanged.
2. ✅ No token or key handling in this diff.
3. n/a
4. n/a (no OAuth)
5. n/a (no scopes)
6. ✅ Nothing new is stored.
7. ✅ No GPS.
8. ✅ No new logging or `console` output.
9. n/a (no client logging change)
10. ✅ No new error responses.
11. ⚠ This is the existing state since 021: no auth, home network only. The diff does not change it.
12. n/a
13. ✅ No `[innerHTML]`. The tooltip still builds HTML only from "Earlier/Later · d MMM" labels and numbers, and the new zoom buttons render numbers through interpolation.
14. n/a (no state-changing endpoint)
15. ✅ No API change.
16. ✅ `DataZoomSliderComponent` comes from the bundled npm `echarts`. No CDN or third party.
17. ✅ Data stays local.
18. ❌ Existing gap, not introduced here: there is no in-app delete.
19. n/a
20. ✅ Unchanged.
21. ✅ Only invented fixture data is used.

## Summary
The amendment is small and correct and matches the spec. ngx-echarts applies new options with `notMerge`, so going from 4x back to 1x removes the slider, and every rebuild for an axis or zoom change starts back at the left edge. A legend click now updates the shared selection object without rebuilding the options, so the scroll position survives it. The one thing worth fixing is the missing `filterMode: 'none'` assertion in the AC7.9 test. Not checked: the AC13.1 360 px / 1280 px layout, which needs a manual browser check, and the uncommitted `.env.example` change in the working tree, which is outside `main...HEAD` (permission to inspect it was denied). Check it for real values before committing it.

---

## Fixes (after this review)
Frontend: lint, 501 tests and the build pass. Backend untouched.

- **[Minor] `filterMode: 'none'` not asserted:** fixed. Both the 2x and 4x expectations now check it, with a comment on why it matters.
- **[Nit] Keyboard and small slider:** partly fixed. The scroll bar is now 28 px tall (the grid bottom grows to 80 px), so it's easier to grab on a phone; its window is also much wider than tall, at half or a quarter of the chart. Keyboard scrolling is not added: the chart is a canvas `role="img"`, like every chart in the app, and 1x always shows the whole run. The numbers are also in the stats and rep tables. Revisit if keyboard use of the charts becomes a need.
- **AC13.1 manual check:** done before this review, in the browser against the Pi's backend: at 360 px the page is exactly 360 px wide, with the header on two rows; at 1280 px the header is one row and the chart controls share one row.
- **`.env.example`:** the user's own uncommitted change, not part of this branch.
