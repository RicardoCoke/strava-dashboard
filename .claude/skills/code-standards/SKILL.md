---
name: code-standards
description: Coding standards for this repo. Kotlin + Ktor backend (layering, null-safety, coroutines, errors, Strava client) and Angular frontend (standalone components, signals, services, charts), plus API contract and testing rules. Use when writing, refactoring or reviewing any code in this repo.
---

# Code standards

Formatting and lint are enforced by tools: **ktlint + detekt** (backend), **ESLint + Prettier** (frontend). All must pass. This document covers what the tools can't.

---

# Part A: Backend (Kotlin / Ktor)

## A1. Architecture & packages
```
backend/src/main/kotlin/app/stravadashboard/
  Application.kt     // entry point + Ktor module wiring only
  config/            // typed AppConfig loaded from env
  api/               // routes + request/response DTOs (the JSON contract with Angular)
  auth/              // Strava OAuth flow, sessions
  domain/            // pure models + services (stats, aggregation)
  strava/            // Strava API client, API DTOs, mappers to domain
  persistence/       // Exposed tables + repositories
```
- Dependencies point inward: `api/auth → domain ← strava/persistence`. `domain` must not import Ktor, Exposed or Strava DTOs.
- Routes are thin: parse and validate input, call a service, return a DTO. No business logic or SQL in routes.
- Strava API DTOs never leave the `strava` package. API DTOs (`api/`) are separate from domain models, so the frontend contract doesn't change by accident.
- Aggregation (weekly totals, zones, PRs) lives in pure functions in `domain`, so it's easy to unit test.

## A2. Kotlin style
- `val` over `var`; read-only collections in public APIs (`List`, not `MutableList`).
- `data class` for values, `sealed interface` for closed result/state hierarchies, `@JvmInline value class` for units and IDs (`ActivityId`, `Meters`, `Seconds`). No raw `Double` distances crossing layers.
- Explicit return types on public functions.
- No `!!`. Handle nulls with `?.`, `?:`, or `requireNotNull(x) { "why" }` at boundaries.
- No wildcard imports. No magic numbers; name them (`const val METERS_PER_KM = 1000.0`).
- Functions stay around 30 lines or less, at one level of abstraction.
- Unit-aware names: `distanceMeters`, `movingTimeSeconds`, `startLocal`.

## A3. Errors
- Expected failures (Strava 401/429, not found, invalid input) are values (`sealed interface StravaResult<out T>` or `Result<T>`), not exceptions thrown through layers.
- Exceptions only for programmer errors and truly unexpected failures; mapped once to HTTP responses in `StatusPages`.
- Error responses use one JSON shape: `{"error": "<code>", "message": "<safe text>"}`. Never include stack traces or raw Strava responses.
- Never swallow exceptions; log with context (no personal data) or rethrow.

## A4. Coroutines
- Suspend all the way down for I/O; no `runBlocking` outside `main` and tests.
- No `GlobalScope`. Use structured concurrency.
- Blocking JDBC/Exposed calls run in `newSuspendedTransaction(Dispatchers.IO)`.
- Parallel Strava requests are bounded (e.g. `Semaphore`) to respect rate limits.

## A5. Strava client
- One `StravaClient` wrapping Ktor `HttpClient` with timeouts and `ContentNegotiation`; retry only idempotent GETs.
- Handle pagination explicitly (`page`, `per_page`); stop on an empty page.
- On 429: back off and return a "rate limited" result; never hot-loop.
- On 401: refresh the token once, then fail cleanly.
- Cache activities in SQLite and sync incrementally with `after=<last synced epoch>`. Never re-download history on every request.
- Store UTC instants; bucket days and weeks with `start_date_local`. Convert units (metres, m/s) in `domain`, never in routes or the frontend.

## A6. API contract
- All JSON endpoints live under `/api`; auth endpoints under `/auth`.
- Endpoints return **chart-ready data** (already aggregated, already in display units, with labels), so Angular doesn't recompute stats.
- DTO field names are camelCase; dates are ISO-8601 strings; durations in seconds; distances in the unit named in the field (`distanceKm`).
- Every endpoint's request/response shape is written in the feature's `specs.md`, and a matching TypeScript interface exists in `frontend/src/app/core/api/`.

