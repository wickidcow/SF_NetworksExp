# Vanilla Grabber two-sided transaction safety

## Problem

The Vanilla Grabber wrote a clone into its Networks output and then cleared the vanilla inventory source slot without
checking whether the source removal succeeded. A third-party inventory setter can reject, normalize, partially apply,
or throw after applying a write. In the source-removal failure case the destination could remain committed while the
vanilla source remained present, creating a duplicate boundary.

The destination readback also compared against the same mutable transfer object passed to the output setter, allowing
in-place destination normalization to redefine the expected commit identity.

## Change

- Snapshot the source with an exact detached clone.
- Give the grabber output a second detached transfer object.
- Require exact output readback before clearing the vanilla source.
- Treat source setters that remove then throw as successful after readback confirms the source is empty.
- If source removal is not verified, roll the output back before preserving/restoring the source.
- If output rollback cannot be verified, retry source removal and verify it so the same logical stack is not knowingly
  retained on both sides.
- Log unresolved reconciliation at SEVERE without dumping item metadata.

No furnace/brewing selection logic, potion filtering, source-slot order, transfer rate, item IDs, storage formats,
permissions or WildChests behavior changes.

## Validation boundary

Focused tests cover exact success, destination write-then-throw, source remove-then-throw, rejected source removal,
in-place destination normalization, rollback refusal with conservative source removal, and aliased clone rejection.
Full three-core, Java 21 universal-JAR, Paper 26.3 and native/restart gates remain required before merge.
