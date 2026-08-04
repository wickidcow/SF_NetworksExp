# Networks Legacy Compatibility Contract

## Release

`2.1.112-Legacy-Alpha2`

## Preserved world and addon identity

Alpha 2 retains:

- Bukkit plugin name: `Networks`
- Main class: `io.github.sefiraat.networks.Networks`
- Required plugin name: `Slimefun`
- Existing Java package names
- All 288 item localization IDs from NetworksExpansion 2.1.111
- Existing Networks and historical fork persistent-data namespaces
- Existing `CargoStorageUnits.db` file and core tables
- Existing configuration keys, with additive compatibility/doctor/database options

The database startup migration only merges duplicate `(ContainerID, ItemID)` rows by summing their amounts and then adds a unique index. No database file rename or intentional item-ID migration is performed.

## Three-core build contract

The same source is compiled in GitHub Actions against exact JARs built from:

1. `wickidcow/Slimefun-Legacy` `master` — primary
2. `Slimefun-United/Slimefun-United` `dev`
3. `SlimefunGuguProject/Slimefun4` `master`

Build requirements:

- Paper API baseline: `1.21.11-R0.1-SNAPSHOT`
- Java source and bytecode target: 21
- Runtime floor: Java 21 and Minecraft 1.21.11
- Exact dependency injection through `-PslimefunCoreJar=/path/to/Slimefun.jar`
- No bundled Slimefun core
- Automatic JAR replacement disabled

## Runtime hardening

- Network nodes and chunk indexes use concurrent registries and normalized block locations.
- Breaking a Networks block clears its runtime node/controller state.
- Chunk unload removes only that chunk's loaded runtime entries.
- Doctor can rebuild a mismatched chunk index without force-loading worlds.
- Machine tickers use synchronized execution by default.
- Bukkit inventory, world, menu, and particle access no longer uses Bukkit asynchronous scheduler calls.
- Drawer reads and writes are ordered through one database worker.
- Shutdown drains queued storage writes within a configured deadline and never deliberately closes SQLite while its worker is still executing.
- Blueprint decoding accepts old array formats and new per-slot formats; malformed serialized items fail closed as invalid blueprints.
- Skull matching compares cached player profiles locally rather than requesting an `OfflinePlayer` profile.
- Inventory-to-network moves use clone-and-commit semantics and explicitly update the source menu slot, player slot, cursor, or held item.
- Control X rejects inventory-bearing block states before insertion into the network.
- Cached nodes are lazily invalidated when their physical Slimefun item or node type no longer matches.
- Quantum Storage withdrawals update the cache before synchronizing persistent block data.
- Crafting grids bind one exact recipe matrix to one output, consume the complete matrix before output creation, restore reserved ingredients after cancellation/failure, and stop repeated crafting when refills or output space run out.

## Doctor commands

```text
/networks doctor status
/networks doctor scan
/networks doctor repair confirm
```

On Slimefun Legacy 4.1.17+, Networks also registers reflectively with:

```text
/sf doctor addons status
/sf doctor addons scan
/sf doctor addons repair confirm
```

The reflective bridge is absent at class-link time, allowing the same JAR to load on United and Gugu.

## Required staging checks

1. Back up the complete server and Slimefun/Networks data.
2. Start with no players online and check the database migration log.
3. Run `/networks doctor scan`.
4. Verify existing Controllers discover their loaded nodes.
5. Open existing Grids and Crafting Grids.
6. Verify Drawers and Quantum Storage retain exact item types and amounts.
7. Test Importers, Exporters, Pushers, Grabbers, Vacuums, and Cargo interaction.
8. Test old and newly encoded blueprints plus every automatic crafter type used by the server.
9. Test wireless and P2P links across chunk unload/reload.
10. Break and replace test nodes, then confirm no ghost entries remain.
11. Stop normally, start again, and repeat the amount/blueprint checks.

Folia remains unclaimed because safe individual ticker dispatch does not by itself make a multi-chunk transactional network region-safe.
