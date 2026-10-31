# Spec: Calendar-like marathon build

**Feature ID:** 004 · **Status:** Approved (Revision 2) · **Intent:** [intent.md](./intent.md) · **Date:** 2026-10-30 · **Revised:** 2026-10-31

## Revision 2 changes
This revision follows intent.md "Revision 2" (approved 2026-10-31). Changed or new items are tagged **[Rev 2]** in this file. Everything without the tag is unchanged from the approved spec.
- **Dates:** race day is Sun 6 Dec 2026, so the build runs Mon 17 Aug 2026 → Sun 6 Dec 2026. `TRAINING_PLAN_START_DATE` now defaults to `2026-08-17`, so `.env` doesn't need a value. All dated examples and fixtures move to 2026, and the build is already running (today, 31 Oct 2026, is in week 11). Affects R1, R6, R7, R8, R12.
- **Layout:** the section is split in two. The "Marathon build" card with the progress bar stays at the top under "Running". A new "Build calendar" card (target paces, plan notes, calendar) is the last element of the page, after the workout pace chart. There is still one request, shared through a page-scoped store. Affects R9, R10, R11, R13, R15. The new requirement is R16.
- **Past days:** new `isPast` day flag from the backend. Past days are dimmed with muted colours (no transparency) and crossed with a thin diagonal cross. Affects R7, R8. The new requirement is R17.
- **Today:** stronger highlight, with a 2px accent border and a faint accent tint, plus the existing "Today" badge. Affects R12 (AC12.6).
- **Day tiles and hover:** every day is a bordered square tile (at 720 px and wider). On mouseover it gets a slightly lighter background and a brighter border, with a short transition. Hover only, no click. Affects R14. The new requirement is R18.

## Summary
The backend reads the user's training-plan CSV (path from `TRAINING_PLAN_CSV`) on every request. It parses the target paces, the sheet notes and the week blocks, and gives each block real dates (week 1 starts on Monday 17 Aug 2026 by default). It works out the build progress for today and which days have passed, and returns everything, ready to display, from `GET /api/training-plan`. **[Rev 2]** On the dashboard page, a "Marathon build" card with the progress bar sits at the top under the "Running" heading. A "Build calendar" card with the target paces, notes and the 16-week Monday-to-Sunday calendar is the last section of the page. Both cards share one request and fail independently of the running charts. Races, the marathon, rest days, past days (dimmed and crossed), today and the current week are highlighted, and day tiles highlight on mouseover.

## Requirements
| ID | Requirement | Priority (Must/Should/Could) |
|---|---|---|
| R1 | **[Rev 2]** Config: optional `TRAINING_PLAN_CSV` (file path) and `TRAINING_PLAN_START_DATE` (ISO date, **default `2026-08-17`**, must be a Monday). The app starts without them, and the CSV is never committed | Must |
| R2 | The backend reads the CSV on every request: semicolon-separated, quoted fields, decimal commas, UTF-8 (BOM allowed) with a Windows-1252 fallback, 1 MB limit. The file's path and contents are never logged | Must |
| R3 | Parse the header block into target paces (every `… PACE` column: target plus optional min–max) and the sheet notes (the column after `NOTES`) | Must |
| R4 | Parse every week block (`WEEK N` / `RACE WEEK`) into a week label, total km, easy % and subT % (as written), and 7 days, each with the session text, day note, minutes and km | Must |
| R5 | Find rest days and races (any `NK RACE` / `RACE NK` cell). A race of 42 km or more is the marathon | Must |
| R6 | **[Rev 2: dates]** Dates: block *i* covers `start + 7·(i−1)` to `start + 7·(i−1) + 6`, Monday to Sunday. The build end is the last block's Sunday (6 Dec 2026 with the defaults) | Must |
| R7 | **[Rev 2: `isPast`]** Progress for today (server time zone): phase, % of days passed (floor), current week, days to go or days until start, and a ready-made label. `isToday`, **`isPast`** and `isCurrentWeek` flags | Must |
| R8 | **[Rev 2: `isPast` field]** `GET /api/training-plan` returns the exact JSON below. It requires a connected session. Not configured → 404, unreadable → 503, invalid → 422 with a safe message | Must |
| R9 | **[Rev 2: changed]** Placement: the "Marathon build" card (`app-calendar-like-build`, progress bar) sits at the top of the dashboard page. The "Build calendar" card (`app-build-calendar-section`: paces, notes, calendar) is the last element of the page. They share one request and show and fail independently of the running dashboard | Must |
| R10 | A progress bar with an accent fill and the backend label, inside the top card | Must |
| R11 | **[Rev 2: moved]** A target-paces panel (and the sheet notes) inside the bottom "Build calendar" card, above the calendar | Must |
| R12 | **[Rev 2: today style, dates]** Calendar: one row per week (label, km, easy/subT %) with 7 day tiles (date, session, note, minutes, km). Rest days, races, the marathon (most prominent), today (accent border + tint + badge) and the current week are marked. The calendar opens scrolled to the current week | Must |
| R13 | **[Rev 2: changed]** Loading, empty (not configured), error (unreadable / invalid / other) and normal states, shown in the top card. The bottom card is rendered only in the normal state | Must |
| R14 | **[Rev 2: square tiles]** Dark theme tokens from 002 only. Day tiles are bordered squares at 720 px and wider. Usable at 375 px and desktop widths, with no horizontal page scroll | Must |
| R15 | **[Rev 2: stub setup]** No regression: 001/002/003 behaviour, `GET /api/dashboard/running` and its JSON are unchanged. No Strava calls or scopes are added | Must |
| R16 | **[Rev 2: new]** One shared plan load: a page-scoped `TrainingPlanStore` makes the single `GET /api/training-plan` request and holds the state that both cards read. Retry goes through the store | Must |
| R17 | **[Rev 2: new]** Past days (date before today) are dimmed with muted, opaque colours and get a thin diagonal cross. Future days look as before | Must |
| R18 | **[Rev 2: new]** Each day tile highlights slightly on mouseover (lighter background, brighter border, short transition). Hover only: tiles are not clickable or focusable | Must |

## Acceptance criteria

**Conventions used below.** Columns are 0-based. Day *d* (0 = Monday … 6 = Sunday) uses the **primary column** `p = 2 + 2d` and the **secondary column** `s = 3 + 2d`. A "number" is a trimmed cell that matches `^-?\d+([.,]\d+)?$`, read with `,` treated as `.`. An empty cell reads as "no value". The test fixtures `plan-sample.csv` (3 blocks: `WEEK 1`, `WEEK 2`, `RACE WEEK`) and `plan-16-weeks.csv` (15 numbered blocks + `RACE WEEK`) use the CSV layout but **invented** values. **[Rev 2]** Dated test expectations use the 2026 dates below. The fixtures' cell values don't change.

### R1 — Configuration
- **AC1.1** Given `TRAINING_PLAN_CSV` is unset or blank, when the app starts, then it starts normally and `AppConfig.trainingPlanCsvPath` is `null`.
- **AC1.2 [Rev 2]** Given `TRAINING_PLAN_START_DATE` is unset or blank, then `AppConfig.trainingPlanStartDate` is `2026-08-17` (`DEFAULT_TRAINING_PLAN_START`). Given `2026-08-17`, it parses. Given `2026-08-18` (a Tuesday), startup fails with "TRAINING_PLAN_START_DATE must be a Monday". Given `17/08/2026`, startup fails with "TRAINING_PLAN_START_DATE must be an ISO date (YYYY-MM-DD)".
- **AC1.3** Given any config, when `AppConfig.toString()` is called, then it contains `trainingPlanCsv=<set>` or `trainingPlanCsv=<unset>` and never the path itself.
- **AC1.4 [Rev 2]** Given `.env.example`, then it lists `TRAINING_PLAN_CSV=` and `TRAINING_PLAN_START_DATE=` with no values, and the comment for `TRAINING_PLAN_START_DATE` says it is optional and defaults to `2026-08-17`. Given `.gitignore`, then it ignores `/*.csv` and `/backend/*.csv`, but not `backend/src/test/resources/**`.

### R2 — Reading the file
- **AC2.1** Given a configured path, when `GET /api/training-plan` is called twice and the file is changed between the calls, then the second response reflects the change. Nothing is cached and no restart is needed.
- **AC2.2** Given the file starts with a UTF-8 BOM, then the first cell reads without the BOM. Given a file that is not valid UTF-8 (Windows-1252 bytes such as `0xE7` for "ç"), then it is decoded as Windows-1252 and parsing succeeds.
- **AC2.3** Given a quoted cell `"5x15 - rest 2'30"""`, when it is split, then the value is `5x15 - rest 2'30"`. Semicolons inside quotes are not separators. Rows may have any number of cells, and a missing trailing cell reads as empty.
- **AC2.4** Given the path doesn't exist, is a directory or can't be read, then the source returns `Unreadable`. One WARN line is logged: `Training plan file could not be read (TRAINING_PLAN_CSV): <ExceptionClass>`. It has no path, no file content and no stack trace.
- **AC2.5** Given a file larger than 1 MB (`MAX_PLAN_FILE_BYTES = 1_048_576`), then the result is `Invalid("The plan file is larger than 1 MB.")`.
- **AC2.6** Given any parse failure, then at most one WARN line is logged with the safe reason from R4 (week label + row/day name). It never contains cell values.

