# Security

Threat model scope: a web application with three roles over simulated infrastructure. This file states what is implemented and tested, and what is not.

## Authentication
- `POST /auth/login` verifies a bcrypt password hash and issues an HS256 JWT (issuer `rackwise`, 15 minutes, `roles` claim).
- The signing secret comes from `JWT_SECRET`; the application refuses to start if it is shorter than 32 bytes. No secret is committed (`.env` is git-ignored; `.env.example` has blanks).
- Wrong username and wrong password give the same 401 message.
- The browser keeps the token in memory only, never in localStorage or cookies, so there is no ambient credential and CSRF protection is disabled by design (stateless bearer API). A reload requires signing in again.

## Authorization
- Roles: `REQUESTER`, `REVIEWER`, `ADMIN`. Enforced in the backend at URL level and with `@PreAuthorize`; the UI only mirrors them.
- Requesters see only their own requests and plans (404 otherwise). Plan generation is limited to the request owner or an admin.
- Separation of duties: the plan creator and the requester cannot approve or reject. The attempt returns 403 and is audited as `DENIED`.
- Approval is bound to the plan's SHA-256 content hash; plans are immutable.
- Admins cannot remove their own admin role.

## Input handling
- Bean Validation on every request body and query parameter; field errors returned as `details`.
- SQL goes through JPA and parameterised queries. The audit and inventory queries use typed parameters.
- Error responses never include stack traces; unexpected errors are logged with the correlation id and returned as `INTERNAL_ERROR`.
- CORS allows only the configured origin (`capacitydesk.cors.allowed-origins`). In Docker the app is same-origin behind nginx.
- nginx sends `Content-Security-Policy` (`script-src 'self'`), `X-Frame-Options: DENY`, `X-Content-Type-Options`, `Referrer-Policy`.

## Audit
Every business and administrative change writes an `audit_events` row in the same transaction; denied attempts and failures use their own transaction so they survive rollback. A database trigger blocks UPDATE, DELETE and TRUNCATE. Not tamper-proof: a database superuser can remove the trigger.

## Metrics endpoint
`/actuator/prometheus` accepts an admin JWT or a static scrape token (`METRICS_SCRAPE_TOKEN`, constant-time comparison). The token maps to role `METRICS`, which only that path accepts; tests confirm it gets 401 on `/api/**` and other actuator endpoints. Other actuator endpoints are admin-only; only `health` and `prometheus` are exposed.

## Assistant and LLM
- No LLM call happens unless `capacitydesk.assistant.llm.enabled=true` and an API key is set.
- Sent to the model: plan figures and rack group codes only. Not sent: justification text, names, usernames, tokens, other credentials.
- The model's reply is untrusted. It is used only if it is at most 600 characters, has no links, markup characters or control characters, no known injection phrases, and every number in it appears in the plan. Otherwise the deterministic text is returned with a `fallbackReason`. Calls have a timeout and one bounded retry.
- Nothing the assistant returns feeds an approval or execution decision.
- The API key is sent in a header, never logged or placed in the request body.

## Dependencies and supply chain
Versions are pinned through Maven and `package-lock.json`. No dependency scanning is configured in CI yet.

## Known gaps (not implemented)
- No login rate limiting or lockout.
- No refresh tokens or token revocation; a token is valid until it expires.
- No TLS in the Docker stack; terminate TLS in front of nginx for any real deployment.
- The Docker stack runs the `dev` profile (known dev passwords). It is a demo, not a production configuration.
- No CI dependency or container image scanning.
- The Gemini adapter has not been exercised against the live service.
- The injection-phrase filter is a heuristic; the numeric check and the advisory-only design are the actual protections.
