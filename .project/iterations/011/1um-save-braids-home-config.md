# Save braids-home in config file next to registry

## Summary

Added `config.edn` to `~/.openclaw/braids/` for persisting braids-home (and future config). The init command now writes config.edn alongside registry.edn, and all braids-home resolution reads from this config file with sensible defaults.

## Changes

### New files
- `src/braids/config.clj` — Pure functions: parse/serialize config EDN, defaults
- `src/braids/config_io.clj` — IO: load/save config.edn, resolve braids-home from config
- `spec/braids/config_spec.clj` — Tests for pure config functions
- `spec/braids/config_io_spec.clj` — Tests for config IO (temp dir integration)

### Modified files
- `src/braids/init.clj` — `plan-init` now includes `:save-config` action with config-path
- `src/braids/init_io.clj` — Executes `:save-config` action, accepts `config-path` override
- `src/braids/ready_io.clj` — `resolve-braids-home` now delegates to `config-io/resolve-braids-home`
- `src/braids/new_io.clj` — Uses `config-io/resolve-braids-home` instead of hardcoded default
- `spec/braids/init_spec.clj` — Updated to expect `:save-config` in plan
- `spec/braids/init_io_spec.clj` — Updated to verify config.edn creation and content

## Config format

```edn
{:braids-home "~/Projects"}
```

Default location: `~/.openclaw/braids/config.edn` (next to `registry.edn`)
