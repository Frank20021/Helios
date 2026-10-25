# Changelog

## Unreleased

### Changed

- Replaced the Go implementation with Java 21 + Spring Boot 3.4 + Maven. Public HTTP routes, `config.yaml` / `HELIOS_*` keys, PostgreSQL schema, gRPC contract, BM25/hybrid formulas, Compose topology, and static UI stay the same.
- Index snapshots are JSON (`data/index.json`) instead of Go gob.
- Schema migrations are Flyway `V1`–`V3` (same SQL, idempotent so an existing Helios database can start).
- CI uses `actions/setup-java` and `mvn test`.
- Docker image is `eclipse-temurin:21`. The shard entrypoint is `/app/helios-shard` (`HELIOS_ROLE=shard`).

### Removed

- Go sources (`cmd/`, `internal/`, `go.mod`, Go Makefile targets, Go Dockerfile).
