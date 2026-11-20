#!/usr/bin/env bash
# Every backend test (unit + integration). Docker required.
set -euo pipefail
cd "$(dirname "$0")/../backend"
./mvnw -B test
