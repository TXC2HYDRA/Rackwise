#!/usr/bin/env bash
# Stop the stack. Data survives in the pgdata volume; pass --wipe to delete it too.
set -euo pipefail
cd "$(dirname "$0")/.."
if [ "${1:-}" = "--wipe" ]; then docker compose down -v; else docker compose down; fi
