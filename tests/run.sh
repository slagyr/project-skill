#!/usr/bin/env bash
# Projects Skill — Test Runner
# Runs all test scripts in the tests/ directory (except itself).

set -euo pipefail

TESTS_DIR="$(cd "$(dirname "$0")" && pwd)"
PASS=0
FAIL=0
ERRORS=()

for test_file in "$TESTS_DIR"/test_*.sh; do
  [ -f "$test_file" ] || continue
  test_name="$(basename "$test_file")"
  if bash "$test_file"; then
    ((PASS++))
  else
    ((FAIL++))
    ERRORS+=("$test_name")
  fi
done

echo ""
echo "==============================="
echo "Results: $PASS passed, $FAIL failed"
if [ ${#ERRORS[@]} -gt 0 ]; then
  echo "Failed:"
  for e in "${ERRORS[@]}"; do echo "  - $e"; done
  exit 1
else
  echo "All tests passed ✓"
  exit 0
fi
