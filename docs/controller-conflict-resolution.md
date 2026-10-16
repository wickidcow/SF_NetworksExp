# Deterministic multi-controller conflict handling

## Problem

Normal Networks placement prevents two controllers from joining one topology. Invalid states can still be created by
older data, administrative/world-edit operations, unusual placement paths, or concurrent runtime registration.

Topology discovery historically handled an extra controller by scheduling that discovered controller for removal.
If two connected controllers rebuilt in the same cycle, controller A could schedule B while B scheduled A. The
existing pending-removal set prevented one controller from being dropped twice, but it did not prevent both distinct
controllers from being removed.

A delayed removal could also execute after the winner was broken or the connecting topology had already changed.

## Change

- Both discovery directions use one deterministic block-coordinate ordering and therefore select the same loser.
- A controller selected for removal stops publishing a temporary NetworkRoot while the removal is pending.
- Multiple discovery edges and both tickers still coalesce onto one pending removal.
- The delayed server-thread removal rechecks that both controllers still exist and remain connected in the loaded
  node registry before changing block/database state.
- If the survivor disappeared or the connection changed, deletion is cancelled rather than guessing.
- The surviving controller is marked dirty so its next rebuild owns a clean set of runtime assignments.

This affects only already-invalid multi-controller topologies. Normal controller/cable placement rejection is
unchanged. Item IDs, recipes, transfer order, storage data, controller limits, and ordinary single-controller
networks are unchanged.

## Validation boundary

Unit coverage asserts order-independent loser selection, coordinate tie-breaking, block-location normalization, and
same-block rejection. Existing project compatibility/native runtime workflows remain required before merge. A green
unit test is not a claim that arbitrary WorldEdit/plugin mutations or live server timing have been reproduced.
