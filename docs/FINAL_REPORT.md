# Final report

Date: 2026-11-20. Repository: `TXC2HYDRA/Rackwise`. 24 pull requests merged to `main` before this one, each from its own branch.

## 1. What was built

Rackwise is an approval-gated capacity planning platform over simulated infrastructure.

- **Backend (Java 21, Spring Boot 4.1, PostgreSQL, Flyway)**, ten modules: identity, inventory, capacity requests, planning, approval, execution, audit, planning assistant, external inventory sync (mock), observability.
- **Frontend (React, TypeScript, Vite, Tailwind, TanStack Query, Recharts)**, ten screens (login, dashboard, inventory, new request, my requests, request detail, plan detail, approval queue and history, audit trail) plus an admin inventory page with the sync control. Light and dark mode.
- **Planning assistant**: deterministic local rules, optional Gemini wording behind strict validation, never in any decision path.
- **Operations**: Docker Compose stack (Postgres, backend, nginx frontend, Prometheus, Grafana), JSON logs, custom metrics, scripts, GitHub Actions CI.
- **Documentation**: README plus ARCHITECTURE, DATABASE, API, SECURITY, TESTING, DEPLOYMENT, DECISIONS, DEVELOPMENT_PLAN, AGENT_EVALUATION.

## 2. Evidence

| Gate | Status | Evidence |
|---|---|---|
| Backend compiles and starts | PASS | CI; Docker stack healthy |
| Backend unit tests | PASS | 58 tests (placement 11, state machines 11, user service 5, assistant 31) |
| Backend integration tests (Testcontainers, real HTTP) | PASS | 59 tests; includes concurrent applies, security matrix, audit, metrics, sync |
| Concurrency safety | PASS | `ConcurrencyIT`: competing applies give exactly one APPLIED and one STALE, no over-allocation |
| Authorization and security tests | PASS | `AuthSecurityIT` (10), scrape-token scope tests, self-approval denial |
| Frontend lint | PASS | 0 errors, 2 Fast Refresh warnings |
| Frontend type-check and build | PASS | `tsc -b`, Vite build |
| Frontend component tests | PASS | 47 Vitest tests |
| Browser end-to-end | PASS | 9 Playwright tests; locally and in CI against the Docker stack |
| Automated accessibility | PASS (partial scope) | axe WCAG 2 A/AA serious+critical: login, dashboard, inventory, approval queue, audit, request form, light and dark |
| Manual accessibility (screen readers, keyboard-only pass) | NOT VERIFIED | not performed |
| Docker images build and stack runs | PASS | all services healthy; smoke and seed scripts run |
| Prometheus scrape | PASS | target `rackwise-backend` up using the scrape token (also asserted in CI) |
| Grafana | PARTIAL | dashboard provisioned, datasource query returned live data; panels not visually inspected |
| CI | PASS | backend, frontend and e2e jobs green on PRs #19 and #24 |
| Assistant evaluation | PASS (scripted) | 20/20 cases, see AGENT_EVALUATION.md |
| Live LLM behaviour | NOT VERIFIED | no API key; adapter tested against a local fake server only |
| Code coverage | NOT VERIFIED | not measured |
| Dependency / image vulnerability scan | NOT VERIFIED | not configured |
| Load / performance | NOT VERIFIED | no benchmarks run |
| Browsers other than Chromium, mobile layouts | NOT VERIFIED | not tested |
| OpenAPI / Swagger UI | NOT IMPLEMENTED | docs/API.md is hand-written |
| Login rate limiting, refresh tokens, TLS | NOT IMPLEMENTED | listed as known gaps |

## 3. Problems found by testing and fixed

- Forced version increment did not bump `rack_groups.version`, so a concurrent loser reported NO_CAPACITY instead of STALE. Replaced by an explicit version update; verified.
- 400 returned instead of 403 for unauthorised callers with invalid bodies. Added URL-level role rules.
- Jackson failed on null primitives with a generic error. Disabled that check so Bean Validation reports field errors.
- Dark-mode contrast failures found by axe. Fixed.
- LLM validator accepted markdown (`**55**`), found by the evaluation harness. Fixed in its own PR.
- nginx dropped the port from `Host`, so browser logins in the Docker stack got a CORS 403. Found only by running e2e on the containers. Fixed.
- A dev database with more than ten pending plans hid the new plan from a paged queue in an e2e test. The test now pages.

## 4. Honest limitations

See README "Known limitations" and docs/SECURITY.md. Highlights: demo-grade deployment (dev users, no TLS), no login throttling or token revocation, greedy placement, in-process sync lock (one backend instance), audit trigger not tamper-proof, assistant LLM path unverified against the live service, no role-administration screen, no coverage or vulnerability scanning.

Two other process notes: the audit counters increment when an event is recorded (a rolled-back transaction can overcount slightly), and the MIT license was chosen as a default, so change it if you prefer another.

## 5. Suggested next steps (in priority order)

1. Login rate limiting and a production profile without dev users.
2. Dependency and container scanning plus coverage reporting in CI.
3. OpenAPI generation.
4. Role administration screen; frontend code splitting.
5. Verify the Gemini adapter against the live API with a real key and add a recorded-contract test.
6. Load test the apply path; DB advisory lock for multi-instance sync.

## 6. How to reproduce the results

```bash
scripts/test-all.sh                 # backend 117 + frontend checks (Docker needed for Testcontainers)
scripts/test-e2e.sh                 # Playwright + axe against a real backend and Postgres
scripts/dev-start.sh && (cd frontend && E2E_BASE_URL=http://localhost:3000 npx playwright test)
```
