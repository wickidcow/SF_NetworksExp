# Networks Legacy

**Networks Legacy** is an English-first, actively maintained continuation of the original **Networks** addon. Slimefun Legacy is the primary and release-blocking target. The same source is also compiled against Slimefun United and Slimefun Gugu before a universal release artifact can be published.

The fork preserves the Bukkit plugin name `Networks`, the main class, Java package names, Slimefun item IDs, persistent-data namespaces, placed machines, and the existing `CargoStorageUnits.db` location so established worlds can upgrade without an intentional format migration.

## Compatibility

| Component | Support |
|---|---|
| Slimefun Legacy | Primary and release-blocking (`master`) |
| Slimefun United | Required compatibility target (`dev`) |
| Slimefun Gugu | Required compatibility target (`master`) |
| Minecraft | 1.21.11 and newer |
| Java runtime | 21 and newer |
| Paper / Purpur | Supported target |
| Folia | Not claimed; cross-region network transactions need a separate design audit |

Unknown Slimefun cores fail closed by default. The override in `config.yml` is intended only for controlled testing.

## Alpha 3: compatibility and lifecycle stability

`2.1.112-Legacy-Alpha3` continues from the working Alpha 2 guide layout without reorganizing categories or changing item content.

- Makes the three-core matrix a required release gate instead of a secondary informational build.
- Builds the final universal JAR only after Legacy, United, and Gugu compile/test jobs pass.
- Improves runtime fingerprinting for Legacy, United, and Gugu while failing closed for unknown forks.
- Defers optional plugin API initialization and disables only the incompatible integration when an optional API fails.
- Declares JustEnoughGuide, LogiTech, and SlimeHUDPlus as soft dependencies so Bukkit can establish safer load order.
- Limits scheduled Doctor work to a rotating node budget instead of scanning the complete loaded registry in one tick.
- Clears Doctor, localization, optional integration, shared ticker, and pending first-tick state during disable/re-enable lifecycles.
- Revalidates first-tick node registration after chunk unload/reload without retaining every historical block location.
- Adds a universal-JAR verifier that rejects accidentally bundled Slimefun-core or optional-plugin API classes.
- Preserves all 288 item IDs, plugin identity, database paths, guide organization, recipes, and world records.

Alpha 3 builds on the Alpha 2 database, cargo, transfer, crafting, quantum-storage, remote, and runtime safety work documented in [`RUNTIME_STABILITY.md`](RUNTIME_STABILITY.md).

## Building

Compile against the exact Slimefun core JAR being tested:

```bash
./gradlew clean build -PslimefunCoreJar=/path/to/Slimefun.jar
```

Supported aliases are `SLIMEFUN_CORE_JAR`, `slimefunLegacyJar`, `SLIMEFUN_LEGACY_JAR`, and `SLIMEFUN_COMPATIBILITY_JAR`.

The GitHub Actions flow performs these release gates:

1. Build exact current JARs from Slimefun Legacy, Slimefun United, and Slimefun Gugu.
2. Compile and test the same Networks source against each exact JAR on Java 21.
3. Verify the static compatibility contract and Java 21 bytecode.
4. Verify that the shaded artifact contains Networks and its intended libraries, but no Slimefun core or optional-plugin APIs.
5. Build/upload the universal JAR from the Legacy compiler target only after all three compatibility targets pass.

## Upgrade safety

Back up the full server before replacing an existing Networks build. Test a copy of the world first, including old Controllers, Grids, Drawers, Quantum Storage, Importers, Exporters, Pushers, Grabbers, wireless/P2P links, encoded blueprints, automatic crafters, chunk unload/reload, a clean restart, and `/networks doctor scan`.

Do not use `/reload`. Perform a complete stop and start when changing Slimefun or Networks JARs.

See [`ALPHA3_COMPATIBILITY_STABILITY.md`](ALPHA3_COMPATIBILITY_STABILITY.md) for the release scope and validation order.

## Credits

- **Sefiraat** — original Networks project and classic English wording
- **SlimefunGuguProject contributors** — continued compatibility work
- **ytdd9527, balugaq, yitoudaidai, tinalness, and contributors** — NetworksExpansion development
- **wickidcow** — Slimefun Legacy maintenance and Albion compatibility

Networks Legacy remains licensed under the GNU General Public License v3.0.
