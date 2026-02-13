# Skill Migration

Migrate a project's files to match the current skill format.

## Trigger

User requests migration via the project's Channel (e.g., "update to latest skill format").

## Steps

### 1. Read Current Skill Format

Read `~/.openclaw/skills/projects/SKILL.md` and extract the canonical formats for:
- `PROJECT.md` (fields, structure, notifications table)
- `ITERATION.md` (fields, structure)
- Directory layout conventions

### 2. Read Project Files

Read the project's existing:
- `PROJECT.md`
- Active `ITERATION.md` (current iteration)
- Any completed iteration files (for reference, but these are immutable)

### 3. Diff and Identify Gaps

Compare the project's files against the canonical format. Common migrations:
- Missing fields in PROJECT.md (e.g., new settings added to the skill)
- Changed field names or structure
- Missing Notifications table
- ITERATION.md format changes
- Directory layout changes

### 4. Report and Migrate

For each gap found:

**If the project's Autonomy is `full`:**
- Apply the migration directly to affected files
- Preserve existing values — only add missing fields with sensible defaults
- Never modify completed iterations (they are immutable)
- Commit changes: `git add -A && git commit -m "Migrate to latest skill format"`
- Notify the Channel with a summary of changes made

**If the project's Autonomy is `ask-first`:**
- Report all gaps to the Channel
- Wait for approval before making changes
- Apply approved changes, commit, and confirm

### 5. Edge Cases

- **Completed iterations**: Never modify. Note any format differences in the Channel message but do not change them.
- **Custom fields**: Preserve any project-specific fields not in the canonical format. Migration is additive.
- **Conflicts**: If a project field contradicts the new format (e.g., renamed field with different semantics), flag it as a question in the Channel rather than guessing.

## Example Channel Message

```
📋 **Skill Migration Report** for <project-name>

**Changes applied:**
- Added `MaxWorkers: 1` to PROJECT.md (new field, default value)
- Added Notifications table to PROJECT.md (all events set to `on`)
- Updated ITERATION.md to include `## Guardrails` section (empty)

**No changes needed:**
- Directory layout ✓
- Iteration format ✓

Committed: `abc1234 — Migrate to latest skill format`
```
