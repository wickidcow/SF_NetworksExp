# Networks 1.0.54 — Source Commit Integrity Candidate

This development release candidate is built on the published **Networks 1.0.53**. It does not change machine IDs, recipes, saved storage formats, transport priority, regular transfer amount limits, Java 21 runtime bytecode, or Minecraft **1.21.11+** support.

## Problem addressed

A network deposit offers a detached clone of a menu-slot, inventory-slot, cursor or stack-reference item. The network can accept part of the offer before the caller replaces the original source with a calculated remainder. The source may have changed after the offer, for example because an addon or callback rewrote that inventory slot. Blindly committing the remainder could overwrite that newer item.

## Proposed behavior

Before source-side commit, compare the **current quantity and complete item metadata** to the source observed before the deposit. If the source is no longer the same, leave the changed source alone and request compensation of the exact accepted quantity from the same network root. Existing `TransferAudit` tracks any compensation shortfall and emits a severe diagnostic.

When a menu/inventory/cursor setter itself fails, restore the previous source only if it still equals the old stack or the expected partially committed remainder; never overwrite a visibly unrelated replacement. The direct stack-reference helper similarly refuses to decrement a live stack that changed during the offer.

This is defense in depth, not a claim of global atomicity across third-party addons, server crashes, inventories and networks. If an addon makes a conflicting mutation *after* the re-read, another thread can still race the setter; correct region-thread scheduling and native interleaving tests remain requirements. A failed compensation attempt also still requires operator investigation.

## Automated checks

- Unit tests cover ordinary partial deposits and source-write failures already in 1.0.53, plus menu/inventory/cursor/main-hand source swaps, PDC metadata replacements, mutable-stack quantity changes and a setter that replaces the source before throwing.
- Required CI includes Slimefun Legacy, United and Gugu build tests, strict Java 21 bytecode, universal JAR checks, stable Paper 26.3 compile smoke and native Purpur 1.21.11 manager/display smoke.
- The successful unversioned candidate CI is not evidence for this new 1.0.54 versioned head; this revision has to pass independently.

## Release gate

1. Review the source-compensation semantics and confirm that partial commits cannot overwrite externally changed stacks.
2. Verify 1.0.54 CI and test custom PDC items with Quantum Storage, Fluffy Barrels, InfinityExpansion2 and player inventories on a disposable server.
3. Test failure compensation and log handling without deleting original items on partial recovery.
4. Back up a real old-world dataset and verify original quantity, item identity and correct restart persistence with a matching core and addon set.
5. After a separately approved merge, verify the raw `SF_Networks1.0.54.jar` release by digest and metadata. The existing `v1.0.53` release must not be overwritten.
6. Only then prepare a targeted, deterministic successor to Slimefun Legacy's published `r134` 45-addon bundle. No production server is changed by this draft.

This candidate intentionally does **not** upgrade the already-published Slimefun Legacy 4.1.73 JAR, resource pack or addon ZIP.
