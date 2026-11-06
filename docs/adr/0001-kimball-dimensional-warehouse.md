# 0001. Kimball dimensional warehouse

**Status:** Accepted

## Context

The consumers are BI dashboards that slice prices, supply and opportunities by item, realm and time. The questions are known in advance and stable. There is one source system and no enterprise-wide model to integrate into.

## Decision

Model the warehouse bottom-up in the Kimball style:

- a staging layer that types and cleans the raw rows;
- a core layer of conformed dimensions (`dim_date`, `dim_hour`, `dim_market`, `dim_item_variant`) and facts of different types (periodic snapshot, accumulating snapshot, aggregate);
- subject-area marts that the dashboards read.

## Consequences

- Star schemas are fast to query and easy to understand in Power BI.
- Each fact table has a one-sentence grain, which drives tests and reconciliation.
- The model is shaped by the questions, so new questions may need new facts. An Inmon-style normalised layer would add work without a second source to integrate.
