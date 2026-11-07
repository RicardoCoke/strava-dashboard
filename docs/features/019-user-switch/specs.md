# Spec: Switch between Ricardo and Marina

**Feature ID:** 019 · **Status:** Approved · **Intent:** [intent.md](./intent.md) · **Date:** 2026-11-04

## Summary
Each person gets a complete, separate backend stack: their own SQLite file, Strava app, sync, plan and settings. Ricardo's existing database and settings are used as they are, so nothing needs migrating. Every `/api` route is mounted once per person and chosen by an `X-Person` request header, which Angular adds from a remembered selection. A slide switch left of the bell changes that selection and recreates the whole dashboard page, so no data from the previous person can stay on screen.

## Requirements
| ID | Requirement | Priority (Must/Should/Could) |
|---|---|---|
| R1 | Marina's settings come from new `MARINA_*` env variables, as an optional group that fails fast when incomplete or when it shares Ricardo's database | Must |
| R2 | Marina's data lives in her own SQLite file. Ricardo's file keeps its path and schema | Must |
| R3 | `X-Person` selects the person on every `/api` route except `/api/health`. No header means Ricardo | Must |
| R4 | One valid session cookie works for both people. A person with no stored tokens gets 401 | Must |
| R5 | Each person connects through their own Strava app. Connecting never deletes data, and an athlete who doesn't belong to that person is rejected | Must |
| R6 | A sync request for one person also starts the other person's sync if it is due and they are connected | Must |
| R7 | Marina's services use her own plan, start date, LTHR, max HR, goal pace and 18-week build | Must |
| R8 | Marina's PDF is converted by hand to a git-ignored CSV in the existing plan format, and the existing parser dates it correctly | Must |
| R9 | The calendar shows her sessions and distances and hides what her plan can't provide. Ricardo's pinned key workouts appear only on his calendar | Must |
| R10 | With no goal pace, her marathon-pace card shows "not configured" and names her variable | Must |
| R11 | Resting HR / HRV uses Marina's own Garmin key, and the card is hidden when she is selected and no key is set | Must |
| R12 | Weather uses the shared home location for both | Must |
| R13 | A slide switch labelled Ricardo / Marina sits just left of the bell and works with keyboard and screen reader | Must |
| R14 | Flipping the switch recreates the whole dashboard for the selected person with no login, and focus stays on the switch | Must |
| R15 | The bell, panel and badge follow the selected person, with a separate "seen" value per person | Must |
| R16 | The selection is remembered per browser across reloads | Must |
| R17 | The connect page connects the selected person and lets the user switch away | Must |
| R18 | Ricardo's experience is unchanged: same data, numbers, plan, session and JSON | Must |

## Acceptance criteria

**Conventions.** `Person` is the enum `RICARDO`, `MARINA`, with wire values `"ricardo"` and `"marina"`. All numbers and IDs in tests are invented. The dates below come from the intent.

### R1 — Marina's configuration
Variables: `MARINA_STRAVA_CLIENT_ID`, `MARINA_STRAVA_CLIENT_SECRET`, `MARINA_DB_PATH` (the required group), plus `MARINA_TRAINING_PLAN_CSV`, `MARINA_TRAINING_PLAN_START_DATE`, `MARINA_LTHR_BPM`, `MARINA_MAX_HR_BPM`, `MARINA_MARATHON_GOAL_PACE`, `MARINA_GARMIN_API_KEY`.
- **AC1.1** Given none of the required group is set, when config loads, then `marina` is null and the app starts.
- **AC1.2** Given one or two of the required group are set, when config loads, then startup fails with a message naming the missing key and no values.
- **AC1.3** Given `MARINA_DB_PATH` resolves to the same absolute path as `DB_PATH`, when config loads, then startup fails.
- **AC1.4** Given `MARINA_TRAINING_PLAN_CSV` is set and the start date is missing, not ISO or not a Monday, when config loads, then startup fails. There is no default date for her.
- **AC1.5** Given Marina is configured, when `AppConfig.toString()` is called, then it contains no client ID, secret, API key, path or HR value of hers.
- **AC1.6** Given Marina isn't configured, when any `/api` route except `/api/health` is called with `X-Person: marina`, then the response is 401 `not_connected`.

### R2 — Separate storage
- **AC2.1** Given a database file created before this feature with runs, tokens, sync state and wellness rows, when the app starts, then every row is unchanged and no table or column was added.
- **AC2.2** Given runs stored for each person, when the dashboard, weekly summary, training load and sync status are read for one person, then their figures come only from that person's runs.
- **AC2.3** Given the same activity ID exists in both files, when one is updated or deleted, then the other is unchanged.

### R3 — Selecting the person
- **AC3.1** Given a request with no `X-Person` header or with `ricardo`, when any `/api` route is called, then Ricardo's stack answers.
- **AC3.2** Given `X-Person: marina`, when each route in *Backend API* is called, then Marina's stack answers. The test covers every listed route, so a route mounted only once fails.
- **AC3.3** Given any other `X-Person` value, when a route is called, then the response is 404 and neither person's data is read.
- **AC3.4** Given `X-Person: marina`, when `GET /api/health` is called, then it answers as before.
- **AC3.5** Given the Angular app, when any `/api/` request is sent, then it carries `X-Person` with the current selection. Requests outside `/api/` don't carry it.

