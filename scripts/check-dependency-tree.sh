#!/usr/bin/env bash
# Fail CI when the resolved Maven graph drifts from the committed lock snapshot.
set -euo pipefail
cd "$(dirname "$0")/.."
./mvnw -B dependency:tree -DoutputFile=target/dependency-tree.txt
diff -u dependency-tree.txt target/dependency-tree.txt
