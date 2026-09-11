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

Status: **in progress — Phase 1A implemented; Phase 1B prototype compiling on all supported cores**.

### P0 findings

- **Controller steady-state topology work:** the audit branch now reuses an unchanged `NetworkRoot` instead of allocating and copying the entire node tree every controller tick. A real topology discovery still occurs when the root is missing, topology is marked dirty, the node limit changes, or record-flow configuration changes. Stable ticks explicitly re-sum live power-node charge, refresh monitor-backed storage views, refresh particle state, and still fire `NetworkRootReadyEvent`. This removes the O(network size) per-tick object-graph copy from the normal steady-state path while keeping the dirty rebuild path intact.
- **Line-transfer grab routing:** the audit branch removes the duplicate safe-slot preflight from Expansion line-transfer grabbers. `LineOperationUtil.grabItem` remains the single owner of transport-slot discovery, so the same transport-mode and quantity logic runs with one fewer potentially expensive destination query.
- **Repeated transport misses:** normal Grabber, Import, Export, and Expansion transfer variants can retry misses frequently. These remain under review; no broad cooldown will be added unless source/destination changes can invalidate it promptly enough to avoid changing successful automation behavior.

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

**Prototype implemented on the audit branch.** Stable controller ticks reuse the existing root instead of rebuilding/copying every node. The root reuse counter is tracked separately from true topology rebuilds. Dynamic power, storage views, crayon particle state, record-flow changes, dirty topology, controller limits, and `NetworkRootReadyEvent` remain explicitly accounted for.

Before this phase is considered merge-ready, stage it on a mature server and verify:

- A large unchanged network continues crafting, pushing, grabbing, importing, exporting, and displaying power normally.
- Adding/removing a cable or machine causes the affected controller to rebuild and immediately reflects the new topology.
- Breaking/replacing a controller or unloading/reloading its chunks leaves no stale runtime root.
- Power-node charge is accurate before and after machine consumption.
- Empty monitored storage becoming non-empty, and non-empty storage becoming empty, is reflected without stale item views.
- Record-flow enable/disable and crayon particle toggles still take effect.
- Optional storage integrations still receive their locate/root-ready events.
- Spark and Slimefun profiler captures show controller work scaling with changed state rather than total network size on every stable tick.

### Phase 1C — core Grabber / Import / Export misses

**Next after the stable-root runtime check.** Measure and reduce repeated misses without delaying successful work. Any cooldown must be request-specific and must be cleared immediately when the relevant source/destination state changes or succeeds.

### Phase 2 — storage lookup and grid/crafting paths

Profile network item aggregation, storage adapter discovery, crafting-grid search, remote/grid refresh, and item similarity work under large item counts and multiple viewers. Prefer versioned/dirty snapshots over repeated complete aggregation.

### Phase 3 — Expansion machines

Audit Auto Crafters, Line Transfers, managers, drawers, advanced transport, wireless components, and utility machines for duplicate tick callbacks, unnecessary scans, unbounded maps, and repeated metadata/database calls.

### Phase 4 — lifecycle and soak testing

Exercise restarts, chunk churn, block break/replacement, old-world data, controller failures, and long-running cache cleanup. Add stress/regression tests for bounded retained state and item conservation.

## Release policy

Audit work should remain on a non-release branch until a complete slice passes compatibility and scale checks. Public version bumps should package one reviewed slice at a time with before/after profiler evidence and no intentional gameplay changes.
