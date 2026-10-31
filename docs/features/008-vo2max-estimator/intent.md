# Intent: VO2max estimator and race time predictions

**Feature ID:** 008 · **Status:** Approved · **Date:** 2026-10-31

## Problem
I can't see whether my aerobic fitness is going up or down as my marathon build progresses, and I have no data-based idea of what finish time my current fitness supports. Runalyze gives this through a VO2max estimate and race predictions, but I want it in my own dashboard. Most of my sub-threshold work is done on the treadmill, which a naive whole-run estimate would misjudge badly.

## Who & when
Me, during the marathon build (race Sun 6 Dec 2026), checking weekly (e.g. after the long run or on Sunday evenings) whether fitness is trending the right way and what race times it implies.

## Desired outcome
The dashboard shows a current VO2max estimate based on my recent training, a chart of how it changed week by week over the last 6 months, and four tiles with the predicted 5K, 10K, half marathon and marathon times for that VO2max. Treadmill interval workouts count, using the paces I write in the Strava description and the heart rate during those intervals.

## Success criteria
- S1: I can see my current VO2max estimate, and which period it is based on (last 7 days, or last 4 weeks when there were no qualifying runs in the last 7 days).
- S2: I can see a chart with one VO2max point per Monday-Sunday week for the last 6 months, and read the trend at a glance.
- S3: I can see four tiles with estimated 5K, 10K, half marathon and marathon times for the current VO2max.
- S4: The marathon tile carries a note that this prediction tends to be optimistic.
- S5: A treadmill workout with rep paces in its Strava description (e.g. "4:15 4:10") contributes to the estimate using those paces and the heart rate recorded during the reps, not the whole-run average.
- S6: Weeks with no qualifying data are visibly distinguishable from weeks with a value (no misleading zero or invented point).
- S7: The new "VO2max" card appears under the "Training load" card.

## Non-goals
- No Runalyze mileage-based marathon adjustment ("plain" prediction only).
- No training-plan comparison or pacing/split advice (race prediction lives here; plan-vs-actual is separate).
- No VO2max values taken from Garmin or other devices.
- No editing or writing back to Strava.
- No per-run VO2max drill-down (assumed).

## Constraints
- Method follows Runalyze style: per-run oxygen cost of the pace (Daniels' formula) divided by the %VO2max implied by heart rate (needs max HR). Race times derived from Daniels' VDOT.
- Max HR default is an estimate of about 194 bpm (derived from lactate threshold HR 175), and must be changeable by me through configuration.
- Garmin lactate threshold HR is 175 bpm at 4:07 min/km (already available as LTHR_BPM from feature 005).
- Treadmill rep paces come from the Strava activity description, as already parsed by feature 001.
- Weeks are Monday-Sunday; chart window is 6 months.
- Treadmill runs count; private runs count; minimum 20 minutes for qualifying runs (from the accepted default).

## Data sensitivity
Uses heart rate and body metrics (max HR, lactate threshold HR) and pace data derived from activities. Activity descriptions are read for rep paces. No GPS is needed (assumed). Must not log raw GPS or tokens, per project rules.

## Related features
- 001 running dashboard: workout classification ("NxMmin") and treadmill rep-pace parsing are reused.
- 005 training load: shares the LTHR_BPM setting; the new card sits directly under its card.
- 006 long-run HR: also works on HR within runs; possible overlap in how HR is read per run.
- 007 week-vs-week tiles (in progress): no direct conflict, but both touch the headline area and fixtures.
- Earlier plans put a "race predictor" in a later plan-vs-actual feature; this feature now owns it, and that plan should drop it.

## Assumptions
- Outdoor runs with HR data of at least 20 minutes qualify (assumed from accepted default).
- "Current week" is the average over qualifying runs, not a single run (assumed).
- Estimated max HR of about 194 bpm is used until I provide a real value (assumed).
- Chart shows only the weekly VO2max line, with the tiles showing only the current value (assumed).
- Predictions are shown as h:mm:ss or mm:ss times without pace (assumed).

## Decisions (resolved open questions, approved 2026-10-31)
1. A treadmill run without paces in the description (e.g. an easy treadmill run) is excluded: the watch's treadmill pace isn't reliable.
2. Outdoor interval workouts use only the rep intervals, like treadmill workouts. Easy and long outdoor runs use the whole-run average pace and heart rate.
3. Reps are located from Strava laps, which carry an average heart rate per lap. No heart-rate stream is stored for this feature.
4. Max HR uses the ~194 bpm estimate for now, configurable in `.env` so the user can replace it with a measured value.
5. A rep must last at least 4 minutes to count, because heart-rate lag at the start of short reps makes VO2max look too high.
6. Within a period, runs are weighted by duration (as Runalyze does).
