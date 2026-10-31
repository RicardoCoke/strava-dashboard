# Intent: Workout pace picker

**Feature ID:** 010 · **Status:** Draft · **Date:** 2026-10-31

## Problem
The "Workout pace: last 30 days vs previous 30 days" graph shows every workout type at once as two average-pace bars. I can't focus on one workout type (e.g. 5x6min) or see how my pace moved run by run. It doesn't behave like "Heart rate by run type", which is what I expected. Separately, the "Plan notes" panel in the Build calendar is clutter I don't want.

## Who & when
Me, after a workout or when reviewing training, checking whether my pace for a given workout type is improving.

## Desired outcome
I pick a workout type (5x6min, 4x8min, ...) and see one point per run showing its average rep pace over time, like the run-type picker in "Heart rate by run type". The "last 30 days vs previous 30 days" change stays visible underneath as a summary line. The Build calendar no longer shows Plan notes.

## Success criteria
- S1: I can choose a workout type from a selector, which shows how many runs each type has.
- S2: For the chosen type I see one point per run with its average rep pace, covering the last 5 months.
- S3: Below the chart I still see the last 30 days vs previous 30 days pace and the change between them for the chosen type.
- S4: Treadmill workouts appear with their rep paces, using the same rule as the VO2max feature, and are visibly marked with a different marker shape from outdoor runs.
- S5: The Build calendar no longer shows the Plan notes panel.

## Non-goals
- No change to "Heart rate by run type".
- No new workout types or new ways of detecting them (assumed).
- No replacement for the information Plan notes held; it is simply removed (assumed).
- No other Build calendar changes.

## Constraints
- Time range for per-run points matches "Heart rate by run type" (5 months).
- Treadmill rep paces follow the same rule as feature 008 (taken from the activity description).

## Data sensitivity
Pace and run history derived from Strava activities. No GPS shown. No tokens involved in the UI.

## Related features
- 001-running-dashboard: hosts both charts; this reworks the workout pace card.
- 004-calendar-like-build: Plan notes removal changes its target paces area.
- 008-vo2max-estimator: shares the treadmill rep-pace rule.
- 003/006 (HR highlight, long-run HR): similar selector pattern to reuse in look and feel (assumed).

## Assumptions
- Pace is shown as average rep pace per run, not whole-run pace (assumed).
- A default workout type is preselected on load, e.g. the most recent or most frequent (assumed).
- Types with too few runs for a 30-day comparison show a "not enough data" message instead of a change figure (assumed).
- The target paces content other than Plan notes stays as is (assumed).

## Open questions
1. Which workout type should be preselected when the page opens (most recent, or most runs)?
2. Should runs without parsable rep paces (for example, treadmill with no description) be skipped silently or listed as excluded?
