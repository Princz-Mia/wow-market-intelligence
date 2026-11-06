# wow-market-intelligence

Market intelligence for the World of Warcraft auction house. Hourly auction snapshots from the Blizzard Game Data API are archived, validated and loaded into a PostgreSQL star schema, where price, liquidity and volatility KPIs, price anomalies and cross-realm trading opportunities are computed for Power BI dashboards.

**Status:** early development. Nothing runs yet.

## Business questions

1. **Market state:** what does an item cost now, how much is listed, and how did price and supply evolve?
2. **Liquidity:** how fast does an item sell, and how many days of supply are listed?
3. **Risk:** how volatile is an item's price, and which price moves were abnormal?
4. **Seasonality:** do prices follow weekly or time-of-day patterns?
5. **Cross-realm opportunities:** which items are consistently cheaper on one realm than another after fees, and which should a trader act on first?
6. **Trust:** is the data fresh and complete?

## Run it

You need Docker with Compose v2 and JDK 25. Copy `.env.example` to `.env` and set the passwords, then start the warehouse:

```sh
docker compose up -d --wait
```

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
