# Intent: Background sync (fast dashboard loading)

**Feature ID:** 009 · **Status:** Approved · **Date:** 2026-10-31

## Problem
Opening the dashboard takes a noticeable time, sometimes tens of seconds, even though my runs are already stored locally. The page waits for the Strava sync to finish before showing anything, so the stored data is held back by a slow external call. It is worse right now while the lap heart-rate backlog from feature 008 is still being imported.

## Who & when
Me, whenever I open the dashboard to check recent runs, training load or VO2max, often right after a run.

## Desired outcome
The dashboard opens immediately from the runs already stored. Any Strava sync happens in the background while I look at the page, and when it brings in new data the cards update by themselves. Large backlogs keep importing in the background without ever blocking the page.

## Success criteria
- S1: When I open the dashboard, my stored runs, training load and VO2max appear right away, with no wait for Strava, including while a sync or backlog import is running.
- S2: While a sync is running, the banner shows "Syncing with Strava…".
- S3: When a background sync brings in new runs or details, the cards update without me reloading the page, and the banner goes away.
- S4: Reopening or reloading the dashboard within 15 minutes of the last sync does not start another sync.
- S5: If I leave the dashboard open for hours, it checks Strava for new runs every 15 minutes without me doing anything.
- S6: A large backlog (such as the 008 lap-HR refetch) keeps importing in the background: the next batch starts right after the previous one finishes, staying within Strava's rate limit (in practice about 80 runs per 15 minutes), while the page never waits for it.
- S7: If a background sync fails, I can still see and use my stored data, and I see that the sync failed (assumed: a short message in the banner).

## Non-goals
- No syncing while the dashboard is closed (no scheduled or always-on sync).
- No change to what data is synced or how metrics are calculated.
- No manual "Sync now" button; sync stays fully automatic.

## Constraints
- Sync only happens while the dashboard is open.
- New-run checks happen at most every 15 minutes; backlog batches may follow each other directly, but total Strava usage stays within Strava's rate limit of about 100 calls per 15 minutes (about 80 calls used per 15 minutes).
- Existing rules hold: Strava tokens stay in the backend and are never logged; minimum OAuth scopes.

## Data sensitivity
Touches Strava tokens (backend only, unchanged) and stored run data including heart rate and activity streams. No new personal data is collected or exposed.

## Related features
- 008-vo2max-estimator: its lap-HR refetch backlog is the main cause of long syncs today and is what this feature must keep importing in the background.
- 001-running-dashboard, 005-training-load: the dashboard and training-load views whose loading is affected and which must refresh when new data arrives.

## Assumptions
- A sync still starts when the dashboard is opened and a sync is due (assumed).
- Only one sync runs at a time, even with several tabs or endpoints open (assumed).
- The sync-failure message in S7 is a lightweight banner message, not a blocking error (assumed).

## Open questions
None. All earlier questions were answered at approval.
