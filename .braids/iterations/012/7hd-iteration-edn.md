# Convert ITERATION.md to iteration.edn

## Summary

Completed the migration from ITERATION.md to iteration.edn format. The CLI now reads only iteration.edn with no markdown fallback.

## Changes

### New functions in `iteration.clj`
- `validate-iteration` — validates iteration data, returns error vector
- `migrate-iteration-md` — converts ITERATION.md content to EDN map
- `parse-iteration-number` — extracts iteration number from MD header (legacy, for migration)
- `parse-iteration-status` — extracts status from MD content (legacy, for migration)
- `parse-iteration-stories` — extracts story list from MD content (legacy, for migration)
- Added `:guardrails` to `iteration-defaults`

### Migration pipeline (`migration.clj` / `migration_io.clj`)
- Added `plan-iteration-migrations` pure function for planning ITERATION.md → iteration.edn conversions
- Updated `migration_io.clj` `run-migrate` to include iteration migrations
- Updated `format-migration-report` to handle `:write-iteration-edn` action type

### Reference docs updated
- `project-creation.md` — now shows iteration.edn format instead of ITERATION.md
- `agents-template.md` — references iteration.edn for active iteration lookup
- `status-dashboard.md` — references iteration.edn for active iteration scanning

### Git cleanup
- Removed all 12 ITERATION.md files from git tracking (they were already replaced by iteration.edn on disk)

### Tests
- All existing iteration tests pass (including the guardrails default fix)
- Added 4 new migration tests for `plan-iteration-migrations`
- 370 examples total, 6 pre-existing failures (homebrew/install.sh repo refs, registry validation, integration checks)
