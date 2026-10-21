# Networks Legacy Compatibility Contract

## Release

`1.0.60`, coordinated with InfinityExpansion2 `2.0.15`.

The exact accepted commits, public artifact hashes, native evidence and remaining limitations are recorded in [NETWORKS_EXPANSION_COMPLETION.md](NETWORKS_EXPANSION_COMPLETION.md).

## Maintenance target and retained compatibility gates

**GuGu Slimefun Legacy** is the primary runtime and development target. The release uses `wickidcow/Slimefun-Legacy` source `1cb32d6e95b305998065b8ef6b936a56f679187d` for its core and coordinated native acceptance.

The existing auxiliary United and Gugu exact-core build gates remain enabled and passed. They preserve the established universal build contract; this release does not undertake new maintenance of abandoned United support. A universal release is not uploaded unless every existing required gate succeeds.

## Preserved world and addon identity

1.0.60 retains:

- Bukkit plugin name `Networks`
- Main class `io.github.sefiraat.networks.Networks`
- Required plugin name `Slimefun`
- Existing Java package names
- All 289 currently registered item IDs, including the established 2.1.111/Alpha series identities
- Existing Networks and historical persistent-data namespaces
- Existing `CargoStorageUnits.db` file and core tables
- Existing configuration keys, with additive compatibility/Doctor options
- Existing guide categories and the Alpha 2 guide organization

No intentional item-ID, guide, placed-block, or database-file migration is included.

## Three-core build contract

GitHub Actions builds exact core JARs from:

- `wickidcow/Slimefun-Legacy` pinned at `1cb32d6e95b305998065b8ef6b936a56f679187d`, using Java 25 for the core build
- `Slimefun-United/Slimefun-United` `dev`
- `SlimefunGuguProject/Slimefun4` `master`

Networks itself is compiled to Java 21 bytecode against each JAR. The build uses the Paper `1.21.11-R0.1-SNAPSHOT` API baseline and injects the exact core through `-PslimefunCoreJar=/path/to/Slimefun.jar`.

Separate gates compile Networks against Paper 26.3 build 176 and exercise native Paper 26.3 builds 176/194 and Purpur 1.21.11 build 2568. IE2's coordinated storage/machine acceptance is on Purpur; it also passes separate Paper 1.21.11/26.2/26.3 compilation. These results do not certify every future server build or Folia runtime safety.

The release verifier rejects:

- Bundled Slimefun core classes
- Bundled WildStacker/RoseStacker/other optional API classes
- Plugin identity/version drift
- Item-ID count drift
- Java bytecode above 21

## Runtime compatibility behavior

- Legacy is identified by explicit metadata and a non-linking Doctor API marker fallback.
- United is identified by explicit metadata or its unique command aliases.
- Gugu is identified by metadata or its unique API marker.
- United/Gugu-specific fingerprints take precedence over the Legacy fallback marker.
- Unknown core variants fail closed by default.

## Stability behavior

- Optional plugin APIs are fail-soft and may disable themselves without disabling Networks.
- Missing optional integrations do not prevent class loading or startup.
- Scheduled Doctor repair uses a rotating configurable budget rather than a full node scan each pass.
- Manual Doctor scans remain complete for loaded state and never force-load chunks.
- Startup and shutdown clear singleton/cache/ticker/cursor state that could otherwise survive a partial lifecycle.
- Chunk first-tick registration is revalidated and pending locations are cleared during shutdown.
- Alpha 2 database, transfer, cargo, quantum, crafting, remote, and controller protections remain active.

## 1.0 Legacy controller and node stability

- Repeated controller rebuild failures are isolated per controller and paused with a configurable exponential cooldown.
- Failed or replaced runtime roots clear only assignments that still belong to that root; loaded block registrations remain available for rebuilding.
- Controller roots are discarded before their chunk registry is removed, preventing unloaded controllers from remaining globally active.
- Node registration is atomic: same-type duplicate attempts retain the live definition, while type conflicts replace stale identity and invalidate the old root.
- Doctor reports tracked failures, quarantines, circuit trips, duplicate registrations, and node-type conflicts without force-loading chunks.
- All Alpha 2 transfer rollback, crafting rollback, database drain, and persistence protections remain active.

## Required staging checks

1. Back up all worlds and Slimefun/Networks data.
2. Use a full stop/start, not `/reload`.
3. Check that the console reports the expected core family.
4. Run `/networks doctor scan`.
5. Test existing and newly placed network blocks.
6. Test exact amounts through every storage/cargo path.
7. Test blueprints and all automatic crafting machines in use.
8. Test chunk unload/reload and Network Remote revalidation.
9. Restart and compare stored data.
10. Test optional integrations individually and in the same combination used on production.

Folia is unsupported by these addons because multi-chunk transactional networks have not been made safe across independent regions. Neither addon enables Folia with a descriptor flag. This is an addon limitation, not a claim that the pinned Slimefun Legacy core lacks Folia support. Leaf runtime validation is also not established by the 1.0.60/2.0.15 refresh.

## 1.0 persistence additions

The database path and schema remain unchanged. The release adds startup backups, `PRAGMA quick_check`, transactional absolute amount snapshots, and an external `CargoStorageUnits.recovery.tsv` file used only for pending write recovery. Missing configuration keys use safe defaults.

The 1.0.60 repair series adds exact-identity template recovery evidence, retains pending amount writes through rejected database work and clean shutdown, and preserves invalid stored balances for reconciliation. Receipt verification and clean restart tests do not claim atomic recovery across an arbitrary process crash.
