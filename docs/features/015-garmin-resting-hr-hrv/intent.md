# Intent: Resting HR & HRV (from Garmin via Intervals.icu)

**Feature ID:** 015 · **Status:** Approved · **Date:** 2026-10-31

## Problem
I am five weeks from my marathon on Sunday 6 Dec 2026 and the dashboard only knows how hard I trained, not how my body is responding. My Garmin records resting heart rate and overnight HRV every day, but that data lives in the Garmin app, away from my training load. I can't see whether a rising resting HR or falling HRV lines up with a heavy block.

## Who & when
Me, the only user, opening the dashboard in the morning or before a hard session, to check recovery next to training load, especially through the taper.

## Desired outcome
The dashboard has a "Resting HR & HRV" card showing my daily resting HR and overnight HRV over the last 12 weeks, each with a 7-day baseline. When the latest value is clearly off baseline it is flagged, and a short factual readiness note sits next to the training load chart. I no longer open the Garmin app to compare recovery with load.

## Success criteria
- S1: I see a "Resting HR & HRV" card with two lines over the last 12 weeks: daily resting HR (bpm) and overnight HRV (ms). I can read each value for a given day.
- S2: Each line has a dashed 7-day rolling average as its baseline, visually distinct from the daily line.
- S3: Days with no data show as gaps in the line, never as zero.
- S4: When the latest resting HR is clearly above its baseline, or the latest HRV is clearly below its baseline, that value is visibly flagged. When neither is, nothing is flagged.
- S5: Next to the training load chart (feature 005) I see a short factual readiness note, for example "HRV below baseline 3 days running while load is high". It states facts only and gives no advice.
- S6: New data shows up automatically on the same schedule as the Strava background sync, without me pressing anything, and past data stays visible without refetching everything.
- S7: If the Intervals.icu key is not configured, the card shows a short "not connected" message and the rest of the dashboard works as before.
- S8: Training load numbers (TRIMP, fitness, fatigue, form) are identical with and without this feature.
- S9: The card follows the dark look of feature 002 and reads well on desktop and mobile widths.

## Non-goals
- No sleep, stress, Body Battery, steps or any other wellness field.
- No Garmin login, Garmin password handling, Garmin API or unofficial Garmin libraries.
- No manual file import or export.
- No writing data back to Intervals.icu.
- No change to the TRIMP / fitness / fatigue / form maths; HRV and resting HR are not inputs to load.
- No advice or coaching text ("rest today"); facts only, as in 005.

## Constraints
- Source is Intervals.icu, which receives my Garmin wellness data through its official Garmin link. I set up that link myself.
- Access uses my personal Intervals.icu API key (basic auth, username `API_KEY`), held in a backend environment variable. `.env.example` lists the variable name with no value. The athlete id in the path can be `0` (key owner).
- Wellness endpoint (user-required, verified from the Intervals.icu OpenAPI spec): `GET https://intervals.icu/api/v1/athlete/{id}/wellness?oldest=YYYY-MM-DD&newest=YYYY-MM-DD&fields=id,restingHR,hrv`. It returns an array where `id` is the local date, `restingHR` is bpm and `hrv` is rMSSD in ms. Request only those fields.
- Fetch on the same schedule as the Strava background sync (feature 009) and cache in the local SQLite DB.
- Window shown: last 12 weeks. Units: bpm and ms.
- The key never goes to the browser and is never logged.
- Other features (011 to 014) are being built in parallel: keep the card in its own component folder and keep edits to shared files minimal.

## Data sensitivity
Resting heart rate and HRV are health data (GDPR special category). They stay in the local SQLite cache and are only sent to my own browser. Values are never logged, only counts. The Intervals.icu API key is a secret: environment variable only, never in the browser, URLs, logs or the repo. Only the three needed fields are requested (data minimisation). No Garmin credentials are involved.

## Related features
- 005 training load: the readiness note sits next to it and reads its load level; it does not alter its maths.
- 009 background sync: provides the schedule this fetch reuses.
- 002 dark dashboard redesign: the card must follow its look.
- 003 HR rising highlight and 006 long-run HR: also heart-rate related but based on run streams, not resting HR (no overlap expected).
- 011 to 014: built in parallel; avoid conflicts in shared files.

## Assumptions
- "Clearly off baseline" needs thresholds, for example resting HR a few bpm above its 7-day average or HRV a set percentage below it. Exact values are for the spec (assumed).
- The 7-day baseline averages only days that have data in the window (assumed).
- "Load is high" in the readiness note is derived from existing 005 values, such as negative form or a ramp warning (assumed).
- On first connection the last 12 weeks are backfilled, then only new or recent days are fetched. Recent days are re-fetched so late Garmin syncs are picked up (assumed).
- If Intervals.icu is unreachable or rejects the key, the card shows cached data or a short error and the rest of the dashboard keeps working (assumed, same pattern as 004 and 005).
- The card is placed near the training load chart (assumed).

## Decisions (resolved open questions)
1. The card sits directly under the training load chart; the readiness note lives on the new card (not inside the training load section), to keep shared-file edits small.
2. Fixed flag thresholds: resting HR ≥ 5 bpm above its 7-day baseline, or HRV ≥ 15% below its 7-day baseline.
