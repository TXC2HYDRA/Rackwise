#!/usr/bin/env bash
# Build and start the whole demo stack (Postgres, backend, frontend, Prometheus, Grafana). Needs a populated .env.
set -euo pipefail
cd "$(dirname "$0")/.."
[ -f .env ] || { echo "Missing .env: copy .env.example and fill in the secrets" >&2; exit 1; }
docker compose up -d --build --wait
set -a; . ./.env; set +a
echo "App:        http://localhost:${FRONTEND_PORT:-3000}   (dev users alice/bob/carol/admin, see README)"
echo "API health: http://localhost:${BACKEND_PORT:-8080}/actuator/health"
echo "Prometheus: http://localhost:${PROMETHEUS_PORT:-9090}"
echo "Grafana:    http://localhost:${GRAFANA_PORT:-3001}   (admin / GRAFANA_ADMIN_PASSWORD)"
