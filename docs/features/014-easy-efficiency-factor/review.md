# Review: 014-easy-efficiency-factor
**Base:** dabee606d7438e451fecfea3416b4b5eda5cc532 → **Head:** b13263a
**Verdict:** APPROVE

## Checks
- Backend: ktlint pass · detekt pass (0 issues) · tests 433 passed, 0 failed (includes `EfficiencyFactorCalculatorTest` and `EfficiencyFactorRoutesTest`)
- Frontend: lint pass · tests 295 passed, 0 failed (52 files) · build pass

## Spec coverage
| Req | Implemented | Tested | Notes |
|---|---|---|---|
| R1 | ✅ | ✅ | AC1.8 test does not really split the local date from the UTC date (see Nit) |
| R2 | ✅ | ✅ | |
| R3 | ✅ | ✅ | |
| R4 | ✅ | ✅ | |
| R5 | ✅ | ✅ | Domain maths is tested. The DTO mapping of `change` (rounding to 4.2, the from/to fields) is not tested (see Minor) |
| R6 | ✅ | ✅ | |
| R7 | ✅ | ✅ | AC7.2 "session but no stored token" path not tested (see Minor) |
| R8 | ✅ | ✅ | AC8.1 placement amended to come after `app-long-run-drift-section`; tested in two page specs |
| R9 | ✅ | ✅ | |
| R10 | ✅ | ✅ | |
| R11 | ✅ | ✅ | |
| R12 | ✅ | ✅ | Existing suites pass. Page specs only gained `stubEfficiencyFactorSection()`. No scope or Strava client change |

## Findings
### [Minor] A quiet reload can replace a loud load and leave the spinner up forever
- **Where:** `frontend/src/app/features/efficiency-factor/efficiency-factor-section.ts:89-108`
- **Problem:** `load({ quiet: true })` cancels the request already in flight (`this.request?.unsubscribe()`). Its error branch and bad-body branch only set state when `!quiet`. If the cancelled request was the first load or a Retry (state `loading`), a failed quiet reload leaves nothing to move the card out of `loading`.
- **Failure scenario:** the dashboard opens, and the background sync finishes while the first `GET /api/efficiency-factor` is still pending. `dataVersion` bumps, so the first request is cancelled and a quiet one starts. That request returns 500, so the card shows "Loading efficiency factor…" until the next sync or a page reload, with no Retry button. The same pattern is in `long-run-drift-section.ts` (copied from 013), but this change adds a new copy of it.
- **Suggested fix:** treat a reload as quiet only when `state().kind === 'loaded'`. Otherwise use the normal error handling.

### [Minor] AC7.2 "session but no stored token" not tested
- **Where:** `backend/src/test/kotlin/app/stravadashboard/api/EfficiencyFactorRoutesTest.kt:150-159`
- **Problem:** the only 401 test calls the route with no session cookie at all. The second half of AC7.2 has no test: a valid session whose token was removed. That case is handled by the service's `NotConnected` branch at `EfficiencyFactorRoutes.kt:21-25` and `EfficiencyFactorService.kt:25`.
- **Failure scenario:** if `EfficiencyFactorService.get()` stopped checking `tokens.find()`, or the route mapped `NotConnected` to another status, the suite would still pass.
- **Suggested fix:** add a case that connects, deletes the token through the repository, and then expects 401 `not_connected`.

### [Minor] `change` JSON shape and rounding not tested end to end
- **Where:** `backend/src/main/kotlin/app/stravadashboard/api/EfficiencyFactorMappers.kt:35-42`; `backend/src/test/kotlin/app/stravadashboard/api/EfficiencyFactorRoutesTest.kt:127`
- **Problem:** the route test only checks `"change": null`. The calculator test checks the unrounded `4.227`. Nothing checks the field names `percent/fromDate/fromEfficiency/toDate/toEfficiency` or the half-up rounding to 1 and 3 decimals that AC5.1 and AC7.1 require ("exactly the JSON shape").
- **Failure scenario:** renaming `fromEfficiency`, or rounding `percent` with `roundEfficiency` (giving 4.227) instead of `roundOneDecimal`, would break the frontend tile and no test would fail.
- **Suggested fix:** add a mapper test, or a route-test seed that spans 28 or more days, and assert the full `change` object, e.g. `{"percent":4.2,"fromDate":"2026-05-03",...}`.

### [Minor] Section component copies the 013 drift section almost line for line
- **Where:** `frontend/src/app/features/efficiency-factor/efficiency-factor-section.ts:38-112`
- **Problem:** the constructor's sync subscription, `load()` with quiet handling, request replacement and `efStateFromError` are the same as `features/long-run-drift/long-run-drift-section.ts:37-108` and `vo2max-section.ts`. Bugs such as the quiet-reload one above now have to be fixed in three places.
- **Failure scenario:** a maintainability issue only. The quiet-reload fix goes into one card and not the others.
- **Suggested fix:** move the shared code into a small reusable "card loader" helper in `shared/`, or justify keeping the copies.

