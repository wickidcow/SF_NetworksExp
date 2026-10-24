# Specified-quantity line-transfer capacity scan

## Profiling context

A real Slimefun `tick top` sample measured 1,818 machines across 10 ticks, including
13 `NTW_EXPANSION_LINE_TRANSFER_PUSHER` invocations totalling 4.46 ms
(0.34 ms per invocation; P95 1.2 ms). This motivated examining Expansion's
transfer hot path. The sample does **not** identify the machines' configured
transport modes, so it cannot prove the SPECIFIED_QUANTITY path caused those costs.

The separate `NTW_AUTO_CRAFTER` machine is from upstream Sefiraat/Networks, not
this maintained Networks Expansion transfer implementation.

## Narrow optimization

`LineOperationUtil.pushItemInternal` previously read every physical destination
slot twice when a `SPECIFIED_QUANTITY` target needed more items:

1. Count existing matching items and stop if the quota is already satisfied.
2. Read the same slot list again to calculate matching-stack and empty-slot
   capacity before withdrawing exactly the deficit.

The updated path collects both counts during **one traversal**, matching each
nonempty stack no more than once and retaining the early exit when the target
already holds the requested quantity. All other transport modes, template
rotation, per-pass source-miss reuse, power costs, output receipts and
persistence handling remain unchanged.

## Test gates

`LineOperationUtilTest` verifies:
- One physical slot read and one metadata comparison per nonempty slot for an
  unsatisfied target, while preserving the exact deficit request.
- A nearly full matching stack only requests its one remaining slot of capacity.
- A zero target quantity never triggers a network withdrawal.
- Existing satisfied-target early-exit and transfer transaction tests still pass.

Require the universal-JAR build, Java unit tests, Paper/Purpur/Leaf/Folia compatibility
and native runtime probes before a release. Compare like-for-like `tick top`
samples before claiming any runtime speedup. This change does not alter the
version, addon release assets or serialized storage schemas.
