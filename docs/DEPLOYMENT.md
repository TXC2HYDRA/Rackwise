# Deployment

## What exists
A Docker Compose stack for local demos and CI: PostgreSQL 16, backend, frontend behind nginx, Prometheus, Grafana. It is **not** a production configuration (see below).

## Prerequisites
Docker with Compose v2; for local development without containers: JDK 21 (full JDK), Node 20+.

## Configuration (`.env`, never committed)
| Variable | Purpose |
|---|---|
| `POSTGRES_PASSWORD` | database password (required) |
| `JWT_SECRET` | HS256 signing secret, at least 32 bytes (required) — `openssl rand -base64 48` |
| `METRICS_SCRAPE_TOKEN` | Prometheus scrape token (required by the compose file) — `openssl rand -hex 24` |
| `GRAFANA_ADMIN_PASSWORD` | Grafana admin password (required by the compose file) |
| `GEMINI_API_KEY` | optional; used only if `capacitydesk.assistant.llm.enabled=true` |
| `BACKEND_PORT`, `FRONTEND_PORT`, `PROMETHEUS_PORT`, `GRAFANA_PORT`, `POSTGRES_PORT` | host ports (defaults 8080, 3000, 9090, 3001, 5440) |

If port 8080 is taken on your machine, set `BACKEND_PORT` to something else; the e2e script uses 8089 for the same reason.

## Start, stop
```bash
scripts/dev-start.sh        # build and start everything, wait for health
scripts/seed-demo.sh        # optional demo requests and plans
scripts/dev-stop.sh         # stop; add --wipe to delete the database volume
```
Dev users (profile `dev`): `alice` requester, `bob` and `carol` reviewers, `admin`; password `Rackwise-Dev-1!` unless `DEV_USER_PASSWORD` is set.

## Without Docker for the app
```bash
docker compose up -d postgres
set -a; . ./.env; set +a
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev     # :8080
cd frontend && npm install && npm run dev                                 # :5173, proxies /api (BACKEND_URL overrides the target)
```
IntelliJ: open `backend/pom.xml`, set the project SDK to JDK 21, put the `.env` values into the run configuration's environment variables.

## Health and monitoring
`/actuator/health` (public). Metrics at `/actuator/prometheus` (admin JWT or scrape token). Grafana dashboard "Rackwise overview" is provisioned from `monitoring/grafana/dashboards`.

## Database migrations
Flyway runs at backend start. Destructive Flyway operations (`clean`) are disabled. Back up the `pgdata` volume before upgrading; rollback means restoring it.

## Before calling this production
Not done in this repository: remove the `dev` profile (dev users and seed data), TLS termination, secret management beyond `.env`, login rate limiting, refresh/revocation of tokens, managed backups, resource limits and replicas (the sync lock is in-process, so run a single backend instance), image scanning, restricted network exposure for Postgres, Prometheus and Grafana.
