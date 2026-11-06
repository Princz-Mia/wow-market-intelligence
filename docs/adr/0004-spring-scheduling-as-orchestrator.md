# 0004. Spring scheduling as the orchestrator

**Status:** Accepted

## Context

The pipeline has two steps, collect and transform, on a fixed hourly schedule. The source changes about once an hour, so batch processing matches it. A streaming platform would add infrastructure without making the data any fresher.

## Decision

Trigger runs with Spring `@Scheduled` (cron from configuration) inside the collector. Each run is recorded in `ops.pipeline_run` with per-step durations and a status. A manual trigger and a replay endpoint cover reruns.

## Consequences

- There is one deployable process and nothing extra to run on the always-on host.
- There is no built-in backfill UI, per-task retry or DAG view. Dagster is the natural next step if the pipeline grows.
