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

## Verified so far
- Request validation (400/422), idempotent replay, FEASIBLE/PARTIAL plans with spillover, visibility rules checked manually.
- Audit rows for login success/failure and inventory writes, role filtering, and the append-only trigger checked manually.
- Inventory reads, admin writes, 403/409/422 rules and pagination limits checked with curl (automated tests pending).
- Login, `/auth/me`, 401/403/400 JSON error contract and admin-only `/api/v1/users` checked manually with curl (automated tests pending on `test/*` branch).
- Backend compiles and starts against Postgres; `/actuator/health` reports UP.
- Frontend production build succeeds.
