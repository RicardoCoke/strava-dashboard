# Strava Dashboard

A personal running dashboard: a Kotlin/Ktor backend and an Angular frontend. Activities, resting HR and HRV come from
the Garmin Connect Personal API (feature 021); runs synced from Strava before that stay in the local database.

## Requirements
- JDK 21
- Node.js 22 or newer
- A Garmin Connect Personal API key per person: https://connect.garmin.com/app/settings/personalApiToken
- No login: the app has no authentication, so only serve it on a trusted home network (`HOST` decides where it listens)

## First-time setup
```bash
cp .env.example .env              # fill in DB_PATH and GARMIN_API_KEY
./scripts/setup-frontend.sh       # creates the Angular app in ./frontend (one time)
cd backend && ./gradlew test      # downloads dependencies and runs the backend tests
```

## Run locally
```bash
# terminal 1
cd backend && ./gradlew run       # http://localhost:8080/api/health

# terminal 2
cd frontend && npm start          # http://localhost:4200 (proxies /api to the backend)
```

## Deploy to the Raspberry Pi
The app runs at http://rcoke.local:8080 as the `strava-dashboard` systemd service, from `~/strava-dashboard` on the Pi. It is only reachable on the home network, over plain HTTP.

The Pi's `.env` differs from the local one: `HOST=0.0.0.0` and Linux paths. `FRONTEND_DIR` is set in the service file.

To release a new version, run `scripts/deploy.sh` from Git Bash (on a clean, up-to-date `main`). It builds, uploads, restarts the service and checks the app answers afterwards.

Never `scp` straight onto the live `app.jar`: the running JVM still loads classes from it, so its shutdown crashes (`NoClassDefFoundError`) and systemd waits 90 s before killing it, leaving the app down. The script uploads `app.jar.new` and renames it over `app.jar`, which takes about a second.

First-time install: `sudo apt-get install openjdk-21-jre-headless`, copy `deploy/strava-dashboard.service` to `/etc/systemd/system/`, then `sudo systemctl enable --now strava-dashboard`. The SQLite data lives in `~/strava-dashboard/data` on the SD card, and is not backed up. Each database has `-wal` and `-shm` files beside it while the app runs; copy all three, or stop the service first.

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
