# Intent: Running dashboard (weekly km, HR fitness trend, workout speed comparison)

**Feature ID:** 001 · **Status:** Approved · **Date:** 2026-10-30

## Problem
The frontend still shows the Angular start-up page, so nothing from Strava is visible. I can't quickly see how much I have been running, whether I am getting fitter, or whether my key workouts are getting faster. Garmin/Strava don't detect treadmill speed, so treadmill workouts look wrong without my own notes.

## Who & when
Me, as the only user, checking my training after runs and when planning upcoming weeks.

## Desired outcome
Opening the app shows my running dashboard instead of the Angular placeholder. It has three graphs: weekly kilometers, heart rate compared with previous runs of the same type (a fitness signal), and workout speed over the last 30 days compared with the 30 days before. The dashboard will grow with more items later; this is the first, most important set.

## Success criteria
- S1: Opening the app shows the dashboard, not the Angular start-up page.
- S2: I can see my weekly running km for the last two months (about 8-9 Monday-Sunday weeks) at a glance.
- S3: Only Run activities count (outdoor and treadmill, including private ones). Walks, rides and hikes are not counted.
- S4: For a given run type, I can see how the average heart rate of the whole run compares with previous runs of that type over the last 5 months, and tell whether I am getting fitter (lower HR for similar effort).
- S5: Runs are grouped by type from the name/description: a workout named like "5x6min" is its own type (all "5x6min" runs are compared with each other), and easy runs form their own type compared with each other.
- S6: For each workout type, I can see average rep pace (min/km) for the last rolling 30 days next to the 30 days before that.
- S7: For treadmill workouts, the paces I wrote in the Strava description (e.g. "4:15 4:10" = rep 1 at 4:15/km, rep 2 at 4:10/km) are used instead of Garmin's pace.
- S8: A run type without enough data for a comparison is clearly shown as such, not as a broken or empty graph.

## Non-goals
- No other dashboard widgets yet (only the three graphs above).
- No HR zones, power, elevation, GPS/maps or route display.
- No comparing non-matching workout types, and no types other than reps x time workouts and easy runs.
- No editing of Strava data; read-only.
- No multi-user support.

## Constraints
- Units: kilometers and min/km pace.
- Weeks run Monday-Sunday.
- Workout names follow the "NxMmin" form (e.g. "5x6min").
- Treadmill paces are in the normal Strava description, given as min/km per rep, space separated.
- HR comparison uses whole-run average heart rate, window of the last 5 months.
- Speed comparison uses rolling 30 days vs the preceding 30 days.
- Replace the Angular placeholder start page.

## Data sensitivity
Heart rate (health data) and private activities are involved, plus Strava OAuth tokens held in the backend. Everything stays in this personal dashboard. No GPS is needed.

## Related features
None (first feature).

## Assumptions
- "Easy run" means any Run that doesn't match the "NxMmin" pattern (assumed).
- Matching of "NxMmin" is tolerant of spacing/case, and the same reps x time counts as the same type regardless of spelling (assumed).
- Pace per rep for non-treadmill workouts comes from Garmin laps/splits (assumed).
- A treadmill run is identified by Strava's activity data (or by the presence of pace values in the description) (assumed).
- The HR graph shows one point per run, per type, over time (assumed).
- Private activities require broader Strava access than the default read scope (assumed).

## Decisions (resolved open questions, approved 2026-10-30)
1. Easy run = any Run that doesn't match the "NxMmin" pattern; no separate label needed.
2. Outdoor workout pace for the speed comparison = average of the work reps only (from Garmin laps), excluding warm-up, cool-down and recoveries.
3. Broader Strava read access (activity:read_all) is OK, so private activities are included. Still read-only.
4. Easy runs of all lengths are compared together for now; splitting by distance may come later.
5. Treadmill and outdoor runs of the same type are compared together, not kept separate.

## Open questions
None.
