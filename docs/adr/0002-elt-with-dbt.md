# 0002. ELT with dbt inside PostgreSQL

**Status:** Accepted

## Context

Auction snapshots must be fetched, archived and loaded hourly, then turned into dimensions, facts and KPIs. The transformations are set-based: percentiles, window functions, joins between consecutive snapshots.

## Decision

The Java collector does extract and load: it streams snapshots into the `raw` schema with `COPY`. All transformation happens inside PostgreSQL in dbt models (`staging`, `core`, `marts`), which the collector runs after a successful load. Python and dbt are managed with uv.

## Consequences

- Transformations are declarative SQL, tested with dbt tests and unit tests, and documented with a lineage graph.
- Raw data stays in the database, so a model can be rebuilt without fetching again.
- The collector image needs Python and dbt alongside the JRE.
- Business logic lives in one place, so a future web client can read the same marts.
