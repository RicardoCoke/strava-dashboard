# Intent: Highlight rising heart rate on the HR chart

**Feature ID:** 003 · **Status:** Approved · **Date:** 2026-10-30

## Problem
On the "Heart rate by run type" chart, the line is one colour all the way. I have to study the numbers to notice when my heart rate at the same effort has stopped improving and started creeping up, which is the early sign I'm getting less fit, tired or overreaching.

## Who & when
Me, glancing at the dashboard after a run or when planning the week, to see whether my fitness trend for easy runs and each workout type is still going the right way.

## Desired outcome
The line on the heart rate chart changes colour to show direction of trend. It stays in the improving colour while my recent average heart rate is falling, and turns red from the point where that recent average starts to rise, staying red while it keeps climbing. Single noisy runs should not flip the colour.

## Success criteria
- S1: On the "Heart rate by run type" chart, I can see at a glance where along each line the trend turned from improving to getting worse.
- S2: The line is red-orange from the turning point onward for as long as the recent average keeps rising, and returns to the improving colour once the recent average falls again (assumed).
- S3: One unusually high or low run does not by itself flip the colour.
- S4: This works independently for each run type shown (easy runs and each NxMmin workout type), so one type can be red while another is green.
- S5: The red matches the existing "getting worse" colour in the dark dashboard, so the meaning is consistent with the rest of the page.

## Non-goals
- Not colouring every single run-to-run rise (option "a" was rejected).
- Not changing which runs or run types appear on the chart, or how heart rate per run is computed.
- Not adding alerts, notifications or written advice about overtraining.
- Not adding a separate chart or table for the running average (assumed).

## Constraints
- Colour rule from feature 002: green accent for improving, red-orange #ff6b4a reserved for "getting worse".
- Running average is calculated in the backend, per the repo's rule that calculations live there.
- Trend is based on the average of the last few runs (user's example: last 3).

## Data sensitivity
Heart rate data (personal health data) is involved. No GPS or tokens are newly touched.

## Related features
- 001-running-dashboard: owns the "Heart rate by run type" chart this changes.
- 002-dark-dashboard-redesign: defines the green and red-orange colour semantics this must follow.

## Assumptions
- The window is 3 runs, as suggested, and is not user-configurable (assumed).
- The average is taken per run type, over that type's runs in date order (assumed).
- "Starts to rise" means the average is higher than at the previous run; ties keep the current colour (assumed).
- The first runs, before a full window exists, have no trend and use the neutral/improving colour (assumed).
- The colour change applies to the line segment leading into the turning run's point onward (assumed).
- A legend or tooltip hint explaining the colours is not required (assumed).

## Decisions (2026-10-30, approved by the user)
1. The rolling window is 3 runs, fixed.
2. The line turns back to the improving colour when the recent average starts falling again.
3. The colour only changes after a rise (or fall) of at least 1 bpm in the recent average, so small wobbles don't flip it.
