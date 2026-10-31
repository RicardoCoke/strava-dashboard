# Intent: Dark dashboard redesign with loading spinner (Whoop-inspired)

**Feature ID:** 002 · **Status:** Approved · **Date:** 2026-10-30

## Problem
While the backend loads my runs, the app only shows plain text: "Loading your runs… (the first import can take a minute)". It feels unfinished and I can't tell that something is happening. More generally, the whole app looks basic, and I want it to feel more elegant, like the Whoop app (whoop.com).

## Who & when
Me, the only user, opening the dashboard after runs or when planning training, including the first-time import that can take a minute.

## Desired outcome
The whole app (dashboard, loading, error and empty states, sync banner, Connect page) has one consistent dark, polished look inspired by Whoop. Waiting for the import shows an animated ring spinner, so I know the app is working. A row of headline numbers at the top gives me the key facts before I look at the charts.

## Success criteria
- S1: While runs are loading, I see an animated circular ring spinner above the text "Loading your runs…" and the note that the first import can take a minute.
- S2: The whole app is dark (near-black background, dark grey cards) with one bright accent colour used for highlights. There is no light mode.
- S3: The dashboard, loading, error and empty states, sync banner and Connect page all share this look; no screen looks like the old style.
- S4: Above the charts I see a row of headline tiles with big, bold numbers and small uppercase labels, for example "This week: 32.4 km", "HR trend: -8 bpm", "Workout pace: 7 s/km faster".
- S5: The tiles show only information the dashboard already has, and agree with what the charts show.
- S6: The three existing charts keep all their information (weekly km, HR trend, workout pace comparison) but look lighter: thin lines, glowing points, no heavy grid lines.
- S7: "Powered by Strava" is still clearly visible.
- S8: Text and numbers stay easy to read on the dark background (good contrast).
- S9: A tile whose data is not available (for example not enough runs for a trend) says so clearly instead of showing a wrong or empty number.

## Non-goals
- No light mode and no follow-system-theme.
- No copying Whoop's logo, name, fonts or branding; style inspiration only.
- No new Strava data, no new metrics beyond what feature 001 already provides.
- No new charts or widgets other than the headline tiles.
- No live "N runs imported" counter during loading (the page only gets an answer once the import finishes).
- No change to what the charts compute (weeks, HR window, 30/30-day comparison).

## Constraints
- "Powered by Strava" must stay visible (Strava brand rule, feature 001 AC1.2).
- Existing endpoint GET /api/dashboard/running and its JSON stay as they are unless the tiles truly need something new.
- Dark mode only.
- Units remain km and min/km.

## Data sensitivity
Same as feature 001: heart rate and private activities are shown. No new personal data, no GPS, and tokens stay in the backend.

## Related features
Restyles feature 001 (running dashboard). Its content and behaviour (S1-S8 of 001) must not regress; this feature only changes look, the loading indicator and adds summary tiles.

## Assumptions
- The spinner is a ring in the accent colour that fills and turns, similar to a recovery ring (assumed).
- A "Whoop feel" means: near-black background, dark grey cards, one bright accent, large bold numbers, small uppercase labels, minimal grid lines (assumed, based on the proposed default the user confirmed).
- "This week" in the tiles means the current Monday-Sunday week (assumed).
- Tile values are derived from data already in the dashboard response (assumed).
- The user is fine with the app being used on desktop and mobile widths as before; no new platform requirements (assumed).

## Decisions (resolved open questions, approved 2026-10-30)
1. Accent colour: electric green (around `#00f19f`) for highlights, the spinner and "improving" signals. A muted blue-grey is the secondary colour (e.g. "previous 30 days" bars). A warm red-orange is used only for "getting worse" (slower pace, higher HR).
2. Spinner: the animated ring with the existing loading text is enough. A live import count ("40 of 130 runs") needs backend progress reporting and is a separate, later feature.
3. Tiles: exactly three.
   - This week: km so far and run count for the current Monday–Sunday week.
   - Easy-run HR trend: bpm change over the 5-month window; "—" with a short note when there isn't enough data.
   - Workout pace: the change for the most recent workout type with data in both 30-day windows (e.g. "5x6min · 7 s/km faster"); "—" with a note when none qualifies.
   Green means improving, red-orange means getting worse.

## Open questions
None.
