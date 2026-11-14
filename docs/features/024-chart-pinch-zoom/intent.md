# Intent: Chart pinch-zoom on touch screens

**Feature ID:** 024 · **Status:** Approved · **Date:** 2026-11-14

## Problem
On an iPhone the graphs are small and dense, and there is no way to zoom into or out of them. The user wants to look closer at a stretch of dates on a chart without the whole page zooming.

## Who & when
Me (and Marina), on an iPhone, looking at the dashboard charts and wanting a closer look at a particular period.

## Desired outcome
On a touch screen, pinching on a chart zooms only that chart's date axis, in or out. One-finger scrolling still moves the page up and down. Every chart in the app behaves the same way.

## Success criteria
- S1: On an iPhone, pinching in on any chart zooms in on its date axis and the rest of the page does not zoom.
- S2: Pinching out on a zoomed chart brings it back toward (and up to) the full view.
- S3: Dragging one finger over a chart scrolls the page as it does today; the chart does not pan or get stuck.
- S4: The page itself cannot be zoomed by accident in any browser: not by a pinch or double-tap on a touch screen, and not by a trackpad pinch or Ctrl + mouse wheel on desktop. (Changed 2026-11-14, see "Amendment" below.)
- S5: Reloading or switching page shows every chart at the full view again.
- S6: Desktop behaviour is unchanged: the mouse wheel over a chart still scrolls the page.

## Non-goals
- Home-screen app, PWA or native app (explicitly excluded by the user).
- Mouse-wheel or trackpad zoom on desktop.
- One-finger sideways panning of a zoomed chart.
- A reset button or any other extra on-screen control.
- Zooming the vertical axis.
- Remembering zoom across reloads or page switches.

## Constraints
- Touch only; the horizontal (date) axis only.
- Applies to all charts, switched on once in the shared chart wrapper (`frontend/src/app/shared/chart/chart.ts`; ECharts modules are registered in `echarts-setup.ts`).
- Frontend only.

## Data sensitivity
None. No backend, data or privacy change.

## Related features
None directly. Touches the shared chart wrapper used by the charts from earlier features (e.g. 001, 005, 006, 013, 014, 016, 023), so each of those charts should be spot-checked.

## Assumptions
- To look at another period, the user pinches out and then pinches in elsewhere on the chart (stated, as the consequence of no panning).
- A chart with very few points or a non-date axis simply stays as it is, or zooms harmlessly (assumed).
- Zoom limits (how far in or out) are left to the spec writer; "out" stops at the full range (assumed).
- Android and other touch devices should behave the same, though iPhone is the target (assumed).
- Nothing has been tried on a real iPhone yet; the touch behaviour can only be confirmed by the user on the device.

## Amendment (2026-11-14, after the first release)
The user asked to lock page zoom so a pinch can't zoom the page by accident instead of the graph ("Yes, lock it"), and added: "It should work for not only iphone but every brwoser". This replaces the earlier answer "Keep it" to "A pinch outside a chart still zooms the whole page".
- Locked: touch pinch and double-tap zoom anywhere on the page, trackpad pinch and Ctrl + mouse wheel on desktop.
- Not locked: deliberate zoom with Ctrl/Cmd + plus/minus or the browser's zoom menu. These are never accidental, and the menu cannot be blocked by a page anyway.
- Trade-off accepted: small text can no longer be pinched bigger on the phone.

## Open questions
None. Answered on 2026-11-14:
1. No chart is excluded. The long-run drift chart, whose horizontal axis is not a date, zooms its horizontal axis like the others.
2. A pinch on a chart that is already fully zoomed out does nothing; the chart keeps the pinch and the page does not zoom.
