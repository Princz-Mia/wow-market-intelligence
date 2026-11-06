#!/bin/bash
# Runs once, on an empty data directory. One warehouse database with a schema per layer:
# the collector owns raw and ops (loaded by Java, migrated by Flyway), the transformer owns
# staging, core and marts (built by dbt), and bi_reader can only read marts.
set -euo pipefail

: "${COLLECTOR_DB_PASSWORD:?must be set}"
: "${TRANSFORMER_DB_PASSWORD:?must be set}"
: "${BI_READER_DB_PASSWORD:?must be set}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
  -v collector_password="$COLLECTOR_DB_PASSWORD" \
  -v transformer_password="$TRANSFORMER_DB_PASSWORD" \
  -v bi_reader_password="$BI_READER_DB_PASSWORD" <<'SQL'
CREATE ROLE collector LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD :'collector_password';
CREATE ROLE transformer LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD :'transformer_password';
CREATE ROLE bi_reader LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD :'bi_reader_password';

CREATE DATABASE wow_market;

REVOKE ALL ON DATABASE wow_market FROM PUBLIC;
REVOKE CONNECT ON DATABASE postgres FROM PUBLIC;
REVOKE CONNECT ON DATABASE template1 FROM PUBLIC;
GRANT CONNECT ON DATABASE wow_market TO collector, transformer, bi_reader;

\connect wow_market
REVOKE ALL ON SCHEMA public FROM PUBLIC;

CREATE SCHEMA raw AUTHORIZATION collector;
CREATE SCHEMA ops AUTHORIZATION collector;
CREATE SCHEMA staging AUTHORIZATION transformer;
CREATE SCHEMA core AUTHORIZATION transformer;
CREATE SCHEMA marts AUTHORIZATION transformer;

-- Power BI reads marts only, including every view dbt creates there later.
GRANT USAGE ON SCHEMA marts TO bi_reader;
ALTER DEFAULT PRIVILEGES FOR ROLE transformer IN SCHEMA marts GRANT SELECT ON TABLES TO bi_reader;
SQL
