# Spec: Dark dashboard redesign with loading spinner (Whoop-inspired)

**Feature ID:** 002 · **Status:** Approved · **Intent:** [intent.md](./intent.md) · **Date:** 2026-10-30

## Summary
Restyle the whole Angular app (app shell, dashboard, loading/error/empty states, sync banner, Connect page and the three ECharts charts) with one dark theme only: a near-black background, dark grey cards, electric-green accent, muted blue-grey secondary colour and a red-orange used only for "getting worse". The plain loading text gets an animated SVG ring spinner above it. A row of three headline tiles (This week, Easy-run HR trend, Workout pace) goes above the charts. The tiles are built in the frontend by *picking* values from the existing `GET /api/dashboard/running` response, without computing anything new. The backend, the JSON contract and what the charts compute stay exactly as in feature 001.

## Requirements
| ID | Requirement | Priority (Must/Should/Could) |
|---|---|---|
| R1 | One dark theme only, defined as colour tokens in one place (CSS custom properties + the ECharts palette); no light mode, no theme switch, no external fonts | Must |
| R2 | Every screen uses the dark look: app shell and "Powered by Strava" footer, dashboard heading and cards, HR type selector, sync banner, error state and Retry button, empty and not-enough-data states, Connect page | Must |
| R3 | While the dashboard request is pending, an animated accent-coloured ring spinner shows above "Loading your runs…" and the first-import note | Must |
| R4 | In the normal state, a row of exactly three headline tiles (big bold value, small uppercase label, detail line) shows above the charts, built only from the existing response | Must |
| R5 | "This week" tile: km so far and run count for the current Monday–Sunday week | Must |
| R6 | "Easy-run HR trend" tile: the easy-run `changeBpm` over the 5-month window, or "—" with a note when not available | Must |
| R7 | "Workout pace" tile: the change for the most recent workout type with data in both 30-day windows, or "—" with a note when none qualifies | Must |
| R8 | Improving/worse colours: green for improving, red-orange for getting worse, neutral otherwise. Applied to the tiles, and also to the change texts already in the HR and pace cards | Must (tiles) / Should (cards) |
| R9 | The three charts keep all their data, axes, labels and tooltips but are restyled: thin lines, glowing points, no axis lines, faint split lines | Must |
| R10 | Text, numbers and focus indicators meet WCAG AA contrast on the dark backgrounds | Must |
| R11 | No regression of feature 001: backend and JSON contract unchanged, all 001 texts and behaviour kept (except the two items this spec replaces) | Must |

## Acceptance criteria

Examples use the 001 fixed clock: **today = Thursday 2026-10-29**, so the current week starts Monday 2026-10-26. Colour values are the tokens defined in the Design section.

### R1 — Dark theme tokens
- **AC1.1** Given `frontend/src/styles.scss`, when it is inspected, then `:root` sets `color-scheme: dark` and defines exactly these tokens with these values: `--color-bg #0b0d0f`, `--color-surface #17191c`, `--color-surface-raised #22262a`, `--color-border #2a2e33`, `--color-text #f2f4f5`, `--color-text-muted #9aa3ad`, `--color-accent #00f19f`, `--color-secondary #6f8299`, `--color-worse #ff6b4a`, `--color-treadmill #a78bfa`, `--color-grid rgba(255, 255, 255, 0.06)`. `body` uses `--color-bg` as its background and `--color-text` as its text colour.
- **AC1.2** Given `shared/chart/palette.ts`, when it is imported, then it exports the same eleven colours under the keys `background`, `surface`, `surfaceRaised`, `border`, `text`, `textMuted`, `accent`, `secondary`, `worse`, `treadmill` and `grid`, with the same values as AC1.1. The 001 keys (`primary`, `previous`, `currentWeek`, `trend`, `treadmill`) no longer exist.
- **AC1.3** Given the frontend source, when it is searched, then there is no `prefers-color-scheme` media query, no light-theme stylesheet and no theme toggle. `index.html` has `<meta name="color-scheme" content="dark">` and `<meta name="theme-color" content="#0b0d0f">`.
- **AC1.4** Given the frontend source, when component styles (`styles:` blocks and component `.scss` files) are searched for colour literals (`#rgb`, `#rrggbb`, `rgb(`, `rgba(`, `hsl(`, named colours like `white`), then none are found. Colours come only from the `var(--color-*)` tokens. The only files that hold colour literals are `src/styles.scss` and `shared/chart/palette.ts`.
- **AC1.5** Given `styles.scss` and `index.html`, then the font stack stays `system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif`. No `@import`, `<link>` or `url()` points to an external host (no web fonts, no CDN).

