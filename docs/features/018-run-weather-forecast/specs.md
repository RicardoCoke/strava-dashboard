# Spec: Run weather forecast on calendar tiles

**Feature ID:** 018 · **Status:** Approved · **Intent:** [intent.md](./intent.md) · **Date:** 2026-11-04

## Summary
A new read-only endpoint, `GET /api/run-weather`, fetches Open-Meteo's hourly forecast each time it is called and returns one verdict per day for the fixed 09:00-11:00 window: sun, cloud, or rain with a total in mm. The location is the home city (from `.env`), except the marathon date, which uses a fixed Valencia location. The build calendar joins the result to its tiles by date, so each tile in range shows a small icon, and every other tile stays as it is today. Nothing is stored.

## Requirements
| ID | Requirement | Priority (Must/Should/Could) |
|---|---|---|
| R1 | The home location comes from `WEATHER_LATITUDE` / `WEATHER_LONGITUDE`. Both unset turns the feature off, and partial or invalid values fail at startup | Must |
| R2 | One Open-Meteo client fetches hourly `precipitation` and `cloud_cover` for 16 days, in the location's local time, and maps every failure to "unavailable" | Must |
| R3 | The window verdict per day: rain from 0.1 mm total (10:00 + 11:00 values), otherwise cloud or sun from mean cloud cover (09:00, 10:00, 11:00) | Must |
| R4 | Only days from today to the end of the forecast get a verdict: no past days, no estimates, no day with incomplete data | Must |
| R5 | The marathon date uses Valencia's forecast, and never falls back to home's | Must |
| R6 | `GET /api/run-weather` returns the exact JSON below, needs a connected session, and fetches a fresh forecast on each call | Must |
| R7 | Every tile with a verdict shows one icon (sun, cloud, cloud with drops), including rest, race and today tiles. Rain tiles show the mm beside the icon | Must |
| R8 | Tiles without a verdict show nothing, and weather loading or failing never affects the calendar | Must |
| R9 | While any weather is available, a visible credit links to Open-Meteo and the CC BY 4.0 licence | Must |
| R10 | No regression: existing endpoints and JSON are unchanged, with no new table, OAuth scope or Strava call, and no browser call to Open-Meteo | Must |

## Acceptance criteria

