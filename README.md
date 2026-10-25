# Helios

Helios is a search engine for source code and technical documentation. It is a **Java 21 + Spring Boot 3** port of the original Helios HTTP API, PostgreSQL schema, gRPC shard contract, BM25/hybrid ranking, Compose topology, and static UI.

Single-node mode remains the default for `make run` / `mvn spring-boot:run`.

## Problem

Keyword search over a cloned repository should return ranked files, functions, and passages without sending source code through an external search product. Lexical, semantic, and hybrid ranking are available on the same search endpoint.

## Current implementation

- Maven / Spring Boot 3.4 / Java 21
- PostgreSQL schema via Flyway (`V1`–`V3`, same SQL as the original migrations)
- Tokenizer, inverted index, and BM25 ranking
- GitHub URL validation, shallow clone, file filters, and passage parsing
- Background indexing jobs and REST APIs for repositories, jobs, and search
- Incremental reindex by commit hash and per-file content hash
- JSON snapshot of the inverted index, restored or rebuilt on startup
- Consistent hashing of documents onto index shards with configurable replication
- gRPC shard processes, coordinator fan-out, global BM25 merge, and replica failover
- Local hashing embeddings, lexical / semantic / hybrid search, and a labeled ranking eval
- Query-response cache (memory by default, Redis in Compose)
- Prometheus `/metrics`, OTLP JSON traces, and a Grafana dashboard
- Static demonstration UI at `/`

Not implemented: Kubernetes deploy.

## Technology choices

- Java 21, Spring Boot Web + JDBC (no JPA), Flyway
- PostgreSQL 16 for repository and document metadata
- YAML configuration (`config.yaml`) with `HELIOS_*` environment overrides
- `git` CLI via `ProcessBuilder` (never executes repository code)
- JSON snapshot file for the inverted index (`index.snapshot_path`)
- Local hashing embeddings stored in PostgreSQL (`passage_embeddings`)
- Optional Redis query cache (`cache.backend=redis`)
- Custom Prometheus text metrics and OTLP JSON trace export
- gRPC (`proto/helios.proto`, `java_package = io.helios.grpc`)

PostgreSQL remains the source of truth for passages. A restart loads the snapshot when it matches stored passage ids, otherwise the index is rebuilt from `raw_content`.

## Architecture

```
Browser --/--> HeliosApiApplication (coordinator + UI)
Client --REST--> HeliosApiApplication
                   ├── job workers (clone, parse, incremental index)
                   ├── query cache (memory or Redis)
                   ├── gRPC --> shard-0 / shard-1 / shard-2
                   └── PostgreSQL

mvn spring-boot:run with cluster.enabled=false keeps a single in-process index.
```

## Local setup

Requires JDK 21, Maven, `git`, and PostgreSQL 16.

```bash
# Option A: local PostgreSQL matching config.yaml
make test
make run

# Option B: Docker Compose (API + 3 shards + PostgreSQL + Redis + Prometheus + Grafana)
docker compose up --build
```

`make run` listens on `0.0.0.0:8080` unless overridden.

```bash
curl http://127.0.0.1:8080/health/live
curl "http://127.0.0.1:8080/api/v1/search?q=authentication+retry"
```

Open `/` in a browser and search `authentication retry` after indexing a repository.

Database integration tests run only when `HELIOS_REQUIRE_DB=1`:

```bash
HELIOS_REQUIRE_DB=1 mvn -q test
```

## Configuration

`config.yaml` is the default file. `HELIOS_CONFIG` or `-config path` overrides it. Environment variables use the `HELIOS_` prefix (`HELIOS_SERVER_PORT`, `HELIOS_DATABASE_URL`, `HELIOS_CLUSTER_ENABLED`, …).

## IR lock

- Tokenizer: camel / Pascal / snake / kebab plus the joined identifier; expanded terms share the surface-token position
- BM25: `IDF = ln((N-df+0.5)/(df+0.5)+1)`, `k1=1.2`, `b=0.75`, unique query terms
- Hybrid: `alpha * norm(BM25) + (1-alpha) * norm(cosine)`

## Security

- HTTPS GitHub URLs only, host allowlist, no credentials in the URL
- Clone timeouts, path-traversal filters, no execution of cloned code
- `GIT_TERMINAL_PROMPT=0`

## License

See repository files. This is an educational / production-style project, not a published product with claimed latency or quality numbers.
