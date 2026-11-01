# Review: 015-garmin-resting-hr-hrv
**Base:** bb7f0ee → **Head:** 9d2aefc
**Verdict:** APPROVE

## Checks
- Backend: ktlint pass · detekt pass (0 issues) · tests 382 passed, 0 failed (Gradle tasks were up to date, so these are the results from the last run on the same inputs)
- Frontend: lint pass · tests 252 passed, 0 failed (43 files) · build pass

## Spec coverage
| Req | Implemented | Tested | Notes |
|---|---|---|---|
| R1 | ✅ | ✅ | AC1.4 only asserts zero Intervals requests and a 200 from sync, not that Strava behaviour and `/api/sync` are identical (see Minor) |
| R2 | ✅ | ✅ | AC2.5 has no timeout case, and a non-JSON 200 escapes the client (see Minor) |
| R3 | ✅ | ✅ | |
| R4 | ✅ | ✅ | AC4.5 is tested only for `KeyRejected`, not for the AC4.4 exception path; AC4.6 is route-tested only for `keyRejected` |
| R5 | ✅ | ✅ | |
| R6 | ✅ | ✅ | |
| R7 | ✅ | ✅ | |
| R8 | ✅ | ✅ | AC8.2 "session not matching stored tokens" isn't tested for this route; the no-session case is |
| R9 | ✅ | ✅ | |
| R10 | ✅ | ✅ | AC10.2 is a manual check. The SCSS stacks the tiles below 768 px and uses a plain legend |
| R11 | ✅ | ✅ | No files changed under `domain/trainingload/` or `api/TrainingLoad*` |

## Findings
### [Minor] A non-JSON 200 body escapes the client instead of becoming `Unavailable`
- **Where:** `backend/src/main/kotlin/app/stravadashboard/intervals/IntervalsClient.kt:62-71`
- **Problem:** The client catches only `IOException`, `UnresolvedAddressException`, `SerializationException` and `ContentConvertException`. When a 200 response is not `application/json` (for example an HTML error page from a proxy), `response.body()` throws Ktor's `NoTransformationFoundException`, which extends `UnsupportedOperationException`. None of these catches handles it, so `IntervalsClient.wellness` throws instead of returning `IntervalsResult.Unavailable` as AC2.5 requires.
- **Failure scenario:** A 200 with `Content-Type: text/html`: the exception reaches `WellnessSync.refreshIfDue`. Its catch-all logs "failed unexpectedly: NoTransformationFoundException" and saves UNAVAILABLE. The end state is about the same, but the error goes through the "unexpected" path, and the client contract (AC2.5) isn't met. The AC2.5 test only sends bodies labelled `application/json`, and it has no timeout case.
- **Suggested fix:** Catch `NoTransformationFoundException` in the client, or check the content type before reading the body. Add a test with a non-JSON content type and one with an `HttpRequestTimeoutException`.

### [Minor] AC1.4 and AC4.5 assertions are incomplete
- **Where:** `backend/src/test/kotlin/app/stravadashboard/api/WellnessRoutesTest.kt:272-278`, `backend/src/test/kotlin/app/stravadashboard/domain/BackgroundSyncTest.kt:262-278`
- **Problem:** AC1.4 requires the job's Strava behaviour and `GET /api/sync` status to be identical with and without the key. The test asserts only that Intervals got zero requests and that sync returned 200. AC4.5 covers AC4.1 to AC4.4, but the test only uses `KeyRejected`. A wellness step that throws (AC4.4) is never shown to leave `SyncState` and `reauthRequired` unchanged.
- **Failure scenario:** A later change to `WellnessSync.recordUnavailable` could write to the wrong repository or change `/api/sync`, and these tests would still pass.
- **Suggested fix:** Compare the `/api/sync` body and the stored `SyncState` with and without the key. Add an AC4.5 case in which the `WellnessSource` throws.

### [Minor] Duplicate, unused `RATE_LIMIT_FALLBACK`
- **Where:** `backend/src/main/kotlin/app/stravadashboard/domain/wellness/WellnessPolicy.kt:22` and `backend/src/main/kotlin/app/stravadashboard/intervals/IntervalsClient.kt:101`
- **Problem:** `WellnessPolicy.RATE_LIMIT_FALLBACK` is never referenced. The client defines its own copy of the same 15-minute value.
- **Failure scenario:** If someone changes the policy constant, the 429 fallback doesn't change, because the client never reads it.
- **Suggested fix:** Delete one of the two constants and have the code use the one that's left.

### [Nit] Intervals CIO engine is created even when no key is set
- **Where:** `backend/src/main/kotlin/app/stravadashboard/Application.kt:87`, `:138`
- **Problem:** The default argument `intervalsEngine = CIO.create()` builds a CIO engine on every startup. When the key is unset, nothing uses or closes it. When the key is set, `http.close()` doesn't close an engine passed in from outside.
- **Suggested fix:** Make the parameter nullable, or create the engine inside `intervalsWellnessSync` only when a key exists.

