# Review: 005-training-load
**Base:** main (d058bf5) → **Head:** d058bf5 plus uncommitted working tree (all 72 changed files are uncommitted on `feature/005-training-load`, so I reviewed `git diff main` of the working tree)
**Verdict:** APPROVE WITH CHANGES

## Checks
- Backend: ktlint pass · detekt pass (0 issues) · tests 199 passed, 0 failed
- Frontend: lint pass · tests 152 passed, 0 failed · build pass

## Spec coverage
| Req | Implemented | Tested | Notes |
|---|---|---|---|
| R1 | ✅ | ✅ | AC1.1–AC1.4 tested in `AppConfigTest`. AC1.5 not verified: reading `.env.example` was denied by permissions |
| R2 | ✅ | ✅ | AC2.1–AC2.5 covered (`SyncServiceTest`, `ExposedRunRepositoryTest` migration test). The spec's `SyncPolicyTest.kt` wasn't created; its cases are covered in `SyncServiceTest` |
| R3 | ✅ | ✅ | AC3.4 is tested as 50 details + 30 HR calls, then 20 HR calls, not the spec's "100 HR-only" case, but the cap and newest-first order are checked. The AC3.11 log check doesn't include the HR stream (Minor) |
| R4 | ✅ | ✅ | |
| R5 | ✅ | ✅ | |
| R6 | ✅ | ✅ | |
| R7 | ✅ | ❌ | AC7.6 change labels have no test (Major) |
| R8 | ✅ | ✅ | |
| R9 | ✅ | ❌ | AC9.5 concurrent-sync reuse and the AC9.6 rate-limited/unavailable → 200 path have no test (Major) |
| R10 | ✅ | ✅ | |
| R11 | ✅ | ✅ | |
| R12 | ✅ | ✅ | AC12.4 styles exist in `ramp-warning.ts` but have no test (manual check allowed by the test plan) |
| R13 | ✅ | ✅ | |
| R14 | ✅ | ❌ | The palette test exists. `R14 - card uses theme tokens` is missing (Minor) |
| R15 | ✅ | ❌ | AC15.1 confirmed: the listed files are unchanged and `REQUIRED_SCOPE` is still `activity:read_all`. AC15.2 stub is in place. AC15.3 test is missing (Major) |

## Findings
### [Major] AC7.6 change labels are untested
- **Where:** `backend/src/main/kotlin/app/stravadashboard/api/TrainingLoadMappers.kt:187-190` (`changeLabel`, `signed`). `backend/src/test/kotlin/app/stravadashboard/api/TrainingLoadRoutesTest.kt:226-233` only checks the ramp keys.
- **Problem:** No test checks `"+14% vs last week"`, `"+14% vs the week before"`, `"-5% vs last week"`, `"0% vs last week"` or `changeLabel = null` when `changePercent` is null. The frontend fixtures hard-code the label, so nothing exercises the backend mapper.
- **Failure scenario:** Someone swaps the two `comparedWith` strings at line 150, or changes `signed()` so 0 gives `"+0%"`. Every test still passes, and the UI shows the wrong callout text.
- **Suggested fix:** Add `R7 - change labels`: unit-test `toDto()` with changePercent 14, -5, 0 and null for both weeks.

### [Major] AC9.5 / AC9.6 sync behaviour of the training-load route is untested
- **Where:** `backend/src/test/kotlin/app/stravadashboard/api/TrainingLoadRoutesTest.kt` (no `R9 - reuses concurrent sync` test). `backend/src/main/kotlin/app/stravadashboard/domain/trainingload/TrainingLoadService.kt:65-75`.
- **Problem:** The code looks right: the same `SyncService` instance is wired in `Application.kt:93`, and every `SyncOutcome.Done` maps to `Ready`. But nothing tests that `/api/training-load` and `/api/dashboard/running` running together start only one sync. Nothing tests that a rate-limited or unavailable sync still returns 200 from stored data.
- **Failure scenario:** Someone wires a second `SyncService` for the load service, or maps a `RATE_LIMITED` outcome to an error. The two requests on the page then run two syncs and double the Strava calls, or the card errors while Strava is rate-limited. No test fails.
- **Suggested fix:** Add `R9 - reuses concurrent sync`: two concurrent requests, assert one list call. Add a 429 case that asserts a 200 body.