### R2 — Every screen in the dark look
- **AC2.1 (Powered by Strava)** Given any route (`/` in every dashboard state, and `/connect`), when the app shell renders, then the footer text "Powered by Strava" is in the DOM, not hidden (`display`/`visibility`/`opacity` are not set to hide it), uses `--color-text-muted` on `--color-bg`, and has a font size of at least `0.8rem`. (001 AC1.2 still applies.)
- **AC2.2 (cards)** Given the dashboard in the normal state, when the three chart cards render, then each `.card` has a `--color-surface` background, a 1px `--color-border` border, a 12px radius and no box-shadow. Each card title (`h2`) uses `--color-text-muted`, `text-transform: uppercase`, `letter-spacing: 0.08em` and a font size of `0.8rem`. The DOM text of the titles is unchanged ("Weekly distance", "Heart rate by run type", "Workout pace: last 30 days vs previous 30 days").
- **AC2.3 (page heading)** Given any dashboard state, then the `h1` "Running" is rendered in `--color-text`, weight 700. Its text is unchanged (001 AC1.1).
- **AC2.4 (sync banner)** Given `sync.status` is `rateLimited`, `stravaUnavailable` or `partial`, when the banner renders, then it has `role="status"`, a `--color-surface-raised` background, a 3px left border in `--color-secondary` and `--color-text` text. The banner texts are exactly those of 001 AC5.4. Given `status = "ok"`, no banner is rendered.
- **AC2.5 (error state)** Given the request fails with a non-401 error, then the page shows "Couldn't load your dashboard." in a `--color-surface` card with `role="alert"`, and a "Retry" button with a transparent background, a 1px `--color-accent` border and `--color-accent` text. Clicking it sends the request again (001 AC13.2).
- **AC2.6 (empty and not-enough-data states)** Given the page-level empty state (001 AC13.3), then "No runs in the last 5 months" shows centred inside a `--color-surface` card in `--color-text-muted`. Given a card-level empty or not-enough-data text (001 AC8.6, AC12.1, AC12.2, AC13.4), then it uses `--color-text-muted`. All texts are unchanged.
- **AC2.7 (HR type selector)** Given the HR card with at least one type, then the native `<select>` has a `--color-surface-raised` background, `--color-text` text and a 1px `--color-border` border. Its options, labels and behaviour are unchanged (001 AC9.7).
- **AC2.8 (Connect page)** Given `/connect`, when it renders, then the content sits in a `--color-surface` card. The "Connect with Strava" link keeps `href="/auth/strava/login"` and is styled as a button with a `--color-accent` background, `--color-bg` text and weight 700. The `role="alert"` message (001 AC2.10, AC3.3) uses the notice style from AC2.4 (raised surface, secondary left border, `--color-text`), not red text. All Connect texts are unchanged.
- **AC2.9 (old style gone)** Given `frontend/src`, when it is searched for the 001 light-theme colours `#f5f7fa`, `#fff`/`#ffffff` (as a background), `#1f2630`, `#5f6b7a`, `#fff4e5`, `#6b3d00`, `#8a1c1c`, `#2f6fdf`, `#a3b8d8`, `#e3e7ed`, `#3a4250`, `#8a5cd1` and `#fc4c02`, and for `box-shadow` with a black alpha (the old card shadow), then none are found.

### R3 — Ring spinner while loading
- **AC3.1** Given the dashboard request is pending, when the page renders, then it shows, in this order: an `app-ring-spinner`, the text "Loading your runs…" and, under it, the note "The first import can take a minute." No tiles, cards or charts are rendered. (This replaces the single-line text of 001 AC13.1.)
- **AC3.2** Given the spinner renders, then it is an inline SVG of 64×64 CSS px with two `<circle>` elements: a track circle stroked in `--color-grid`, and an arc circle stroked in `--color-accent` with `stroke-linecap: round` and a `drop-shadow` glow in the accent colour.
- **AC3.3** Given the spinner renders, then the arc has two CSS animations, both `infinite`: a rotation of 360° every 1.4 s (`linear`), and a `stroke-dasharray`/`stroke-dashoffset` animation every 1.4 s (`ease-in-out`) that grows the arc from about 10 % to about 75 % of the ring and shrinks it back ("fills and turns").
- **AC3.4 (accessibility)** Given the loading state, then the element that wraps the spinner and texts has `role="status"` and `aria-live="polite"`, and the SVG has `aria-hidden="true"`. The texts are the accessible content.
- **AC3.5** Given the response arrives (success, error or empty), then the spinner is removed from the DOM. Given the error state, when Retry is clicked, then the spinner shows again until the next response.
- **AC3.6** Given the implementation, then the spinner is pure SVG + CSS in a standalone `OnPush` component, with no new npm dependency, image file or external asset.

### R4 — Headline tiles row
- **AC4.1** Given a `200` response that is not the page-level empty state (001 AC13.3), when the page renders, then exactly three tiles are rendered below the sync banner (if any) and above the "Weekly distance" card. In DOM order, their labels are "This week", "Easy-run HR trend" and "Workout pace".
- **AC4.2** Given a tile, then it shows: a label (DOM text as in AC4.1, rendered in `--color-text-muted`, `text-transform: uppercase`, `letter-spacing: 0.08em`, `0.75rem`), a value (at least `2rem`, weight 700, `font-variant-numeric: tabular-nums`), an optional unit after the value (smaller, `--color-text-muted`) and a detail line (`0.85rem`, `--color-text-muted`). The tile background is `--color-surface` with the AC2.2 border and radius.
- **AC4.3 (layout)** Given a viewport at least 720 px wide, then the three tiles sit side by side in one row of equal-width columns. Given a 375 px wide viewport, then they stack one per row, and the page has no horizontal scroll.
- **AC4.4** Given the loading, error or page-level empty state, then no tile is rendered.
- **AC4.5** Given the page loads, when the HTTP traffic is inspected (`HttpTestingController`), then exactly one request is made, `GET /api/dashboard/running`. The tiles add no request.