### R4 — One session for both people
- **AC4.1** Given a session cookie set when Ricardo connected and both people connected, when Marina's routes are called, then they return 200 with her data and no login step.
- **AC4.2** Given a session cookie set when Marina connected, when Ricardo's routes are called, then they return 200 with his data.
- **AC4.3** Given a valid session and Marina has no stored tokens, when her route is called, then the response is 401 `not_connected`. Ricardo's routes still return 200.
- **AC4.4** Given no cookie or an unsigned one, when either person's route is called, then the response is 401.
- **AC4.5** Given Marina's connection was revoked by Strava, when `GET /api/sync` is called as Marina, then the response is 401 `strava_reauth_required`. Ricardo's tokens and routes are unaffected.

### R5 — Connecting without losing data
- **AC5.1** Given `GET /auth/strava/login?person=marina`, then the redirect uses Marina's client ID, the existing `STRAVA_REDIRECT_URI` and scope `activity:read_all`, and the signed state cookie records `marina`.
- **AC5.2** Given `GET /auth/strava/login` with no parameter, then the redirect uses Ricardo's client ID, as before.
- **AC5.3** Given an unknown `person` value, then the redirect goes to `/connect?error=unknown_person`.
- **AC5.4** Given Marina isn't configured and `person=marina`, then the redirect goes to `/connect?error=person_not_configured` and no Strava redirect happens.
- **AC5.5** Given a callback whose state says `marina`, then the code is exchanged with Marina's credentials and her tokens go only into her database.
- **AC5.6** Given Marina's first connect with an athlete ID unknown to both people, then her tokens and owner are stored, the session is set and Ricardo's file is unchanged.
- **AC5.7** Given Ricardo's owner is athlete A with stored runs, when athlete B completes a connect as Ricardo, then the redirect goes to `/connect?error=wrong_athlete`, no session is set, and Ricardo's runs, tokens and sync state are unchanged.
- **AC5.8** Given Ricardo's owner is athlete A, when athlete A completes a connect as Marina, then the redirect goes to `/connect?error=wrong_athlete` and nothing is stored for Marina. The reverse case behaves the same.
- **AC5.9** Given the owner's tokens were revoked and that same athlete reconnects, then new tokens are stored and runs and sync cursor are kept.
- **AC5.10** Given a stored owner of null with tokens present, when that athlete reconnects, then the owner is filled in and the cursor is kept.
- **AC5.11** Given a state cookie issued before this feature (no person field), when the callback runs, then it is treated as Ricardo.

### R6 — Background sync for both
- **AC6.1** Given both connected and Marina's sync due, when `POST /api/sync` is called as Ricardo, then the response is Ricardo's status and Marina's job also starts. The reverse direction behaves the same.
- **AC6.2** Given the other person has no tokens, when the sync is posted, then no job starts for them, no Strava call is made and their reauth flag isn't set.
- **AC6.3** Given starting the other person's job throws, when the sync is posted, then the response is still the selected person's status, and only the exception class name is logged.
- **AC6.4** Given Marina's budget is used up or she is rate limited, then Ricardo's sync still runs, because each person has their own `StravaCallBudget` and sync state.
- **AC6.5** Given an expired token, then the refresh uses that person's client ID and secret.

### R7 — Marina's own settings
- **AC7.1** Given `MARINA_LTHR_BPM` differs from `LTHR_BPM`, when training load, race-day form and weekly summary load are read as Marina, then they use her zones. As Ricardo they use his.
- **AC7.2** Given `MARINA_MAX_HR_BPM` is set, when both VO2max endpoints are read as Marina, then `maxHr` is hers, and Ricardo's is unchanged.
- **AC7.3** Given a goal pace and start date of 2026-08-03 for Marina, when `GET /api/marathon-pace` is read as Marina, then `buildStart` is `2026-08-03`, `buildEnd` is `2026-12-06` and `weeks` has 18 items.
- **AC7.4** Given Ricardo's settings, when `GET /api/marathon-pace` is read as Ricardo, then there are 16 weeks ending at the same date as before.

### R8 — Marina's plan
- **AC8.1** Given an invented 18-week plan in the format below with start 2026-08-03, when it is parsed, then the first week is labelled `Week 17` with `index: 1`, starting `2026-08-03`, and the last is `Week 0` with `index: 18`, ending `2026-12-06`.
- **AC8.2** Given today is 2026-11-04, then `Week 4` has `isCurrentWeek: true` and `startDate: 2026-11-02`, and the progress label is `73% · week 14 of 18 · 32 days to go`.
- **AC8.3** Given a session cell `LT 13-14 km with 20-25 min at LT pace` and volume `13,50`, then `session` is that exact text, `distanceKm` is 13.5 and `isRest` is false.
- **AC8.4** Given week 0's Sunday has `RACE 42,2K` in its type cell, then `race.isMarathon` is true and race-day form uses `2026-12-06`.
- **AC8.5** Given the plan CSV has no pace rows, then `targetPaces` is `[]` and the plan is valid.
- **AC8.6** Given Ricardo's plan fixture, when it is parsed, then the result is unchanged. `TrainingPlanParser` and its helpers have no code changes.
- **AC8.8** Given a VO2max session in her CSV, then its text reads `at 5K pace`, never `5K race pace`, and `race` is null. The existing race detection would otherwise read `RACE` plus `5K` as a 5K race.
- **AC8.7 (manual)** Before Marina's calendar is called done, Ricardo checks the CSV against the PDF: 18 blocks, every week's session texts and midpoints, with particular care for weeks 9 to 3, including the `102max` cell.

