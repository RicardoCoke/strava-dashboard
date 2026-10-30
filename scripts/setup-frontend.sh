#!/usr/bin/env bash
# Creates the Angular app in ./frontend with the project's tooling.
# Run once from the repo root:  ./scripts/setup-frontend.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [ -f frontend/angular.json ]; then
  echo "frontend/ already contains an Angular app. Nothing to do."
  exit 0
fi

echo "==> Creating Angular 22 app in ./frontend"
npx -y @angular/cli@22 new frontend \
  --directory frontend \
  --defaults \
  --routing \
  --style=scss \
  --ssr=false \
  --skip-git

cd frontend

echo "==> Adding ESLint (angular-eslint)"
npx ng add @angular-eslint/schematics --skip-confirmation

echo "==> Adding charts (ECharts + ngx-echarts) and Prettier"
npm install echarts ngx-echarts
npm install --save-dev prettier

echo "==> Dev proxy to the Ktor backend"
cp "$ROOT/scripts/frontend-files/proxy.conf.json" proxy.conf.json
cp "$ROOT/scripts/frontend-files/.prettierrc.json" .prettierrc.json
npx ng config projects.frontend.architect.serve.options.proxyConfig proxy.conf.json

echo "==> Creating folder structure"
mkdir -p src/app/core/api src/app/shared src/app/features
touch src/app/core/api/.gitkeep src/app/shared/.gitkeep src/app/features/.gitkeep

echo "==> Checking it builds"
npm run lint
npm test -- --watch=false
npm run build

echo
echo "Done. Start it with: cd frontend && npm start  (backend: cd backend && ./gradlew run)"
