#!/usr/bin/env bash
# Build main and release it to the Raspberry Pi. Run from anywhere in Git Bash: scripts/deploy.sh
set -euo pipefail

PI="${PI:-rcoke@192.168.1.162}" # rcoke.local doesn't resolve from Windows; override with PI=user@host
cd "$(dirname "$0")/.."

[ "$(git branch --show-current)" = main ] || { echo "Deploy only from main." >&2; exit 1; }
[ -z "$(git status --porcelain)" ] || { echo "Working tree not clean." >&2; exit 1; }
git pull --ff-only

(cd backend && ./gradlew buildFatJar)
(cd frontend && npm run build)

# Upload beside the live files and swap with mv: overwriting app.jar in place breaks the running JVM's shutdown.
scp backend/build/libs/strava-dashboard-backend-all.jar "$PI:strava-dashboard/app.jar.new"
ssh "$PI" "rm -rf ~/strava-dashboard/frontend.new"
scp -r frontend/dist/frontend/browser "$PI:strava-dashboard/frontend.new"
ssh "$PI" "cd ~/strava-dashboard && mv app.jar.new app.jar && rm -rf frontend && mv frontend.new frontend && sudo -n systemctl restart strava-dashboard"

echo "Waiting for the app to come up..."
for _ in $(seq 30); do
  if ssh "$PI" "curl -fs -o /dev/null http://localhost:8080/"; then
    echo "Deployed $(git rev-parse --short HEAD) to $PI"; exit 0
  fi
  sleep 2
done
echo "App did not answer on :8080 after 60s. Check: ssh $PI journalctl -u strava-dashboard -n 50" >&2
exit 1