### [Nit] `WellnessLatest` input differs from the spec
- **Where:** `frontend/src/app/features/wellness/wellness-latest/wellness-latest.ts:88`
- **Problem:** The spec says `input.required<WellnessLatestPair>()`. The code uses `WellnessTile[]`, with the tiles built in `wellness.view.ts`. The behaviour is the same and the logic is arguably cleaner. This is a spec-wording drift only.

## Privacy checklist
1. ✅ No secrets committed. `.env.example` adds `INTERVALS_ICU_API_KEY=` with no value and the exact comment. Fixtures use invented data.
2. ✅ The Intervals key lives only in `AppConfig`, which `toString` redacts, and in the `basicAuth` header. It isn't in any DTO, cookie or URL.
3. n/a. No OAuth. 401/403 map to `KeyRejected`.
4. n/a
5. ✅ No new Strava scopes.
6. ✅ Only `fields=id,restingHR,hrv` is requested. Only the date, the two values and `fetched_at` are stored.
7. n/a
8. ✅ Client, sync and background logs carry status codes, counts and exception class names only. Log-capture tests cover this (IntervalsClientTest:122, WellnessSyncTest:127,146).
9. ✅ No `Logging` plugin in `IntervalsHttp.kt`.
10. ✅ Fixed `ErrorResponse` texts. Intervals bodies are never passed through.
11. ✅ `hasConnectedSession` is checked before the key (`WellnessRoutes.kt:17`).
12. ✅ Session cookie unchanged.
13. ✅ No `[innerHTML]`. The tooltip formatter joins dates and numbers only (`wellness.options.ts:83-86`).
14. n/a. No new state-changing endpoint.
15. ✅ The response contains only chart-ready DTO fields.
16. ✅ Intervals.icu is called only from the backend. Nothing comes from a CDN.
17. ✅ No Strava data is sent to Intervals.icu.
18. ❌ partial. There's no in-app way to delete wellness data; the spec accepts this and lists it as a follow-up. Rows are pruned at today − 90.
19. n/a
20. ✅ As stated in the spec.
21. ✅ `intervals/*.json` and `wellness-fixtures.ts` use invented values.

## Summary
The implementation follows the spec closely. The domain logic (baselines, flags, streaks, readiness note, fetch range) is pure and well tested, and the layering is clean. Privacy handling of the API key and the health values is careful. The most useful fix is to make the client return `Unavailable` for a non-JSON 200 body, as AC2.5 requires. After that, strengthen the AC1.4 and AC4.5 tests so they actually check that Strava sync state is unaffected.

---

## Resolution (by the implementing session)
No Blockers or Majors, so the reviewer was not re-run.

- **[Minor] Non-JSON 200 escapes the client:** fixed. `IntervalsClient` also catches `NoTransformationFoundException` and returns `Unavailable`. `R2 - 5xx, network error and bad JSON map to unavailable` now also covers a `text/html` 200 and an `HttpRequestTimeoutException`.
- **[Minor] AC1.4 and AC4.5 assertions incomplete:** fixed.
  - `R1 - job makes no Intervals calls without a key and the Strava sync is unchanged` runs one sync with and one without the key, and compares the `/api/sync` body and the Strava paths called.
  - `R4 - rejected key leaves the Strava sync and its status unchanged` does the same with Intervals returning 401.
  - `015 R4 - a throwing wellness step leaves Strava sync state untouched` (BackgroundSyncTest) uses a throwing `WellnessSource`. It checks that `reauthRequired` is false and that the stored runs and status are unchanged, and it compares `SyncState` with the same sync run without a wellness step.
- **[Minor] Duplicate `RATE_LIMIT_FALLBACK`:** fixed. The client's copy is removed, and `IntervalsClient` uses `WellnessPolicy.RATE_LIMIT_FALLBACK`.
- **[Nit] Intervals CIO engine always created:** fixed. `module(intervalsEngine: HttpClientEngine? = null)`. `intervalsWellnessSync` creates a CIO engine only when a key is set, and closes it with the client when the application stops.
- **[Nit] `WellnessLatest` input differs from spec:** the spec now names `WellnessTile[]`, built by `latestTiles(data)`. The code is unchanged.
- Coverage notes not raised as findings: AC8.2's "session not matching the stored tokens" now has its own test, `R8 - wellness requires a session matching the stored tokens`. AC10.2 (360 px) stays a manual check. The SCSS stacks the tiles below 768 px, and ECharts wraps a plain legend.
- **Merge with `main` (011, 013):** 011 also places its race-day form card directly under training load. The user chose the order Training load → Race-day form → Resting HR & HRV → VO2max. AC9.1 in `specs.md` and the page specs are updated to match. 011's "directly before VO2max" order test now lists the wellness card in between.
