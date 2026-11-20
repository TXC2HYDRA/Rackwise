#!/usr/bin/env bash
# Authentication, authorization and privilege-escalation tests.
set -euo pipefail
cd "$(dirname "$0")/../backend"
./mvnw -B -q test -Dtest='AuthSecurityIT'
