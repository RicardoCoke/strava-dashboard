# Intent: Recovery tab with a side menu

**Feature ID:** 023 · **Status:** Approved · **Date:** 2026-11-10

## Problem
All the data lives on one long page. The three rings (Sleep, Recovery, Strain) only show today, so I cannot see how my sleep, recovery and strain have moved over the past weeks. I also have no place to grow a recovery-focused view without crowding the page further.

## Who & when
Ricardo and Marina, when checking how recovery has trended over the last weeks, for example before planning a hard week or after a bad stretch.

## Desired outcome
A menu button at the top of the page opens a side menu with two entries: "Performance" (today's page, unchanged) and "Recovery" (new). Recovery shows three graphs, one each for past Sleep, Recovery and Strain scores, the same three scores as the rings. Each page keeps its own address, so a refresh stays where I was. Recovery follows the Ricardo / Marina switch.

## Success criteria
- S1: A menu button sits at the top-left of the header; clicking it opens a side panel listing "Performance" and "Recovery".
- S2: Picking an entry opens that page and closes the panel.
- S3: "Performance" shows exactly what the dashboard shows today, nothing moved or lost.
- S4: Refreshing the browser on Recovery keeps me on Recovery; refreshing on Performance keeps me there. Performance is the default page.
- S5: Recovery shows exactly three graphs: Sleep, Recovery, Strain, one point per day.
- S6: Recovery graph: bars coloured green, yellow or red by the same zones as the ring (green 67-100, yellow 34-66, red 0-33).
- S7: Sleep graph: a line on a 0-100% scale. Strain graph: a line on the 0-21 scale.
- S8: A 7 / 30 / 90-day picker sets the range for the graphs; the default is the last 30 days.
- S9: A day with no data shows as a gap, never as zero.
- S10: Flipping the Ricardo / Marina switch updates the Recovery graphs to that person.
- S11: The menu and the graphs work on mobile as well as desktop, in the dark look of feature 002.

## Non-goals
- No new metrics on Recovery (no HRV, resting HR or sleep hours).
- No averages, trend lines or baselines on the graphs.
- No change to how Sleep, Recovery or Strain are computed.
- No other pages or menu entries for now.

## Constraints
- Menu is a slide-over side panel opened from a button at the top-left of the header.
- URLs: `/` is Performance, `/recovery` is Recovery.
- Chart styles as stated: Recovery as coloured bars, Sleep and Strain as lines.
- Same data source as the rings (Garmin only, per feature 020); no Strava data.

## Data sensitivity
Sleep, recovery and strain scores, derived from heart rate, HRV and sleep for two people: health data. Same handling as 020: local cache and the user's browser only, never log values, only counts.

## Related features
- 020 whoop-rings: supplies the three scores and their zone colours; this feature shows their history.
- 019 user switch: Recovery must follow the selected person.
- 002 dark redesign: visual style.
- 021 garmin-activities: runs on the same Garmin pulls; no conflict expected (assumed).

## Assumptions
- Past days are backfilled once from Garmin so the 90-day range is full from the start (decided). Today's Strain is partial.
- Today's point is included, with partial Strain for today (assumed).
- The range picker choice does not need to be remembered after reload (assumed).
- The menu panel is also reachable by keyboard and closes with Escape or a tap outside (assumed).
- The header keeps the person switch and bell as they are (assumed).
- If a person has no Garmin data, the graphs are empty with the rest of the page working (assumed, as in 020 S9).

## Decisions (were open questions; all assumptions above confirmed by the user)
1. Past history: the backend stores daily ring data (sleep, strain) for 90 days, but each sync only fetches the last 31 days, and Recovery % needs a 30-day baseline before each day. Decision: add a one-off backfill so the 90-day view, including Recovery's baseline, is full from day one.
2. The menu button and panel appear in the header of both pages: yes.
3. The current page is highlighted in the menu: yes.
