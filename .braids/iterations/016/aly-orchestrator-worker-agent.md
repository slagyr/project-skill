# Configure orchestrator to spawn scrapper agents (braids-aly)

## Problem

The orchestrator spawned generic sub-agents without specifying `agentId`, causing workers to lack braids protocol knowledge. Workers would complete technical work but skip protocol steps (bead-start notification, deliverable file, bead-complete notification).

## Changes

### 1. `src/braids/orch.clj` — Core logic

- **`tick` function**: Now reads `:worker-agent` from project config and includes it in spawn entries when present
- **`format-orch-run-json`**: Now outputs `agentId` field in spawn JSON when `:worker-agent` is set

### 2. `braids/references/orchestrator.md` — Documentation

- Updated JSON example to show `agentId` field
- Updated `sessions_spawn` call template to include `agentId` parameter

### 3. All project `config.edn` files

Added `:worker-agent "scrapper"` to all active projects:
- braids, zaap, zane-setup, wealth, cfii

## Design

- **Per-project configuration**: `:worker-agent` in `.braids/config.edn` controls which agent handles bead work
- **Optional field**: If absent, no `agentId` is emitted (backward compatible — generic agent spawned)
- **Flows through cleanly**: tick → spawn entry → format-orch-run-json → orchestrator → sessions_spawn

## Verification

- `braids orch-run` now outputs `"agentId": "scrapper"` in each spawn entry
- All existing orch specs pass (0 new failures)
