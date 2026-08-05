# Networks Legacy Compatibility Contract

## Release

`2.1.112-Legacy-Alpha4`

## Priority order

1. **Slimefun Legacy** — primary runtime, compiler target, and release-blocking requirement
2. **Slimefun United** — required exact-core compatibility build
3. **Slimefun Gugu** — required exact-core compatibility build

A universal release is not uploaded unless the same source compiles/tests against all three.

## Preserved world and addon identity

Alpha 3 retains:

- Bukkit plugin name `Networks`
- Main class `io.github.sefiraat.networks.Networks`
- Required plugin name `Slimefun`
- Existing Java package names
- All 288 item IDs from the established 2.1.111/Alpha series contract
- Existing Networks and historical persistent-data namespaces
- Existing `CargoStorageUnits.db` file and core tables
- Existing configuration keys, with additive compatibility/Doctor options
- Existing guide categories and the Alpha 2 guide organization

No intentional item-ID, guide, placed-block, or database-file migration is included.

## Three-core build contract

GitHub Actions builds exact core JARs from:

- `wickidcow/Slimefun-Legacy` `master` using Java 25 for the core build
- `Slimefun-United/Slimefun-United` `dev`
- `SlimefunGuguProject/Slimefun4` `master`

Networks itself is compiled to Java 21 bytecode against each JAR. The build uses the Paper `1.21.11-R0.1-SNAPSHOT` API baseline and injects the exact core through `-PslimefunCoreJar=/path/to/Slimefun.jar`.

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

Folia remains unclaimed because multi-chunk transactional networks are not inherently region-safe.
