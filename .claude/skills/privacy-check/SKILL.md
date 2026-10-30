---
name: privacy-check
description: Privacy and security checklist for this Strava dashboard (Ktor backend + Angular frontend): OAuth tokens and scopes, secrets, GPS and heart-rate data, logging, browser storage, XSS/CSRF, retention, third parties and GDPR basics. Use when specifying a feature that touches user or Strava data, and when reviewing code.
---

# Privacy & security check

This app processes personal data from Strava. Some of it is sensitive:
- **Location** — GPS tracks, start/end coordinates and polylines can reveal home, work and routines.
- **Health data** — heart rate, power, calories, weight. Heart rate counts as health data, a special category under GDPR (Art. 9).
- **Credentials** — OAuth access/refresh tokens grant access to the user's Strava account.

Even for a personal, single-user dashboard, treat this data as if someone else will run the app.

## Checklist
Mark each item ✅ / ❌ / n/a in specs and reviews.

### Secrets & credentials
1. No client secret, tokens, API keys or `.env` committed (check the diff, test fixtures and config files).
2. Strava tokens stored in the backend only (DB, encrypted at rest if the DB can leave the machine). They never reach the browser: not in API responses, cookies, localStorage, sessionStorage, URLs or HTML.
3. Refresh-token flow handles expiry; revoked/deauthorized tokens are deleted.
4. OAuth `state` parameter is generated, stored and verified on callback (CSRF protection).

### Scopes & minimisation
5. Only the scopes the feature needs are requested (`read`, `activity:read`; `activity:read_all` only if private activities are explicitly required; `profile:read_all` only if needed).
6. Only fields the feature uses are stored. Don't persist full raw API responses "just in case".
7. GPS streams/polylines are not stored unless a requirement needs them. If shown on a map, consider trimming start/end points or respecting Strava privacy zones.

### Logging & errors
8. Backend logs and browser `console` output never contain tokens, `Authorization` headers, OAuth codes, raw GPS coordinates or HR series. Log IDs and counts instead.
9. Ktor client logging level is not `ALL`/`BODY`/`HEADERS` in any committed config.
10. Error responses and pages don't expose stack traces, SQL or raw Strava responses.

### Web security
11. App is not publicly exposed without authentication (bind to localhost or put it behind login). Every `/api` route checks the session.
12. Session cookie: `HttpOnly`, `SameSite=Lax`, `Secure` when served over HTTPS; signed with `SESSION_SECRET`.
13. No XSS: Angular templates use normal interpolation for Strava text (activity names are user-written). No `[innerHTML]`, `bypassSecurityTrust*`, or ECharts `formatter` functions that build HTML from Strava text without escaping.
14. State-changing endpoints are POST/PUT/DELETE and CSRF-protected (Angular XSRF cookie → `X-XSRF-TOKEN` header, verified by Ktor).
15. API responses contain only the fields the UI needs (no raw Strava objects passed through). CORS is not enabled in production (same origin).

### Third parties
16. No analytics, tracking scripts, external fonts/CDNs, or other external calls that receive personal data. Frontend libraries come from npm and are bundled, not loaded from CDNs.
17. Strava data is not shared with or shown to third parties, and is used in line with the Strava API Agreement and brand guidelines (e.g. "Powered by Strava" attribution where required).

### Retention & user control
18. There is a way to disconnect Strava and delete all stored data (tokens + cached activities).
19. If a Strava deauthorization webhook is used, deauth deletes the user's data.
20. Backups/exports of the DB are treated as containing personal data.

### Tests & fixtures
21. Test fixtures use invented data — no real names, coordinates, HR or athlete IDs.

## In specs
The spec's **Privacy** section must list: data fields used, sensitivity, where stored, retention, scopes needed, and which checklist items apply.
