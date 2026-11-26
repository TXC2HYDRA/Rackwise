# Architecture

Rackwise is a modular monolith: one Spring Boot application (`com.capacitydesk`), one PostgreSQL database, one React single-page app. Infrastructure is simulated; no real systems are touched.

## Shape

```
Browser (React SPA)
   │  same origin
nginx (docker) / Vite dev proxy ── /api ──▶ Spring Boot backend ──▶ PostgreSQL
                                                │
                                                ├─ /actuator/prometheus ◀── Prometheus ──▶ Grafana
                                                └─ optional: Gemini HTTP API (narrative text only)
```

## Backend modules

Each module has `api` (controllers, DTOs), `application` (services, transactions), `domain` (entities, rules), `persistence` (repositories). Modules call each other through application services, never through another module's repositories.

| Module | Responsibility |
|---|---|
| `identity` | Users, roles, login (JWT), role administration |
| `inventory` | Data centers, rack groups, servers, capacity derivation, allocation for plans |
| `capacityrequest` | Requests and their state machine, idempotent creation |
| `planning` | `PlacementEngine` (pure, deterministic), plan generation, plan views |
| `approval` | Approve / reject bound to the plan content hash, self-approval denial, queue and history |
| `execution` | Applying an approved plan atomically with a capacity and version re-check |
| `audit` | Append-only event log, correlation ids |
| `assistant` | Advisory explanations (local rules, optional LLM narrative) |
| `inventorysync` | Mock external inventory adapter and sync runs |
| `observability` | Capacity gauges (audit counters live in `AuditService`) |
| `security`, `common` | JWT/role configuration, error contract, correlation filter, base entities |

## Core workflow

1. A requester submits a capacity request (`SUBMITTED`).
2. The owner (or an admin) generates a plan. `PlacementEngine` computes placement from current rack group free slots and power. Result is `FEASIBLE`, `PARTIAL` or `INFEASIBLE`. Earlier open plans for the request become `STALE`.
3. A different reviewer approves (must present the plan's content hash) or rejects (comment required). Only `FEASIBLE` plans are approvable.
4. A reviewer or admin applies the approved plan. Execution locks the plan row and the rack groups (in id order), checks each rack group's `version` equals the plan snapshot, re-checks slots and power, creates the servers, and bumps versions. If anything changed, the plan is recorded `STALE` or `FAILED` in a separate transaction and nothing is created.

State machines: request `SUBMITTED → PLANNED → APPROVED → FULFILLED` (or `REJECTED`; a failed apply returns it to `PLANNED`); plan `PROPOSED → APPROVED | REJECTED | STALE`, `APPROVED → APPLIED | FAILED | STALE`.

## Consistency model

- Capacity is never stored; it is derived from server rows (a server occupies capacity unless `DECOMMISSIONED`).
- `rack_groups.version` is the inventory version. Every capacity change (admin write, plan application, sync) bumps it. Plans record the version they were computed against.
- Concurrent applies of plans competing for the same rack group are serialized by `PESSIMISTIC_WRITE`; the loser sees a version mismatch and is marked `STALE`. This is tested with real concurrent HTTP requests against PostgreSQL.

## The assistant is not in the decision path

`assistant` reads a `PlanView` and returns text and suggestions. No code in approval, execution or planning reads its output. The LLM, when enabled, can only replace the explanation sentence, and only after validation (see SECURITY.md).

## Frontend

React 19, TypeScript, Vite, React Router, TanStack Query for server state, Tailwind v4 (class-based dark mode); utilisation is shown with accessible meters and tables, no chart library. The JWT lives in memory only. Route guards and hidden buttons are convenience; the backend enforces every rule.

## Observability

Micrometer to Prometheus. Audit events are counted by action and result, fleet capacity is exposed as gauges, HTTP latency histograms are on. The `prod` profile writes JSON logs; every request carries `X-Correlation-Id` (also stored on audit rows).
