-- Staged listings: every listing that passed the ingestion checks, typed but otherwise as listed. One partition per
-- UTC day of event time, created by the collector on first use, so expired days can be dropped as a whole.
--
-- No foreign key to ops.snapshot on purpose: it would be checked for each of the ~600k rows a run loads. Loading
-- deletes a snapshot's rows before copying them, which keeps reloads and replays idempotent instead.
CREATE TABLE raw.auction_listing (
    snapshot_id        bigint      NOT NULL,
    last_modified      timestamptz NOT NULL,
    connected_realm_id integer,                -- null for the region-wide commodity market
    auction_id         bigint      NOT NULL,
    item_id            integer     NOT NULL,
    item_variant_key   bigint      NOT NULL,
    quantity           integer     NOT NULL,
    unit_price         bigint,                 -- copper; commodities only
    buyout             bigint,                 -- copper; realm listings, whole listing
    bid                bigint,                 -- copper; realm listings
    time_left          text,
    item_context       integer,
    bonus_lists        integer[],              -- null when the listing has none
    pet_species_id     integer,
    pet_level          smallint,
    pet_quality_id     smallint,
    pet_breed_id       smallint
) PARTITION BY RANGE (last_modified);

-- Rows arrive in event-time order, so a BRIN index stays tiny and still serves incremental reads within a day.
CREATE INDEX auction_listing_last_modified ON raw.auction_listing USING brin (last_modified);
