# Spec: Whoop-style rings (Sleep, Recovery, Strain)

**Feature ID:** 020 · **Status:** Approved (2026-11-09) — see "Build deviations" at the end · **Intent:** [intent.md](./intent.md) · **Date:** 2026-11-09

## Summary
The existing Garmin wellness refresh (015) also reads last night's sleep duration from the daily summaries it already fetches. It adds one all-day heart-rate call per day, which it reduces to a single daily strain number before storing. A new endpoint, `GET /api/rings`, computes today's Sleep, Recovery and Strain values from the local cache. Each value comes with a "vs 30-day avg" label. A new row of three SVG rings sits at the top of the dashboard, styled like Whoop and following the Ricardo / Marina switch. No Strava data feeds any ring.

## Requirements
| ID | Requirement | Priority |
|---|---|---|
| R1 | The Garmin refresh stores last night's sleep duration per day from the existing daily-summaries call | Must |
| R2 | The Garmin refresh fetches all-day heart rate per day, turns it into a daily strain (0 to 21) with the person's LTHR zones, and stores only that number | Must |
| R3 | Strain fetch failures keep cached data, respect 429, never change Strava state and never change the 015 card's status | Must |
| R4 | A pure function computes today's Sleep performance (% of sleep need), with need = 8 h + strain extra + sleep-debt extra | Must |
| R5 | A pure function computes today's Recovery (0 to 100) from HRV, resting HR and Sleep vs 30-day baselines, weighted in that order, with a green / yellow / red zone | Must |
| R6 | Each ring has a "vs 30-day avg" label built from that person's own previous 30 days | Must |
| R7 | `GET /api/rings` returns the exact JSON contract below, per person (`X-Person`), from the cache only | Must |
| R8 | Three rings (Sleep, Recovery, Strain) sit at the top of the dashboard with loading, error, empty-ring and normal states, and reload after each sync | Must |
| R9 | The rings look like Whoop's (gray track, light blue, blue, green / yellow / red) in the dark theme, on desktop and down to 360 px | Must |
| R10 | Existing 015 card output, 005 training load and Strava sync are unchanged | Must |

## Acceptance criteria

**Conventions.** `today` = `LocalDate.now(clock.withZone(zone))` (server zone, as in 005, 009 and 015). "Day D" is a Garmin `calendarDate`, the athlete's local date, used as-is. Sleep and HRV on day D are the night that ended on the morning of D, as Garmin files them. All numbers in tests are invented. Labels use U+2212 (`−`) for minus.

### R1 — Sleep duration
- **AC1.1** Given a daily-summaries element `{"calendarDate":"2026-11-08","sleepingSeconds":25200,...}`, when the source maps it, then `WellnessDay(2026-11-08).sleepSeconds` is `25200`. Given the key is missing or null, it is `null`.
- **AC1.2** Given `sleepingSeconds` ≤ 0, then it is stored as `null`. A day is stored when at least one of resting HR, HRV or sleep is usable. This replaces 015 AC3.5's "both values null isn't stored" with "all three null". A sleep-only day is stored.
- **AC1.3** Given the existing `wellness_days` file without the column, when the app starts, then `sleep_s INTEGER NULL` is added and every existing row and value is unchanged.
- **AC1.4** Given a refresh, then no extra Garmin request is made for sleep. The same `dailysummaries` call is used. The test counts requests on the fake engine.

### R2 — All-day heart rate to daily strain
- **AC2.1** Given a configured Garmin key and a valid LTHR, when a refresh's summaries step returns `Success`, then the source sends `GET https://personal.connectapi.garmin.com/wellness-service/wellness/dailyHeartRate?date=YYYY-MM-DD` with `Authorization: Bearer <token>` and `Accept: application/json`, once per day to fetch.
- **AC2.2** Days to fetch, in ascending order: every day in `today − 31 .. today` with no `day_strain` row, plus `today − 1` and `today` always. So the first refresh makes 32 calls, and a normal refresh makes 2.
- **AC2.3** Given `{"heartRateValues":[[t0,60],[t0+120000,110],[t0+240000,null],[t0+360000,150]]}` (epoch ms), then each non-null sample counts for `min(next sample time − its time, 5 min)` and the last sample counts for 0. A null sample counts for 0 and still ends the previous sample's duration.
- **AC2.4** Zone weights by bpm as a percentage of LTHR: below 55 % → 0, 55 to < 66 % → 1, 66 to < 77 % → 2, 77 to < 88 % → 3, 88 to < 100 % → 4, ≥ 100 % → 5. Bounds use exact bpm comparison against `lthr × p / 100` as a Double. `trimp = Σ weight × minutes`.
- **AC2.5** `strain = min(21.0, 8.35 × ln(1 + trimp / 65))`. Given trimp 0, strain is `0.0`. Given 60 min at weight 2 plus 30 min at weight 4 (trimp 240), strain is `12.9` (rounded half-up to 1 decimal). Given trimp 1000, strain is `21.0`.
- **AC2.6** Given a 200 with no samples or a null `heartRateValues`, or a 204, then a `day_strain` row with `strain = null` is stored, so the day isn't fetched again unless it is today or yesterday. Given samples that are all below 55 % LTHR, then `strain = 0.0` is stored.
- **AC2.7** Given a success for a day, then its `day_strain` row is upserted with the unrounded strain and `fetched_at = now`. The heart-rate samples are not stored anywhere.
- **AC2.8** Given `LTHR_BPM` (or `MARINA_LTHR_BPM`) is unset or invalid, then no heart-rate call is made and the strain ring is `null`. Given LTHR is set later, the next refresh backfills the missing days (AC2.2).
- **AC2.9** Given any call in R2, then the log contains no token, key, URL query, date-to-value pair, bpm or strain value. One info line is written: `Strain refresh finished: days=<fetched>, stopped=<NONE|RATE_LIMITED|UNAVAILABLE|KEY_REJECTED>`.

