# 0006. Power BI in Import mode on marts only

**Status:** Accepted

## Context

Power BI is the presentation layer. The data changes hourly, and dashboards need fast interaction and time intelligence.

## Decision

Power BI imports only the `marts` schema, as the `bi_reader` role, and builds a star schema from the dimensions and facts exposed there. KPI logic lives in dbt. DAX holds only aggregations, time intelligence and what-if parameters.

## Consequences

- Report interaction is fast, and a refresh is cheap enough to run after each pipeline run.
- Each KPI has one definition, which other clients can reuse.
- Mart names and column meanings become an interface, documented in dbt.
