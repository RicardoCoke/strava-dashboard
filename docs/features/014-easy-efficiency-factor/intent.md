# Intent: Easy-run efficiency factor

**Feature ID:** 014 · **Status:** Approved · **Date:** 2026-10-31

## Problem
My easy pace stays about the same, so "Heart rate by run type" and pace charts don't clearly show whether my aerobic base is still improving. Fitness gains show up as the same speed at a lower heart rate (or faster at the same heart rate), and I can't see that combined in one number over time.

## Who & when
Me, glancing at the running dashboard after easy and long runs during the marathon build (race Sun 6 Dec 2026, about 5 weeks away), to check the aerobic base is still moving the right way.

## Desired outcome
A new card next to "Heart rate by run type" shows an efficiency factor (speed per heartbeat) for each qualifying run over the last 6 months, with a smoothed trend line and a headline tile with the current value and the change since the start of the window. I can see the aerobic base improving even when pace is flat.

## Success criteria
- S1: The card shows one point per qualifying run over the last 6 months, plus a 4-week rolling-average line, in the same visual style as the "Workout pace by type" chart.
- S2: A headline tile shows the current efficiency factor and the percentage change since the start of the window (e.g. "+4.2% since May").
- S3: Easy runs and long runs both appear; treadmill runs, runs without average heart rate, and runs under 20 minutes moving time do not.
- S4: The value is shown in metres per minute per beat (e.g. about 1.65).
- S5: The card sits next to "Heart rate by run type" on the running dashboard.
- S6: With too little data for a trend or change, I see a short note instead of a misleading number (assumed).

## Non-goals
- No heat, hill or wind correction.
- No per-km heart-rate streams (whole-run average HR only).
- No use of Garmin HRV or resting HR data (that is feature 015).
- No workout/interval runs, no treadmill runs.
- No new Strava API calls; read endpoints never call Strava.

## Constraints
- Efficiency factor = distance / moving time divided by average HR, in metres per minute per beat.
- Data comes only from what is already in the SQLite cache.
- Window: last 6 months. Rolling average: 4 weeks.
- Built as a self-contained card (own component folder) with minimal edits to shared files, because 011, 012, 013 and 015 are built in parallel.

## Data sensitivity
Heart rate and pace/distance data (body metrics). No GPS, no tokens involved.

## Related features
- 001-running-dashboard: owns run classification and the "Heart rate by run type" card this sits beside.
- 003-hr-rising-highlight and 006-long-run-hr: also track HR trends on the same runs; this card complements rather than replaces them.
- 010-workout-pace-picker: visual style reference (per-run scatter plus line); treadmill exclusion differs from its treadmill-included handling.
- 008-vo2max-estimator: also excludes treadmill runs without rep paces because watch treadmill pace is unreliable.
- 009-background-sync: data is read from cache only.
- 011, 012, 013, 015: in parallel; avoid conflicts in shared files.

## Assumptions
- A run counts as "easy" per the existing classifier; long runs are included at the user's explicit request (stated).
- "Start of window" for the change figure compares the current rolling value with the first rolling value in the window (assumed).
- "Current EF" is the latest 4-week rolling average value rather than the most recent single run (assumed).
- Treadmill runs are identified the same way as elsewhere in the dashboard (assumed).
- Private runs count, as in other features (assumed).
- Efficiency factor mixes runs of very different lengths; the long-run drift in HR may make long runs look less efficient than easy ones (assumed to be acceptable).

## Decisions (resolved open questions)
1. Long runs: use the existing `RunType.LongRun` class from `RunClassifier` (a non-workout run over 20 km). Easy = `RunType.Easy`.
2. Long runs get a different colour on the scatter from easy runs; one rolling-average line covers both.
3. "Current EF" is the latest 4-week rolling-average value.
4. Runs with average HR at or above 153 bpm still count; no HR-based exclusion. The classifier decides the type.
