# Architecture

Helios is a Java 21 / Spring Boot 3 search engine for source code and docs. There are two process mains in one Maven module:

- `io.helios.api.HeliosApiApplication` — HTTP API, job workers, optional coordinator
- `io.helios.shard.HeliosShardApplication` — gRPC `IndexShard` plus `/metrics`

`HELIOS_ROLE=shard` on the fat jar starts the shard main (used by Compose as `/app/helios-shard`).

## Data flow

1. `POST /api/v1/repositories` validates a public GitHub HTTPS URL and queues a job.
2. A worker shallow-clones with `git`, discovers supported text files, parses passages, and writes PostgreSQL rows.
3. Each passage is added to the in-memory inverted index (local or replicated to shards) and to the hashing embedder / vector index.
4. Search tokenizes the query, ranks with BM25, cosine, or hybrid, hydrates rows from PostgreSQL, and highlights snippets.

## Persistence

- Flyway: `V1__init.sql`, `V2__job_progress.sql`, `V3__embeddings.sql`
- Index snapshot: JSON at `index.snapshot_path` (atomic temp + rename)
- Embeddings: `passage_embeddings.embedding BYTEA`, model id `hashing-v1/{dim}`

## Cluster

Documents are assigned with FNV-1a consistent hashing. Replication walks clockwise from the owner. Search asks one partition per primary id; if the primary is down, healthy replicas are combined (`RF=2` sums stats, `RF>2` takes max). Ready fails only when `down >= rf`.

Embeddings live on the coordinator, not on shards.

## Cache and traces

Cache keys are `helios:search:v1:` plus SHA-256 of the normalized query. The cache is invalidated after persist. Traces record `helios.search`, `cache.lookup`, `coordinator.search`, and `shard.search`.
