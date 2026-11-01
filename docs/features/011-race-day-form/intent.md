# Intent: Race-day form projection

**Feature ID:** 011 · **Status:** Approved · **Date:** 2026-10-31

## Problem
About 5 weeks before my marathon on 6 December 2026, I can see my current fitness and fatigue, but not whether I will arrive fresh without losing fitness. I have a taper in my plan, but no way to see what it does to my numbers by race day.

## Who & when
Me, a few times a week over the next 5 weeks, checking that the build and taper are on track, and adjusting the plan if needed.

## Desired outcome
I open the dashboard and see one chart from 6 weeks ago to race day showing fitness, fatigue and form. Past days reflect what I actually ran. Future days reflect my planned sessions. I can tell at a glance whether I am projected to be fresh but still fit on 6 Dec.

## Success criteria
- S1: I see a chart covering the last 6 weeks through race day (6 Dec) with fitness, fatigue and form lines.
- S2: Past days are drawn solid and planned days dashed, so I can tell actual from projected.
- S3: A "Race day" marker sits on 6 Dec.
- S4: A tile above the chart shows race-day form and fitness, with fitness relative to my peak over the last 6 months, e.g. "Form +18 · Fitness 61 (−4 from peak)".
- S5: A shaded "ready" band (form at 5–25% of fitness) is visible, clearly labelled as a rough guide, not a rule.
- S6: A planned day with no session counts as rest. A session already run today counts as done. A session planned today but not yet run counts as planned.
- S7: The card sits directly under Training load.
- S8: If the plan or LTHR_BPM is not set, the card is hidden and a short message says which is missing.
- S9: Changing the plan (for example, a longer taper) changes the projection when I next load the dashboard.
- S10: A planned day with distance but no minutes is projected from its distance (see Constraints) and is not counted as rest.
- S11: If SubT rate is estimated because I lack SubT runs, a short note says it is an estimate.
- S12: If I lack easy runs too, only the past part of the chart is shown, with the message "Not enough runs with heart rate yet to project the plan."

## Non-goals
- Not recommending or auto-adjusting a taper.
- Not treating other races in the plan as race day.
- Not penalising or reconstructing past skipped sessions (past days use only what was run).
- Not predicting race time or performance.
- Not editing the plan from this card.

## Constraints
- Fitness is the 42-day load average and fatigue the 7-day average, with form = fitness − fatigue, using the same load as 005.
- Race day is the plan's end date only (6 Dec 2026).
- Planned sessions have minutes (easy, SubT) and volume but no heart rate, so their load is estimated from my own runs over the last 6 months, per minute of easy running and per minute of SubT.
- A rate is learned from my own runs only when there are at least 3 runs of that kind.
- If SubT runs are fewer than 3, the SubT rate is estimated as 2x the easy rate (the same 2x weight the training-load zones use).
- If easy runs are fewer than 3, no plan projection is shown.
- Planned days with distance but no minutes: minutes are estimated from the distance at the plan's easy target pace.
- "Peak" fitness is the highest fitness over the last 6 months.
- The card is hidden when the plan or LTHR_BPM is not configured.

## Data sensitivity
Heart rate (via load from HR streams and LTHR_BPM) and training history. No GPS or tokens are shown.

## Related features
- 005 training load: supplies the fitness, fatigue and load definitions and the 2x zone weight. The new card must stay consistent with its numbers and sits below it.
- 004 calendar-like build: supplies the plan (planned easy minutes, SubT minutes, volume per day, end date) and the race-day date.
- 010 workout pace picker: touches workout types and could overlap on how sessions are classified as easy or SubT (assumed).

## Assumptions
- The projected load of a planned day is its minutes multiplied by my average load per minute for that session type (assumed).
- Form % of fitness uses the same day's fitness (assumed).
- The plan provides an easy target pace usable for the distance-to-minutes estimate (assumed). If it doesn't, the spec writer should flag it.
- "Runs of that kind" means runs with heart-rate data classified as easy or SubT (assumed).

## Open questions
None.
