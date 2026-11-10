# Intent: Move activity data from Strava to Garmin Connect

**Feature ID:** 021 · **Status:** Approved · **Date:** 2026-11-09

## Problem
Strava makes me authorize again every time I open the dashboard from a different browser. The dashboard is only used at home, so a login is pointless friction. My Garmin data (resting HR, HRV) is already pulled with a personal API key, so Garmin can be the single source.

## Who & when
Ricardo and Marina, on the home network, opening the dashboard from any browser or device.

## Desired outcome
The dashboard opens straight away with no login or authorization step, in any browser. New runs come from Garmin Connect instead of Strava, and every existing chart and description-based feature behaves as before for those runs. Old Strava history stays in place.

## Success criteria
- S1: Opening the dashboard from a browser I have never used shows data immediately, with no login or Strava authorization.
- S2: A run recorded on Garmin after the cut-over date shows up in the dashboard (calendar, weekly stats, charts) without any manual step.
- S3: Runs cached from Strava before the cut-over are still there and unchanged; no run appears twice.
- S4: The interval description I write on a Garmin activity drives rep pace parsing, workout/treadmill description lines and marathon pace counting exactly as the Strava description did.
- S5: HR drift, VO2max segments, rep pace from streams, efficiency factor and normalized VO2max show values for Garmin runs, as they do for Strava runs.
- S6: The person switch (Ricardo / Marina) still works, each showing their own Garmin data.
- S7: No Strava code, OAuth routes or Strava environment variables remain in the app.

## Non-goals
- No login page, user accounts or authentication of any kind.
- No deletion or migration of existing Strava-cached data.
- No GPS, map or route features; GPS in Garmin files is never stored or logged.
- No new charts or metrics; this is a source swap.
- No keeping Strava as a fallback (git history is the rollback).

## Constraints
- Source: Garmin Connect Personal API (same API and per-person key as feature 015); no other Garmin access.
- Per-lap data, per-km splits and per-second streams only exist in the original FIT file, which will be parsed with Garmin's official FIT SDK (approved by Ricardo).
- GPS fields in FIT files are ignored: never stored, never logged.
- Keys, secrets and paths stay in environment variables, never committed.
- Work on branch feature/021-garmin-activities; goal is to test, then deploy if it works.
- Only intended for use on the home network.

## Data sensitivity
Heart rate, pace, and activity timing/descriptions for two people. FIT files contain GPS, which must be discarded. Garmin API keys are secrets (backend only, never logged or sent to the browser). With auth removed, the data is open to anyone who can reach the app (home network only, accepted by Ricardo).

## Related features
- 015 (Garmin resting HR/HRV): shares the API, keys and source class pattern.
- 019 (user switch): per-person keys; switch stays.
- 009 (background sync), 001 (dashboard), 010/012 (description parsing), 013/014/016/008 (stream and lap based metrics): all consume activities and must keep working.
- 018 (weather forecast): check it does not depend on Strava location data (assumed).

## Assumptions
- Garmin activity "description" is a drop-in replacement for the Strava description text (assumed).
- Only running-type activities matter (running, treadmill_running, trail_running), as with Strava runs today (assumed).
- Old Strava rows and new Garmin rows can coexist in the same cache and charts without distinguishing them (assumed).
- Background sync continues to run automatically for Garmin (assumed).

## Open questions
1. Cut-over date: is it a fixed config date (2026-11-09) or the date of first run? (Suggest fixed config.)
2. Same cut-over date for Marina, or does she have her own date? Does she write descriptions the same way?
3. If a person has no Garmin key configured, should their data show only old Strava history, or an error/empty state?
4. Garmin activities from before the cut-over (including ones that duplicate Strava runs): ignore completely, correct?
5. Grade-adjusted speed is not in FIT files (assumed): fall back to computing it from altitude/speed, or accept plain speed for Garmin runs?
6. Do Garmin-only metrics (e.g. Garmin's own VO2max or training effect) matter, or strictly a like-for-like swap?
7. Existing Strava tokens/session data in the DB: delete them on cleanup, or leave the rows?

## Decisions (approved 2026-11-09)
1. Cut-over is a fixed config date per person (`GARMIN_ACTIVITIES_FROM`, `MARINA_GARMIN_ACTIVITIES_FROM`), default 2026-11-10 (approved: the day after the last Strava sync).
2. Marina uses the same mechanism with her own date, defaulting to the same day; description parsing is unchanged.
3. A person without a Garmin key shows their cached Strava history read-only; sync status says Garmin isn't configured instead of failing.
4. Garmin activities that start before the cut-over are ignored completely (this is what prevents duplicates).
5. Grade-adjusted speed comes from the FIT file when present, otherwise it is absent (plain speed); it is not computed from altitude.
6. Garmin-only metrics are out of scope: strictly a like-for-like swap.
7. Old Strava token/session rows stay in the database untouched; nothing reads them.
- 018 weather checked: it uses the configured home location, not Strava data.
