# Testing

All numbers below come from runs on 2026-11-20 on the development machine, and from the first GitHub Actions run of PR #19 (all three jobs green). Nothing here is a target; if a suite is not listed it was not run.

## Summary

| Suite | Tool | Result |
|---|---|---|
| Backend unit | JUnit 5 | 27 tests (placement engine 11, state machines 11, user service 5) + 31 assistant unit tests; all pass |
| Backend integration | Spring Boot + Testcontainers (PostgreSQL 16, real HTTP) | 58 tests; all pass |
| Assistant evaluation | JUnit harness | 20 / 20 cases (see AGENT_EVALUATION.md) |
| Frontend unit/component | Vitest + Testing Library | 40 tests; all pass |
| Browser end to end | Playwright (Chromium) + axe-core | 8 tests; all pass, locally against Vite and against the Docker stack, and in CI |
| Static | `tsc -b`, oxlint, production build | pass (2 Fast Refresh lint warnings, 0 errors) |
| Smoke | `scripts/smoke-test.sh` on the Docker stack | passed |

Backend total: 117 tests, 0 failures, 0 skipped.

## Backend integration tests (per class)

| Class | Tests | Covers |
|---|---|---|
| `WorkflowIT` | 15 | the 40-server expansion end to end, self-approval denial, unapproved/duplicate apply, stale plans, partial and infeasible plans, idempotent submission, validation, pagination |
| `ConcurrencyIT` | 4 | simultaneous applies competing for one rack group (exactly one wins, loser `STALE`), simultaneous approvals, simultaneous requests with one idempotency key, no over-allocation |
| `AuthSecurityIT` | 10 | login, expired/forged/missing tokens, role matrix, privilege escalation attempts, actuator protection |
| `InventoryIT` | 9 | capacity derivation, limits, duplicate hostnames/codes, over-capacity writes rejected |
| `AuditIT` | 4 | audit rows in the same transaction, denied attempts survive rollback, append-only trigger, role-scoped reads |
| `AssistantIT` | 5 | advice endpoint access rules (owner, reviewer, other user 404, anonymous 401), no side effects |
| `MetricsIT` | 4 | scrape token scope, lifecycle counters including denied attempts, capacity gauges, assistant counter |
| `InventorySyncIT` | 8 | idempotent sync, status alignment, capacity never exceeded, failure handling, stale approved plan, single run at a time, admin-only |

Concurrency tests use real parallel HTTP calls against PostgreSQL, not mocks.

## Frontend

- Vitest (40): API client (bearer header, error mapping, network failure, 401 handling), login (validation, bad credentials, success, offline), request form (client rules, idempotency header, server field errors), plan detail (approve binds hash, reject needs a comment, partial not approvable, own-request notice, stale/error states), loading/empty/error states, dashboard figures, status badges with text labels.
- Playwright (8): full workflow (request, plan, independent review, apply, inventory update, history, audit), invalid login, signed-out redirect, form validation, admin cannot decide their own request's plan, plus axe scans (WCAG 2 A/AA, serious and critical) on login, dashboard, inventory, approval queue, audit trail and request form in light and dark mode.
- Findings from these runs that were fixed: dark-mode contrast failures (axe); nginx dropping the port from `Host`, which made browser logins fail with a CORS 403 (found by running e2e against the containers).

## How to run

```bash
scripts/test-unit.sh          # no Docker
scripts/test-integration.sh   # Docker for Testcontainers
scripts/test-api.sh           # workflow, inventory, audit
scripts/test-security.sh      # authentication and authorization
scripts/test-frontend.sh      # tsc + Vitest
scripts/test-e2e.sh           # starts Postgres + backend (port 8089), runs Playwright
scripts/test-all.sh           # backend suite + frontend checks
```
Against a running Docker stack: `cd frontend && E2E_BASE_URL=http://localhost:3000 npx playwright test`.

## CI
`.github/workflows/ci.yml` runs backend tests, frontend lint/types/tests/build, and the e2e job (Docker stack + Playwright + axe + Prometheus target check). First run on PR #19: backend 1m22s, frontend 12s, e2e 2m16s, all passing.

## Not tested / limits
- The Gemini adapter against the live API (only a local fake HTTP server).
- Load and performance; no benchmarks were run.
- Manual assistive-technology testing; accessibility coverage is automated axe only, which finds a subset of issues.
- Browsers other than Chromium; mobile widths.
- Grafana dashboard panels were provisioned and a datasource query returned data, but each panel was not visually inspected.
- Upgrade and migration-from-existing-data paths (migrations are only tested on empty databases).
- No code coverage measurement is configured.
