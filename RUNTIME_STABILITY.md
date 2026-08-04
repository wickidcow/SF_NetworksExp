# Networks Runtime Stability Pack

## Scope

This update keeps the public release at `2.1.112-Legacy-Alpha2` so the existing Slimefun Legacy compatibility target remains exact. It is a runtime-hardening pack, not an item-ID, plugin-name, database-path, or world-format migration.

Slimefun Legacy is the primary and release-blocking core. The same source is also compiled against Slimefun United and Slimefun Gugu to catch API drift, but the universal release JAR is produced from the Slimefun Legacy lane.

## Preserved data contract

- Bukkit plugin name: `Networks`
- Main class and Java package names
- All 288 Slimefun item IDs
- Existing persistent-data namespaces and keys
- Existing `CargoStorageUnits.db` file and tables
- Existing placed blocks, controllers, grids, links, blueprints, drawers, and quantum storage records
- Existing configuration keys, with only additive compatibility and diagnostic settings

## Storage and transfer rules

Every movement path should follow a reserve, verify, commit, and rollback model:

1. Clone or reserve the source stack without destroying the original record.
2. Ask the destination how much it can really accept.
3. Verify the destination contains the expected item and amount.
4. Commit only the accepted amount to the source.
5. Restore any remainder to its source or a safe fallback inventory/world drop.

The hardened paths cover menu slots, Bukkit inventories, player inventory slots, cursors, held items, Networks storage, quantum storage, crafting interfaces, cargo pushers/grabbers, importers, exporters, vacuums, and LogiTech linker reservations.

## Slimefun cargo compatibility

`BlockMenuUtil.getSafeTransportSlots` is the compatibility boundary for Slimefun machine menus. It:

- Prefers the item-aware transport-slot overload.
- Falls back to the legacy one-argument overload.
- Handles missing/unsupported overloads without crashing a network tick.
- Rejects invalid and duplicate slot indexes.
- Is used by network roots, greedy storage, pushers, grabbers, offsetters, drawers/barrels, LogiTech integration, and other cargo-facing paths.

This keeps Networks compatible with the menu implementations exposed by Slimefun Legacy while retaining compile verification against United and Gugu.

## Runtime hardening included

- Synchronized machine tickers by default for Bukkit/Paper world and inventory ownership.
- Correct synchronous/asynchronous mode for `NetworkRootReadyEvent` based on the actual firing thread.
- Stale controller records are rejected when the world block is now air or another Slimefun item.
- Network Remote revalidates the chunk, live Slimefun item, protection permission, block data, and menu after deferred loading.
- Network and drawer hot-path caches use normalized locations and concurrency-safe collections.
- Reverse accessor references are removed when nodes break or unload.
- Vanilla pushers allow verified partial insertion and reject Crafter inventories.
- Vanilla grabbers clone into the Networks menu, verify the exact destination stack, and only then clear the source slot.
- Quantum Storage updates amounts before synchronizing persistent block data and can restore failed output remainders without truncation.
- Crafting grids and auto-crafters bind exact input matrices to exact outputs, consume atomically, and restore reserved ingredients after failure or cancellation.
- SQLite drawer operations are ordered through one worker, with duplicate-row migration, uniqueness enforcement, bounded shutdown, and counter recovery.
- `/networks doctor status|scan|repair confirm` scans loaded state without force-loading chunks.
- Reflective Slimefun Legacy Doctor integration avoids hard-linking Legacy-only classes on United or Gugu.

## Required server validation

Use a copy of the production server and preserve the original world and plugin data until all checks pass.

1. Back up the worlds, `/plugins/Networks`, `/plugins/Slimefun`, and `CargoStorageUnits.db`.
2. Replace the Networks JAR and perform a complete server restart. Do not use `/reload`.
3. Confirm startup identifies Slimefun Legacy and reports no Networks error files.
4. Run `/networks doctor status` and `/networks doctor scan`.
5. Open old and new Controllers, Grids, Crafting Grids, and Network Remotes.
6. Deposit and withdraw stackable, unstackable, damaged, enchanted, potion, shield, skull, bundle, and Slimefun items.
7. Verify exact counts through Drawers, Quantum Storage, cells, barrels, importers, exporters, pushers, grabbers, vacuums, and wireless/P2P links.
8. Connect Slimefun cargo nodes to Networks machines and to machines from installed addons. Test both insert and withdraw directions, full inventories, partial stacks, filtered slots, and rejected items.
9. Test furnaces, brewing stands, ordinary containers, and unsupported Crafter inventories with vanilla pushers/grabbers.
10. Test every blueprint and automatic crafting machine used by the server, including cancellation, no-output-space, and missing-ingredient cases.
11. Unload and reload chunks containing controllers, storage nodes, cargo endpoints, grids, and remotes.
12. Stop normally, restart, and compare all stored amounts and item metadata with the pre-restart values.
13. Break and replace disposable test nodes, then rerun Doctor to confirm no ghost runtime entries remain.

## Build gates

- `Build Networks Universal`: builds and tests the release JAR against Slimefun Legacy.
- `Networks Compatibility`: compiles the same source against Legacy, United, and Gugu.
- `scripts/verify_legacy_compatibility.py`: verifies identity, 288 item IDs, Java/Paper floors, exact-core workflows, storage/cargo/crafting invariants, remote safety, and Doctor integration.
- `scripts/verify_java21_bytecode.py`: rejects class files above the Java 21 bytecode level.

Folia support is not claimed. A multi-chunk network transaction can cross region ownership boundaries and needs a separate scheduler and transactional design audit.