### [Major] AC15.3 regression test is missing
- **Where:** No test named `R15 - 001 aggregates unchanged with older runs stored` exists in `backend/src/test`.
- **Problem:** R2 now keeps runs back to `today − 6 months − 41 days`. I checked that `WeeklyDistance`, `HeartRateTrend.kt:52` and `WorkoutPaceComparison.compute` all filter by their own windows, so the outputs are currently unchanged. No test locks this in.
- **Failure scenario:** A later change to an 001 aggregate that relies on "everything stored is in range" would silently start including the 41 warm-up days (for example, 5-month HR trend points from month 6). No test fails.
- **Suggested fix:** Seed runs older than 5 months, compute the 001 aggregates, and assert they equal the result without those runs.

### [Minor] R14 theme-token test is missing
- **Where:** `frontend/src/app/features/training-load/` (no `R14 - card uses theme tokens` spec). Styles are in `training-load.scss` and `ramp-warning/ramp-warning.ts:14-24`.
- **Problem:** The test plan names this test, but only `palette.spec.ts` checks colours. The SCSS uses `var(--color-*)` tokens throughout, so nothing is wrong today. It is just unguarded.
- **Failure scenario:** A hard-coded hex colour added to the card styles passes every check.
- **Suggested fix:** Add the named test, for example assert the section has class `card` and the callout's computed style uses the token, or note in `review.md` that it is a manual check.

### [Minor] AC3.11 log check doesn't cover the heart-rate stream call
- **Where:** `backend/src/test/kotlin/app/stravadashboard/strava/StravaClientTest.kt:346-365`
- **Problem:** The log-privacy test only calls `listActivities`, `activityDetail` and `speedStream`, never `heartRateStream`. No current code logs bpm values; I checked `SyncService.kt:93,100`. But HR is health data (privacy item 8) and is not covered.
- **Failure scenario:** A later debug log of the decoded `HeartRateStream` or histogram would pass all tests.
- **Suggested fix:** Add `src.heartRateStream(ActivityId(111))` with `streams-heartrate.json` to the test, and assert none of the fixture bpm values appear in the logs.

### [Nit] Histogram status is stored lowercase, but the spec says `COMPUTED` / `UNAVAILABLE`
- **Where:** `backend/src/main/kotlin/app/stravadashboard/persistence/RunRowMappers.kt:40` (`name.lowercase()`)
- **Problem:** The spec's Persistence section says the `status` text is `COMPUTED` / `UNAVAILABLE`. This matches the existing stream-pace convention and round-trips correctly, so it only matters for anyone reading the DB by hand. Align the spec or the code.

### [Nit] Daily loads computed twice per request
- **Where:** `backend/src/main/kotlin/app/stravadashboard/domain/trainingload/TrainingLoadService.kt:71-72`
- **Problem:** `TrainingLoadCalculator.compute` already builds `dailyLoads` internally, and the service calls `dailyLoads(...)` again for `RampRate`. It is cheap but duplicated. Consider exposing the map from `TrainingLoad`, or computing it once and passing it to both.

### [Nit] Package cycle between config and domain
- **Where:** `backend/src/main/kotlin/app/stravadashboard/config/LthrSetting.kt:3` imports `domain.model.Bpm`. `backend/src/main/kotlin/app/stravadashboard/domain/trainingload/TrainingLoadService.kt:3` imports `config.LthrSetting`.
- **Problem:** The spec prescribes this design, but it makes `domain` depend on `config`. A small domain-side type (or passing `Bpm?` plus a validity flag) would keep dependencies pointing inward.

### [Nit] `formatShortDate` test is in `numbers.spec.ts` and doesn't vary the time zone
- **Where:** `frontend/src/app/shared/format/numbers.spec.ts:30-33`
- **Problem:** The test named "ignores browser zone" runs only in the machine's zone, and it sits in the numbers spec rather than a dates spec. The UTC-based code (`dates.ts:16-19`) is correct.