### R9 — Calendar for a plan without minutes
A week "has no split" when `easyPercent == 0` and `subtPercent == 0`.
- **AC9.1** Given a week with no split, then the week row shows its km and no `% easy · % subT` text.
- **AC9.2** Given a non-rest day in a week with no split, then the tile shows the session and km, without `0 min`.
- **AC9.3** Given `targetPaces: []`, then no "Target paces" panel shows (existing behaviour).
- **AC9.4** Given Marina is selected and her plan has a day on `2026-11-20`, then the tile shows her session text with no key-workout highlight. On `2026-11-24` there is no highlight either.
- **AC9.5** Given Ricardo is selected, then those two dates show the same text and highlight as today.
- **AC9.6** Given Ricardo's plan, where every week has a split, then the week and day text is identical to before.

### R10 — Goal pace not set
- **AC10.1** Given `MARINA_MARATHON_GOAL_PACE` is unset, when `GET /api/marathon-pace` is read as Marina, then it returns the existing 404 `marathon_pace_not_configured`.
- **AC10.2** Given that response with Marina selected, then the card shows `Marathon pace not configured.` and `Set MARINA_MARATHON_GOAL_PACE in .env (for example 4:15), then restart the backend.`
- **AC10.3** Given Ricardo is selected, then the text still names `MARATHON_GOAL_PACE`.
- **AC10.4** Given her goal pace is unset, then every other card still loads for her.

### R11 — Resting HR / HRV
- **AC11.1** Given `MARINA_GARMIN_API_KEY` is set, when her sync runs, then Garmin is called with her key and her rows go only into her database.
- **AC11.2** Given no Marina key, when she is selected, then after the 404 `wellness_not_configured` the whole card, heading included, is removed from the page.
- **AC11.3** Given Ricardo is selected with no Garmin key, then the existing "Garmin Connect not connected." message still shows.
- **AC11.4** Given no Marina key, then her weekly summary has no resting HR or HRV notes (existing rule).
- **AC11.5** Given only Ricardo has a key, when Marina's sync runs, then no Garmin call is made for her.

### R12 — Shared weather
- **AC12.1** Given `WEATHER_LATITUDE` and `WEATHER_LONGITUDE` are set, when `GET /api/run-weather` is read as either person, then the home forecast uses those coordinates.
- **AC12.2** Given her plan's marathon date is inside the forecast window, then that one date uses the existing race-location rule, the same as Ricardo's.

### R13 — The switch
- **AC13.1** Given the dashboard header, then `Ricardo`, the slide control and `Marina` appear in that order, directly before the bell in the same group.
- **AC13.2** Given the control, then it is a native `<button role="switch">` with `aria-label="Show dashboard for Marina"` and `aria-checked` true when Marina is selected.
- **AC13.3** Given keyboard focus on it, when Space or Enter is pressed, then the selection flips.
- **AC13.4** Given a narrow screen (360 px wide), then heading, switch and bell stay on screen with no horizontal scroll (manual check).

### R14 — Changing the whole page
- **AC14.1** Given Ricardo's page is loaded, when the switch is flipped, then the old page instance is destroyed, a new one is created, and each section sends its request again with `X-Person: marina`.
- **AC14.2** Given the switch is flipped, then each section shows its loading state until its new data arrives. No section shows the previous person's data, even if the new request fails.
- **AC14.3** Given a Ricardo request is in flight when the switch is flipped, then it is cancelled and its response can't reach the new page.
- **AC14.4** Given a flip, then no navigation to Strava or `/connect` happens when both people are connected.
- **AC14.5** Given a flip made from the switch, then focus is on the new switch after rendering. A page load with no flip doesn't steal focus.
- **AC14.6** Given a sync timer was pending for Ricardo, when the switch is flipped, then it stops and the new page starts Marina's status check.

### R15 — Bell per person
- **AC15.1** Given different newest summaries for each person, when the switch is flipped, then the bell requests the new person's summaries and the badge reflects their revision.
- **AC15.2** Given Ricardo opened his summary and Marina hasn't opened hers, when switching to Marina and back, then her badge shows and his stays clear.
- **AC15.3** Given a value already stored under `sd.weeklySummary.seenRevision`, then Ricardo keeps using that key, so shipping doesn't relight his badge. Marina uses `sd.weeklySummary.seenRevision.marina`.
- **AC15.4** Given the panel is open, when the switch is flipped, then the panel is closed on the new page.

### R16 — Remembered selection
- **AC16.1** Given Marina is selected, when the page reloads, then the first `/api` request already carries `X-Person: marina` and the switch shows Marina.
- **AC16.2** Given nothing stored, an unknown value or disabled storage, then Ricardo is selected. With storage disabled the switch still works until reload.

### R17 — Connect page
- **AC17.1** Given Marina is selected and not connected, when her dashboard is opened, then the existing 401 handling goes to `/connect`, the intro reads `Connect Marina's Strava account. Sign in to Strava as Marina first.` and the link is `/auth/strava/login?person=marina`.
- **AC17.2** Given Ricardo is selected, then the page's text and `/auth/strava/login` link are as they are today.
- **AC17.3** Given the connect page, then the switch is shown. When it is flipped, the app goes to `/`.
- **AC17.4** Given `error=wrong_athlete`, then the page shows `That Strava account doesn't belong to this person. Sign out of Strava, sign in to the right account, then try again.`
- **AC17.5** Given `error=person_not_configured`, then the page shows `Marina's Strava app isn't set up yet. Add the MARINA_ settings to .env, then restart the backend.`
- **AC17.6** Given `error=unknown_person`, then the existing generic message shows.

