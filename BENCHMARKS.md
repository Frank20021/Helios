# Benchmarks

The previous Go process numbers do not apply to this Java rewrite. Run a fresh measurement before quoting latency or quality:

```bash
make eval
make bench
```

`make eval` indexes `testdata/repos/sample` and scores `testdata/eval/queries.json` for lexical, semantic, and hybrid modes. `make bench` repeats a local search and prints cache counters plus process latencies for that run only.

Do not copy older Go P50/P99 or eval figures into this file.