### R3 — Strain failures
- **AC3.1** Given a heart-rate call returns 401/403 (after the source's existing one retry with a fresh token), 5xx, a network error, a timeout or bad JSON, then the strain loop stops at that day. Rows already stored in this refresh are kept, no other row changes, and `wellness_state.lastStatus` stays `OK`.
- **AC3.2** Given a 429 with `Retry-After: 120`, then the loop stops and `wellness_state.retryAfter = now + 120 s` (fallback 15 min, the same parsing as 015 AC2.4), so the next refresh isn't due before then. `lastStatus` stays `OK`.
- **AC3.3** Given the summaries step didn't succeed (015 R4), then no heart-rate call is made.
- **AC3.4** Given any R3 case, then `GET /api/wellness` returns the same body as without this feature, and `SyncState`, runs and `GET /api/sync` are unchanged.

### R4 — Sleep performance
Inputs per day D: `sleep(D)` in seconds, `strain(D−1)`.
- **AC4.1** `need(D) = 28800 + 300 × max(0, strain(D−1) − 10.0) + 0.25 × Σ_{k=1..3} max(0, 28800 − sleep(D−k))`, in seconds. A missing `strain(D−1)` adds 0. A missing night in the debt sum adds 0.
- **AC4.2** `performance(D) = min(100, sleep(D) / need(D) × 100)`, unrounded. The ring shows it rounded half-up to an integer. Given sleep 25200, `strain(D−1)` 14.0, and previous nights 25200, 28800 and missing, then need is 30900 and the ring shows `82`.
- **AC4.3** Given sleep 30600 and need 28800, then the ring shows `100` (capped).
- **AC4.4** Given no `sleep(today)`, then `sleep` is `null` in the response. There is no fall-back to an earlier night.

### R5 — Recovery
Baselines for day D: the mean of the values on D−30 .. D−1 that exist. At least 3 are needed, otherwise the baseline is null.
- **AC5.1** Components, each clamped to 0..100: `hrvC = 50 + 250 × (hrv / hrvBase − 1)`, `rhrC = 50 − 500 × (rhr / rhrBase − 1)`, `sleepC = performance(D)` from R4.
- **AC5.2** `recovery = Σ wᵢ·cᵢ / Σ wᵢ` over the available components, with weights HRV 0.60, resting HR 0.25, Sleep 0.15, rounded half-up to an integer. Given HRV 66 / base 60, resting HR 48 / base 50 and sleep performance 81.55, then recovery is `75`. Without resting HR it is `76`.
- **AC5.3** Given no HRV on D, or a null HRV baseline, then `recovery` is `null`. A missing resting HR or sleep only drops that component (AC5.2).
- **AC5.4** Zone from the rounded score: 67..100 `"green"`, 34..66 `"yellow"`, 0..33 `"red"`. Checked at 33, 34, 66 and 67.

### R6 — "vs 30-day avg" labels
- **AC6.1** Each ring's baseline is the mean of that ring's unrounded daily value on D−30 .. D−1 where it exists (performance from R4, recovery from R5, strain from R2), with at least 3 values. Past days are computed with the same pure functions as today.
- **AC6.2** The sleep and recovery labels use `delta = roundHalfUp(value − baseline)` on unrounded values: `"+5 pts vs 30-day avg"`, `"−12 pts vs 30-day avg"`, `"0 pts vs 30-day avg"`.
- **AC6.3** The strain delta is rounded half-up to 1 decimal: `"+2.3 vs 30-day avg"`, `"−0.8 vs 30-day avg"`, `"0.0 vs 30-day avg"`.
- **AC6.4** Given a null baseline, then `deltaLabel` is `null` and the ring shows `No 30-day average yet`.
- **AC6.5** Today's strain is partial and is compared with full past days (intent assumption). Past days' strains are full days.

### R7 — `GET /api/rings`
- **AC7.1** Given a connected session, then the response is 200 with exactly the keys `date`, `sleep`, `recovery`, `strain`, using the types in the contract.
- **AC7.2** Given no session, or no Strava tokens for the selected person (the shared `hasConnectedSession` guard), then the response is 401 `not_connected`.
- **AC7.3** Given the person has no Garmin key, then the response is 200 with `sleep`, `recovery` and `strain` all `null`. There is no 404, so the rings show empty (S9).
- **AC7.4** Given `X-Person: marina`, then only Marina's database and LTHR are used. Given Ricardo, only his. Tested with different stored values per person.
- **AC7.5** The endpoint makes no Garmin or Strava request and reads no runs. The test checks that the fake engines get zero requests and that the run repository is never called.
- **AC7.6** `fill` = sleep percent / 100, recovery / 100, strain / 21, each rounded half-up to 3 decimals and in 0..1.

### R8 — The rings section
- **AC8.1** `<app-rings-section />` is the first element after `</header>` in `running-dashboard-page.html`, before `<app-sync-banner>`. It is outside the page's `@switch`, so it shows whatever the runs request does.
- **AC8.2** The rings appear in order Sleep, Recovery, Strain, with labels `SLEEP`, `RECOVERY`, `STRAIN` under each ring.
- **AC8.3** Loading: three track-only rings with no value or line, and visually hidden text "Loading rings…" with `role="status"`.
- **AC8.4** Error: for a non-401 error, or a 200 body that fails `isRingsResponse`, the section shows "Couldn't load rings." and a Retry button that reloads. A 401 shows nothing new (the interceptor handles it).
- **AC8.5** An empty ring (`null` in the response) shows the gray track only, `—` in the centre and the line `No data`.
- **AC8.6** Normal: the centre text is `82%` (sleep), `75%` (recovery) or `12.9` (strain, always 1 decimal). The arc length is `fill` of the circumference, starting at 12 o'clock and going clockwise. The line under the label is `deltaLabel`, or `No 30-day average yet`.
- **AC8.7** Given `SyncStore.dataVersion` goes up, then the section reloads quietly and keeps its content if the reload fails (the same pattern as `WellnessSection`).
- **AC8.8** Each ring is `role="img"` with an `aria-label` such as `"Sleep 82%, +5 pts vs 30-day avg"`, `"Recovery 75%, green"` plus its line, `"Strain 12.9 of 21, …"` or `"Sleep, no data"`. Recovery colour is always paired with the number.
- **AC8.9** Flipping the person switch recreates the page (019 R14), so the section reloads with the new `X-Person` and never shows the previous person's rings.

### R9 — Look
- **AC9.1** New tokens in `styles.scss`: `--color-ring-track: #3a3f45`, `--color-sleep: #7ba1bb`, `--color-strain: #0093e7`, `--color-recovery-green: #16ec06`, `--color-recovery-yellow: #ffde00`, `--color-recovery-red: #ff0026`. The ring component uses only these tokens: sleep → `--color-sleep`, strain → `--color-strain`, recovery → by `zone`.
- **AC9.2** Stroke has round caps, ring stroke width is about 10 % of the diameter, and the centre value is in bold `--color-text` with the label in uppercase `--color-text-muted`. There is no card background, matching Whoop's bare rings on dark.
- **AC9.3** At 360 px the three rings stay in one row (about 96 px each) with no horizontal scroll. At ≥ 768 px each ring is 140 px. Manual check.

### R10 — Nothing else changes
- **AC10.1** Given sleep-only rows and `day_strain` rows exist, then `GET /api/wellness` and `GET /api/weekly-summaries` return the same JSON as without them (`daysWithData` still counts resting HR or HRV only).
- **AC10.2** No file under `domain/trainingload/` or `api/TrainingLoad*` changes, and `GET /api/training-load` is byte-identical.
- **AC10.3** Without a Garmin key, the background job makes no Garmin call (015 AC1.4 still passes).

## Traceability
| Success criterion (intent) | Covered by |
|---|---|
| S1 three rings at top, Sleep · Recovery · Strain | R8 (AC8.1, AC8.2) |
| S2 sleep % of need, light blue on gray | R1, R4, R9 (AC9.1) |
| S3 recovery 0–100 %, green / yellow / red | R5, R9 (AC9.1) |
| S4 strain 0–21, blue, all-day effort | R2, R3, R9 (AC9.1) |
| S5 vs-baseline line under each ring | R6, R8 (AC8.6) |
| S6 recovery weighted HRV > RHR > sleep vs 30-day baseline | R5 |
| S7 sleep need = 8 h + strain extra + debt | R4 (AC4.1) |
| S8 follows switch, own HR zones | R2 (AC2.4, AC2.8), R7 (AC7.4), R8 (AC8.9) |
| S9 no Garmin data → empty rings, rest works | R7 (AC7.3), R8 (AC8.5), R10 |
| S10 Whoop look, desktop + mobile, dark | R9 |
| Constraint: no Strava data | R7 (AC7.5) |
| Constraint: no change to 005 / 015 card | R10 |

## Design

### Data from Strava
None. No Strava endpoint, scope, activity, stream or token feeds any ring. The only Strava link is the route's existing session guard (`hasConnectedSession`), shared by every route and owned by the Strava-removal work.

### Data from Garmin Connect (Personal API, existing `GarminWellnessSource`)
- **Sleep:** the existing `GET /usersummary-service/v2/dailysummaries?startDate=&endDate=` now also reads `data[].sleepingSeconds` (int, seconds). There is no new call, and the range and schedule are unchanged (015 R3: first run today − 90, then from last success − 6).
- **All-day HR:** `GET /wellness-service/wellness/dailyHeartRate?date=YYYY-MM-DD`, Bearer token from the existing token cache. Field read: `heartRateValues: [[epochMillis, bpm | null], …]` (about 2-minute samples). 204 means no data. No pagination: one day per call.
- **Availability (approved decision 1):** neither field nor endpoint has been verified on the Personal API yet. Build step 1 is one manual call with the real key to confirm both. If either is missing, stop and come back, and don't invent a substitute.
- **Schedule:** inside `WellnessSync.refreshIfDue()` (009 job, at most every 15 min), after the summaries step succeeds. Calls are sequential (no parallelism) and stop at the first failure.
- **Errors:** 401/403 → the existing one-retry-with-fresh-token, then stop · 429 → stop and set `retryAfter` (`Retry-After` or 15 min) · 5xx / network / timeout (connect 5 s, request 15 s, existing client) / bad JSON → stop. The next refresh retries the missing days. Expired token → the existing in-memory 24 h token, refreshed on 401. The Garmin key has no expiry; a revoked key gives `KeyRejected`.

### Domain model & logic
- `WellnessDay` gains `val sleepSeconds: Int? = null` (default keeps existing callers and fakes compiling).
- New port `domain/ports/HeartRateDaySource`: `suspend fun fetchHeartRateDay(date: LocalDate): HeartRateDayResult`, implemented by `GarminWellnessSource`. `sealed interface HeartRateDayResult { Samples(list: List<HrSample>); Failed(result: WellnessFetchResult) }`, `data class HrSample(val at: Instant, val bpm: Bpm?)`.
- New port `domain/ports/StrainRepository`: `findBetween(from, to): Map<LocalDate, Double?>` (a key with a null value = fetched, no data), `upsert(date, strain: Double?, fetchedAt)`. Implemented by `ExposedWellnessRepository`.
- New package `domain/rings/` (pure except the two classes marked):
  - `RingsPolicy`: all constants named in R2 to R6 (`STRAIN_DAYS = 31`, `MAX_SAMPLE_GAP = 5 min`, `ZONE_PERCENTS = [55, 66, 77, 88, 100]`, `STRAIN_MAX = 21.0`, `STRAIN_SCALE = 8.35`, `STRAIN_KNEE_TRIMP = 65.0`, `BASE_SLEEP_NEED_S = 28800`, `STRAIN_EXTRA_FROM = 10.0`, `STRAIN_EXTRA_S_PER_POINT = 300`, `DEBT_NIGHTS = 3`, `DEBT_PAYBACK = 0.25`, `HRV_SPAN = 0.20`, `RHR_SPAN = 0.10`, weights, `BASELINE_DAYS = 30`, `MIN_BASELINE_VALUES = 3`, `GREEN_FROM = 67`, `YELLOW_FROM = 34`).
  - `StrainCalculator.strain(samples, lthr: Bpm): Double?` (R2). `null` when there are no non-null samples.
  - `RingsCalculator.build(days: List<WellnessDay>, strains: Map<LocalDate, Double?>, today): RingsView`. It computes sleep performance, recovery and strain for every day it needs (back to today − 30 for baselines), then today's values and baselines (R4 to R6). `RingsView(sleep: RingValue?, recovery: RecoveryValue?, strain: RingValue?)`, with unrounded `value` and `baseline`.
  - `StrainSync(source, repo, lthr, clock)` (I/O): `suspend fun refresh(today): Instant?`. It returns `retryAfter` on 429, otherwise null. It logs as in AC2.9.
  - `RingsService(configured: Boolean, wellness: WellnessRepository, strains: StrainRepository, clock, zone)` (I/O): `get(): RingsView`. It returns all-null when not configured. It reads `today − 61 .. today` (recovery baselines need 30 days of baselines).
- `WellnessSync` gains an optional `strain: StrainSync? = null`. On summaries `Success` it calls `strain?.refresh(today)` and saves the returned `retryAfter` into the state it already saves. `usable()` treats sleep like the other two fields.
- Units: sleep in seconds, HR in bpm, strain unitless. No metres or pace. Day bucketing uses Garmin's `calendarDate` / `date` as-is, and samples are never re-bucketed by time zone.

### Persistence
- `wellness_days`: add `sleep_s INTEGER NULL` (`addColumnIfMissing` in `DatabaseFactory.connect`). The columns read and written by `ExposedWellnessRepository` gain it.
- New table `day_strain`: `date TEXT PRIMARY KEY`, `strain REAL NULL`, `fetched_at INTEGER NOT NULL`. Added to `SchemaUtils.create`. It is pruned with the same 90-day cutoff after each successful refresh (`pruneBefore` deletes from both tables).
- Each person's file gets these via their own `DatabaseFactory.connect` (019). No data migration.

### Backend API
`GET /api/rings`: no query params. `X-Person` selects the person (019). Requires a connected session.

Response 200 (exact; example for 2026-11-09):
```json
{
  "date": "2026-11-09",
  "sleep": { "percent": 82, "fill": 0.82, "deltaLabel": "+5 pts vs 30-day avg" },
  "recovery": { "percent": 75, "zone": "green", "fill": 0.75, "deltaLabel": "−3 pts vs 30-day avg" },
  "strain": { "value": 12.9, "fill": 0.614, "deltaLabel": null }
}
```
Types:
- `date`: ISO date (today, server zone).
- `sleep`, `recovery`, `strain`: object or `null`.
- `percent`: int 0..100. `value`: number with 1 decimal, 0.0..21.0. `fill`: number 0..1, 3 decimals.
- `zone`: `"green" | "yellow" | "red"`. `deltaLabel`: string or `null`.

Errors (`{"error","message"}`): 401 `not_connected` (existing) · 500 `internal_error` (existing StatusPages).

Files: `api/RingsDtos.kt` (`RingsResponse`, `SleepRingDto`, `RecoveryRingDto`, `StrainRingDto`), `api/RingsMappers.kt` (rounding, fill, labels), `api/RingsRoutes.kt` (`ringsRoutes(tokens, service)`), mounted in `PersonStack` routes.

### Frontend (Angular)
- Route / page: no new route. Add `<app-rings-section />` to `running-dashboard-page.html` (AC8.1) and add one import in `running-dashboard-page.ts`.
- `core/api/rings.models.ts`: `RingsResponse`, `SleepRing`, `RecoveryRing`, `StrainRing`, `RecoveryZone`, plus `isRingsResponse(x: unknown)`. This is a light shape check: `date` is a string, and each ring is `null` or an object with a numeric `fill`.
- `core/api/rings.service.ts`: `getRings(): Observable<unknown>` → `GET /api/rings`.
- `features/rings/rings-section.ts|.html|.scss`: the container. It holds `signal<RingsState>` (`loading | error | loaded`), does a quiet reload on `SyncStore.dataVersion` and renders three `<app-ring>`.
- `features/rings/ring/ring.ts`: presentational. Inputs: `label`, `valueText: string | null`, `fill: number`, `colour: 'sleep' | 'strain' | RecoveryZone`, `line: string`, `ariaLabel`. It draws an SVG circle track plus an arc (`stroke-dasharray`/`dashoffset`, rotated −90°), the same technique as `shared/spinner/ring-spinner.ts`. It doesn't use ECharts: a static progress ring isn't a chart, and SVG keeps the shared `ChartComponent` and its 300 px height untouched.
- `features/rings/rings.view.ts` (pure): `ringViews(data: RingsResponse): RingView[]` (order, value text, line, aria label) and `LOADING_RINGS`.
- States: loading (AC8.3) · error with Retry (AC8.4) · empty per ring (AC8.5) · normal (AC8.6). The whole section is never hidden, including for Marina without a key.
- Tests: `testing/rings-stub.ts` (`stubRingsSection()`, the same pattern as `wellness-stub.ts`), called in the nine page specs that call `stubWellnessSection()`.

### Files expected to change
**Backend (new)**
- `backend/src/main/kotlin/app/stravadashboard/domain/rings/RingsPolicy.kt`, `StrainCalculator.kt`, `RingsCalculator.kt`, `RingsModels.kt`, `StrainSync.kt`, `RingsService.kt`
- `backend/src/main/kotlin/app/stravadashboard/domain/ports/HeartRateDaySource.kt`, `StrainRepository.kt`
- `backend/src/main/kotlin/app/stravadashboard/api/RingsDtos.kt`, `RingsMappers.kt`, `RingsRoutes.kt`
- Tests: `domain/rings/StrainCalculatorTest.kt`, `RingsCalculatorTest.kt`, `StrainSyncTest.kt`; `garmin/GarminHeartRateDayTest.kt`; `api/RingsRoutesTest.kt`; fixtures `backend/src/test/resources/garmin/daily-heart-rate.json` (invented) and an updated `summaries.json` with `sleepingSeconds`

**Backend (edited)**
- `garmin/GarminDtos.kt` (+ `sleepingSeconds`, + `GarminDailyHeartRateDto`), `garmin/GarminWellnessSource.kt` (+ sleep in `merge`, + `fetchHeartRateDay`)
- `domain/wellness/WellnessModels.kt` (+ `sleepSeconds`), `domain/wellness/WellnessSync.kt` (+ optional `StrainSync`, `usable`)
- `persistence/Tables.kt` (+ column, + `DayStrainTable`), `persistence/DatabaseFactory.kt`, `persistence/ExposedWellnessRepository.kt` (+ sleep column, + `StrainRepository`, prune both)
- `PersonStack.kt` (wire `StrainSync` when key and valid LTHR, `RingsService`, `ringsRoutes`)
- Tests: `WellnessSyncTest.kt`, `ExposedWellnessRepositoryTest.kt`, the Garmin source test, plus the R3 case in `PersonIsolationTest.kt` route list

**Frontend (new)**
- `frontend/src/app/core/api/rings.models.ts`, `rings.service.ts`, `rings.service.spec.ts`
- `frontend/src/app/features/rings/rings-section.ts`, `.html`, `.scss`, `rings-section.spec.ts`
- `frontend/src/app/features/rings/ring/ring.ts`, `ring.spec.ts`
- `frontend/src/app/features/rings/rings.view.ts`, `rings.view.spec.ts`
- `frontend/src/app/testing/rings-stub.ts`, `rings-fixtures.ts`

**Frontend (edited)**
- `frontend/src/app/features/running-dashboard/running-dashboard-page.ts` (+1 import), `.html` (+1 line)
- `frontend/src/styles.scss` (+6 tokens)
- The nine `running-dashboard-page*.spec.ts` files (+ `stubRingsSection()`)

## Edge cases
| Case | Expected behaviour |
|---|---|
| No activities / no workouts today | Strain still counts all-day HR. Walking adds a little, and sitting below 55 % LTHR adds 0. A low strain is real, not empty |
| Manual activities, activities without GPS or HR | Not relevant: no activity data is read |
| No Garmin key for this person | 200 with three `null`, three empty rings, no Garmin calls |
| Key set, no LTHR | Sleep and Recovery work. Strain is `null`, no HR calls, and sleep need uses no strain extra |
| Watch not worn last night | `sleep` `null`, and recovery uses HRV + resting HR if present, otherwise `null` |
| No HRV last night | Recovery `null` (empty) even if sleep and resting HR exist (AC5.3) |
| Fewer than 3 days of history (new user) | Rings show values, and the line says `No 30-day average yet` |
| Today's data not synced yet (early morning) | Sleep and Recovery empty until Garmin has today's `calendarDate`. Strain shows today so far |
| Watch off for hours | Sample gaps over 5 min count only 5 min, so a gap doesn't inflate strain |
| Day with no HR samples | Stored as fetched with `null` strain, a gap in baselines, never 0 |
| Late Garmin upload for an older day already fetched as empty | Not re-fetched (only today and yesterday are). Accepted limit, see Risks |
| Time zones / DST | Dates are Garmin's local `calendarDate` / `date`. Samples are not re-bucketed. A DST day has 23 or 25 h of samples, and that's counted as-is. `today` uses the server zone |
| Units | Sleep seconds and HR bpm, with no metres or pace. The percentage and fill are computed in the backend only |
| Sleep longer than need | Capped at 100 % |
| Strain above the scale | Capped at 21.0 |
| Garmin 429 mid-backfill | Stops. Days stored so far are kept, and the rest are fetched after `Retry-After` |
| Person switched mid-request | The page is recreated (019) and the old request is cancelled |

## Privacy
- **Data fields & sensitivity:** all-day heart rate (health data, GDPR Art. 9) is processed in memory only and reduced to one daily strain number. Sleep duration, daily strain, recovery and sleep performance are derived health data. Resting HR and HRV are already stored by 015. Two people's data are kept in two separate files. No location or GPS.
- **Storage & retention:** `wellness_days.sleep_s` and `day_strain.strain` in each person's own SQLite file, pruned at 90 days like 015. HR samples are never persisted or logged. The Garmin key stays in `.env` and the access token in memory (unchanged).
- **Scopes:** no Strava scopes. Garmin Personal API key per person (existing, read-only use).
- **Applicable privacy-check items:**
  - 1 ✅ No new secrets. Fixtures are invented.
  - 2 ✅ (Garmin key and token) Never in responses, cookies, storage or URLs. The token goes in the `Authorization` header only.
  - 3 ✅ The existing token refresh on 401 is used. A rejected key stops the loop.
  - 4 n/a No OAuth flow.
  - 5 ✅ No Strava scopes.
  - 6 ✅ Only `sleepingSeconds` and the derived daily strain are stored. Raw HR samples and raw responses are not.
  - 7 n/a No GPS.
  - 8 ✅ Logs carry counts, statuses and class names only (AC2.9).
  - 9 ✅ No Ktor `Logging` plugin on the Garmin client (unchanged).
  - 10 ✅ Fixed error shapes.
  - 11 ✅ The route uses the shared session guard (AC7.2).
  - 12 ✅ Cookie unchanged.
  - 13 ✅ Only numbers and fixed backend strings are rendered with interpolation. No `[innerHTML]` and no ECharts formatter.
  - 14 n/a No new state-changing endpoint.
  - 15 ✅ The response holds only ring-ready fields.
  - 16 ✅ No new third party. Garmin is called by the backend only.
  - 17 ✅ No Strava data is used.
  - 18 ❌ Existing gap (as 015 and 019): no in-app delete. Delete the person's DB file or wait for the 90-day prune.
  - 19 n/a
  - 20 ✅ DB backups already treated as personal data.
  - 21 ✅ Invented fixtures, and no real HR, sleep or dates from either person.

## Test plan
| AC | Test type | Test name |
|---|---|---|
| AC1.1, AC1.2 | backend client (MockEngine) + unit | `R1 - maps sleepingSeconds and nulls non-positive`, `R1 - sleep-only day is stored` |
| AC1.3 | backend persistence | `R1 - sleep column added to existing file without changing rows` |
| AC1.4 | backend client | `R1 - sleep needs no extra request` |
| AC2.1, AC2.2 | backend unit (StrainSync + fake) / client | `R2 - fetches missing days plus today and yesterday`, `R2 - daily heart rate request shape` |
| AC2.3–AC2.5 | backend unit | `R2 - sample durations capped at 5 minutes`, `R2 - zone weights by percent of LTHR`, `R2 - strain log scale and cap` |
| AC2.6, AC2.7 | backend unit + persistence | `R2 - no samples stores null strain`, `R2 - samples not stored` |
| AC2.8 | backend unit (wiring) | `R2 - no heart rate calls without LTHR` |
| AC2.9 | backend unit (log capture) | `R2 - strain logs counts and status only` |
| AC3.1–AC3.3 | backend unit (WellnessSync) | `R3 - strain failure stops loop and keeps rows`, `R3 - 429 sets retryAfter`, `R3 - no HR calls when summaries fail` |
| AC3.4, AC10.1 | backend route | `R3 - wellness response unchanged by strain and sleep` |
| AC4.1–AC4.4 | backend unit | `R4 - sleep need with strain and debt`, `R4 - performance capped at 100`, `R4 - no sleep today is null` |
| AC5.1–AC5.4 | backend unit | `R5 - recovery weighted components`, `R5 - missing component reweights`, `R5 - no HRV means no recovery`, `R5 - zone thresholds` |
| AC6.1–AC6.5 | backend unit + mapper | `R6 - 30-day baselines need 3 values`, `R6 - delta labels per ring` |
| AC7.1, AC7.6 | backend route | `R7 - rings response shape and fill` |
| AC7.2 | backend route | `R7 - rings require session` |
| AC7.3 | backend route | `R7 - no garmin key gives three nulls` |
| AC7.4 | backend route | `R7 - rings follow X-Person` |
| AC7.5 | backend route | `R7 - rings make no external calls and read no runs` |
| AC8.1, AC8.2 | frontend component (page) | `R8 - rings sit first under header in order` |
| AC8.3, AC8.4 | frontend component | `R8 - shows loading rings`, `R8 - shows error with retry` |
| AC8.5, AC8.6 | frontend view unit + component | `R8 - empty ring shows dash and no data`, `R8 - ring values, arc and lines` |
| AC8.7 | frontend component | `R8 - reloads quietly when dataVersion changes` |
| AC8.8 | frontend view unit | `R8 - aria labels per ring` |
| AC8.9 | covered by 019 `R14 - flip recreates page` | — |
| (service) | frontend service | `R7 - getRings calls GET /api/rings` |
| AC9.1 | frontend component | `R9 - ring colour per kind and zone` |
| AC9.2, AC9.3 | manual at 360 px and 1280 px + SCSS review | `R9 - Whoop look and layout` |
| AC10.2 | review + existing training-load route tests | diff has no `domain/trainingload/` changes |
| AC10.3 | existing 015 test | `R1 - job makes no Garmin calls without a key` |

## Out of scope
- Tapping a ring, any breakdown or history view.
- Sleep stages. Only total sleep duration is used.
- Any claim of matching Whoop's proprietary algorithm.
- Advice or coaching text.
- Changes to TRIMP, fitness, fatigue, form (005) or the 015 card.
- Any Strava data.
- Per-person sleep-need or threshold settings. All constants are fixed.

## Suggested follow-ups
- A per-person `SLEEP_NEED_HOURS` setting (the intent says "default 8 h", implying it could change).
- Re-fetch empty past days for a few days so late Garmin uploads are picked up.
- Recompute stored strain when LTHR changes (stored strain uses the LTHR at fetch time).
- In-app "delete my Garmin data" (privacy item 18).

## Risks & decisions to confirm
- **Garmin endpoints unverified (blocking at build):** `sleepingSeconds` in the daily summaries and `/wellness-service/wellness/dailyHeartRate` on the Personal API are assumed from Garmin Connect's web API. Build step 1 checks them with the real key. If they're missing, the feature stops for a decision.
- **Zones from LTHR only, not max HR (please confirm):** Edwards-style 5 bands at 55 / 66 / 77 / 88 / 100 % of LTHR (roughly 50 to 90 % of max HR). LTHR is set for both people and is what 005 uses. Ricardo may not have `MAX_HR_BPM` set, and the code default is a population value. Max HR isn't used.
- **Formula constants are approximations (please confirm):** strain `8.35 × ln(1 + trimp/65)` gives about 10 for an hour of easy running and about 18 for trimp 500. Recovery spans are HRV ±20 % and resting HR ±10 % for 0..100, with weights 0.60 / 0.25 / 0.15. Sleep need adds 5 min per strain point above 10, and 25 % of the base-need shortfall over the last 3 nights. At an exact baseline with full sleep, recovery reads 58 (yellow).
- **"pts" in labels:** the sleep and recovery deltas are percentage points, labelled `pts` so `+5` isn't read as +5 %.
- **Today only:** Sleep and Recovery show only today's `calendarDate`. Before the watch syncs in the morning they're empty, with no fall-back to yesterday.
- **Strain backfill cost:** the first refresh per person makes up to 32 sequential Garmin calls inside the background job, so the sync spinner may run longer once. The Personal API rate limits are unknown, and 429 is handled.
- **Strain stored, not samples:** only the daily number is kept, for privacy. A later LTHR change doesn't alter past strains.
- **SVG instead of ECharts:** this departs from code-standards B3's "all charts through ngx-echarts". A progress ring is treated as a gauge widget, as `ring-spinner` already is.
- **Whoop colours:** the hex values in AC9.1 are close approximations of Whoop's palette, not official values.
- **Session guard depends on Strava tokens:** `GET /api/rings` uses the shared `hasConnectedSession`, which today checks the person's Strava tokens. The Strava-removal work must replace this guard for all routes. This feature adds no other Strava dependency.
- **Schedule coupling:** Garmin refreshes still run only inside the 009 background job, which the Strava-removal work also owns.

## Build deviations (2026-11-09)
The Garmin check before the build showed that `dailysummaries` has **no** sleep field. Sleep is at
`GET /wellness-service/wellness/dailySleep?date=` (`sleepTimeSeconds`). All-day heart rate is at the specced endpoint
(about 720 samples a day). So:
- **R1 (sleep source):** sleep comes from `dailySleep`, fetched per day in the same loop as heart rate. That's 2 calls
  per day: 64 on the first refresh and 4 after that. AC1.3 and AC1.4 don't apply. `wellness_days` and 015's AC3.5 are
  unchanged.
- **Storage:** one table, `ring_days (date, sleep_s, strain, fetched_at)` with `ExposedRingDayRepository`, replaces the
  `sleep_s` column and the `day_strain` table. Ports are `RingDaySource.fetchDay(date, withHeartRate)` and
  `RingDayRepository`. The class is `RingsSync`, not `StrainSync`.
- **No LTHR:** sleep is still fetched. No heart-rate call is made, and strain is stored as null.
  `shortcut: days fetched before LTHR was set keep a null strain until pruned; re-fetch them if LTHR is added later.`
- **Frontend:** the rings are drawn inline in `RingsSection` with `@for`, not by a separate `ring` component. The view
  logic is in `rings.view.ts`.