**Conventions.** `today = LocalDate.now(clock.withZone(zone))` (server zone, the same value behind the plan's `isToday` and `isPast`). Constants: `FORECAST_DAYS = 16`, `RAIN_MIN_MM = 0.1`, `CLOUDY_MIN_PERCENT = 50.0`. Examples use `today = 2026-11-04` and a marathon on 2026-12-06. All forecast numbers are invented.

### R1 — Home location config
- **AC1.1** Given `WEATHER_LATITUDE=10.50` and `WEATHER_LONGITUDE=20.50`, when the config loads, then `weatherHome` holds those values.
- **AC1.2** Given both are unset or blank, when the config loads, then `weatherHome` is null and startup succeeds.
- **AC1.3** Given only one is set, or either is not a number, or latitude is outside -90..90, or longitude is outside -180..180, when the config loads, then startup fails with a message naming the key but not its value.
- **AC1.4** Given a location is set, when `AppConfig.toString()` is called, then it shows `weather=<set>` and not the coordinates.

### R2 — Open-Meteo client
- **AC2.1** Given a location, when the client fetches, then it sends one `GET https://api.open-meteo.com/v1/forecast` with exactly the query parameters `latitude`, `longitude`, `hourly=precipitation,cloud_cover`, `forecast_days=16` and `timezone=auto`.
- **AC2.2** Given a valid response, when it is decoded, then each `hourly.time[i]` is paired with `precipitation[i]` and `cloud_cover[i]`, and null values stay null.
- **AC2.3** Given a network error, a timeout, HTTP 429, any other non-2xx status, malformed JSON, an unparsable time, or arrays of different lengths, when the client fetches, then it returns `Unavailable` after one attempt, with no retry.
- **AC2.4** Given any failure, when it is logged, then the log holds only the status code or exception class, and never the URL or the coordinates.

### R3 — Window verdict
`rainMm = roundOneDecimal(precipitation@10:00 + precipitation@11:00)`, because each hourly value covers the preceding hour. `meanCloud = mean(cloud_cover@09:00, @10:00, @11:00)`. The rounding only removes floating-point noise (0.1 + 0.2), and it keeps the icon and the shown number in agreement.
- **AC3.1** Given precipitation 0.4 at 10:00 and 0.8 at 11:00, then the day is `rain` with `rainMm = 1.2`.
- **AC3.2** Given precipitation 0.0 at 10:00 and 0.1 at 11:00, then the day is `rain` with `rainMm = 0.1`, whatever the cloud cover.
- **AC3.3** Given precipitation 0.0 at both hours and cloud cover 80, 90 and 100, then the day is `cloud` with `rainMm = null`.
- **AC3.4** Given precipitation 0.0 at both hours and cloud cover 10, 20 and 30, then the day is `sun` with `rainMm = null`.
- **AC3.5** Given mean cloud cover of exactly 50, then the day is `cloud`. Given a mean of 49.9, then the day is `sun`.
- **AC3.6** Given rain at 09:00 (the hour before the window) and at 12:00, and 0.0 at 10:00 and 11:00, then those hours don't count and the day is not `rain`.
- **AC3.7** Given precipitation 0.1 at 10:00 and 0.2 at 11:00, then `rainMm = 0.3` exactly.

### R4 — Range and missing data
- **AC4.1** Given `today = 2026-11-04` and a 16-day forecast, then the days returned run from 2026-11-04 to 2026-11-19, and 2026-11-20 is absent.
- **AC4.2** Given the source returns hours dated before `today`, then those days are absent.
- **AC4.3** Given any of the needed values for a day (precipitation at 10:00 or 11:00, cloud cover at 09:00, 10:00 or 11:00) is missing or null, then that day is absent, with no estimate.
- **AC4.4** Given the current time is after 11:00 today, then today is still returned, from the source's values for that morning.

### R5 — Marathon location
A day is the marathon if the plan marks it `race.isMarathon`. Valencia is a code constant: `39.47, -0.38`.
- **AC5.1** Given the marathon is on 2026-12-06 and `today = 2026-11-04`, when the service runs, then Valencia is not fetched and 2026-12-06 is absent.
- **AC5.2** Given `today = 2026-11-21` (the marathon is the last forecast day), when the service runs, then Valencia is fetched, and the 2026-12-06 entry comes from Valencia's data while all other days come from the home location's.
- **AC5.3** Given the marathon is in range and the Valencia fetch fails, when the service runs, then 2026-12-06 is absent (not home's), and the other days are returned as normal.
- **AC5.4** Given Valencia returns incomplete data for the marathon date, when the service runs, then that date is absent.
- **AC5.5** Given the marathon date is in the past, when the service runs, then Valencia is not fetched.
- **AC5.6** Given the plan is not configured, unreadable or invalid, when the service runs, then only the home forecast is used and the request still succeeds.
- **AC5.7** Given the training plan JSON, when it is compared with today's, then `GET /api/training-plan` and 011's race-day form are unchanged.

### R6 — Endpoint
- **AC6.1** Given a connected session and a successful fetch, when `GET /api/run-weather` is called, then it returns 200 with the exact JSON in the Backend API section, with days in ascending date order.
- **AC6.2** Given no connected session, when it is called, then it returns 401 `not_connected`, with no Open-Meteo call.
- **AC6.3** Given `weatherHome` is null, when it is called, then it returns 404 `weather_not_configured`, with no Open-Meteo call.
- **AC6.4** Given the home location fetch fails, when it is called, then it returns 503 `weather_unavailable`, with no provider details in the body.
- **AC6.5** Given two calls where the source's data changes between them, when the second returns, then it reflects the new data (no cache).

### R7 — Tile icon
- **AC7.1** Given a `sun`, `cloud` or `rain` verdict for a tile's date, when the calendar renders, then that tile has exactly one `.weather` element, with `data-condition` set to that value and an icon whose accessible name is `Forecast 09:00-11:00: sunny`, `...: cloudy` or `...: rain`.
- **AC7.2** Given a `rain` verdict with `rainMm = 1.2`, when the tile renders, then `.rain-mm` shows `1.2 mm` beside the icon.
- **AC7.3** Given a `sun` or `cloud` verdict, when the tile renders, then there is no `.rain-mm` element.
- **AC7.4** Given a verdict for a rest day, a race day and today, when the calendar renders, then each of those tiles shows the icon, and its other content is unchanged.
- **AC7.5** Given verdicts on near and far days in range, when the calendar renders, then their markup differs only by verdict, with no reliability marking.
- **AC7.6** Given new data on the next page load, when the calendar renders, then the icon and mm match the new data.

### R8 — Tiles without weather
- **AC8.1** Given no verdict for a tile's date (beyond range, past, or incomplete), when the calendar renders, then the tile has no `.weather` element.
- **AC8.2** Given the plan has loaded and the weather request is pending, when the calendar renders, then all tiles show with no weather and no spinner, and icons appear once the request completes.
- **AC8.3** Given the weather request fails with 404, 503 or a network error, or returns a body without a `days` array, when the calendar renders, then it shows the tiles without weather, no error message and no credit.
- **AC8.4** Given the weather request returns 401, when it fails, then the existing interceptor handles it (redirect to `/connect`) and the section adds nothing.
- **AC8.5** Given the current week's scroll position is set on first render, when weather arrives afterwards, then the current week stays in place (only tiles at or after today change).

### R9 — Credit
- **AC9.1** Given `days` is not empty, when the Build calendar card renders, then a `.weather-credit` line below the calendar shows `Weather data by Open-Meteo.com (CC BY 4.0)`, linking to `https://open-meteo.com/` and `https://creativecommons.org/licenses/by/4.0/`, each opening in a new tab with `rel="noopener noreferrer"`.
- **AC9.2** Given `days` is empty or the request failed, when the card renders, then there is no credit line.

### R10 — No regression
- **AC10.1** Given the change, when the existing backend and frontend tests run, then they pass, with page specs changed only to stub the new request.
- **AC10.2** Given the schema, when compared with before, then no table or column is added.
- **AC10.3** Given a weather request, when it is served, then no Strava call is made and the OAuth scopes are unchanged.

## Traceability
| Success criterion (intent) | Covered by |
|---|---|
| S1 (one icon on every in-range tile, including rest days) | R4, R7 |
| S2 (sun, cloud, cloud with drops) | R3, R7 |
| S3 (mm beside the rain icon) | R3, R7 |
| S4 (rain from 0.1 mm) | R3 |
| S5 (nothing beyond range, no estimate) | R4, R5, R8 |
| S6 (nothing on past days) | R4, R8 |
| S7 (home, Valencia for the marathon) | R1, R2, R5 |
| S8 (updates as forecasts arrive) | R6, R7 |
| S9 (provider credit) | R9 |

## Design

### Data from Strava
None. This feature makes no Strava calls and needs no scope. It reads the marathon date from the existing training plan CSV.

### Data from Open-Meteo
- Endpoint: `GET https://api.open-meteo.com/v1/forecast?latitude=<lat>&longitude=<lon>&hourly=precipitation,cloud_cover&forecast_days=16&timezone=auto`. No API key.
- `timezone=auto` makes `hourly.time` local to the coordinates (local time at home, Spain time for Valencia), so no time-zone config is needed.
- Fields used: `hourly.time` (`"2026-11-05T10:00"`), `hourly.precipitation` (mm, sum of the preceding hour, covers rain, showers and snow), `hourly.cloud_cover` (%, at that moment). `weather_code` isn't needed.
- Pagination: none. Timeouts: 5 s connect, 15 s request, as in `garminHttpClient`.
- Caching: none. There are at most 2 calls per calendar load, far below the 10,000 a day limit, and fresh data meets S8 with no stored weather.
- Errors: 429, other non-2xx, network failure or bad data → `Unavailable`, with one attempt and no retry. There are no tokens, so no expiry case.
- Engine: Java (JDK HttpClient), created only when a location is set, wired the same way as `garminWellnessSync` in `Application.kt`. The Ktor `Logging` plugin is not installed.

### Domain model & logic
Package `domain/weather/`:
```kotlin
enum class WeatherCondition { SUN, CLOUD, RAIN }
data class DayWeather(val date: LocalDate, val condition: WeatherCondition, val rainMm: Double?) // rainMm set only for RAIN
data class HourlyWeather(val time: LocalDateTime, val precipitationMm: Double?, val cloudCoverPercent: Double?)
sealed interface WeatherFetchResult { data class Success(val hours: List<HourlyWeather>); data object Unavailable }
sealed interface RunWeatherResult { data object NotConfigured; data object Unavailable; data class Ready(val days: List<DayWeather>) }
```
- `runWindowDays(hours: List<HourlyWeather>, today: LocalDate): List<DayWeather>` is a pure function that implements R3 and R4, using `roundOneDecimal` from `domain/model/Units.kt`. It runs on hours already in local time. Open-Meteo applies the UTC offset as of the request to the whole range, so forecast days after a DST change are one hour off until the change has passed. Accepted: the current build ends on 6 Dec, with no change before then.
- `RunWeatherService(home: WeatherLocation?, source: WeatherSource, plan: TrainingPlanService, clock, zone).get()`:
  1. Without `home`, return `NotConfigured`.
  2. Fetch `home`. If that fails, return `Unavailable`.
  3. Read the marathon date as the first day in `plan.get()` (when `Ready`) with `race?.isMarathon == true`.
  4. If that date is in `today..today + 15`, fetch `VALENCIA`, and replace the home entry for that date with Valencia's (or remove it if Valencia has none).
- `WeatherLocation(latitude: Double, longitude: Double)` lives in `config/`, as `LthrSetting` does. `VALENCIA` is a constant in the service.

Fixed location values: the home city's coordinates (city level) live only in the git-ignored `.env`, because this repository is public.

### Persistence
None. No table, no migration. The forecast stays in memory for the length of the request.

### Backend API
- `GET /api/run-weather`: no query params, connected session required.
- Response (JSON, exact). `condition` is `"sun"`, `"cloud"` or `"rain"`. `rainMm` is millimetres to one decimal for `rain`, and `null` otherwise. Days not listed have no weather:
```json
{
  "days": [
    { "date": "2026-11-04", "condition": "sun", "rainMm": null },
    { "date": "2026-11-05", "condition": "cloud", "rainMm": null },
    { "date": "2026-11-06", "condition": "rain", "rainMm": 1.2 }
  ]
}
```
- Errors (standard `{"error", "message"}`):
  - 401 `not_connected`
  - 404 `weather_not_configured`: "No weather location is configured."
  - 503 `weather_unavailable`: "The weather forecast is unavailable."

### Frontend (Angular)
- Route / page: no new route. This changes the existing Build calendar card on the running dashboard.
- Service & TS interfaces, in `core/api/`:
```ts
// run-weather.models.ts
export type WeatherCondition = 'sun' | 'cloud' | 'rain';
export interface RunWeatherDay { date: string; condition: WeatherCondition; rainMm: number | null; }
export interface RunWeatherResponse { days: RunWeatherDay[]; }
// run-weather.service.ts: getRunWeather(): Observable<RunWeatherResponse>, GET /api/run-weather
```
- `BuildCalendarSection` (container): `weather = toSignal(service.getRunWeather().pipe(...), { initialValue: [] })`. The pipe takes `days`, treats a body without a `days` array as `[]`, and turns any error into `[]`. It passes `[weather]="weather()"` to the calendar and renders the credit line when the list isn't empty.
- `BuildCalendar`: new `weather = input<RunWeatherDay[]>([])`, and `rows` becomes `weekRows(this.weeks(), this.weather())`. The `.weather` block sits at the end of `.body`, outside the rest/session branch, so rest days get it too.
- `plan-view.ts`: `weekRows(weeks, weather: RunWeatherDay[] = [])` joins by date, and `DayCell` gains `weather: { condition: WeatherCondition; label: string; rainText: string | null } | null`. `rainText` uses the existing `formatOneDecimal` (`"1.2 mm"`).
- Icons: three inline `<svg>` shapes with `fill="currentColor"` in an `@switch`, like the bell in `weekly-summary-bell.html`. Each is `role="img"` with the `aria-label` from AC7.1.
- `build-calendar.scss`: styles for `.weather` and `.rain-mm`, and `.weather` added to the `.day.marathon` and `.day.key-workout` colour lists, so the icon is readable on those backgrounds.
- States:
  - Loading: tiles with no weather and no spinner (the plan keeps its own loading state).
  - Empty: tiles with no weather and no credit.
  - Error: same as empty, with no message. The backend logs a warning.
  - Normal: icons on tiles in range, plus the credit.
- Chart: none.

### Files expected to change
Backend:
- `backend/src/main/kotlin/app/stravadashboard/config/AppConfig.kt`, and new `config/WeatherLocation.kt`
- `backend/src/main/kotlin/app/stravadashboard/Application.kt` (new `weatherEngine` test seam, wiring)
- `backend/src/main/kotlin/app/stravadashboard/api/ErrorResponse.kt`
- New `backend/src/main/kotlin/app/stravadashboard/api/RunWeatherRoutes.kt`, `api/RunWeatherDtos.kt`
- New `backend/src/main/kotlin/app/stravadashboard/domain/ports/WeatherSource.kt`
- New `backend/src/main/kotlin/app/stravadashboard/domain/weather/RunWeather.kt`, `domain/weather/RunWeatherService.kt`
- New `backend/src/main/kotlin/app/stravadashboard/weather/OpenMeteoClient.kt` (client + private DTOs)
- `backend/src/test/kotlin/app/stravadashboard/AppConfigTest.kt`, and new tests under `domain/weather/`, `weather/` and `api/`, with invented fixtures in `backend/src/test/resources/open-meteo/`
- `.env.example` (the two keys, no values)

Frontend:
- New `frontend/src/app/core/api/run-weather.models.ts`, `run-weather.service.ts`, `run-weather.service.spec.ts`
- `frontend/src/app/features/calendar-like-build/plan-view.ts`, `plan-view.spec.ts`
- `frontend/src/app/features/calendar-like-build/build-calendar/build-calendar.ts`, `build-calendar.scss`
- `frontend/src/app/features/calendar-like-build/build-calendar-section/build-calendar-section.ts`
- `frontend/src/app/features/calendar-like-build/plan-sections.spec.ts`, and a new `build-calendar-section.spec.ts`
- New `frontend/src/app/testing/run-weather-stub.ts`, added to every `running-dashboard-page.*.spec.ts`. These call `http.verify()`, so the new request would fail them otherwise.

## Edge cases
| Case | Expected behaviour |
|---|---|
| No activities in range | Not relevant: weather doesn't use activities. Tiles still show icons |
| Manual activity (no GPS) | Not relevant: no activity data is read |
| Activity without heart rate | Not relevant: no activity data is read |
| Week spans DST change / time zones | Stamps use the location's UTC offset as of the request, so days after an upcoming change read one hour off until it has passed. Accepted: no change falls before the 6 Dec race |
| Unit conversion | None: the provider's mm are shown as they are, to one decimal |
| Today, after the window has passed | Still shown (AC4.4). "Today" counts as upcoming |
| Past day, including a past marathon | Nothing shown |
| Marathon before it comes into range | Nothing shown until 2026-11-21, for a 2026-12-06 race |
| Valencia fails, home succeeds | All tiles as normal, marathon blank |
| Home fails | No weather at all (503), and Valencia isn't fetched |
| Snow | Counts as precipitation, so it shows the cloud with drops and its water-equivalent mm |
| Light rain just under the window (09:00 stamp) or after it (12:00 stamp) | Doesn't count (AC3.6) |
| More than one plan day at marathon distance | The first is treated as the marathon; the rest use home weather |
| Forecast dates after the plan's end | Returned but matched to no tile, so never shown |
| Tab left open for days | The icons don't refresh until the page reloads (see Risks) |
| Weather slow or down | The calendar isn't blocked, because weather has its own request |

## Privacy
- Data fields & sensitivity: outgoing are the fixed city-level coordinates of home and Valencia, plus the backend's IP address as seen by Open-Meteo. Incoming are public forecast values. No Strava data, GPS track, health data or token is involved.
- Storage & retention: nothing stored. The coordinates live in the git-ignored `.env`; Valencia is a code constant.
- Scopes: no change.
- Applicable privacy-check items:
  - 1 ✅ no key exists, and `.env.example` has no values.
  - 6 ✅ nothing persisted.
  - 8 ✅ logs hold status or exception class only, never the URL with coordinates.
  - 9 ✅ no `Logging` plugin on the client.
  - 10 ✅ error bodies are fixed text.
  - 11 ✅ session check on the route.
  - 13 ✅ only interpolated values from the backend, with no `[innerHTML]`.
  - 14 n/a (GET only).
  - 15 ✅ DTO holds only date, condition and mm; no raw provider object.
  - 16 ✅ the browser never calls Open-Meteo, and the backend sends no personal data beyond the intended coordinates. The credit links load nothing until clicked.
  - 21 ✅ fixtures are invented.
  - 2-5, 7, 12, 17-20: n/a, no change to Strava data, tokens, sessions or retention.

## Test plan
| AC | Test type | Test name |
|---|---|---|
| AC1.1, AC1.2 | backend unit | `R1 - reads weather location or leaves it unset` |
| AC1.3 | backend unit | `R1 - partial or invalid location fails at startup` |
| AC1.4 | backend unit | `R1 - toString hides coordinates` |
| AC2.1 | backend client (MockEngine) | `R2 - sends the exact forecast query` |
| AC2.2 | backend client | `R2 - decodes hourly values and keeps nulls` |
| AC2.3 | backend client | `R2 - every failure is Unavailable after one attempt` |
| AC2.4 | backend client | `R2 - failure logs omit url and coordinates` |
| AC3.1, AC3.2, AC3.7 | backend unit | `R3 - rain totals the 10 and 11 values` |
| AC3.3, AC3.4 | backend unit | `R3 - dry days are cloud or sun` |
| AC3.5 | backend unit | `R3 - cloud threshold at 50 percent` |
| AC3.6 | backend unit | `R3 - hours outside the window are ignored` |
| AC4.1 | backend unit | `R4 - range is today to the last forecast day` |
| AC4.2 | backend unit | `R4 - past days are dropped` |
| AC4.3 | backend unit | `R4 - incomplete day is dropped` |
| AC4.4 | backend unit | `R4 - today stays after the window has passed` |
| AC5.1, AC5.5 | backend unit | `R5 - Valencia fetched only when marathon is in range` |
| AC5.2 | backend unit | `R5 - marathon entry comes from Valencia` |
| AC5.3, AC5.4 | backend unit | `R5 - no Valencia data leaves the marathon blank` |
| AC5.6 | backend unit | `R5 - plan problems still return home weather` |
| AC5.7, AC10.1 | existing tests | existing 004 and 011 suites pass |
| AC6.1 | backend route | `R6 - returns the exact JSON` |
| AC6.2 | backend route | `R6 - not connected is 401` |
| AC6.3 | backend route | `R6 - no location is 404` |
| AC6.4 | backend route | `R6 - home failure is 503` |
| AC6.5 | backend unit | `R6 - each call uses fresh data` |
| AC7.1-AC7.3 | frontend component | `R7 - tile shows the icon for its verdict and mm only for rain` |
| AC7.4 | frontend component | `R7 - rest, race and today tiles show the icon` |
| AC7.5 | frontend component | `R7 - near and far tiles look the same` |
| AC7.6, AC8.2 | frontend component | `R8 - icons appear when the weather arrives` |
| AC8.1 | frontend component | `R8 - tile without a verdict has no weather` |
| AC8.3 | frontend component | `R8 - failed or malformed weather leaves the calendar intact` |
| AC8.4 | frontend component | `R8 - 401 adds nothing to the section` |
| AC8.5 | frontend component | `R8 - scroll position holds when weather arrives` |
| AC9.1, AC9.2 | frontend component | `R9 - credit shows only with weather` |
| AC10.2, AC10.3 | backend unit | `R10 - weather makes no Strava call` (schema: checked in review) |
| service contract | frontend service | `R6 - getRunWeather calls the endpoint` |

## Out of scope
- Weather on past days, or any stored weather history.
- Estimates for days beyond forecast range (including from past weather).
- Temperature, wind, or any metric other than sun/cloud/rain and rain mm.
- Per-day location or per-day run-time settings; the 09:00-11:00 window is fixed, including on race day. Valencia on the marathon tile is the one location exception, with no settings screen.
- Marking far-off forecasts as less reliable.
- Changes to 011 race-day form.
- Caching, retries, and refreshing while the page stays open.

## Suggested follow-ups
- Refresh weather on a timer or after each finished sync, if open tabs going stale becomes annoying.
- A short in-memory cache that keeps icons showing through a brief Open-Meteo outage.

## Risks & decisions to confirm
- **Cloud threshold (the intent left this to the spec):** mean cloud cover of 50% or more shows the cloud, below shows the sun. So a mostly cloudy, not fully overcast morning shows a cloud. Raising it is a one-constant change.
- **Valencia is included:** it costs a constant, one plan lookup and one extra fetch, which is small enough under your "skip it if it adds a lot" rule.
- **Updates on page load only:** S8 is met each time the calendar loads, not while a tab stays open.
- **A failure looks like "out of range":** tiles are blank with no message when Open-Meteo is down. The missing credit line is the only hint, and the backend logs a warning.
- **Feature off by default:** without `WEATHER_LATITUDE` and `WEATHER_LONGITUDE` in `.env` no weather shows, including for the marathon. Both must be added, then the backend restarted.
- **Provider details checked on 2026-11-04:** Open-Meteo's docs confirm `precipitation` is a preceding-hour sum and `cloud_cover` is instantaneous. A live request for Valencia with `timezone=auto` returned Europe/Madrid times and a complete last day (19 Nov).
- **Licence:** the intent records Open-Meteo as free for non-commercial use, which fits this personal dashboard. Commercial use would need a paid plan.
- **Uncommitted work:** `build-calendar.ts`, `build-calendar.scss`, `plan-view.ts` and `plan-sections.spec.ts` already have uncommitted changes, and this feature edits the same files. Commit or stash those first to keep the diff clean.
