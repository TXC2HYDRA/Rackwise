-- Runs of the external inventory synchronisation (mock adapter in this project).
CREATE TABLE sync_runs (
    id           UUID PRIMARY KEY,
    source       VARCHAR(60)  NOT NULL,
    status       VARCHAR(10)  NOT NULL,
    started_at   TIMESTAMPTZ  NOT NULL,
    finished_at  TIMESTAMPTZ,
    created      INT          NOT NULL DEFAULT 0,
    updated      INT          NOT NULL DEFAULT 0,
    unchanged    INT          NOT NULL DEFAULT 0,
    skipped      INT          NOT NULL DEFAULT 0,
    skip_details TEXT,
    error        VARCHAR(500),
    triggered_by UUID         NOT NULL REFERENCES users (id),
    CONSTRAINT ck_sync_runs_status CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED'))
);
CREATE INDEX ix_sync_runs_started ON sync_runs (started_at DESC);
