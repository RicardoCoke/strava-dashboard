# Review: 013-long-run-hr-drift
**Base:** bb7f0ee → **Head:** 711522a
**Verdict:** APPROVE

## Checks
- Backend: ktlint pass · detekt pass (0 issues) · tests 374 passed, 0 failed
- Frontend: lint pass · tests 249 passed, 0 failed · build pass

## Spec coverage
| Req | Implemented | Tested | Notes |
|---|---|---|---|
| R1 | ✅ | ✅ | AC1.1–AC1.9 in `HrDriftCalculatorTest`. See the Minor finding on the extra `second.seconds == 0` no-data check |
| R2 | ✅ | ✅ | AC2.1–AC2.8 in `LongRunDriftSyncTest` and `DriftStreamClientTest` (keys exclude latlng, mapper null cases) |
| R3 | ✅ | ✅ | AC3.2 is tested with a 12-call window, not 80. The cap logic is the same |
| R4 | ✅ | ✅ | AC4.1–AC4.3 in `LongRunDriftRoutesTest` (zero Strava requests, exact keys, no name or ID) |
| R5 | ✅ | ✅ | AC5.1–AC5.8 in `LongRunDriftReportTest`. AC5.9 in the route test |
| R6 | ✅ | ✅ | Section, view and page-order specs cover AC6.1–AC6.10, including the 401 pass-through and the invalid-body error |
| R7 | ✅ | ✅ | `long-run-drift.options.spec.ts` covers bounds, bands, zone colours, tooltip and a single point |
| R8 | ✅ | ✅ | `HrDriftRepositoryTest`: table exists on reopen, upsert, prune/delete/deleteAll cascade, findAll join |

## Findings
### [Minor] One usable interval is reported as NO_DATA instead of TOO_SHORT
- **Where:** `backend/src/main/kotlin/app/stravadashboard/domain/longrundrift/HrDriftCalculator.kt:46`
- **Problem:** The no-data check includes `second.seconds == 0.0`. AC1.9 doesn't list that condition; it only lists all bpm 0, all speed 0, and first-half avgSpeed 0. The second half is empty only when there is exactly one usable interval (≤ 30 s), and a stream that short should fall through to the 60-minute rule.
- **Failure scenario:** A stream with HR and speed where only one interval is usable (for example, the rest is `moving = false` or dropouts) is stored as `NO_DATA`. The card then counts it as "missing HR or speed data" instead of "under 60 min usable".
- **Suggested fix:** Drop `second.seconds == 0.0` from `noData` and let the `usable < MIN_USABLE_SECONDS` branch handle it. The TOO_SHORT branch returns before the EF division, so nothing divides by zero.

### [Nit] The response guard is shallow
- **Where:** `frontend/src/app/core/api/long-run-drift.models.ts:53-68`
- **Problem:** `isLongRunDriftResponse` checks that `points` is an array and that `excluded.total` is a number. It doesn't check the point items, `windowStart`/`windowEnd`, or the other `excluded` fields.
- **Failure scenario:** A malformed 200 body with `points: [{}]` passes the guard. `driftAxisBounds` then gets `NaN` from `Math.min(undefined)` and the chart renders broken instead of showing the error state. This needs a misbehaving server.
- **Suggested fix:** Also check that each point has a string `date`, a number `driftPct`, a valid `zone` and a number `distanceKm`.

### [Nit] A quiet reload doesn't cancel an earlier request still in flight
- **Where:** `frontend/src/app/features/long-run-drift/long-run-drift-section.ts:83-104`
- **Problem:** Each `dataVersion` bump starts a new subscription, and older ones are not cancelled.
- **Failure scenario:** Two syncs finish close together and the first response arrives last. The card then shows the older report until the next sync.
- **Suggested fix:** Feed reload triggers through a `Subject` with `switchMap`. This matches whatever `Vo2maxSection` does, if that gets fixed too.

