# Deliverable: Add colored stderr debug output to braids orch-run (braids-dwc)

## Summary

Added human-readable colored debug output to stderr when running `braids orch-run`. Stdout remains clean JSON for orchestrator parsing.

## Changes

### `src/braids/orch.clj`
- Added ANSI color constants and helper functions (`c`, `no-color?`)
- Added `format-debug-output` — pure function producing the debug string
- Handles bead status icons: ○ open, ⚙️ in-progress, 🚫 blocked, ✓ closed

### `src/braids/orch_io.clj`
- Added `*-debug` variants of gather functions returning `{:result :debug-ctx}`

### `src/braids/core.clj`
- `:orch-run` now prints debug to stderr via `(binding [*out* *err*] ...)`

### `spec/braids/orch_spec.clj`
- 7 new tests for `format-debug-output`

## Verification

```
$ bb braids orch-run 2>/tmp/debug.txt
{"action":"spawn","spawns":[...]}

$ cat /tmp/debug.txt
  braids  active  iteration 018  → 3 beads
    ○ hio  open
    ○ nh6  open
    ⚙️ dwc  in_progress
  wealth  active  (no iteration)
  ...
  → spawn: 1 worker(s)

$ NO_COLOR=1 bb braids orch-run 2>&1 1>/dev/null
(same output, no ANSI codes)

$ bb test
466 examples, 9 failures (all pre-existing), 875 assertions
```
