# Spec: Chart pinch-zoom on touch screens

**Feature ID:** 024 · **Status:** Approved · **Intent:** [intent.md](./intent.md) · **Date:** 2026-11-14

## Summary
On touch devices, the shared chart wrapper adds an ECharts "inside" dataZoom on the horizontal axis to every chart's options, so a two-finger pinch zooms that chart's horizontal axis in and out. One-finger drags keep scrolling the page, and the browser does not zoom the page while you pinch on a chart. On desktop the wrapper adds nothing, so behaviour there stays exactly as it is today. Frontend only, done once in `shared/chart/`.

## Requirements
| ID | Requirement | Priority |
|---|---|---|
| R1 | On a touch device, pinching inside a chart's plot area zooms that chart's horizontal axis in (fingers apart) or out (fingers together), centred on the pinch. | Must |
| R2 | Zoom is clamped: zooming out stops at the full range, zooming in stops at 5% of the full range (20x). | Must |
| R3 | A pinch that starts on a chart never zooms the page, even when the chart cannot zoom any further. | Must |
| R4 | A one-finger drag over a chart scrolls the page as it does today. The chart does not pan and does not capture the touch. | Must |
| R5 | The page cannot be zoomed by accident in any browser: touch pinch and double-tap anywhere on the page, trackpad pinch and Ctrl + mouse wheel on desktop all leave the page at its normal size. Ctrl/Cmd + plus/minus and the browser's zoom menu still work. (Amended 2026-11-14.) | Must |
| R6 | Every chart starts at the full range each time it is created (reload, page switch). Zoom is not saved anywhere. | Must |
| R7 | On non-touch devices (primary pointer is not coarse), the wrapper does not change the chart options, so the mouse wheel and mouse drag over a chart behave as they do today. | Must |
| R8 | This applies to every chart that renders through `app-chart`, with no changes to the individual chart option builders. | Must |

## Acceptance criteria
### R1: Pinch zooms the horizontal axis
- **AC1.1** Given any options object and a touch device, when `withTouchZoom(options, true)` is called, then the result has exactly one `dataZoom` entry of `type: 'inside'` with `xAxisIndex: 0`, and no dataZoom on any y axis.
- **AC1.2** Given an iPhone showing any chart at full range, when the user spreads two fingers on the plot area, then the horizontal axis shows a narrower range around the pinch point and the vertical axis keeps its range. (Manual, on the device.)
- **AC1.3** Given a zoomed chart, when the user brings two fingers together on it, then the horizontal axis shows a wider range. (Manual.)

### R2: Zoom limits
- **AC2.1** Given `withTouchZoom(options, true)`, then the dataZoom has `start: 0`, `end: 100` and `minSpan: 5`.
- **AC2.2** Given a chart on an iPhone, when the user keeps pinching out, then the axis stops at the full data range and does not go past it. (Manual.)
- **AC2.3** Given a chart on an iPhone, when the user keeps pinching in, then the visible range stops shrinking at about one twentieth of the full range. (Manual.)

### R3: The page does not zoom while you pinch on a chart
- **AC3.1** Given `blockPageZoom(document)`, then `<html>` has `touch-action: pan-x pan-y`. A child can't widen its parent's `touch-action`, so this covers every chart. (Amended 2026-11-14: replaces the rule on `.chart`; tested as AC5.1.)
- **AC3.2** Given an iPhone and a chart already at full range, when the user pinches out on it, then neither the chart nor the page zooms. (Manual.)
- **AC3.3** Given an iPhone, when the user pinches on a chart's axis labels or legend (inside the chart box but outside the plot area), then the page does not zoom. (Manual.)

### R4: One-finger scrolling still works
- **AC4.1** Given `withTouchZoom(options, true)`, then the dataZoom has `moveOnMouseMove: false`, `moveOnMouseWheel: false`, `zoomOnMouseWheel: false` and `preventDefaultMouseMove: false`.
- **AC4.2** Given an iPhone, zoomed in or not, when the user drags one finger up or down starting on a chart's plot area, then the page scrolls and the chart's range does not change. (Manual.)
- **AC4.3** Given an iPhone, when the user taps a data point, then the tooltip shows as it does today. (Manual.)