### R18 — Nothing changes for Ricardo
- **AC18.1** Given the existing backend and frontend suites, then they pass unchanged, except the two `RoutesTest` tests that assert a different athlete wipes data (replaced by AC5.7), the `NormalizedVo2maxRoutesTest` test that a session of another athlete gets 401 (replaced, because R4 loosens that check), and two page tests that assumed the bell is the header's only action and the retry button the page's first button.
- **AC18.2** Given an existing browser with Ricardo's session cookie and nothing in `sd.person`, when the new version opens, then Ricardo's dashboard loads with no login.
- **AC18.3** Given Ricardo's env variables, then their names, meanings and defaults are unchanged.
- **AC18.4** Given Ricardo's data, then all response bodies for his routes are byte-identical to the same request without `X-Person`.

## Traceability
| Success criterion (intent) | Covered by |
|---|---|
| S1 switch left of the bell | R13 |
| S2 everything changes with no login | R3, R4, R14, R15 |
| S3 reload opens the last selection | R16 |
| S4 her own plan, start date, LTHR, max HR, goal pace | R1, R7, R8 |
| S5 her plan in the correct weeks | R8 |
| S6 Ricardo's data exactly as before | R2, R5, R18 |
| S7 runs never mix | R2, R3, R5, R9 |
| S8 Garmin key per person, hidden when unset | R11 |
| S9 shared weather location | R12 |
| S10 her sessions and distances, only the unknown parts hidden | R8, R9 |
| S11 "not configured" until goal pace is set | R10 |
| Constraint: each person connects once | R4, R5, R17 |
| Related: 009 syncs both people | R6 |

## Design

### Data from Strava
- Endpoints: **none new.** Each person uses the existing `GET /athlete/activities` (page, per_page, after), `GET /activities/{id}`, streams and `POST /oauth/token`, through their own app credentials.
- Scopes: `activity:read_all` for both, the existing `REQUIRED_SCOPE`.
- Fields used: unchanged.
- Sync & caching: unchanged per person. Marina's first sync is a normal one-time import of 6 months + 41 days into her file, then incremental by `after`. Sync still runs only while a dashboard tab is open (009).
- Errors, per person and independent of the other:
  - 401: refresh once with that person's credentials. A rejected refresh deletes only their tokens, and their routes then return 401 (`strava_reauth_required` on `/api/sync`, `not_connected` elsewhere).
  - 429: their sync records the rate limit and backs off through the existing budget. Strava limits are per app, so the budgets are separate.
  - Network failure: their sync status becomes `strava_unavailable`, and cached data still shows.

### Domain model & logic
- `Person` enum with `wire` value and `parse(text: String?): Person?`. Null text gives `RICARDO`, unknown text gives null.
- `ConnectService(oauth, tokens, syncState, otherPeople: List<OwnerLookup>)`. The `runs` dependency is removed, because connecting no longer deletes.
  - `owner = syncState.ownerAthleteId ?: tokens.athleteId`
  - If `owner != null && owner != athleteId`, or `athleteId` is another person's owner, return `ConnectResult.WrongAthlete` before anything is saved.
  - If the stored `ownerAthleteId` is null, save `state.copy(ownerAthleteId = athleteId)`, which keeps the cursor. Then save the tokens.
- `MarathonPaceVolume.compute` gains `buildWeeks: Int = BUILD_WEEKS`, and `MarathonPaceService` passes it through. Ricardo keeps 16 and Marina passes a named constant, `MARINA_BUILD_WEEKS = 18`.
- `hasConnectedSession(tokens)` becomes `sessions.get<UserSession>() != null && tokens.find() != null`. The signed cookie proves this browser connected once, and `tokens` is the selected person's repository. Routes keep their signature.
- No change to any calculation, parser or classifier. Bucketing stays on `start_date_local`; metres and m/s conversion stay in the existing domain code.

