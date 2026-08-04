# Slimefun Legacy Compatibility

## Release

`2.1.112-Legacy-Alpha1`

## Compatibility contract

This release preserves:

- Bukkit plugin name: `Networks`
- Main class: `io.github.sefiraat.networks.Networks`
- Slimefun dependency name: `Slimefun`
- Existing Java package names
- All 288 item localization IDs from NetworksExpansion 2.1.111
- Existing configuration keys
- Existing storage and database code paths

No item-ID migration or storage-schema rewrite is included in Alpha 1.

## Build contract

- Compiles against an exact local Slimefun Legacy JAR
- Does not compile against Gugu Slimefun core
- Paper API baseline: `1.21.11-R0.1-SNAPSHOT`
- Java bytecode target: 21
- Java 25 may be used by CI while producing Java 21 class files
- Guizhan updater and automatic JAR replacement are removed
- Pinyin/OpenCC runtime libraries are removed from the English edition
- GuizhanLib display-name helpers are replaced with a Networks-owned compatibility utility

## English localization

The English locale uses the original Sefiraat/Blob Builds names and descriptions for classic Networks items where an original equivalent exists. Expansion-only content uses maintained English terminology based on its actual machine role.

The original Chinese locale is retained only under `scripts/localization/` as a key-structure reference for the reproducible locale generator. It is not packaged as a runtime language.

## First server test checklist

1. Back up the complete server and Slimefun data.
2. Start with no players online.
3. Confirm Networks enables without missing-class errors.
4. Confirm existing Controllers discover their networks.
5. Open existing Grids and Crafting Grids.
6. Verify Drawers and Quantum Storage retain item types and amounts.
7. Test Importers, Exporters, Pushers, Grabbers, and Cargo interaction.
8. Test encoded blueprints and automatic crafting.
9. Test wireless and P2P links.
10. Stop the server normally and verify data after a second startup.

Folia is intentionally not claimed for Alpha 1 because the inherited code still contains direct Bukkit scheduler access that needs a separate ownership audit.

## Java compilation boundary

The workflow builds Slimefun Legacy using Java 25, then deliberately switches to Java 21 before compiling Networks. This keeps Networks on its supported Java 21 bytecode and annotation-processing path while still consuming the exact current Slimefun Legacy JAR. Paper 26.x also makes `RecipeChoice.ExactChoice` final, so Networks uses a composition-based recipe choice rather than subclassing Bukkit internals.
