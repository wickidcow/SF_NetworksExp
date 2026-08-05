# Networks Runtime Stability Contract

## Scope

This update advances the release to `2.1.112-Legacy-Alpha3`. Slimefun Legacy is the primary and release-blocking target; United and Gugu are required compatibility gates. Alpha 3 is a compatibility, lifecycle, and scheduled-maintenance hardening release, not an item-ID, plugin-name, database-path, guide-layout, or world-format migration.

## Preserved data contract

The following identifiers and storage locations remain unchanged:

- Bukkit plugin name: `Networks`
- Main class: `io.github.sefiraat.networks.Networks`
- Required dependency name: `Slimefun`
- Existing Java packages used by integrations
- All 288 existing Slimefun item IDs
- Existing Networks and historical fork persistent-data namespaces
- Existing placed Slimefun blocks and machine records
- Existing `CargoStorageUnits.db` path and core table names
- Existing guide organization from Alpha 2
- Existing recipes and item definitions

The Alpha 2 database migration remains intact: duplicate `(ContainerID, ItemID)` rows are merged transactionally before a permanent uniqueness index is created. Alpha 3 adds no new destructive database migration.

## Release compatibility gate

One source tree must compile and test against exact JARs built from:

1. `wickidcow/Slimefun-Legacy` `master` — primary and release-blocking
2. `Slimefun-United/Slimefun-United` `dev`
3. `SlimefunGuguProject/Slimefun4` `master`

The universal artifact is produced only after all three jobs succeed. It is compiled from the Legacy target and verified not to contain Slimefun core classes or optional-plugin API classes.

Runtime detection uses multiple independent signals:

- Legacy metadata, maintained-fork metadata, and a non-linking Legacy Doctor marker fallback
- United metadata and its unique Slimefun command aliases
- Gugu metadata and its unique API marker class

Unknown cores fail closed unless the explicit testing override is enabled.

## Alpha 3 lifecycle hardening

- Startup records the active initialization stage so a failure identifies whether it occurred during configuration, compatibility, integrations, database startup, item registration, listeners, commands, or services.
- Partial startup failures disable the plugin through Bukkit and reuse the normal cleanup path.
- Optional integrations initialize fail-soft; a broken optional API disables only that integration.
- RoseStacker and LogiTech API initialization is deferred one tick so load order can settle.
- JustEnoughGuide, LogiTech, and SlimeHUDPlus are declared as soft dependencies.
- Localization caches are concurrent, duplicate language registration is rejected, and embedded resources are closed after loading.
- The rotating Doctor cursor, shared hanging ticker, pending first-tick registrations, optional integration singleton, localization cache, database cache, node registry, and controller runtime maps are reset during disable.
- First-tick node registration rechecks the live chunk, Slimefun block data, and registered item before adding the runtime node.

## Scheduled Doctor behavior

The automatic repair pass is deliberately bounded:

- `doctor.max-auto-scan-entries: 512` controls the maximum node entries examined per scheduled pass.
- Each pass continues from a rotating cursor so large servers are covered over time without one full-registry spike.
- Unloaded chunks are skipped and never force-loaded.
- Stale node entries and chunk-index drift can be repaired automatically.
- Database queue/connection health remains visible in the report.

Manual commands remain complete loaded-state scans:

```text
/networks doctor status
/networks doctor scan
/networks doctor repair confirm
```

On compatible Slimefun Legacy builds, the reflective addon Doctor bridge remains available without class-linking Legacy-only APIs on United or Gugu.

## Alpha 2 safety retained

Alpha 3 retains the established protections for:

- Ordered SQLite work, bounded shutdown, duplicate-row merge, UPSERT writes, and counter recovery
- Concurrent loaded-node/chunk indexes and stale runtime-state invalidation
- Clone-and-commit item transfers and source-slot verification
- Quantum Storage withdraw-before-sync ordering and rollback capacity repair
- Exact recipe/output binding, atomic ingredient consumption, and rollback
- Defensive cross-fork cargo-slot resolution
- Vanilla pusher/grabber partial-transfer verification
- Network Remote and controller revalidation after chunk changes
- Local-only skull profile comparison
- Synchronized machine tickers by default on Paper/Purpur

## Required server validation

Use a copy of the production server and preserve the original worlds and plugin data until all checks pass.

1. Back up the worlds, `/plugins/Networks`, `/plugins/Slimefun`, and `CargoStorageUnits.db`.
2. Replace the JAR and perform a complete server stop/start. Do not use `/reload`.
3. Confirm startup identifies the intended Slimefun core and reaches `enabled successfully`.
4. Run `/networks doctor status` and `/networks doctor scan`.
5. Open existing Controllers, Grids, Crafting Grids, Drawers, Quantum Storage, and Network Remotes.
6. Verify exact item types and counts through importers, exporters, pushers, grabbers, vacuums, cargo, wireless, and P2P links.
7. Test old/new blueprints and automatic crafting, including cancellation, missing ingredients, and full output inventories.
8. Unload/reload chunks containing controllers, storage nodes, cargo endpoints, grids, and remotes.
9. Stop normally, restart, and compare stored amounts and metadata with the pre-restart values.
10. Test with each installed optional integration and confirm an optional API failure does not disable Networks.
11. Review the automatic Doctor log on a populated test server and confirm each pass stays within the configured node budget.

Folia support is not claimed. A multi-chunk network transaction can cross region ownership boundaries and requires a separate scheduler and transaction design.
