-- dbt reads everything the collector loads and records, including tables created by later migrations.
-- It can never write to raw or ops.
GRANT USAGE ON SCHEMA raw, ops TO transformer;
ALTER DEFAULT PRIVILEGES IN SCHEMA raw, ops GRANT SELECT ON TABLES TO transformer;
