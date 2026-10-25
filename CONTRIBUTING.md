# Contributing

## Build

Requires JDK 21 and Maven.

```bash
mvn -q test
HELIOS_REQUIRE_DB=1 mvn -q test
```

`HELIOS_REQUIRE_DB=1` tests talk to the PostgreSQL URL in `config.yaml` or `HELIOS_DATABASE_URL`.

## Run

```bash
mvn spring-boot:run
# or
make run
```

Shard process:

```bash
HELIOS_ROLE=shard HELIOS_SHARD_ID=shard-0 mvn -Dstart-class=io.helios.shard.HeliosShardApplication spring-boot:run
```

The shard main is `HeliosShardApplication` and does not need the Web stack; Compose uses `HELIOS_ROLE=shard` on the same jar.

## Style

- Keep the HTTP JSON field names and IR formulas unchanged.
- Do not invent latency or quality claims.
- Prefer explicit JDBC SQL over JPA.
- Never execute cloned repository code.

## Tests

Add unit tests next to the package they cover (`tokenizer`, `bm25`, `index`, `cache`, `hybrid`, `hashring`, coordinator failover). Database tests must skip unless `HELIOS_REQUIRE_DB=1`.
