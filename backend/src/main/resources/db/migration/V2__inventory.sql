CREATE TABLE data_centers (
    id         UUID PRIMARY KEY,
    code       VARCHAR(16)  NOT NULL,
    name       VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_data_centers_code UNIQUE (code)
);

-- "version" doubles as the inventory version of the rack group: it is bumped on every
-- limit change and every allocation, so plans can detect stale inventory.
CREATE TABLE rack_groups (
    id                    UUID PRIMARY KEY,
    data_center_id        UUID         NOT NULL REFERENCES data_centers (id),
    code                  VARCHAR(16)  NOT NULL,
    name                  VARCHAR(120) NOT NULL,
    slot_capacity         INTEGER      NOT NULL,
    power_capacity_watts  BIGINT       NOT NULL,
    version               BIGINT       NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ  NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_rack_groups_dc_code UNIQUE (data_center_id, code),
    CONSTRAINT ck_rack_groups_slots CHECK (slot_capacity > 0),
    CONSTRAINT ck_rack_groups_power CHECK (power_capacity_watts > 0)
);

CREATE TABLE servers (
    id            UUID PRIMARY KEY,
    hostname      VARCHAR(80) NOT NULL,
    rack_group_id UUID        NOT NULL REFERENCES rack_groups (id),
    cpu_cores     INTEGER     NOT NULL,
    ram_gb        INTEGER     NOT NULL,
    storage_tb    INTEGER     NOT NULL,
    power_watts   INTEGER     NOT NULL,
    status        VARCHAR(20) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_servers_hostname UNIQUE (hostname),
    CONSTRAINT ck_servers_status CHECK (status IN ('ACTIVE', 'MAINTENANCE', 'DECOMMISSIONED')),
    CONSTRAINT ck_servers_cpu CHECK (cpu_cores > 0),
    CONSTRAINT ck_servers_ram CHECK (ram_gb > 0),
    CONSTRAINT ck_servers_storage CHECK (storage_tb > 0),
    CONSTRAINT ck_servers_power CHECK (power_watts > 0)
);
CREATE INDEX ix_servers_rack_group ON servers (rack_group_id, status);
CREATE INDEX ix_rack_groups_dc ON rack_groups (data_center_id);
