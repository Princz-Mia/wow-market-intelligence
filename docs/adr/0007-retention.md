# 0007. Retention limited by the API terms

**Status:** Proposed

## Context

The Blizzard Developer API Terms of Use ask that data pulled from the APIs is kept for no longer than 30 days. Raw listings are also by far the largest data.

## Decision

Retention is configurable, with these defaults:

| Data | Kept for |
|---|---|
| Raw archive files | 30 days |
| `raw.auction_listing` partitions | 7 days (the archive can replay older data) |
| Hourly facts and listing lifecycle | 30 days |
| Daily aggregates and opportunity history | Until the end of the project, as derived statistics |

A housekeeping step in each run drops expired partitions and deletes expired files.

## Consequences

- History older than 30 days exists only as daily aggregates.
- Whether derived statistics may be kept longer than 30 days is a reading of the terms. The README states it.
