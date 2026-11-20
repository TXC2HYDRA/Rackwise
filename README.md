# Rackwise

Approval-gated infrastructure capacity planning platform (simulated infrastructure).
Independent portfolio project. Not affiliated with or endorsed by Apple or any other company.

**Status: bootstrap only.** Nothing below the "Verified so far" list is implemented yet.

## Stack
Java 21, Spring Boot 4.1, Maven wrapper, PostgreSQL 16, Flyway, React + TypeScript + Vite.

## Getting started
Prereqs: JDK 21 (a full JDK, not a JRE), Node 20+, Docker.

```bash
cp .env.example .env            # set POSTGRES_PASSWORD and JWT_SECRET (openssl rand -base64 48)
docker compose up -d postgres   # Postgres on localhost:5440
set -a; . ./.env; set +a
cd backend && ./mvnw spring-boot:run    # http://localhost:8080/actuator/health
cd frontend && npm install && npm run dev
```

IntelliJ: open `backend/pom.xml` as a project, set Project SDK to JDK 21, add the `.env` values as run-configuration environment variables.

## Auth (implemented)
Stateless JWT (HS256, 15 min, in-memory on the client; no refresh token yet). Roles: `REQUESTER`, `REVIEWER`, `ADMIN`.
Dev accounts (profile `dev` only, password `Rackwise-Dev-1!` or `DEV_USER_PASSWORD`): `alice` requester, `bob` and `carol` reviewers, `admin`.
Run with `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`.

## Inventory (implemented)
Data centers, rack groups (slot + power limits, `version` bumped on every capacity change) and servers.
`GET /api/v1/inventory/{datacenters,rack-groups,capacity,servers}` for any signed-in user; writes are ADMIN only.
A server occupies capacity unless `DECOMMISSIONED`. Capacity is derived from server rows, never stored.
Dev profile seeds DC-A (rack groups X, Y, Z) and DC-B.

## Audit trail (implemented)
`audit_events` rows are written in the same transaction as the change they describe (failed logins use their own transaction).
A DB trigger rejects UPDATE/DELETE/TRUNCATE (application-level append-only; a DB superuser can still drop the trigger, so this is not tamper-proof).
`GET /api/v1/audit-events` (ADMIN: everything, REVIEWER: request/plan events only). Every response carries `X-Correlation-Id`.

## Capacity requests and planning (implemented)
`POST /api/v1/capacity-requests` (REQUESTER/ADMIN, optional `Idempotency-Key`), `GET` list/detail, `POST /capacity-requests/{id}/plans`, `GET /plans/{id}`.
`PlacementEngine` is a pure deterministic function: preferred rack group first, other rack groups only when `allowOtherRackGroups` is true, each group limited by free slots and free watts.
Result is `FEASIBLE`, `PARTIAL` (never reported as success) or `INFEASIBLE`, with a per-rack-group evaluation. Plans store a SHA-256 content hash and the rack group versions they were computed against; a new plan marks earlier open plans `STALE`.
Requesters only see their own requests (404 otherwise); reviewers and admins see all.

## Approval and execution (implemented)
Plan states: `PROPOSED -> APPROVED | REJECTED | STALE`, `APPROVED -> APPLIED | FAILED | STALE` (terminal otherwise).
- `POST /plans/{id}/approve` (REVIEWER) must carry the plan's `contentHash`; only `FEASIBLE` plans are approvable. Requesters (and plan creators) cannot decide their own plans (403, audited as DENIED).
- `POST /plans/{id}/reject` (REVIEWER, comment required). `GET /approvals/pending`, `GET /approvals/history`.
- `POST /plans/{id}/apply` (REVIEWER/ADMIN): locks the plan and its rack groups in id order, requires each rack group `version` to equal the plan snapshot, re-checks slots and power, creates servers, bumps versions. A changed rack group makes the plan `STALE`; the request returns to `PLANNED` for a new plan.
- One decision per plan (unique constraint) and unique server hostnames back the application-level duplicate guards.

## External inventory sync (mock)
`POST /api/v1/admin/inventory-sync` (ADMIN) pulls from an `ExternalInventorySource`; only a synthetic mock CMDB exists (5 servers in DC-A/Y plus one record for an unknown rack group). New servers are created and statuses aligned, never deleted. Everything goes through `InventoryService`, so capacity limits, duplicate hostnames, locking, version bumps (which stale open plans) and audit all apply; records that do not fit are skipped and reported. `GET .../runs` lists history. Verified against the running app: first run created 5 and skipped 1, second run changed nothing, reviewer got 403.

## Planning assistant (advisory only)
`GET /api/v1/plans/{id}/advice` returns an explanation, risks and what-if suggestions for a plan. It is read-only: nothing in the approval or execution path reads it, and it uses the plan's own visibility rules.
- **Local (always on):** deterministic rules over the plan numbers (`LocalPlanningAssistant`).
- **Optional LLM narrative (off by default):** set `capacitydesk.assistant.llm.enabled=true` and `GEMINI_API_KEY`. The model only rewrites the explanation text. Only plan figures and rack group codes are sent (no free text, names or credentials). Output is used only if it is short plain text with no links/markup and every number appears in the plan; otherwise, on timeout (4s), error, or after one bounded retry, the local text is returned with a `fallbackReason`.

