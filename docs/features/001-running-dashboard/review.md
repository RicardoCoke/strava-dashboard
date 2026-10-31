# Review: 001-running-dashboard
**Base:** d009a34 → **Head:** d009a34 (uncommitted working tree, branch feature/001-running-dashboard)
**Verdict:** APPROVE WITH CHANGES

## Checks
- Backend: ktlint not run · detekt not run · tests not run. `./gradlew` stopped straight away with "JAVA_HOME is not set and no 'java' command could be found in your PATH".
- Frontend: lint not run · tests not run · build not run. `npm: command not found` in the reviewer's shell.
- I reviewed the code by reading it only. Run both check suites in a shell that has Java 21 and Node before merging.
- `.env.example` is blocked by my permission settings, so I could not read it. `.gitignore` does list `.env` and `*.db`.

## Spec coverage
| Req | Implemented | Tested | Notes |
|---|---|---|---|
| R1 | ✅ | ✅ | app.spec.ts covers AC1.1–AC1.3 |
| R2 | ✅ | ✅ | The AC2.11 contract test only checks top-level and first-level keys (Minor 2) |
| R3 | ✅ | ✅ | AC3.4 log test runs at client level, not through a full SyncService run |
| R4 | ✅ | ✅ | Concurrency edge case, see Minor 1 |
| R5 | ✅ | ⚠ | No test for a timeout (AC5.3), see Minor 3 |
| R6 | ✅ | ✅ | |
| R7 | ✅ | ✅ | |
| R8 | ✅ | ✅ | |
| R9 | ✅ | ✅ | |
| R10 | ✅ | ✅ | |
| R11 | ✅ | ✅ | |
| R12 | ✅ | ✅ | |
| R13 | ✅ | ✅ | |

## Findings
### [Major] Malformed Strava JSON is not caught: it becomes a 500, and the logged exception may contain part of the response body
- **Where:** `backend/src/main/kotlin/app/stravadashboard/strava/StravaHttp.kt:57`. Also reached from `strava/StravaOAuthClient.kt:55-58` and `Application.kt:65-68`.
- **Problem:** `stravaCall` catches `SerializationException` to turn bad JSON into `Unavailable`. But decoding goes through Ktor's client `ContentNegotiation`. Ktor's `KotlinxSerializationConverter` wraps decode errors in `JsonConvertException` (a `ContentConvertException`, which is not a `SerializationException`). So this catch never fires for `it.body()`. The exception goes up to `StatusPages`, which logs `cause` with its message. kotlinx-serialization messages include a "JSON input: …" excerpt of the raw body.
- **Failure scenario:** Strava returns 200 with a body that doesn't match the DTOs, for example a lap without `elapsed_time`, `expires_at` as a string, or truncated JSON.
  - During a dashboard sync, the user gets `500 internal_error` instead of `200` with `sync.status = "stravaUnavailable"`. That breaks the R5 / AC5.3 intent ("bad JSON is Unavailable", as the doc comment on line 34 says).
  - On `/auth/strava/callback`, the user gets a 500 instead of `302 /connect?error=token_exchange_failed`.
  - Either way, the logged excerpt can include activity names or descriptions, or `access_token` / `refresh_token` from the token response. That breaks privacy item 8.
- **Suggested fix:** Catch `io.ktor.serialization.ContentConvertException` (or `JsonConvertException`) in `stravaCall` and log only the class name, as `unavailable()` already does. Add MockEngine tests for a 200 response with a broken body on the list call and on the token call.

### [Major] A new athlete connecting after a revoke inherits the previous athlete's runs and sync cursor
- **Where:** `backend/src/main/kotlin/app/stravadashboard/auth/ConnectService.kt:33-37`
- **Problem:** The wipe only happens when `tokens.find()` returns a row with a different athlete id. AC3.3 / `StravaTokenProvider.revoke()` deletes the tokens but keeps the runs and `sync_state`. After a revoke there is no stored athlete id, so `previous == null` and nothing is wiped.
- **Failure scenario:**
  1. Athlete A's access is revoked, so the tokens are deleted and A's runs stay in the DB.
  2. Athlete B connects. The wipe is skipped.
  3. B's dashboard shows A's runs and heart-rate data (health data, GDPR Art. 9).
  4. `list_cursor` is still set from A's import. `listAfter` uses `min(cursor, now − 7d)`, so B's one-time 5-month import (AC4.1) never runs. B only sees runs newer than A's newest run, minus 7 days.