### R3 — Target paces and sheet notes
The **header block** is every row before the first week header row (R4).
- **AC3.1** Given a header-block cell at column *c* whose trimmed upper-case text matches `^(.+?)\s+PACE\b`, then it becomes a target pace. Its `label` is the matched group (`EASY` is shown as `Easy`, anything else as written, e.g. `15K`, `HM`, `30K`), and its `key` is the label in lower case. A `5K RACE TIME` cell is not a pace.
- **AC3.2** Given the header has no `(min-max)` (e.g. `EASY PACE`), when the next row has `0:05:38` at column *c*, then `targetSecondsPerKm = 338`, and `minSecondsPerKm` and `maxSecondsPerKm` are `null`.
- **AC3.3** Given the header contains `(min-max)`, when the next row has `0:04:00` at *c* and `0:04:10` at *c+1*, and the row after that has `0:04:05` at *c*, then `min = 240`, `max = 250` and `target = 245`. If the third cell is empty, then `target` is `null`.
- **AC3.4** Given pace values in the form `h:mm:ss` or `m:ss`, then they convert to whole seconds per km. Given a value that can't be parsed, then that pace is left out (WARN with the label only) and the rest of the plan still loads.
- **AC3.5** Given a header-block cell whose trimmed upper-case text is `NOTES` at column *n*, then `notes` is the non-empty trimmed cells in column *n+1*, from that row to the end of the header block, in row order. With no `NOTES` cell, `notes` is `[]`.
- **AC3.6** Given the paces keep the order of their columns, then the fixture gives `["easy", "15k", "hm", "30k"]`.

### R4 — Week blocks
- **AC4.1 (block detection)** Given a row whose column 0, trimmed and in upper case, matches `^WEEK\s+\d+$` or equals `RACE WEEK`, then a block starts there and runs until the next such row or the end of the file. Blocks are kept in file order. Their `index` is 1, 2, 3… in that order, and their `label` is column 0 as written (`WEEK 1`, `RACE WEEK`).
- **AC4.2 (block rows)** Within a block: the header row is the block's row 0, the **type row** is row 1 and the **session row** is row 2. The **EASY**, **SUBT** and **VOLUME** rows are the rows in the block whose column 0, in upper case, equals `EASY`, equals `SUBT`, or starts with `VOLUME`. If any of these rows is missing, the result is `Invalid("<label>: <ROW> row missing")`, e.g. `"WEEK 3: VOLUME row missing"`.
- **AC4.3 (day headers)** Given header-row cell *p* for day *d*, then its text in upper case must start with the weekday name (`MONDAY` … `SUNDAY`). Otherwise the result is `Invalid("<label>: expected <WEEKDAY> in column <p>")`. Text inside the first `(` and the last `)` becomes the day's `note` (trimmed), e.g. `TUESDAY (75min progression)` → `"75min progression"`. No parentheses means `note = null`.
- **AC4.4 (week totals as written)** Given VOLUME column 1 = `62,40`, EASY column 1 = `79,25` and SUBT column 1 = `20,75`, then `distanceKm = 62.4`, `easyPercent = 79.25` and `subtPercent = 20.75` (rounded half-up to 2 decimals). They are not recomputed from the days. If one of them is not a number, the result is `Invalid("<label>: <ROW> total is not a number")`.
- **AC4.5 (day numbers)** For day *d*: `easyMinutes` = EASY row cell *p*, `subtMinutes` = SUBT row cell *p* (whole minutes, rounded half-up), `totalMinutes = easyMinutes + subtMinutes`, and `distanceKm` = VOLUME row cell *p* (2 decimals, half-up). An empty cell counts as 0. A non-empty cell that isn't a number gives `Invalid("<label>, <WEEKDAY>: <ROW> value is not a number")`.
- **AC4.6 (session text)** Let `W` = session-row cell *p*, `M` = session-row cell *s*, `T1` = type-row cell *p* and `T2` = type-row cell *s*. The **kind** is `easy` if `T2` (upper case) starts with `EASY`, `subT` if it starts with `SUBT`, and none otherwise. Then, unless R5 makes the day a rest day or a race-header day:
  - The WU/CD part: `W` a number > 0 → `"<W>' WU/CD"`. `W` = 0 or empty → no part. `W` is other text → `"<W> WU/CD"`.
  - The main part: `M` empty or `0` → no part. `M` a number → `"<M>' <kind>"`. `M` contains `RACE` → `M` as written. Any other text → `"<M> <kind>"`. With no kind, the trailing ` <kind>` is left out.
  - `session` = the parts joined with `" + "`. With no parts, it is `""`.
  - Examples: `W=0, T2="EASY [min]", M="60"` → `"60' easy"`; `W="25", T2="SUBT [min]", M="3x10'"` → `"25' WU/CD + 3x10' subT"`; `W="30", T2="SUBT [min]", M="45"` → `"30' WU/CD + 45' subT"`; `W="25", M="5K RACE"` → `"25' WU/CD + 5K RACE"`.
- **AC4.7** Given a valid file, then every block has exactly 7 days in Monday-to-Sunday order. Given a file with no block, the result is `Invalid("No week blocks found (expected rows starting with WEEK 1 … RACE WEEK).")`.

### R5 — Rest days and races
- **AC5.1 (race detection)** Given `T1` or `M` contains `RACE` (any case) and a distance matching `(\d+(?:[.,]\d+)?)\s*K\b`, then `race = { distanceKm, isMarathon, label }`. `isMarathon = distanceKm >= 42.0` (`MARATHON_MIN_KM`). `label` is `"5K race"` / `"10K race"` (integer distances without decimals, e.g. `"21.1K race"` otherwise), or `"Marathon · 42.2 km"` for a marathon. Days that aren't races have `race = null`.
- **AC5.2 (race header day)** Given `T1 = "RACE 42,2K"`, then `session = "RACE 42,2K"` (as written), whatever `W` and `M` contain, and the day is a marathon with `distanceKm = 42.2`. Its minutes and km still come from the EASY, SUBT and VOLUME rows.
- **AC5.3 (rest day)** Given a day that is not a race, with `distanceKm == 0` and `totalMinutes == 0`, then `isRest = true` and `session = "Rest"`. This applies even when `M` still holds text such as `4x15'` (the leftover text is not shown). Otherwise `isRest = false`.
- **AC5.4** Given the fixture's `WEEK 1` Sunday `W="25", M="5K RACE"`, then `race.label = "5K race"`, `isMarathon = false` and `session = "25' WU/CD + 5K RACE"`.

### R6 — Dates
- **AC6.1 [Rev 2]** Given `trainingPlanStartDate = 2026-08-17` and `plan-16-weeks.csv`, then `startDate = "2026-08-17"`, `endDate = "2026-12-06"` and there are 16 weeks. Week 1 runs from `2026-08-17` to `2026-08-23`, and `RACE WEEK` (index 16) runs from `2026-11-30` to `2026-12-06`. Its Sunday has `date = "2026-12-06"`, and that is the marathon day.
- **AC6.2** Given any block count *n*, then `endDate = start + 7n − 1` days. Every day's `date` is `start + 7·(index−1) + d`, and the dates are consecutive with no gaps.
- **AC6.3** Given the CSV gains or loses a block, then the dates and `endDate` follow on the next request, with no code change.

### R7 — Progress
`today = LocalDate.now(clock.withZone(zone))` (the injected clock and the server time zone, as in 001). `totalDays = 7 · weekCount`. `daysPassed = clamp(DAYS.between(startDate, today), 0, totalDays)`, so today counts as passed only once it is over. `percentComplete = floor(daysPassed · 100 / totalDays)`. These examples use the 16-week fixture with start `2026-08-17`. **[Rev 2]** All example dates are new.
- **AC7.1 (in progress) [Rev 2]** Given today = `2026-10-31` (Saturday), then `phase = "inProgress"`, `daysPassed = 75`, `percentComplete = 66`, `currentWeekIndex = 11`, `daysToGo = 36` (days from today to `endDate`), `daysUntilStart = null` and `label = "66% · week 11 of 16 · 36 days to go"`. Week 11 has `isCurrentWeek = true`, and only the day `2026-10-31` has `isToday = true`.
- **AC7.2 (first day) [Rev 2]** Given today = `2026-08-17`, then the label is `"0% · week 1 of 16 · 111 days to go"` and the phase is `inProgress`.
- **AC7.3 (one day / race day) [Rev 2]** Given today = `2026-12-05`, then the label is `"98% · week 16 of 16 · 1 day to go"`. Given today = `2026-12-06`, then `percentComplete = 99`, `daysToGo = 0` and the label is `"99% · week 16 of 16 · race day"`. If the last day is not a marathon, the label ends with `"last day"` instead.
- **AC7.4 (before) [Rev 2]** Given today = `2026-05-01`, then `phase = "notStarted"`, `percentComplete = 0`, `daysPassed = 0`, `daysUntilStart = 108`, `currentWeekIndex = null`, `daysToGo = null` and `label = "0% · starts in 108 days"` (`"starts in 1 day"` on `2026-08-16`). No week is current and no day is today.
- **AC7.5 (after) [Rev 2]** Given today = `2026-12-07`, then `phase = "finished"`, `percentComplete = 100`, `daysPassed = 112`, `currentWeekIndex = null`, `daysToGo = null`, `daysUntilStart = null` and `label = "100% · build complete"`. No week is current and no day is today.
- **AC7.6 [Rev 2]** Given the server zone is `Europe/Lisbon` and the clock is `2026-10-20T23:30:00Z` (00:30 local on 21 Oct, summer time), then today is `2026-10-21`. The zone is the server's, not the browser's.
- **AC7.7 (past days) [Rev 2: new]** Every day has `isPast = (date < today)`, using the same `today` as above. `isPast` and `isToday` are never both true. Given today = `2026-10-31`, then the 75 days from `2026-08-17` to `2026-10-30` have `isPast = true`, and `2026-10-31` and every later day have `isPast = false`. So the number of past days equals `daysPassed`. Given today = `2026-05-01` (before), no day is past. Given today = `2026-12-07` (after), all 112 days are past. Rest days and race days follow the same rule.

