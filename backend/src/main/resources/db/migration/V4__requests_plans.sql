CREATE TABLE capacity_requests (
    id                     UUID PRIMARY KEY,
    requester_id           UUID          NOT NULL REFERENCES users (id),
    data_center_id         UUID          NOT NULL REFERENCES data_centers (id),
    preferred_rack_group_id UUID         REFERENCES rack_groups (id),
    allow_other_rack_groups BOOLEAN      NOT NULL DEFAULT FALSE,
    server_count           INTEGER       NOT NULL,
    cpu_cores              INTEGER       NOT NULL,
    ram_gb                 INTEGER       NOT NULL,
    storage_tb             INTEGER       NOT NULL,
    power_watts            INTEGER       NOT NULL,
    justification          VARCHAR(1000) NOT NULL,
    priority               VARCHAR(10)   NOT NULL,
    status                 VARCHAR(20)   NOT NULL,
    idempotency_key        VARCHAR(64),
    created_at             TIMESTAMPTZ   NOT NULL,
    updated_at             TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_requests_count CHECK (server_count BETWEEN 1 AND 500),
    CONSTRAINT ck_requests_power CHECK (power_watts > 0),
    CONSTRAINT ck_requests_priority CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_requests_status CHECK (status IN ('SUBMITTED', 'PLANNED', 'APPROVED', 'REJECTED', 'FULFILLED'))
);
CREATE UNIQUE INDEX uq_requests_idempotency ON capacity_requests (requester_id, idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX ix_requests_requester ON capacity_requests (requester_id, created_at DESC);
CREATE INDEX ix_requests_status ON capacity_requests (status, created_at DESC);

CREATE TABLE placement_plans (
    id                UUID PRIMARY KEY,
    request_id        UUID         NOT NULL REFERENCES capacity_requests (id),
    plan_number       INTEGER      NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    feasibility       VARCHAR(20)  NOT NULL,
    requested_servers INTEGER      NOT NULL,
    allocated_servers INTEGER      NOT NULL,
    summary           TEXT         NOT NULL,
    evaluation_json   TEXT         NOT NULL,
    content_hash      VARCHAR(64)  NOT NULL,
    created_by        UUID         NOT NULL REFERENCES users (id),
    applied_at        TIMESTAMPTZ,
    applied_by        UUID REFERENCES users (id),
    failure_reason    VARCHAR(500),
    version           BIGINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_plans_request_number UNIQUE (request_id, plan_number),
    CONSTRAINT ck_plans_status CHECK (status IN ('PROPOSED', 'APPROVED', 'REJECTED', 'APPLIED', 'FAILED', 'STALE')),
    CONSTRAINT ck_plans_feasibility CHECK (feasibility IN ('FEASIBLE', 'PARTIAL', 'INFEASIBLE')),
    CONSTRAINT ck_plans_counts CHECK (allocated_servers >= 0 AND allocated_servers <= requested_servers)
);
CREATE INDEX ix_plans_status ON placement_plans (status, created_at);

CREATE TABLE plan_allocations (
    id                      UUID PRIMARY KEY,
    plan_id                 UUID        NOT NULL REFERENCES placement_plans (id),
    rack_group_id           UUID        NOT NULL REFERENCES rack_groups (id),
    rack_group_code         VARCHAR(16) NOT NULL,
    data_center_code        VARCHAR(16) NOT NULL,
    server_count            INTEGER     NOT NULL,
    rack_group_version      BIGINT      NOT NULL,
    CONSTRAINT uq_plan_alloc UNIQUE (plan_id, rack_group_id),
    CONSTRAINT ck_plan_alloc_count CHECK (server_count > 0)
);
