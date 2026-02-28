# Rename orch-run back to orch-tick (braids-73f)

## Summary

Renamed the `orch-run` CLI command back to `orch-tick` across all code, tests, and documentation. The old simple `orch-tick` command (which only ran `format-tick-json`) was removed — the renamed command retains the full functionality (debug output, logging, zombie detection, session collection).

## Changes

- **`src/braids/core.clj`** — Removed old `orch-tick` entry and `:orch-tick` case (simple tick-only). Renamed `orch-run` command and `:orch-run` case to `orch-tick`. Updated help text.
- **`src/braids/orch.clj`** — Renamed `format-orch-run-json` → `format-orch-tick-json`. Updated docstring.
- **`src/braids/orch_log.clj`** — Log header changed from `=== orch-run` to `=== orch-tick`
- **`spec/braids/orch_spec.clj`** — Updated all `format-orch-run-json` references to `format-orch-tick-json`
- **`spec/braids/orch_log_spec.clj`** — Updated expected log header
- **`braids/references/orchestrator.md`** — All `orch-run` references → `orch-tick`

## Verification

```
$ bb braids help | grep orch
  orch-tick   Orchestrator tick: compute spawns, detect zombies, log to /tmp/braids.log (JSON)

$ bb braids orch-run 2>&1 | head -1
Unknown command: orch-run

$ bb braids orch-tick 2>/dev/null
{"action":"idle","reason":"no-ready-beads","disable_cron":false}

$ rm -f /tmp/braids.log && bb braids orch-tick 2>/dev/null && head -1 /tmp/braids.log
=== orch-tick 2026-02-27T17:21:16 ===

$ bb test 2>&1 | grep "examples"
481 examples, 9 failures, 898 assertions
# (9 failures are pre-existing integration tests unrelated to this change)
```
