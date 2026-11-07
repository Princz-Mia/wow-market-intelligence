# wow-market-intelligence

Market intelligence for the World of Warcraft auction house. Hourly auction snapshots from the Blizzard Game Data API are archived, validated and loaded into a PostgreSQL star schema, where price, liquidity and volatility KPIs, price anomalies and cross-realm trading opportunities are computed for Power BI dashboards.

**Status:** early development. The collector captures the EU commodity market and eight connected realms twice an hour into the raw archive. Parsing, validation and the warehouse are not built yet.

## Business questions

1. **Market state:** what does an item cost now, how much is listed, and how did price and supply evolve?
2. **Liquidity:** how fast does an item sell, and how many days of supply are listed?
3. **Risk:** how volatile is an item's price, and which price moves were abnormal?
4. **Seasonality:** do prices follow weekly or time-of-day patterns?
5. **Cross-realm opportunities:** which items are consistently cheaper on one realm than another after fees, and which should a trader act on first?
6. **Trust:** is the data fresh and complete?

## Course alignment

The project is the large homework for the *Business Intelligence* course at BME VIK. The design maps onto the course topics:

| Course topic | Where it appears |
|---|---|
| BI, decision support, KPIs | The business questions above, the KPI catalogue, and the opportunity ranking |
| BI architecture, Kimball vs Inmon | Layered Kimball warehouse with a staging area and subject marts ([ADR 0001](docs/adr/0001-kimball-dimensional-warehouse.md)) |
| Staging, raw data, incremental load, ETL metadata | Gzipped raw zone, `raw` staging tables, incremental dbt models, run metadata in `ops` |
| ETL vs ELT | Java extracts and loads, dbt transforms inside PostgreSQL ([ADR 0002](docs/adr/0002-elt-with-dbt.md)) |
| Dimensional modelling, grain, facts, SCD | Periodic snapshot, accumulating snapshot and aggregate facts; SCD2 market dimension |
| ETL testing | Unit, integration (WireMock, Testcontainers), end-to-end and performance tests |
| Data quality | Ingestion batch checks before staging, dbt tests on finished tables |
| Python data stack | dbt-core managed with uv, `dbt docs` lineage |
| Event processing | Event time vs processing time ([ADR 0008](docs/adr/0008-event-time-from-last-modified.md)), idempotent loads, robust z-score anomaly flags |
| NoSQL, data lake | Compressed JSON raw zone instead of a NoSQL store ([ADR 0003](docs/adr/0003-raw-zone-as-compressed-files.md)) |
| Docker | The whole stack runs with Docker Compose |
| Power BI | Star schema model, DAX measures, time intelligence, what-if parameters ([ADR 0006](docs/adr/0006-power-bi-import-on-marts.md)) |
| Visualisation and storytelling | One question per visual, a bookmark-driven story, case studies |

## Run it

You need Docker with Compose v2, and JDK 25 to build outside Docker. You also need a Battle.net API client: create one at <https://develop.battle.net/access/clients> (it only needs the client credentials flow, so no redirect URL or service URL). Copy `.env.example` to `.env`, fill in the client id and secret, and set the passwords. Then start PostgreSQL and the collector:

```sh
docker compose up -d --build --wait
```

The collector captures once at startup and then at :10 and :40 every hour (UTC). Each snapshot is kept as a gzipped file in the `archive-data` volume and registered in `ops.snapshot`; each run is recorded in `ops.pipeline_run`. To run the stack without collecting, set `COLLECTOR_CAPTURE_ENABLED=false`. Running it on an always-on host, checking it, and copying its data are described in the [runbook](docs/runbook.md).

PostgreSQL listens on `localhost:15432`. The `wow_market` database has one schema per layer: `raw` and `ops` belong to the `collector` role, `staging`, `core` and `marts` to the `transformer` role, and the `bi_reader` role can read only `marts`.

Build and test:

```sh
./mvnw verify        # unit tests, format check, and *IT tests against PostgreSQL in Testcontainers (needs Docker)
./mvnw test          # Docker-free unit tests only
./mvnw spotless:apply
```

## Contributing

After cloning, enable the commit message check:

```sh
git config core.hooksPath .githooks
```

Commits follow [Conventional Commits](https://www.conventionalcommits.org/). Work happens on short-lived branches named `<type>/<topic>` and reaches `main` through pull requests.

## Data source

Auction and item data come from the [Blizzard Battle.net Game Data API](https://develop.battle.net/). World of Warcraft and Blizzard Entertainment are trademarks of Blizzard Entertainment, Inc. This project is not affiliated with or endorsed by Blizzard.
