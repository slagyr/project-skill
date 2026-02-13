#!/usr/bin/env bash
# Structural tests: validate files, symlinks, registry, and spawn config
# for the projects-skill project and any registered projects.

set -euo pipefail

PROJECTS_HOME="${PROJECTS_HOME:-$HOME/Projects}"
SKILL_SYMLINK="$HOME/.openclaw/skills/projects"
SKILL_SOURCE="$PROJECTS_HOME/projects-skill/projects"
REGISTRY="$PROJECTS_HOME/registry.md"

PASS=0
FAIL=0

pass() { ((PASS++)); echo "  ✓ $1"; }
fail() { ((FAIL++)); echo "  ✗ $1"; }
check() {
  # Usage: check "description" <command...>
  local desc="$1"; shift
  if "$@" >/dev/null 2>&1; then pass "$desc"; else fail "$desc"; fi
}

# ─── Skill Symlink ───────────────────────────────────────────────────

echo "▸ Skill Symlink"
check "Symlink exists at $SKILL_SYMLINK" test -L "$SKILL_SYMLINK"
check "Symlink target is valid directory" test -d "$SKILL_SYMLINK"
if [ -L "$SKILL_SYMLINK" ]; then
  target="$(readlink "$SKILL_SYMLINK")"
  # Use stat to get inode-level identity (handles case-insensitive filesystems)
  # Append /. to dereference symlinks before comparing inodes
  symlink_inode="$(stat -f '%d:%i' "$SKILL_SYMLINK/." 2>/dev/null || stat -c '%d:%i' "$SKILL_SYMLINK/." 2>/dev/null)"
  source_inode="$(stat -f '%d:%i' "$SKILL_SOURCE/." 2>/dev/null || stat -c '%d:%i' "$SKILL_SOURCE/." 2>/dev/null)"
  if [ "$symlink_inode" = "$source_inode" ]; then
    pass "Symlink points to projects-skill/projects"
  else
    fail "Symlink points to '$target' (expected $SKILL_SOURCE)"
  fi
fi

# ─── Skill Directory Contents ────────────────────────────────────────

echo "▸ Skill Directory"
check "SKILL.md exists" test -f "$SKILL_SYMLINK/SKILL.md"
check "references/ directory exists" test -d "$SKILL_SYMLINK/references"
for ref in orchestrator.md worker.md agents-template.md status-dashboard.md migration.md; do
  check "references/$ref exists" test -f "$SKILL_SYMLINK/references/$ref"
done

# ─── SKILL.md has frontmatter ────────────────────────────────────────

echo "▸ SKILL.md Format"
if [ -f "$SKILL_SYMLINK/SKILL.md" ]; then
  if head -1 "$SKILL_SYMLINK/SKILL.md" | grep -q '^---$'; then
    pass "SKILL.md has YAML frontmatter"
  else
    fail "SKILL.md missing YAML frontmatter (must start with ---)"
  fi
  check "SKILL.md frontmatter has name field" grep -q '^name:' "$SKILL_SYMLINK/SKILL.md"
  check "SKILL.md frontmatter has description field" grep -q '^description:' "$SKILL_SYMLINK/SKILL.md"
fi

# ─── Registry ────────────────────────────────────────────────────────

