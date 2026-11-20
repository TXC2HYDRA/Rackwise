# Rackwise

Approval-gated infrastructure capacity planning over **simulated** infrastructure. A requester asks for servers, a deterministic planner proposes a placement, a different person approves it, and applying it re-checks capacity under locks before anything is created. Everything is audited.

Independent portfolio project. Not affiliated with or endorsed by Apple or any other company. No real infrastructure is touched and all data is synthetic.

## Status

Working end to end: backend (10 modules), React frontend (10 screens plus admin inventory management), advisory planning assistant, mock external inventory sync, Prometheus/Grafana monitoring, Docker stack, CI. All automated suites pass (numbers below). Not production-ready: see [Known limitations](#known-limitations).

## What it does

1. **Request.** A requester submits a capacity request (server count, profile, preferred rack group, justification). Idempotent via an `Idempotency-Key`.
2. **Plan.** The planner (`PlacementEngine`, a pure function, no AI) places servers into rack groups limited by free slots and power. The result is `FEASIBLE`, `PARTIAL` or `INFEASIBLE`, with a per-rack-group explanation.
3. **Review.** A reviewer other than the requester approves (binding the plan's content hash) or rejects (comment required). Only fully feasible plans can be approved.
4. **Apply.** Locks the plan and rack groups, checks the inventory version still matches, re-checks slots and power, then creates the servers. If anything changed the plan becomes `STALE` and nothing is created.
5. **Audit.** Every change and every denied attempt is recorded with a correlation id.

Extras: an **advisory planning assistant** (deterministic rules, optional validated LLM wording; never part of any decision) and a **simulated external inventory sync**.

## Quick start

Prerequisites: Docker. For development without containers: a full JDK 21 (not a JRE) and Node 20+.

```bash
cp .env.example .env     # fill POSTGRES_PASSWORD, JWT_SECRET, METRICS_SCRAPE_TOKEN, GRAFANA_ADMIN_PASSWORD (hints inside)
scripts/dev-start.sh     # Postgres, backend, frontend (nginx), Prometheus, Grafana
scripts/seed-demo.sh     # optional: a few demo requests and plans
scripts/dev-stop.sh      # --wipe also deletes the database volume
```

| Service | Default URL |
|---|---|
| App | http://localhost:3000 |
| API health | http://localhost:8080/actuator/health |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3001 (admin / `GRAFANA_ADMIN_PASSWORD`) |

If a port is taken, set `BACKEND_PORT`, `FRONTEND_PORT`, `PROMETHEUS_PORT`, `GRAFANA_PORT` or `POSTGRES_PORT` in `.env`.

Dev users (profile `dev`, which the Docker stack uses): `alice` requester, `bob` and `carol` reviewers, `admin`; password `Rackwise-Dev-1!` (or `DEV_USER_PASSWORD`). The demo inventory is DC-A (rack groups X, Y, Z) and DC-B.

Try the main scenario: sign in as `alice`, request 40 servers in DC-A rack group X, generate a plan, sign out, sign in as `bob`, approve and apply it, then check Inventory and (as `admin`) the Audit trail.

### Without Docker for the app

```bash
docker compose up -d postgres
set -a; . ./.env; set +a
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev     # :8080
cd frontend && npm install && npm run dev                                 # :5173, proxies /api (BACKEND_URL overrides the target)
```

IntelliJ IDEA: open `backend/pom.xml` as a project, set the Project SDK to JDK 21, and add the `.env` values as environment variables in the Spring Boot run configuration (profile `dev`). Open `frontend/` separately for the React app.

## Tests

| Command | What |
|---|---|
| `scripts/test-unit.sh` | backend unit tests, no Docker |
| `scripts/test-integration.sh` | Testcontainers + real HTTP (Docker) |
| `scripts/test-api.sh`, `scripts/test-security.sh` | workflow/contract and auth subsets |
| `scripts/test-frontend.sh` | type-check + Vitest |
| `scripts/test-e2e.sh` | Playwright + axe against a real backend and Postgres |
| `scripts/test-all.sh` | backend suite + frontend checks |
| `scripts/smoke-test.sh [url]` | quick check of a running backend |

Latest results (details and limits in [docs/TESTING.md](docs/TESTING.md)): backend 117 tests (58 unit, 59 integration including real concurrent applies), frontend 47 Vitest, 9 Playwright e2e with axe scans in light and dark mode, assistant evaluation 20/20 cases. CI runs all of it on every PR.

## Documentation

| File | Content |
|---|---|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | modules, workflow, state machines, consistency model |
| [docs/DATABASE.md](docs/DATABASE.md) | schema, constraints, migrations |
| [docs/API.md](docs/API.md) | endpoints, roles, error contract |
| [docs/SECURITY.md](docs/SECURITY.md) | controls and known gaps |
| [docs/TESTING.md](docs/TESTING.md) | real results, how to run, not-tested list |
| [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) | configuration and operations |
| [docs/DECISIONS.md](docs/DECISIONS.md) | decision records |
| [docs/AGENT_EVALUATION.md](docs/AGENT_EVALUATION.md) | assistant evaluation report |
| [docs/DEVELOPMENT_PLAN.md](docs/DEVELOPMENT_PLAN.md) | done / partial / planned |
| [docs/FINAL_REPORT.md](docs/FINAL_REPORT.md) | final report and quality gates |

## Planning assistant

`GET /api/v1/plans/{id}/advice` returns an explanation, risks and what-if suggestions. It is read-only and nothing in approval or execution reads it. The local deterministic assistant is always on. An optional Gemini adapter (`capacitydesk.assistant.llm.enabled=true` plus `GEMINI_API_KEY`) may only rewrite the explanation; only plan figures and rack group codes are sent, and the reply is used only if it is short plain text with no links or markup and every number appears in the plan. Otherwise (timeout 4 s, one retry, any error) the local text is returned with a `fallbackReason`. The adapter has only been tested against a local fake server, not the live service.

## Monitoring

`/actuator/prometheus` accepts an admin JWT or a static scrape token (`METRICS_SCRAPE_TOKEN`) that grants a role valid only for that path. Custom metrics: `rackwise_audit_events_total{action,result}`, capacity gauges `rackwise_capacity_{slots,power}_{used,limit}`, `assistant_advice_total`, `assistant_fallback_total`, `rackwise_inventory_sync_total`, plus JVM and HTTP histograms. The `prod` profile writes JSON logs. `monitoring/` has the Prometheus config and a provisioned Grafana dashboard; Prometheus scraped the backend and Grafana returned live values in the Docker stack (panels were not each inspected visually).

## Known limitations

- Demo configuration: dev users and data, no TLS, ports exposed on the host. Not a production deployment.
- No login rate limiting, refresh tokens or token revocation. The token lives in memory, so a reload means signing in again.
- Audit log is append-only by database trigger, which a database superuser could remove (not tamper-proof).
- Placement is greedy, not an optimal packer. Inventory sync has an in-process lock (single backend instance only).
- No OpenAPI/Swagger UI (see docs/API.md), no code coverage or dependency scanning, no load tests, only Chromium for browser tests, accessibility checked with automated axe only.
- Live Gemini behaviour unverified.
- Frontend has no role-administration screen (the API exists), and the bundle is about 780 kB (no code splitting).

## Repository workflow

Each feature on its own branch, merged by squash through a described PR. Tests on `test/*`, documentation on `docs/*`, CI on `ci/*`.

## License

MIT, see [LICENSE](LICENSE).