### R5: The page cannot be zoomed by accident
- **AC5.1** Given `blockPageZoom(document)`, then `<html>` has the inline style `touch-action: pan-x pan-y`, so touch browsers neither pinch-zoom nor double-tap-zoom the page but still scroll it.
- **AC5.2** Given `blockPageZoom(document)`, when a cancelable `wheel` event with `ctrlKey` (what a trackpad pinch and Ctrl + wheel send) reaches the document, then its default is prevented. A `wheel` without `ctrlKey` is left alone, so normal scrolling works.
- **AC5.3** Given `blockPageZoom(document)`, when Safari's `gesturestart` or `gesturechange` event reaches the document, then its default is prevented (macOS Safari trackpad pinch).
- **AC5.4** Given an iPhone, when the user pinches or double-taps on a heading, a stat card or empty page space, then the page does not zoom. (Manual.)
- **AC5.5** Given a desktop browser (Chrome, Edge, Firefox, Safari), when the user pinches on the trackpad or turns the wheel with Ctrl held, then the page does not zoom; Ctrl + plus/minus still zooms it. (Manual.)

### R6: Every chart starts at full range
- **AC6.1** Given any options object, when `withTouchZoom` is called, then the dataZoom always has `start: 0` and `end: 100` (no stored state is read).
- **AC6.2** Given a zoomed chart on an iPhone, when the user reloads or switches page and comes back, then the chart shows its full range. (Manual.)

### R7: Desktop unchanged
- **AC7.1** Given any options object, when `withTouchZoom(options, false)` is called, then the same object is returned unchanged.
- **AC7.2** Given a desktop browser, when the user turns the mouse wheel over a chart, then the page scrolls and the chart does not zoom. (Manual.)

### R8: All charts, no per-chart changes
- **AC8.1** Given options that already have keys (`xAxis`, `series`, `grid`, ...), when `withTouchZoom(options, true)` is called, then every original key is kept unchanged and only `dataZoom` is added. The input object is not mutated.
- **AC8.2** Given the build, then `DataZoomInsideComponent` is registered in `echarts-setup.ts`, and no file under `features/` changes.
- **AC8.3** Given an iPhone, when the user pinches on each chart listed under "Spot-check list", then each one zooms as in R1. (Manual.)

## Traceability
| Success criterion (intent) | Covered by |
|---|---|
| S1: pinch in zooms the date axis, the page does not zoom | R1, R3, R8 |
| S2: pinch out goes back toward and up to the full view | R1, R2 |
| S3: one-finger drag scrolls the page, the chart does not pan or get stuck | R4 |
| S4: the page cannot be zoomed by accident in any browser | R3, R5 |
| S5: reload or page switch shows the full view | R6 |
| S6: on desktop the mouse wheel still scrolls the page | R7 |

## Design

### Data from Strava / Garmin
n/a. No API calls, no new data.

### Domain model & logic
n/a (no backend change).

### Persistence
n/a. Zoom state lives only inside the ECharts instance and goes away with the component.

### Backend API
n/a. No endpoints added or changed.

### Frontend (Angular)
- **Route / page:** none added.
- **Pure function** `withTouchZoom(options: EChartsCoreOption, touch: boolean): EChartsCoreOption` in `frontend/src/app/shared/chart/touch-zoom.ts`:
  - `touch === false` → return `options` as is (R7).
  - `touch === true` → return `{ ...options, dataZoom: [TOUCH_ZOOM] }` where
    ```ts
    const TOUCH_ZOOM = {
      type: 'inside',
      xAxisIndex: 0,          // every chart in the app has exactly one x axis
      filterMode: 'none',     // lines keep running to the plot edge; the y axis does not rescale
      start: 0,
      end: 100,
      minSpan: 5,             // max 20x zoom in
      zoomOnMouseWheel: false,
      moveOnMouseWheel: false,
      moveOnMouseMove: false, // no one-finger pan
      preventDefaultMouseMove: false, // otherwise ECharts blocks one-finger page scroll over the plot area
    };
    ```
