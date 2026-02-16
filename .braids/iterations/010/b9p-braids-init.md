# braids init: first-time setup

## Summary

Implemented `braids init` — a CLI command that performs first-time setup for the braids skill, replacing the manual steps in `references/init.md`.

## What it does

1. Creates `~/.openclaw/braids/` state directory
2. Creates `~/Projects` (or `$PROJECTS_HOME`) if it doesn't exist
3. Initializes `registry.edn` with empty project list

## Flags

- `--force` — reinitialize even if already set up
- `--projects-home <path>` — override default projects home

## Prerequisite checks

- Verifies `bd` (beads) is installed
- Detects if already initialized (requires `--force` to proceed)

## Files added/changed

- `src/braids/init.clj` — pure logic (prerequisites, plan, formatting)
- `src/braids/init_io.clj` — IO layer (filesystem, bd detection, arg parsing)
- `spec/braids/init_spec.clj` — unit tests for pure logic (8 tests)
- `spec/braids/init_io_spec.clj` — integration tests with temp filesystem (6 tests)
- `src/braids/core.clj` — wired `init` into command dispatch

## Notes

- Does NOT set up the orchestrator cron (that remains a manual/reference doc step since cron setup requires OpenClaw API access, not filesystem operations)
- Follows the same IO/pure separation pattern as `braids new`
- All 14 new tests pass; no regressions in existing tests
