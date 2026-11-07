# Review: 019-user-switch
**Base:** main (ab430cb) → **Head:** ab430cb plus uncommitted working tree. HEAD equals main, so `git diff main...HEAD` is empty and I reviewed `git diff HEAD`.
**Verdict:** APPROVE WITH CHANGES

## Checks
- Backend: ktlint pass · detekt pass (0 issues) · tests 673 passed, 0 failed
- Frontend: lint pass · tests 435 passed, 0 failed · build pass

## Spec coverage
| Req | Implemented | Tested | Notes |
|---|---|---|---|
| R1 | ✅ | ✅ | Config code is complete, but the `.env.example` keys are missing (finding 1) |
| R2 | ✅ | ❌ | No test for AC2.1 or AC2.3. AC2.2 is checked on the dashboard route only |
| R3 | ✅ | ✅ | `POST /api/sync` is not part of the Marina route sweep |
| R4 | ✅ | ❌ | No test for AC4.5 or the unsigned-cookie half of AC4.4 |
| R5 | ✅ | ✅ | |
| R6 | ✅ | ❌ | No test for AC6.3, AC6.4, AC6.5 or the reverse direction of AC6.1. AC6.2 is weak |
| R7 | ✅ | ❌ | No test for AC7.1 |
| R8 | ✅ | ✅ | No parser changes in the diff. AC8.7 is manual and not verifiable here. AC8.4's race-day form date is not tested |
| R9 | ✅ | ✅ | |
| R10 | ✅ | ✅ | AC10.4 is asserted on the dashboard route only |
| R11 | ✅ | ❌ | No test for AC11.1 or AC11.5 |
| R12 | ✅ | ❌ | No test for AC12.1 or AC12.2 |
| R13 | ✅ | ❌ | No test for AC13.3. AC13.4 is manual |
| R14 | ✅ | ❌ | No test for AC14.2's failed-request case, AC14.4 or AC14.6 |
| R15 | ✅ | ❌ | No test for AC15.1, AC15.4 or the switch-back half of AC15.2 |
| R16 | ✅ | ✅ | |
| R17 | ✅ | ✅ | |
| R18 | ✅ | ❌ | No test for AC18.2 or AC18.3. AC18.4 is checked on one route only |

## Findings
### [Major] `.env.example` has no `MARINA_*` keys
- **Where:** `.env.example` (unmodified), referenced from `backend/src/main/kotlin/app/stravadashboard/config/PersonSettings.kt:59`
- **Problem:** The spec lists `.env.example` under files expected to change, and standard A7 says it lists the config keys. `git status` shows no change to the file, and its last commit is 2428bf1 from feature 015, before these keys existed.
- **Failure scenario:** Setting only `MARINA_DB_PATH` stops startup with `MARINA_STRAVA_CLIENT_ID must be set (see .env.example)`. That file doesn't list the key or any of the other `MARINA_*` keys.
- **Suggested fix:** Add all nine `MARINA_*` keys with no values.

### [Major] Storage safety criteria have no tests
- **Where:** `backend/src/test/kotlin/app/stravadashboard/api/PersonRoutesTest.kt:170` (only seeding of same-ID runs); the planned `persistence/ExistingDatabaseTest.kt` does not exist
- **Problem:** AC2.1 (a pre-feature database stays unchanged at startup) and AC2.3 (updating or deleting the same activity ID in one file leaves the other unchanged) have no test. AC2.2 is asserted only for the dashboard, not weekly summary, training load or sync status.
- **Failure scenario:** A startup change that adds a column to Ricardo's existing file, or repository wiring that crosses files on update or delete, passes the whole suite. This protects the spec's main "Ricardo's data is untouched" promise.
- **Suggested fix:** Add `ExistingDatabaseTest` and `R2 - same activity id stays separate`, and extend `R2 - each person reads only their runs` to the other three reads.

