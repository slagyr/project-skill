#!/usr/bin/env bash
# Integration smoke tests: end-to-end validation after each iteration.
# Validates live project state — bd tracker, git history, deliverables,
# iteration consistency — across all registered projects.
#
# Unlike simulation tests (mock data) or structural tests (file existence),
# these tests verify that the actual system state is internally consistent.

set -euo pipefail

PROJECTS_HOME="${PROJECTS_HOME:-$HOME/Projects}"
REGISTRY="$PROJECTS_HOME/registry.md"

PASS=0
FAIL=0

pass() { ((PASS++)); echo "  ✓ $1"; }
fail() { ((FAIL++)); echo "  ✗ $1"; }
check() {
  local desc="$1"; shift
  if "$@" >/dev/null 2>&1; then pass "$desc"; else fail "$desc"; fi
}

set +e

# ═══════════════════════════════════════════════════════════════════════
# Preflight: Registry must exist
# ═══════════════════════════════════════════════════════════════════════

if [ ! -f "$REGISTRY" ]; then
  echo "  ✗ Registry not found at $REGISTRY"
  echo ""
  echo "==============================="
  echo "Integration Smoke Tests: 0 passed, 1 failed"
  exit 1
fi

# ═══════════════════════════════════════════════════════════════════════
# Helper: extract bead ids from ITERATION.md stories section
# ═══════════════════════════════════════════════════════════════════════

extract_bead_ids() {
  local iter_md="$1"
  # Extract bead IDs from story lines: "- slug-slug-id: title"
  # Match story lines: "- bead-id: title" where bead-id contains at least one hyphen
  # Exclude bold metadata lines (- **Status:** etc.)
  grep '^- [a-z]' "$iter_md" | grep -v '^\- \*\*' | grep ':' | sed 's/^- \([^:]*\):.*/\1/' || true
}

# ═══════════════════════════════════════════════════════════════════════
# Helper: get bead status from bd
# ═══════════════════════════════════════════════════════════════════════

get_bead_status() {
  local project_dir="$1" bead_id="$2"
  local output
  output="$(cd "$project_dir" && bd show "$bead_id" 2>&1)" || { echo "UNKNOWN"; return; }
  echo "$output" | grep -oE '\b(OPEN|CLOSED|BLOCKED|IN_PROGRESS)\b' | head -1 || echo "UNKNOWN"
}

# ═══════════════════════════════════════════════════════════════════════
# Iterate registered projects
# ═══════════════════════════════════════════════════════════════════════

