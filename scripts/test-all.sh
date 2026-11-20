#!/usr/bin/env bash
# Every backend test (unit + integration, Docker required) plus frontend type-check and Vitest.
set -euo pipefail
cd "$(dirname "$0")/../backend"
./mvnw -B test

cd ../frontend
npm ci --silent
npx tsc -b
npx vitest run