### R8 — API
- **AC8.1** Given a connected session and a valid file, when `GET /api/training-plan` is called, then the response is `200` with exactly the JSON fields in the Design section (no extra fields, no file path). **[Rev 2]** This now includes `isPast` on every day.
- **AC8.2** Given no session cookie, or a session for another athlete, then the response is `401 {"error":"not_connected", …}`, as for the 001 route.
- **AC8.3** Given `TRAINING_PLAN_CSV` is not configured, then the response is `404 {"error":"training_plan_not_configured","message":"No training plan is configured."}`.
- **AC8.4** Given the file can't be read (AC2.4), then the response is `503 {"error":"training_plan_unavailable","message":"The training plan file could not be read."}`.
- **AC8.5** Given the file is invalid (AC2.5, AC4.2–AC4.7), then the response is `422 {"error":"training_plan_invalid","message":"<safe reason>"}`, e.g. `"WEEK 3: VOLUME row missing"`. The message never contains the path or cell values.
- **AC8.6** Given any request to this route, then no Strava HTTP call is made and the sync is not triggered.

### R9 — Placement and independence [Rev 2: changed]
- **AC9.1 [Rev 2]** Given the dashboard page in any state, then `app-calendar-like-build` (the "Marathon build" card) is rendered directly after the `h1` "Running" and before the sync banner, tiles, loading, error and empty elements of the running dashboard.
- **AC9.2 [Rev 2: new]** Given the dashboard page in any state, then `app-build-calendar-section` is the last child element of the page host. It comes after every element of the running dashboard's `@switch`. In the loaded, non-empty dashboard state, that means after `app-workout-pace-comparison-chart`.
- **AC9.3 [Rev 2]** Given the page loads, then exactly two GET requests are made: `/api/dashboard/running` and `/api/training-plan` (once, even though two plan components render). Neither waits for the other.
- **AC9.4** Given `/api/training-plan` fails with 404, 422, 503 or 500, then the running dashboard still renders its own states normally. Given `/api/dashboard/running` fails or is still loading, then the plan cards still render their own states (the top card's state, plus the bottom card once the plan has loaded).
- **AC9.5** Given `/api/training-plan` returns 401, then the shared interceptor handles it (redirect to `/connect`), and neither plan card shows error text.

### R10 — Progress bar
- **AC10.1 [Rev 2]** Given a normal response, then the top card's heading `h2` "Marathon build" is first, followed by an element with `role="progressbar"`, `aria-valuemin="0"`, `aria-valuemax="100"`, `aria-valuenow="<percentComplete>"` and `aria-valuetext="<label>"`, and the visible label text equals `progress.label`. The top card holds nothing else in the normal state (no paces, notes or calendar).
- **AC10.2** Given `percentComplete = 66`, then the fill's inline width is `66%`. Given 0, the width is `0%` (the track is still visible). Given 100, the width is `100%`.
- **AC10.3** The fill uses `--color-accent` on a `--color-surface-raised` track. The label uses the large-number style (at least `1.25rem`, weight 700, `tabular-nums`).

### R11 — Target paces and notes [Rev 2: moved]
- **AC11.1** Given `targetPaces`, then a panel titled "Target paces" (small uppercase label) lists them in order. A pace with no range shows `"Easy"` and `"5:38 /km"`. A pace with a range shows `"15K"`, `"4:00–4:10 /km"` and `"target 4:05"`, or no target line when `targetSecondsPerKm` is `null`. All formatting uses the existing `formatPace`.
- **AC11.2** Given `notes` is not empty, then a "Plan notes" list shows each note as plain text, in order. Given `[]`, the list is not rendered.
- **AC11.3** Given `targetPaces` is `[]`, then the panel is not rendered and the calendar still renders.
- **AC11.4 [Rev 2: new]** Given a normal response, then the paces panel and the notes are inside `app-build-calendar-section`, after its heading and before the calendar, and they are not inside the top card.

### R12 — Calendar
- **AC12.1 [Rev 2]** Given 16 weeks, then 16 week rows render in `index` order, each with its 7 days Monday to Sunday. The first day tile shows `Mon 17 Aug` and the last shows `Sun 6 Dec`.
- **AC12.2 (week summary)** Given a week, then its row shows the `label` as written (e.g. `WEEK 1`, `RACE WEEK`), `"62.40 km"` and `"79.25% easy · 20.75% subT"`. Values are shown with 2 decimals and a `.` decimal point.
- **AC12.3 (day tile) [Rev 2: date example]** Given a day that is not a rest day, then the tile shows the date (`Tue 18 Aug`), the `session` text, the `note` (if any, as a separate muted line), `"<totalMinutes> min"` and `"<distanceKm with 2 decimals> km"`. Given `session = ""`, the session line is not rendered.
- **AC12.4 (rest)** Given `isRest = true`, then the tile shows the date and "Rest" in `--color-text-muted`, has the class `rest`, and shows no minutes or km.
- **AC12.5 (races)** Given a 5K or 10K race day that is not past, then the tile has the class `race`, a `--color-accent` 1px border and a badge with `race.label` (`5K race`). Given the marathon day (not past), then the tile has the classes `race marathon`, a `--color-accent` background with `--color-bg` text, a larger session text (at least `1.1rem`, weight 700) and the badge `Marathon · 42.2 km`. It is the only tile styled this way. **[Rev 2]** A past race keeps its classes and badge but uses the past styling (AC17.2).
- **AC12.6 (today) [Rev 2: changed]** Given a day with `isToday = true`, then the tile has the class `today`, `aria-current="date"` and a `Today` badge, as before. It is now styled with a 2px `--color-accent` border (replacing the old 2px `--color-text` outline) and a faint accent tint as its background: `color-mix(in srgb, var(--color-accent) 12%, var(--color-surface))`. The result is opaque, and `--color-text` on it still reaches 4.5:1. The `Today` badge text uses `--color-accent`. When today is the marathon day, the tile keeps the marathon accent background and gets a 2px `--color-text` border instead. No other tile has these.
- **AC12.7 (current week)** Given `isCurrentWeek = true`, then that week row has the class `current-week`, `aria-current="true"`, a `--color-surface-raised` background and a `This week` tag next to its label. No other row has these.
- **AC12.8 (scroll to current week)** The calendar is a scroll container (`max-height: 34rem; overflow-y: auto`). After the first render in the normal state, its `scrollTop` is set so that the row returned by `scrollTargetWeekIndex(progress, weeks)` sits at the top. That row is: the current week when `inProgress`, week 1 when `notStarted`, and the last week when `finished`. The page itself is not scrolled. **[Rev 2]** This still applies now that the calendar is at the bottom of the page. Only the container's `scrollTop` is set (no `scrollIntoView`), so the page doesn't jump down to it on load.
- **AC12.9 (XSS)** Given a session or note containing `<b>x</b>`, then the literal text `<b>x</b>` is shown. Nothing is rendered as HTML.

### R13 — Section states [Rev 2: changed]
All non-normal states are shown in the top "Marathon build" card. The bottom card is not rendered in them, so there is never a second message or Retry button.
- **AC13.1 (loading)** Given the request is pending, then the top card shows the heading "Marathon build" and "Loading your plan…" in a `role="status"` element, and no bar. **[Rev 2]** `app-build-calendar-section` renders nothing.
- **AC13.2 (empty)** Given a `404 training_plan_not_configured`, then the top card shows "No training plan set up yet." and the muted hint "Set TRAINING_PLAN_CSV in .env to the path of your plan CSV, then reload." It has no Retry button. **[Rev 2]** The bottom card renders nothing.
- **AC13.3 (error: unreadable)** Given a `503 training_plan_unavailable`, then the top card shows "Couldn't read your training plan file." in a `role="alert"` element, with a Retry button in the 002 style. Retry calls `TrainingPlanStore.retry()`, which sends the request again and puts both cards into the loading state. **[Rev 2]** The bottom card renders nothing, and the page has exactly one plan Retry button.
- **AC13.4 (error: invalid)** Given a `422 training_plan_invalid` with message `M`, then the top card shows "Your training plan file has a problem: M" (interpolated as text) in `role="alert"`, with Retry. The bottom card renders nothing.
- **AC13.5 (error: other)** Given a 500, a network error or an unexpected body, then the top card shows "Couldn't load your training plan." in `role="alert"`, with Retry. The bottom card renders nothing.
- **AC13.6 (normal) [Rev 2: changed]** Given `200`, then the top card shows heading → progress bar. The bottom card (`section.card` labelled by its `h2` "Build calendar") shows heading → target paces + notes → calendar.

### R14 — Look and layout
- **AC14.1** Given the section's styles, then every colour is a `var(--color-*)` token from 002 (002 AC1.4 still holds: no colour literals in component styles), and `--color-worse` is not used. **[Rev 2]** `color-mix()` is allowed only when every colour argument is a `var(--color-*)` token. No `opacity` below 1 is used on text or tiles (002 AC10.4).
- **AC14.2 [Rev 2]** Both plan cards use the 002 card look (`--color-surface` background, 1px `--color-border` border, 12px radius, no shadow). The bottom card is separated from the chart cards above it by the same vertical gap as the 002 cards. Small labels (week labels, the "Target paces" title, badges) use uppercase, `letter-spacing: 0.08em`, `0.75rem` and `--color-text-muted`, except on the marathon and today tiles (AC12.5, AC12.6).
- **AC14.3 [Rev 2: changed]** Given a viewport at least 720 px wide, then each week row is a grid with a summary column plus 7 equal day columns, the paces sit side by side, and each day tile is a square: `aspect-ratio: 1 / 1`. A tile may grow taller only if its content doesn't fit, and then text wraps and is never clipped. Given 375 px, then each week shows its summary and then its 7 day tiles stacked one per line (date left, session right) as full-width bordered tiles that are not forced square, the paces wrap, and the page has no horizontal scroll.
- **AC14.4 (tile box) [Rev 2: new]** Every day tile has a 1px `--color-border` border (instead of the old transparent border), an 8px radius and a `--color-surface` background, unless rest, race, marathon, today or past styling overrides it as described in AC12.4–AC12.6 and R17.

### R15 — No regression
- **AC15.1** Given the diff, then `RunningDashboardRoutes.kt`, `RunningDashboardDtos.kt`, `running-dashboard.models.ts`, `running-dashboard.service.ts` and `api-error.interceptor.ts` are unchanged, and no file under `backend/src/main/kotlin/app/stravadashboard/strava/` changes.
- **AC15.2 [Rev 2]** Given the existing 001/002/003 frontend page specs, then they pass with only a setup change. Both `CalendarLikeBuild` and `BuildCalendarSection` are replaced by stub components through `TestBed.overrideComponent(RunningDashboardPage, …)`. Their assertions are unchanged. With both stubbed, nothing injects `TrainingPlanStore`, so no plan request is made. (002 AC4.5, "exactly one request", is read as "the tiles add no request"; the stubs keep that test as it is.)
- **AC15.3** Given the backend and frontend check commands, then everything passes.

### R16 — Shared plan load [Rev 2: new]
- **AC16.1** Given `TrainingPlanStore` is `@Injectable()` and listed in `RunningDashboardPage`'s `providers` (page-scoped, not `providedIn: 'root'`), when both plan components render, then `HttpTestingController` sees exactly one `GET /api/training-plan`.
- **AC16.2** Given the store, then it exposes `state: Signal<PlanState>` (the existing `PlanState` union), `ensureLoaded()` and `retry()`. `ensureLoaded()` sends the request only the first time it is called, and both components call it in `ngOnInit`. `retry()` always sets `loading` and sends a new request.
- **AC16.3** Given the store's response handling, then it is the logic that `CalendarLikeBuild` has today, moved as it is: `isTrainingPlanResponse` → `loaded`, otherwise `planErrorState`, and a 401 leaves the state unchanged (AC9.5). Given the page is destroyed while a request is pending, then the subscription is cancelled (`takeUntilDestroyed` with the store's `DestroyRef`).
- **AC16.4** Given the page is left and opened again, then a new store is created and the plan is requested again (no caching across visits, consistent with AC2.1).

