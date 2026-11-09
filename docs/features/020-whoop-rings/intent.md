# Intent: Whoop-style rings (Sleep, Recovery, Strain)

**Feature ID:** 020 · **Status:** Approved · **Date:** 2026-11-09

## Problem
The dashboard shows training load and a resting HR / HRV card, but nothing tells me at a glance how ready I am today. In the Whoop app, three rings answer that in one look: did I sleep enough, how recovered am I, how hard have I worked today. I want that same quick read on top of my dashboard, for me and for Marina.

## Who & when
Ricardo and Marina, each opening the dashboard in the morning or before a session, to see last night's sleep, today's recovery and the day's effort so far.

## Desired outcome
Three rings sit at the top of the page, styled as closely as possible to Whoop: Sleep, Recovery and Strain. Each shows today's value and a small "vs baseline" line under it. They follow the existing Ricardo / Marina switch. All numbers come only from Garmin data.

## Success criteria
- S1: Three rings appear at the top of the dashboard, in this order: Sleep, Recovery, Strain (order assumed from the Whoop app).
- S2: Sleep ring shows the percentage of last night's sleep need that I actually slept. Light blue on a gray track.
- S3: Recovery ring shows a 0 to 100% score. Its colour follows the score: green 67 to 100, yellow 34 to 66, red 0 to 33, on a gray track.
- S4: Strain ring shows today's day strain on the 0 to 21 scale, in blue on a gray track. It counts all-day effort, not only recorded workouts.
- S5: Each ring has a small line under it comparing today with my own baseline (for example "vs 30-day average").
- S6: Recovery is driven mostly by last night's HRV against my 30-day baseline, then resting HR against baseline, then sleep performance (the Sleep ring).
- S7: Sleep need = baseline need (default 8 h) plus extra for yesterday's strain plus recent sleep debt.
- S8: Flipping the Ricardo / Marina switch updates all three rings to that person. Their HR zones use their own LTHR / max HR.
- S9: If a person has no Garmin data, their rings show empty (gray track, no value). The rest of the dashboard works as before.
- S10: The rings look and feel like Whoop (gray, light blue, blue, green/yellow/red) and read well on desktop and mobile, in the dark look of feature 002.

## Non-goals
- No tap or click on a ring, no breakdown view.
- No sleep stages, only total sleep duration.
- No claim of being Whoop's exact algorithm; it is an approximation (Whoop's is proprietary).
- No advice or coaching text, facts only (as in 005 and 015).
- No change to TRIMP / fitness / fatigue / form or to the 015 card.
- No Strava data of any kind.

## Constraints
- Garmin data only. No Strava activities, streams or tokens may feed any ring. The user is removing Strava from the app in another session.
- Reuse the existing Garmin wellness source (015) for resting HR and HRV; sleep duration and all-day heart rate need new Garmin data pulls.
- Strain uses Whoop's 0 to 21 logarithmic scale, from all-day heart rate in HR zones based on LTHR / max HR.
- Per-person HR settings stay in `.env`; no numbers in docs.
- Colours: gray track, light blue sleep, blue strain, green/yellow/red recovery with the thresholds above.
- Rings only: today's value plus the baseline line.

## Data sensitivity
Heart rate (resting and all-day), HRV, sleep duration and derived scores for two people: health data. Stored only in the local cache and sent only to the user's browser. Never log values, only counts. Garmin credentials/tokens stay in `.env`, never in the browser or logs.

## Related features
- 015 Garmin resting HR / HRV: supplies resting HR and HRV; this feature adds sleep and all-day HR.
- 019 user switch: rings must follow the selected person.
- 009 background sync: new Garmin pulls should ride the same schedule (assumed).
- 005 training load: also uses HR zones; the two must not alter each other.
- 002 dark redesign: visual style.
- Strava removal (other session): this feature must not add any Strava dependency.

## Assumptions
- Order Sleep, Recovery, Strain, placed above all existing content (assumed).
- The 30-day baseline is the average of days with data (assumed).
- Strain for "today" is partial and grows during the day; Sleep and Recovery refer to last night (assumed).
- "Yesterday's strain" for sleep need uses the same strain calculation (assumed).
- Recent sleep debt covers the last few days; exact window is for the spec (assumed).
- Recovery with missing inputs uses what exists, or shows empty if there is no HRV (assumed).
- The "vs baseline" wording per ring is for the spec (assumed).
- Past days are cached so baselines do not need refetching (assumed).

## Decisions (approved 2026-11-09)
1. Source: the existing direct Garmin Connect client (`GarminWellnessSource`), adding its all-day heart-rate and sleep endpoints. Not Intervals.icu. Availability is verified during build.
2. Strain shows today so far and updates on each sync, like Whoop.
3. Each person's rings use only their own Garmin connection; empty if they have none (as 015/019).
