# Autocrafter output dirty-state accounting

## Real-server baseline

A production `slimefun tick top` sample over ten Slimefun ticks recorded
400 `NTW_AUTO_CRAFTER` entries totalling **32.31 ms** (0.08 ms mean,
0.37 ms P95; 2.21 ms max at `world -1300,63,801`).
This figure measures aggregate ticker work, **not** whole-server MSPT.
It does not reveal how many machines successfully crafted versus idled.

## Source finding

`AutoCrafter.tryCraft` calls `BlockMenuUtil.pushItem` to commit its
output. That helper already writes the changed slot via
`BlockMenu.replaceExistingItem`, and the maintained Slimefun Legacy
`DirtyChestMenu` records a persistence change for each such replacement.

The crafter then called `blockMenu.markDirty()` **again** regardless of
whether any output actually entered the destination. This counted a single
physical output write twice and marked fully rejected insertions dirty.

## Narrow correction

`AutoCrafter.commitCraftedOutput` delegates the insertion to the same
`BlockMenuUtil.pushItem` and returns the exact uninserted remainder for
the existing rollback path. It does not introduce an additional dirty mark.
The utility remains the sole owner of physical writes and persistence
invalidation. The helper preserves null/full/partial remainder semantics,
craft power charging, item amounts, recipe throughput, and template caching.

JUnit tests focus on accepted, partially accepted, entirely rejected and
empty/AIR outputs, including assertions that the crafter does not call
`markDirty` independently. The existing menu-insertion regressions still
cover actual physical persistence.

## Acceptance boundary

Require the exact commit's Legacy, United and Gugu builds and tests,
Paper 26.3 compile, Purpur 1.21.11 native storage probes and Paper
26.3 native build 176/194 persistence checks before considering merge.
Do not infer a measurable TPS improvement until matched-server samples
confirm it; this reduces duplicate bookkeeping only when a crafter
actually produces output.

No changes to item IDs, blueprint contents, Slimefun inventory APIs,
storage schema, recipe speeds, addon version or resource-pack files.
A separately staged 1.0.62 release-candidate PR is still pending; this
work must not automatically publish or silently alter its source pin.
