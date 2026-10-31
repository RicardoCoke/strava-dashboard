# Review: 002-dark-dashboard-redesign
**Base:** d009a34 → **Head:** d009a34 (plus uncommitted working tree)
**Verdict:** APPROVE

## Checks
- Backend: ktlint pass · detekt pass (0 issues) · tests pass (Gradle reported all tasks UP-TO-DATE, so these are cached results, BUILD SUCCESSFUL; no per-test count was printed)
- Frontend: lint pass · tests 70 passed, 0 failed (13 files) · build pass

## Spec coverage
| Req | Implemented | Tested | Notes |
|---|---|---|---|
| R1 | ✅ | ✅ | Tokens, meta tags and font stack match AC1.1/1.3/1.5. A grep found colour literals only in `styles.scss`, `palette.ts` and the `theme-color` meta tag. No `prefers-color-scheme`, `@import`, `url(` or external host. The palette test is in `palette.spec.ts` |
| R2 | ⚠️ | ✅ | The AC12.2 not-enough-data list items in the pace card are not muted (see Minor 1). The other style criteria are manual or review checks, as the spec says |
| R3 | ✅ | ✅ | Spinner: inline SVG, 64×64, track and arc circles. Two infinite 1.4 s animations, the dash grows from about 9.8 % to 74.7 %. `role="status"` and `aria-live="polite"` on the wrapper, `aria-hidden` on the SVG. No new dependency or asset |
| R4 | ✅ | ⚠️ | The test for AC4.4 does not cover the error state (see Minor 2). AC4.3 is a manual check |
| R5 | ✅ | ✅ | |
| R6 | ✅ | ✅ | `formatBpmChange` is shared with `hrTrendText`. −0 gives "0.0" and neutral |
| R7 | ✅ | ✅ | Uses `comparablePaceTypes(...)[0]` |
| R8 | ✅ | ✅ | `--color-worse` is used only by `.tone-worse` (AC8.4 verified by grep) |
| R9 | ✅ | ✅ | `chart-theme.ts` helpers are spread into all three builders. The 001 data and tooltip tests are unchanged apart from the replaced colour check |
| R10 | ✅ | ✅ | Contrast unit tests pass. Global `:focus-visible` is 2px accent with 2px offset. No `opacity` anywhere in `frontend/src` |
| R11 | ⚠️ | ✅ | Not fully checkable against the spec's baseline: feature 001 is also uncommitted, so I couldn't split a 002-only diff of `backend/` and `core/api/` from `d009a34`. The `core/api` files I read contain nothing 002-specific |

## Findings
### [Minor] Pace card "Not enough data yet" list items are not muted
- **Where:** `frontend/src/app/features/running-dashboard/workout-pace-comparison-chart/workout-pace-comparison-chart.ts:55-59`
- **Problem:** AC2.6 says card-level not-enough-data texts (including 001 AC12.2) use `--color-text-muted`. The `<h3>` is muted through `.card h3` in `styles.scss:81-88`. But `<ul class="not-enough"><li>` has no class or style that sets a colour, so it inherits `--color-text` from `body` (`styles.scss:28`).
- **Failure scenario:** Response with type `4x8min` where `previous = null`: "4x8min: no paced runs in the previous 30 days" renders in bright `#f2f4f5` instead of muted `#9aa3ad`. All other empty and not-enough texts are muted.
- **Suggested fix:** Add a muted colour for `.not-enough` (or add `class="empty"` to the list) using `var(--color-text-muted)`.

### [Minor] AC4.4 test name covers the error state but never checks it
- **Where:** `frontend/src/app/features/running-dashboard/running-dashboard-page.redesign.spec.ts:108-115`
- **Problem:** `002 R4 - no tiles in loading error or empty state` checks only the loading and page-empty states. It never answers with an error.
- **Failure scenario:** If someone moves `<app-headline-tiles>` outside the `@case ('loaded')` block in `running-dashboard-page.html`, tiles could show in the error state and this test would still pass.
- **Suggested fix:** Add a second fixture, or extend the test, that answers with a 500 and asserts `app-headline-tiles` is absent.

### [Nit] Static `class` repeated inside the `[class]` binding
- **Where:** `frontend/src/app/features/running-dashboard/headline-tiles/headline-tiles.ts:14`, `heart-rate-trend-chart/heart-rate-trend-chart.ts:33`, `workout-pace-comparison-chart/workout-pace-comparison-chart.ts:46`
- **Problem:** `class="value" [class]="'value tone-' + tile.tone"` names `value` twice (the same for `note` and `change`). `[class.tone-x]` or `[class]="'tone-' + tone"` next to the static class is enough.