### R17 — Past days [Rev 2: new]
- **AC17.1** Given a day with `isPast = true`, then its tile has the class `past`. Given `isPast = false`, then it doesn't. Given today = `2026-10-31`, then exactly 75 tiles have `past` and the `today` tile is not one of them.
- **AC17.2 (dimmed)** Given a `past` tile, then its background is `--color-bg`, its border is 1px `--color-border`, and all its text (date, session, note, numbers, badges) uses `--color-text-muted`. No `opacity` or transparent colour is used. This applies to past rest days, and to past race and marathon tiles too: the race accent border and the marathon accent background are replaced by the past styling, and the race badge text stays visible.
- **AC17.3 (cross)** Given a `past` tile, then a thin diagonal cross (two 1px lines, corner to corner) in `--color-secondary` is drawn behind its text. It is drawn with CSS only (`background-image` with two `linear-gradient`s, or a `::before`/`::after` pseudo-element below the content), so no DOM element or text is added and the tile's text stays readable on top.
- **AC17.4** Given a future day (`isPast = false`, `isToday = false`), then its tile looks as specified in AC12.3–AC12.5 and AC14.4, unchanged by this revision apart from the AC14.4 border.
- **AC17.5** Given `phase = "notStarted"`, then no tile is `past`. Given `phase = "finished"`, then every tile is `past`, and the calendar still opens on the last week (AC12.8).

### R18 — Day tile hover [Rev 2: new]
- **AC18.1** Given a pointer device (`@media (hover: hover)`), when the mouse is over a day tile, then the tile's background becomes `--color-surface-raised` and its border becomes `--color-text-muted`. A `past` tile on hover gets a `--color-surface` background and a `--color-text-muted` border, so it stays dimmer than a future tile. A `today` tile keeps its accent border and its background changes to `color-mix(in srgb, var(--color-accent) 20%, var(--color-surface))`. The `marathon` tile keeps its background and gets a `--color-text` border.
- **AC18.2** The change is animated with `transition: background-color 150ms ease, border-color 150ms ease`. No size, position or layout change happens on hover (no `transform`, no border-width change), so the grid doesn't shift.
- **AC18.3** Given a day tile, then it has no click handler, no `tabindex`, no `role="button"` and no `cursor: pointer`. Keyboard focus never lands on tiles.

## Traceability
| Success criterion (intent) | Covered by |
|---|---|
| S1 (section at top, current week visible) | R9 (AC9.1), R10, R12 (AC12.7, AC12.8). **[Rev 2]** Read with S14: the top card shows the progress bar and the current week label ("week 11 of 16"). The calendar opens on the current week within its own container. |
| S2 (bar + label) | R7 (AC7.1–AC7.3), R10 |
| S3 (0% before with days until start, 100% after) | R7 (AC7.4, AC7.5), R10 (AC10.2) |
| S4 (first day of the build → marathon day, Mon–Sun, 16 weeks) **[Rev 2: 17 Aug → 6 Dec 2026]** | R1 (AC1.2), R6, R4 (AC4.7), R12 (AC12.1) |
| S5 (session, minutes, km, rest days) | R4 (AC4.5, AC4.6), R5 (AC5.3), R12 (AC12.3, AC12.4) |
| S6 (week km, easy/subT %) | R4 (AC4.4), R12 (AC12.2) |
| S7 (races highlighted, marathon most prominent) | R5 (AC5.1, AC5.2), R12 (AC12.5) |
| S8 (notes on their day) | R4 (AC4.3), R3 (AC3.5), R11 (AC11.2), R12 (AC12.3); see Risk 1 |
| S9 (target paces) | R3, R11 |
| S10 (today marked) | R7 (AC7.1), R12 (AC12.6) |
| S11 (dark look, desktop + mobile) | R14 |
| S12 (matches CSV, no code change on edit) | R2 (AC2.1), R4 (AC4.4), R6 (AC6.3), R1 |
| **S13 [Rev 2]** (past days dimmed and crossed, today stands out, future unchanged) | R7 (AC7.7), R8 (AC8.1), R17, R12 (AC12.6) |
| **S14 [Rev 2]** (progress bar at top, calendar + paces + notes last) | R9 (AC9.1, AC9.2), R10 (AC10.1), R11 (AC11.4), R13 (AC13.6), R16 |
| **S15 [Rev 2]** (tile highlights on mouseover) | R18, R14 (AC14.3, AC14.4) |
| Decision 1 (plan only) | Out of scope; R8 (AC8.6) |
| Decision 2 (start 16 Aug 2027) | **Superseded by Revision 2 decision 1** |
| Decision 3 (missing CSV → message, rest keeps working) | R8 (AC8.3, AC8.4), R9 (AC9.4), R13 |
| **Rev 2 decision 1** (race 6 Dec 2026, start 17 Aug 2026 as code default) | R1 (AC1.2, AC1.4), R6 (AC6.1), R7 (AC7.1–AC7.7) |
| **Rev 2 decision 2** (bar at top, calendar + paces + notes after the workout pace chart) | R9, R11 (AC11.4), R13, R16 |
| **Rev 2 decision 3** (past dimmed with muted colours, not transparency, plus cross; past rest days too) | R17, R14 (AC14.1) |
| **Rev 2 decision 4** (today: accent border, faint tint, badge) | R12 (AC12.6) |
| **Rev 2 decision 5** (square tiles, hover only) | R14 (AC14.3, AC14.4), R18 |

