# Runbook

How to run, check and maintain the collector on an always-on Linux host (here an Ubuntu 24.04 arm64 VM), and how to get its data onto a workstation.

## First deployment

On Ubuntu, install Docker with the Compose and Buildx plugins (the image build needs BuildKit), then log in again so the group change applies:

```bash
sudo apt update && sudo apt install -y docker.io docker-compose-v2 docker-buildx git
sudo usermod -aG docker "$USER"
```

Then clone the repository and fill in `.env`:

```bash
git clone https://github.com/Princz-Mia/wow-market-intelligence.git
cd wow-market-intelligence
cp .env.example .env
nano .env        # Battle.net client id and secret, and a long random password for each database role
chmod 600 .env
```

`openssl rand -hex 24` prints a suitable password. Start the stack:

```bash
docker compose up -d --build --wait
```

The image builds on the host itself, so it matches the host's CPU architecture. The first build downloads Maven dependencies and takes a few minutes.

The collector captures once on startup, then at :10 and :40 every hour (UTC).

## Is it collecting?

```bash
docker compose ps                                     # both services "healthy"
docker compose logs --since 2h collector | grep "capture run="
```

Each run logs one line such as `capture run=12 trigger=SCHEDULE status=SUCCEEDED new=9 unchanged=0 failed=0`. About once an hour a run reports `new=9`, and the run in between reports `unchanged=9`.

The run log and snapshot registry in the database:

```bash
docker compose exec postgres psql -U postgres -d wow_market -c \
  "SELECT id, status, snapshots_new, sources_failed, started_at FROM ops.pipeline_run ORDER BY id DESC LIMIT 10"
docker compose exec postgres psql -U postgres -d wow_market -c \
  "SELECT source, max(last_modified) AS newest, count(*) FROM ops.snapshot GROUP BY source ORDER BY source"
```

`error_summary` on a `PARTIAL` or `FAILED` run names the failing sources.

Disk usage (the archive grows by about 250 MB a day):

```bash
docker system df -v | grep -E "archive-data|postgres-data"
df -h /
```

## Update to a new version

```bash
git pull
docker compose up -d --build --wait
```

Flyway applies new migrations on startup. Snapshots published while the collector is down for the update are fetched on the next run, as long as Blizzard still serves them (it serves only the current one).

## Stop and start

```bash
docker compose stop          # data stays in the volumes
docker compose start
```

`docker compose down -v` deletes the volumes, and with them every collected snapshot. Do not use `-v` on the host.

## Copy the data to a workstation

Archive files (from the workstation, with the host's SSH key):

```bash
ssh -i ~/.ssh/wow-oracle ubuntu@<host> \
  "docker run --rm -v wow-market-intelligence_archive-data:/a alpine tar czf - -C /a ." > archive.tar.gz
```

Database dump:

```bash
ssh -i ~/.ssh/wow-oracle ubuntu@<host> \
  "cd wow-market-intelligence && docker compose exec -T postgres pg_dump -U postgres -Fc wow_market" > wow_market.dump
```

## Reach the database from a workstation

PostgreSQL listens only on the host's localhost. Open an SSH tunnel and connect to `localhost:15433`:

```bash
ssh -i ~/.ssh/wow-oracle -N -L 15433:localhost:15432 ubuntu@<host>
```

## Troubleshooting

| Symptom | Likely cause | What to do |
|---|---|---|
| Collector restarts at startup with a configuration error | A missing or invalid value in `.env` | `docker compose logs collector`, fix `.env`, `docker compose up -d` |
| Every source fails with `token request answered HTTP 401` | Wrong client id or secret, or the secret was regenerated | Update `.env`, then `docker compose up -d` |
| Runs report `unchanged` for hours | Blizzard has not published a new snapshot | Compare `newest` per source with the time; usually resolves itself |
| A single realm fails repeatedly | The realm is down or its auction data is unavailable | Visible in `error_summary`; other sources are unaffected |
