#!/usr/bin/env bash
# Assert scripts/check-dependency-tree.sh is green on the committed lock, then fails on drift.
set -euo pipefail
cd "$(dirname "$0")/.."
bash scripts/check-dependency-tree.sh
cp dependency-tree.lock /tmp/rca-dependency-tree.lock.bak
restore() { mv /tmp/rca-dependency-tree.lock.bak dependency-tree.lock; }
trap restore EXIT
echo "# intentional-drift" >> dependency-tree.lock
if bash scripts/check-dependency-tree.sh; then
  echo "expected check-dependency-tree.sh to fail after lock drift" >&2
  exit 1
fi
echo "check-dependency-tree.sh failed on drift as expected"