## A7. Config & secrets
- All config in a typed `AppConfig` loaded at startup; fail fast if required values are missing.
- Secrets only from environment variables. `.env` is git-ignored; `.env.example` lists keys with no values.

## A8. Backend testing
- Each acceptance criterion gets at least one test, named after it: ``fun `R2 - weekly totals bucket by local date`()``.
- `domain`: plain unit tests.
- `strava`: Ktor `MockEngine` with JSON fixtures in `src/test/resources/strava/` (invented data only).
- `api`: Ktor `testApplication` (status, JSON shape).
- `persistence`: in-memory SQLite.
- Deterministic: inject `Clock`, no real network, no sleeps.

---

# Part B: Frontend (Angular)

## B1. Structure
```
frontend/src/app/
  core/            // singletons: api/ (typed HTTP services + interfaces), auth/, interceptors, error handling
  shared/          // reusable presentational components, pipes (e.g. pace, distance format)
  features/<name>/ // one folder per dashboard feature: page component, child components, feature service
  app.config.ts, app.routes.ts
```
- Standalone components only (no NgModules). Feature routes are lazy-loaded with `loadComponent` / `loadChildren`.
- Components don't call `HttpClient` directly; they use a typed service from `core/api` or the feature folder.
- Container/presentational split: page components fetch and hold state; child components get data via `input()` and emit via `output()`.

## B2. TypeScript & Angular style
- `strict` TypeScript and strict templates on. No `any`; use `unknown` and narrow it.
- Signals for component state (`signal`, `computed`, `input()`, `output()`); convert observables at the edge with `toSignal`. Use RxJS for HTTP and streams, not for simple local state.
- `ChangeDetectionStrategy.OnPush` on every component.
- Use `inject()` instead of constructor injection.
- Use control flow blocks (`@if`, `@for` with `track`), not `*ngIf`/`*ngFor`.
- No manual `subscribe` without cleanup; prefer `toSignal`, the `async` pipe or `takeUntilDestroyed()`.
- No business logic or unit conversion in templates or components. Formatting (e.g. `5:12 /km`) goes in pure pipes in `shared/`.
- One component per file; file names follow Angular CLI conventions.

## B3. Charts
- All charts go through `ngx-echarts`, wrapped in a shared `ChartComponent` (or one component per chart type) that takes typed series via `input()`.
- Chart options are built in pure functions (`buildWeeklyDistanceOptions(data): EChartsOption`) so they can be unit-tested.
- Every chart has a loading state, an empty state ("No activities in this period") and an error state.
- Axis labels and tooltips show units. Colours come from one shared palette file.

## B4. HTTP, auth & errors
- `HttpClient` is set up with `provideHttpClient(withInterceptors([...]), withXsrfConfiguration(...))`.
- One error interceptor maps API errors to user-friendly messages. A 401 from `/api` redirects to the "Connect with Strava" page.
- Never store tokens or personal data in `localStorage`/`sessionStorage`. The session lives in an HttpOnly cookie set by the backend.
- Never use `bypassSecurityTrust*` or `[innerHTML]` with Strava data (activity names and descriptions are user-written text).

## B5. Frontend testing
- Each acceptance criterion that's visible in the UI gets a test named after it: `it('R3 - shows empty state when no activities', ...)`.
- Services: `provideHttpClientTesting()` + `HttpTestingController`.
- Components: test rendered output and inputs/outputs, not private methods.
- Chart option builders and pipes: plain unit tests.
- Fixtures use invented data.

---

# Part C: Shared rules
- New dependencies need a reason in the PR description. Backend versions live in `backend/gradle/libs.versions.toml`.
- Small commits; message format `<type>(<scope>): <summary>`, e.g. `feat(charts): weekly distance endpoint`.
- Delete dead code; don't comment it out.
