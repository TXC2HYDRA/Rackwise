#!/usr/bin/env bash
# Frontend type-check + component/unit tests (Vitest). No backend needed.
set -euo pipefail
cd "$(dirname "$0")/../frontend"
npm ci --silent
npx tsc -b
npx vitest run
