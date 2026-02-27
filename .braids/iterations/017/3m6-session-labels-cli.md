# Pass session labels to orch-run CLI (braids-3m6)

## Summary

Added `--sessions` flag to `braids orch-run` that accepts space-separated session labels instead of JSON. The CLI handles concurrency checking in pure Clojure, reducing the orchestrator to 3 tool calls.

## Changes

### `src/braids/orch.clj`
- Added `parse-session-labels-string`: parses space-separated labels, filters to `project:` prefixed ones
- Added `detect-zombies-from-labels`: lightweight zombie detection from plain labels (bead-closed only)

### `src/braids/orch_io.clj`
- Added `gather-and-tick-from-session-labels`: full IO pipeline using plain labels — parses labels, batch-loads bead statuses, detects bead-closed zombies, counts healthy workers, runs tick

### `src/braids/core.clj`
- `orch-run` now checks `--sessions` first, falls back to `--session-labels` (JSON), then no-args
- Backward compatible: `--session-labels` and no-args modes still work

### `braids/references/orchestrator.md`
- Updated Step 1 to document `--sessions` as the primary interface
- Documented the simplified 3-call orchestrator flow
- Noted `--session-labels` as legacy fallback

## Tests

### Unit tests (spec/braids/orch_spec.clj)
New test groups:
- `parse-session-labels-string`: 5 specs (empty, nil, whitespace, filtering)
- `detect-zombies-from-labels`: 4 specs (bead-closed detection, open bead, non-project labels, missing status)

## Verification

```
$ bb run braids orch-run --sessions 'project:braids:braids-3m6'
{"action":"idle","reason":"all-at-capacity","disable_cron":false}

$ bb run braids orch-run --sessions ''
{"action":"spawn","spawns":[{"task":"You are a project worker...","label":"project:braids:braids-1zy",...}]}

$ bb run braids orch-run
{"action":"spawn","spawns":[{"task":"You are a project worker...","label":"project:braids:braids-1zy",...}]}
```

All 3 modes work correctly. Pre-existing test suite: 455 examples, same 10 pre-existing integration failures (unrelated to this change).