echo "▸ Registry ($REGISTRY)"
check "registry.md exists" test -f "$REGISTRY"
if [ -f "$REGISTRY" ]; then
  check "Registry has table header" grep -q '| Slug | Status | Priority | Path |' "$REGISTRY"

  # Validate each registered project
  while IFS='|' read -r _ slug status priority path _; do
    slug="$(echo "$slug" | xargs)"
    status="$(echo "$status" | xargs)"
    priority="$(echo "$priority" | xargs)"
    path="$(echo "$path" | xargs)"
    [ -z "$slug" ] && continue
    [[ "$slug" == "Slug" ]] && continue
    [[ "$slug" == -* ]] && continue

    # Expand ~ to $HOME
    resolved_path="${path/#\~/$HOME}"

    echo "▸ Project: $slug"
    check "Project directory exists ($resolved_path)" test -d "$resolved_path"
    check "PROJECT.md exists" test -f "$resolved_path/PROJECT.md"
    check "AGENTS.md exists" test -f "$resolved_path/AGENTS.md"
    check ".beads/ directory exists (bd init)" test -d "$resolved_path/.beads"
    check "iterations/ directory exists" test -d "$resolved_path/iterations"
    check "Is a git repo" test -d "$resolved_path/.git"

    # Validate PROJECT.md has required fields
    if [ -f "$resolved_path/PROJECT.md" ]; then
      check "PROJECT.md has Status field" grep -qiE '(^\- \*\*Status:\*\*|^Status:)' "$resolved_path/PROJECT.md"
      check "PROJECT.md has Goal section" grep -q '^## Goal' "$resolved_path/PROJECT.md"
      check "PROJECT.md has Guardrails section" grep -q '^## Guardrails' "$resolved_path/PROJECT.md"
    fi

    # Validate status is a known value
    case "$status" in
      active|paused|blocked) pass "Registry status is valid ($status)" ;;
      *) fail "Registry status '$status' is not valid (expected active|paused|blocked)" ;;
    esac

    # Validate priority is a known value
    case "$priority" in
      high|normal|low) pass "Registry priority is valid ($priority)" ;;
      *) fail "Registry priority '$priority' is not valid (expected high|normal|low)" ;;
    esac

    # Check active iterations have valid format
    if [ -d "$resolved_path/iterations" ]; then
      for iter_dir in "$resolved_path"/iterations/*/; do
        [ -d "$iter_dir" ] || continue
        iter_name="$(basename "$iter_dir")"
        # Skip directories that don't match NNN format
        [[ "$iter_name" =~ ^[0-9]{3}$ ]] || { fail "iterations/$iter_name doesn't match NNN naming convention"; continue; }
        iter_md="$iter_dir/ITERATION.md"
        if [ -f "$iter_md" ]; then
          check "iterations/$iter_name/ITERATION.md has Status" grep -qi 'Status:' "$iter_md"
          check "iterations/$iter_name/ITERATION.md has Stories section" grep -q '^## Stories' "$iter_md"

          # Validate iteration status value
          iter_status="$(grep -i 'Status:' "$iter_md" | head -1 | sed 's/.*[Ss]tatus:\*\* *//; s/.*[Ss]tatus: *//' | xargs)"
          case "$iter_status" in
            planning|active|complete) pass "iterations/$iter_name status is valid ($iter_status)" ;;
            *) fail "iterations/$iter_name status '$iter_status' is not valid" ;;
          esac

          # Completed iterations should have RETRO.md
          if [ "$iter_status" = "complete" ]; then
            check "iterations/$iter_name has RETRO.md (completed)" test -f "$iter_dir/RETRO.md"
          fi

          # Active iteration: stories should reference valid bead ids
          if [ "$iter_status" = "active" ]; then
            while read -r line; do
              bead_id="$(echo "$line" | grep -o 'projects-skill-[a-z0-9]*' || true)"
              if [ -n "$bead_id" ]; then
                if (cd "$resolved_path" && bd show "$bead_id" >/dev/null 2>&1); then
                  pass "Bead $bead_id exists in tracker"
                else
                  fail "Bead $bead_id referenced in iteration but not found in bd"
                fi
              fi
            done < <(grep '^- ' "$iter_md" | grep -v '^- \*\*')
          fi
        else
          fail "iterations/$iter_name missing ITERATION.md"
        fi
      done
    fi

  done < <(grep '|' "$REGISTRY" | tail -n +3)
fi

# ─── Spawn Config Validation ─────────────────────────────────────────

echo "▸ Spawn Config"
# Check that cron job exists for the orchestrator
if command -v openclaw >/dev/null 2>&1; then
  cron_output="$(openclaw cron list 2>/dev/null || true)"
  if echo "$cron_output" | grep -q 'projects'; then
    pass "Orchestrator cron job exists"
  else
    fail "No cron job found matching 'projects'"
  fi
else
  pass "openclaw not in PATH (skip cron check)"
fi

# Validate PROJECT.md spawn-relevant fields for active projects
if [ -f "$REGISTRY" ]; then
  while IFS='|' read -r _ slug status _ path _; do
    slug="$(echo "$slug" | xargs)"
    status="$(echo "$status" | xargs)"
    path="$(echo "$path" | xargs)"
    [ -z "$slug" ] && continue
    [[ "$slug" == "Slug" ]] && continue
    [[ "$slug" == -* ]] && continue
    [ "$status" != "active" ] && continue

    resolved_path="${path/#\~/$HOME}"
    project_md="$resolved_path/PROJECT.md"
    [ -f "$project_md" ] || continue

    echo "▸ Spawn Config: $slug"

    # MaxWorkers should be a positive integer if present
    mw="$(grep -i 'MaxWorkers' "$project_md" | head -1 | grep -o '[0-9]*' || true)"
    if [ -n "$mw" ] && [ "$mw" -gt 0 ] 2>/dev/null; then
      pass "MaxWorkers is valid ($mw)"
    elif [ -z "$mw" ]; then
      pass "MaxWorkers not set (default 1 applies)"
    else
      fail "MaxWorkers value '$mw' is not a positive integer"
    fi

    # Channel should be set for notifications
    channel="$(grep -i '^\- \*\*Channel:\*\*' "$project_md" | head -1 | sed 's/.*Channel:\*\* *//' | xargs)"
    if [ -n "$channel" ]; then
      pass "Channel is set ($channel)"
    else
      fail "Channel not set — notifications will be skipped"
    fi

    # iteration-complete notification should include a mention for phone alerts
    iter_complete="$(grep -i 'iteration-complete' "$project_md" | head -1)"
    if echo "$iter_complete" | grep -q 'mention.*<@[0-9]\+>'; then
      pass "iteration-complete includes @mention"
    elif echo "$iter_complete" | grep -qi 'on'; then
      fail "iteration-complete is on but missing @mention — no phone notification"
    else
      pass "iteration-complete not enabled (mention not required)"
    fi

  done < <(grep '|' "$REGISTRY" | tail -n +3)
fi

# ─── Summary ─────────────────────────────────────────────────────────

echo ""
echo "==============================="
echo "Structural Tests: $PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ] && exit 0 || exit 1
