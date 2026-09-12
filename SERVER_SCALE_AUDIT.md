# Networks Server-Scale and Original-Behavior Audit

## Goal

Make Networks safe and predictable on old, heavily developed servers with many concurrent players and very large automation networks, while preserving the gameplay and data contract of the original Networks and bundled Networks Expansion behavior.

This audit treats performance work as an implementation change, not a gameplay redesign. A server owner should be able to replace the older plugin with this fork without rebuilding bases, changing recipes, reconfiguring directions, migrating stored items, or accepting different transfer semantics.

## Original behavior contract

The following are compatibility requirements unless a documented bug fix explicitly requires otherwise:

- Keep the plugin identity `Networks` and the existing main class.
- Preserve registered Slimefun item IDs, recipes, namespaces, placed-block data, controller/network relationships, and existing database formats/paths.
- Preserve transfer direction, configured tick rate, transport mode, quantity limits, storage priority, successful-transfer throughput, and item conservation.
- Preserve controller limits and the historical rule that a connected network has one effective controller.
- Preserve GUI slot behavior and item transport exposure.
- Preserve optional addon integrations without making them hard dependencies.
- Never trade correctness for TPS: no duplication, silent loss, stale-network withdrawals, or unsafe inventory mutation.

The maintained compatibility baseline remains `compatibility/item-ids-2.1.111.txt`, with source behavior compared against Sefiraat/Networks and ytdd9527/NetworksExpansion where applicable.

## Large-server acceptance

Every performance slice should be checked against workloads that resemble mature production servers rather than empty test worlds:

- Multiple simultaneously loaded networks, including networks with thousands of nodes.
- Hundreds or thousands of active transport machines spread across player bases.
- Large item diversity and deep storage with both successful requests and frequent misses.
- Full or temporarily rejecting destinations, including item-aware Slimefun machines.
- Multiple players opening grids/crafting interfaces while transport continues.
- Chunk load/unload churn, restarts, old placed blocks, and topology edits in large networks.
- Cross-addon destinations such as Supreme, Infinity Expansion 2, DynaTech, FluffyMachines, and standard Slimefun inventories.

A change is not considered complete merely because idle TPS remains 20. The important properties are bounded worst-case work, cheap failed operations, cleanup of retained state, and unchanged final item/automation results.

## Audit rules

1. Prefer dirty-state updates, indexing, reuse, bounded work, and failed-operation backoff over global rescans.
2. Do not move Bukkit, Slimefun inventory, block, entity, or world mutation off-thread merely to improve timings.
3. Avoid rebuilding collections or object graphs every tick when the underlying state is unchanged.
4. Avoid asking a destination the same expensive transport question multiple times during one operation.
5. Failed requests should become cheaper while a failure condition persists, but successful work must stay responsive.
6. Every static/per-block cache must have a lifecycle cleanup path for break, unload, disable, or bounded stale eviction.
7. Performance changes must pass the existing compatibility verifier and supported-core CI gates before release.

## Phase 1: topology and transport hot paths

Status: **in progress — Phase 1A implemented; Phase 1B runtime-tested; Phase 1C implementation in progress**.

### P0 findings

- **Controller steady-state topology work:** the audit branch now reuses an unchanged `NetworkRoot` instead of allocating and copying the entire node tree every controller tick. A real topology discovery still occurs when the root is missing, topology is marked dirty, the node limit changes, or record-flow configuration changes. Stable ticks explicitly re-sum live power-node charge, refresh monitor-backed storage views, refresh particle state, and still fire `NetworkRootReadyEvent`. This removes the O(network size) per-tick object-graph copy from the normal steady-state path while keeping the dirty rebuild path intact.
- **Line-transfer grab routing:** the audit branch removes the duplicate safe-slot preflight from Expansion line-transfer grabbers. `LineOperationUtil.grabItem` remains the single owner of transport-slot discovery, so the same transport-mode and quantity logic runs with one fewer potentially expensive destination query.
- **Repeated transport misses:** the root already contains the historical accessor-level miss limiter. Phase 1C now short-circuits Grabber, Import, Export, Advanced Import, and Advanced Export work while that same limiter is active, instead of layering a second cooldown with different gameplay timing.

### Existing protections confirmed

- Loaded network nodes are indexed globally and by chunk.
- Normal topology lookup avoids a Slimefun storage lookup for every graph edge.
- Topology changes and chunk unload paths can mark controllers dirty and discard stale runtime roots.
- Transfer utilities use reserve/commit/rollback semantics to avoid silent item loss.
- Controller rebuild failures have a circuit breaker.
- Doctor maintenance uses bounded rotating scans.
- The compatibility verifier protects plugin identity, item IDs, supported Slimefun core families, Java target, runtime safety, and transaction/storage invariants.

## Phase status

### Phase 1A — line-transfer duplicate routing

**Implemented on the audit branch.** Duplicate destination slot discovery was removed from normal and vanilla Expansion grab line transfers. The compatibility matrix passes against Slimefun Legacy, United, and Gugu.

### Phase 1B — controller steady-state root reuse

**Prototype implemented and runtime-tested on the audit branch.** Stable controller ticks reuse the existing root instead of rebuilding/copying every node. The root reuse counter is tracked separately from true topology rebuilds. Dynamic power, storage views, crayon particle state, record-flow changes, dirty topology, controller limits, and `NetworkRootReadyEvent` remain explicitly accounted for.

Runtime testing on a mature production-style setup showed no immediate regression while Pushers, MorePushers, Supreme machines, grids, and line transfer activity remained operational. Before merge, continue watching topology edits, chunk reloads, power accounting, and storage-view changes under normal use.

### Phase 1C — core Grabber / Import / Export misses

**Implementation in progress.** The original root miss limiter remains the sole recovery timer (`speed-down.transport-miss-threshold` and `speed-down.reduce-ms`). The audit branch now checks that existing state before expensive repeated work:

- Network Grabber avoids transport-slot discovery while its network-input accessor is already limited and stops scanning additional source slots if a miss crosses the threshold mid-tick.
- Network Import and Advanced Import preserve empty-inventory behavior, but stop scanning remaining occupied slots once the root is already rejecting that accessor.
- Network Export preserves template/output validation before checking the existing output limiter.
- Advanced Export preserves `NO_ITEM_REQUEST` for an empty template area, but stops processing additional templates once the existing output limiter becomes active.

No new cooldown duration, miss threshold, item ordering, transfer amount, tick rate, or success path is introduced by this phase.

### Phase 2 — storage lookup and grid/crafting paths

Profile network item aggregation, storage adapter discovery, crafting-grid search, remote/grid refresh, and item similarity work under large item counts and multiple viewers. Prefer versioned/dirty snapshots over repeated complete aggregation.

### Phase 3 — Expansion machines

Audit Auto Crafters, Line Transfers, managers, drawers, advanced transport, wireless components, and utility machines for duplicate tick callbacks, unnecessary scans, unbounded maps, and repeated metadata/database calls.

### Phase 4 — lifecycle and soak testing

Exercise restarts, chunk churn, block break/replacement, old-world data, controller failures, and long-running cache cleanup. Add stress/regression tests for bounded retained state and item conservation.

## Release policy

Audit work should remain on a non-release branch until a complete slice passes compatibility and scale checks. Public version bumps should package one reviewed slice at a time with before/after profiler evidence and no intentional gameplay changes.
