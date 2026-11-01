# Intent: Normalized VO2max (hill- and drift-adjusted)

**Feature ID:** 016 · **Status:** Draft · **Date:** 2026-11-01

## Problem
My dashboard VO2max has been trending down during the Valencia build, but analysis of my Strava data shows this is mostly an artefact of the estimator, not lost fitness. Hillier routes, heart-rate drift on long runs and the sheer length of long runs pull the estimate down (e.g. the 34.7 km run on 1 Nov scores ~49 against ~57-60 for easy runs, and dominates the week because it is weighted by duration). I can't tell whether I am really gaining or losing fitness, and I trust the race-prediction tiles less.

## Who & when
Me, checking weekly (e.g. Sunday evenings, after the long run) during the Valencia Marathon build (race 6 Dec 2026, goal: PB), to judge whether fitness is trending the right way.

## Desired outcome
The VO2max section gets two tabs: "Vo2" (today's estimate, unchanged) and "Normalized Vo2" (a new estimate that corrects for hills, long-run drift and run length). Each tab shows its own current value, weekly series and race predictions, so I can compare them and read the real fitness trend from the normalized one.

## Success criteria
- S1: The VO2max section has two tabs, "Vo2" and "Normalized Vo2", and I can switch between them.
- S2: The "Vo2" tab shows exactly the same current value, weekly chart and race tiles as before this feature.
- S3: The "Normalized Vo2" tab shows its own current value, its own weekly series and its own 5K / 10K / half / marathon predictions, laid out like the "Vo2" tab.
- S4: A hilly and a flat run at the same effort give about the same normalized score; moving my 15 km easy loop to the hillier route no longer produces a visible drop.
- S5: A long run with strong HR drift (like the 1 Nov run) no longer scores far below my easy runs in the normalized series; it is judged on its early portion only.
- S6: A 3-hour long run counts as one run in its week, the same as a short easy run, not more.
- S7: The "Normalized Vo2" tab carries a short note saying the estimate is hill- and drift-adjusted.
- S8: Weeks with no qualifying data are visibly distinguishable from weeks with a value, as in 008.

## Non-goals
- No change to the existing "Vo2" estimate, its chart or its tiles.
- No heat or temperature correction.
- No changes to treadmill or hot-gym handling.
- No new Strava OAuth scopes.
- No per-run drill-down and no writing back to Strava.

## Constraints
- Hills: use grade-adjusted pace (Strava GAP where available, otherwise a per-km-lap correction from elevation/grade).
- Drift: for easy and long runs, use only the early portion of the run (e.g. first ~60 minutes, or until HR drift begins) instead of the whole-run average. Long runs are not excluded.
- Weighting: one vote per run in the weekly mean.
- The tab labels are "Vo2" and "Normalized Vo2", as the user named them.
- Max HR 193 was used in the analysis (the 008 setting is ~194, configurable); the normalized estimate should use the same configured max HR (assumed).
- Metric units, Monday-Sunday weeks, 6-month window as in 008 (assumed).

## Data sensitivity
Heart rate, pace, and elevation/lap data already stored; body metrics (max HR). No new Strava scopes expected. No raw GPS coordinates needed or to be stored or logged; tokens stay in the backend per project rules.

## Related features
- 008 vo2max-estimator: this feature adds a second, parallel estimate next to it; the existing one must stay untouched. Treadmill rep-pace handling, race-prediction method and chart layout are likely reused.
- 013 long-run-hr-drift: related drift concept, but 013 deliberately uses plain speed and stores only derived drift; this feature needs grade-adjusted pace and a cut-off point inside the run, so data needs may overlap (extra per-run data, Strava call budget under 009).
- 009 background-sync: any new per-run data must fit the shared call budget, and opening the dashboard must not trigger Strava calls (assumed).
- 005 training-load, 006 long-run-hr, 014 easy-efficiency-factor: also read HR/pace per run; no direct conflict expected.

## Assumptions
- The normalized estimate keeps 008's core method (Daniels oxygen cost divided by Swain %VO2max from %HRmax) and only changes the inputs and weighting. (assumed)
- Qualifying-run rules from 008 (minimum 20 minutes, treadmill handling, interval reps only) carry over unchanged, except where this feature overrides them. (assumed)
- Outdoor interval workouts keep using rep segments only; the early-portion rule applies to easy and long runs. (assumed)
- Race predictions on the normalized tab use the same VDOT-based method and marathon-optimism note as 008. (assumed)
- The tabs replace the single view in the existing VO2max card, which keeps its place under the Training load card. (assumed)
- The last-7-days, falling back to last 4 weeks, "current value" logic from 008 applies to both tabs. (assumed)

## Open questions
1. Should "Vo2" be the default tab when the page opens (assumed yes), or "Normalized Vo2"?
2. Should the normalized chart also show the original series as a faint comparison line, or keep the two tabs fully separate?
3. When grade-adjusted pace is not available from Strava and a run has no usable elevation data, should the run be skipped or scored at plain pace?
4. For "early portion" on long runs, is a fixed cut-off (first 60 minutes) fine, or do you want it to stop earlier when drift starts?
5. Should runs shorter than the cut-off (e.g. a 45-minute easy run) still use the whole run (assumed yes)?
