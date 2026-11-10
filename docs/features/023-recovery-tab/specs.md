# Spec: Recovery tab with a side menu

**Feature ID:** 023 · **Status:** Approved · **Intent:** [intent.md](./intent.md) · **Date:** 2026-11-10

## Summary
A menu button at the top-left of the header opens a slide-over side panel with two entries: "Performance" (`/`, today's dashboard, unchanged) and "Recovery" (`/recovery`, new). The Recovery page shows the daily history of the three ring scores from feature 020 (Sleep %, Recovery % and Strain) as three ECharts graphs over 7, 30 or 90 days. The data comes from one new endpoint, `GET /api/rings/history`. It reuses 020's calculator and cache. The Garmin cache is widened with a one-off backfill, so that the 90-day range and Recovery's 30-day baselines are full from the start.

## Requirements
| ID | Requirement | Priority |
|---|---|---|
| R1 | A shared menu button at the top-left of both page headers opens a slide-over panel listing "Performance" and "Recovery", with the current page highlighted | Must |
| R2 | Picking a menu entry navigates to that page and closes the panel. The panel also closes with Escape, a tap outside or the close button, and works by keyboard | Must |
| R3 | `/` shows the Performance page exactly as today. `/recovery` shows Recovery. A refresh keeps the current page, and any unknown path goes to `/` | Must |
| R4 | The Garmin cache keeps enough history for 90 days of Sleep, Recovery and Strain, including Recovery's 30-day baselines, and backfills it once for existing databases | Must |
| R5 | `GET /api/rings/history?days=7\|30\|90` returns one entry per day (oldest first, today last) with Sleep %, Recovery %, Recovery zone and Strain, computed with 020's functions, from the cache only, per person | Must |
| R6 | The Recovery page shows exactly three graphs in the order Sleep, Recovery, Strain, with one point per day and gaps for missing days | Must |
| R7 | Graph styles: Sleep is a line on 0–100 %, Recovery is bars coloured by zone (green 67–100, yellow 34–66, red 0–33) on 0–100 %, and Strain is a line on 0–21 | Must |
| R8 | A 7 / 30 / 90-day picker sets the range for all three graphs. The default is 30 days, and the choice isn't remembered after a reload | Must |
| R9 | The Recovery page has loading, error, per-graph empty and normal states | Must |
| R10 | Recovery follows the Ricardo / Marina switch and never shows the previous person's data | Must |
| R11 | The menu and the Recovery page work at 360 px and on desktop, in the 002 dark style | Must |

## Acceptance criteria

**Conventions.** `today` = `LocalDate.now(clock.withZone(zone))` (server zone, as in 020). Day D is Garmin's local `calendarDate`, used as-is. All test numbers are invented.

### R1 — Menu button and panel
- **AC1.1** Given either page, when it renders, then the first element inside `<header class="page-header">` is `<app-nav-menu>`, whose button is the left-most control in the header. The button is `type="button"`, has `aria-label="Open menu"`, `aria-expanded="false"` and `aria-controls` pointing at the panel id, and shows a three-line (hamburger) icon.
- **AC1.2** Given the panel is closed, when the button is clicked, then a panel slides in from the left edge over the page with a dimmed backdrop. The panel is `role="dialog"`, `aria-modal="true"`, `aria-label="Menu"`, and the button's `aria-expanded` becomes `"true"`.
- **AC1.3** Given the panel is open, then it lists exactly two links, in order: "Performance" (`href="/"`) and "Recovery" (`href="/recovery"`).
- **AC1.4** Given the current URL is `/`, then the "Performance" link has `aria-current="page"` and the active style. Given `/recovery`, then "Recovery" does, and the other link has neither.
- **AC1.5** Given either page, then the header still holds `<app-person-switch>` and `<app-weekly-summary-bell>` on the right, unchanged.

### R2 — Navigating and closing
- **AC2.1** Given the panel is open on `/`, when "Recovery" is clicked, then the URL becomes `/recovery`, the Recovery page shows and the panel is closed. The same works the other way.
- **AC2.2** Given the panel is open, when the current page's own link is clicked, then the panel closes and the page isn't reloaded.
- **AC2.3** Given the panel is open, when Escape is pressed, the backdrop is clicked, or the close button (`aria-label="Close menu"`) is clicked, then the panel closes and focus returns to the menu button.
- **AC2.4** Given the panel opens, then focus moves to the first link. Tab moves between the links and the close button. Everything is reachable without a mouse.
- **AC2.5** Given the panel is closed, then its links are not focusable and not in the accessibility tree (the panel isn't rendered, `@if`).

### R3 — Routes
- **AC3.1** Given the browser opens `/`, then the Performance page renders with the same sections in the same order as before this feature: header title "Running", rings, sync banner, runs, then calendar, load, race-day form, wellness, VO2max, marathon pace and build calendar. The only header difference is the menu button (AC1.1).
- **AC3.2** Given the browser opens or refreshes `/recovery`, then the Recovery page renders. In dev this goes through `ng serve`, and in production through Ktor's existing `singlePageApplication` fallback. No backend route change is needed.
- **AC3.3** Given an unknown path such as `/foo`, then the app redirects to `/` (existing `**` route).
- **AC3.4** Both pages are lazy-loaded with `loadComponent`.

### R4 — History in the cache and one-off backfill
- **AC4.1** `RingsPolicy` gains `HISTORY_DAYS = 92` (the 90-day graph starts at today − 89, and sleep need looks back 3 nights). This replaces `STRAIN_DAYS = 31`. `RingsSync.refresh` now fetches every day in `today − 92 .. today` that isn't complete, plus today and yesterday always, by the existing rules (020 AC2.2). So a database synced under 020 fetches the 61 missing days once, and a normal refresh still fetches 2 days.
- **AC4.2** `RingsPolicy.RETENTION_DAYS` becomes 92, so `ring_days` keeps `today − 92 .. today` and nothing in the backfill is pruned.
- **AC4.3** `WellnessPolicy.RETENTION_DAYS` becomes 120 (Recovery on today − 89 needs HRV and resting HR from today − 119). The first fetch and the prune cutoff use it.
- **AC4.4** One-off wellness backfill: `wellness_state` gains a nullable `history_days INTEGER` column (`addColumnIfMissing`), and `WellnessState` gains `historyDays: Int? = null`. Given `historyDays` is null or below `RETENTION_DAYS`, when a refresh is due, then `fetchRange` returns `today − 120 .. today` even if `lastSuccessAt` is set. On `Success` the state is saved with `historyDays = 120`. After that, the 015 rule applies again (from last success − 6). Tested in `WellnessPolicyTest` with `historyDays` null, 90 and 120.
- **AC4.5** Given the backfill hits a 429, a 5xx or a rejected key, then the existing 015 / 020 failure handling applies: rows are kept, `retryAfter` is honoured, and the missing days are fetched by a later refresh. For wellness, `historyDays` stays unset until a fetch succeeds.
- **AC4.6** Given the backfill, then `GET /api/wellness`, `GET /api/weekly-summaries` and `GET /api/rings` return the same JSON for the same cached days as before. Their windows (84 days, the weeks they show, today − 61) don't change. Only extra older rows exist.
- **AC4.7** Logging stays as in 020 AC2.9 and 015: counts and statuses only. The backfill adds no log line with dates, values or URLs.

### R5 — `GET /api/rings/history`
- **AC5.1** Given `days=30` and today 2026-11-10, then the response is 200 with `days` holding exactly 30 entries, from `2026-10-12` to `2026-11-10`, ascending, one per calendar date, with no missing or duplicate dates. Given `days=7`, there are 7 entries. Given `days=90`, there are 90.
- **AC5.2** Given `days` is missing, not an integer, or not one of 7, 30 or 90 (for example `14`, `0` or `abc`), then the response is 400 `{"error":"invalid_days","message":"days must be 7, 30 or 90."}`.
- **AC5.3** For each day D, `sleepPercent = roundHalfUpToInt(RingsCalculator.sleepPerformance(D))`, `recoveryPercent = roundHalfUpToInt(RingsCalculator.recovery(D))`, `recoveryZone = RingsCalculator.zoneOf(recovery(D))` lowercased, and `strain = roundOneDecimal(ring_days[D].strain)`. These are the same functions and rounding as today's rings. Test: the entry for today equals `GET /api/rings`'s `sleep.percent`, `recovery.percent`, `recovery.zone` and `strain.value` for the same cache.
- **AC5.4** Given a day where a value can't be computed (no sleep row, no HRV, a HRV baseline with fewer than 3 values, a null strain, or no row at all), then that field is `null`, never `0`. `recoveryZone` is `null` exactly when `recoveryPercent` is `null`.
- **AC5.5** Given a real strain of `0.0` (stored, all below 55 % LTHR), then `strain` is `0.0`, not `null`.
- **AC5.6** Given today, then today's entry is included. Its strain is today-so-far (partial), and sleep and recovery are null until Garmin has today's data.
- **AC5.7** Given the person has no Garmin key, then the response is 200 with `days` entries for every date and all four value fields `null` (as 020 AC7.3).
- **AC5.8** Given `X-Person: marina`, then only Marina's database is read. Given Ricardo (or no header), only his. Given Marina isn't configured, then the response is the existing 404 `person_not_configured`. Tested with different stored values per person.
- **AC5.9** The endpoint makes no Garmin or other outbound request and reads no runs. The test checks that the fake Garmin engine receives zero requests.
- **AC5.10** The service reads `wellness` and `ring_days` from `firstDay − 31` to today (30-day HRV / resting HR baselines and 3 nights of sleep debt for the first day), and nothing older.

### R6 — Recovery page and graphs
- **AC6.1** Given `/recovery`, then the page shows the header (menu button, `<h1>Recovery</h1>`, person switch, bell), then the range picker, then exactly three chart cards, in order, titled "Sleep", "Recovery" and "Strain".
- **AC6.2** Given a response with N days, then each graph has N x-axis categories, labelled with `formatDayMonth(date)` (for example `12 Oct`), oldest on the left and today on the right.
- **AC6.3** Given a day with a `null` value, then that graph has no point or bar for that day: the series data holds `null`, line series use `connectNulls: false`, and a bar is absent. Lines show symbols (`showSymbol: true`), so a single day between two gaps is still visible. Tested on the option builders.
- **AC6.4** Tooltip (axis trigger) for day D shows `formatShortDate(D)` and one line: `Sleep 82%`, `Recovery 75% (green)` or `Strain 12.9`, or `Sleep no data` etc. for null. The tooltip is built from dates and numbers only (no user-written text).
- **AC6.5** Each chart's `aria-label` (via `app-chart`'s `label` input) is `"Sleep performance per day, last 30 days"`, `"Recovery score per day, last 30 days"` and `"Strain per day, last 30 days"`, with the number following the selected range.

### R7 — Graph styles
- **AC7.1** Sleep: a `line` series, colour `palette.sleep` (`#7ba1bb`), y-axis `min: 0`, `max: 100`, name `Sleep (%)`.
- **AC7.2** Recovery: a `bar` series. Each data item is `{ value, itemStyle: { color } }`, with colour `palette.recoveryGreen` / `recoveryYellow` / `recoveryRed` from `recoveryZone` (the backend's zone, not recomputed in the frontend). The y-axis is `min: 0`, `max: 100`, name `Recovery (%)`. Tested with zone boundaries 33 → red, 34 → yellow, 66 → yellow, 67 → green, as delivered by the backend (AC5.3).
- **AC7.3** Strain: a `line` series, colour `palette.strain` (`#0093e7`), y-axis `min: 0`, `max: 21`, name `Strain (0–21)`.
- **AC7.4** None of the graphs has an average, trend line, baseline or legend.

### R8 — Range picker
- **AC8.1** The picker is a `role="group"` with `aria-label="Range"` and three buttons, `7 days`, `30 days` and `90 days`, each with `aria-pressed` set to `"true"` only for the selected one.
- **AC8.2** Given the page opens, then `30 days` is pressed and the request is `GET /api/rings/history?days=30`.
- **AC8.3** Given `7 days` is clicked, then `GET /api/rings/history?days=7` is sent, and all three graphs show the 7 returned days. Given a range change while an older request is still pending, then only the latest response is shown (the older request is cancelled).
- **AC8.4** Given a reload, then the range is back to 30 days. Nothing is written to `localStorage` or `sessionStorage`.

### R9 — Page states
- **AC9.1** Loading (first load and after a range change): the three cards are replaced by `<app-ring-spinner>` and the text "Loading recovery history…" in a `role="status"` region. The header and the picker stay usable.
- **AC9.2** Error: for a non-401 error, or a 200 body that fails `isRingsHistoryResponse`, the page shows "Couldn't load recovery history." and a Retry button that reloads the selected range. A 401 is left to the interceptor.
- **AC9.3** Empty per graph: given every `sleepPercent` in the range is `null`, then the Sleep card shows its title and "No sleep data in this period" instead of the chart. The same goes for Recovery ("No recovery data in this period") and Strain ("No strain data in this period"). The other cards render normally.
- **AC9.4** Normal: the three charts render with the data (R6, R7).
- **AC9.5** After a background sync finishes (`SyncStore.dataVersion` goes up), the page reloads the selected range quietly and keeps the current graphs if the reload fails (the 020 `RingsSection` pattern).

### R10 — Person switch
- **AC10.1** `/recovery` loads a `RecoveryShell` that renders `<app-recovery-page>` inside `@for (person of [store.person()]; track person)`, the same as `DashboardShell` (019). Given the switch is flipped, then the page is recreated: the pending request is cancelled, a new `GET /api/rings/history?days=30` is sent with the new `X-Person` (existing interceptor), and no graph from the previous person is shown at any point (the loading state shows in between).
- **AC10.2** Given the switch is flipped, then the range goes back to 30 days (a consequence of AC10.1, accepted under AC8.4).

### R11 — Mobile, desktop and look
- **AC11.1** At 360 px wide: the header fits on one row (menu button, title, switch, bell) with no horizontal scroll. The panel is `min(80vw, 280px)` wide, and the picker buttons fit on one row. Each chart card is full width at the existing `app-chart` height (300 px). Manual check.
- **AC11.2** At ≥ 768 px the panel is 280 px wide. The cards stack in one column, the same as the Performance `.cards` grid. Manual check.
- **AC11.3** The panel and backdrop use only existing tokens: panel `--color-surface`, border `--color-border`, text `--color-text`, active link `--color-accent`, backdrop `rgba(0, 0, 0, 0.6)`. The slide animation is ≤ 200 ms and is disabled under `prefers-reduced-motion: reduce`.
- **AC11.4** The menu button and links have a visible focus ring, and each tap target is at least 44 × 44 px.

## Traceability
| Success criterion (intent) | Covered by |
|---|---|
| S1 menu button top-left opens side panel with two entries | R1 (AC1.1–AC1.3) |
| S2 picking an entry opens the page and closes the panel | R2 (AC2.1, AC2.2) |
| S3 Performance unchanged | R3 (AC3.1), R1 (AC1.5), R4 (AC4.6) |
| S4 refresh keeps the page, Performance is default | R3 (AC3.2, AC3.3) |
| S5 three graphs, one point per day | R5 (AC5.1), R6 (AC6.1–AC6.3) |
| S6 Recovery bars coloured by zone | R5 (AC5.3), R7 (AC7.2) |
| S7 Sleep line 0–100 %, Strain line 0–21 | R7 (AC7.1, AC7.3) |
| S8 7 / 30 / 90 picker, default 30 | R8 |
| S9 no data → gap, never zero | R5 (AC5.4, AC5.5), R6 (AC6.3), R9 (AC9.3) |
| S10 follows Ricardo / Marina switch | R5 (AC5.8), R10 |
| S11 mobile and desktop, dark look | R11 |
| Decision 1: one-off backfill, 90 days full incl. baseline | R4 |
| Decision 2: menu on both pages | R1 (AC1.1) |
| Decision 3: current page highlighted | R1 (AC1.4) |
| Assumption: keyboard, Escape, tap outside | R2 (AC2.3–AC2.5) |
| Assumption: no Garmin data → empty graphs, page works | R5 (AC5.7), R9 (AC9.3) |
| Constraint: Garmin only, no Strava | R5 (AC5.9) |

## Design

### Data from Strava
None. No Strava endpoint, scope, token or activity is used. Strava was removed by 021, and every source here is the Garmin cache from 015 and 020.

### Data from Garmin (existing `GarminWellnessSource`, no new endpoint)
- **Wellness (HRV, resting HR):** the existing `GET /usersummary-service/v2/dailysummaries?startDate=&endDate=`. The one-off backfill asks for `today − 120 .. today` in one call (015's first fetch was 91 days).
- **Ring days (sleep, strain):** the existing per-day `GET /wellness-service/wellness/dailySleep?date=` and `GET /wellness-service/wellness/dailyHeartRate?date=` (020's build deviations). The backfill is about 61 extra days, so about 122 sequential calls once per person. That's 61 without LTHR, where no heart rate is fetched.
- **Schedule:** unchanged. Everything runs inside `WellnessSync.refreshIfDue()` in the background job (at most every 15 min), sequentially, stopping at the first failure.
- **Errors:** unchanged from 015 / 020. 401/403 → one retry with a fresh token, then stop (`KeyRejected`). 429 → stop and set `retryAfter` (`Retry-After`, or 15 min). 5xx / network / timeout / bad JSON → stop. Rows stored so far are kept, and the next refresh continues with the missing days. There are no pagination parameters: summaries take one date range, and the others take one day per call.
- **Caching:** the endpoint reads SQLite only. A day is fetched again only if it isn't complete (fetched before it ended), or if it is today or yesterday.

### Domain model & logic
- `RingsPolicy`: `STRAIN_DAYS = 31` → `HISTORY_DAYS = 92L`, and `RETENTION_DAYS = 92L` (R4).
- `WellnessPolicy`: `RETENTION_DAYS = 120L`. `fetchRange` treats `state.historyDays == null || state.historyDays < RETENTION_DAYS` like a first fetch.
- `WellnessState` gains `historyDays: Int? = null`. `WellnessSync` saves `historyDays = RETENTION_DAYS.toInt()` on `Success`.
- New `data class RingsHistoryDay(val date: LocalDate, val sleep: Double?, val recovery: Double?, val strain: Double?)` in `domain/rings/RingsModels.kt`, with unrounded values.
- `RingsCalculator` gains `fun history(from: LocalDate, to: LocalDate): List<RingsHistoryDay>`, which maps each date to `sleepPerformance(d)`, `recovery(d)` and `rings[d]?.strain`. It's pure and reuses the existing functions. No formula changes (intent non-goal).
- `RingsService` gains `suspend fun history(days: Int): List<RingsHistoryDay>`. It computes `first = today − (days − 1)`. When Garmin isn't configured it returns all-null days. Otherwise it reads `wellness.findBetween(first − 31, today)` and `rings.findBetween(first − 31, today)`, and calls `RingsCalculator.history(first, today)`.
- Units: percentages 0–100, strain 0–21 (unitless). There are no metres, pace, GPS or HR series. Dates are Garmin's local `calendarDate`, never re-bucketed. `today` uses the server zone, so a DST day is just one date.

### Persistence
- `wellness_state`: add `history_days INTEGER NULL` via `addColumnIfMissing` in `DatabaseFactory.connect`. It's read and written in `ExposedWellnessRepository.getState/saveState`.
- `ring_days` and `wellness_days`: no schema change. They only keep more rows (92 and 120 days).
- Each person's own file (019). There's no data migration. The backfill fills it.

### Backend API
`GET /api/rings/history?days=<7|30|90>`. `X-Person` selects the person (019). No auth beyond the existing setup: there's no session guard since 021, and the app runs on the home network.

Response 200 (exact; example `days=7`, today 2026-11-10):
```json
{
  "days": [
    { "date": "2026-11-04", "sleepPercent": 82, "recoveryPercent": 75, "recoveryZone": "green", "strain": 12.9 },
    { "date": "2026-11-05", "sleepPercent": null, "recoveryPercent": null, "recoveryZone": null, "strain": 8.4 },
    { "date": "2026-11-06", "sleepPercent": 64, "recoveryPercent": 31, "recoveryZone": "red", "strain": 0.0 },
    { "date": "2026-11-07", "sleepPercent": 91, "recoveryPercent": 52, "recoveryZone": "yellow", "strain": 15.2 },
    { "date": "2026-11-08", "sleepPercent": 100, "recoveryPercent": 88, "recoveryZone": "green", "strain": 10.1 },
    { "date": "2026-11-09", "sleepPercent": 77, "recoveryPercent": 67, "recoveryZone": "green", "strain": null },
    { "date": "2026-11-10", "sleepPercent": 85, "recoveryPercent": 70, "recoveryZone": "green", "strain": 3.6 }
  ]
}
```
Types:
- `days`: array, length = `days` query value, ascending by `date`, last = today (server zone).
- `date`: ISO date string `YYYY-MM-DD`.
- `sleepPercent`: int 0..100 or `null`.
- `recoveryPercent`: int 0..100 or `null`.
- `recoveryZone`: `"green" | "yellow" | "red"` or `null` (null exactly when `recoveryPercent` is null).
- `strain`: number with 1 decimal, 0.0..21.0, or `null`.

Errors (`{"error","message"}`):
- 400 `invalid_days`: "days must be 7, 30 or 90." (new `ErrorResponse.INVALID_DAYS`).
- 404 `person_not_configured` (existing).
- 500 `internal_error` (existing StatusPages, which logs the path only).

Files: `api/RingsDtos.kt` (+ `RingsHistoryResponse`, `RingsHistoryDayDto`), `api/RingsMappers.kt` (+ `List<RingsHistoryDay>.toHistoryDto()`, reusing `roundHalfUpToInt`, `roundOneDecimal` and `zoneOf`), and `api/RingsRoutes.kt` (+ `get("/api/rings/history")`, which parses `days` against `setOf(7, 30, 90)` and responds 400 otherwise).

### Frontend (Angular)
- **Routes** (`app.routes.ts`): keep `''` → `DashboardShell`. Add `{ path: 'recovery', loadComponent: () => import('./features/recovery/recovery-shell').then((m) => m.RecoveryShell) }` before `**`.
- **`shared/nav-menu/nav-menu.ts|.html|.scss`** (new, standalone, OnPush). It holds `open = signal(false)` and renders a button, then `@if (open())` a backdrop and a `<nav role="dialog">` panel with a close button and two `routerLink`s with `routerLinkActive="active"` and `ariaCurrentWhenActive="page"`. `[routerLinkActiveOptions]="{ exact: true }"` is set on `/`. A link click sets `open(false)`. The host listens to `document:keydown.escape` (as `WeeklySummaryBell` does). Focus goes to the first link on open and back to the button on close. There are no inputs or outputs.
- **Header markup:** in `running-dashboard-page.html`, `<app-nav-menu />` and `<h1>Running</h1>` are wrapped in `<div class="header-start">` as the first child of `.page-header`. Nothing else in the page changes. The `.page-header`, `.header-start` and `.header-actions` rules move from `running-dashboard-page.scss` to `styles.scss`, so both pages share them.
- **`features/recovery/recovery-shell.ts`** (new): a copy of the `DashboardShell` pattern, keyed on `PersonStore.person()`, rendering `<app-recovery-page />`.
- **`features/recovery/recovery-page.ts|.html|.scss`** (new container). Header as above with `<h1>Recovery</h1>`. `providers: [SyncStore]`, with `sync.start()` in `ngOnInit` and a quiet reload on `dataVersion` (AC9.5). It has no sync banner or spinner. It holds `range = signal<HistoryRange>(30)`, with `type HistoryRange = 7 | 30 | 90`, and `state = signal<{kind:'loading'} | {kind:'error'} | {kind:'loaded'; data: RingsHistoryResponse}>`. The request goes through `RingsService.getHistory(range)`, and the previous subscription is unsubscribed before each new one (latest wins). Three `computed` option objects come from the builders below, plus three `computed` "is empty" flags.
- **Charts:** three `<app-chart [options] [label]>` inside `.card` sections with an `<h2>`. No new chart component.
- **`features/recovery/recovery-history.options.ts`** (new, pure): `buildSleepHistoryOptions(days, range)`, `buildRecoveryHistoryOptions(days, range)`, `buildStrainHistoryOptions(days, range)`, `historyTooltipLine(...)`, `historyChartLabel(metric, range)` and `isEmptySeries(values)`. They use `baseChartStyle`, `categoryAxisStyle`, `valueAxisStyle` and `tooltipStyle` from `shared/chart/chart-theme`, and `formatDayMonth` / `formatShortDate` from `shared/format/dates`.
- **`shared/chart/palette.ts`:** add `sleep: '#7ba1bb'`, `strain: '#0093e7'`, `recoveryGreen: '#16ec06'`, `recoveryYellow: '#ffde00'` and `recoveryRed: '#ff0026'` (the same values as 020's CSS tokens in `styles.scss`).
- **Service & TS interfaces:** in `core/api/rings.models.ts`, add `RingsHistoryDay { date: string; sleepPercent: number | null; recoveryPercent: number | null; recoveryZone: RecoveryZone | null; strain: number | null }`, `RingsHistoryResponse { days: RingsHistoryDay[] }` and `isRingsHistoryResponse(x: unknown)`. That's a light check: `days` is an array, and each item has a string `date`. In `core/api/rings.service.ts`, add `getHistory(days: 7 | 30 | 90): Observable<unknown>` → `GET /api/rings/history` with `params: { days }`.
- **States:** loading (AC9.1) · error with Retry (AC9.2) · empty per graph (AC9.3) · normal (AC9.4).
- **Tests:** the Performance page specs render `<app-nav-menu>`, which needs `provideRouter([])` in their `TestBed` providers. Add a `testing/nav-menu-stub.ts` (`stubNavMenu()`, the same pattern as `rings-stub.ts`), or add `provideRouter([])`, whichever is smaller per spec file.

### Files expected to change
**Backend (edited)**
- `backend/src/main/kotlin/app/stravadashboard/domain/rings/RingsPolicy.kt`, `RingsModels.kt`, `RingsCalculator.kt`, `RingsService.kt`, `RingsSync.kt` (constant rename)
- `backend/src/main/kotlin/app/stravadashboard/domain/wellness/WellnessPolicy.kt`, `WellnessModels.kt`, `WellnessSync.kt`
- `backend/src/main/kotlin/app/stravadashboard/persistence/Tables.kt`, `DatabaseFactory.kt`, `ExposedWellnessRepository.kt`
- `backend/src/main/kotlin/app/stravadashboard/api/RingsDtos.kt`, `RingsMappers.kt`, `RingsRoutes.kt`, `ErrorResponse.kt`
- Tests: `RingsCalculatorTest.kt`, `RingsSyncTest.kt`, `WellnessPolicyTest.kt`, `WellnessSyncTest.kt`, `ExposedWellnessRepositoryTest.kt`, `RingsRoutesTest.kt`, and the route list in `PersonIsolationTest.kt`

**Frontend (new)**
- `frontend/src/app/shared/nav-menu/nav-menu.ts`, `.html`, `.scss`, `nav-menu.spec.ts`
- `frontend/src/app/features/recovery/recovery-shell.ts`, `recovery-shell.spec.ts`
- `frontend/src/app/features/recovery/recovery-page.ts`, `.html`, `.scss`, `recovery-page.spec.ts`
- `frontend/src/app/features/recovery/recovery-history.options.ts`, `recovery-history.options.spec.ts`
- `frontend/src/app/testing/rings-history-fixtures.ts` (and `nav-menu-stub.ts` if used)

**Frontend (edited)**
- `frontend/src/app/app.routes.ts`, `frontend/src/app/app.spec.ts` (route test)
- `frontend/src/app/core/api/rings.models.ts`, `rings.service.ts`, `rings.service.spec.ts`
- `frontend/src/app/shared/chart/palette.ts`
- `frontend/src/app/features/running-dashboard/running-dashboard-page.html`, `.ts` (+ import), `.scss` (header rules moved out)
- `frontend/src/styles.scss` (header rules)
- `running-dashboard-page*.spec.ts` and `dashboard-shell.spec.ts` (router provider or stub)

## Edge cases
| Case | Expected behaviour |
|---|---|
| No Garmin key for this person | 200 with all-null days. All three cards show their empty text, and the header and menu work |
| No data at all in the range (watch not worn, new user) | Same as above: three empty cards, no zeros |
| Partly missing days | Gaps in the lines, missing bars. A lone day between gaps shows as a symbol |
| Strain truly 0.0 | Plotted at 0, not a gap (AC5.5) |
| HRV present but fewer than 3 HRV values in the prior 30 days | Recovery is null (gap) for that day, by 020's rule |
| Key set, no LTHR | Strain is null for every day (empty Strain card). Sleep and Recovery work |
| Today | Included. Strain is partial, and Sleep and Recovery may be null until the watch syncs |
| Backfill not finished yet (429 or first minutes after deploy) | Older days show as gaps until later refreshes fill them. No error state |
| Garmin late upload for a past day already stored as empty | Not re-fetched unless incomplete, today or yesterday (020 limit, unchanged) |
| Time zones / DST | Dates are Garmin's local `calendarDate`, one entry per date. `today` uses the server zone |
| Units / conversion | None: percentages and 0–21 strain from the backend. No metres, pace or GPS |
| Manual activities / activities without GPS or HR | Not relevant: no activity data is read |
| Range change while a request is pending | The older request is cancelled, and only the latest result shows |
| Person flip on Recovery | The page is recreated, the range resets to 30, and the old request is cancelled |
| Marina not configured, switch set to Marina | 404 `person_not_configured` → error state with Retry (as other sections) |
| Deep link `/recovery` in production | Served by Ktor's SPA fallback. `/api/...` unknown paths stay 404 |
| Menu open while the person is flipped | The page and menu are recreated closed |

## Privacy
- **Data fields & sensitivity:** daily Sleep %, Recovery % and zone, and Strain, for two people. These are derived health data (GDPR Art. 9), from HRV, resting HR, sleep duration and all-day HR. No raw HR series, GPS or location.
- **Storage & retention:** the same tables as 015 and 020, each person in their own SQLite file. Retention grows from 90 days to 92 days (`ring_days`) and 120 days (`wellness_days`). The new `wellness_state.history_days` is a number, not personal data. The browser holds the data in memory only. Nothing goes to `localStorage` or `sessionStorage` (the range isn't stored).
- **Scopes:** no Strava scopes. The existing per-person Garmin Personal API key is used read-only, with no new endpoint.
- **Applicable privacy-check items:**
  - 1 ✅ No new secrets. Fixtures are invented.
  - 2 ✅ The Garmin key and token are unchanged and never sent to the browser.
  - 3 ✅ The existing token refresh on 401 is used, and a rejected key stops the loop.
  - 4 n/a No OAuth flow.
  - 5 ✅ No Strava scopes.
  - 6 ✅ Only the existing derived fields are stored, plus a `history_days` counter. The response holds only chart-ready numbers.
  - 7 n/a No GPS.
  - 8 ✅ No new log lines with values. Backfill logs stay counts and statuses (AC4.7). No `console` output of data.
  - 9 ✅ No client logging plugin change.
  - 10 ✅ Fixed error shapes, and the 400 message is fixed text.
  - 11 ⚠ Existing: no session guard since 021. The app is reachable on the home network only. This feature adds no new exposure beyond one more read endpoint.
  - 12 n/a No cookie change.
  - 13 ✅ Only numbers, dates and fixed labels are rendered. The ECharts tooltip formatter builds its HTML from dates and numbers only. No `[innerHTML]`.
  - 14 n/a A GET only, with no state change.
  - 15 ✅ Only the fields the graphs need. Same origin, no CORS.
  - 16 ✅ No new third party or CDN.
  - 17 ✅ No Strava data.
  - 18 ❌ Existing gap (015, 019, 020): no in-app delete. Delete the person's DB file, or wait for the prune.
  - 19 n/a
  - 20 ✅ DB backups already treated as personal data. They now hold up to 120 days.
  - 21 ✅ Invented fixtures only.

## Test plan
| AC | Test type | Test name |
|---|---|---|
| AC1.1, AC1.3 | frontend component (NavMenu) | `R1 - menu button opens panel with two links` |
| AC1.2 | frontend component | `R1 - panel is a modal dialog and button is expanded` |
| AC1.4 | frontend component (with router) | `R1 - current page link is marked active` |
| AC1.1, AC1.5 | frontend component (Performance page + Recovery page) | `R1 - header has menu first and keeps switch and bell` |
| AC2.1, AC2.2 | frontend component (router harness) | `R2 - picking an entry navigates and closes the panel` |
| AC2.3 | frontend component | `R2 - escape, backdrop and close button close and refocus` |
| AC2.4, AC2.5 | frontend component | `R2 - focus moves to first link and closed panel is not rendered` |
| AC3.1 | existing Performance page specs pass unchanged + `R3 - performance page sections unchanged` | — |
| AC3.2–AC3.4 | frontend (app routes) | `R3 - routes map / and /recovery and redirect unknown paths` |
| AC3.2 | backend route (existing SPA test) | `R3 - deep link /recovery serves index.html` |
| AC4.1, AC4.2 | backend unit (RingsSync + fake) | `R4 - fetches missing days back to today minus 92`, `R4 - prune keeps 92 days` |
| AC4.3, AC4.4 | backend unit (WellnessPolicy, WellnessSync) | `R4 - full 120-day fetch until history is backfilled`, `R4 - success records history days` |
| AC4.4 | backend persistence | `R4 - history_days column added without changing rows` |
| AC4.5 | backend unit | `R4 - failed backfill keeps rows and history unset` |
| AC4.6 | backend route | `R4 - wellness and rings responses unchanged by older rows` |
| AC5.1 | backend route | `R5 - history returns one entry per day ascending` |
| AC5.2 | backend route | `R5 - invalid days gives 400` |
| AC5.3 | backend route | `R5 - today matches rings endpoint` |
| AC5.3–AC5.6 | backend unit (RingsCalculator.history) | `R5 - missing values are null never zero`, `R5 - real zero strain kept`, `R5 - today included` |
| AC5.7 | backend route | `R5 - no garmin key gives all nulls` |
| AC5.8 | backend route | `R5 - history follows X-Person` |
| AC5.9 | backend route | `R5 - history makes no external calls` |
| AC5.10 | backend unit (fake repos) | `R5 - reads from first day minus 31` |
| (service) | frontend service | `R5 - getHistory calls GET /api/rings/history with days` |
| AC6.1 | frontend component (RecoveryPage) | `R6 - three cards in order under header and picker` |
| AC6.2, AC6.3 | frontend options unit | `R6 - one category per day and nulls stay gaps` |
| AC6.4, AC6.5 | frontend options unit | `R6 - tooltip lines and aria labels` |
| AC7.1–AC7.4 | frontend options unit | `R7 - sleep line 0-100`, `R7 - recovery bars coloured by zone`, `R7 - strain line 0-21`, `R7 - no averages or legend` |
| AC8.1, AC8.2 | frontend component | `R8 - picker defaults to 30 days` |
| AC8.3 | frontend component | `R8 - range change reloads and latest wins` |
| AC8.4 | frontend component | `R8 - range is not stored` |
| AC9.1 | frontend component | `R9 - shows loading` |
| AC9.2 | frontend component | `R9 - shows error with retry` |
| AC9.3 | frontend component | `R9 - empty graph shows no data text` |
| AC9.4 | frontend component | `R9 - renders three charts` |
| AC9.5 | frontend component | `R9 - reloads quietly when dataVersion changes` |
| AC10.1, AC10.2 | frontend component (RecoveryShell) | `R10 - flip recreates recovery page with new person` |
| AC11.1–AC11.4 | manual at 360 px and 1280 px + SCSS review | `R11 - menu and graphs on mobile and desktop` |

## Out of scope
- New metrics (HRV, resting HR, sleep hours) on Recovery.
- Averages, trend lines or baselines on the graphs.
- Any change to how Sleep, Recovery or Strain are computed.
- Other pages or menu entries.
- Remembering the range picker after a reload.
- Any Strava data.

## Suggested follow-ups
- Keep the range when flipping the person (lift it into the shell).
- Re-fetch recent empty past days so late Garmin uploads are picked up (020 follow-up, still open).
- In-app "delete my Garmin data" (privacy item 18).
- Click a day on a graph to see that day's rings.

## Risks & decisions to confirm
- **Retention grows (please confirm):** `wellness_days` goes from 90 to 120 days and `ring_days` to 92 days. That's the minimum for a full 90-day Recovery history with 020's 30-day HRV / resting HR baselines. It means more health data kept per person.
- **Backfill cost:** about 122 sequential Garmin calls once per person, about 61 without LTHR. The wellness backfill is one `dailysummaries` call over 121 days. The 015 first fetch used 91 days, and Garmin's maximum range for this call is not verified. **Build step 1:** check one 121-day call with the real key. If it's refused, split it into two calls inside `GarminWellnessSource.fetch`. Until the backfill finishes (for example after a 429), older days show as gaps.
- **Recovery page starts the background sync (decision):** syncs are driven by the browser's `SyncStore` (009), which today only runs on Performance. Recovery also provides `SyncStore` and calls `start()`, so opening `/recovery` directly still brings in today's data and reloads after a sync. It shows no banner or spinner. If you'd rather keep Recovery purely read-only, drop AC9.5 and the provider.
- **Range resets on person flip:** this follows from 019's "recreate the page" pattern (AC10.2). Keeping it is a follow-up.
- **Header markup change on Performance:** the menu button and title get a `.header-start` wrapper, and the header CSS moves to `styles.scss`. Visually, only the button is added. The existing page tests are updated only for the router or stub.
- **Missing `days` parameter returns 400, not a default of 30:** the frontend always sends it, so this keeps the contract strict.
- **No auth guard:** like every other route since 021, `/api/rings/history` relies on the home-network deployment (privacy item 11 ⚠).