### [Nit] Tile card styling duplicates `.card`
- **Where:** `frontend/src/app/features/running-dashboard/headline-tiles/headline-tiles.ts:38-43`
- **Problem:** `.tile` repeats the padding, border, radius and background of the global `.card` (`styles.scss:65-70`). If the card style changes, the tiles drift out of sync with it.

### [Nit] Zero pace change: tile and card text differ in case
- **Where:** `frontend/src/app/features/running-dashboard/headline-tiles/headline-tiles.model.ts:102` vs `frontend/src/app/shared/format/pace.ts:12`
- **Problem:** The tile reads "Same pace" and the card reads "same pace". The spec asks for exactly this (AC7.2 vs 001 AC11.5), but AC7.5 says they "read the same". The AC7.5 test only covers −7 and +3 (`headline-tiles.model.spec.ts:175`).

### [Nit] Tone and bpm format tests are in the palette spec
- **Where:** `frontend/src/app/shared/chart/palette.spec.ts:50-63`
- **Problem:** The tests for `toneForChange` and `formatBpmChange` live in `palette.spec.ts`, not next to `shared/format/tone.ts` and `heart-rate.ts` as the spec's file list expects. The tests themselves are correct.

## Privacy checklist
1. ✅ No secrets in the 002 frontend files or fixtures.
2. ✅ No token handling added to the frontend.
3. n/a (no change)
4. n/a (no change)
5. ✅ No scope change.
6. ✅ Nothing new stored.
7. ✅ No GPS used.
8. ✅ No `console` output added.
9. n/a
10. ✅ The error card shows fixed text only.
11. n/a (no route change)
12. n/a
13. ✅ Tiles use plain interpolation. No `[innerHTML]` or `bypassSecurityTrust*`. Tooltip formatters are unchanged and return plain strings built from numbers, dates and labels.
14. n/a
15. ✅ No new field or endpoint. Tiles add no request (AC4.5 test).
16. ✅ No external fonts, CDNs or URLs. No new npm dependency (`package.json` unchanged). Inline SVG only.
17. ✅ "Powered by Strava" footer is muted text on the background at 0.8rem and passes the contrast test.
18. n/a (deferred as in 001)
19. n/a
20. n/a
21. ✅ Fixtures are invented (`testing/dashboard-fixtures.ts`).

## Summary
The redesign is clean and matches the spec closely. Theme tokens, the palette, chart restyling, the spinner and the three headline tiles are implemented as pure, well-tested helpers, and all frontend and backend checks pass. The most important fix is to mute the pace card's "Not enough data yet" list items (AC2.6) and to add the missing error-state check to the AC4.4 test. AC11.1 (no backend or `core/api` change from 002) could not be checked by diff, because feature 001 is uncommitted in the same working tree.

## Resolution
Checks after the fixes: frontend `lint`, `test` (71 tests) and `build` pass.

| Finding | Outcome |
|---|---|
| [Minor] Pace card "Not enough data yet" not muted | Fixed: the list has the `empty` class (`--color-text-muted`). |
| [Minor] AC4.4 test skips the error state | Fixed: new test `002 R4 - no tiles in error state`; the other test is renamed `002 R4 - no tiles in loading or empty state`. |
| [Nit] Static class repeated in `[class]` | Fixed: bindings are now `[class]="'tone-' + …"`; Angular keeps the static class. |
| [Nit] Tile styling duplicates `.card` | Fixed: tiles use the global `card` class (as `<article>`, so they aren't counted as chart sections). |
| [Nit] "Same pace" vs "same pace" | Kept as specified: AC7.2 asks for "Same" + "pace" in the tile (value + unit), AC11.5 of 001 keeps the lowercase sentence in the card. |
| [Nit] Tone/bpm tests in the palette spec | Fixed: moved to `shared/format/tone.spec.ts`. |

Change requested by the user after the review: treadmill HR points are purple again (`palette.treadmill` / `--color-treadmill` `#a78bfa`, about 6.5:1 on the card surface, covered by the 3:1 chart-colour contrast test). Spec AC1.1, AC1.2, AC9.3 and decision 5 updated.