while IFS='|' read -r _ slug status priority path _; do
  slug="$(echo "$slug" | xargs)"
  status="$(echo "$status" | xargs)"
  path="$(echo "$path" | xargs)"
  [ -z "$slug" ] && continue
  [[ "$slug" == "Slug" ]] && continue
  [[ "$slug" == -* ]] && continue

  resolved_path="${path/#\~/$HOME}"
  [ -d "$resolved_path" ] || continue
  [ -f "$resolved_path/PROJECT.md" ] || continue

  echo "=== Project: $slug ==="

  # ─────────────────────────────────────────────────────────────────
  # 1. Git state consistency
  # ─────────────────────────────────────────────────────────────────

  echo "▸ Git State"
  if [ -d "$resolved_path/.git" ]; then
    # Check for unpushed commits (integration means everything is pushed)
    unpushed="$(cd "$resolved_path" && git log --oneline '@{u}..HEAD' 2>/dev/null | wc -l | tr -d ' ')"
    if [ "$unpushed" -le 5 ]; then
      pass "Unpushed commits within tolerance ($unpushed)"
    else
      fail "Many unpushed commits ($unpushed) — work may not be pushed"
    fi

    # Check we're on a known branch
    branch="$(cd "$resolved_path" && git branch --show-current 2>/dev/null)"
    if [ -n "$branch" ]; then
      pass "On branch '$branch'"
    else
      fail "Detached HEAD state"
    fi
  fi

  # ─────────────────────────────────────────────────────────────────
  # 2. Completed iterations: full consistency check
  # ─────────────────────────────────────────────────────────────────

  for iter_dir in "$resolved_path"/iterations/*/; do
    [ -d "$iter_dir" ] || continue
    iter_name="$(basename "$iter_dir")"
    iter_md="$iter_dir/ITERATION.md"
    [ -f "$iter_md" ] || continue

    iter_status="$(grep -i 'Status:' "$iter_md" | head -1 | sed 's/.*[Ss]tatus:\*\* *//; s/.*[Ss]tatus: *//' | xargs)"

    if [ "$iter_status" = "complete" ]; then
      echo "▸ Completed Iteration $iter_name"

      # RETRO.md must exist
      check "RETRO.md exists" test -f "$iter_dir/RETRO.md"

      # Every story should have a deliverable file
      bead_ids="$(extract_bead_ids "$iter_md")"
      for bead_id in $bead_ids; do
        suffix="${bead_id##*-}"
        # Check both naming conventions: suffix-style (uu0-desc.md) and full-id (projects-skill-uu0.md)
        deliverable_match="$(ls "$iter_dir"/${suffix}-*.md "$iter_dir"/${bead_id}.md 2>/dev/null | grep -v ITERATION.md | grep -v RETRO.md | head -1)"
        if [ -n "$deliverable_match" ]; then
          pass "Deliverable exists for $bead_id ($(basename "$deliverable_match"))"
        else
          fail "No deliverable found for $bead_id (expected ${suffix}-*.md or ${bead_id}.md)"
        fi
      done

      # Check bd status only if beads still exist in tracker
      # (older iterations may have had beads pruned from bd)
      for bead_id in $bead_ids; do
        bd_status="$(get_bead_status "$resolved_path" "$bead_id")"
        if [ "$bd_status" = "UNKNOWN" ]; then
          pass "Bead $bead_id not in bd (archived/pruned — OK for completed iteration)"
        elif [ "$bd_status" = "CLOSED" ]; then
          pass "Bead $bead_id is closed in bd"
        else
          fail "Bead $bead_id is $bd_status (expected CLOSED for completed iteration)"
        fi
      done

      # Git history should contain commits referencing these beads
      for bead_id in $bead_ids; do
        if (cd "$resolved_path" && git log --oneline --all --grep="$bead_id" 2>/dev/null | head -1 | grep -q .); then
          pass "Git commit found for $bead_id"
        else
          fail "No git commit references $bead_id"
        fi
      done
    fi

    # ─────────────────────────────────────────────────────────────────
    # 3. Active iterations: state consistency
    # ─────────────────────────────────────────────────────────────────

    if [ "$iter_status" = "active" ]; then
      echo "▸ Active Iteration $iter_name"

      bead_ids="$(extract_bead_ids "$iter_md")"
      closed_count=0
      open_count=0
      total_count=0

      for bead_id in $bead_ids; do
        ((total_count++))
        bd_status="$(get_bead_status "$resolved_path" "$bead_id")"

        # Closed beads should have deliverables
        if [ "$bd_status" = "CLOSED" ]; then
          ((closed_count++))
          suffix="${bead_id##*-}"
          deliverable_match="$(ls "$iter_dir"/${suffix}-*.md 2>/dev/null | grep -v ITERATION.md | grep -v RETRO.md | head -1)"
          if [ -n "$deliverable_match" ]; then
            pass "Closed bead $bead_id has deliverable"
          else
            fail "Closed bead $bead_id missing deliverable"
          fi

          # Closed beads should have git commits
          if (cd "$resolved_path" && git log --oneline --all --grep="$bead_id" 2>/dev/null | head -1 | grep -q .); then
            pass "Closed bead $bead_id has git commit"
          else
            fail "Closed bead $bead_id missing git commit"
          fi
        else
          ((open_count++))
        fi

        # Every listed bead should exist in bd
        if [ "$bd_status" != "UNKNOWN" ]; then
          pass "Bead $bead_id exists in bd ($bd_status)"
        else
          fail "Bead $bead_id listed in ITERATION.md but not found in bd"
        fi
      done

      pass "Active iteration $iter_name: $closed_count/$total_count beads closed"

      # If all beads closed but iteration still active → inconsistency
      if [ "$total_count" -gt 0 ] && [ "$closed_count" -eq "$total_count" ]; then
        fail "All beads closed but iteration still active (should be complete)"
      fi
    fi
  done

  # ─────────────────────────────────────────────────────────────────
  # 4. At most one active iteration (§1.3)
  # ─────────────────────────────────────────────────────────────────

  active_count="$(grep -rl 'Status:.*active' "$resolved_path"/iterations/*/ITERATION.md 2>/dev/null | wc -l | tr -d ' ')"
  if [ "$active_count" -le 1 ]; then
    pass "At most one active iteration ($active_count)"
  else
    fail "Multiple active iterations ($active_count) — violates §1.3"
  fi

  # ─────────────────────────────────────────────────────────────────
  # 5. No orphaned deliverables (deliverables without matching bead)
  # ─────────────────────────────────────────────────────────────────

  for iter_dir in "$resolved_path"/iterations/*/; do
    [ -d "$iter_dir" ] || continue
    iter_name="$(basename "$iter_dir")"
    iter_md="$iter_dir/ITERATION.md"
    [ -f "$iter_md" ] || continue

    bead_suffixes="$(extract_bead_ids "$iter_md" | while read -r id; do echo "${id##*-}"; done | sort -u)"

    for deliverable in "$iter_dir"/*.md; do
      [ -f "$deliverable" ] || continue
      dname="$(basename "$deliverable")"
      # Skip ITERATION.md and RETRO.md
      [[ "$dname" == "ITERATION.md" ]] && continue
      [[ "$dname" == "RETRO.md" ]] && continue

      # Match by suffix prefix (e.g., uu0-*.md) or full bead ID (e.g., projects-skill-uu0.md)
      prefix="${dname%%-*}"
      fname_no_ext="${dname%.md}"
      matched=false
      # Check suffix-style naming (uu0-description.md)
      if echo "$bead_suffixes" | grep -qx "$prefix"; then
        matched=true
      fi
      # Check full bead ID naming (projects-skill-uu0.md) — old convention
      bead_full_ids="$(extract_bead_ids "$iter_md")"
      if echo "$bead_full_ids" | grep -qx "$fname_no_ext"; then
        matched=true
      fi
      if $matched; then
        pass "Deliverable $dname matches a bead"
      else
        fail "Orphaned deliverable $dname — no matching bead in iteration"
      fi
    done
  done

done < <(grep '|' "$REGISTRY" | tail -n +3)

# ═══════════════════════════════════════════════════════════════════════
# Cross-project: STATUS.md freshness
# ═══════════════════════════════════════════════════════════════════════

echo "=== Cross-Project Checks ==="

echo "▸ STATUS.md"
STATUS_FILE="$PROJECTS_HOME/STATUS.md"
if [ -f "$STATUS_FILE" ]; then
  pass "STATUS.md exists"
  # Check it was updated recently (within last hour)
  if command -v stat >/dev/null 2>&1; then
    now="$(date +%s)"
    # macOS stat
    mtime="$(stat -f '%m' "$STATUS_FILE" 2>/dev/null || stat -c '%Y' "$STATUS_FILE" 2>/dev/null || echo 0)"
    age=$(( now - mtime ))
    if [ "$age" -lt 3600 ]; then
      pass "STATUS.md updated within last hour (${age}s ago)"
    elif [ "$age" -lt 86400 ]; then
      pass "STATUS.md updated within last day ($(( age / 3600 ))h ago)"
    else
      fail "STATUS.md is stale ($(( age / 86400 ))d old)"
    fi
  fi
else
  fail "STATUS.md not found at $STATUS_FILE"
fi

echo "▸ Orchestrator State"
STATE_FILE="$PROJECTS_HOME/.orchestrator-state.json"
if [ -f "$STATE_FILE" ]; then
  pass "Orchestrator state file exists"
  # Validate JSON structure
  if python3 -c "import json; json.load(open('$STATE_FILE'))" 2>/dev/null; then
    pass "Orchestrator state is valid JSON"
    # Check required fields
    check "State has lastRunAt" python3 -c "import json; d=json.load(open('$STATE_FILE')); assert 'lastRunAt' in d"
    check "State has idleSince" python3 -c "import json; d=json.load(open('$STATE_FILE')); assert 'idleSince' in d"
    check "State has idleReason" python3 -c "import json; d=json.load(open('$STATE_FILE')); assert 'idleReason' in d"

    # Validate idleReason is a known value or null
    idle_reason="$(python3 -c "import json; print(json.load(open('$STATE_FILE')).get('idleReason', 'null'))" 2>/dev/null)"
    case "$idle_reason" in
      null|None|no-active-iterations|no-ready-beads|all-at-capacity)
        pass "idleReason is valid ($idle_reason)" ;;
      *)
        fail "idleReason '$idle_reason' is not a recognized value" ;;
    esac
  else
    fail "Orchestrator state is not valid JSON"
  fi
else
  fail "Orchestrator state file not found"
fi

# ═══════════════════════════════════════════════════════════════════════
# Summary
# ═══════════════════════════════════════════════════════════════════════

echo ""
echo "==============================="
echo "Integration Smoke Tests: $PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ] && exit 0 || exit 1
