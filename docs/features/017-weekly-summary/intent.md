# Intent: Weekly summary notification

**Feature ID:** 017 · **Status:** Approved · **Date:** 2026-11-03

## Problem
When a training week is solid (5+ runs), I have to read several charts and tiles myself to work out what mattered: was the volume a jump, was there a standout long run, did my body cope? There is no single place that says "here is your week, and here is what to watch", compared with the weeks before.

## Who & when
Me, whenever a week reaches 5 runs (often mid-week or Sunday evening), and later when looking back at earlier weeks.

## Desired outcome
Once a week has at least 5 runs, a bell in the dashboard header shows a red count badge. Clicking it opens a floating panel on the right with a short list of plain-language notes about the week, compared with last week and the previous 4-week average. The panel never pushes or covers the charts permanently; I can close it with a cross and step back through older qualifying weeks.

## Success criteria
- S1: A week with fewer than 5 runs has no summary; the moment the 5th run of a Monday-Sunday week syncs, the bell shows a red badge with 1.
- S2: Clicking the bell opens a panel anchored to the right that overlays the page; the charts do not move or resize.
- S3: The panel lists notes built from my own numbers (e.g. "Longest run of the block: 32 km", "Volume up 18% vs your 4-week average, watch load"), each comparing to last week and/or the average of the previous 4 weeks.
- S4: Notes cover the metrics the app already shows: distance, time, longest run, pace, training load, HR drift, efficiency factor, resting HR/HRV (where data exists).
- S5: Opening the panel clears the badge; the ✕ closes the panel.
- S6: If more runs sync into that week and the summary changes, the badge comes back.
- S7: With ‹ › I can step to older weeks that had 5+ runs and read their summaries.
- S8: The same data always produces the same notes (no randomness, no AI-generated text).

## Non-goals
- No email, push or phone notifications.
- No sharing or export of the summary.
- No maps or GPS content.
- No user-editable notes.
- No LLM or external service for the text.
- No non-running activities in counts or comparisons.
- No dashboard card for this (explicitly rejected).

## Constraints
- Rule-based, deterministic notes from metrics the app already computes.
- Runs only; weeks are Monday-Sunday.
- Compare against last week and the average of the previous 4 weeks.
- Read/unread state is per-browser (localStorage); single-user app.
- Placement: bell icon in the header, top right, with right-anchored floating panel.

## Data sensitivity
Heart rate (drift), resting HR and HRV, and training metrics are involved. No GPS or location is shown. Read state in localStorage holds no personal data beyond which summary was seen (assumed).

## Related features
- 007 week-vs-week tiles: overlapping week comparison; the summary should agree with those numbers.
- 005 training load, 013 long-run HR drift, 014 easy efficiency factor, 015 Garmin resting HR/HRV: sources of the metrics used in notes.
- 009 background sync: runs arriving via sync trigger the summary and badge.

## Assumptions
- A "run" is any activity the app already treats as a run (assumed).
- If a comparison week lacks data (fewer than 4 prior weeks, or no resting HR/HRV), that note is skipped rather than shown empty (assumed).
- The 4-week average includes prior weeks regardless of whether they had 5 runs (assumed).
- The current week's summary is the first/default one shown when opening the panel (assumed).
- "Changed" means the notes or underlying numbers differ from what I last opened (assumed).
- The summary for the in-progress week is labelled as in progress until the week ends (assumed).

## Decisions (approved 2026-11-03)
1. Summaries exist only for weeks with 5+ runs, but the previous-4-week average includes all prior weeks regardless of run count.
2. Only the current / most recent qualifying week can light the badge; older qualifying weeks never count toward it (no "12" on first use). They stay browsable with ‹ ›.
3. Cap at about 6 notes per summary, most notable first.
