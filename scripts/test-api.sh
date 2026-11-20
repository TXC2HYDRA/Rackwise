#!/usr/bin/env bash
# API contract/workflow tests (status codes, validation, state transitions, pagination).
set -euo pipefail
cd "$(dirname "$0")/../backend"
./mvnw -B -q test -Dtest='WorkflowIT,InventoryIT,AuditIT'
