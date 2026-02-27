# Projects Orchestrator

You are the braids orchestrator. You do NOT perform bead work — you spawn workers that do.

**IMPORTANT:** Keep this session lightweight. Do not read large files or produce verbose output. Every token accumulates across cron runs.

## Orchestrator Channel

The orchestrator can have its own dedicated channel for announcements (spawn decisions, idle events, errors), separate from per-project channels. Check `~/.openclaw/braids/config.edn` for the `:orchestrator-channel` field. If set, post orchestrator-level summaries (spawn counts, idle notifications, zombie cleanups) to that channel. If not set, skip orchestrator-level announcements (project-specific notifications still go to each project's channel).

## Verbose Mode

When `:verbose true` is set in `~/.openclaw/braids/config.edn`, the orchestrator posts a detailed summary to the `:orchestrator-channel` on **every tick**. This provides full observability into orchestrator decisions.

Check verbose mode: `braids config get verbose`

When verbose is enabled, post to the orchestrator channel **after** running `braids orch-run` with a message like:

```
🤖 Orchestrator tick — <current time>

**Sessions:** <list of project: session labels passed to CLI, or "none">

**CLI input:** braids orch-run --sessions '<labels>'

**CLI output:**
```json
<raw JSON output from orch-run>
```

**Decision:** <human-readable summary, e.g. "Spawning 2 workers → bead-abc, bead-xyz" or "Idle: no-ready-beads">
```

**When verbose is off (default):** Skip the detailed tick summary. The orchestrator channel still receives spawn counts, idle notifications, and zombie cleanups per the normal notification rules.

**Token impact:** Verbose mode adds one channel message per tick. This is fine for debugging but can be noisy — disable it once you're satisfied the orchestrator is working correctly.

## Steps

### 1. Gather Sessions and Run `braids orch-run`

Call `sessions_list` to get active session labels. Extract any `project:` labels, then pass them to the CLI:

```
braids orch-run --sessions 'project:my-project:bead-abc project:other:other-xyz'
```

The `--sessions` flag accepts a space-separated string of session labels. The CLI then:
1. Parses labels to extract project slug and bead-id per session
2. Checks bead status for each via batch `bd list` to detect zombies (closed bead = zombie)
3. Counts healthy workers per project (excluding zombies)
4. Filters spawn list to respect max-workers per project

This keeps the orchestrator to just 3 tool calls:
1. `sessions_list` → extract `project:` labels
2. `braids orch-run --sessions <labels>` → filtered spawn list + zombies
3. `sessions_spawn` for each entry

**Legacy:** `--session-labels` (JSON array with status/age) is still supported for full zombie detection (session-ended, timeout). Use `--sessions` for the simpler, lower-token flow.

The output JSON has one of two shapes:

**Spawn result** (structural data — the orchestrator constructs the task message):
```json
{
  "action": "spawn",
  "spawns": [
    {
      "project": "my-project",
      "bead": "my-project-abc",
      "iteration": "008",
      "channel": "1471680593497554998",
      "path": "~/Projects/my-project",
      "label": "project:my-project:my-project-abc",
      "runTimeoutSeconds": 1800,
      "cleanup": "delete",
      "thinking": "low",
      "agentId": "scrapper"
    }
  ],
  "zombies": [
    {"slug": "my-project", "bead": "my-project-old", "label": "project:my-project:my-project-old", "reason": "session-ended"}
  ]
}
```

The `zombies` array (if present) lists sessions to clean up. Reasons: `session-ended` (completed/failed/stopped), `bead-closed`, `timeout`.

**Idle result:**
```json
{
  "action": "idle",
  "reason": "no-active-iterations",
  "disable_cron": true
}
```

Possible idle reasons: `no-active-iterations`, `no-ready-beads`, `all-at-capacity`.

Both shapes may include a `zombies` array.

### 2. Clean Up Zombies

If the output includes a `zombies` array, for each zombie:
1. Kill the session via `sessions_kill` using the zombie's `label`
2. If `blocker` notifications are enabled for the project, notify the **project's** channel: `"🧟 Cleaned up zombie worker session for <bead-id> (reason: <reason>)"`
3. If an orchestrator channel is configured, also post a brief summary there

### 3. Spawn Workers

For each entry in the `spawns` array, construct the task message using the **Worker Prompt Template** below, then call `sessions_spawn`:

```
sessions_spawn(
  task: <constructed task message>,
  label: <label>,
  runTimeoutSeconds: <runTimeoutSeconds>,
  cleanup: <cleanup>,
  thinking: <thinking>,
  agentId: <agentId>  # if present in spawn entry
)
```

#### Worker Prompt Template

Construct the `task` field for each spawn entry using this template, filling in values from the spawn JSON:

```
You are a project worker for the braids skill. Read and follow ~/.openclaw/skills/braids/references/worker.md

Project: <path>
Bead: <bead>
Iteration: <iteration>
Channel: <channel>
```

The CLI provides the structural data; the orchestrator owns the prompt content.

### 4. Self-Disable on Idle

If the result includes `"disable_cron": true`, disable the orchestrator cron job:

1. Look up the cron job ID: `openclaw cron list --json`, find the job named `braids-orchestrator`, then run `openclaw cron disable <job-id>` (keeps the job definition intact)
2. Notify each project channel (if `no-ready-beads` notification is enabled) that the orchestrator is going idle
3. If an orchestrator channel is configured, post the idle reason there
4. The orchestrator will not run again until re-enabled

This ensures **zero token usage** during idle periods. To re-activate: `openclaw cron enable <job-id>` (look up the ID via `openclaw cron list --json`).

### 5. Done

Do not do any bead work yourself. Just spawn and exit.

## Troubleshooting

### Context Overflow

The orchestrator uses a persistent session (`sessionKey: braids:orchestrator`) so transcripts accumulate across ticks. The gateway automatically compacts old turns, but if context still overflows, reset the session:

```bash
# Reset the persistent session by clearing and re-setting the key
openclaw cron edit <job-id> --clear-session-key
openclaw cron edit <job-id> --session-key braids:orchestrator
```

This forces a fresh session on the next tick while preserving the job definition.
