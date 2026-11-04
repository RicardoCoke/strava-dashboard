# Review: 018-run-weather-forecast
**Base:** main → **Head:** d45ab7e (plus uncommitted working tree, including untracked files)
**Verdict:** APPROVE WITH CHANGES

> The report below is verbatim, except that the home coordinates and city name are replaced by `[home coordinates]` and `[home city]`, because this repository is public. Resolutions are at the end.

## Checks
- Backend: ktlint pass · detekt pass (0 issues) · tests 633 passed, 0 failed. Gradle restored these from up-to-date or build cache state, so nothing was re-executed. The result files include the new weather suites.
- Frontend: lint pass · tests 411 passed, 0 failed · build pass

## Spec coverage
| Req | Implemented | Tested | Notes |
|---|---|---|---|
| R1 | ✅ | ✅ | `.env.example` is not in the change set (see Minor finding) |
| R2 | ✅ | ✅ | AC2.3: no test throws a timeout or unresolved-address failure (see Minor finding) |
| R3 | ✅ | ✅ | |
| R4 | ✅ | ❌ | AC4.4 has no test |
| R5 | ✅ | ✅ | AC5.7 relies on the existing suites, which pass |
| R6 | ✅ | ✅ | |
| R7 | ✅ | ✅ | |
| R8 | ✅ | ❌ | AC8.4 has no test; the AC8.5 test cannot fail |
| R9 | ✅ | ✅ | |
| R10 | ✅ | ✅ | No persistence or scope change in the diff |

## Findings
### [Major] AC4.4 has no test (today after the window has passed)
- **Where:** `backend/src/test/kotlin/app/stravadashboard/domain/weather/RunWindowDaysTest.kt:66`
- **Problem:** The test plan requires `R4 - today stays after the window has passed`, and no such test exists. The route and service tests fix the clock at 10:00Z (`RunWeatherRoutesTest.kt:31`, `RunWeatherServiceTest.kt:44`), so no test runs with the current time after 11:00.
- **Failure scenario:** A change that drops today once the window has passed, for example a clock-based filter in `RunWeatherService`, removes today's icon every afternoon and all tests still pass. The current code is correct, because it filters by date only.
- **Suggested fix:** Add a `RunWeatherServiceTest` case with the clock at 15:00 that asserts today's entry is returned.

### [Major] AC8.4 has no test (401 on the weather request)
- **Where:** `frontend/src/app/features/calendar-like-build/build-calendar-section/build-calendar-section.spec.ts:41`
- **Problem:** The test plan requires `R8 - 401 adds nothing to the section`, and the spec file only flushes 503, `{}` and `{ days: [] }`. The interceptor's own spec (`api-error.interceptor.spec.ts:32`) does not cover this section.
- **Failure scenario:** If the `catchError` at `build-calendar-section.ts` stopped covering 401, or a 401 began rendering a message in the card, no test would fail. The current code is correct: `catchError` returns `[]` for every error, and `api-error.interceptor.ts:23` handles the redirect.
- **Suggested fix:** Add a test that flushes a 401 and asserts the tiles remain, with no `.weather` and no `.weather-credit`. Include the interceptor in that test to assert the navigation to `/connect`.

### [Major] The AC8.5 scroll test cannot fail
- **Where:** `frontend/src/app/features/calendar-like-build/plan-sections.spec.ts:225`
- **Problem:** The frontend tests have jsdom as their DOM dependency (`frontend/package.json:33`), and jsdom does no layout, so `row.offsetTop` is 0. The test never sets a non-zero scroll position, so `before` and the final `scrollTop` are both 0 whatever the component does. The scroller element is also outside the `@for`, so it is never recreated.
- **Failure scenario:** A regression that re-runs the initial scroll whenever `weather` changes moves the user back to the current week in a real browser, yet the test stays green. The current code is correct: `afterNextRender` at `build-calendar.ts:124` runs once, and weeks and days are tracked by stable keys.
- **Suggested fix:** Set `scroller.scrollTop` to a non-zero value before the weather input arrives and assert it is unchanged. Stub `offsetTop` to non-zero so a re-scroll would change the value.

### [Minor] Real home-city coordinates in tests and fixtures (privacy item 21)
- **Where:** `backend/src/test/resources/open-meteo/forecast.json:2`, `backend/src/test/kotlin/app/stravadashboard/weather/OpenMeteoClientTest.kt:27`, `backend/src/test/kotlin/app/stravadashboard/domain/weather/RunWeatherServiceTest.kt:19`, `backend/src/test/kotlin/app/stravadashboard/api/RunWeatherRoutesTest.kt:42`
- **Problem:** Item 21 forbids real coordinates in fixtures. These files use `[home coordinates]`, the [home city] values the spec says belong in the git-ignored `.env`. `AppConfigTest.kt:28` also uses them, as AC1.1 prescribes. `specs.md` marks item 21 as passing, and the same values appear in `specs.md` itself.
- **Failure scenario:** Committing this publishes the owner's home city in the test code, which defeats keeping it in `.env`.
- **Suggested fix:** Use clearly invented coordinates in the fixture and tests, as `AppConfigTest` already does with `41.77`. Decide separately whether `specs.md` should keep the real values.

### [Minor] `.env.example` is not in the change set
- **Where:** `docs/features/018-run-weather-forecast/specs.md:193` (expected change); the file is absent from `git status --short -uall`
- **Problem:** The spec and standard A7 require the two new keys to be listed. A permission rule denied my access to the file, so I could not read its contents. It shows as neither modified nor untracked, so it either lacks the keys or is git-ignored.
- **Failure scenario:** The feature is off by default, so someone setting up from `.env.example` never learns about `WEATHER_LATITUDE` and `WEATHER_LONGITUDE` and sees no weather and no error.
- **Suggested fix:** Add both keys with no values, and confirm the file is tracked.

