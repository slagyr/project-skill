#!/usr/bin/env bash
# Projects Skill — Test Runner
# Delegates to Babashka speclj specs.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# Ensure bb is available
if ! command -v bb >/dev/null 2>&1; then
  if [ -x "$HOME/bin/bb" ]; then
    export PATH="$HOME/bin:$PATH"
  else
    echo "ERROR: Babashka (bb) not found. Install: https://github.com/babashka/babashka"
    exit 1
  fi
fi

cd "$REPO_ROOT"
exec bb run test
