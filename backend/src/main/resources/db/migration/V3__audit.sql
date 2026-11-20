CREATE TABLE audit_events (
    id             UUID PRIMARY KEY,
    occurred_at    TIMESTAMPTZ  NOT NULL,
    actor_id       UUID,
    actor_name     VARCHAR(64)  NOT NULL,
    action         VARCHAR(60)  NOT NULL,
    target_type    VARCHAR(40)  NOT NULL,
    target_id      VARCHAR(64)  NOT NULL,
    result         VARCHAR(20)  NOT NULL,
    correlation_id VARCHAR(64),
    before_state   TEXT,
    after_state    TEXT,
    detail         VARCHAR(500),
    CONSTRAINT ck_audit_result CHECK (result IN ('SUCCESS', 'FAILURE', 'DENIED'))
);
CREATE INDEX ix_audit_occurred ON audit_events (occurred_at DESC);
CREATE INDEX ix_audit_target ON audit_events (target_type, target_id);
CREATE INDEX ix_audit_actor ON audit_events (actor_id);

-- Application-level append-only guard. A database superuser can still drop the trigger;
-- this is NOT cryptographic tamper resistance.
CREATE FUNCTION audit_events_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_events is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_events_no_change BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION audit_events_append_only();
CREATE TRIGGER trg_audit_events_no_truncate BEFORE TRUNCATE ON audit_events
    FOR EACH STATEMENT EXECUTE FUNCTION audit_events_append_only();
