# Review: 024-chart-pinch-zoom
**Base:** 1b2946b → **Head:** 1b2946b + uncommitted working tree
**Verdict:** APPROVE

## Checks
- Backend: not run. The diff touches no backend code; the only non-frontend change is `.env.example`.
- Frontend: lint pass · tests 464 passed, 0 failed (80 files) · build pass

## Spec coverage
| Req | Implemented | Tested | Notes |
|---|---|---|---|
| R1 | ✅ | ✅ | AC1.1 in `touch-zoom.spec.ts:7`. AC1.2 and AC1.3 are manual. |
| R2 | ✅ | ✅ | AC2.1 in `touch-zoom.spec.ts:13`. AC2.2 and AC2.3 are manual. |
| R3 | ✅ | ⚠️ | `touch-action: pan-x pan-y` is set at `chart.ts:23`. The AC3.1 test only checks the stylesheet, not the element (see Minor). AC3.2 and AC3.3 are manual. |
| R4 | ✅ | ✅ | AC4.1 in `touch-zoom.spec.ts:17`. |
| R5 | ✅ | n/a | Manual only. `touch-action` is limited to `.chart`, so nothing else changes. |
| R6 | ✅ | ✅ | `start: 0` and `end: 100` are constants, tested at `touch-zoom.spec.ts:13`. Nothing is stored. |
| R7 | ✅ | ✅ | `touch-zoom.spec.ts:28` checks that the same reference comes back. |
| R8 | ✅ | ✅ | `touch-zoom.spec.ts:32`. `DataZoomInsideComponent` is registered in `echarts-setup.ts`. No files under `features/` changed, and no feature sets its own `dataZoom` (checked with grep), so none gets overwritten. |

## Findings
### [Minor] The AC3.1 test never checks the rendered chart element
- **Where:** `frontend/src/app/shared/chart/chart.spec.ts:7-15`
- **Problem:** `stubCharts()` replaces the template with `<div class="chart-stub">`, so no `.chart` element is ever rendered. The test then collects every `<style>` in `document` and matches a `.chart[...] { touch-action: pan-x pan-y }` rule. That proves the component's stylesheet has the rule. It does not prove that "its chart element has the CSS", which is what AC3.1 asks for.
- **Failure scenario:** someone renames the template's `class="chart"`, or moves the rule to a selector the template doesn't use. The rule still appears in the stylesheet, so the test passes, but on the phone a pinch on the chart zooms the page again (R3 broken).
- **Suggested fix:** don't call `stubCharts()` here, or override only `imports` and keep the real template. Then also assert that `fixture.nativeElement.querySelector('.chart')` exists and has the scoped attribute the CSS rule matches.

### [Nit] `.env.example` change is outside this feature
- **Where:** `.env.example:5-9` (removed lines)
- **Problem:** the diff removes `STRAVA_CLIENT_ID`, `STRAVA_CLIENT_SECRET` and `STRAVA_REDIRECT_URI`. The cleanup is correct, since `backend/src/main/kotlin/app/stravadashboard/config/AppConfig.kt` no longer reads any `STRAVA_*` variable. But the spec doesn't list it under "Files expected to change".
- **Suggested fix:** commit it separately as a `chore` so this feature's diff stays limited to `shared/chart/`.

## Privacy checklist
1. ✅ No secrets in the diff. The `.env.example` change only removes empty keys.
2. n/a · 3. n/a · 4. n/a · 5. n/a
6. ✅ Nothing stored.
7. n/a
8. ✅ No new logging or console output.
9. n/a · 10. n/a · 11. n/a · 12. n/a
13. ✅ No new formatters, `innerHTML` or HTML building.
14. n/a · 15. n/a
16. ✅ `DataZoomInsideComponent` comes from the bundled `echarts` package. No new dependency and no CDN.
17. n/a · 18. n/a · 19. n/a · 20. n/a
21. ✅ Test fixtures use invented options (`[1, 2]` series).

## Summary
The change is small and matches the spec: one pure function, one computed signal and one CSS rule. All automated checks pass. The only real gap is that the AC3.1 component test reads the stylesheet instead of the rendered element, so it wouldn't catch a template/class mismatch that breaks R3. The iPhone and desktop behaviour (AC1.2, AC2.2, AC3.2, AC4.2, AC5.1, AC6.2, AC7.2, AC8.3 and the other manual ACs) can't be checked here and still needs the manual spot-check on the device.

Not checked: backend checks (no backend code changed), and any behaviour on a real device.

---

## Follow-up (2026-11-14, after the review)
- **Minor (AC3.1 test):** fixed. `chart.spec.ts` now keeps the real template, finds the rendered `.chart` element and matches the `touch-action` rule scoped to that element's own attribute.
- **Nit (`.env.example`):** not changed. That edit was already in the working tree before this feature started and is not part of it; it should be committed separately.
- Backend checks were run during the build step and passed (`ktlintCheck detekt test`).
