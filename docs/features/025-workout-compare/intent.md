# Intent: Workout compare

**Feature ID:** 025 · **Status:** Approved · **Date:** 2026-11-26

## Problem
I can see my runs one at a time, but I can't put two of them next to each other. When I repeat a session a few weeks apart, I can't easily tell whether I am fitter, i.e. whether the same effort now gives faster pace or lower heart rate. I also want to compare workouts of different lengths (e.g. 5x6min against 8x3min).

## Who & when
Me (or Marina), on phone or desktop, after a key session, checking it against the last time I did a similar session.

## Desired outcome
A new tab where I pick two of my recent runs from a list and see their pace and heart rate overlaid on one chart, with side-by-side stats and, for interval sessions, a rep comparison. For sessions with different rep lengths, the longer session's reps are cut into pieces of the shorter length so the two can be compared like for like.

## Success criteria
- S1: A new tab shows my latest 30 runs of the chosen type, newest first, in a list next to (or, on mobile, above/beside) a chart.
- S2: I can filter the list by all / workouts / long / easy, matching how the app already classifies runs.
- S3: When I select two runs, one chart overlays pace and heart rate for both whole runs, so I can tell which run is which. The chart does not try to line up reps.
- S4: I can switch the chart x-axis between distance and time.
- S5: For runs where detailed data is not available (e.g. older Strava-era runs), I still see a comparison at per-km resolution, and the page tells me it is coarser.
- S6: A stats block shows both runs side by side: distance, moving time, average pace, average HR, elevation, efficiency factor, HR drift.
- S7: For interval sessions (time-based, e.g. "5x6min"), a rep table shows each run's reps with pace and HR.
- S8: Same rep length and same count (5x6min vs 5x6min): rep N of run A is paired with rep N of run B, with the pace and HR difference shown.
- S9: Same rep length, different count (5x3min vs 8x3min): rep 1 is paired with rep 1 up to the shorter count; the extra reps are listed on their own.
- S10: Different rep lengths (5x6min vs 8x3min): the longer session's reps are cut into pieces of the shorter length (5x6min gives 10 three-minute pieces). I see average pace and average HR over all pieces versus all reps of the shorter session, plus piece N against rep N in order.
- S11: When the longer length doesn't divide evenly (4x5min vs 6x3min), each longer rep gives one shorter-length piece and the leftover is ignored.
- S12: When either run has no detailed data (treadmill runs, older Strava-era runs), cutting is not possible: I see whole-session averages instead, with a note saying why.
- S13: When pieces are compared, the page states the caveat: pieces of the longer session usually look slower with higher HR (longer reps are paced easier and HR rises through a rep), so this shows whether I held short-rep pace inside the longer reps, not purely whether I am fitter.
- S14: The whole tab is usable on a phone.

## Non-goals
- No maps or GPS display.
- Exactly two runs, not three or more.
- No comparing with the other person's runs.
- Runs only (no rides, etc.).
- Deferred for later: a "gap" line showing how far ahead/behind run B is versus run A at each km.
- No attempt to line up reps on the chart.
- (assumed) Distance-named workouts (e.g. "6x1000m") are not covered; time-based workouts only.

## Constraints
- Own runs of the current person only (per `X-Person`).
- Detailed per-second curves are not stored today; they come from Garmin on demand, and must respect the activity-source budget of 80 calls per 15 minutes.
- Per-km splits and laps (pace, HR, grade-adjusted speed, elevation) are stored and are the fallback.
- Workouts in this app are time-based: the run classifier recognises names like "5x6min", and a bare "10x3" means minutes.
- Must work on mobile.

## Data sensitivity
Heart rate and pace/performance data, personal to each person. No GPS or location is shown or needed; raw coordinates must not be logged.

## Related features
- 006 / 013 long-run HR and HR drift, 014 easy efficiency factor: the stats block reuses these concepts (efficiency factor, drift).
- 010 workout pace picker and the existing run classifier (RunClassifier): the type filter and the rep length/count come from these.
- Existing rep detection (StreamRepPaceFinder: the N fastest non-overlapping M-minute windows in the speed stream; RepPaceCalculator for average work-rep pace) is the basis for finding reps and pieces.
- 021 Garmin activities: source of detailed data and the Strava-era fallback.
- 019 user switch: scopes the list to the current person.
- 023 recovery tab and 024 chart pinch-zoom: precedent for a new tab and for chart behaviour on mobile (zoom on the compare chart is assumed to follow it).

## Assumptions
- (assumed) The core question is "am I fitter than last time?", comparing similar sessions weeks apart.
- (assumed) "Same structure" means the same classifier workout type (same N and M from the name), with no tolerance.
- (assumed) The 30-run limit applies after the type filter: the 30 latest runs of the chosen type.
- (assumed) If the Garmin call budget is exhausted, the comparison falls back to per-km splits with a visible note, not silently.
- (assumed) Existing rep detection is reused; average rep HR does not exist today and is new.
- (assumed) Efficiency factor and HR drift are defined as the app already defines them elsewhere.
- (assumed) Chart pinch-zoom behaves as in feature 024.
- (assumed) Runs with no HR data show pace only, without error.

## Open questions
1. Please confirm: "same structure" means same classifier workout type (same N and M from the name), no tolerance?
2. Please confirm: the 30-run limit applies after the type filter (30 latest of the chosen type)?
3. Please confirm: if the Garmin call budget is exhausted, fall back to per-km splits with a visible note?
4. Workouts named by distance (e.g. "6x1000m") aren't recognised as workouts today. Are they in scope? (assumed: no, time-based only.)

## Amendment (2026-11-26)
After the first deploy the user asked: "allow the compare graph to be zoomed in and zoomed out otherwise the lines get difficult to read", then refined it: "have a option where i can see less minutes but more in details and i can just scroll to the right for the rest. Have those options be checked in the side. For example 1x, 2x, 4x". On a narrow phone the page also scrolled sideways (the shared header did not fit).
- S15: Beside the chart there are 1x / 2x / 4x options. 2x and 4x show a half or a quarter of the run in more detail, and I can scroll to the right for the rest.
- S14 is clarified: on a phone (360 px) nothing on the page scrolls sideways, the shared header included.
