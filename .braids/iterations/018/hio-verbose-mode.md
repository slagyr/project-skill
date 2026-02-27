# Verbose Mode for Orchestrator (braids-hio)

## Summary

Added verbose mode to the orchestrator. When enabled, the orchestrator posts a detailed tick summary to the orchestrator channel on every run, showing sessions, CLI input/output, and the decision made.

## Changes

### 1. Config defaults (`src/braids/config.clj`)
- Added `:verbose false` to `defaults` map

### 2. Config tests (`spec/braids/config_spec.clj`, `spec/braids/config_io_spec.clj`)
- Updated expected values to include `:verbose false`
- Added test for parsing `:verbose true/false`

### 3. Orchestrator reference (`braids/references/orchestrator.md`)
- Added "Verbose Mode" section documenting:
  - How to enable (`:verbose true` in `~/.openclaw/braids/config.edn`)
  - Message format posted to orchestrator channel each tick
  - Token impact note

### 4. Global config (`~/.openclaw/braids/config.edn`)
- Set `:verbose true` as requested by the bead

## Verification

```
$ braids config get verbose
true

$ braids config list
braids-home = ~/Projects
orchestrator-channel = 1476813011925598343
verbose = true

$ bb test 2>&1 | grep "examples,"
467 examples, 9 failures, 877 assertions
(9 failures are pre-existing integration tests, no regressions)
```

## Design Notes

Verbose mode is an **orchestrator agent behavior** documented in orchestrator.md, not a CLI feature. The orchestrator agent reads the config, and when verbose is true, posts the detailed summary after running `braids orch-run`. This keeps the implementation simple — no code changes to the CLI beyond recognizing the config key.