### R5 — "This week" tile
- **AC5.1** Given `weeklyDistance.weeks` whose `isCurrentWeek` entry (weekStart 2026-10-26) has `distanceKm = 32.4` and `runCount = 4`, then the tile shows value "32.4", unit "km", detail "4 runs" and tone `neutral`.
- **AC5.2** Given the current week has `runCount = 1` and `distanceKm = 5.0`, then the value is "5.0" and the detail is "1 run". Given `runCount = 0` and `distanceKm = 0.0`, then the value is "0.0", the unit "km" and the detail "0 runs". This is valid data, not "not available".
- **AC5.3 (agrees with chart)** Given any weeks list, then the tile's value is exactly `distanceKm.toFixed(1)` of the same `isCurrentWeek` entry that the weekly chart highlights in the accent colour (AC9.2), and matches the number in that bar's tooltip (001 AC8.5).
- **AC5.4** Given a weeks list with no `isCurrentWeek` entry (defensive; the backend always sends one), then the value is "—", there is no unit, the detail is "No data for this week" and the tone is `neutral`.

### R6 — "Easy-run HR trend" tile
*Note (007):* superseded. The tile is now "Easy-run HR" and compares this week's mean easy-run HR with last week's. See [007 specs](../007-week-vs-week-tiles/specs.md).

- **AC6.1** Given `heartRateTrend.types` contains `typeKey = "easy"` with `hasEnoughData = true` and `trend.changeBpm = -8.0`, then the tile shows value "−8.0" (U+2212 minus), unit "bpm", detail "Easy runs · last 5 months" and tone `better`.
- **AC6.2** Given `changeBpm = 3.2`, then the value is "+3.2" and the tone `worse`. Given `changeBpm = 0.0` (or `-0.0`), then the value is "0.0" and the tone `neutral`.
- **AC6.3 (not enough data)** Given the easy entry has `hasEnoughData = false` (or `trend = null`) with 2 `points`, and `minRunsForComparison = 3`, then the value is "—", there is no unit, the detail is "Not enough data yet: 2 easy runs with heart rate, at least 3 needed." ("1 easy run" when there is 1 point) and the tone is `neutral`.
- **AC6.4 (no easy runs)** Given no `easy` entry in `types` (including an empty list), then the value is "—", the detail is "No easy runs in the last 5 months" and the tone is `neutral`.
- **AC6.5** Given the HR card's selector is switched to a workout type, then the tile still shows the easy-run value.
- **AC6.6 (agrees with chart)** Given the easy type is selected in the HR card, then the number in the tile value equals the number in the card's note "Trend: −8.0 bpm over the period (lower = fitter)". Both use the same formatter (`formatBpmChange`).

### R7 — "Workout pace" tile
*Note (007):* superseded. The pace tile now compares the mean rep pace of all workouts this week with last week. See [007 specs](../007-week-vs-week-tiles/specs.md).

- **AC7.1** Given `workoutPaceComparison.types` in backend order (most recent run first, 001 AC11.4): `4x8min` (`hasEnoughData = false`), `5x6min` (previous 260, current 253, `changeSecondsPerKm = -7`) and `2x10min` (`hasEnoughData = true`, change +3), then the tile uses `5x6min`, the first type with a paced run in both windows. It shows value "7", unit "s/km faster", detail "5x6min · 4:20 → 4:13 /km" (previous → current) and tone `better`.
- **AC7.2** Given the chosen type has change `+3`, then the value is "3", the unit "s/km slower" and the tone `worse`. Given change `0`, then the value is "Same", the unit "pace" and the tone `neutral`.
- **AC7.3 (none qualifies)** Given `types` is not empty but no type has a paced run in both windows, then the value is "—", there is no unit, the detail is "No workout type with paced runs in both 30-day periods yet" and the tone is `neutral`.
- **AC7.4 (no workouts)** Given `types` is empty, then the value is "—" and the detail is "No workouts (e.g. 5x6min) in the last 60 days".
- **AC7.5 (agrees with chart)** Given any `types`, then the chosen type is `comparablePaceTypes(types)[0]` (the same rule that decides which bars the pace chart draws), and the tile's value + unit put together read the same as that type's change text in the pace card (`formatPaceChange`), e.g. "7 s/km faster".

