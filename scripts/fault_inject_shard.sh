#!/bin/sh
# Stop one Compose shard so the coordinator must serve from a replica.
# Usage: scripts/fault_inject_shard.sh [shard-0]
set -eu
cd "$(dirname "$0")/.."
target="${1:-shard-0}"
docker compose stop "$target"
echo "stopped $target; search should continue on remaining replicas"
