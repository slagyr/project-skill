#!/usr/bin/env bash
# Tests for references/project-creation.md
# Validates the reference doc exists and contains required guidance sections.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REF="$SCRIPT_DIR/../projects/references/project-creation.md"

PASS=0
FAIL=0

pass() { ((PASS++)); echo "  ✓ $1"; }
fail() { ((FAIL++)); echo "  ✗ $1"; }
check() {
  local desc="$1"; shift
  if "$@" >/dev/null 2>&1; then pass "$desc"; else fail "$desc"; fi
}

# ─── Test: File exists ───────────────────────────────────────────────

echo "▸ Reference doc exists"
check "project-creation.md exists" test -f "$REF"

# ─── Test: Required sections ─────────────────────────────────────────

echo "▸ Required sections"
check "Has Gather Information step" grep -q "Gather Information" "$REF"
check "Has Suggest Defaults step" grep -q "Suggest Defaults" "$REF"
check "Has Discord Channel step" grep -q "Discord Channel" "$REF"
check "Has Scaffold step" grep -q "Scaffold" "$REF"
check "Has PROJECT.md generation step" grep -q "Generate PROJECT.md" "$REF"
check "Has AGENTS.md setup step" grep -q "AGENTS.md" "$REF"
check "Has Iteration 001 step" grep -q "Iteration 001" "$REF"
check "Has Seed Stories step" grep -q "Seed Stories" "$REF"
check "Has Registry step" grep -q "Registry" "$REF"
check "Has Review step" grep -q "Review with the Human" "$REF"

# ─── Test: Key content ───────────────────────────────────────────────

echo "▸ Key content"
check "References git init" grep -q "git init" "$REF"
check "References bd init" grep -q "bd init" "$REF"
check "References agents-template.md" grep -q "agents-template" "$REF"
check "Has slug validation" grep -q "slug" "$REF"
check "No TODO placeholders in generated content guidance" grep -q "not a placeholder" "$REF"
check "References PROJECT.md format" grep -q "Status:" "$REF"
check "References Notifications table" grep -q "Notifications" "$REF"

# ─── Test: Old script removed ────────────────────────────────────────

echo "▸ Old script removed"
OLD_SCRIPT="$SCRIPT_DIR/../projects/bin/projects-init"
if [ -f "$OLD_SCRIPT" ]; then
  fail "projects-init script should be removed"
else
  pass "projects-init script removed"
fi

OLD_TEST="$SCRIPT_DIR/test_projects_init.sh"
if [ -f "$OLD_TEST" ]; then
  fail "test_projects_init.sh should be removed"
else
  pass "test_projects_init.sh removed"
fi

# ─── Test: SKILL.md references new doc ───────────────────────────────

echo "▸ SKILL.md updated"
SKILL="$SCRIPT_DIR/../projects/SKILL.md"
check "SKILL.md references project-creation.md" grep -q "project-creation.md" "$SKILL"
if grep -q "projects-init" "$SKILL"; then
  fail "SKILL.md should not reference projects-init script"
else
  pass "SKILL.md no longer references projects-init"
fi

# ─── Test: init.md references new doc ────────────────────────────────

echo "▸ init.md updated"
INIT="$SCRIPT_DIR/../projects/references/init.md"
check "init.md references project-creation.md" grep -q "project-creation.md" "$INIT"
if grep -q "projects-init" "$INIT"; then
  fail "init.md should not reference projects-init script"
else
  pass "init.md no longer references projects-init"
fi

# ─── Summary ─────────────────────────────────────────────────────────

echo ""
echo "==============================="
echo "project-creation-reference Tests: $PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ] && exit 0 || exit 1
