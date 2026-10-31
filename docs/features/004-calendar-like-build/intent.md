# Intent: Calendar-like marathon build

**Feature ID:** 004 · **Status:** Approved · **Date:** 2026-10-30

## Problem
My marathon training plan lives in a spreadsheet (Book(Folha1).csv), which is hard to read at a glance. I can't easily see what today's session is, how the week looks, or how far through the build I am. I want the plan inside the dashboard, laid out like a calendar.

## Who & when
Me, the only user, opening the dashboard daily during the build (Mon 16 Aug 2027 to Sun 5 Dec 2027) to see today's session, the week ahead and overall progress. Before the build starts I'd also open it to preview the plan.

## Desired outcome
The dashboard has a "Marathon build" section at the top, with a percentage bar showing how much of the build has passed and the current week highlighted. Below it is a full calendar of the 16 weeks (15 numbered weeks plus race week), where each day shows its planned workout. The first calendar day is the first day of the build and the last is marathon day.

## Success criteria
- S1: I see a "Marathon build" section at the top of the dashboard, with the current week visible without scrolling through the whole plan.
- S2: A percentage bar at the top of the section shows the share of build days that have passed, with a label such as "73% · week 11 of 16 · 37 days to go".
- S3: Before the build starts the bar shows 0% with the days until it starts; after the race it shows 100%.
- S4: The calendar covers every day from Monday 16 Aug 2027 (first day of the build) to Sunday 5 Dec 2027 (marathon day), Monday to Sunday, 16 weeks.
- S5: Each day shows its session as written in my plan (for example "25' WU/CD + 3x10' subT"), its minutes and its planned km. Rest days are clear as rest days.
- S6: Each week shows its total planned km and its easy / subT percentage.
- S7: Races (5K, 10K and the marathon, 42.2K) are visibly highlighted, with marathon day most prominent.
- S8: Notes from the plan (for example "75min progression", "med-long w/30' subT", "5x15 - rest 2'30''") are visible on the day they belong to.
- S9: The target paces from the top of my plan (easy 5:38, 15K, HM, 30K) are shown in the section.
- S10: Today's day is clearly marked once the build is running.
- S11: The section uses the dark look of feature 002 and reads well on desktop and mobile widths.
- S12: What I see matches my CSV; if I change the CSV, the calendar reflects it without code changes.

## Non-goals
- No editing the plan in the dashboard; the CSV stays the source of truth.
- No uploading a CSV through the browser.
- No ticking off or matching workouts against Strava runs (unless I say otherwise, see open questions).
- No generating or adjusting training plans.
- No new Strava data or scopes.
- No light mode.

## Constraints
- Component name: calendar-like-build.
- Source is the file Book(Folha1).csv (semicolon-separated, decimal commas); it is not committed to git and its path is configured through the environment (for example TRAINING_PLAN_CSV in `.env`).
- The backend reads the file and sends the calendar to the page; the browser never reads the file directly.
- Race date is Sunday 5 December 2027 (the user corrected 6 Dec, which is a Monday); week 1 starts Monday 16 Aug 2027.
- Progress % is based on days of the build that have passed.
- Must use the dark theme from feature 002.
- Units: km and minutes, paces in min/km.

## Data sensitivity
The plan has no health data, location or tokens. It is the user's personal file, so it stays out of git and its path comes from the environment. Nothing from it is logged beyond what's needed to diagnose a read error.

## Related features
- 001 running dashboard: the new section is added to the same dashboard page and uses the same backend and session.
- 002 dark dashboard redesign: the section must follow its look (near-black background, dark grey cards, green accent, large numbers, small uppercase labels).

## Assumptions
- Week boundaries are Monday to Sunday, as in the CSV (assumed).
- The 16 blocks map to consecutive weeks: WEEK 1 starts 16 Aug 2027, and RACE WEEK is the one ending 5 Dec 2027 (assumed).
- "Percentage completed" counts today as passed only once the day is over, with local time zone (assumed).
- Paces shown are the target pace ranges from the top of the sheet, shown as-is (assumed).
- The section sits above the existing running charts and tiles (assumed, from the confirmed default).
- If the CSV is missing or unreadable, the section shows a clear message and the rest of the dashboard still works (assumed).

## Decisions (2026-10-30, approved by the user)
1. The calendar is plan-only: no ticks or matching against Strava runs. This could be a later feature.
2. The build starts Monday 16 Aug 2027: 16 weeks ending Sunday 5 Dec 2027 (verified: 16 Aug + 111 days = 5 Dec).
3. If the CSV is missing or unreadable, the section shows a short message saying so, and the rest of the dashboard keeps working.

## Revision 2 (2026-10-31, requested and approved by the user)
User's request: "the marathon is 6 december 2026 so the build already started. Also can you make a cross and/or fade out the days that have already passed and highlight the day im currently in the calendar. Also make the calendar the last graph of it but leave the % of the build on top like it is. Also can you make each day have like a square that slightly highlights when i mouseover."

Decisions (user answered yes to each proposed default):
1. Race day is Sunday 6 Dec 2026, so the build starts Monday 17 Aug 2026 (16 weeks). This becomes the default start date in code, so no `.env` value is needed. It supersedes Decision 2 and the Aug 2027 dates above.
2. Layout: the "Marathon build" progress bar stays at the top of the dashboard, under "Running". The calendar moves to the very bottom of the page, after the workout pace chart, and the target paces and plan notes move down with it.
3. Past days (before today) are dimmed with muted colours, not transparency (so 002's contrast rules still hold), and get a thin diagonal cross. Past rest days are dimmed too.
4. Today is highlighted more strongly: an accent (green) border and a faint accent tint, plus the existing "Today" badge.
5. Every day is a square tile. On mouseover it gets a slightly lighter background and a brighter border, with a short transition. Hover only, since days are not clickable.

Additional success criteria:
- S13: Days that have passed are visibly dimmed and crossed; today stands out; future days look as before.
- S14: The progress bar is at the top of the dashboard and the calendar (with paces and notes) is the last section of the page.
- S15: Each day tile highlights slightly on mouseover.
