# Intent: Long run type on the HR chart

**Feature ID:** 006 · **Status:** Approved · **Date:** 2026-10-31

## Problem
On the "Heart rate by run type" chart, every run that is not a workout is an "easy run", including my 20 km+ long runs. Long runs naturally have a higher average heart rate, so they push the easy-run line up and make it look like I am getting less fit. I also cannot compare my long runs with each other.

## Who & when
Me, glancing at the dashboard after a run or when planning the week, checking whether my fitness trend is right for easy runs, long runs and each workout type.

## Desired outcome
The "Heart rate by run type" chart has a new run type, "Long runs", containing every run over 20 km. Long runs are compared with each other, and no longer affect the easy-run line.

## Success criteria
- S1: A run of more than 20.0 km that is not a workout appears only under "Long runs", never under "Easy runs".
- S2: A run of exactly 20.0 km or less stays in "Easy runs".
- S3: A run over 20 km whose name/description matches a workout pattern (e.g. "3x20min") stays a workout and is not a long run.
- S4: Treadmill runs over 20 km count as long runs and keep their treadmill dot.
- S5: The chart order is Easy runs, then Long runs, then workout types.
- S6: Long runs get the same treatment as other types: last 5 months, average HR per run, rising-HR highlight (feature 003) and a trend line.
- S7: The easy-run line no longer includes any run over 20 km.
- S8: With too few long runs for a comparison, the type is shown as such, not as a broken or empty graph (feature 001, S8).

## Non-goals
- No change to the workout pace comparison or any other chart.
- No other new run types, and no configurable threshold.
- No other distance splits for easy runs (for example 10 km+).
- No change to how heart rate per run is computed.
- No editing of Strava data.

## Constraints
- Threshold: strictly more than 20.0 km, using the distance Strava recorded.
- Units: kilometers.
- Workout classification takes priority over long run.
- Built on its own branch from main.

## Data sensitivity
Heart rate data (personal health data). No GPS, new tokens or other personal data.

## Related features
- 001-running-dashboard: owns the "Heart rate by run type" chart. This supersedes its decision 4 (easy runs of all lengths compared together) for runs over 20 km.
- 003-hr-rising-highlight: long runs use the same rising-HR highlight.
- 002-dark-dashboard-redesign: colour semantics apply unchanged.

## Assumptions
- The 20 km threshold is fixed, not user-configurable (assumed).
- Runs that become long runs are reclassified retroactively across the whole 5-month window (assumed).
- The label is "Long runs", matching "Easy runs" (assumed).

## Open questions
None.