- **`ChartComponent`** (`shared/chart/chart.ts`):
  - Works out touch once: `private readonly touch = matchMedia('(pointer: coarse)').matches;` (primary pointer is a finger. Touch laptops with a mouse count as desktop.)
  - `protected readonly chartOptions = computed(() => withTouchZoom(this.options(), this.touch));` and the template binds `[options]="chartOptions()"`.
  - Inputs (`options`, `label`) unchanged. No new inputs or outputs.
- **`blockPageZoom(doc: Document): void`** in `frontend/src/app/core/no-page-zoom.ts`, called once from `main.ts` before bootstrap (R3, R5):
  - `doc.documentElement.style.touchAction = 'pan-x pan-y'`: touch browsers scroll but never pinch- or double-tap-zoom the page. Child elements can't widen it, so this also covers every chart (R3) and replaces the earlier `.chart` rule.
  - `wheel` listener (`passive: false`) that calls `preventDefault()` only when `ctrlKey` is set: desktop trackpad pinch and Ctrl + wheel.
  - `gesturestart` and `gesturechange` listeners that call `preventDefault()`: macOS Safari trackpad pinch.
  - Keyboard zoom is not blocked (deliberate, see intent amendment).
- **`echarts-setup.ts`:** add `DataZoomInsideComponent` from `echarts/components` to `echarts.use([...])`. The slider dataZoom is not registered.
- **Reset (R6):** comes for free. Each page creates new chart components, and zoom is never stored. When a chart's `options` input changes (e.g. switching person), ngx-echarts replaces the whole option and the zoom resets to full range. That's fine and needs no extra code.
- **States:** loading, empty and error states stay with each feature component as they are today. The wrapper only renders in the normal state, so this feature adds no new states. A chart with one point or a few categories zooms harmlessly: ECharts always shows at least one category.
- **Chart:** axes, series, units and tooltips are not changed.

### Files expected to change
- `frontend/src/app/shared/chart/touch-zoom.ts` (new)
- `frontend/src/app/shared/chart/touch-zoom.spec.ts` (new)
- `frontend/src/app/shared/chart/chart.ts`
- `frontend/src/app/core/no-page-zoom.ts` (new, amendment)
- `frontend/src/app/core/no-page-zoom.spec.ts` (new, amendment)
- `frontend/src/main.ts` (amendment)
- `frontend/src/app/shared/chart/chart.spec.ts` (removed by the amendment: the `.chart` rule it tested moved to `<html>`)
- `frontend/src/app/shared/chart/echarts-setup.ts`

### Spot-check list (manual, on an iPhone)
Weekly distance, heart-rate trend, workout pace comparison (running dashboard) · efficiency factor · training load · race-day form · VO2max and normalized VO2max · wellness (two y axes) · recovery history · marathon pace · long-run drift.

## Edge cases
| Case | Expected behaviour |
|---|---|
| No activities / empty data | The feature shows its empty state and no chart renders, so nothing changes. |
| Chart with one point or very few categories | Pinch zooms harmlessly. ECharts keeps at least one category or point range visible. |
| Category axis (weekly bars, training load) vs time axis (HR trend, EF, long-run drift) | Both zoom the same way, by percent of the full range. |
| Chart with two y axes (wellness) | Only x axis 0 zooms. Both y axes keep their ranges (`filterMode: 'none'`). |
| Mark lines (race-day form race day, threshold lines) | They stay drawn. A mark line outside the visible range is clipped. |
| Pinch at full range | Chart stays as it is. The page does not zoom (open question 2). |
| Pinch starting on the chart's axis labels or legend | Page does not zoom (`touch-action` on `<html>`). The chart does not zoom either (ECharts only zooms from the plot area). |
| Touch laptop / iPad with trackpad | Decided by `(pointer: coarse)`. If the primary pointer is a mouse or trackpad, it behaves like desktop. |
| Desktop trackpad pinch (ctrl+wheel) | Page does not zoom (R5). Charts don't zoom either: there is no dataZoom on desktop. |
| Ctrl/Cmd + plus/minus, browser zoom menu | Still zooms the page, on purpose. |
| Manual activities without GPS, activities without HR, unit conversion, time zones | Not affected. No data or bucketing changes. |

