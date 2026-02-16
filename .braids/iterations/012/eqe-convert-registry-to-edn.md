# Convert registry.md to registry.edn

## Summary

Removed the markdown registry fallback so the CLI reads/writes only `registry.edn`. No markdown fallback remains.

## Changes

### Source
- **`src/braids/ready_io.clj`**: Removed `registry.md` fallback from `load-registry`. Now only reads `registry.edn`; returns `{:projects []}` if missing.

### Tests
- **`spec/braids/ready_io_spec.clj`**: Added tests: "does not fall back to registry.md" and "returns empty projects when no registry.edn exists"
- **`spec/structural_spec.clj`**: Rewritten to parse `registry.edn` as EDN instead of markdown table
- **`spec/integration_smoke_spec.clj`**: Updated to read `registry.edn` and iterate EDN projects
- **`spec/simulation_spec.clj`**: Setup writes `registry.edn` instead of `registry.md`; Scenario 9 validates EDN format
- **`spec/contracts_spec.clj`**: Updated file format check from `registry.md` to `registry.edn`

### Docs
- **`CONTRACTS.md`**: Section 1.1 updated from `registry.md` to `registry.edn` with EDN-specific format documentation

### Cleanup
- `~/.openclaw/braids/registry.md` moved to `.bak` (trashed)

## Test Results

371 examples, 10 failures (all pre-existing — wealth/.beads, zaap state, stale registry slugs, install.sh/homebrew repo rename). No new failures introduced.
