#!/usr/bin/env bash
# Named tests/test_* so file-glob scanners detect a suite. Runs the JUnit 5 Maven tests.
set -euo pipefail
exec "$(dirname "$0")/run.sh"
