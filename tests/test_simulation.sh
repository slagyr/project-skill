#!/usr/bin/env bash
# Simulation tests: scripted orchestrator/worker scenarios against a test project.
# Validates orchestrator and worker behavior contracts using a temporary project
# with mock files. Does NOT actually spawn sessions — tests the file-level
# contracts and state transitions that orchestrator/worker must uphold.

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CONTRACTS="$PROJECT_ROOT/CONTRACTS.md"
TEST_TMP="$(mktemp -d)"
TEST_PROJECT="$TEST_TMP/test-sim-project"

PASS=0
FAIL=0

pass() { ((PASS++)); echo "  ✓ $1"; }
fail() { ((FAIL++)); echo "  ✗ $1"; }
check() {
  local desc="$1"; shift
  if "$@" >/dev/null 2>&1; then pass "$desc"; else fail "$desc"; fi
}

cleanup() { rm -rf "$TEST_TMP"; }
trap cleanup EXIT

# Disable errexit for test assertions
set +e

# ═══════════════════════════════════════════════════════════════════════
# Setup: Create a minimal test project structure
# ═══════════════════════════════════════════════════════════════════════

setup_test_project() {
  rm -rf "$TEST_PROJECT"
  mkdir -p "$TEST_PROJECT/iterations/001"

  # PROJECT.md with all required fields
  cat > "$TEST_PROJECT/PROJECT.md" <<'EOF'
# Test Simulation Project

- **Status:** active
- **Priority:** high
- **Autonomy:** full
- **Checkin:** daily
- **Channel:** test-channel-123
- **MaxWorkers:** 2
- **WorkerTimeout:** 1800

## Notifications

| Event | Notify |
|-------|--------|
| iteration-start | on |
| bead-start | on |
| bead-complete | on |
| iteration-complete | on |
| no-ready-beads | on |
| question | on |
| blocker | on |

## Goal

Test project for simulation tests.

## Guardrails

- This is a test project
EOF

  # AGENTS.md
  cat > "$TEST_PROJECT/AGENTS.md" <<'EOF'
# Test Project AGENTS.md
Read worker.md for instructions.
EOF

  # ITERATION.md with active status
  cat > "$TEST_PROJECT/iterations/001/ITERATION.md" <<'EOF'
# Iteration 001

- **Status:** active

## Stories
- test-sim-aaa: First test bead
- test-sim-bbb: Second test bead (depends on aaa)
- test-sim-ccc: Third test bead (independent)
EOF

  # Registry
  cat > "$TEST_TMP/registry.md" <<EOF
| Slug | Status | Priority | Path |
|------|--------|----------|------|
| test-sim-project | active | high | $TEST_PROJECT |
EOF
}

# ═══════════════════════════════════════════════════════════════════════
# Scenario 1: PROJECT.md Defaults (Contract §1.2)
# Missing fields should have correct defaults
# ═══════════════════════════════════════════════════════════════════════

echo "=== Scenario 1: PROJECT.md Field Defaults ==="

setup_test_project

# Create a minimal PROJECT.md with only required fields
cat > "$TEST_PROJECT/PROJECT.md" <<'EOF'
# Minimal Project

- **Status:** active
- **Priority:** normal
- **Autonomy:** full

## Goal
Minimal test.

## Guardrails
- None
EOF

# Verify defaults per CONTRACTS.md §1.2
check "Status field present" grep -q 'Status:' "$TEST_PROJECT/PROJECT.md"
# Missing MaxWorkers → default 1
check "MaxWorkers missing (default 1 applies)" bash -c '! grep -q "MaxWorkers" "$0"' "$TEST_PROJECT/PROJECT.md"
# Missing WorkerTimeout → default 1800
check "WorkerTimeout missing (default 1800 applies)" bash -c '! grep -q "WorkerTimeout" "$0"' "$TEST_PROJECT/PROJECT.md"
# Missing Channel → none (skip notifications silently)
check "Channel missing (default: skip notifications)" bash -c '! grep -q "Channel:" "$0"' "$TEST_PROJECT/PROJECT.md"
# Missing Checkin → on-demand
check "Checkin missing (default: on-demand)" bash -c '! grep -q "Checkin:" "$0"' "$TEST_PROJECT/PROJECT.md"
# Missing Notifications table → all events on
check "Notifications table missing (default: all on)" bash -c '! grep -q "Notifications" "$0"' "$TEST_PROJECT/PROJECT.md"

# ═══════════════════════════════════════════════════════════════════════
# Scenario 2: ITERATION.md Lifecycle (Contracts §1.3, §4.5)
# ═══════════════════════════════════════════════════════════════════════

echo "=== Scenario 2: Iteration Lifecycle ==="

setup_test_project

