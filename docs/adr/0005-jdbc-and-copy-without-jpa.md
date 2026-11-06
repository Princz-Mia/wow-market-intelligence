# 0005. Plain JDBC and COPY, no JPA

**Status:** Accepted

## Context

The collector's database work is bulk inserts of hundreds of thousands of listings per run, plus a few metadata tables. There is no object graph to manage.

## Decision

Use Spring `JdbcClient` for metadata and PostgreSQL `COPY` for listings. Schema changes are Flyway migrations.

## Consequences

- Loads stay fast and use flat memory, because rows stream from the parser into `COPY`.
- SQL is explicit and reviewable, with no ORM mapping to learn or debug.
- Mapping rows to records is written by hand.
