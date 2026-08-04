# Networks Legacy

**Networks Legacy** is an English-first, actively maintained continuation of the original **Networks** addon. Slimefun Legacy is the primary target, with source and build verification against Slimefun United and Slimefun Gugu.

The fork preserves the public plugin name `Networks`, Java package names, Slimefun item IDs, persistent-data keys, and the existing `CargoStorageUnits.db` location so established worlds can load the same machines and stored items.

## Compatibility

| Component | Support |
|---|---|
| Slimefun Legacy | Primary and release-blocking |
| Slimefun United | Compatibility-matrix target (`dev`) |
| Slimefun Gugu | Compatibility-matrix target (`master`) |
| Minecraft | 1.21.11 and newer |
| Java runtime | 21 and newer |
| Paper / Purpur | Supported target |
| Folia | Not claimed in Alpha 2; cross-region network transactions still require a dedicated audit |

Unknown Slimefun cores fail closed by default. An override exists in `config.yml`, but it should only be used for controlled testing.

## Alpha 2 hardening

- Serial SQLite query/update worker with bounded shutdown
- Transactional duplicate drawer-row migration and a permanent `(ContainerID, ItemID)` uniqueness index
- Corrected item/container ID recovery after stale environment counters
- Thread-safe drawer cache, pending-change snapshots, network-node registry, and chunk indexes
- Stale node/controller cleanup on breaks and chunk unloads to reduce ghost-network duplication paths
- Clone-and-commit inventory transfers that explicitly update the exact source slot, cursor, or held item
- Control X inventory-container rejection before a block item can enter the network
- Quantum Storage withdraw-before-sync persistence ordering
- Exact recipe/output binding, ingredient rollback, and bounded multi-craft behavior for crafting grids and auto-crafters
- Main-thread machine ticker safety by default for Paper 1.21.11+
- Version-tolerant blueprint storage and malformed legacy blueprint recovery
- Local-only skull profile comparison, avoiding remote profile lookups during item matching
- Runtime detection for Legacy, United, and Gugu plus Minecraft/Java support floors
- `/networks doctor status|scan|repair confirm`
- Optional reflective integration with Slimefun Legacy's `/sf doctor addons` service
- Exact-core GitHub Actions builds against all three Slimefun families

## Building

Compile against the exact Slimefun core JAR you intend to test:

```bash
./gradlew clean build -PslimefunCoreJar=/path/to/Slimefun.jar
```

Supported aliases are `SLIMEFUN_CORE_JAR`, `slimefunLegacyJar`, `SLIMEFUN_LEGACY_JAR`, and `SLIMEFUN_COMPATIBILITY_JAR`.

The GitHub Actions workflow builds each Slimefun core first, compiles this same Networks source against that exact JAR on Java 21, runs tests, verifies the source contract, and checks Java 21 class-file output.

## Upgrade safety

Back up the full server before replacing an existing Networks build. Test a copy of the world first, including existing Controllers, Grids, Drawers, Quantum Storage, Importers, Exporters, Pushers, Grabbers, wireless/P2P links, encoded blueprints, automatic crafters, a clean restart, and `/networks doctor scan`.

Alpha 2 contains a safe startup migration that combines duplicate drawer rows before creating a uniqueness index. It does not rename the database or intentionally change item IDs, plugin identity, or world block records.

## Credits

- **Sefiraat** — original Networks project and classic English wording
- **SlimefunGuguProject contributors** — continued compatibility work
- **ytdd9527, balugaq, yitoudaidai, tinalness, and contributors** — NetworksExpansion development
- **wickidcow** — Slimefun Legacy maintenance and Albion compatibility

Networks Legacy remains licensed under the GNU General Public License v3.0.
