# Development plan and status

Status as of 2026-11-20. "Done" means implemented and exercised by the tests listed in TESTING.md.

## Done
| Area | Notes |
|---|---|
| Identity, JWT, roles | login, `/me`, role administration, URL and method level rules |
| Inventory | data centers, rack groups, servers, derived capacity, admin writes |
| Capacity requests | validation, idempotent creation, state machine |
| Planning | deterministic placement engine, immutable plans, content hash, stale handling |
| Approval | hash-bound approve/reject, self-approval denied, queue, history |
| Execution | atomic apply with lock, version check, capacity re-check, separate-transaction failure records |
| Audit | append-only table with trigger, correlation ids, role-scoped query |
| Planning assistant | advisory endpoint, local rules, optional Gemini narrative with validation and fallback |
| External inventory sync (mock) | admin-triggered runs, history, audit, metrics |
| Observability | custom metrics, scrape token, Prometheus config, Grafana dashboard, JSON logs |
| Frontend | ten screens plus admin inventory management, light/dark, accessible patterns |
| Tests | 117 backend, 40 Vitest, 8 Playwright with axe |
| Delivery | Dockerfiles, compose stack, scripts, GitHub Actions |
| Documentation | README, architecture, database, API, security, testing, deployment, decisions, assistant evaluation |

## Partial
- Frontend does not yet expose the inventory sync, role administration or assistant advice; the APIs exist and are tested.
- The audit page action filter does not list the inventory sync actions.
- Grafana dashboard provisioned and queried, panels not individually inspected.
- Gemini adapter tested against a local fake server only.

## Planned / known gaps
- Login rate limiting; refresh tokens and revocation; TLS.
- OpenAPI/Swagger UI generation.
- Code splitting of the frontend bundle (about 780 kB).
- Dependency and image vulnerability scanning in CI; coverage reporting.
- Load testing; mobile and cross-browser tests.
- A production profile without dev users; multi-instance safe sync lock.

## Delivery history (PRs)
Backend bootstrap, modules and their tests (#1–#8), frontend app (#9) and tests (#10), planning assistant (#11, fix #12, tests #13), observability (#14, tests #15), inventory sync (#16, tests #17), Docker stack (#18), CI (#19), documentation (#20, #21, and this set). Each feature on its own branch, merged by squash with a described PR; tests on `test/*`, documentation on `docs/*`, CI on `ci/*`.
