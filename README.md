# Strava Dashboard

A personal web dashboard for Strava activities: a Kotlin/Ktor backend and an Angular frontend.

## Requirements
- JDK 21
- Node.js 22 or newer
- A Strava API application: https://www.strava.com/settings/api
  - Set **Authorization Callback Domain** to `localhost`

## First-time setup
```bash
cp .env.example .env              # fill in STRAVA_CLIENT_ID, STRAVA_CLIENT_SECRET, SESSION_SECRET
./scripts/setup-frontend.sh       # creates the Angular app in ./frontend (one time)
cd backend && ./gradlew test      # downloads dependencies and runs the backend tests
```

## Run locally
```bash
# terminal 1
cd backend && ./gradlew run       # http://localhost:8080/api/health

# terminal 2
cd frontend && npm start          # http://localhost:4200 (proxies /api and /auth to the backend)
```

## Working with Claude Code
This repo is set up for a spec-driven workflow with sub-agents. See `CLAUDE.md` for how it works.

| Piece | Where | What it does |
|---|---|---|
| `feature-intake` skill | `.claude/skills/` | Interviews you about a new feature |
| `intent-extractor` agent | `.claude/agents/` | Writes `intent.md` (why + outcome) |
| `spec-writer` agent | `.claude/agents/` | Writes `specs.md` (requirements, API contract, tests) |
| `reviewer` agent | `.claude/agents/` | Independent, read-only review against spec, standards and privacy |
| `code-standards`, `code-review`, `privacy-check` skills | `.claude/skills/` | The rules the agents apply |

To start a feature, open Claude Code in the repo and describe it, e.g.:

> New feature: a chart of my weekly running distance for the last 12 weeks.
