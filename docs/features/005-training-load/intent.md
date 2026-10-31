# Intent: Training load (fitness / fatigue / form)

**Feature ID:** 005 · **Status:** Approved · **Date:** 2026-10-31

## Problem
I am in week 11 of 16 of my marathon build and I can't tell from the dashboard whether I am absorbing the training or digging a hole. I have no view of how my load builds up over weeks, whether I am ramping too fast, or whether I will be fresh on race day. Today I only see per-run stats and weekly distance, which ignore how hard each run was.

## Who & when
Me, the only user, opening the dashboard during the build (and especially through the taper) to check whether I am absorbing the load and heading to Sunday 6 Dec 2026 fresh, with positive form.

## Desired outcome
The dashboard has a training load chart built only from my Strava runs. It shows three lines over the last 6 months: fitness (long-term load), fatigue (short-term load) and form (fitness minus fatigue). A warning appears when my weekly load rises by more than 10%. I can tell at a glance whether I am building, absorbing or overreaching.

## Success criteria
- S1: I see a training load chart covering the last 6 months with fitness, fatigue and form as three distinguishable lines, and I can read each value for a given day.
- S2: Every run with heart-rate data (including treadmill and private runs) contributes a load score, based on time spent at each heart-rate intensity. Rides and walks do not contribute.
- S3: Fitness reflects roughly the last 42 days of load, fatigue the last 7 days, and form is fitness minus fatigue. After a hard week fatigue rises faster than fitness, and form drops.
- S4: Form is clearly shown as positive or negative (for example a zero line), so I can see when I am fresh versus tired.
- S5: When my weekly load rises by more than 10% over the previous week, I see a visible ramp-rate warning; when it does not, no warning is shown.
- S6: The warning and chart state their facts only (numbers and flags); there is no advice text telling me what to do.
- S7: Changing my threshold heart rate or threshold pace in configuration changes the load numbers the next time the data is calculated, without code changes.
- S8: The section follows the dark look of feature 002 and reads well on desktop and mobile widths.

## Non-goals
- No advice or coaching text ("rest today", "you are overtrained").
- No Garmin data, no HRV, no data from outside Strava.
- No rides, walks or other sports in the load.
- No aerobic fitness metrics (efficiency factor, decoupling, long-run progression): that is a later feature (007).
- No plan-vs-actual comparison (planned vs actual km, sessions done, intensity split vs plan %, marathon-pace volume, race predictor): that is the next feature (006).
- No use of Strava's own heart-rate zones endpoint, and no new Strava OAuth scope.

## Constraints
- Source is Strava runs only, using the heart-rate data of each run.
- Load score is HR-based TRIMP per run; fitness = 42-day average, fatigue = 7-day average, form = fitness - fatigue.
- Window shown: last 6 months. Ramp warning threshold: weekly load up by more than 10%.
- Threshold values are configured in `.env`: `LTHR_BPM=175` and `LT_PACE=4:07` (min/km), so I can update them when Garmin re-estimates. They come from environment variables, not code.
- Heart-rate zones derived from the lactate threshold (175 bpm): easy below 153 bpm (under 87% of 175), subT 153 to 175, hard above 175. Time in each zone comes from the heart-rate stream. Recorded here as agreed decisions; whether the load score needs them is for the spec (see Assumptions).
- Backend owns the Strava data and calculation; the browser never sees a Strava token (project hard rule).
- Minimum Strava OAuth scopes: no new scope.
- Units: km, min/km, bpm.
- Must use the dark theme from feature 002.

## Data sensitivity
Heart rate data (per-run heart-rate streams) and body-related thresholds (LTHR, threshold pace) are involved. Strava tokens stay in the backend. No GPS coordinates are needed or should be logged, and nothing sensitive is logged beyond what is needed to diagnose errors.

## Related features
- 001 running dashboard: same page, backend, session and Strava sync; this adds a new chart section.
- 002 dark dashboard redesign: the section must follow its look.
- 003 HR rising highlight: also uses heart-rate data from runs; check for shared heart-rate data handling rather than duplicating it (assumed overlap).
- 004 calendar-like build: provides the build dates (Mon 17 Aug 2026 to race Sun 6 Dec 2026). Race day and week 11 of 16 context come from there. Verified: the code already uses 2026-08-17 to 2026-12-06.
- 006 plan-vs-actual and 007 aerobic fitness: planned follow-ups, explicitly out of scope here. The intensity-split chart belongs to 006, which will reuse the zone boundaries recorded above.

## Assumptions
- The load score weights time in the three zones, so the zones are used here as well as in 006 (assumed); the exact TRIMP formula is for the spec.
- Weekly load for the ramp warning is the sum of daily load scores over a Monday-to-Sunday week, compared with the previous week (assumed).
- Days with no run count as zero load, so fitness and fatigue decay on rest days (assumed).
- Fitness and fatigue need history before the 6-month window to be accurate at its start, so earlier runs are used for the calculation but not displayed (assumed).
- The chart sits with the other running charts; exact position is an open question.
- If configuration values are missing or invalid, the section shows a short message and the rest of the dashboard keeps working (assumed, same pattern as 004).

## Decisions (resolved open questions, approved 2026-10-31)
1. Runs without heart-rate data are skipped (no load estimated from pace), and the section says how many runs were skipped.
2. No forward projection to race day: the chart shows data up to today only. A projection from the plan's taper may come with the plan-vs-actual feature (006).
3. The chart sits directly under the "Marathon build" progress bar.
4. The ramp warning shows the percentage change, for example "+14% vs last week".
5. No ramp warning when the previous week was very light: below half the average weekly load of the 6 weeks before it.