### [Nit] The drift fetch is a free function, while its siblings are private `SyncService` methods
- **Where:** `backend/src/main/kotlin/app/stravadashboard/domain/longrundrift/DriftStreamFetch.kt:16` and `backend/src/main/kotlin/app/stravadashboard/domain/SyncService.kt:205`
- **Problem:** `fetchDetail`, `fetchStream` and `fetchHrStream` are private methods of `SyncService`. `fetchDriftStream` lives in another package and takes `source`/`runs` as parameters. `ActivityCall` now also carries two independent booleans (`heartRate`, `drift`) that could both be true.
- **Suggested fix:** This is optional. Consider a sealed call kind in `ActivityCall` and keeping the fetchers together.

### [Nit] In the normal state, the pending line comes before the excluded line
- **Where:** `frontend/src/app/features/long-run-drift/long-run-drift-section.html:38-41`
- **Problem:** The spec's States section lists the order as "header, chart, legend, excluded line, pending line". The template renders pending first.
- **Suggested fix:** Swap the two `@if` blocks, or confirm that the order doesn't matter.

## Privacy checklist
1. ✅ No secrets. The fixture `drift-streams.json` is invented
2. ✅ No tokens in the response. The route test asserts there is no name or ID
3. ✅ Existing refresh/reauth path is unchanged. Tested for drift calls (AC2.7)
4. n/a OAuth is unchanged
5. ✅ No new scope
6. ✅ Only status, drift % and computed_at are stored (`Tables.kt` `RunHrDriftsTable`)
7. ✅ The keys `time,velocity_smooth,heartrate,moving` include no latlng. Test `streams request never asks for latlng`
8. ✅ Sync logs only status, counts and exception class names. The log-capture test asserts no drift or HR values
9. ✅ Client logging is unchanged
10. ✅ The standard `ErrorResponse` shape is used. The StatusPages refactor keeps path-only logging
11. ✅ `hasConnectedSession` check on the route, plus `tokens.find()` in the service
12. n/a No cookie change
13. ✅ No Strava text is rendered. The ECharts tooltip formatter builds its text from dates and numbers only
14. ✅ The endpoint is a GET with no side effects
15. ✅ The response carries only the fields the card needs
16. ✅ No new dependencies or external calls
17. ✅ Unchanged
18. ❌ deferred (carried from 001, as stated in the spec). The new rows are covered by `deleteAll`
19. n/a No webhook
20. ✅ Unchanged
21. ✅ All fixtures are marked and appear invented

## Summary
The implementation is clean and closely follows the spec. Every R# and acceptance criterion has a named test that would fail if the behaviour broke, and all backend and frontend checks pass. The only substantive item is the extra `second.seconds == 0.0` no-data check in `HrDriftCalculator.kt:46`: it puts a one-interval stream under "missing data" instead of "too short". The rest are optional polish.

---

## Resolution (author notes, added after the review)
No Blockers or Majors, so the reviewer was not re-run.

| Finding | Outcome |
|---|---|
| [Minor] One usable interval reported as NO_DATA | **Fixed.** `second.seconds == 0.0` dropped from the no-data check in `HrDriftCalculator`; a single usable interval is now `TOO_SHORT`. Test added to `R1 - needs 60 minutes usable moving time`. |
| [Nit] Response guard is shallow | **Fixed.** `isLongRunDriftResponse` now checks every point (`date`, `driftPct`, `zone`, `distanceKm`). Test `R6 - malformed point shows error`. |
| [Nit] Quiet reload doesn't cancel an earlier request | **Fixed.** `LongRunDriftSection` unsubscribes the in-flight request before starting a new one. Test `R6 - a newer reload cancels one still in flight`. `Vo2maxSection` has the same pattern and is left as is (out of scope for 013). |
| [Nit] Drift fetch is a free function | **Not changed.** `SyncService` is at detekt's `TooManyFunctions` limit (11), so a 12th private method fails the build. Keeping the fetcher in `domain/longrundrift/` keeps `SyncService` to a one-line branch, which also limits merge conflicts with features built in parallel. Replacing the `ActivityCall` booleans with a sealed call kind would touch every existing phase, so it is a refactor of its own. |
| [Nit] Pending line before excluded line | **Fixed.** The template now renders the excluded line, then the pending line, as in the spec. |

Note: during the fix run, `RoutesTest > R3 - failed refresh in background sync deletes tokens and returns strava_reauth_required` failed once and passed in 8 later runs, both alone and in the full suite. It looks like an existing timing flake in that 009 background-sync polling test, not something 013 changed.
