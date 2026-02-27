# Fix orchestrator self-disable: stay enabled while active iterations exist (braids-q6p)

## Summary

Fixed the orchestrator `tick` function so `disable_cron` is only `true` when the idle reason is `no-active-iterations`. Previously, both `no-ready-beads` and `all-at-capacity` also set `disable_cron: true`, which caused the orchestrator to disable its cron job even when active iterations existed (beads could become unblocked later).

## Changes

- **`src/braids/orch.clj`**: Changed `:disable-cron true` to `:disable-cron false` for `no-ready-beads` and `all-at-capacity` idle reasons
- **`CONTRACTS.md`**: Updated §1.5 to document that only `no-active-iterations` triggers `disable_cron: true`
- **`spec/braids/orch_spec.clj`**: Updated 2 tests to assert `disable-cron false` for those cases
- **`spec/simulation_spec.clj`**: Updated Scenario 4 test similarly

## Verification

```
$ bb -cp src:spec -e '...(no-ready-beads)...'
{"action":"idle","reason":"no-ready-beads","disable_cron":false}

$ bb -cp src:spec -e '...(no-active-iterations)...'
{"action":"idle","reason":"no-active-iterations","disable_cron":true}

$ bb test → 437 examples, 10 failures (pre-existing), 0 new failures
```