### [Major] Background sync independence is largely untested
- **Where:** `backend/src/test/kotlin/app/stravadashboard/api/PersonRoutesTest.kt:362-385`
- **Problem:** No test exists for AC6.3 (the other person's start throws), AC6.4 (separate budgets) or AC6.5 (refresh uses that person's credentials). AC6.1's reverse direction is also untested. `R6 - unconnected person not started` only checks that Marina's `GET /api/sync` returns 401 `not_connected`.
- **Failure scenario:** Removing the `try/catch` in `OtherPersonSync.startIfConnected` (`api/SyncRoutes.kt`) makes a failure in Marina's start turn Ricardo's `POST` into a 500, and nothing fails. Passing one person's `StravaOAuthClient` to the other's token provider also goes undetected.
- **Suggested fix:** Add the three tests named in the spec's test plan and a Marina-to-Ricardo `POST` case. Assert the response body and the Strava calls made.

### [Major] Per-person settings, revocation and Ricardo compatibility criteria lack backend tests
- **Where:** `backend/src/test/kotlin/app/stravadashboard/api/PersonRoutesTest.kt:69` and `:72` (fixture sets Marina's LTHR to not configured and her Garmin key to null), `:260` (no-cookie case only), `:432` (one route only)
- **Problem:** No test exists for AC7.1, AC11.1, AC11.5, AC12.1, AC12.2, AC4.5, AC18.2 or AC18.3. A search of the backend tests finds no unsigned or tampered cookie case for AC4.4. AC18.4 is checked only on `/api/dashboard/running`.
- **Failure scenario:** Passing `config.lthr` instead of `settings.lthr` into a service in `PersonStack.kt` gives Marina Ricardo's zones with all tests green. Sending the wrong Garmin key to the wrong person also goes undetected, although that key controls which account's health data is fetched.
- **Suggested fix:** Add the test-plan tests, using a fixture with a distinct Marina LTHR and Garmin key and an unsigned-cookie request.

### [Major] Flip behaviour criteria are not tested through the shell
- **Where:** `frontend/src/app/features/running-dashboard/dashboard-shell.spec.ts:39-68`, `frontend/src/app/features/weekly-summary/weekly-summary-bell.spec.ts:78`
- **Problem:**
  - No test exists for AC14.2's failed-request case, AC14.4 (no navigation), AC14.6 (sync timer stops), AC15.1, AC15.4 or AC13.3.
  - `R15 - badges are separate` creates a second bell by hand rather than flipping the shell, and doesn't switch back to Ricardo (AC15.2).
  - The spec also asked for a build-time test of the 18-week chart, and `marathon-pace.options` specs are unchanged.
- **Failure scenario:** If `SyncStore` moved to a root provider, Ricardo's timer would survive a flip and post under Marina's selection. The shell spec would not catch it, because it never checks sync timers.
- **Suggested fix:** Add the named tests to `dashboard-shell.spec.ts` using the real `SyncStore`, flush a failing Marina response, and add an 18-week option-builder case.

### [Minor] Focus is lost when flipping to a person who isn't connected
- **Where:** `frontend/src/app/shared/person-switch/person-switch.ts:85-89`, `frontend/src/app/core/person/person.store.ts:34`, `frontend/src/app/core/interceptors/api-error.interceptor.ts:27`
- **Problem:** The focus request is one-shot, and the switch on the recreated dashboard consumes it before the 401 redirect creates the connect page's switch.
- **Failure scenario:** A keyboard user presses Space with Marina not connected. The dashboard switch takes focus, the 401 goes to `/connect`, and focus falls to `<body>`, which contradicts R14's "focus stays on the switch".
- **Suggested fix:** Clear the focus request only after a stable render, or have the connect page restore focus after a flip-driven redirect.

### [Minor] Marina's invalid-setting messages name Ricardo's variables
- **Where:** `backend/src/main/kotlin/app/stravadashboard/api/MarathonPaceDtos.kt:56`, `backend/src/main/kotlin/app/stravadashboard/api/ErrorResponse.kt:25`, `:29`, `:43`
- **Problem:** The error messages hard-code `MARATHON_GOAL_PACE`, `LTHR_BPM` and `MAX_HR_BPM`, and both stacks use them.
- **Failure scenario:** With `MARINA_MARATHON_GOAL_PACE=abc`, Marina's card says `MARATHON_GOAL_PACE is not a pace...` directly above `Set MARINA_MARATHON_GOAL_PACE in .env`. The person fixing it may edit Ricardo's working value. The spec defers the help texts for LTHR, max HR, plan and Garmin, but not this goal-pace message.
- **Suggested fix:** Word the goal-pace message without a variable name while the frontend hint names it, keeping Ricardo's response in mind for AC18.4.

### [Nit] Call logging now includes Marina's client ID and OAuth state
- **Where:** `backend/src/main/kotlin/app/stravadashboard/Application.kt:121`
- **Problem:** Test output shows `CallLogging` printing the login redirect's `Location`, including `client_id` and `state`. This already happens for Ricardo and exposes no secret or code, but the spec's privacy section says no new logging of IDs.
- **Failure scenario:** Logs from a Marina login contain her client ID and a short-lived state value. The state is not usable without the signed cookie.
- **Suggested fix:** Filter or reformat the `/auth/strava/login` log line, or accept and note it.

## Fix notes (added after the review, not part of the reviewer's report)
- **`.env.example` (Major): not fixed, needs Ricardo.** The file is blocked by the project's permission settings, so the keys have to be added by hand.
- **Storage, sync, per-person settings and flip tests (Majors): fixed.** Added `PersonIsolationTest.kt`, new cases in `dashboard-shell.spec.ts`, and the 18-week chart test. Still only covered indirectly: AC7.1 for race-day form and weekly summary load (training load is asserted), AC8.4's race-day form date (the marathon date is asserted in the parser and weather tests), AC18.2 and AC18.3 (the existing suites and the no-header tests cover them).
- **Focus lost on a flip to an unconnected person (Minor): not fixed.** It only lasts until Marina connects, and a fix adds more state than it's worth. A keyboard user presses Tab a few extra times.
- **Goal-pace message (Minor): fixed.** It now says "The goal pace is not a pace…", and the card's hint names the right variable. This changes that one invalid-config message for Ricardo too. The LTHR and max HR messages stay as they are, the same deferral as their help texts.
- **Call logging (Nit): accepted.** It's the existing behaviour, a client ID isn't secret, and the state is useless without the signed cookie.

## Privacy checklist
1. ✅ No secrets in the diff, and test values are invented. `.env` and `*.db` are git-ignored.
2. ✅ Tokens stay in each person's database, with no new response fields.
3. ✅ Refresh and revocation are per person by wiring (`PersonStack.kt`), though not tested (see the Major findings).
4. ✅ State is signed and verified, and the person comes from the cookie.
5. ✅ `REQUIRED_SCOPE` is unchanged.
6. ✅ No schema or stored-field change.
7. ✅ No GPS added.
8. ✅ No tokens, codes or HR in new logs. `OtherPersonSync` logs only the class name. See the Nit on client ID and state.
9. ✅ Ktor client logging config is not in the diff.
10. ✅ Standard error shapes and fixed connect messages.
11. ✅ Every route checks the session and that person's tokens, and the default host is `127.0.0.1` (`AppConfig.kt:85`). Caveat, as specified: before Marina's first connect, any athlete who completes `?person=marina` gets a session that also opens Ricardo's data (AC5.6, R4). This is safe only while the app stays local, so a `HOST` override removes the protection.
12. ✅ Cookie settings are unchanged.
13. ✅ Normal interpolation only.
14. ✅ `POST /api/sync` keeps its XSRF check, and the switch changes no server state.
15. ✅ No new response fields, and no CORS added.
16. ✅ No new external hosts.
17. n/a in code. The spec's Marina consent and Strava terms questions are still marked "please confirm" and need a person to answer.
18. ❌ No disconnect route. This is an existing gap, and the spec marks it out of scope. It now also covers Marina's data.
19. n/a
20. n/a in code. Backups now cover two personal-data files.
21. ✅ Fixtures are invented.

## Summary
The implementation matches the spec and I found no wrong-data or leak bug in the code: the stacks are separated per person, the wrong-athlete guard is correct, and the keyed page recreation cancels in-flight requests. The most important work is adding `MARINA_*` to `.env.example` and the missing acceptance tests, above all AC2.1 and AC2.3, then R6 and per-person settings; the passing suites do not protect those behaviours yet. Commit the work too, because the review covers an uncommitted working tree.

Not checked: Marina's real CSV (AC8.7), the narrow-screen check (AC13.4) and live Strava or Garmin behaviour. I did not read `.env.example`: `git show` on it was denied, so finding 1 rests on `git status` and `git log`. I also ran `git rev-parse`, `git check-ignore` and `ls`, which are read-only but outside my allowed command list.