### [Nit] AC1.8 test does not split the local date from the UTC date
- **Where:** `backend/src/test/kotlin/app/stravadashboard/domain/efficiency/EfficiencyFactorCalculatorTest.kt:94-98`
- **Problem:** the `run()` helper sets `startUtc = startLocal.toInstant(ZoneOffset.UTC)`, so at 23:30 local the UTC date is still 2026-05-01. The test would also pass if the code bucketed by `startUtc`. The code itself is correct (`RunActivity.localDate` comes from `startLocal`).
- **Suggested fix:** build the `RunActivity` with a `startUtc` on 2026-05-02.

### [Nit] Chart input differs from the spec
- **Where:** `frontend/src/app/features/efficiency-factor/efficiency-factor-chart/efficiency-factor-chart.ts:16`
- **Problem:** the spec says `input.required<EfficiencyFactorResponse>()`. The code takes `EfPoint[]`. It works the same way and is arguably simpler. Update the spec to match.

### [Nit] Shape guard skips some fields
- **Where:** `frontend/src/app/core/api/efficiency-factor.models.ts:49-80`
- **Problem:** `isEfficiencyFactorResponse` does not check `movingTimeSeconds`, `windowStart`, `windowEnd` or most `change` fields (`fromEfficiency`, `toDate`, `toEfficiency`). None of these are rendered today, so there is no visible failure.

### [Nit] Domain test imports from the api layer
- **Where:** `backend/src/test/kotlin/app/stravadashboard/domain/efficiency/EfficiencyFactorCalculatorTest.kt:3`
- **Problem:** the domain test uses `app.stravadashboard.api.roundEfficiency`. Asserting with a delta, as the R5 test already does, would keep domain tests free of the api layer.

## Privacy checklist
1. ✅ No secrets. Fixtures are invented (`efficiency-factor-fixtures.ts`, route test uses `code=invented`).
2. ✅ No tokens in the response; the DTOs hold only numbers and dates.
3. n/a (OAuth unchanged)
4. n/a (OAuth unchanged)
5. ✅ No new scope.
6. ✅ No new stored fields, no schema change.
7. ✅ No GPS read or stored.
8. ✅ No new logging in the backend or frontend.
9. ✅ Client logging unchanged.
10. ✅ 401 uses the shared `ErrorResponse.NOT_CONNECTED`. 500s go through the existing `StatusPages`.
11. ✅ The route checks `hasConnectedSession(tokens)` (`EfficiencyFactorRoutes.kt:17`).
12. ✅ Cookies unchanged.
13. ✅ The tooltip formatter builds text only from dates and numbers (`efficiency-factor.options.ts:26-38`). No `[innerHTML]` or `bypassSecurityTrust*`.
14. n/a (GET only)
15. ✅ Only the fields the UI needs; the test asserts there is no activity name (`EfficiencyFactorRoutesTest.kt:137`).
16. ✅ No third-party calls.
17. ✅ Unchanged.
18. ✅ Unchanged; existing disconnect/delete covers the data.
19. ✅ Unchanged.
20. ✅ Unchanged.
21. ✅ Fixtures use invented numbers.

## Summary
The work is solid and closely follows the spec. The domain maths (rolling window, same-day ordering, 28-day staleness and change guards, pending count) is correct and well tested, and all backend and frontend checks pass. The most important fix is the quiet-reload path in `efficiency-factor-section.ts`: a sync that finishes during the first load, followed by a failed reload, leaves the card stuck on its spinner. Close the two test gaps (AC7.2 no-token case, `change` DTO shape) too.

---

## Resolution (implementer)
No Blockers or Majors, so the reviewer was not re-run. Checks after the fixes: backend ktlint, detekt and tests pass; frontend lint, 296 tests and the build pass.

| Finding | Outcome |
|---|---|
| [Minor] Quiet reload can leave the spinner up | **Fixed.** `load()` is only quiet when the card is already `loaded`, so a reload that replaces a first load or Retry shows its error. New test `014 R8 - a sync during the first load still shows a failure`. |
| [Minor] AC7.2 no-token case untested | **Fixed.** New route test `R7 - 401 not_connected with a session but no stored token` deletes the token after connecting. |
| [Minor] `change` JSON shape and rounding untested | **Fixed.** New `EfficiencyFactorMappersTest` asserts the full JSON, including `percent` at 1 decimal and the efficiencies at 3. |
| [Minor] Section copies the drift/VO2max loader | **Not changed here, justified.** A shared card loader would have to change the 008 and 013 cards (and their specs), which this feature must not touch while other features are built in parallel. The same quiet-reload bug exists in `long-run-drift-section.ts` and `vo2max-section.ts`, so the shared helper and those fixes are proposed as a separate follow-up. |
| [Nit] AC1.8 test doesn't split local from UTC date | **Fixed.** The run now has `startUtc` on 2 May while `start_date_local` is 1 May 23:30. |
| [Nit] Chart input differs from spec | **Fixed in the spec.** `specs.md` now says the chart takes `EfPoint[]`. |
| [Nit] Shape guard skips fields | **Fixed.** The guard also checks `windowStart`, `windowEnd`, `movingTimeSeconds` and all `change` fields. |
| [Nit] Domain test imports from the api layer | **Fixed.** The calculator test uses its own `round3` helper instead of `api.roundEfficiency`. |
