-- A snapshot is archived when captured, then either loaded into staging or quarantined by a blocking check.
ALTER TABLE ops.snapshot DROP CONSTRAINT snapshot_status_check;
ALTER TABLE ops.snapshot
    ADD CONSTRAINT snapshot_status_check CHECK (status IN ('ARCHIVED', 'LOADED', 'QUARANTINED')),
    ADD COLUMN listings_read   integer CHECK (listings_read >= 0),
    ADD COLUMN listings_staged integer CHECK (listings_staged >= 0),
    ADD COLUMN distinct_items  integer CHECK (distinct_items >= 0),
    ADD COLUMN processed_at    timestamptz,
    ADD CONSTRAINT snapshot_processed CHECK ((status = 'ARCHIVED') = (processed_at IS NULL));

-- The loader's queue: archived snapshots in event-time order.
CREATE INDEX snapshot_pending ON ops.snapshot (last_modified, id) WHERE status = 'ARCHIVED';

-- One row per ingestion check per processed snapshot; the catalogue is in docs/data-quality.md.
CREATE TABLE ops.validation_result (
    snapshot_id bigint      NOT NULL REFERENCES ops.snapshot (id),
    rule_id     text        NOT NULL,
    outcome     text        NOT NULL CHECK (outcome IN ('PASSED', 'WARNED', 'FAILED')),
    detail      text        NOT NULL,
    checked_at  timestamptz NOT NULL,
    PRIMARY KEY (snapshot_id, rule_id)
);