**Plan CSV format.** The existing parser reads this with no code change. Columns are the same as Ricardo's, with each day taking 2 columns from column 2:
```
Week 17;;MONDAY;;TUESDAY;;WEDNESDAY;;THURSDAY;;FRIDAY;;SATURDAY;;SUNDAY;
;;;;;;;;;;;;;;;
;;;<session>;;<session>;;<session>;;<session>;;<session>;;<session>;;<session>
EASY;0,00;;;;;;;;;;;;;;
SUBT;0,00;;;;;;;;;;;;;;
VOLUME [km];<week km>;<km>;;<km>;;<km>;;<km>;;<km>;;<km>;;<km>;
;;;;;;;;;;;;;;;
```
- Blocks run from `Week 17` to `Week 0` in file order, which is date order.
- Session text is kept as written, including its ranges, with one exception: `5K race pace` is written `5K pace` (AC8.8). Each km value is the range's midpoint. The weekly km is the midpoint of the PDF's weekly range, so it may not equal the sum of the days.
- A rest day has 0 km. A marathon day has `RACE 42,2K` in the type row (the block's second row) at the Sunday column and `42,20` km.
- There are no pace rows above the first block.

### Persistence
- **No table or column changes.** `MARINA_DB_PATH` opens the same schema through `DatabaseFactory.connect`.
- Ricardo's `DB_PATH` is untouched. Suggested value: `data/marina.db`, already git-ignored.
- `OAuthState` gains `person: String = "ricardo"`, so existing cookies still read.

### Backend API
No new endpoint and no response shape changes.

**Request header on `/api/*`:** `X-Person: ricardo | marina`, optional, default Ricardo. It applies to all of these, with the JSON defined in their own feature specs:

| Route | Shape defined in |
|---|---|
| `GET /api/dashboard/running` | 001, 007 |
| `GET /api/sync`, `POST /api/sync` | 009 |
| `GET /api/training-plan` | 004 |
| `GET /api/training-load` | 005 |
| `GET /api/race-day-form` | 011 |
| `GET /api/vo2max` | 008 |
| `GET /api/vo2max/normalized` | 016 |
| `GET /api/long-run-drift` | 013 |
| `GET /api/efficiency-factor` | 014 |
| `GET /api/marathon-pace` | 012 |
| `GET /api/wellness` | 015 |
| `GET /api/weekly-summaries` | 017 |
| `GET /api/run-weather` | 018 |

`GET /api/health` isn't per person.

For Marina's plan, `GET /api/training-plan` returns the existing shape with these values:
```json
{
  "targetPaces": [],
  "weeks": [
    {
      "index": 14,
      "label": "Week 4",
      "startDate": "2026-11-02",
      "endDate": "2026-11-08",
      "distanceKm": 55.5,
      "easyPercent": 0.0,
      "subtPercent": 0.0,
      "isCurrentWeek": true,
      "days": [
        {
          "date": "2026-11-03",
          "dayOfWeek": "TUESDAY",
          "session": "LT 13-14 km with 20-25 min at LT pace",
          "note": null,
          "totalMinutes": 0,
          "easyMinutes": 0,
          "subtMinutes": 0,
          "distanceKm": 13.5,
          "isRest": false,
          "race": null,
          "isToday": false,
          "isPast": true
        }
      ]
    }
  ]
}
```
(Fields not shown are unchanged. All km values here are invented.)

Errors:
- 401 `not_connected`: no session, no tokens for the selected person, or Marina not configured.
- 401 `strava_reauth_required`: `/api/sync` only.
- 404 with no JSON body: unrecognised `X-Person`. The app never sends this.
- Existing 403, 404, 422 and 503 codes are unchanged.

**Auth routes:**
- `GET /auth/strava/login?person=ricardo|marina`: `person` is optional and defaults to Ricardo. It redirects to Strava. Failures redirect to `/connect?error=unknown_person` or `/connect?error=person_not_configured`.
- `GET /auth/strava/callback`: reads the person from the signed state cookie, never from the query. A new failure redirects to `/connect?error=wrong_athlete`. Success sets `sd_session` and redirects to `/`, as before.

**Wiring (`Application.kt`).**
- A new `PersonStack` holds one person's repositories and services, built by one function called once per person. Ricardo's input comes from the existing `AppConfig` fields, and Marina's from `config.marina`.
- The Strava `HttpClient`, Open-Meteo source and background scope are shared. `StravaOAuthClient`, `StravaTokenProvider`, `StravaClient`, `StravaCallBudget`, `SyncService`, `BackgroundSync`, `WellnessSync`, and every read service are per person.
- A small `PersonRouteSelector` mounts `personRoutes(stack)` once for each person, so exactly one branch matches each request. When Marina isn't configured, her branch answers every `/api` path with 401 `not_connected`.

### Frontend (Angular)
- **Route:** `''` now loads `DashboardShell`, which holds only:
  ```html
  @for (person of [store.person()]; track person) { <app-running-dashboard-page /> }
  ```
  A new key destroys the page, along with its `TrainingPlanStore`, `SyncStore`, timers and in-flight requests. This reuses each section's existing start-up and loading path.
- **`core/person/person.store.ts`** (`providedIn: 'root'`):
  ```ts
  export type Person = 'ricardo' | 'marina';
  // person: Signal<Person>; select(person: Person): void; takeFocusRequest(): boolean
  ```
  It reads localStorage key `sd.person` once at start-up and writes it on each flip, inside try/catch. The signal, not storage, is the live value for the tab.
- **`core/interceptors/person.interceptor.ts`:** adds `X-Person` to `/api/` requests. Registered before `apiErrorInterceptor`.
- **`shared/person-switch/person-switch.ts`** (`app-person-switch`): the button from R13, with output `changed`. The thumb position comes from `person()`. It restores focus in `afterNextRender` when `takeFocusRequest()` is true.
- **`running-dashboard-page.html`:** a new `.header-actions` flex group holds `<app-person-switch />` and `<app-weekly-summary-bell />`.
- **`plan-view.ts`:**
  - `weekRows(weeks, weather = [], person: Person = 'ricardo')` applies `KEY_WORKOUTS` only for Ricardo.
  - `splitText` and `minutesText` become `string | null`, null when the week has no split.
  - `build-calendar.ts` hides the null parts.
- **`seen-revision.ts`:** `readSeenRevision(person = 'ricardo')` and `writeSeenRevision(revision, person = 'ricardo')`, with the keys from AC15.3.
- **`wellness-section`:** `@if (!hidden())` wraps the card, with `hidden` true when the state is `notConfigured` and Marina is selected.
- **`marathon-pace-section`:** the variable name in its two help texts comes from the selected person.
- **`connect-page.ts`:** person-specific link and intro, the new error messages, and the switch, whose `changed` output navigates to `/`.
- **Service & TS interfaces:** no API model changes.
- **Chart:** no chart changes. `marathon-pace.options.ts` has no hard-coded week count, so 18 weeks should need no change (confirm with a test at build time).

**States**
| View | Loading | Empty | Error | Normal |
|---|---|---|---|---|
| Switch | Not applicable, shows straight away from the stored value | Not applicable | Not applicable. Storage failure falls back to Ricardo | Selected side highlighted |
| Dashboard after a flip | Each section's existing loading text or spinner | Existing texts, such as `No runs in the last 5 months` | Existing per-section error and Retry, never old data | The new person's data |
| Marina during first import | Sync banner and spinner | Existing empty texts until runs arrive | Existing banner | Fills in on each quiet reload |
| Marina's calendar | Existing "Loading your plan…" in the top card | `No training plan set up yet.` when her CSV isn't set | Existing plan errors | Sessions and km, with no minutes or split |
| Bell | No badge while loading | Existing panel empty text | Existing panel error and Retry | The person's summaries |
| Wellness, Marina without a key | Existing loading text, then the card disappears | Not applicable | Existing error and Retry | Not applicable |
| Connect page | Not applicable | Not applicable | Fixed error messages | Link for the selected person, plus the switch |

### Files expected to change
**Backend**
- `backend/src/main/kotlin/app/stravadashboard/domain/Person.kt` (new)
- `backend/src/main/kotlin/app/stravadashboard/config/AppConfig.kt`, `config/MarinaConfig.kt` (new)
- `backend/src/main/kotlin/app/stravadashboard/Application.kt`, `PersonStack.kt` (new)
- `backend/src/main/kotlin/app/stravadashboard/api/PersonRouteSelector.kt` (new), `api/SyncRoutes.kt`
- `backend/src/main/kotlin/app/stravadashboard/auth/AuthRoutes.kt`, `auth/ConnectService.kt`, `auth/OAuthState.kt`, `auth/SessionGuard.kt`
- `backend/src/main/kotlin/app/stravadashboard/domain/marathonpace/MarathonPaceVolume.kt`, `MarathonPaceService.kt`
- `backend/src/test/kotlin/app/stravadashboard/api/RoutesTest.kt` (replace two tests), `api/PersonRoutesTest.kt` (new), `config/MarinaConfigTest.kt` (new), `auth/ConnectServiceTest.kt` (new or extended)
- `backend/src/test/kotlin/app/stravadashboard/persistence/ExistingDatabaseTest.kt` (new), `domain/trainingplan/CountdownPlanTest.kt` (new), the marathon-pace tests (add 18-week case)

**Frontend**
- `frontend/src/app/core/person/person.store.ts`, `person.store.spec.ts` (new)
- `frontend/src/app/core/interceptors/person.interceptor.ts`, `person.interceptor.spec.ts` (new)
- `frontend/src/app/shared/person-switch/person-switch.ts`, `person-switch.spec.ts` (new)
- `frontend/src/app/features/running-dashboard/dashboard-shell.ts`, `dashboard-shell.spec.ts` (new)
- `frontend/src/app/features/running-dashboard/running-dashboard-page.html|.ts|.scss`
- `frontend/src/app/app.config.ts`, `frontend/src/app/app.routes.ts`
- `frontend/src/app/features/calendar-like-build/plan-view.ts`, `plan-view.spec.ts`, `build-calendar/build-calendar.ts`
- `frontend/src/app/features/weekly-summary/seen-revision.ts`, `seen-revision.spec.ts`, `weekly-summary-bell.ts`, `weekly-summary-bell.spec.ts`
- `frontend/src/app/features/wellness/wellness-section.ts|.html|.spec.ts`
- `frontend/src/app/features/marathon-pace/marathon-pace-section/marathon-pace-section.ts|.html|.spec.ts`
- `frontend/src/app/features/connect/connect-page.ts`, `connect-page.spec.ts`

**Other**
- `.env.example` (add `MARINA_*` keys with no values)

## Edge cases
| Case | Expected behaviour |
|---|---|
| No activities for Marina | Existing empty states. Calendar still shows her plan |
| Manual activity (no GPS) | Same rules as Ricardo's: counts for distance, left out where fields are missing |
| Activity without heart rate | Same rules: no load, drift or EF contribution |
| Week spans DST change / time zones | Bucketed by `start_date_local` per person, so runs don't move week |
| Marina's first import still running | Numbers fill in on each quiet reload. Ricardo's view is unaffected |
| Same Strava account used for both people | `wrong_athlete`, nothing stored |
| Browser signed in to Strava as Ricardo when connecting Marina | Strava authorises Ricardo, or refuses because the app allows one athlete. Either way nothing is stored for her and the page says what to do |
| Session cookie expired (30 days) | Existing redirect to connect. Connecting either person restores access to both |
| Two tabs open, one flipped | Each tab keeps its own in-memory selection, so its screen and requests stay on one person. A reload opens the last person flipped to |
| Switch flipped quickly twice | Each flip recreates the page and cancels the last one's requests. The final selection wins |
| Rest day written as `Rest or cross-train` | Shows `Rest`, because the existing parser fixes that text for a 0 km day |
| Tune-up `8K-15K tune-up race (total 16-21 km)` | Full text shows, plus an `8K race` badge from the existing race detection |
| Marina's plan file missing or malformed | Existing plan errors, for her only. Backend still starts |
| Race-day form for Marina | Works from her km at her easy rate, since she has no minutes or subT split |
| Her plan after 2026-12-06 | Existing "build complete" behaviour |
| Marina removed from `.env` later | Her routes return 401 and the connect page explains. Her file stays on disk |

## Privacy
- **Data fields & sensitivity:**
  - Marina's Strava runs: distances, times, name, description.
  - Heart rate, HR zones, resting HR and HRV: **health data, GDPR Art. 9**.
  - Strava tokens: credentials.
  - Her LTHR, max HR and goal pace.
  - No GPS or polylines are stored for either person.
- **Storage & retention:**
  - Her data is in `MARINA_DB_PATH`, with the same retention as Ricardo's (runs: 6 months + 41 days; wellness: 90 days).
  - Her credentials and values are in `.env`. Her plan CSV is in a git-ignored path (`/*.csv` or `/backend/*.csv`).
  - The browser stores only `sd.person` (`ricardo` or `marina`) and an opaque per-person revision hash.
- **Scopes:** `activity:read_all`, the same as Ricardo's, requested on her own app.
- **Applicable privacy-check items:**
  - 1 ✅ New secrets only in `.env`. `.env.example` has keys only, and fixtures are invented.
  - 2 ✅ Her tokens stay in her database and are never sent to the browser. They are not encrypted at rest, the same as Ricardo's.
  - 3 ✅ Per-person refresh; a revoked token deletes only that person's tokens.
  - 4 ✅ The state cookie stays signed and verified, and the person is read from it, not from the callback query.
  - 5 ✅ No new scope.
  - 6, 7 ✅ No new stored fields, no GPS.
  - 8, 9 ✅ No new logging of tokens, HR, IDs or the header. `MarinaConfig.toString` is redacted.
  - 10 ✅ Standard error shape. Connect errors are fixed texts.
  - 11 ⚠️ Every per-person route checks the session and that person's tokens. By design, a valid session reads either person (intent non-goal), and the session no longer has to match the stored athlete. See Risks.
  - 12 ✅ Cookie settings unchanged.
  - 13 ✅ Plan text and activity names use normal interpolation.
  - 14 ✅ `POST /api/sync` keeps its XSRF check. The switch itself changes no server state.
  - 15 ✅ No new response fields.
  - 16 ✅ No new external calls. Marina's Garmin and Strava calls go to the same services.
  - 17 ⚠️ Marina's data is shown to whoever opens the dashboard. See Risks.
  - 18 ❌ Existing gap, not caused by this feature: there is no disconnect route. Deleting her data means removing `MARINA_DB_PATH` and revoking the app on Strava.
  - 19 n/a No webhook.
  - 20 ✅ Backups now cover two database files, both personal data.
  - 21 ✅ Invented fixtures. Marina's real plan is never used as a fixture.

## Test plan
| AC | Test type | Test name |
|---|---|---|
| AC1.1–AC1.5 | backend unit | `R1 - marina optional when group unset`, `R1 - partial group fails`, `R1 - shared db path fails`, `R1 - plan needs a monday start date`, `R1 - toString redacts marina` |
| AC1.6 | backend route | `R1 - unconfigured marina returns 401` |
| AC2.1 | backend persistence | `R2 - existing database file is unchanged at startup` |
| AC2.2–AC2.3 | backend route, persistence | `R2 - each person reads only their runs`, `R2 - same activity id stays separate` |
| AC3.1–AC3.4 | backend route | `R3 - no header is ricardo`, `R3 - every route follows marina`, `R3 - unknown person is 404`, `R3 - health is shared` |
| AC3.5 | frontend service | `R3 - adds X-Person to api requests only` |
| AC4.1–AC4.5 | backend route | `R4 - ricardo session reads marina`, `R4 - marina session reads ricardo`, `R4 - person without tokens is 401`, `R4 - no or unsigned cookie is 401`, `R4 - revoke affects one person` |
| AC5.1–AC5.4 | backend route | `R5 - login uses marina client`, `R5 - default login is ricardo`, `R5 - unknown person redirects`, `R5 - unconfigured marina redirects` |
| AC5.5–AC5.11 | backend route + unit | `R5 - callback stores in marina database`, `R5 - first marina connect`, `R5 - wrong athlete keeps ricardo data`, `R5 - other person's athlete rejected`, `R5 - reconnect keeps cursor`, `R5 - null owner filled in`, `R5 - old state cookie is ricardo` |
| AC6.1–AC6.5 | backend route + client | `R6 - post starts the other person`, `R6 - unconnected person not started`, `R6 - other failure keeps response`, `R6 - budgets are separate`, `R6 - refresh uses own credentials` |
| AC7.1–AC7.4 | backend route + unit | `R7 - load uses marina lthr`, `R7 - vo2max uses marina max hr`, `R7 - marina build has 18 weeks`, `R7 - ricardo build keeps 16 weeks` |
| AC8.1–AC8.6 | backend unit | `R8 - countdown labels map to dates`, `R8 - current week and progress`, `R8 - session text and midpoint`, `R8 - marathon on last sunday`, `R8 - no pace rows is valid`, `R8 - existing plan unchanged` |
| AC8.8 | backend unit | `R8 - 5K pace session is not a race` |
| AC8.7 | manual | Ricardo's CSV-to-PDF check |
| AC9.1–AC9.6 | frontend unit + component | `R9 - no split text for week without split`, `R9 - no minutes for week without split`, `R9 - empty paces hide panel`, `R9 - marina has no pinned workouts`, `R9 - ricardo keeps pinned workouts`, `R9 - ricardo text unchanged` |
| AC10.1 | backend route | `R10 - marina without goal pace is 404` |
| AC10.2–AC10.4 | frontend component + backend route | `R10 - names marina variable`, `R10 - names ricardo variable`, `R10 - other cards still load` |
| AC11.1, AC11.5 | backend route | `R11 - marina key used for her sync`, `R11 - no garmin call without her key` |
| AC11.2–AC11.3 | frontend component | `R11 - card hidden for marina`, `R11 - message kept for ricardo` |
| AC11.4 | backend unit | existing 017 `R8 - too few wellness values no note` |
| AC12.1–AC12.2 | backend route | `R12 - same home for both`, `R12 - marathon date rule for both` |
| AC13.1–AC13.3 | frontend component | `R13 - switch left of bell`, `R13 - switch role and label`, `R13 - keyboard flips` |
| AC13.4 | manual | Narrow-screen check |
| AC14.1–AC14.6 | frontend component | `R14 - flip recreates page`, `R14 - no old data while loading or on error`, `R14 - flip cancels requests`, `R14 - flip has no navigation`, `R14 - focus stays on switch`, `R14 - flip stops sync timer` |
| AC15.1–AC15.4 | frontend component + unit | `R15 - bell follows person`, `R15 - badges are separate`, `R15 - ricardo keeps old key`, `R15 - panel closed after flip` |
| AC16.1–AC16.2 | frontend service | `R16 - selection survives reload`, `R16 - bad or missing value is ricardo` |
| AC17.1–AC17.6 | frontend component | `R17 - marina link and intro`, `R17 - ricardo page unchanged`, `R17 - switch on connect page goes home`, `R17 - wrong athlete message`, `R17 - not configured message`, `R17 - unknown person generic message` |
| AC18.1 | existing suites | All checks pass, with the two named tests replaced |
| AC18.2–AC18.4 | backend route + frontend component | `R18 - old session still works`, `R18 - env names unchanged`, `R18 - header makes no difference for ricardo` |

## Out of scope
- Adding, renaming or managing users. The two names are fixed.
- Per-person login or password.
- Combined or side-by-side views.
- A second set of weather locations.
- Changes to how Ricardo's plan or numbers are calculated.
- Parser changes, an automatic PDF importer, or merging both people into one database.
- Encrypting tokens at rest and adding a disconnect route.

## Suggested follow-ups
- Name the `MARINA_*` variable in the other "Set X in .env" help texts (LTHR, max HR, plan, Garmin). Only the goal-pace text changes now, because it's the one she will see.
- Keep `Rest or cross-train` text on rest days.
- A plan-CSV way to mark key workouts, replacing the pinned dates.
- Treat `MP`/`LT` sessions in her plan as harder than easy in the race-day projection.
- Sync both people on a server timer, even with no tab open.
- A slide animation on the switch. The page is recreated on a flip, so the thumb moves straight to its new side.

## Risks & decisions to confirm
- **Marina's consent (please confirm):** her heart-rate and training data will be visible to anyone with dashboard access. The intent records Ricardo accepting this, but it's her health data, and Strava's API terms may limit showing one athlete's data to others (not checked against the current terms). Please confirm she agrees.
- **Session check loosened (please confirm):** a signed cookie now opens both people, without having to match the stored athlete. Any unexpired cookie from an earlier connection also still works. This is the smallest way to get "no login to switch". The app must stay bound to localhost or behind a login.
- **Connecting a different athlete no longer wipes data (please confirm):** it now gives `wrong_athlete`. Replacing Ricardo's athlete would mean deleting his database by hand. This removes the data-loss risk the intent calls out, and it replaces two existing tests.
- **Valencia on her race day (please confirm):** `RunWeatherService` uses Valencia for any plan's marathon date. That is right only if her 2026-12-06 marathon is there too. If not, that one date would show the wrong city, and the fix is a small rule.
- **Marathon label (please confirm):** the PDF's `Goal marathon` shows as `RACE 42,2K` with a `Marathon · 42.2 km` badge, because the parser needs that form to detect race day.
- **Plan conversion needs people:**
  - The PDF stays out of the repo, in Ricardo's Downloads folder.
  - Weeks 9 to 3 extract badly, and `102max` is only presumed to be VO2max. Ricardo checks the result (AC8.7).
- **Setup only you can do, without pasting values into chat:**
  - Marina creates her Strava app, with its Authorization Callback Domain matching `STRAVA_REDIRECT_URI`.
  - Fill in the `MARINA_*` values in `.env`.
  - When connecting her, sign out of Ricardo's Strava in that browser first.
- **Strava's one-athlete limit is unverified:** the design works either way, because the check is on the athlete ID the exchange returns.
- **Her race-day projection is an estimate:** without minutes or a split, planned days count as easy running at her easy rate, so hard sessions are under-counted and her projected fatigue will read low.
- **Her LTHR is an estimate,** so her zones, load and form numbers carry that uncertainty until it's updated in `.env`.
- **`.env.example` wasn't readable** when this spec was written (permission denied). The key list above comes from `AppConfig.kt`, so its layout still needs checking at build time.
