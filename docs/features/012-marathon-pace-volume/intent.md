# Intent: Marathon-pace km per week

**Feature ID:** 012 · **Status:** Approved · **Date:** 2026-10-31

## Problem
The marathon is on 6 Dec 2026 and the goal pace (MP) is 4:15/km. Nothing on the dashboard shows how much running I actually do at that pace. The training plan has no MP targets, so I can't tell whether my MP volume is building toward race day.

## Who & when
Me, after a run or when planning the coming week, during the 16-week build to the marathon.

## Desired outcome
A card shows the km I ran at marathon pace in each week of the build. I can see at a glance whether MP volume is growing, and how this week compares to the rest.

## Success criteria
- S1: I see one bar per Monday-Sunday week for the 16-week build (17 Aug 2026 to race week), each showing MP km with 1 decimal.
- S2: A run segment counts as MP only if its average pace is within 4:15/km +/- 10 s/km (4:05 to 4:25), inclusive.
- S3: A summary shows total MP km so far, this week's MP km, and this week's MP km as a share of this week's total running volume.
- S4: Treadmill runs and segments shorter than 200 m add nothing to the totals.
- S5: If the goal pace is not configured, the card says "not configured" instead of showing zeros or breaking.
- S6: The card sits after the "Workout pace" chart and before the "Build calendar" card.
- S7: Viewing the card never triggers a Strava call.

## Non-goals
- No comparison to a plan target (the plan has no MP volume).
- No use of speed streams or sub-lap analysis.
- No treadmill data.
- No change to the training-plan CSV.
- No alerts or recommendations about how much MP to run.

## Constraints
- Goal MP is 4:15/km (255 s/km), set through an environment setting and not hard-coded in the UI.
- Tolerance is +/- 10 s/km.
- Uses only data already cached; read endpoints never call Strava (feature 009).
- Km shown with 1 decimal.

## Data sensitivity
Uses pace and distance from my own runs. No GPS coordinates, heart rate or tokens are shown or needed.

## Related features
- 004 calendar-like-build: provides the 16-week build and the plan, which has no MP paces. The card's week range should match it.
- 010 workout-pace-picker: the preceding "Workout pace" chart; the new card follows it.
- 009 background-sync: the cached data this card reads comes from there.
- 007 week-vs-week-tiles: related weekly volume view, so "share of this week's volume" should agree with its weekly totals.

## Assumptions
Defaults the user accepted by not objecting:
- Tolerance is inclusive of the +/- 10 s/km edges.
- Counted data is every Strava lap (auto-lap km and workout reps) of outdoor runs, at least 200 m long.
- Treadmill runs are excluded because lap paces are unreliable.
- Weeks are Monday-Sunday over the 16-week build, with the summary described in S3.
- The card is placed as in S6.

Inferred by me:
- "This week's volume" means total km of all runs that week (treadmill included), including non-MP laps (confirmed).
- A lap's average pace is distance over elapsed time, so stops inside a lap count against it (assumed).
- Weeks with no MP km still appear as empty bars (assumed).

## Open questions
None. Resolved 2026-10-31: "share of this week's volume" uses all of this week's running km, treadmill runs included.
