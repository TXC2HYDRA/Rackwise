#!/usr/bin/env bash
# Integration tests (Testcontainers PostgreSQL; Docker required). Non-zero exit on failure.
set -euo pipefail
cd "$(dirname "$0")/../backend"
./mvnw -B -q test -Dtest='com.capacitydesk.integration.*IT'
