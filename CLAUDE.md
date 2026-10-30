# Project: Strava Dashboard

A small personal web dashboard that pulls activities from the Strava API and shows graphs and stats.

## Repo layout
```
backend/    Kotlin + Ktor JSON API, Strava OAuth & sync, SQLite cache
frontend/   Angular app (dashboard UI and charts)
docs/features/NNN-slug/   intent.md, specs.md, review.md per feature
.claude/    sub-agents, skills, templates
```

## Stack
- **Backend:** Kotlin (JVM 21), Ktor 3 (Netty), kotlinx.serialization, Ktor client for Strava, Exposed + SQLite. Gradle Kotlin DSL, ktlint, detekt, JUnit 5 + kotlin.test + MockK.
- **Frontend:** Angular 22 (standalone components, signals), TypeScript strict, Apache ECharts via `ngx-echarts`, ESLint, Angular's default test runner.
- **Auth pattern (backend-for-frontend):** the backend owns the Strava OAuth flow and tokens. The browser only holds an HttpOnly session cookie. Angular never sees a Strava token.
- **Dev:** `ng serve` proxies `/api` and `/auth` to Ktor on `localhost:8080` (`frontend/proxy.conf.json`), so there's no CORS. In production Ktor serves the built Angular app from the same origin.

## Commands
| What | Command |
|---|---|
| Run backend | `cd backend && ./gradlew run` |
| Backend checks | `cd backend && ./gradlew ktlintCheck detekt test` |
| Run frontend | `cd frontend && npm start` |
| Frontend checks | `cd frontend && npm run lint && npm test -- --watch=false && npm run build` |

## How we build features (always follow this flow)

Each feature lives in `docs/features/NNN-short-slug/` (e.g. `docs/features/001-weekly-distance-chart/`).

1. **Intake.** When I describe a feature, use the `feature-intake` skill to interview me with short, focused questions in the main conversation.
2. **Intent.** Delegate to the `intent-extractor` sub-agent with the feature folder path and the interview notes. It writes `intent.md`. Show me the open questions; don't continue until I approve the intent.
3. **Spec.** Delegate to the `spec-writer` sub-agent with the folder path. It writes `specs.md` from `intent.md` only. Wait for my approval.
4. **Build.** Implement against `specs.md`, following the `code-standards` skill. Run both backend and frontend checks before calling it done.
5. **Review.** Delegate to the `reviewer` sub-agent. Pass ONLY the feature folder path and the base branch or commit to diff against. Do NOT summarise the implementation, explain decisions, or pass conversation context; the reviewer must judge the code cold. Save its report verbatim to `review.md` in the feature folder.
6. **Fix.** Address Blocker and Major findings. For each Minor or Nit, fix it or note in `review.md` why not. Re-run the reviewer if there were Blockers.

## Hard rules
- Never commit secrets. Strava client ID/secret, session secret and DB path come from environment variables (see `.env.example`).
- Strava tokens stay in the backend: never send them to the browser, never put them in localStorage, URLs or logs.
- Never log access tokens, refresh tokens, or raw GPS coordinates.
- Request the minimum Strava OAuth scopes the feature needs.
