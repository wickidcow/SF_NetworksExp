# Vanilla Pusher ordinary-inventory transaction safety

## Problem

The ordinary InventoryHolder path previously delegated the live pusher source stack to `InventoryUtil.addItem`.
That helper reduces the source while building destination contents and calls `setStorageContents` only afterward.
If a third-party inventory implementation rejects, normalizes, partially applies, or throws from that setter, the
pusher can no longer prove that the source reduction and destination mutation describe one conserved transaction.

## Change

- Preserve the historical insertion order: fill all compatible partial stacks, then empty storage slots.
- Build both the baseline and desired destination states from detached exact clones.
- Give the destination a second detached write array, so in-place normalization cannot redefine expected readback.
- Require exact destination readback before reducing the live pusher source by the accepted amount.
- On write/read mismatch, restore the detached baseline before preserving the source.
- If rollback remains unresolved but destination contents are readable, calculate the amount actually proven to
  survive relative to the immutable baseline and preserve the complementary source remainder.
- If destination state is unreadable or contains unexplained changes, fall back conservatively to the attempted
  amount rather than knowingly retaining a duplicate.
- Keep furnace/brewing special-slot handling in the preceding focused transaction helper.

No inventory routing, slot preference, stack limit, item identity, recipe behavior, transfer rate, permissions, saved
data, or WildChests exclusion changes are included.

## Validation boundary

Focused tests cover partial-stack then empty-slot ordering, write-then-throw reconciliation, destination
normalization with successful rollback, readable rollback refusal with exact item conservation, surviving-insert
accounting across partial/empty slots, and no-capacity no-op.
The full Legacy/United/Gugu, Java 21 universal-JAR, Paper 26.3 and native/restart workflows remain required.
