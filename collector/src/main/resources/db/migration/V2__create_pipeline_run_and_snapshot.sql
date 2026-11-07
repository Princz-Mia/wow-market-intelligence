-- One row per pipeline run, whatever started it. Counts describe the capture step.
CREATE TABLE ops.pipeline_run (
    id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    run_trigger       text        NOT NULL CHECK (run_trigger IN ('SCHEDULE', 'MANUAL')),
    status            text        NOT NULL CHECK (status IN ('RUNNING', 'SUCCEEDED', 'PARTIAL', 'FAILED')),
    started_at        timestamptz NOT NULL,
    finished_at       timestamptz,
    sources_total     integer     NOT NULL CHECK (sources_total >= 0),
    snapshots_new     integer     NOT NULL DEFAULT 0 CHECK (snapshots_new >= 0),
    sources_unchanged integer     NOT NULL DEFAULT 0 CHECK (sources_unchanged >= 0),
    sources_failed    integer     NOT NULL DEFAULT 0 CHECK (sources_failed >= 0),
    error_summary     text,
    CHECK ((status = 'RUNNING') = (finished_at IS NULL)),
    CHECK (finished_at IS NULL OR finished_at >= started_at)
);

-- One row per captured snapshot. A source and its Last-Modified identify a snapshot, so capturing it twice is
-- refused by the unique key and a rerun changes nothing.
CREATE TABLE ops.snapshot (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    region           text        NOT NULL,
    source           text        NOT NULL,
    last_modified    timestamptz NOT NULL,
    fetched_at       timestamptz NOT NULL,
    pipeline_run_id  bigint      NOT NULL REFERENCES ops.pipeline_run (id),
    archive_location text        NOT NULL,
    archive_bytes    bigint      NOT NULL CHECK (archive_bytes > 0),
    archive_sha256   char(64)    NOT NULL,
    status           text        NOT NULL CHECK (status IN ('ARCHIVED')),
    UNIQUE (region, source, last_modified)
);

-- Serves "newest snapshot per source" for conditional requests and freshness.
CREATE INDEX snapshot_source_latest ON ops.snapshot (region, source, last_modified DESC);
