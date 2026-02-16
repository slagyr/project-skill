# Standardize iteration status format and add orchestrator format tolerance

## Summary

Fixed a bug where the orchestrator failed to detect active iterations when workers reformatted the ITERATION.md Status field using markdown bold (`- **Status:** active`) instead of plain text (`Status: active`). This caused 12+ hours of wasted idle backoff cycles.

## Changes

### Code: `src/braids/orch_io.clj`
- Extracted `parse-iteration-status` as a public function with a tolerant regex that handles:
  - Plain: `Status: active`
  - Markdown bold: `**Status:** active`
  - List + bold: `- **Status:** active`
  - Case variations
  - Extra whitespace
- `find-active-iteration` now uses `parse-iteration-status` instead of a rigid regex

### Tests: `spec/braids/orch_io_spec.clj` (new)
- 12 specs covering `parse-iteration-status` (8 cases) and `find-active-iteration` (4 cases)
- Tests plain format, markdown bold, list prefix, case insensitivity, missing status, and multi-iteration scanning

### Docs: `projects/references/worker.md`
- Added explicit format guidance: always use canonical plain-text `Status: active` (not markdown bold)
- Documented that non-standard formatting can cause orchestrator parsing issues

## Approach

Defense in depth: the orchestrator is now tolerant of format variations (so it won't break again), AND workers are instructed to use the canonical format (so the variation shouldn't happen).
