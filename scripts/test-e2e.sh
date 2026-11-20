#!/usr/bin/env bash
# Browser end-to-end tests (Playwright) against the real backend and PostgreSQL.
# Needs: Docker, JDK 21 (JAVA_HOME), Node 20+, populated .env (see .env.example).
# Backend runs on 8089 so it does not clash with anything already on 8080.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
port="${E2E_BACKEND_PORT:-8089}"
cd "$root"
set -a; . ./.env; set +a
docker compose up -d --wait postgres
(cd backend && ./mvnw -B -q -DskipTests package)
java -jar backend/target/capacity-desk-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev --server.port="$port" >"$root/e2e-backend.log" 2>&1 &
pid=$!
trap 'kill $pid 2>/dev/null || true' EXIT
for _ in $(seq 1 60); do curl -fs "localhost:$port/actuator/health" >/dev/null && break; sleep 2; done
curl -fs "localhost:$port/actuator/health" >/dev/null || { echo "backend did not start; see e2e-backend.log" >&2; exit 1; }
cd frontend
npm ci --silent
npx playwright install chromium
BACKEND_URL="http://localhost:$port" npx playwright test
