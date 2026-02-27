# braids-914: Refine orchestrator disable logic

## Summary

Fixed the orchestrator `tick` function to correctly disable cron when active iterations have no open beads.

## Changes

### `src/braids/orch.clj`
- Made `tick` multi-arity: 6-arg (backward compatible, assumes open beads exist) and 7-arg (accepts `open-beads` map)
- When `open-beads` is provided and no eligible project has any open beads, `disable-cron` is `true` even for `no-ready-beads` reason
- When `open-beads` is `nil` (old callers), behavior unchanged: `disable-cron false`

### `src/braids/orch_io.clj`
- Added `load-open-beads` function: loads all non-closed beads via `bd list --json`
- Updated all three gather functions to collect open-beads and pass to `tick`

### `spec/braids/orch_spec.clj`
- Added 3 new tests for the refined disable logic + 1 backward compat test
- All orch tests pass (zero failures)

## Verification

```
$ bb test 2>&1 | grep "examples"
458 examples, 10 failures, 853 assertions
```

All 10 failures are pre-existing integration tests. Zero orch test failures.
