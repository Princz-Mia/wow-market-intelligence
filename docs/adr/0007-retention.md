# 0007. Retention limited by the API terms

**Status:** Accepted

## Context

The Blizzard Developer API Terms of Use ask that data pulled from the APIs is kept for no longer than 30 days. Raw listings are also by far the largest data.

## Decision

Retention is configurable, with these defaults:

| Data | Kept for |
|---|---|
| Raw archive files | 30 full days besides today |
| `raw.auction_listing` partitions | 3 full days besides today (the archive can replay older data) |
| Hourly facts and listing lifecycle | 30 days |
| Daily aggregates and opportunity history | Until the end of the project, as derived statistics |

A housekeeping step at the end of each run drops expired partitions and deletes expired files. Days are UTC days. Staging was first planned at 7 days, but measured volume is about 1.9 GB of staged listings a day, so it was cut to 3, which still leaves at least 72 hours of consecutive snapshots for the listing lifecycle. The archive must be kept at least as long as staging, so every staged day can be replayed.

## Consequences

- History older than 30 days exists only as daily aggregates.
- Whether derived statistics may be kept longer than 30 days is a reading of the terms. The README states it.