## Design

### Data from Strava
None. No endpoint, scope, field, sync or cache change. The new route doesn't touch `SyncService` or `StravaClient` (AC8.6). 401/429/outage behaviour is unchanged. The route only reuses `hasConnectedSession` for access control.

### Domain model & logic
Package `domain/trainingplan/` (pure, no Ktor, no `java.io`):

```kotlin
@JvmInline value class Kilometres(val value: Double)          // 2-decimal, half-up
data class TargetPace(val key: String, val label: String,
    val target: SecondsPerKm?, val min: SecondsPerKm?, val max: SecondsPerKm?)
data class PlanRace(val distanceKm: Double, val isMarathon: Boolean, val label: String)
data class PlanDay(val dayOfWeek: DayOfWeek, val session: String, val note: String?,
    val easyMinutes: Int, val subtMinutes: Int, val distance: Kilometres,
    val isRest: Boolean, val race: PlanRace?) { val totalMinutes: Int get() = easyMinutes + subtMinutes }
data class PlanWeek(val label: String, val distance: Kilometres,
    val easyPercent: Double, val subtPercent: Double, val days: List<PlanDay>)  // days.size == 7
data class TrainingPlan(val paces: List<TargetPace>, val notes: List<String>, val weeks: List<PlanWeek>)

sealed interface PlanParseResult { data class Valid(val plan: TrainingPlan) : PlanParseResult
                                   data class Invalid(val reason: String) : PlanParseResult }

enum class BuildPhase { NOT_STARTED, IN_PROGRESS, FINISHED }
data class BuildProgress(val phase: BuildPhase, val percentComplete: Int, val daysPassed: Int,
    val totalDays: Int, val currentWeekIndex: Int?, val weekCount: Int,
    val daysToGo: Int?, val daysUntilStart: Int?, val label: String)
// [Rev 2] isPast added
data class DatedDay(val date: LocalDate, val day: PlanDay, val isToday: Boolean, val isPast: Boolean)
data class DatedWeek(val index: Int, val week: PlanWeek, val startDate: LocalDate,
    val endDate: LocalDate, val isCurrentWeek: Boolean, val days: List<DatedDay>)
data class BuildCalendar(val startDate: LocalDate, val endDate: LocalDate,
    val progress: BuildProgress, val paces: List<TargetPace>, val notes: List<String>, val weeks: List<DatedWeek>)
```

Pure functions (each unit-tested):
- `SemicolonCsv.parse(text: String): List<List<String>>`: RFC 4180-style quoting with `;`, `""` escaping, CRLF or LF line endings (AC2.3).
- `TrainingPlanParser.parse(rows: List<List<String>>): PlanParseResult`: header block → `parsePaces`, `parseNotes` (R3). Blocks → `parseWeek` → `parseDay` (R4, R5). Small helpers: `parseNumber(cell): Double?` (comma decimals), `parsePaceSeconds("0:05:38") = 338`, `sessionText(...)`, `detectRace(...)`, `dayNote(header)`. Named constants: `PRIMARY_COL_OFFSET = 2`, `COLS_PER_DAY = 2`, `DAYS_PER_WEEK = 7`, `MARATHON_MIN_KM = 42.0`, `MAX_PLAN_FILE_BYTES`.
- `BuildCalendarBuilder.build(plan, startDate: LocalDate, today: LocalDate): BuildCalendar`: dates (R6), progress and label (R7), **[Rev 2]** and `isPast = date.isBefore(today)` per day (AC7.7). Label strings are built only here (singular/plural "day"/"days").
- `TrainingPlanService(source: TrainingPlanSource, startDate, clock, zone)`: `suspend fun get(): TrainingPlanResult`, with `sealed interface TrainingPlanResult { NotConfigured; Unavailable; Invalid(reason); Ready(calendar) }`.

