# Intent: Week vs last week headline tiles

**Feature ID:** 007 · **Status:** Approved · **Date:** 2026-10-31

## Problem
The three headline tiles at the top of the dashboard compare things over long windows (5 months for HR, 30 vs 30 days for workout pace). They don't tell me how this week is going compared with last week. The "This week" km tile shows only a number, so I can't tell whether I am running more or less than last week.

## Who & when
Me, checking the dashboard during the week and after each run, to see whether this week is on track compared with last week.

## Desired outcome
The headline tiles become a direct this-week vs last-week comparison. The km tile keeps its big number and gets a smaller note beside it showing how many km more I have run than last week. The easy-run HR tile and the workout pace tile each compare this week directly with last week.

## Success criteria
- S1: The "This week" km tile still shows this week's km and run count as today, with the same big number.
- S2: Once this week's km pass last week's total, a smaller green text to the right of the number shows the extra km, e.g. "+3.4 km".
- S3: While this week is below last week's total, a smaller muted grey text shows the difference, e.g. "−4.2 km vs last week" (not red). Exactly equal shows "±0.0 km".
- S4: The easy-run HR tile shows the change in average HR over easy runs, this week vs last week, e.g. "−2.1 bpm", with detail "141.0 → 138.9 bpm".
- S5: On the HR tile, lower HR is green (better) and higher HR is red-orange (worse).
- S6: Long runs (feature 006) and workouts are not included in the easy-run HR tile.
- S7: The workout pace tile shows average rep pace across all workouts (any NxMmin type) this week vs last week, with detail naming the types, e.g. "5x6min → 4x8min · 4:13 → 4:08 /km".
- S8: On the pace tile, faster is green and slower is red-orange.
- S9: If either week has no easy run with HR (HR tile) or no workout with a measured pace (pace tile), that tile shows "—" and a short note saying why.
- S10: Weeks are Monday–Sunday, matching the weekly distance chart.

## Non-goals
- No change to the three charts or what they compute.
- No new tiles and no other comparison periods (e.g. 4-week average).
- No pace-per-workout-type matching in the tile; it averages across all workout types.
- No red for fewer km.
- No weekly goals or targets.
- No editing of Strava data.

## Constraints
- Weeks are Monday–Sunday.
- Units: km, bpm, min/km.
- The km difference compares this week so far against all of last week.
- Colour semantics from feature 002 apply (green = improving, red-orange = worse, muted for neutral).
- Treadmill workout paces from the Strava description (feature 001) still apply where relevant.
- Built on a new branch from main.

## Data sensitivity
Heart rate data (personal health data) and private activities. No GPS, new tokens or other personal data.

## Related features
- 002-dark-dashboard-redesign: owns the headline tiles. This replaces the HR-trend and workout-pace tile content (S4 and S3 of its decisions) with week vs last week, and extends the "This week" tile. Colour rules and the "—" with note behaviour carry over.
- 001-running-dashboard: defines the run types, Monday–Sunday weeks, workout pace rules and treadmill paces.
- 006-long-run-hr: long runs stay excluded from the easy-run figure.
- The long-window comparisons remain visible in the charts.

## Assumptions
- Early in the week (e.g. Monday), the comparison is against all of last week, so most of the week shows grey "vs last week" (assumed, stated in interview).
- Easy-run average HR is the mean of per-run average HR, not weighted by distance or time (assumed).
- Workout average pace is the mean across all work reps of all workouts in the week, weighted equally per workout (assumed).
- A week with two workouts of the same type lists the type once in the detail (assumed).
- Only a difference of exactly 0 (after rounding to what is shown) is neutral grey; any other change is green or red-orange, as the tiles do today (decided).

## Decisions (approved with the intent)
1. Averages weight each run equally: easy-run HR is the mean of per-run average HR, and workout pace is the mean of per-workout rep pace. Not weighted by distance or time.
2. With several workout types in a week, the detail lists all of them (e.g. "5x6min, 3x10min → 4x8min"), each type once.
3. No "tiny difference" band: only exactly 0 is neutral, matching the existing tone rule.
