# Intent: Long-run HR drift (aerobic decoupling)

**Feature ID:** 013 · **Status:** Approved · **Date:** 2026-10-31

## Problem
The long-run HR chart shows heart rate, but not whether my heart rate climbs relative to pace as a long run goes on. That is the best sign of aerobic durability for a marathon, and today I can't see it without doing the maths by hand.

## Who & when
Me, after a long run and on weekly planning days, checking whether my endurance is holding up on the way to the marathon on 6 December 2026 (5 weeks away).

## Desired outcome
A card directly under the long-run HR chart shows, for each long run, how much my HR-versus-pace efficiency dropped from the first half to the second half (the common Pa:Hr / aerobic decoupling method). I can see at a glance whether I am under the ~5% "good durability" level and whether the trend is improving.

## Success criteria
- S1: Each qualifying long run in the last 5 months appears as one drift % value, over time.
- S2: A "good" zone (< 5%), a "watch" zone (5-10%) and a "poor" zone (> 10%) are visible, and each run is coloured by its zone.
- S3: The header shows the latest long run's drift and the average of my last 4 long runs.
- S4: I can see how many long runs were left out, and the reason (treadmill, under 60 min usable, missing HR or speed data).
- S5: Older long runs fill in automatically over later syncs, without me doing anything and without tripping Strava rate limits. Until they fill in, the card shows how many are pending.
- S6: Opening the dashboard never triggers Strava calls.

## Non-goals
- No per-second or per-km drift curves, no raw stream display, no GPS or map.
- No drift for non-long runs, workouts or races.
- No coaching advice or race-day prediction.
- No changes to the existing long-run rule or the long-run HR chart.

## Constraints
- Long run = same rule as 006: non-workout Run over 20,000 m, 5-month window (same as the HR chart).
- Needs both HR and speed data per run. Speed data is not fetched today for non-workouts, so this adds a new Strava request per long run.
- That request must use the shared 009 budget (80 Strava calls per 15 minutes) and the existing sync phases, as a low-priority phase after the existing ones (about 1 call per run).
- Only derived drift numbers are stored, never raw streams or GPS.
- Treadmill runs are excluded.
- Card lives in its own component folder. Edits to shared files are kept minimal, because features 011, 012, 014 and 015 are built in parallel.
- Units are metric (km, min/km).

## Data sensitivity
Heart rate and pace data, which are body and performance metrics. GPS is not needed and must not be stored or logged. Strava tokens stay in the backend as per the project rules.

## Related features
- 006 long-run-hr: defines the long-run rule and the 5-month window, and the card sits under its chart.
- 005 training-load: HR streams are already fetched and reduced to histograms. Raw streams are not kept, so this feature needs its own derived value.
- 001 running-dashboard: speed streams are only fetched for workouts today.
- 009 background-sync: the call budget and sync phases are reused. Read endpoints must not call Strava.
- 010 workout-pace-picker: separate pace analysis, no direct conflict.
- 011, 012, 014, 015: built in parallel, so shared-file conflicts are a risk.

## Assumptions
- "Common strategy" means the standard Pa:Hr method (TrainingPeaks / Friel): efficiency = speed ÷ HR for each half, drift % = (first-half efficiency − second-half efficiency) ÷ first-half efficiency × 100. (assumed)
- No warm-up exclusion: the halves cover the whole run's moving time, as in the plain method. (decided)
- Halves are split by moving time, and paused time is ignored. (assumed)
- Runs with less than 60 minutes usable are skipped. (assumed)
- Treadmill runs are identified from Strava's activity data, such as the trainer flag or missing GPS. (assumed)
- Thresholds are 5% and 10%. 5% is the user's stated rule of thumb. 10% is the agreed "poor" line. (decided)
- Negative drift (HR falls relative to pace) counts as "good" and is shown as is. (assumed)
- Plain speed, not grade-adjusted; drift is not corrected for terrain or heat. (decided)
- Latest and last-4 averages count only runs that have a computed value. (assumed)

## Decisions (approved 2026-10-31)
1. No 10-minute warm-up exclusion: first half vs second half of the whole run's moving time (plain common method). The 60-minute usable minimum stays.
2. Plain speed, not grade-adjusted (no altitude stream is requested).
3. Runs still waiting for backfill get no point; the card shows a short "N long runs still syncing" line.
4. Thresholds: good < 5%, watch 5-10%, poor > 10%.