## CI
`.github/workflows/ci.yml` runs on every PR and push to `main`: backend tests (Testcontainers), frontend lint + type-check + Vitest + build, then an end-to-end job that starts the Docker stack and runs Playwright with axe and checks the Prometheus target is up. Reports are uploaded as artifacts.

## Run everything with Docker
```
cp .env.example .env   # fill POSTGRES_PASSWORD, JWT_SECRET (openssl rand -base64 48), METRICS_SCRAPE_TOKEN (openssl rand -hex 24), GRAFANA_ADMIN_PASSWORD
scripts/dev-start.sh   # builds and starts Postgres, backend, frontend (nginx), Prometheus, Grafana
scripts/seed-demo.sh http://localhost:8080   # optional demo requests/plans
scripts/dev-stop.sh    # add --wipe to delete the database volume
```
The stack uses the `dev,prod` profiles: synthetic inventory, dev users and JSON logs. It is a demo stack, not a production configuration. nginx serves the SPA with a CSP and proxies `/api` to the backend.
Verified on this machine: all five services healthy; Prometheus target `rackwise-backend` UP using the scrape token; Grafana loaded the provisioned dashboard and queries Prometheus (returned live `rackwise_capacity_slots_used`); the 8 Playwright e2e tests (incl. axe) pass against the containerised app (`E2E_BASE_URL=http://localhost:3000 npx playwright test` in `frontend/`). Running the stack found a real bug (nginx dropped the port from `Host`, so Spring rejected browser logins with a CORS 403); fixed.

## Monitoring
- `/actuator/prometheus` needs an ADMIN JWT, or a static bearer token (`METRICS_SCRAPE_TOKEN`) that grants role METRICS and works only on that path (verified against the running app: no auth 401, wrong token 401, scrape token 200 on `/actuator/prometheus` and 401 on `/api/**` and other actuator endpoints).
- Custom metrics: `rackwise_audit_events_total{action,result}` (plan generated/approved/rejected/applied/failed/stale, denied attempts; counted when recorded), capacity gauges `rackwise_capacity_{slots,power}_{used,limit}`, `assistant_advice_total{source}`, `assistant_fallback_total{reason}`, plus standard JVM and HTTP histograms.
- `monitoring/` holds the Prometheus scrape config and a provisioned Grafana dashboard (JSON not yet loaded into a running Grafana).
- `prod` profile switches console logs to structured JSON (verified); the correlation id is carried via MDC.

## Frontend (implemented)
React + TypeScript + Vite, Tailwind, TanStack Query, Recharts. Screens: login, dashboard, inventory, new request, my requests, request detail, plan detail (approve / reject / apply, plus the advisory planning-assistant panel), approval queue, approval history, audit trail, manage inventory (admin; includes the simulated external inventory sync). Light and dark mode.
The access token is kept in memory only (never in browser storage), so a page reload requires signing in again; the session ends automatically when the token expires.
Dev: `cd frontend && npm run dev` (proxies `/api` to `localhost:8080`).

## Verified so far
- Backend: 117 tests pass (unit + Testcontainers integration): includes 36 for the planning assistant (rules, validator, fake-server Gemini adapter, 20-case evaluation, endpoint security), 4 for metrics and scrape-token security, 8 for inventory sync (idempotency, capacity never exceeded, stale approved plans, concurrent runs, admin-only).
- Frontend: type-check, lint and production build pass; 40 Vitest component/unit tests pass (`scripts/test-frontend.sh`).
- Browser e2e (Playwright + real backend + PostgreSQL, `scripts/test-e2e.sh`): 8 tests pass, covering the full request → plan → review → apply → audit workflow, login failure, route guard, form validation, self-approval prevention, and axe checks (WCAG 2 A/AA, serious/critical) on login, dashboard, inventory, queue, audit and the request form in light and dark mode. The first run found and fixed a dark-mode contrast failure.
- End-to-end workflow checked manually: unapproved/rejected/re-applied plans return 409, self-approval 403, wrong hash 409, two competing plans applied concurrently gave exactly one APPLIED and one STALE with capacity not exceeded (automated tests pending).
- Request validation (400/422), idempotent replay, FEASIBLE/PARTIAL plans with spillover, visibility rules checked manually.
- Audit rows for login success/failure and inventory writes, role filtering, and the append-only trigger checked manually.
- Inventory reads, admin writes, 403/409/422 rules and pagination limits checked with curl (automated tests pending).
- Login, `/auth/me`, 401/403/400 JSON error contract and admin-only `/api/v1/users` checked manually with curl (automated tests pending on `test/*` branch).
- Backend compiles and starts against Postgres; `/actuator/health` reports UP.
- Frontend production build succeeds.
