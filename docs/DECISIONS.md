# Decisions

Short records of choices that shaped the system, with the trade-off accepted.

1. **Modular monolith, not microservices.** One deployable, one transactional database. Consistency between inventory, plans and audit needs real transactions; service boundaries would add distributed failure for no benefit at this size. Cost: modules share a process, so boundaries rely on convention (services, not repositories).
2. **Rack group `version` as the inventory version.** A plan records the version of each rack group it used; execution requires equality. Simple, cheap staleness detection. Cost: any change to a rack group invalidates plans that touch it, even if the change would not have affected the fit.
3. **Pessimistic locking for execution.** Rack groups locked in id order inside one transaction. Chosen over retry loops because contention is rare and the outcome (apply or stale) must be exact. An explicit `bumpVersion` update was needed because forcing an increment through JPA did not reliably change the version.
4. **Approval bound to a content hash.** The reviewer must present the hash of the plan they saw; a plan cannot change under an approval. Plans are immutable; changes mean a new plan.
5. **Only fully feasible plans can be approved.** Partial plans can be reviewed and rejected but not applied. Avoids silently fulfilling part of a request.
6. **Deterministic placement engine.** Pure function, greedy by fit. Explainable and testable. Cost: not an optimal packer.
7. **Failures recorded in a separate transaction.** STALE/FAILED outcomes and denied attempts must survive the rollback of the operation that produced them.
8. **JWT in memory, no refresh token.** Avoids token storage in the browser; reload or 15-minute expiry means signing in again. Known gap, accepted for now.
9. **Spring Security resource server with HS256.** One service issues and verifies its own tokens, so a shared secret is enough; the secret is required and checked for length at startup.
10. **Role rules at URL level and method level.** URL rules make unauthorised callers get 403 before body validation runs; `@PreAuthorize` keeps the rule next to the code.
11. **404 for other users' resources.** Do not reveal that an id exists.
12. **Assistant is advisory and bounded.** Local deterministic rules are the default. The LLM may only rewrite the explanation; output must be short plain text with no links or markup and every number must come from the plan, else the local text is used. Only plan figures are sent to the model.
13. **Static scrape token for Prometheus.** JWTs expire in 15 minutes and cannot be used by a scraper. The token grants role METRICS, accepted only by `/actuator/prometheus`.
14. **Audit counter in `AuditService`.** One hook covers all lifecycle events; it counts when an event is recorded, so a rolled-back transaction can overcount slightly. Accepted over scattering counters across services.
15. **Sync goes through the inventory service.** The mock external sync gets limits, locking, version bumps and audit for free, and can never force an over-commit.
16. **nginx passes `Host` with its port.** Spring compares `Origin` and `Host` to decide same-origin; dropping the port made browser logins fail with a CORS 403 (found by running the e2e suite on the containers).
