# Architecture decision records

Short records of decisions that shape the system. Each states the context, the decision and its consequences. A superseded record stays, with a link to the one that replaces it.

| # | Decision | Status |
|---|---|---|
| [0001](0001-kimball-dimensional-warehouse.md) | Kimball dimensional warehouse with a staging area and subject marts | Accepted |
| [0002](0002-elt-with-dbt.md) | ELT: Java extracts and loads, dbt transforms inside PostgreSQL | Accepted |
| [0003](0003-raw-zone-as-compressed-files.md) | Raw zone as gzipped JSON files instead of a NoSQL store | Accepted |
| [0004](0004-spring-scheduling-as-orchestrator.md) | Spring scheduling as the orchestrator | Accepted |
| [0005](0005-jdbc-and-copy-without-jpa.md) | Plain JDBC and `COPY`, no JPA | Accepted |
| [0006](0006-power-bi-import-on-marts.md) | Power BI in Import mode on `marts` only | Accepted |
| [0007](0007-retention.md) | Retention limited by the API terms | Proposed |
| [0008](0008-event-time-from-last-modified.md) | Event time is the snapshot's `Last-Modified` | Accepted |
