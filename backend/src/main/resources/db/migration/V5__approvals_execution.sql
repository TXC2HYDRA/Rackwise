CREATE TABLE approvals (
    id           UUID PRIMARY KEY,
    plan_id      UUID         NOT NULL REFERENCES placement_plans (id),
    reviewer_id  UUID         NOT NULL REFERENCES users (id),
    decision     VARCHAR(10)  NOT NULL,
    comment      VARCHAR(500),
    content_hash VARCHAR(64)  NOT NULL,
    decided_at   TIMESTAMPTZ  NOT NULL,
    -- one decision per plan; concurrent reviewers cannot both decide
    CONSTRAINT uq_approvals_plan UNIQUE (plan_id),
    CONSTRAINT ck_approvals_decision CHECK (decision IN ('APPROVED', 'REJECTED'))
);
CREATE INDEX ix_approvals_decided ON approvals (decided_at DESC);

-- provenance of servers created by plan execution
ALTER TABLE servers ADD COLUMN plan_id UUID REFERENCES placement_plans (id);
CREATE INDEX ix_servers_plan ON servers (plan_id);