Units and rounding: km and percentages are rounded half-up to 2 decimals (they're already 2-decimal in the sheet). Minutes are whole minutes, half-up. Paces are whole seconds per km. Dates use `LocalDate` only, so DST can't shift a day.

**[Rev 2]** `AppConfig.DEFAULT_TRAINING_PLAN_START = LocalDate.of(2026, 8, 17)`. The env override and the Monday validation are unchanged.

### Persistence
No DB tables or migrations. Port `domain/ports/TrainingPlanSource.kt`:
```kotlin
sealed interface PlanFile { data object NotConfigured : PlanFile; data object Unreadable : PlanFile
    data object TooLarge : PlanFile; data class Content(val text: String) : PlanFile }
interface TrainingPlanSource { suspend fun read(): PlanFile }
```
Adapter `persistence/TrainingPlanFileSource.kt(path: String?)`: `withContext(Dispatchers.IO)`, checks the size before reading, decodes UTF-8 with a strict `CharsetDecoder` (`REPORT`), falls back to `windows-1252`, strips the BOM, and logs per AC2.4. The service maps `TooLarge` to `Invalid` (AC2.5).

### Backend API
#### `GET /api/training-plan`
- Query params: none. Auth: `hasConnectedSession(tokens)`, otherwise `401 not_connected`.
- `200` response (exact shape; values here are invented, for a 16-week plan with today = 2026-10-31). **[Rev 2]** The dates are new and `isPast` is a new field:
```json
{
  "startDate": "2026-08-17",
  "endDate": "2026-12-06",
  "progress": {
    "phase": "inProgress",
    "percentComplete": 66,
    "daysPassed": 75,
    "totalDays": 112,
    "currentWeekIndex": 11,
    "weekCount": 16,
    "daysToGo": 36,
    "daysUntilStart": null,
    "label": "66% · week 11 of 16 · 36 days to go"
  },
  "targetPaces": [
    { "key": "easy", "label": "Easy", "targetSecondsPerKm": 338, "minSecondsPerKm": null, "maxSecondsPerKm": null },
    { "key": "15k", "label": "15K", "targetSecondsPerKm": 245, "minSecondsPerKm": 240, "maxSecondsPerKm": 250 }
  ],
  "notes": ["5x15 - rest 2'30\"", "3x9' during long - jog 4'"],
  "weeks": [
    {
      "index": 1,
      "label": "WEEK 1",
      "startDate": "2026-08-17",
      "endDate": "2026-08-23",
      "distanceKm": 62.4,
      "easyPercent": 79.25,
      "subtPercent": 20.75,
      "isCurrentWeek": false,
      "days": [
        {
          "date": "2026-08-18",
          "dayOfWeek": "TUESDAY",
          "session": "25' WU/CD + 3x10' subT",
          "note": null,
          "totalMinutes": 55,
          "easyMinutes": 25,
          "subtMinutes": 30,
          "distanceKm": 11.2,
          "isRest": false,
          "race": null,
          "isToday": false,
          "isPast": true
        },
        {
          "date": "2026-08-23",
          "dayOfWeek": "SUNDAY",
          "session": "25' WU/CD + 5K RACE",
          "note": null,
          "totalMinutes": 43,
          "easyMinutes": 25,
          "subtMinutes": 18,
          "distanceKm": 9.4,
          "isRest": false,
          "race": { "label": "5K race", "distanceKm": 5.0, "isMarathon": false },
          "isToday": false,
          "isPast": true
        }
      ]
    }
  ]
}
```
Field rules: `phase` ∈ `"notStarted" | "inProgress" | "finished"`. `currentWeekIndex` and `daysToGo` are non-null only when `inProgress`, and `daysUntilStart` only when `notStarted`. `dayOfWeek` ∈ `MONDAY…SUNDAY`. `days` always has 7 entries (the example shows 2 for brevity). `note` and `race` are null when absent. Pace minimum and maximum are both null or both set. **[Rev 2]** `isPast` is a boolean on every day, `isPast && isToday` is never true, and the count of `isPast` days equals `progress.daysPassed`. Numbers are JSON numbers; dates are `YYYY-MM-DD`.
- Errors (`{"error","message"}`, `ErrorResponse` constants):

| Status | `error` | `message` |
|---|---|---|
| 401 | `not_connected` | existing text |
| 404 | `training_plan_not_configured` | "No training plan is configured." |
| 503 | `training_plan_unavailable` | "The training plan file could not be read." |
| 422 | `training_plan_invalid` | safe reason (AC4.2–AC4.7, AC2.5) |
| 500 | `internal_error` | existing StatusPages handler |

DTOs live in `api/TrainingPlanDtos.kt` (`@Serializable`, camelCase; **[Rev 2]** `PlanDayDto.isPast: Boolean`), and the mappers in `api/TrainingPlanMappers.kt` (enum → `"inProgress"`, etc.). The route stays thin: session check → `service.get()` → `when` → respond.

### Frontend (Angular)
- **Route / page [Rev 2: changed]:** no new route. `RunningDashboardPage` adds `providers: [TrainingPlanStore]` and imports `CalendarLikeBuild` and `BuildCalendarSection`. The template is:
  ```html
  <h1>Running</h1>
  <app-calendar-like-build />
  @switch (state().kind) { …unchanged… }
  <app-build-calendar-section />
  ```
  Both plan elements sit outside the `@switch` (AC9.1, AC9.2). The page's own dashboard logic is unchanged.
- **Service & interfaces** (`core/api/`):
  - `training-plan.models.ts`: `TrainingPlanResponse`, `BuildProgress` (`phase: 'notStarted' | 'inProgress' | 'finished'`), `TargetPace`, `PlanWeek`, `PlanDay` (`dayOfWeek: 'MONDAY' | … | 'SUNDAY'`, **[Rev 2]** `isPast: boolean`), `PlanRace`, all matching the JSON above field for field, with `| null` where nullable.
  - `training-plan.service.ts`: `getTrainingPlan(): Observable<TrainingPlanResponse>` → `GET /api/training-plan`. Unchanged.
- **Store [Rev 2: new]** (`features/calendar-like-build/training-plan.store.ts`): `TrainingPlanStore`, `@Injectable()` (page-scoped, R16). It holds a private `signal<PlanState>({kind:'loading'})` exposed read-only as `state`, plus computed `data` (`TrainingPlanResponse | null`) and `errorText`. It has `ensureLoaded()` and `retry()`. The load and error mapping move here from `CalendarLikeBuild` and reuse `isTrainingPlanResponse`, `planErrorState` and `planErrorText` from `plan-view.ts`.
- **Components** (`features/calendar-like-build/`, all standalone, `OnPush`, plain interpolation only):
  - `calendar-like-build.ts` **[Rev 2: changed]** (`CalendarLikeBuild`, selector `app-calendar-like-build`): the top "Marathon build" card. It injects the store, calls `ensureLoaded()` in `ngOnInit` and renders the `@switch` over `store.state()`: loading, notConfigured and error (with Retry → `store.retry()`) as before. In the loaded state it renders only `<app-build-progress>`. It no longer makes the request itself or renders the paces or calendar.
  - `build-calendar-section/build-calendar-section.ts` **[Rev 2: new]** (`BuildCalendarSection`, selector `app-build-calendar-section`): the bottom card. It injects the store and calls `ensureLoaded()` in `ngOnInit`. Only when `store.data()` is non-null does it render `<section class="card plan-calendar" aria-labelledby="plan-calendar-heading">` with `h2#plan-calendar-heading` "Build calendar", `<app-target-paces>` and `<app-build-calendar>`. Otherwise it renders nothing.
  - `build-progress/build-progress-bar.ts` (`BuildProgressBar`, `progress = input.required<BuildProgress>()`): the bar (R10). Unchanged.
  - `target-paces/target-paces.ts` (`paces`, `notes` inputs): R11. Unchanged apart from where it is used.
  - `build-calendar/build-calendar.ts` + `.scss` **[Rev 2: changed]** (`weeks`, `progress` inputs): R12. It adds `[class.past]="day.isPast"`, the today, past and hover styles (AC12.6, R17, R18), the tile box and the square aspect ratio (AC14.3, AC14.4). Scroll behaviour is unchanged (AC12.8).
  - `plan-view.ts` **[Rev 2: changed]**: `DayCell` gains `isPast: boolean`, mapped from `day.isPast` in `weekRows`. `scrollTargetWeekIndex`, `paceRows`, the error-mapping and shape helpers are unchanged.
- **Formatting** (pure, in `shared/format/`): `dates.ts` has `formatWeekdayDayMonth("2026-08-18", "TUESDAY") → "Tue 18 Aug"` **[Rev 2: example date]**. `numbers.ts` has `formatTwoDecimals(62.4) → "62.40"`. Paces use the existing `formatPace`. Nothing is computed in templates.
- **Day tile styling summary [Rev 2: new]** (all `var(--color-*)`; specificity order: base → rest → race → marathon → past → today):

| Tile | Background | Border | Text | Extra |
|---|---|---|---|---|
| future (base) | `--color-surface` | 1px `--color-border` | `--color-text` / muted lines | square at ≥ 720 px |
| rest (future) | `--color-surface` | 1px `--color-border` | "Rest" in `--color-text-muted` | |
| race (future) | `--color-surface` | 1px `--color-accent` | as base | race badge |
| marathon (future) | `--color-accent` | 1px `--color-accent` | `--color-bg` | larger session, badge |
| past (any kind) | `--color-bg` | 1px `--color-border` | all `--color-text-muted` | 1px diagonal cross in `--color-secondary` |
| today | `color-mix(in srgb, var(--color-accent) 12%, var(--color-surface))` | 2px `--color-accent` (marathon: keeps accent bg, 2px `--color-text`) | `--color-text`; badge `--color-accent` | `aria-current="date"`, Today badge |
| hover (pointer only) | `--color-surface-raised` (past: `--color-surface`; today: 20% accent mix; marathon: unchanged) | `--color-text-muted` (today: unchanged; marathon: `--color-text`) | unchanged | 150 ms transition |

  The 2px today border must not shift the grid. Implement it with `box-shadow: inset 0 0 0 1px` on top of the 1px border, or with `box-sizing: border-box` and a fixed outer size. Either is fine, as long as no tile changes size between states.
- **States [Rev 2: changed]:**

| View | Loading | Empty | Error | Normal |
|---|---|---|---|---|
| "Marathon build" card (top) | heading + "Loading your plan…" (`role="status"`) | 404: "No training plan set up yet." + `.env` hint, no Retry | 503 / 422 / other texts (AC13.3–AC13.5) + Retry, `role="alert"` | heading → progress bar |
| Progress bar | not rendered | not rendered | not rendered | bar + label; 0 % before, 100 % after |
| "Build calendar" card (bottom, last on page) | not rendered | not rendered | not rendered | heading → paces + notes → calendar |
| Target paces | not rendered | not rendered | not rendered | list; hidden when `targetPaces = []`; notes hidden when `[]` |
| Calendar | not rendered | not rendered | not rendered | weeks; rest, race, marathon, past (dimmed + cross), today, current-week marks; hover highlight |
| Rest of dashboard | its own 001/002 states, unaffected | same | same | same |

- **Chart:** none (the bar is a styled `div`, the calendar is a CSS grid, no ECharts).

### Files expected to change
Already built for the original spec. Rev 2 touches the files marked **[Rev 2]**. The others are listed for completeness.

Backend (`backend/`):
- `src/main/kotlin/app/stravadashboard/config/AppConfig.kt` **[Rev 2: default start date]**
- `src/main/kotlin/app/stravadashboard/Application.kt` (wire the source, service and route)
- `src/main/kotlin/app/stravadashboard/domain/trainingplan/TrainingPlan.kt` **[Rev 2: `DatedDay.isPast`]**, `BuildCalendarBuilder.kt` **[Rev 2: compute `isPast`]**, `SemicolonCsv.kt`, `TrainingPlanParser.kt`, `PlanHeaderParser.kt`, `PlanDayParser.kt`, `PlanCells.kt`, `TrainingPlanService.kt`
- `src/main/kotlin/app/stravadashboard/domain/ports/TrainingPlanSource.kt`
- `src/main/kotlin/app/stravadashboard/persistence/TrainingPlanFileSource.kt`
- `src/main/kotlin/app/stravadashboard/api/TrainingPlanDtos.kt` **[Rev 2: `isPast`]**, `TrainingPlanMappers.kt` **[Rev 2: map `isPast`]**, `TrainingPlanRoutes.kt`, `ErrorResponse.kt`
- `src/test/kotlin/app/stravadashboard/AppConfigTest.kt` **[Rev 2]**, `domain/trainingplan/BuildCalendarBuilderTest.kt` **[Rev 2: 2026 dates + `isPast`]**, `api/TrainingPlanRoutesTest.kt` **[Rev 2: JSON shape]**, `testsupport/PlanFixtures.kt` **[Rev 2: start date]**, other `domain/trainingplan/*Test.kt`, `persistence/TrainingPlanFileSourceTest.kt`
- `src/test/resources/trainingplan/plan-sample.csv` (invented, values unchanged)

Repo root: `.env.example` **[Rev 2: comment for the default]**, `.gitignore`.

Frontend (`frontend/src/app/`):
- `core/api/training-plan.models.ts` **[Rev 2: `isPast`]**, `training-plan.service.ts`, `training-plan.service.spec.ts`
- `features/calendar-like-build/training-plan.store.ts` + `training-plan.store.spec.ts` **[Rev 2: new]**
- `features/calendar-like-build/calendar-like-build.ts|html|scss|spec.ts` **[Rev 2: uses the store, bar only in normal state]**
- `features/calendar-like-build/build-calendar-section/build-calendar-section.ts|scss` **[Rev 2: new]**
- `features/calendar-like-build/build-calendar/build-calendar.ts|scss` **[Rev 2: past, today, tile, hover]**, `plan-view.ts` **[Rev 2: `DayCell.isPast`]**, `plan-sections.spec.ts` **[Rev 2]**, `build-progress/*`, `target-paces/*`
- `shared/format/dates.ts` (+ spec **[Rev 2: example dates]**), `shared/format/numbers.ts` (+ spec)
- `features/running-dashboard/running-dashboard-page.ts|html` **[Rev 2: providers, bottom element]**, `running-dashboard-page.plan.spec.ts` **[Rev 2]**, `running-dashboard-page.spec.ts`, `running-dashboard-page.redesign.spec.ts` **[Rev 2: second stub, AC15.2]**
- `testing/training-plan-fixtures.ts` **[Rev 2: 2026 dates, `isPast`]**

## Edge cases
| Case | Expected behaviour |
|---|---|
| No activities in range / not connected to Strava | The plan doesn't depend on activities. With a connected session both plan cards render normally even if the running dashboard is empty. With no session, 401 → `/connect` (as for every `/api` route). |
| Manual activity (no GPS) / activity without HR | Not applicable: no Strava data is used (plan only, decision 1). |
| Week spans DST change / time zones | Dates are `LocalDate` arithmetic, so DST can't shift them. "Today" and "past" use the server zone (AC7.6, AC7.7), as in 001. If the browser is in another zone, the server's day still applies. **[Rev 2]** The build crosses the Europe DST change on 25 Oct 2026, and that has no effect on dates. |
| Unit conversion | No metre or m/s conversion. Decimal commas → numbers (AC4.4, AC4.5). `h:mm:ss`/`m:ss` → seconds per km, shown as `m:ss /km` by `formatPace`. km and % are shown with 2 decimals, as in the sheet. |
| `TRAINING_PLAN_CSV` unset | 404 → empty state in the top card. No bottom card. The app and the rest of the dashboard work. |
| **[Rev 2]** `.env` still has `TRAINING_PLAN_START_DATE=2027-08-16` | The env value wins over the new default, so the calendar shows 2027. The user should clear it (Risk 4). |
| File missing / locked by Excel / is a directory | 503 → "Couldn't read your training plan file." + Retry in the top card. No bottom card. |
| File saved by Excel as Windows-1252 or UTF-8 with BOM | Read correctly (AC2.2). |
| File saved with `,` separators instead of `;` | No week header in column 0 matches → 422 "No week blocks found…". |
| Missing EASY/SUBT/VOLUME row, wrong weekday header, text in a number cell | 422 with a reason naming the week (and the day). |
| Day with km 0 and minutes 0 but leftover session text | Rest day, shows "Rest" (AC5.3). |
| Session text and EASY/SUBT minutes disagree in the sheet | Shown as written. Session text comes from the session row, minutes from the EASY/SUBT rows, with no reconciliation (S12). |
| Day with no session text but minutes/km | Session line hidden, minutes and km shown (AC12.3). |
| Last day not a marathon / no marathon in CSV | No marathon tile. The label says "last day" on the final day (AC7.3). |
| CSV has more or fewer than 16 blocks | Calendar and dates follow the CSV (AC6.3), and the "week N of M" label uses the actual count. |
| Pace value unparseable / no pace headers | That pace is skipped, or the panel is hidden (AC3.4, AC11.3). |
| Quotes / `<` in notes | Shown as literal text (AC12.9). |
| Race day itself | 99 %, "race day" (AC7.3). The marathon tile is today (accent background + `--color-text` border). The next day shows 100 %, "build complete", and every tile is past. |
| **[Rev 2]** Page left open past midnight | The flags come from the server at load time. `isToday`/`isPast` update on the next load or Retry, not live (same as the progress label). |
| **[Rev 2]** Long session text in a square tile | The tile grows taller than square rather than clipping (AC14.3). The row's tiles stretch to the same height. |
| **[Rev 2]** Touch screens | No hover styles apply (`hover: hover` media query), so there is no "sticky" highlight after a tap. |
| Very narrow screen | Days stack per week as full-width tiles (AC14.3). The calendar scroll container keeps the current week in view. |

## Privacy
- **Data fields & sensitivity:** training-plan text (session descriptions, notes, week labels), planned minutes/km/percentages, target paces, **[Rev 2]** and a derived `isPast` flag. Personal but **not** health data, location or credentials. No Strava data is involved. The file path may contain the OS user name, so it is treated as personal and never sent or logged.
- **Storage & retention:** nothing is stored. The file is read per request and not cached, not written to SQLite, and not sent to the browser as a file. The browser only gets the parsed JSON and keeps it in memory (signals, in the page-scoped store that is discarded with the page), with no `localStorage`/`sessionStorage`. The CSV lives outside git (`.gitignore` guards the common locations), and its path comes from `.env`.
- **Scopes:** none added (unchanged from 001).
- **Applicable privacy-check items:**

| # | Status | How it's satisfied |
|---|---|---|
| 1 | ✅ | No secrets added. `.env.example` lists keys without values. The real CSV is git-ignored, and test CSVs are invented (AC1.4). |
| 2–4 | ✅ unchanged | No token or OAuth change. The route never touches tokens beyond the existing session check. |
| 5 | ✅ | No new scopes (AC8.6). |
| 6 | ✅ | Only the plan's display fields are in the DTO. Nothing is persisted. |
| 7 | n/a | No GPS. |
| 8 | ✅ | Logs hold only the exception class or a safe parse reason. There's no path and no cell content (AC2.4, AC2.6), and `AppConfig.toString` hides the path (AC1.3). |
| 9 | ✅ unchanged | No Ktor client logging change. |
| 10 | ✅ | Error bodies use the standard shape with safe text. There are no stack traces or paths (AC8.3–AC8.5). |
| 11 | ✅ | The route checks `hasConnectedSession` (AC8.2). |
| 12 | ✅ unchanged | Session cookie unchanged. |
| 13 | ✅ | Session text and notes are user-written. They are rendered only through interpolation, with no `[innerHTML]` (AC12.9). No ECharts formatter is involved. |
| 14 | n/a | GET only, no state change. |
| 15 | ✅ | The response holds only the UI fields (no file path, no raw rows). CORS unchanged. |
| 16 | ✅ | No external assets, fonts or new npm/Gradle dependencies (the CSV parser is hand-written; the cross and hover are plain CSS). |
| 17 | ✅ unchanged | No Strava data is shown in the plan cards. The "Powered by Strava" footer is unchanged. |
| 18–20 | unchanged | Nothing new is stored. Disconnect is unaffected (item 18 still deferred, as in 001). |
| 21 | ✅ | All fixtures are invented and mirror only the layout of the user's CSV. |

## Test plan
Test names are prefixed `004`. **[Rev 2]** Existing tests with 2027 dates are updated to the 2026 dates in this spec, keeping their names unless listed here.

| AC | Test type | Test name |
|---|---|---|
| AC1.1–AC1.3 | backend unit (`AppConfigTest`) | `004 R1 - training plan path optional`, `004 R1 - start date defaults and must be a Monday` **[Rev 2: 2026-08-17]**, `004 R1 - toString hides plan path` |
| AC1.4 | review check | `.env.example` keys and default comment, `.gitignore` patterns |
| AC2.1 | backend unit (`TrainingPlanFileSourceTest`, temp file) | `004 R2 - file is re-read on every call` |
| AC2.2 | backend unit | `004 R2 - strips BOM`, `004 R2 - falls back to windows-1252` |
| AC2.3 | backend unit (`SemicolonCsvTest`) | `004 R2 - quoted cells and doubled quotes`, `004 R2 - ragged rows read missing cells as empty` |
| AC2.4, AC2.6 | backend unit (log capture) | `004 R2 - unreadable file logs class only` , `004 R2 - parse failure log has no cell values` |
| AC2.5 | backend unit | `004 R2 - file over 1 MB is invalid` |
| AC3.1–AC3.6 | backend unit (`TrainingPlanParserTest`) | `004 R3 - single pace`, `004 R3 - pace range with target`, `004 R3 - unparseable pace skipped`, `004 R3 - sheet notes from column after NOTES`, `004 R3 - paces keep column order` |
| AC4.1, AC4.7 | backend unit | `004 R4 - blocks detected in order with labels`, `004 R4 - no blocks is invalid` |
| AC4.2, AC4.3 | backend unit | `004 R4 - missing VOLUME row is invalid`, `004 R4 - wrong weekday header is invalid`, `004 R4 - day note from parentheses` |
| AC4.4, AC4.5 | backend unit | `004 R4 - week totals read as written`, `004 R4 - day minutes and km with decimal commas`, `004 R4 - text in number cell is invalid` |
| AC4.6 | backend unit | `004 R4 - session text composition examples` |
| AC5.1–AC5.4 | backend unit | `004 R5 - 5K and 10K races detected`, `004 R5 - race header day is marathon`, `004 R5 - zero km zero minutes is rest even with leftover text` |
| AC6.1–AC6.3 | backend unit (`BuildCalendarBuilderTest`) | `004 R6 - sixteen weeks end on 6 Dec 2026` **[Rev 2: renamed]**, `004 R6 - consecutive dates for any block count` |
| AC7.1–AC7.6 | backend unit (fixed `Clock`) | `004 R7 - in progress label`, `004 R7 - first day`, `004 R7 - one day to go and race day`, `004 R7 - before start`, `004 R7 - after race`, `004 R7 - today uses server zone` **[Rev 2: 2026 dates]** |
| AC7.7 **[Rev 2]** | backend unit (fixed `Clock`) | `004 R7 - past days are those before today`, `004 R7 - no past days before start and all past after race` |
| AC8.1 | backend route (`testApplication`, fake source) | `004 R8 - returns calendar json shape` **[Rev 2: includes `isPast`]** |
| AC8.2–AC8.5 | backend route | `004 R8 - 401 without session`, `004 R8 - 404 not configured`, `004 R8 - 503 unreadable`, `004 R8 - 422 invalid with safe message` |
| AC8.6 | backend route (MockEngine counts requests) | `004 R8 - no Strava call` |
| AC9.1–AC9.3 **[Rev 2]** | frontend component (page, `HttpTestingController`) | `004 R9 - progress card first and calendar card last`, `004 R9 - one plan request in parallel with dashboard` |
| AC9.4 | frontend component | `004 R9 - plan error does not affect running dashboard`, `004 R9 - dashboard error does not affect plan` |
| AC9.5 | frontend component | `004 R9 - 401 shows no plan error text` |
| AC10.1, AC10.2 | frontend component (`BuildProgressBar`, `CalendarLikeBuild`) | `004 R10 - progressbar aria and label`, `004 R10 - fill width follows percent`, `004 R10 - top card shows only the bar when loaded` **[Rev 2]** |
| AC10.3, AC14.1–AC14.4 | review check + manual visual check (1280 px, 375 px) | tokens only (`color-mix` with tokens only), no `--color-worse`, no `opacity`, square tiles at desktop, stacked tiles on mobile, no horizontal scroll |
| AC11.1–AC11.3 | frontend component (`TargetPaces`) | `004 R11 - single and range paces formatted`, `004 R11 - notes listed and hidden when empty`, `004 R11 - panel hidden without paces` |
| AC11.4 **[Rev 2]** | frontend component (`BuildCalendarSection`) | `004 R11 - paces and notes in the calendar card` |
| AC12.1–AC12.4 | frontend component (`BuildCalendar`) | `004 R12 - sixteen weeks Monday to Sunday` **[Rev 2: Mon 17 Aug to Sun 6 Dec]**, `004 R12 - week summary`, `004 R12 - day tile content`, `004 R12 - rest day` |
| AC12.5–AC12.7 | frontend component | `004 R12 - races and single marathon highlight`, `004 R12 - today marked once`, `004 R12 - current week marked once` |
| AC12.6 styling **[Rev 2]** | review check + manual | today has accent border and tint, marathon-today variant |
| AC12.8 | frontend unit (`scrollTargetWeekIndex`) + manual | `004 R12 - scroll target per phase`; manual: the calendar (now at the bottom) opens on the current week and the page doesn't jump |
| AC12.9 | frontend component | `004 R12 - session and note rendered as text` |
| AC13.1–AC13.6 **[Rev 2]** | frontend component (`CalendarLikeBuild` + `BuildCalendarSection` with a real store) | `004 R13 - loading`, `004 R13 - not configured empty state`, `004 R13 - unreadable error with retry`, `004 R13 - invalid shows backend message`, `004 R13 - other error`, `004 R13 - normal order`, `004 R13 - calendar card hidden unless loaded`, `004 R13 - single retry button on error` |
| AC16.1–AC16.4 **[Rev 2]** | frontend service (`TrainingPlanStore`, `HttpTestingController`) | `004 R16 - ensureLoaded requests once`, `004 R16 - retry requests again`, `004 R16 - 401 keeps state`, `004 R16 - unexpected body is other error` |
| AC17.1, AC17.5 **[Rev 2]** | frontend component (`BuildCalendar`) | `004 R17 - past class on days before today`, `004 R17 - no past before start and all past after` |
| AC17.2–AC17.4 **[Rev 2]** | review check + manual | muted opaque colours, cross drawn in CSS with `--color-secondary`, past race and marathon dimmed, future unchanged |
| AC18.1–AC18.2 **[Rev 2]** | review check + manual (desktop mouse, touch emulation) | `.day:hover` inside `@media (hover: hover)`, 150 ms transition, no layout shift |
| AC18.3 **[Rev 2]** | frontend component | `004 R18 - day tiles are not interactive` (no `tabindex`, `role`, click listener) |
| formatters | frontend unit | `004 - formatWeekdayDayMonth` **[Rev 2: 2026 dates]**, `004 - formatTwoDecimals` |
| service | frontend service | `004 R8 - getTrainingPlan calls /api/training-plan` |
| AC15.1 | review check (diff) | listed files unchanged |
| AC15.2, AC15.3 | full suite | backend `./gradlew ktlintCheck detekt test`; frontend `npm run lint && npm test -- --watch=false && npm run build` |

## Out of scope
- Editing the plan in the dashboard. The CSV stays the source of truth.
- Uploading a CSV through the browser. The browser never reads the file.
- Ticking off or matching workouts against Strava runs (decision 1). **[Rev 2]** The past-day cross only means "date has passed", not "done".
- Generating or adjusting training plans.
- New Strava data or scopes.
- Light mode.
- Reconciling inconsistencies inside the CSV (e.g. session text vs minutes). Values are shown as written.
- **[Rev 2]** Clicking a day tile (details, popovers). Tiles are hover-only.

## Suggested follow-ups
- Match planned days against actual Strava runs (done / missed / km vs plan), deferred by decision 1. This would let the cross mean "done" rather than "past".
- Attach the sheet-level notes (e.g. "3-4x15 - rest 3'-4'") to the days whose session matches their rep pattern.
- Show the "5K race time" cell from the sheet header next to the paces.
- A weekly planned-km mini chart.
- A plan-file "lint" endpoint or CLI that lists all suspect cells at once (leftover text on rest days, minute mismatches).
- Respect `prefers-reduced-motion` (drop the hover transition), and add keyboard shortcuts to jump to today.
- **[Rev 2]** A "Jump to calendar" link in the top card, and screen-reader text for past days (the cross is visual only).
- **[Rev 2]** Refresh `isToday`/`isPast` at midnight without a reload.

## Risks & decisions to confirm
1. **Where the notes appear (S8).** The day notes in the sheet live in the day headers (e.g. `TUESDAY (75min progression)`, `SUNDAY (med-long w/30' subT)`), and those are shown on their day. The `NOTES` column at the top (e.g. `5x15 - rest 2'30"`, `75' prog - 3x25'`) isn't tied to any day in the CSV, so it is shown as a "Plan notes" list next to the target paces (now in the bottom card). Attaching those notes to days is a follow-up.
2. **Session text is composed, not copied.** The sheet splits each session over two cells (WU/CD minutes + EASY/SUBT value), so the text is built as `"25' WU/CD + 3x10' subT"` by the AC4.6 rules. A race header such as `RACE 42,2K` is shown as written.
3. **Rest day rule.** A day with 0 km and 0 minutes shows "Rest" and hides any leftover text. Your current CSV has a few such days that still contain rep text in the session row (one in week 13 and two in week 14). They will show as Rest. A few other cells also disagree (e.g. a session row number vs the EASY-row minutes in week 12, and the week-13 Sunday). Those are shown as written. You may want to tidy the CSV.
4. **[Rev 2] Start date default is now `2026-08-17`.** `TRAINING_PLAN_START_DATE` stays as an optional override. **If your local `.env` sets it (e.g. to `2026-08-16` or `2027-08-16`), that value still wins, so clear it or set `2026-08-17`.** The end date and the marathon date come from the block count, so 16 blocks → Sun 6 Dec 2026.
5. **The progress example in the intent ("73% · week 11 of 16 · 37 days to go") doesn't add up** for one single date. With the rules here (floor of days passed / 112; days to go = days until race day), today (Sat 31 Oct 2026) shows **"66% · week 11 of 16 · 36 days to go"**. Race day shows 99 % and "race day", and the day after shows 100 %.
6. **"Today" and "past" use the server's time zone** (the same as the running dashboard), not the browser's. **[Rev 2]** `isPast` is computed by the backend for that reason, rather than in the browser.
7. **Error statuses** are 404 (not configured, shown as the empty state), 503 (unreadable) and 422 (invalid, with a safe reason shown in the UI).
8. **The plan route requires a Strava-connected session**, like every `/api` route (privacy item 11).
9. **Numbers are shown with 2 decimals** (`62.40 km`, `79.25% easy`) to match the sheet exactly.
10. **Existing page specs get stubs** for both plan components (AC15.2).
11. **[Rev 2] Component split and naming.** `app-calendar-like-build` (the required component name) stays as the top "Marathon build" card, so its state handling and tests change least. The calendar itself moves to a new `app-build-calendar-section` with the heading "Build calendar". Confirm the heading text, and confirm that keeping the name on the top card (rather than on the calendar) is fine.
12. **[Rev 2] Bottom card in non-normal states.** While loading, and when the plan is missing or broken, only the top card shows the message and Retry. The bottom "Build calendar" card is not rendered at all, so there are no duplicate alerts or Retry buttons.
13. **[Rev 2] Past races and marathon are dimmed like any past day.** They keep their badge text but lose the accent border/background once passed. The alternative is to keep races highlighted even when past.
14. **[Rev 2] Square tiles on mobile.** At 375 px, seven squares per row would be about 45 px wide, too small for the session text. So squares apply from 720 px up, and on mobile each day is a full-width bordered tile, stacked as today. On desktop, a tile grows taller than square if its text doesn't fit, rather than clipping.
15. **[Rev 2] Cross colour.** The cross uses `--color-secondary` (`#6f8299`) so it is visible on the dark background without hurting the text on top. `--color-border` would be almost invisible.
16. **[Rev 2] Today tint** uses `color-mix()` of two existing tokens (12 % accent into surface, 20 % on hover) instead of adding a new token to `styles.scss`. This keeps 002 AC1.4 (no colour literals in components) and avoids transparency.
