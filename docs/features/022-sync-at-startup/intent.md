# Intent: Sync at startup

**Feature ID:** 022 · **Status:** Approved · **Date:** 2026-11-10

## Problem
The app runs on the Raspberry Pi and starts at boot, but nothing is synced until someone opens the page. The first visit after a boot waits on the "Syncing..." banner before the data is fresh.

## Who & when
Me (Ricardo), and Marina, opening the dashboard after the Pi has been (re)started, e.g. after a power cut or a deploy.

## Desired outcome
As soon as the Pi starts the app, it syncs the data for each connected person (Ricardo and Marina) with no browser open. When I open the page, the data is already loaded. After that, nothing changes: syncing continues only while a page is open, on the existing 15-minute schedule.

## Success criteria
- S1: After the Pi boots, without opening the page, the last-synced time for Ricardo shows a time shortly after boot.
- S2: Same for Marina, if she is connected.
- S3: Opening the page soon after boot shows the data straight away, with no waiting on the "Syncing..." banner.
- S4: With no page open, no further syncs happen after the startup one.
- S5: With a page open, syncing follows today's 15-minute schedule. Opening the page within 15 minutes of the startup sync shows that data, and the next sync follows the existing schedule.

## Non-goals
- No periodic syncing on the server while no page is open.
- No change to sync rules, rate limits or the request budget.
- No frontend change.
- No startup sync for a person who is not connected.

## Constraints
- Syncs stay at least the existing 15 minutes apart, so a visit shortly after the startup sync does not trigger an extra one.

## Data sensitivity
Uses the existing Strava tokens and activity data (including heart rate), which stay in the backend as they do today. Nothing new is exposed or logged.

## Related features
- 009-background-sync: provides the sync and its 15-minute schedule, which stay as they are.
- 019-user-switch: the other person's sync only runs when that person is connected. Startup follows the same rule.

## Assumptions
- A person who is not connected at startup is skipped silently, as the other-person sync already does. (assumed)
- If the startup sync fails or hits a rate limit, the normal schedule rules apply and the next page visit retries. (assumed)
- The startup sync counts as the "last successful sync" for the schedule. (assumed)

## Open questions
1. If a startup sync fails, is it fine to wait for the next page visit rather than retry on its own?

## Decisions (approved 2026-11-10)
1. A startup sync that fails or is rate limited is not retried on its own; the next page visit retries on the existing schedule.
2. A person who isn't connected at startup is skipped silently (as `OtherPersonSync.startIfConnected` already does).
3. The startup sync counts as the last sync for the 15-minute schedule.
