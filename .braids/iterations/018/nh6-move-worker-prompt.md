# Move worker prompt out of CLI and into orchestrator.md (braids-nh6)

## Summary

Removed the hardcoded worker prompt from the CLI (`orch.clj`) and moved prompt ownership to the orchestrator agent via `orchestrator.md`.

## Changes

### `src/braids/orch.clj`
- Removed `worker-instruction` constant, `spawn-msg` function, `format-spawn-msg-json` function
- Updated `format-orch-run-json` to output structural data only (project, bead, iteration, channel, path, label, runTimeoutSeconds, cleanup, thinking, agentId) — no `task` field

### `src/braids/core.clj`
- Removed `spawn-msg` command from commands map and dispatch
- Removed unused `clojure.set` require

### `braids/references/orchestrator.md`
- Added **Worker Prompt Template** section under "Spawn Workers"
- Updated spawn result JSON example to show structural fields instead of `task`

### Tests
- Updated spawn_msg_spec, orch_spec, core_spec to reflect removals
- No new test failures (9 pre-existing integration failures unchanged)

## Verification

```
$ bb braids help
  (spawn-msg no longer listed in commands)

$ bb braids spawn-msg
  Unknown command: spawn-msg

$ bb braids orch-run
  {"action":"idle","reason":"no-ready-beads","disable_cron":false}

$ bb test
  465 examples, 9 failures (all pre-existing), 863 assertions
```
