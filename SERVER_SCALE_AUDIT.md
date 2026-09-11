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

Status: **in progress**.

### P0 findings

- **Controller steady-state topology work:** the maintained controller no longer performs original full neighbor discovery every stable tick, but it still creates a fresh `NetworkRoot` and copies the entire cached node tree on every controller tick. This remains O(network size) object allocation and registry work for an unchanged network. The original implementation rebuilt the entire graph every tick, so the fork is already safer, but giant networks still pay a large steady-state cost. This needs a behavior-preserving stable-root strategy with explicit dynamic-state refresh for power, storage views, record-flow state, particles, and root-ready event compatibility.
- **Line-transfer grab routing:** `AbstractTransfer` currently checks whether a target has a withdrawable item by asking for safe transport slots, then `LineOperationUtil.grabItem` asks for the same safe transport slots again. Item-aware destinations can make that callback expensive. Phase 1 removes the duplicate query while retaining the same transfer modes and empty-inventory behavior.
- **Repeated transport misses:** normal Grabber, Import, Export, and Expansion transfer variants can retry misses frequently. These will be reviewed for bounded/adaptive miss handling after parity tests establish the exact original successful-transfer semantics.

### Existing protections confirmed

- Loaded network nodes are indexed globally and by chunk.
- Normal topology lookup avoids a Slimefun storage lookup for every graph edge.
- Topology changes and chunk unload paths can mark controllers dirty and discard stale runtime roots.
- Transfer utilities use reserve/commit/rollback semantics to avoid silent item loss.
- Controller rebuild failures have a circuit breaker.
- Doctor maintenance uses bounded rotating scans.
- The compatibility verifier protects plugin identity, item IDs, supported Slimefun core families, Java target, runtime safety, and transaction/storage invariants.

## Planned phases

### Phase 1A — line-transfer duplicate routing

Remove duplicate destination slot-discovery work from Expansion grab line transfers. Verify all transport modes retain their original final inventory result.

### Phase 1B — controller steady-state root reuse

Prototype a stable-root path on an audit branch. A controller with unchanged topology should not recreate every node each tick. Dynamic values that historically refreshed through reconstruction must be refreshed explicitly. Add counters for reused roots versus topology rebuilds and retain a fallback to full discovery whenever invariants are uncertain.

### Phase 1C — core Grabber / Import / Export misses

Measure and reduce repeated misses without delaying successful work. Any cooldown must be request-specific and must be cleared immediately when the relevant source/destination state changes or succeeds.

### Phase 2 — storage lookup and grid/crafting paths

Profile network item aggregation, storage adapter discovery, crafting-grid search, remote/grid refresh, and item similarity work under large item counts and multiple viewers. Prefer versioned/dirty snapshots over repeated complete aggregation.

### Phase 3 — Expansion machines

Audit Auto Crafters, Line Transfers, managers, drawers, advanced transport, wireless components, and utility machines for duplicate tick callbacks, unnecessary scans, unbounded maps, and repeated metadata/database calls.

### Phase 4 — lifecycle and soak testing

Exercise restarts, chunk churn, block break/replacement, old-world data, controller failures, and long-running cache cleanup. Add stress/regression tests for bounded retained state and item conservation.

## Release policy

Audit work should remain on a non-release branch until a complete slice passes compatibility and scale checks. Public version bumps should package one reviewed slice at a time with before/after profiler evidence and no intentional gameplay changes.
