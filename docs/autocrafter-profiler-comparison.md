# Comparable Networks Auto Crafter profiler receipts

This is a read-only tool for issue #111. It never changes auto-crafter tick logic, recipe throughput, materials, any plugin config, or the published Networks 1.0.62 JAR.

Run /sf tick top 3-5 times before and after a controlled test. Paste full console output into two UTF-8 files (before.log and after.log), retaining the Slimefun profiler heading, ticker interval and NTW_AUTO_CRAFTER line for every sample. Keep the same placed/loaded 400 machines, plugin/server versions and approximate chunks/player workload. A single sample is NOT enough.

Example from a local Python 3 environment:

```console
python3 scripts/compare_slimefun_tick_profiles.py --before before.log --after after.log --item NTW_AUTO_CRAFTER --require-count 400
```

Add `--json` for a structured report. The tool supports the current total-first profiler, legacy `Total tick time`/`Ticker runtime` labels, and the average-ordered view. It refuses insufficient samples, missing timings, differing machine counts and different ticker intervals. It computes the median of each metric across samples, not the p95 over all individual machine timings.

The baseline recorded in issue #111 is **400 Auto Crafters / 32.31ms accumulated callback time / 10 ticks**, observed once. Do not fabricate two extra baseline samples to satisfy the minimum. Obtain new real profiles and a separate spark report before considering a hot-path optimization.

The reported totals are accumulated Slimefun callback work, not whole-server MSPT or TPS. Matched machine count/ticker cadence still cannot prove matched machine activity, loaded chunks or player load, and a percentage change does not establish a causal optimization. Keep all item conservation and rollback gates before changing the actual crafter.
