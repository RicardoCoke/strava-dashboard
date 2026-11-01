# Review: 012-marathon-pace-volume
**Base:** bb7f0ee → **Head:** cbd2681
**Verdict:** APPROVE

## Checks
- Backend: ktlint pass · detekt pass (0 issues) · tests pass (`./gradlew ktlintCheck detekt test` BUILD SUCCESSFUL. Gradle reused cached results for every task, so it did not print a test count. 0 failed.)
- Frontend: lint pass · tests 253 passed, 0 failed (43 files) · build pass

## Spec coverage
| Req | Implemented | Tested | Notes |
|---|---|---|---|
| R1 | ✅ | ✅ | AC1.1–AC1.4 in `MarathonPaceSettingTest`. AC1.5 checked in the `.env.example` diff: no value and the exact comment. |
| R2 | ✅ | ✅ | AC2.1–AC2.9 all covered in `MarathonPaceVolumeTest` |
| R3 | ✅ | ✅ | AC3.1–AC3.9 covered. The label format (`d MMM`, "7 Sep") is checked in the route test. |
| R4 | ✅ | ✅ | AC4.1–AC4.7 covered, including a cross-check with `WeekComparison` |
| R5 | ✅ | ⚠️ | The "session but no stored tokens → 401" part of AC5.2 has no test (see Minor) |
| R6 | ✅ | ✅ | Options, tooltip, subtitle, summary and aria-label are all tested |
| R7 | ✅ | ✅ | All states, the shape check, 401 and the page-level isolation (AC7.7) are tested |
| R8 | ✅ | ✅ | `running-dashboard-page.marathon-pace.spec.ts` |
| R9 | ✅ | ✅ | Tested in both backend and frontend. Treadmill runs are excluded from the pending count. |
| R10 | ✅ | ✅ | Quiet reload, and the current content is kept when the reload fails |

## Findings
### [Minor] AC5.2 "session but no stored tokens" is not tested
- **Where:** `backend/src/test/kotlin/app/stravadashboard/api/MarathonPaceRoutesTest.kt:152`
- **Problem:** `R5 - not connected gives 401` only covers a request with no cookie. AC5.2 also requires a 401 when there is a session but the tokens are gone. That path depends on `hasConnectedSession(tokens)` (`MarathonPaceRoutes.kt:17`) and on `MarathonPaceResult.NotConnected` (`MarathonPaceService.kt:39`), and no test exercises either one.
- **Failure scenario:** Suppose someone later removes the token check from `hasConnectedSession` or reorders the service so it computes before checking tokens. A user with a stale session cookie whose tokens were deleted (disconnect) would then get 200 with cached data. No test would fail.
- **Suggested fix:** Add a route test that connects, deletes the stored tokens (or disconnects), then calls `/api/marathon-pace` and expects 401 with the `not_connected` body.

### [Minor] The shape guard skips fields the chart depends on
- **Where:** `frontend/src/app/core/api/marathon-pace.models.ts:59`
- **Problem:** `isMarathonPaceResponse` only checks `mpKm` on each week. `buildMarathonPaceOptions` and `weekTooltip` also use `label`, `weekEnd`, `weekIndex`, `isCurrentWeek` and `isFuture` (`marathon-pace.options.ts:21,39,49,53`). The guard also doesn't check `summary.thisWeekSharePercent`, which `summaryItems` uses.
- **Failure scenario:** Take a 200 body whose weeks lack `label` or `weekEnd`, for example after a backend contract drift. The guard passes it, the card goes to the normal state, and the x-axis and tooltips render "undefined" (or `formatDayMonth(undefined)`) instead of the error state AC7.4 requires.
- **Suggested fix:** In `every(...)`, also check `typeof week['label'] === 'string'`, `typeof week['weekEnd'] === 'string'`, `typeof week['weekIndex'] === 'number'` and the two booleans. In the summary, check that `thisWeekSharePercent` is a number or null.

### [Nit] `MpSummary` shape differs from the spec's design sketch
- **Where:** `backend/src/main/kotlin/app/stravadashboard/domain/marathonpace/MarathonPaceVolume.kt:30-37`
- **Problem:** The spec sketches `thisWeek: DateWindow`. The code uses separate `thisWeekStart` and `thisWeekEnd`. The JSON contract is unaffected, so this is a naming/design detail only.
- **Suggested fix:** Optional. Use the existing `DateWindow` for consistency with `WeekComparison`.

## Privacy checklist
1. ✅ No secrets. `.env.example` adds `MARATHON_GOAL_PACE=` with no value. Fixtures use invented values.
2. ✅ No token use. The endpoint reads only `TokenRepository.find()` for a presence check and `RunRepository.findAll()`.
3. n/a. OAuth is unchanged.
4. n/a
5. ✅ No new scope.
6. ✅ No new stored fields or schema change.
7. ✅ No GPS read or returned.
8. ✅ No new logging. `MarathonPaceSetting.Valid.toString()` is redacted and `AppConfig.toString()` shows `<set>`, `<unset>` or `<invalid>` (tested).
9. n/a. No client logging change.
10. ✅ Fixed `{error, message}` bodies. The 422 message never includes the configured value (`MarathonPaceDtos.kt:53-57`).
11. ✅ The route checks `hasConnectedSession` (`MarathonPaceRoutes.kt:17`).
12. n/a
13. ✅ Template uses interpolation only. Tooltip and label formatters build text from numbers and dates only (`marathon-pace.options.ts:19-27,56`).
14. n/a. GET only.
15. ✅ Chart-ready DTOs only, no raw Strava objects.
16. ✅ No third parties. ECharts is bundled.
17. ✅ No sharing.
18. ✅ Unchanged.
19. ✅ Unchanged.
20. ✅ Unchanged.
21. ✅ Backend tests and `testing/marathon-pace-fixtures.ts` use invented data.

## Summary
The implementation follows the spec closely. The domain logic is pure and correct: the pace check is exact with inclusive edges, weeks are bucketed by `start_date_local`, and metres are summed before rounding. The backend contract matches the Angular interfaces field for field, and every acceptance criterion except one AC5.2 sub-case has a matching test. The most useful fixes are a route test for "session without tokens → 401" and a stricter frontend shape guard so a malformed 200 body shows the error state.

## Resolution
- **[Minor] AC5.2 session without tokens:** fixed. Added `R5 - session without stored tokens gives 401`: it connects, deletes the stored tokens, then expects 401 `not_connected`.
- **[Minor] Shape guard:** fixed. `isMarathonPaceResponse` now checks every week field the chart uses (`weekIndex`, `label`, `weekEnd`, `mpKm`, `isCurrentWeek`, `isFuture`) and that `thisWeekSharePercent` is a number or null. There is a new guard test for a week without `label`.
- **[Nit] `MpSummary` uses `DateWindow`:** fixed. `MpSummary.thisWeek` is now a `DateWindow`, the same type `WeekComparison` uses. The JSON is unchanged.