### R8 — Improving / worse colours
- **AC8.1** Given a tile with tone `better`, `worse` or `neutral`, then its value element has the class `tone-better` (`--color-accent`), `tone-worse` (`--color-worse`) or `tone-neutral` (`--color-text`).
- **AC8.2** Given the tone rule `toneForChange(delta)`, then a negative delta gives `better`, a positive one `worse`, and 0 or −0 `neutral`. It is used for both bpm and s/km changes (lower is better for both).
- **AC8.3 (Should)** Given the pace card's change list (001 AC11.5) and the HR card's trend note (001 AC9.7), then the change text gets the same tone class from `toneForChange`. The texts are unchanged.
- **AC8.4** Given the frontend source, then `--color-worse`/`palette.worse` is used only by the `tone-worse` class. It is not used for errors, banners or chart series. *Amended by 003 R7: the HR chart's rising stretches also use `palette.worse`.*

### R9 — Lighter charts
- **AC9.1 (all charts)** Given each option builder (`buildWeeklyDistanceOptions`, `buildHeartRateTrendOptions`, `buildWorkoutPaceOptions`), when it is called with fixture data, then the returned option has `backgroundColor: 'transparent'`, `textStyle.color = palette.textMuted`, and on both axes `axisLine.show = false`, `axisTick.show = false` and `axisLabel.color = palette.textMuted`. The value axis has `splitLine.lineStyle` = `{ color: palette.grid, width: 1, type: 'dashed' }`, and the category/time axis has `splitLine.show = false`. The tooltip has `backgroundColor = palette.surfaceRaised`, `borderColor = palette.border` and `textStyle.color = palette.text`.
- **AC9.2 (weekly)** Given 9 weeks, then the bars use `palette.secondary`, and the `isCurrentWeek` bar uses `palette.accent` (this replaces the "current" colour of 001 AC8.5). `barMaxWidth = 28` and `itemStyle.borderRadius = [4, 4, 0, 0]`. Labels, the "km" axis name and the tooltip text are unchanged (001 AC8.5).
- **AC9.3 (HR)** Given an HR type, then the points series has `lineStyle.width = 1.5` in `palette.accent`, `symbol = 'circle'`, `symbolSize = 6`, and each outdoor point has `itemStyle` `{ color: palette.accent, shadowBlur: 10, shadowColor: palette.accent }` (glow). Treadmill points use `palette.treadmill` (purple, as in 001; changed on request after the review) for both `color` and `shadowColor`. The trend series stays dashed, with `lineStyle.width = 1` in `palette.textMuted`. The time axis, "Avg HR (bpm)" with `scale: true`, the tooltip text and the trend note are unchanged (001 AC9.7).
- **AC9.4 (pace)** Given comparable types, then "Previous 30 days" uses `palette.secondary` and "Last 30 days" uses `palette.accent`, with `barMaxWidth = 28` and `borderRadius = [4, 4, 0, 0]`. The data labels use `palette.text`, and the legend text uses `palette.textMuted`. The axis name, `m:ss` ticks, data label format and change text are unchanged (001 AC11.5).
- **AC9.5 (data unchanged)** Given the 001 chart fixtures, when the option builders run, then every series' data values, categories, axis names and tooltip strings equal those produced before this feature. The 001 option-builder tests for data and tooltips pass unchanged.

### R10 — Contrast
- **AC10.1** Given the palette, when the WCAG 2.x contrast ratio is computed in a unit test, then `text`, `textMuted`, `accent` and `worse` each reach at least 4.5:1 against `background`, `surface` and `surfaceRaised`, and `background` text on an `accent` fill (the Connect button) reaches at least 4.5:1.
- **AC10.2** Given the palette, then `secondary` and `accent` reach at least 3:1 against `surface` (non-text chart graphics, WCAG 1.4.11).
- **AC10.3** Given the Connect link, the Retry button or the HR `<select>` has keyboard focus, then a `:focus-visible` outline of 2px `--color-accent` with a 2px offset is shown.
- **AC10.4** Given any text element, then no text colour is made lighter or darker with `opacity` below 1. Muted text uses `--color-text-muted`.

### R11 — No regression of feature 001
- **AC11.1** Given the diff against `main`, when it is inspected, then no file under `backend/` changes, and `frontend/src/app/core/api/running-dashboard.models.ts`, `running-dashboard.service.ts` and `api-error.interceptor.ts` are unchanged. `GET /api/dashboard/running` and its JSON are exactly as in 001.
- **AC11.2** Given the frontend test suite, when it runs, then every 001 test passes. The only exceptions are the tests for 001 AC13.1 (loading text, replaced by AC3.1) and the colour check in 001 AC8.5 (replaced by AC9.2), which are updated to the new expectations.
- **AC11.3** Given all 001 states (normal, loading, error, page empty, card empty, not-enough-data, each sync banner status, Connect error/reason messages, 401 redirect), then the same texts and behaviour are present. Only the styling and the loading markup (R3) differ, and the tiles are added (R4).

