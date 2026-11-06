# 0008. Event time is the snapshot's Last-Modified

**Status:** Accepted

## Context

A snapshot describes the auction house at the moment Blizzard generated it, which can be well before we fetch it. Fetches can be delayed, retried or replayed.

## Decision

The `Last-Modified` response header is the snapshot's event time and, together with the source, its identity. The fetch time is kept as processing time. All timestamps are stored as `timestamptz` in UTC. Local (Europe/Budapest) dates and hours are attributes of `dim_date` and `dim_hour`.

## Consequences

- Loading the same snapshot twice is detected and skipped, so runs are idempotent.
- Time series line up by when the market was observed, not by when we happened to fetch it.
- Gaps between consecutive event times show missed snapshots, which the coverage checks report.