## Privacy checklist
1. ✅ No secrets in the diff. Fixtures `streams-heartrate*.json` hold invented values. `.env.example` could not be read (permission denied), so the AC1.5 wording is unverified.
2. ✅ Token handling unchanged. The response has no tokens.
3. ✅ HR stream calls go through the existing `authorizedGet` refresh/revoke flow (`StravaClient.kt:1272-1278`). The refresh-once case is tested.
4. n/a (OAuth flow unchanged)
5. ✅ No new scope. `REQUIRED_SCOPE = "activity:read_all"` unchanged.
6. ✅ Only the histogram is stored (`RunHrHistogramsTable`). The raw stream is reduced in memory (`SyncService.kt:500-513`).
7. ✅ `keys=time,heartrate` only (`StravaClient.kt:1272`), tested. No `latlng`.
8. ✅ No new log lines with bpm, histograms, LTHR or loads. `AppConfig.toString` and `LthrSetting.Valid.toString` mask the value. The HR log test is missing (see Minor).
9. ✅ Client logging unchanged.
10. ✅ Fixed error texts. The 422 message has no value (tested).
11. ✅ The route checks `hasConnectedSession` first (`TrainingLoadRoutes.kt:213`). The 401 is tested.
12. n/a (session cookie unchanged)
13. ✅ Plain interpolation only. The tooltip formatter joins dates and numbers with `<br/>` (`training-load.options.ts:53-56`). No Strava text.
14. n/a (GET only)
15. ✅ The response has only chart fields. Exact key sets are tested.
16. ✅ No new dependencies or external calls.
17. ✅ No third-party sharing.
18. ❌ Deferred, as in the spec (no disconnect/delete action). Not a finding for this feature.
19. n/a
20. ⚠ The DB now holds HR histograms (health data). Backups need the same care, as the spec notes.
21. ✅ Test data is invented.

## Summary
The implementation follows the spec closely. The domain maths (histogram, zones, TRIMP, rolling averages, ramp rate), the migration, the sync phase and the frontend states are all correct, and all backend and frontend checks pass. The gaps are tests: the AC7.6 labels, the AC9.5/AC9.6 sync behaviour of the new route, and the AC15.3 regression guard have none. Add those three before merging.

---

## Resolution (after review)

No Blockers, so the reviewer was not re-run. Backend checks: ktlint, detekt and 203 tests pass. Frontend checks: lint, 153 tests and the build pass.

| Finding | Outcome |
|---|---|
| [Major] AC7.6 change labels untested | **Fixed.** `api/TrainingLoadMappersTest` › `R7 - change labels` covers +14, -5, 0 and null for both weeks. |
| [Major] AC9.5 / AC9.6 untested | **Fixed.** `TrainingLoadRoutesTest` › `R9 - reuses concurrent sync` (training load and dashboard requests at the same time give one list call) and `R9 - rate limited sync still serves stored data` (Strava 429 → 200 with the stored run counted). |
| [Major] AC15.3 regression test missing | **Fixed.** `domain/OlderRunsRegressionTest` › `R15 - 001 aggregates unchanged with older runs stored`: the weekly distance, HR trend and pace comparison results are identical with and without runs from the warm-up period. |
| [Minor] R14 theme-token test missing | **Not fixed; checked manually.** jsdom doesn't apply component SCSS, so a computed-style test would assert nothing real. The test plan allows a manual check for AC12.4 and AC14.1–AC14.4. Every colour in `training-load.scss` and `ramp-warning.ts` is a `var(--color-*)` token. The 375 px / desktop layout still needs a check in a real browser. |
| [Minor] AC3.11 log check misses the HR stream | **Fixed.** The log-privacy test in `StravaClientTest` now also calls `heartRateStream` with `streams-heartrate.json` and asserts the bpm values don't appear in the logs. |
| [Nit] Status stored lowercase | **Spec aligned** to the code: lowercase, matching the existing `run_stream_paces` convention. |
| [Nit] Daily loads computed twice | **Fixed.** `TrainingLoadService` builds the daily loads once and passes them to `TrainingLoadCalculator.compute(runs, loads, today)` and `RampRate.compute`. |
| [Nit] config ↔ domain package cycle | **Not fixed.** The spec prescribes `TrainingLoadService(lthr: LthrSetting, …)`. The dependency is on a tiny sealed config type with no framework imports, so the layering rule (domain must not import Ktor, Exposed or Strava) still holds. It can be revisited if `config` grows. |
| [Nit] `formatShortDate` test location / zone | **Fixed.** Moved to a new `shared/format/dates.spec.ts`, renamed honestly, plus a midnight-edge case (`2026-11-01` stays "Sun 1 Nov"). The runner can't switch the time zone per test. |

Spec deviation noted during the build: the AC9.4 / 422 message was changed from "LTHR_BPM must be a whole number…" to "LTHR_BPM is not a whole number between 100 and 220.", because AC12.5 bans the word "must" in every rendered state of the card, including the invalid-config state that shows this message.