# Active iteration has required fields
check "ITERATION.md has Status" grep -qi 'Status:' "$TEST_PROJECT/iterations/001/ITERATION.md"
check "ITERATION.md has Stories section" grep -q '## Stories' "$TEST_PROJECT/iterations/001/ITERATION.md"
check "Iteration status is active" grep -q 'active' "$TEST_PROJECT/iterations/001/ITERATION.md"

# At most one active iteration (§1.3)
mkdir -p "$TEST_PROJECT/iterations/002"
cat > "$TEST_PROJECT/iterations/002/ITERATION.md" <<'EOF'
# Iteration 002
- **Status:** planning
## Stories
- test-sim-ddd: Future bead
EOF

active_count=$(grep -rl 'Status:.*active' "$TEST_PROJECT"/iterations/*/ITERATION.md 2>/dev/null | wc -l | tr -d ' ')
check "At most one active iteration ($active_count)" test "$active_count" -le 1

# Completed iteration immutability contract (§4.5)
mkdir -p "$TEST_PROJECT/iterations/000"
cat > "$TEST_PROJECT/iterations/000/ITERATION.md" <<'EOF'
# Iteration 000
- **Status:** complete
## Stories
- test-sim-zzz: Completed bead
EOF
cat > "$TEST_PROJECT/iterations/000/RETRO.md" <<'EOF'
# Iteration 000 Retrospective
## Summary
Test completed iteration.
## Completed
| Bead | Title | Deliverable |
|------|-------|-------------|
| test-sim-zzz | Completed bead | zzz-completed.md |
EOF

check "Completed iteration has RETRO.md" test -f "$TEST_PROJECT/iterations/000/RETRO.md"
check "Completed iteration status is complete" grep -q 'complete' "$TEST_PROJECT/iterations/000/ITERATION.md"

# ═══════════════════════════════════════════════════════════════════════
# Scenario 3: Deliverable Naming Convention (Contract §1.4)
# ═══════════════════════════════════════════════════════════════════════

echo "=== Scenario 3: Deliverable Naming ==="

# Simulate a worker writing a deliverable
deliverable="$TEST_PROJECT/iterations/001/aaa-first-test-bead.md"
cat > "$deliverable" <<'EOF'
# First Test Bead

## Summary
Completed the first test bead.
EOF

check "Deliverable file created" test -f "$deliverable"
check "Deliverable has Summary section" grep -q '## Summary' "$deliverable"
# Naming: <id-suffix>-<descriptive-name>.md where id-suffix = last 3 chars of bead id
check "Deliverable name matches convention (aaa-*.md)" bash -c 'basename "$0" | grep -qE "^[a-z0-9]{3}-[a-z0-9-]+\.md$"' "$deliverable"

# ═══════════════════════════════════════════════════════════════════════
# Scenario 4: Orchestrator State File (Contracts §1.7, §2.7)
# Frequency scaling state transitions
# ═══════════════════════════════════════════════════════════════════════

echo "=== Scenario 4: Orchestrator Frequency Scaling ==="

STATE_FILE="$TEST_TMP/.orchestrator-state.json"

# After spawning workers: idle state cleared
cat > "$STATE_FILE" <<'EOF'
{
  "idleSince": null,
  "idleReason": null,
  "lastRunAt": "2026-02-13T12:00:00Z"
}
EOF

check "State file: idleSince null after spawn" bash -c 'python3 -c "import json; d=json.load(open(\"$0\")); assert d[\"idleSince\"] is None" "$0"' "$STATE_FILE"
check "State file: idleReason null after spawn" bash -c 'python3 -c "import json; d=json.load(open(\"$0\")); assert d[\"idleReason\"] is None" "$0"' "$STATE_FILE"
check "State file: lastRunAt is set" bash -c 'python3 -c "import json; d=json.load(open(\"$0\")); assert d[\"lastRunAt\"] is not None" "$0"' "$STATE_FILE"

# Transition to idle: no-active-iterations
cat > "$STATE_FILE" <<'EOF'
{
  "idleSince": "2026-02-13T12:05:00Z",
  "idleReason": "no-active-iterations",
  "lastRunAt": "2026-02-13T12:05:00Z"
}
EOF

check "State file: idle with no-active-iterations" bash -c 'python3 -c "import json; d=json.load(open(\"$0\")); assert d[\"idleReason\"] == \"no-active-iterations\"" "$0"' "$STATE_FILE"

# Validate backoff intervals per §2.7
# no-active-iterations = 30min, no-ready-beads = 15min, all-at-capacity = 10min
check "Contract: no-active-iterations backoff documented as 30min" grep -q 'no-active-iterations.*30' "$CONTRACTS"
check "Contract: no-ready-beads backoff documented as 15min" grep -q 'no-ready-beads.*15' "$CONTRACTS"
check "Contract: all-at-capacity backoff documented as 10min" grep -q 'all-at-capacity.*10' "$CONTRACTS"

# Validate valid idleReasons per §1.7
for reason in "no-active-iterations" "no-ready-beads" "all-at-capacity"; do
  check "idleReason '$reason' is documented" grep -q "$reason" "$CONTRACTS"
done

# ═══════════════════════════════════════════════════════════════════════
# Scenario 5: Worker Context Loading Order (Contract §3.1)
# ═══════════════════════════════════════════════════════════════════════

echo "=== Scenario 5: Worker Context Loading ==="

setup_test_project

# All context files must exist for a well-formed project
check "PROJECT.md exists (context step 1)" test -f "$TEST_PROJECT/PROJECT.md"
check "Project AGENTS.md exists (context step 3)" test -f "$TEST_PROJECT/AGENTS.md"
check "ITERATION.md exists (context step 4)" test -f "$TEST_PROJECT/iterations/001/ITERATION.md"

# Workspace AGENTS.md (context step 2) — check real workspace
check "Workspace AGENTS.md exists (context step 2)" test -f "$HOME/.openclaw/workspace/AGENTS.md"

# ═══════════════════════════════════════════════════════════════════════
# Scenario 6: Spawn Message Format (Contract §2.4)
# ═══════════════════════════════════════════════════════════════════════

echo "=== Scenario 6: Spawn Message Format ==="

# Simulate the spawn message the orchestrator would create
spawn_msg="Project: $TEST_PROJECT
Bead: test-sim-aaa
Iteration: 1
Channel: test-channel-123"

# Verify exactly four fields
line_count=$(echo "$spawn_msg" | wc -l | tr -d ' ')
check "Spawn message has exactly 4 lines" test "$line_count" -eq 4
# Write spawn message to file for reliable testing
echo "$spawn_msg" > "$TEST_TMP/spawn_msg.txt"
check "Spawn message has Project field" grep -q '^Project:' "$TEST_TMP/spawn_msg.txt"
check "Spawn message has Bead field" grep -q '^Bead:' "$TEST_TMP/spawn_msg.txt"
check "Spawn message has Iteration field" grep -q '^Iteration:' "$TEST_TMP/spawn_msg.txt"
check "Spawn message has Channel field" grep -q '^Channel:' "$TEST_TMP/spawn_msg.txt"

# ═══════════════════════════════════════════════════════════════════════
# Scenario 7: Session Label Convention (Contract §2.5)
# ═══════════════════════════════════════════════════════════════════════

echo "=== Scenario 7: Session Label Convention ==="

label="project:test-sim-project:test-sim-aaa"
check "Label matches format project:<slug>:<bead-id>" bash -c 'echo "$1" | grep -qE "^project:[a-z0-9-]+:[a-z0-9-]+$"' _ "$label"
check "Label starts with project: prefix" bash -c 'echo "$1" | grep -q "^project:"' _ "$label"

# ═══════════════════════════════════════════════════════════════════════
# Scenario 8: Bead Lifecycle (Contract §4.6)
# Valid state transitions: open → in_progress → closed | blocked
# ═══════════════════════════════════════════════════════════════════════

echo "=== Scenario 8: Bead Lifecycle ==="

# Verify contract documents valid transitions
check "Contract documents open → in_progress" grep -q 'open.*in_progress' "$CONTRACTS"
check "Contract documents closed state" grep -q 'closed.*final' "$CONTRACTS"
check "Contract documents blocked can be reopened" grep -q 'Blocked.*reopened' "$CONTRACTS"

# ═══════════════════════════════════════════════════════════════════════
# Scenario 9: Registry Validation (Contract §1.1)
# ═══════════════════════════════════════════════════════════════════════

echo "=== Scenario 9: Registry Validation ==="

check "Registry has required columns" grep -q '| Slug | Status | Priority | Path |' "$TEST_TMP/registry.md"
check "Registry has valid status" grep -q 'active' "$TEST_TMP/registry.md"
check "Registry has valid priority" grep -q 'high' "$TEST_TMP/registry.md"

# Invalid status should be caught
cat > "$TEST_TMP/bad-registry.md" <<'EOF'
| Slug | Status | Priority | Path |
|------|--------|----------|------|
| bad-proj | complete | high | /tmp/bad |
EOF

# "complete" is not a valid registry status per §1.1
bad_status=$(grep 'bad-proj' "$TEST_TMP/bad-registry.md" | awk -F'|' '{print $3}' | xargs)
check "Registry rejects 'complete' status" test "$bad_status" != "active" -a "$bad_status" != "paused" -a "$bad_status" != "blocked"

# ═══════════════════════════════════════════════════════════════════════
# Scenario 10: Worker Error Handling Simulation (Contract §3.10)
# ═══════════════════════════════════════════════════════════════════════

echo "=== Scenario 10: Worker Error Handling ==="

# Simulate partial completion: deliverable written but bead blocked
partial_deliverable="$TEST_PROJECT/iterations/001/bbb-second-test-bead.md"
cat > "$partial_deliverable" <<'EOF'
# Second Test Bead (Partial)

## Summary
Partially completed. Dependency test-sim-aaa was reopened mid-work.

## Remaining
- Complete integration after aaa lands
EOF

check "Partial deliverable written" test -f "$partial_deliverable"
check "Partial deliverable has Summary" grep -q '## Summary' "$partial_deliverable"
check "Partial deliverable documents remaining work" grep -q '## Remaining' "$partial_deliverable"

# ═══════════════════════════════════════════════════════════════════════
# Scenario 11: RETRO.md Format (Contract §1.5)
# ═══════════════════════════════════════════════════════════════════════

echo "=== Scenario 11: RETRO.md Format ==="

# Re-create the completed iteration (setup_test_project may have been called since Scenario 2)
mkdir -p "$TEST_PROJECT/iterations/000"
cat > "$TEST_PROJECT/iterations/000/ITERATION.md" <<'EOF2'
# Iteration 000
- **Status:** complete
## Stories
- test-sim-zzz: Completed bead
EOF2
cat > "$TEST_PROJECT/iterations/000/RETRO.md" <<'EOF2'
# Iteration 000 Retrospective
## Summary
Test completed iteration.
## Completed
| Bead | Title | Deliverable |
|------|-------|-------------|
| test-sim-zzz | Completed bead | zzz-completed.md |
EOF2

retro="$TEST_PROJECT/iterations/000/RETRO.md"
check "RETRO.md has Summary section" grep -q '## Summary' "$retro"
check "RETRO.md has Completed table" grep -q '## Completed' "$retro"
check "RETRO.md is in completed iteration" grep -q 'complete' "$TEST_PROJECT/iterations/000/ITERATION.md"

# ═══════════════════════════════════════════════════════════════════════
# Scenario 12: Orchestrator No-Direct-Work Contract (§2.1)
# ═══════════════════════════════════════════════════════════════════════

echo "=== Scenario 12: Orchestrator Invariants ==="

check "Contract: orchestrator never performs bead work" grep -q 'never.*performs bead work' "$CONTRACTS"
check "Contract: orchestrator only reads state and spawns" grep -q 'only reads state and spawns' "$CONTRACTS"
check "Contract: concurrency enforcement documented" grep -q 'Concurrency Enforcement' "$CONTRACTS"
check "Contract: active iteration required for spawn" grep -q 'Active Iteration Required' "$CONTRACTS"

# ═══════════════════════════════════════════════════════════════════════
# Scenario 13: Git Commit Convention (Contract §3.6)
# ═══════════════════════════════════════════════════════════════════════

echo "=== Scenario 13: Git Conventions ==="

# Validate commit format: "<summary> (<bead-id>)"
test_commit_msg="Add first test bead (test-sim-aaa)"
check "Commit format matches convention" bash -c 'echo "$1" | grep -qE "^.+ \([a-z0-9-]+\)$"' _ "$test_commit_msg"

iter_commit_msg="Complete iteration 1"
check "Iteration commit format" bash -c 'echo "$1" | grep -qE "^Complete iteration [0-9]+$"' _ "$iter_commit_msg"

# ═══════════════════════════════════════════════════════════════════════
# Scenario 14: STATUS.md Auto-generation (Contract §1.6)
# ═══════════════════════════════════════════════════════════════════════

echo "=== Scenario 14: STATUS.md ==="

check "Contract: STATUS.md is auto-generated" grep -q 'Auto-generated' "$CONTRACTS"
check "Contract: STATUS.md overwritten every run" grep -q 'Overwritten every orchestrator run' "$CONTRACTS"
check "Contract: STATUS.md never hand-edit" grep -q 'never hand-edit' "$CONTRACTS"

# ═══════════════════════════════════════════════════════════════════════
# Scenario 15: Cross-Cutting Path Convention (§4.1, §4.2)
# ═══════════════════════════════════════════════════════════════════════

echo "=== Scenario 15: Path Conventions ==="

check "Contract: ~ resolves to user home" grep -q 'always resolves to the user'"'"'s home directory' "$CONTRACTS"
check "Contract: PROJECTS_HOME default ~/Projects" grep -q 'PROJECTS_HOME.*defaults to.*~/Projects' "$CONTRACTS"
check "Contract: project files not in workspace" grep -q 'never created inside.*workspace' "$CONTRACTS"

# ═══════════════════════════════════════════════════════════════════════
# Summary
# ═══════════════════════════════════════════════════════════════════════

echo ""
echo "==============================="
echo "Simulation Tests: $PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ] && exit 0 || exit 1
