# Data quality

Checks run at two points. Each check has an id, a rule and a severity: **ERROR** blocks the data, **WARN** is recorded and the data goes on. Every result is stored, so the Data Health page can show what was checked and what was found.

## Checkpoint 1: ingestion

The collector checks each archived snapshot while streaming it into staging, before anything reaches `raw.auction_listing`. A snapshot with a failed check is **quarantined**: its file stays in the archive and nothing is staged, so it can be replayed once the cause is understood. One row per check and snapshot goes to `ops.validation_result`, and the snapshot's status, listing count and distinct item count to `ops.snapshot`.

| ID | Rule | Severity |
|---|---|---|
| DQ-I01 | The archived file exists and is JSON with an `auctions` array | ERROR |
| DQ-I02 | A snapshot already captured (same source and `Last-Modified`) is skipped, not reloaded | idempotency, no result row |
| DQ-I03 | `Last-Modified` is newer than the last loaded snapshot of the source | ERROR |
| DQ-I04 | Auction id, item id, quantity and at least one price are present | listing dropped; ERROR above 0.1% of the snapshot, else WARN |
| DQ-I05 | Quantity and prices are positive, and the pricing mode matches the source (see below) | as DQ-I04 |
| DQ-I06 | No auction id appears twice in the snapshot | ERROR |
| DQ-I07 | Listing count within ±50% of the source's trailing 7-day median | WARN beyond ±50%, ERROR beyond ±80% |
| DQ-I08 | Distinct item count within ±50% of the trailing 7-day median | WARN |
| DQ-I09 | `time_left` is SHORT, MEDIUM, LONG or VERY_LONG | WARN; the listing is kept |

Notes on the rules:

- **Pricing modes.** Commodity listings carry a per-unit `unit_price` and nothing else. Realm listings carry a `buyout` for the whole listing, a `bid`, or both, and never a `unit_price`. In the first live snapshots about 85% of realm listings had a buyout only, 15% both, and well under 1% a bid only.
- **A listing is counted once.** A listing missing its quantity breaks DQ-I04 and DQ-I05; it is counted under the first check that drops it.
- **Expectations come from history.** A busy realm lists ten times what a quiet one does, so DQ-I07 and DQ-I08 compare a snapshot with its own source's median over the previous seven days. They pass until a day (24 snapshots) of history exists. Quarantined snapshots count towards the median, so a lasting change, such as a new patch, stops failing within a few days instead of quarantining a whole week.
- **Retrying.** A missing or unreadable file is quarantined straight away, since retrying will not fix it. Any other error, such as the database being unreachable, leaves the snapshot waiting and it is tried again on the next run; later snapshots of the same source wait behind it so the order of DQ-I03 holds.

### What the first real loads showed

The first 27 live snapshots loaded on 7 November 2026 (8 connected realms and the commodity market, 3 hours) passed every check: 1,956,112 listings, none dropped. Nine further snapshots were quarantined under DQ-I01 because their files had been written outside the archive during an early test, which is how the missing-file handling above was found.

## Checkpoint 2: finished tables

The dbt tests on the warehouse are added together with the dbt project.