- **Suggested fix:** Persist the owning athlete id somewhere that survives a token delete, for example a column in `sync_state` or keeping the athlete id when the tokens are deleted. Wipe runs and sync state whenever the connecting athlete differs from it. Add a test: revoke, then a different athlete connects, then the data is wiped and the next list call uses the backfill `after`.

### [Minor] Concurrent requests can run two syncs back to back while a sync is partial or Strava is down
- **Where:** `backend/src/main/kotlin/app/stravadashboard/domain/SyncService.kt:54-64`, `backend/src/main/kotlin/app/stravadashboard/domain/SyncPolicy.kt:38-39`
- **Problem:** The second request waits on the mutex, then checks `isDue` again.
  - After a `partial` sync, `pending > 0`, so it is still due.
  - After a `stravaUnavailable` sync, `lastSuccessfulSyncAt` is unchanged and stale, so it is still due.
- **Failure scenario:**
  - During the first import, two tabs load at once. That gives two 80-call syncs back to back, about 160 activity calls, which goes over Strava's 100 per 15 minutes and is likely to get a 429.
  - During an outage, the second response waits for a second round of timeouts (up to about 15 s more).
  - AC4.13 says "only one sync runs, and both responses are built after it finishes". The existing test only covers the case where nothing is pending.
- **Suggested fix:** Record when the last sync attempt finished. Skip a new sync if that was moments ago, or let waiters reuse the result of the in-flight sync.

### [Minor] The AC2.11 contract test doesn't check the nested JSON shape
- **Where:** `backend/src/test/kotlin/app/stravadashboard/api/RoutesTest.kt:228-264`
- **Problem:** The test checks the root keys, the `sync` keys, the week keys and the first-level type keys. It never checks the keys of `points[]`, `trend`, `current`/`previous`, `currentWindow`/`previousWindow`, or the top-level `heartRateTrend` keys. It also doesn't check that nullable fields come back as `null` rather than missing.
- **Failure scenario:** Someone renames `HrPointDto.averageHeartRateBpm` or adds a field to `PaceWindowStatDto`. Every test still passes, but the frontend chart breaks because it no longer matches `running-dashboard.models.ts`.
- **Suggested fix:** Compare the whole response against a golden JSON file, or assert the key sets of every nested object, using a fixture that has a trend and both pace windows.

### [Minor] AC5.3 timeout path is untested
- **Where:** `backend/src/test/kotlin/app/stravadashboard/strava/StravaClientTest.kt:232-241`
- **Problem:** Only a plain `IOException` and a 502 are tested. Nothing exercises `HttpRequestTimeoutException` or `ConnectTimeoutException`.
- **Failure scenario:** A future Ktor change makes the timeout exceptions stop extending `IOException`. Timeouts would then surface as a 500, and no test would catch it.
- **Suggested fix:** Add a MockEngine handler that throws `HttpRequestTimeoutException` and assert the result is `SourceResult.Unavailable`.

### [Minor] `run_stream_paces.status` values don't match the spec schema
- **Where:** `backend/src/main/kotlin/app/stravadashboard/persistence/ExposedRunRepository.kt:74`
- **Problem:** The code stores `result.status.name`, which is `"COMPUTED"` / `"UNAVAILABLE"`. The spec schema (Persistence table, AC4.7) says `computed | unavailable`.
- **Failure scenario:** A manual query or a later migration that follows the spec (for example `WHERE status = 'unavailable'`) matches nothing.
- **Suggested fix:** Map the enum to the lowercase spec values when writing and reading, or update the spec.

### [Nit] HR points are sorted by UTC start, not by local date
- **Where:** `backend/src/main/kotlin/app/stravadashboard/domain/HeartRateTrend.kt:70`
- **Problem:** AC9.1 says points are "sorted by date ascending", and `date` is the local date. Sorting by `startUtc` can order two runs from different time zones differently from their local dates. Sort by `startLocal`.

### [Nit] A detail 404 deletes the run, which the spec doesn't describe
- **Where:** `backend/src/main/kotlin/app/stravadashboard/domain/SyncService.kt:136-143`
- **Problem:** The spec's edge-case table says "Run deleted on Strava: not detected". The code deletes the run when the detail call returns 404. The behaviour is reasonable, but the spec should be updated to match.

### [Nit] The "drawn" pace-type filter is written twice
- **Where:** `frontend/src/app/features/running-dashboard/chart-options/workout-pace.options.ts:17` and `frontend/src/app/features/running-dashboard/workout-pace-comparison-chart/workout-pace-comparison-chart.ts:62-79`
- **Problem:** The same `hasEnoughData && current && previous` condition is written in both places, so they could drift apart. Pull it into one shared helper.

