# Intent: Switch between Ricardo and Marina

**Feature ID:** 019 · **Status:** Approved · **Date:** 2026-11-04

## Problem
The dashboard only knows one athlete. Marina is training for a marathon on a different plan, and she cannot see her own runs, plan or numbers without a separate copy of the app. I want one dashboard that can show either of us.

## Who & when
Me (Ricardo) and Marina, whenever either of us opens the dashboard to look at our own training, and when I want to glance at hers.

## Desired outcome
A slide switch in the header, just left of the notification bell, toggles between "Ricardo" and "Marina". The whole page changes to the selected person: runs, charts, build calendar, her own training plan and settings, and the bell's weekly summary. The last selection is remembered after a reload. Each person connects Strava once; after that, switching is instant with no further login.

## Success criteria
- S1: A slide switch labelled Ricardo / Marina sits just left of the bell.
- S2: Flipping it changes everything on the page, including the bell's weekly summary and badge, to the selected person's data, with no login step.
- S3: After a reload, the page opens on the person I last selected.
- S4: Marina's dashboard is driven by her own plan, with her own start date, LTHR, max HR and marathon goal pace.
- S5: Marina's plan appears in the build calendar for the correct weeks (the plan is already in progress).
- S6: Ricardo's runs, history, wellness data and plan are exactly as before after this ships (nothing lost, nothing reset).
- S7: Marina's runs never appear in Ricardo's numbers, or the reverse.
- S8: The resting HR / HRV section uses Marina's Garmin key when one is set, and is hidden when she is selected and none is set.
- S10: Marina's calendar shows her sessions and distances; only the parts that cannot be worked out from her plan are hidden.
- S11: Until her marathon goal pace is set, features that need it show "not configured" for her.
- S9: Weather keeps the shared home location for both.

## Non-goals
- No adding, renaming or managing users; the two names are fixed.
- No per-person login or password; anyone who can open the dashboard can switch to either person.
- No combined or side-by-side view of both athletes.
- No second set of weather locations.
- No changes to how Ricardo's plan or numbers are calculated.

## Constraints
- Marina creates her own Strava API app; its client ID and secret go into `.env` as new variables. Never pasted in chat, never committed.
- Each person connects Strava once; no repeated login to switch.
- Her plan comes from her PDF (`Maratona_Marina.pdf`), converted to a CSV. Her plan: 18 weeks, labelled Week 17 down to Week 0, Monday to Sunday, marathon on 2026-12-06, distances in km.
- Her settings are her own: plan CSV, plan start date, LTHR, max HR, marathon goal pace. Their values live in `.env`, not in these docs. Her goal pace is not known yet; her LTHR is an estimate.
- Heart rate and tokens for both people stay on the backend rules in CLAUDE.md (no tokens to the browser, none in logs).

## Data sensitivity
Heart rate, HR zones, training history, resting HR/HRV and Strava tokens for two people. Both people's data is visible to anyone with access to the dashboard (accepted by the user). Marina's Strava credentials and any Garmin key are secrets and live only in `.env`. Her converted plan CSV is personal and stays out of git.

## Related features
- 017 weekly summary: the bell and its summary must follow the selected person.
- 015 Garmin resting HR/HRV: Marina may not have this data.
- 004 calendar-like build, 010 workout pace picker, 011 race-day form, 012 marathon-pace volume: depend on the plan, pace table and goal pace; they must work from Marina's plan or be hidden for her.
- 005 training load, 008/016 VO2max: depend on LTHR / max HR, now per person.
- 009 background sync: must sync both people.
- 018 weather: shared home location, unchanged.

## Assumptions
- Ricardo's existing data must survive this feature, and Marina must not connect before it ships, because today connecting a different athlete wipes the stored runs (from code review; stated by the team as must-have).
- New Strava API apps allow only one connected athlete, which is why Marina gets her own app. Recalled, unverified.
- Ranges in her PDF (distances, weekly volume) become a single number in the CSV using the midpoint, with the original range kept in the session text (decided).
- Her PDF has no easy/subT minutes, no easy/subT percentages and no target pace table, so those cannot be filled in (from the PDF).
- Text extraction of weeks 9 to 3 is tangled and needs careful manual checking; one cell reads "102max", presumably VO2max (assumed).
- Session types are text like "LT 13-14 km with 20-25 min at LT pace", "Rest or cross-train", "8K-15K tune-up race (total 16-21 km)" and "Goal marathon" (from the PDF).
- Week 17 starts Monday 2026-08-03, derived from the marathon date (decided).
- The slide state is remembered per browser (assumed).

## Open questions
None blocking. Marina's marathon goal pace is still unknown (see S11).