### [Minor] Unspecified key-workout behaviour is mixed into this feature
- **Where:** `frontend/src/app/features/calendar-like-build/plan-view.ts:118`, `plan-view.ts:183`, `plan-view.ts:187`; `frontend/src/app/features/calendar-like-build/build-calendar/build-calendar.scss:137`; `frontend/src/app/features/calendar-like-build/plan-sections.spec.ts:114`
- **Problem:** `KEY_WORKOUTS` hard-codes two dates, replaces the plan's session text and adds a new tile style. No requirement covers this. The spec's Risks section already warned that these files held uncommitted work and asked for it to be committed or stashed first.
- **Failure scenario:** Editing the 20 Nov session in the plan CSV has no visible effect, because the tile still shows `5x5km`, while its minutes and km do follow the CSV. Moving the plan's start date highlights the wrong days.
- **Suggested fix:** Move this to its own commit and spec, outside feature 018.

### [Minor] AC2.3's timeout case and the unresolved-address branch are untested
- **Where:** `backend/src/test/kotlin/app/stravadashboard/weather/OpenMeteoClientTest.kt:96`; `backend/src/main/kotlin/app/stravadashboard/weather/OpenMeteoClient.kt:78`
- **Problem:** The only thrown failure in the test is a plain `IOException`. Handling of a timeout relies on the Ktor timeout exception being an `IOException`, and no test confirms that. The `UnresolvedAddressException` catch never runs under test.
- **Failure scenario:** If a timeout or DNS failure stopped matching a catch branch, the exception would escape the client and the request would no longer get the specified 503 `weather_unavailable`, with no test failing.
- **Suggested fix:** Add `HttpRequestTimeoutException` and `UnresolvedAddressException` cases to the existing list.

### [Nit] The nullable `source` is redundant
- **Where:** `backend/src/main/kotlin/app/stravadashboard/domain/weather/RunWeatherService.kt:14`, `RunWeatherService.kt:20`
- **Problem:** The spec declares `source: WeatherSource`. The implementation makes it nullable, so `source == null` duplicates the `home == null` check and `withValencia` needs an extra `weather` parameter. `Application.kt` only passes null when `home` is null, so the second check never decides the result.
- **Failure scenario:** None today; this is extra code only.
- **Suggested fix:** Optional. Keep both values together as one nullable pair, or build the service so it needs no second check.

### [Nit] Unrelated `.run/` IDE configs in the working tree
- **Where:** `.run/Backend.run.xml:1`, `.run/Dev.run.xml:1`, `.run/Frontend.run.xml:1`
- **Problem:** These are not part of the spec. They contain no secrets.
- **Failure scenario:** None; they would add noise to the feature commit.
- **Suggested fix:** Leave them out of this feature's commit.

## Privacy checklist
1. ✅ No secrets or keys in the diff, fixtures or `.run/`. `.env.example` could not be read (access denied).
2. n/a
3. n/a
4. n/a
5. n/a (no scope change)
6. ✅ Nothing is persisted.
7. n/a
8. ✅ Logs hold only the status code or exception class (`OpenMeteoClient.kt:73`, `:103`). The root log level is INFO.
9. ✅ No `Logging` plugin on the Open-Meteo client.
10. ✅ Error bodies are fixed text (`ErrorResponse.kt`).
11. ✅ `RunWeatherRoutes.kt:17` checks the session before any fetch, and a test confirms it.
12. n/a
13. ✅ Only interpolated values and fixed labels, with no `[innerHTML]`.
14. n/a (GET only)
15. ✅ The DTO holds only date, condition and mm. No CORS change.
16. ✅ The browser never calls Open-Meteo. The credit links load nothing until clicked. The backend sends only the configured coordinates.
17. n/a (no Strava data is involved)
18. n/a
19. n/a
20. n/a
21. ❌ See the Minor finding on real home-city coordinates. Forecast values are invented.

## Summary
The implementation matches the spec on every requirement, I found no correctness or contract bugs, and all checks pass, although the backend results came from Gradle's cache. The most important fixes are the test gaps: AC4.4 and AC8.4 have no tests and the AC8.5 test cannot fail, so those behaviours are unprotected against regressions. Also replace the real coordinates in test data and move the key-workout change out of this feature. I did not check rendering in a real browser, or the contents of `.env.example`.

## Resolutions
There were no Blockers, so the reviewer was not re-run. After these fixes, all checks were re-run with Gradle's cache off: backend 634 tests passed, frontend 412 tests passed.

| Finding | Resolution |
|---|---|
| Major: AC4.4 no test | Fixed: `R4 - today stays after the window has passed` runs with the clock at 15:00 |
| Major: AC8.4 no test | Fixed: `018 R8 - 401 adds nothing to the section` runs with the real interceptor |
| Major: AC8.5 test cannot fail | Fixed: `offsetTop` is stubbed, and a moved `scrollTop` must survive the weather arriving |
| Minor: real coordinates | Fixed: tests and fixtures use invented values, and `intent.md` and `specs.md` no longer name the home city |
| Minor: `.env.example` | Not fixed: permission rules deny access to this file, so the owner has to add `WEATHER_LATITUDE=` and `WEATHER_LONGITUDE=` by hand |
| Minor: key-workout change mixed in | Not fixed: the owner's request made before this feature, and it shares lines with it, so it ships in the same PR. The hard-coded dates carry a `shortcut:` comment |
| Minor: timeout and address failures | Fixed: both are added to the failure test |
| Nit: nullable `source` | Not fixed: it lets the HTTP engine exist only when a location is set, and a pair would be no shorter |
| Nit: `.run/` configs | Fixed: left out of the commit |