## Privacy checklist
1. ✅ No secrets in the diff. Fixtures and `testConfig` use dummy values. `.gitignore` has `.env` and `*.db`. I could not read `.env.example` (blocked by permissions).
2. ✅ (partial, as the spec says) Tokens live only in SQLite, stored unencrypted. `UserSession` holds only the athlete id. Token DTOs and domain types override `toString`.
3. ✅ Refresh happens before expiry and once on a 401. Revoked tokens are deleted. The data left behind after a revoke is covered in Major 2.
4. ✅ `state` is 32 random bytes in base64url, kept in the signed HttpOnly cookie, and compared in constant time.
5. ✅ Only `activity:read_all` is requested, and the callback checks for it.
6. ✅ Only the spec'd fields are declared and stored. Non-Runs are not stored. Streams are reduced to one pace per run.
7. ✅ Only `keys=time,velocity_smooth` is requested. No GPS fields are declared.
8. ⚠ Normal paths log only class names and counts, and CallLogging logs the path only. Malformed-JSON exceptions can log a body excerpt (see Major 1).
9. ✅ The Ktor client `Logging` plugin is not installed.
10. ✅ One error shape is used. `StatusPages` hides stack traces from responses.
11. ✅ The server binds to `127.0.0.1` by default. `/api/dashboard/running` checks that the session matches the stored athlete.
12. ✅ `sd_session` is HttpOnly, SameSite=Lax, Secure on https, and HMAC-signed with `SESSION_SECRET`.
13. ✅ No `[innerHTML]` or `bypassSecurityTrust*`. Tooltip formatters only see numbers, dates and generated labels. The connect page maps codes to fixed texts using `Object.hasOwn`.
14. n/a No state-changing endpoints. XSRF config is present.
15. ✅ DTOs carry only chart fields. No CORS is configured.
16. ✅ ECharts is bundled from npm and lazy-loaded. No CDN or analytics.
17. ✅ "Powered by Strava" footer.
18. ❌ Deferred by the spec: there is no disconnect / delete-data action.
19. n/a No webhook.
20. ✅ Documented in the spec.
21. ✅ Fixtures are invented ("Invented tempo", ids 111/222, coordinates 0,0).

## Summary
The implementation is thorough and follows the spec closely. The domain logic is pure and well tested, the layering is respected, and tokens never reach the browser. Two things need fixing before merge:
- Catch Ktor's `ContentConvertException` in `stravaCall`, so malformed Strava responses degrade to `stravaUnavailable` / `token_exchange_failed` and never log body excerpts.
- Wipe a previous athlete's runs and sync cursor even when their tokens were already deleted by a revoke.

None of the checks ran in my shell (no Java or npm on PATH). Run both check suites before merging.

## Resolution
Checks after the fixes (run with Java 21 and Node on PATH): backend `ktlintCheck detekt test` pass (92 tests); frontend `lint`, `test` (31 tests) and `build` pass.

| Finding | Outcome |
|---|---|
| [Major] Malformed Strava JSON | Fixed. `stravaCall` also catches `ContentConvertException` and logs only the class name. Tests: `R5 - malformed JSON maps to unavailable and logs no body`, `R3 - malformed token response is a failed refresh, not a crash`, `R2 - malformed token response redirects with token_exchange_failed`. |
| [Major] New athlete after a revoke inherits data | Fixed. `sync_state.owner_athlete_id` records whose data is stored and survives a token delete; `ConnectService` wipes runs and sync state whenever the connecting athlete differs. Spec AC2.9 and schema updated. Test: `R2 - a different athlete connecting after a revoke gets a clean store`. |
| [Minor] Back-to-back syncs for concurrent requests | Fixed. A request that waited on the mutex while another sync ran reuses that outcome. Test: `R4 - concurrent requests during a partial import share one sync`. |
| [Minor] Contract test misses nested shape | Fixed. `RunningDashboardContractTest` compares the full JSON (nested objects and explicit nulls) with the spec's example. |
| [Minor] Timeout untested | Fixed. Test: `R5 - timeout maps to unavailable` (`HttpRequestTimeoutException`). |
| [Minor] `run_stream_paces.status` casing | Fixed. Stored as `computed` / `unavailable`, as in the spec. |
| [Nit] HR points sorted by UTC | Fixed. Sorted by `startLocal`. |
| [Nit] Detail 404 deletes the run | Kept the behaviour; spec edge-case table updated to describe it. |
| [Nit] Duplicated "drawn" filter | Fixed. `comparablePaceTypes()` in `workout-pace.options.ts` is used by both the option builder and the card. |

No Blockers, so the reviewer was not re-run.