## Traceability
| Success criterion (intent) | Covered by |
|---|---|
| S1 | R3 |
| S2 | R1, R2 |
| S3 | R2 (AC2.1–AC2.9), R1 (AC1.4) |
| S4 | R4, R5, R6, R7 |
| S5 | R5 (AC5.3), R6 (AC6.6), R7 (AC7.5), R4 (AC4.5), R11 |
| S6 | R9 |
| S7 | R2 (AC2.1) |
| S8 | R10 |
| S9 | R6 (AC6.3, AC6.4), R7 (AC7.3, AC7.4), R5 (AC5.4) |
| Decision 1 (colours) | R1, R8, R9 |
| Decision 2 (no live count) | R3 (out of scope noted) |
| Decision 3 (three tiles, green/red-orange) | R4–R8 |

## Design

### Data from Strava
No change. No new Strava endpoint, scope, field or call. Sync, caching, 401/429/outage behaviour and the `activity:read_all` scope are exactly as in 001. A 429 or outage still gives `200` with `sync.status`. The banner shows it, and the tiles show the saved data.

### Domain model & logic
No backend change. In the frontend, the tiles only **select and format** fields that are already in the response. They do no arithmetic on activity data (code-standards A6: no stats recomputed in Angular):

| Tile | Source | Selection rule |
|---|---|---|
| This week | `weeklyDistance.weeks[]` | the entry with `isCurrentWeek == true` (the backend's current Monday–Sunday week, server zone, bucketed by `start_date_local`) → `distanceKm`, `runCount` |
| Easy-run HR trend | `heartRateTrend.types[]` | the entry with `typeKey == "easy"`; when `hasEnoughData && trend != null` → `trend.changeBpm`; otherwise `points.length` and `minRunsForComparison` for the note |
| Workout pace | `workoutPaceComparison.types[]` | `comparablePaceTypes(types)[0]` (existing helper; backend order = most recent run first) → `changeSecondsPerKm`, `previous`/`current.averageRepPaceSecondsPerKm` |

Units: no conversion in the frontend. km is shown with `toFixed(1)` (the backend already sends 1 decimal), bpm with 1 decimal, and s/km as the backend's integer. Paces are formatted `m:ss` with the existing `formatPace`.

New pure frontend helpers (all unit-tested):
- `shared/format/tone.ts`: `type Tone = 'better' | 'worse' | 'neutral'`; `toneForChange(delta: number): Tone` (AC8.2).
- `shared/format/heart-rate.ts`: `formatBpmChange(change: number): string` → "−8.0" / "+3.2" / "0.0". `hrTrendText` in `heart-rate-trend.options.ts` is refactored to use it, and its output doesn't change.
- `features/running-dashboard/headline-tiles/headline-tiles.model.ts`:
```ts
export type HeadlineTileId = 'thisWeek' | 'easyHrTrend' | 'workoutPace';
export interface HeadlineTile {
  id: HeadlineTileId;
  label: string;        // "This week" | "Easy-run HR trend" | "Workout pace"
  value: string;        // "32.4", "−8.0", "7", "Same", or "—" when not available
  unit: string | null;  // "km", "bpm", "s/km faster", "s/km slower", "pace", or null
  detail: string;       // detail line or the "not available" note
  tone: Tone;
  available: boolean;   // false when value is "—"
}
export function buildHeadlineTiles(data: RunningDashboardResponse): HeadlineTile[]; // always 3, in order
```

### Persistence
No change.

### Backend API
No change. `GET /api/dashboard/running`, its exact JSON and its errors stay as defined in [001 specs.md](../001-running-dashboard/specs.md#get-apidashboardrunning). The tiles use only these existing fields: `weeklyDistance.weeks[].{isCurrentWeek, distanceKm, runCount}`, `heartRateTrend.minRunsForComparison`, `heartRateTrend.types[].{typeKey, hasEnoughData, points, trend.changeBpm}` and `workoutPaceComparison.types[].{typeKey, label, hasEnoughData, current, previous, changeSecondsPerKm}`. TS interfaces in `core/api/running-dashboard.models.ts` are unchanged.

### Frontend (Angular)
- **Routes:** unchanged (`''` → dashboard, `'connect'` → Connect, `'**'` → `''`).
- **Theme**
  - `src/styles.scss` holds the tokens (AC1.1), base `body`/`h1`/`a`/`button`/`select` styles, the shared `.card`, `.notice` (the banner style reused by the sync banner and the Connect message), `.empty`/`.note`, the `tone-*` classes and the `:focus-visible` rule.
  - `index.html` gets the two meta tags (AC1.3).
  - Component styles only use `var(--color-*)`.
- **Palette:** `shared/chart/palette.ts` has the same values as the CSS tokens (ECharts draws on a canvas and can't read CSS variables). New `shared/chart/chart-theme.ts` exports pure helpers, `categoryAxisStyle()`, `valueAxisStyle()`, `tooltipStyle()` and `baseChartStyle()`, that all three builders spread into their options (AC9.1).
- **Components**
  - `shared/spinner/ring-spinner.ts` (`RingSpinner`, selector `app-ring-spinner`, standalone, `OnPush`, no inputs): the inline SVG and CSS animation (AC3.2–AC3.3).
  - `features/running-dashboard/headline-tiles/headline-tiles.ts` (`HeadlineTiles`, selector `app-headline-tiles`, `OnPush`): `dashboard = input.required<RunningDashboardResponse>()`, `tiles = computed(() => buildHeadlineTiles(this.dashboard()))`. Renders `@for (tile of tiles(); track tile.id)` with plain interpolation only.
  - `RunningDashboardPage`: the loading case renders the spinner + two texts in a `role="status"` wrapper. The loaded, non-empty case renders `<app-sync-banner>`, then `<app-headline-tiles [dashboard]="data">`, then the cards. State handling and `retry()` are unchanged.
  - `SyncBanner`, `ConnectPage`, `WeeklyDistanceChart`, `HeartRateTrendChart`, `WorkoutPaceComparisonChart`: restyle only, plus the `tone-*` class on the change texts (AC8.3). No text or logic changes.
  - `App` shell: `app.scss` restyled; the footer stays in `app.html`.
- **States**

| View | Loading | Empty | Error | Normal |
|---|---|---|---|---|
| Dashboard page | Ring spinner + "Loading your runs…" + "The first import can take a minute."; no tiles or cards | Banner (if any) + "No runs in the last 5 months" in a card; no tiles | "Couldn't load your dashboard." card + accent-outlined Retry | Banner (if any) → 3 tiles → 3 cards |
| Headline tiles | not rendered | not rendered (page empty); a single tile without data shows "—" + note (AC5.4, AC6.3, AC6.4, AC7.3, AC7.4) | not rendered | 3 tiles with values and tones |
| Chart cards | not rendered | 001 card empty texts, muted | not rendered | restyled charts (R9) |
| Sync banner | not rendered | shown if status ≠ ok | not rendered | shown if status ≠ ok |
| Connect page | n/a (static) | n/a | error/reason message in `.notice` | heading, text, accent button |

- **Charts:** types, axes, series, units and tooltip texts are as in 001. Only the styling changes (AC9.1–AC9.4). No new ECharts components are needed (`shadowBlur` and `borderRadius` are core item styles), so `echarts-setup.ts` stays the same.

### Files expected to change
Backend: none.

Frontend (`frontend/src/`):
- `styles.scss`, `index.html`
- `app/app.scss` (and `app/app.spec.ts` if the footer assertions change)
- `app/shared/chart/palette.ts` + new `palette.spec.ts` (contrast tests)
- `app/shared/chart/chart-theme.ts` (new)
- `app/shared/spinner/ring-spinner.ts` + `ring-spinner.spec.ts` (new)
- `app/shared/format/tone.ts` + spec, `app/shared/format/heart-rate.ts` + spec (new)
- `app/features/running-dashboard/headline-tiles/headline-tiles.ts`, `headline-tiles.model.ts` + `headline-tiles.spec.ts`, `headline-tiles.model.spec.ts` (new)
- `app/features/running-dashboard/running-dashboard-page.html|scss|ts|spec.ts`
- `app/features/running-dashboard/chart-options/weekly-distance.options.ts`, `heart-rate-trend.options.ts`, `workout-pace.options.ts`, `chart-options.spec.ts`
- `app/features/running-dashboard/sync-banner/sync-banner.ts` (+ spec)
- `app/features/running-dashboard/heart-rate-trend-chart/heart-rate-trend-chart.ts`, `workout-pace-comparison-chart/workout-pace-comparison-chart.ts`, `weekly-distance-chart/weekly-distance-chart.ts`, `dashboard-cards.spec.ts`
- `app/features/connect/connect-page.ts` (+ spec)
- `app/testing/dashboard-fixtures.ts` (tile fixtures: easy not enough data, no easy, no comparable workout, zero-run current week)

## Edge cases
| Case | Expected behaviour |
|---|---|
| No activities in range | Page-level empty state ("No runs in the last 5 months") in a dark card; no tiles. The banner still shows if the status is not ok. |
| Runs this week = 0 but older runs exist | "This week" tile shows "0.0 km · 0 runs" (valid data, neutral); the other tiles show their own data. |
| Monday morning before the first run of the week | Same as above: the current week exists with 0 runs. |
| Only easy runs | Workout pace tile: "—", "No workouts (e.g. 5x6min) in the last 60 days". |
| Only workouts, no easy runs | Easy-run HR tile: "—", "No easy runs in the last 5 months". |
| Most recent workout type lacks one window | Skipped; the tile uses the next type in backend order that has both windows (AC7.1). If none has both → AC7.3. |
| Manual activity (no GPS) | No new handling: its km count in "This week" (backend). It has no HR, so it isn't an easy-run HR point, which can lead to AC6.3. It has no pace, so it can't make a type comparable. |
| Activity without heart rate | Not in `points` (backend); can cause the AC6.3 note with the count of runs *with* HR. |
| Unit conversion | None in the frontend. km and bpm are shown as sent (1 decimal), pace change as integer s/km, paces as `m:ss` via `formatPace`. |
| Time zones / DST / week boundary | "This week" is the backend's `isCurrentWeek` (server time zone for "today", `start_date_local` for bucketing), so the tile always matches the highlighted bar. The browser's zone is not used. |
| `changeBpm` is `-0.0` | Shown as "0.0", neutral (AC6.2). |
| Sync `partial` / `rateLimited` / `stravaUnavailable` | Banner above the tiles; the tiles reflect the saved data, the same as the charts. |
| First import takes over a minute | The spinner keeps animating until the response arrives; no frontend timeout. No progress counter (non-goal). |
| Request fails after a long wait | Spinner replaced by the error card + Retry; Retry shows the spinner again (AC3.5). |
| Narrow mobile width | Tiles stack (AC4.3); cards and charts keep 100 % width as before. |
| HR selector switched | Tiles don't change (AC6.5). |

## Privacy
- **Data fields & sensitivity:** no new data. The tiles show the current week's km and run count (personal activity data), the easy-run HR change (**health data, GDPR Art. 9**, already shown in the HR card) and a workout pace change (personal activity data). They are built in the browser from the existing aggregated response; no names, descriptions, ids, GPS, streams or tokens are involved.
- **Storage & retention:** unchanged. Nothing new is stored in the backend; no `localStorage`/`sessionStorage`/cookies are added in the browser.
- **Scopes:** unchanged, `activity:read_all` only.
- **Applicable privacy-check items:**

| # | Status | How it's satisfied |
|---|---|---|
| 1–12 | ✅ unchanged | No backend, auth, token, logging or session change (AC11.1). |
| 13 | ✅ | Tile texts use normal Angular interpolation; tile labels and type labels are backend-generated (`NxMmin`) or fixed strings. No `[innerHTML]` or `bypassSecurityTrust*`. ECharts tooltip formatters are unchanged and see only numbers, dates and generated labels. |
| 14 | n/a | No state-changing endpoint added. |
| 15 | ✅ | No new endpoint or field; the response shape is unchanged. |
| 16 | ✅ | No web fonts, CDN, icon library or images: the spinner is inline SVG/CSS, and the system font stack is kept (AC1.5, AC3.6). No new npm dependency. |
| 17 | ✅ | "Powered by Strava" stays visible on every screen with AA contrast (AC2.1, AC10.1). |
| 18–20 | unchanged | As in 001 (item 18 still deferred). |
| 21 | ✅ | New frontend fixtures use invented numbers. |

## Test plan
All new frontend tests are prefixed `002` to avoid clashing with the 001 test names.

| AC | Test type | Test name |
|---|---|---|
| AC1.1, AC1.3, AC1.4, AC1.5, AC2.9 | review check (grep over `frontend/src`) | listed in the reviewer checklist: tokens present, no `prefers-color-scheme`, no colour literals in component styles, no external URLs, no old colours |
| AC1.2 | frontend unit | `002 R1 - palette exposes the dark theme colours` |
| AC2.1 | frontend component | `002 R2 - footer shows Powered by Strava on dashboard and connect` (+ 001 `R1 - app shell shows Powered by Strava`) |
| AC2.2, AC2.3, AC2.6, AC2.7 | frontend component + manual visual check | `002 R2 - card titles keep their text`; manual: check cards, heading, select and empty states at 1280 px and 375 px |
| AC2.4 | frontend component | `002 R2 - sync banner uses notice style and keeps texts` |
| AC2.5 | frontend component | `002 R2 - error state shows alert card and retry button` |
| AC2.8 | frontend component | `002 R2 - connect page button keeps login href and message uses notice style` |
| AC3.1 | frontend component | `002 R3 - loading shows ring spinner above loading text and note` |
| AC3.2, AC3.3 | frontend component + manual visual check | `002 R3 - spinner renders track and accent arc`; manual: arc fills and turns |
| AC3.4 | frontend component | `002 R3 - loading state is a polite live region and spinner is aria hidden` |
| AC3.5 | frontend component | `002 R3 - spinner removed on response and shown again on retry` |
| AC3.6 | review check | no new dependency in `package.json`, no asset files |
| AC4.1 | frontend component | `002 R4 - three tiles render between banner and weekly card in order` |
| AC4.2 | frontend component | `002 R4 - tile shows label value unit and detail` |
| AC4.3 | manual visual check | 1280 px: one row; 375 px: stacked, no horizontal scroll |
| AC4.4 | frontend component | `002 R4 - no tiles in loading error or empty state` |
| AC4.5 | frontend component (HttpTestingController) | `002 R4 - tiles make no extra request` |
| AC5.1, AC5.2 | frontend unit (`buildHeadlineTiles`) | `002 R5 - this week tile shows current week km and runs`, `002 R5 - zero and one run wording` |
| AC5.3 | frontend unit | `002 R5 - this week tile matches highlighted bar` |
| AC5.4 | frontend unit | `002 R5 - missing current week shows dash` |
| AC6.1, AC6.2 | frontend unit | `002 R6 - easy hr tile shows signed change and tone` |
| AC6.3 | frontend unit | `002 R6 - easy hr tile not enough data note` |
| AC6.4 | frontend unit | `002 R6 - no easy runs shows dash` |
| AC6.5 | frontend component | `002 R6 - hr selector does not change tile` |
| AC6.6 | frontend unit (`formatBpmChange` + `hrTrendText`) | `002 R6 - tile and trend note use same bpm format` |
| AC7.1, AC7.2 | frontend unit | `002 R7 - workout tile uses most recent comparable type`, `002 R7 - slower and same pace wording` |
| AC7.3, AC7.4 | frontend unit | `002 R7 - no comparable workout shows dash`, `002 R7 - no workouts shows dash` |
| AC7.5 | frontend unit | `002 R7 - workout tile text matches pace card change text` |
| AC8.1 | frontend component | `002 R8 - tile value has tone class` |
| AC8.2 | frontend unit | `002 R8 - tone for negative positive and zero change` |
| AC8.3 | frontend component | `002 R8 - pace and hr card change texts have tone class` |
| AC8.4 | review check | `--color-worse` / `palette.worse` only used by `tone-worse` |
| AC9.1 | frontend option builder | `002 R9 - all charts use dark axis grid and tooltip style` |
| AC9.2 | frontend option builder | `002 R9 - weekly bars secondary with accent current week` (replaces the colour part of 001 `R8 - weekly options…`) |
| AC9.3 | frontend option builder | `002 R9 - hr points are thin glowing line with treadmill in secondary` |
| AC9.4 | frontend option builder | `002 R9 - pace bars secondary and accent with rounded tops` |
| AC9.5 | frontend option builder | existing 001 R8/R9/R11 data and tooltip tests unchanged |
| AC10.1, AC10.2 | frontend unit (WCAG contrast helper in spec) | `002 R10 - text colours reach 4.5 to 1 on all surfaces`, `002 R10 - chart colours reach 3 to 1 on surface` |
| AC10.3 | manual check (keyboard Tab) | focus ring visible on Connect link, Retry, select |
| AC10.4 | review check | no `opacity` on text elements |
| AC11.1 | review check (diff) | no `backend/` or `core/api` changes |
| AC11.2, AC11.3 | full suite | `npm run lint && npm test -- --watch=false && npm run build`; backend `./gradlew ktlintCheck detekt test` still green |

## Out of scope
- Light mode and follow-system theme.
- Whoop's logo, name, fonts or branding (style inspiration only).
- New Strava data, new metrics, new backend fields or endpoints.
- New charts or widgets other than the three headline tiles.
- A live "N runs imported" counter or any progress reporting during loading (intent decision 2).
- Any change to what the charts compute (9 weeks, 5-month HR window, 30/30-day comparison).
- Responsive/platform work beyond keeping desktop and mobile widths usable as before.

## Suggested follow-ups
- Respect `prefers-reduced-motion` (slow or stop the spinner animation) for accessibility.
- Live import progress ("40 of 130 runs"): needs backend progress reporting (intent decision 2).
- Use Strava's official "Connect with Strava" button and "Powered by Strava" logo assets, as the Strava brand guidelines recommend (the app has used styled text since 001).
- Make a tile clickable to scroll to its chart.
- Generate `palette.ts` and the CSS tokens from one source to remove the duplication.

## Risks & decisions to confirm
1. **Loading text split into two lines.** "Loading your runs…" plus the note "The first import can take a minute." replaces the single line of 001 AC13.1, to match S1 ("spinner above the text … and the note"). Its 001 test is updated. Confirm, or keep the old one-line text under the spinner.
2. **Tiles built in the frontend.** They only *select* existing response fields (current week, easy `changeBpm`, first comparable pace type) and do no arithmetic, so the backend and JSON stay unchanged, as the intent constraint asks. This deviates slightly from code-standards A6 ("endpoints return chart-ready data"). The alternative is a `headline` block in the response, which means a backend change. Recommended: frontend.
3. **"Most recent workout type"** = the first type in the backend's existing order (by its most recent run, paced or not) that has a paced run in both windows.
4. **Proposed colour values** (AC1.1): bg `#0b0d0f`, card `#17191c`, raised `#22262a`, accent `#00f19f`, secondary `#6f8299`, worse `#ff6b4a`, text `#f2f4f5`, muted `#9aa3ad`. All pass the AC10 contrast thresholds by calculation; confirm the look.
5. **Visible colour changes from 001:** the current-week bar goes from Strava orange to the accent; HR treadmill points stay purple (`#a78bfa`, a dark-theme purple; the user asked to keep purple after the first build); the Connect button goes from Strava orange to the accent with dark text (white on Strava orange is only about 3.4:1, which fails AC10).
6. **Errors and warnings don't use red-orange.** That colour is reserved for "getting worse" (decision 1), so the sync banner, error card and Connect message use a neutral notice style with a blue-grey edge.
7. **Some criteria can only be checked by hand or by review:** the "Whoop feel", the spinner animation, responsive layout, focus rings and the grep-based checks (AC1.4, AC2.9) can't be fully covered by the test runner, because global styles and canvas rendering aren't checked in unit tests.
8. **Colour tokens are duplicated** in `styles.scss` and `palette.ts` (ECharts canvas can't read CSS variables). AC1.1 and AC1.2 pin both to the same values.
