# API

Base path `/api/v1`. JSON in and out. Authenticate with `Authorization: Bearer <token>` from `POST /auth/login`. Lists are paged (`page` from 0, `size` default 25, max 100) and return `{content, page, size, totalElements, totalPages}`.

There is no OpenAPI/Swagger UI yet; this file is the reference. Use `scripts/smoke-test.sh` and `scripts/seed-demo.sh` for worked examples with curl.

## Error contract

Every error: `{ "timestamp", "status", "code", "message", "path", "details": [] }`. `details` holds field messages as `"field: message"` for validation errors. Stack traces and internals are never returned.

| Status | Codes (examples) |
|---|---|
| 400 | `VALIDATION_FAILED` |
| 401 | `UNAUTHENTICATED` (missing, invalid or expired token; also wrong login) |
| 403 | `FORBIDDEN` (role), `SELF_APPROVAL_FORBIDDEN`, `SELF_DEMOTION` |
| 404 | `NOT_FOUND` (also for other users' requests and plans) |
| 409 | `STALE_INVENTORY`, `NO_CAPACITY`, `DUPLICATE_HOSTNAME`, `DUPLICATE_CODE`, `DUPLICATE_SUBMISSION`, `PLAN_NOT_PROPOSED`, `PLAN_NOT_APPROVABLE`, `PLAN_CONTENT_MISMATCH`, `ALREADY_APPLIED`, `REQUEST_CLOSED`, `SYNC_IN_PROGRESS`, `ILLEGAL_*_TRANSITION`, `LIMIT_BELOW_USAGE` |
| 422 | `UNKNOWN_DATA_CENTER`, `UNKNOWN_RACK_GROUP` |
| 500 | `INTERNAL_ERROR` |

Every response carries `X-Correlation-Id`; send your own to trace a call end to end.

## Endpoints

Roles: **R** requester, **V** reviewer, **A** admin, **any** = any signed-in user.

### Auth and users
| Method | Path | Who | Notes |
|---|---|---|---|
| POST | `/auth/login` | public | `{username, password}` → `{accessToken, tokenType, expiresIn, user}` |
| GET | `/auth/me` | any | current user and roles |
| GET | `/users` | A | |
| PUT | `/users/{id}/roles` | A | cannot remove your own admin role |

### Inventory
| Method | Path | Who | Notes |
|---|---|---|---|
| GET | `/inventory/datacenters` | any | |
| GET | `/inventory/rack-groups` | any | `?dataCenterId=` |
| GET | `/inventory/rack-groups/{id}` | any | slots/power used and available, `version` |
| GET | `/inventory/capacity` | any | totals plus per rack group; `?dataCenterId=` |
| GET | `/inventory/servers` | any | `?rackGroupId=&status=` |
| POST | `/inventory/datacenters` | A | |
| POST | `/inventory/rack-groups` | A | |
| PUT | `/inventory/rack-groups/{id}/limits` | A | rejected if below current usage |
| POST | `/inventory/servers` | A | rejected if it would exceed slots or power |
| PUT | `/inventory/servers/{id}/status` | A | `ACTIVE`, `MAINTENANCE`, `DECOMMISSIONED` |

### Requests and plans
| Method | Path | Who | Notes |
|---|---|---|---|
| POST | `/capacity-requests` | R, A | optional `Idempotency-Key` header (8–64 chars `[A-Za-z0-9-]`): same key returns the original request |
| GET | `/capacity-requests` | any | requesters see their own; reviewers and admins see all; `?status=` |
| GET | `/capacity-requests/{id}` | any | 404 if not visible |
| POST | `/capacity-requests/{id}/plans` | owner, A | computes a plan; earlier open plans become `STALE` |
| GET | `/capacity-requests/{id}/plans` | any | |
| GET | `/plans/{id}` | any | allocations, per rack group evaluation, `contentHash` |
| GET | `/plans/{id}/advice` | any | advisory explanation, risks, suggestions; read-only |

Create request body: `dataCenterId`, optional `preferredRackGroupId`, `allowOtherRackGroups`, `serverCount` (1–500), `cpuCores`, `ramGb`, `storageTb`, `powerWatts` (per server), `priority` (`LOW|NORMAL|HIGH|CRITICAL`), `justification`.

### Approval and execution
| Method | Path | Who | Notes |
|---|---|---|---|
| GET | `/approvals/pending` | V, A | proposed plans, oldest first |
| GET | `/approvals/history` | V, A | decisions |
| POST | `/plans/{id}/approve` | V | body `{contentHash, comment?}`; only `FEASIBLE` plans; not the requester or plan creator |
| POST | `/plans/{id}/reject` | V | body `{comment}` required |
| POST | `/plans/{id}/apply` | V, A | only `APPROVED`; re-checks capacity and inventory version; creates servers; returns `{plan, serversCreated}` |

### Audit and admin
| Method | Path | Who | Notes |
|---|---|---|---|
| GET | `/audit-events` | A (all), V (request and plan events) | filters `action, targetType, targetId, actorId, from, to` |
| POST | `/admin/inventory-sync` | A | runs the mock external sync; returns the run |
| GET | `/admin/inventory-sync/runs` | A | latest 20 runs |

### Operations (not under `/api/v1`)
| Path | Who |
|---|---|
| `/actuator/health` | public |
| `/actuator/prometheus` | admin JWT, or the scrape token (role METRICS) |
| other `/actuator/**` | admin |
