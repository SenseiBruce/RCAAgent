#!/usr/bin/env bash
# Runnable JUnit 5 entrypoint under tests/ (same as make test / scripts/test.sh).
set -euo pipefail
exec "$(dirname "$0")/../scripts/test.sh"
