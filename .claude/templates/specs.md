# Spec: <feature name>

**Feature ID:** NNN · **Status:** Draft | Approved · **Intent:** [intent.md](./intent.md) · **Date:** YYYY-MM-DD

## Summary
<2–3 sentences describing what will be built.>

## Requirements
| ID | Requirement | Priority (Must/Should/Could) |
|---|---|---|
| R1 | | Must |

## Acceptance criteria
### R1 — <title>
- **AC1.1** Given <state>, when <action>, then <observable result>.
- **AC1.2** ...

## Traceability
| Success criterion (intent) | Covered by |
|---|---|
| S1 | R1, R2 |

## Design

### Data from Strava
- Endpoints: <e.g. `GET /athlete/activities` (page, per_page, after)>
- Scopes: <minimum needed>
- Fields used: <list>
- Sync & caching: <incremental by `after`, refresh trigger>
- Errors: 401 → <behaviour> · 429 → <behaviour> · network failure → <behaviour>

### Domain model & logic
<New/changed models and pure functions. Units, rounding, time-zone rules.>

### Persistence
<Tables/columns added or changed. Migration notes.>

### Backend API
For each endpoint:
- `GET /api/...`: query params, auth required
- Response (JSON, exact):
```json
{ }
```
- Errors: status + `error` code

### Frontend (Angular)
- Route / page: <path, lazy-loaded feature folder>
- Components: <page component, child components, inputs/outputs>
- Service & TS interfaces: <in core/api or feature folder; must match the JSON above>
- States: loading · empty · error · normal (what each one shows)
- Chart: <type, x/y axes, series, units, tooltip content>

### Files expected to change
- `backend/src/main/kotlin/...`
- `frontend/src/app/...`

## Edge cases
| Case | Expected behaviour |
|---|---|
| No activities in range | |
| Manual activity (no GPS) | |
| Activity without heart rate | |
| Week spans DST change / time zones | |

## Privacy
- Data fields & sensitivity:
- Storage & retention:
- Scopes:
- Applicable privacy-check items: <#s and how they're satisfied>

## Test plan
| AC | Test type (backend unit/route/client · frontend service/component) | Test name |
|---|---|---|
| AC1.1 | unit | `R1 - ...` |

## Out of scope
<Copied from intent non-goals + anything deferred.>

## Suggested follow-ups
<Ideas not in the intent. Not to be built now.>

## Risks & decisions to confirm
-
