#!/usr/bin/env bash
# Unit tests only (no Docker, no database). Exits non-zero on failure.
set -euo pipefail
cd "$(dirname "$0")/../backend"
./mvnw -B -q test -Dtest='com.capacitydesk.unit.*Test'
