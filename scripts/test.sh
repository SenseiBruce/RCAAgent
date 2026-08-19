#!/usr/bin/env bash
# Explicit test entrypoint for a fresh clone. Equivalent to: make test
set -euo pipefail
cd "$(dirname "$0")/.."
exec ./mvnw -B test
