# Intent: Run weather forecast on calendar tiles

**Feature ID:** 018 · **Status:** Approved · **Date:** 2026-11-04

## Problem
When I look at the build calendar I can't tell what the weather will be at the time I actually run. I usually run 09:00-11:00 and want to know whether it will be sunny or raining, and how much rain, so I can plan around it.

## Who & when
Me, whenever I open the build calendar to look at the coming days, before deciding how to fit the day's run.

## Desired outcome
Each upcoming day tile on the build calendar shows a small weather icon for my 09:00-11:00 running window: sun, plain cloud, or cloud with drops. If it is raining, the total rain in mm is written beside the icon. Days too far ahead for a forecast stay blank until they come into range.

## Success criteria
- S1: Every upcoming day tile in forecast range shows one icon, including rest days.
- S2: The icon is a sun when dry and clear, a plain cloud when overcast and dry, and a cloud with drops when rain is expected.
- S3: When the cloud-with-drops icon shows, the total mm for 09:00-11:00 is visible beside it.
- S4: Rain counts as rain from 0.1 mm total or more in that window.
- S5: Tiles beyond forecast range (today roughly 1-2 weeks out, so the 6 Dec marathon has none yet) show nothing, with no estimate.
- S6: Past days show no weather.
- S7: The forecast is for my home city, except the marathon tile, which is for Valencia, Spain (the race location).
- S8: As new forecasts arrive, the icon and mm update; a far-off forecast may change.
- S9: The weather service is credited somewhere visible (required by its licence) (assumed).

## Non-goals
- Weather on past days, or any stored weather history.
- Estimates for days beyond forecast range (including from past weather).
- Temperature, wind, or any metric other than sun/cloud/rain and rain mm.
- Per-day location or per-day run-time settings; the 09:00-11:00 window is fixed, including on race day. Valencia on the marathon tile is the one location exception, and it needs no settings screen.
- Marking far-off forecasts as less reliable. I only want to know what to prepare for, so all tiles in range look the same.

## Constraints
- Location: my home city, configured in `.env`. The marathon tile uses Valencia, Spain. Keep this small: I said to skip Valencia if it adds a lot of extra code. If it would, the marathon tile shows no weather rather than home's.
- Window: 09:00-11:00 local time at the location (local time at home, Spain time for Valencia).
- Provider: Open-Meteo (approved). It gives hourly mm, weather code and cloud cover up to 16 days ahead, needs no API key, is free for non-commercial use under 10,000 calls a day, and its data is CC-BY 4.0 (credit required). Its hourly rain is the sum of the preceding hour, so 09:00-11:00 uses the values stamped 10:00 and 11:00.
- Rejected: The national weather service's API (daily only, 5 days, no mm amounts) and OpenWeatherMap (free range reportedly 5 days). MET Norway is free and global but was not investigated further.
- Limit of the research: no independent accuracy comparison was found, so "best" means best fit for the need, not proven most accurate.

## Data sensitivity
Only the fixed home and Valencia locations are sent to the weather service. No Strava data, GPS, tokens or other personal data leave the app.

## Related features
- 004-calendar-like-build: this adds weather to its day tiles.
- 011-race-day-form: not changed by this feature.

## Assumptions
- Rest days are treated the same as run days (stated), and "today" counts as an upcoming day (assumed).
- Overcast versus clear is decided from cloud cover, with the threshold left to the spec (assumed).
- The credit line for the provider is acceptable in a small footer or tooltip (assumed).

## Open questions
None.