## Privacy
- Data fields & sensitivity: none read or added. Charts that already show heart-rate data are re-rendered with a different view range only.
- Storage & retention: nothing stored. Zoom is not written to localStorage, sessionStorage, URLs or the backend.
- Scopes / API keys: n/a. No Garmin or Strava calls.
- Applicable privacy-check items: #8 (no new `console` output: nothing logged) ✅ · #13 (no new formatters or HTML) ✅ · #16 (no new dependency or CDN: `DataZoomInsideComponent` is part of the bundled `echarts` package) ✅ · #21 (test fixtures use invented options) ✅. All other items n/a.

## Test plan
| AC | Test type | Test name |
|---|---|---|
| AC1.1 | frontend unit (`touch-zoom.spec.ts`) | `R1 - adds one inside dataZoom on x axis 0 for touch` |
| AC2.1, AC6.1 | frontend unit | `R2 - zoom starts at full range and is limited to 20x` |
| AC4.1 | frontend unit | `R4 - disables wheel, pan and mouse-move preventDefault` |
| AC7.1 | frontend unit | `R7 - returns options unchanged when not touch` |
| AC8.1 | frontend unit | `R8 - keeps existing keys and does not mutate input` |
| AC3.1, AC5.1 | frontend unit (`no-page-zoom.spec.ts`) | `R5 - touch browsers only pan the page` |
| AC5.2 | frontend unit | `R5 - blocks ctrl+wheel zoom but not scrolling` |
| AC5.3 | frontend unit | `R5 - blocks Safari gesture zoom` |
| AC8.2 | build + review | `npm run build` passes; diff touches no `features/` files |
| AC1.2, AC1.3, AC2.2, AC2.3, AC3.2, AC3.3, AC4.2, AC4.3, AC5.4, AC6.2, AC8.3 | manual on iPhone (Safari) | user runs the spot-check list |
| AC7.2, AC5.5 | manual on desktop | mouse wheel over each chart scrolls the page; trackpad pinch and Ctrl + wheel don't zoom the page |

## Out of scope
- Home-screen app, PWA or native app.
- Mouse-wheel or trackpad zoom on desktop.
- One-finger sideways panning of a zoomed chart.
- A reset button or any other on-screen control.
- Zooming the vertical axis.
- Remembering zoom across reloads or page switches.

## Suggested follow-ups
- Double-tap on a chart to reset to the full view, if pinching out turns out to be tedious.
- Let the y axis rescale to the visible window (`filterMode: 'filter'`) if the fixed y axis looks too flat when zoomed in.

## Risks & decisions to confirm
- **Decision: touch is detected with `(pointer: coarse)` and the dataZoom is only added on touch devices.** This keeps desktop exactly as it is (S6). I chose it because ECharts' inside dataZoom can block the page's wheel scroll over the plot area even with `zoomOnMouseWheel: false`. The downside is that a touch laptop whose primary pointer is a mouse gets no pinch zoom.
- **Decision: max zoom-in is 20x (`minSpan: 5`), chosen by the user on 2026-11-14.** For a year of daily data that's about 18 days on screen.
- **Decision: the y axis stays fixed while zoomed (`filterMode: 'none'`).** This keeps lines continuous to the plot edges and matches "don't zoom the vertical axis". The other option, `'filter'`, rescales y to the visible points but can cut lines at the edges.
- **Risk: iOS Safari touch behaviour can only be checked on the device.** The settings `preventDefaultMouseMove: false` (one-finger scroll) and `touch-action: pan-x pan-y` (no page zoom) are based on how ECharts and the browser normally handle touch, and the project uses ECharts 6.1. Automated tests cover only the option builder and the CSS. If one-finger scroll gets stuck on the device, check `preventDefaultMouseMove` first.
- **Note:** the intent calls the long-run drift chart's horizontal axis "not a date", but in code it is `type: 'time'`. Either way it zooms like the others, as the intent requires.
