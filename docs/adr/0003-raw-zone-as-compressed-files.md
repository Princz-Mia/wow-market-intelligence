# 0003. Raw zone as compressed JSON files

**Status:** Accepted

## Context

Every snapshot should be kept as received, so the pipeline can be replayed after a bug fix and a number can be traced back to its source. The data is written once, read rarely, and has a single consumer.

## Decision

Store each snapshot as a gzipped JSON file under `{root}/{region}/{source}/yyyy/MM/dd/HH/{id}.json.gz`, and record its SHA-256 in `ops.snapshot`. Do not add a document database.

## Consequences

- Replay reads files from the archive, independent of the database.
- There is no second database to operate, back up or secure.
- Files cannot be queried directly; analysis goes through the warehouse.
- Object storage can replace the filesystem later behind the same port.
