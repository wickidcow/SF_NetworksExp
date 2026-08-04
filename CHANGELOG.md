# Changelog

## 2.1.112-Legacy-Alpha2

### Core compatibility

- Added one exact-core build matrix for Slimefun Legacy (`master`), Slimefun United (`dev`), and Slimefun Gugu (`master`).
- Made Slimefun Legacy the primary release artifact while retaining one source/JAR for all three cores.
- Added runtime core detection, Minecraft 1.21.11 floor, Java 21 floor, and fail-closed unknown-core handling.
- Standardized exact JAR injection through `slimefunCoreJar` / `SLIMEFUN_CORE_JAR` with Legacy compatibility aliases.

### World and duplication safety

- Rebuilt the loaded network registry with concurrent maps, normalized block locations, and correct per-chunk cleanup.
- Removes stale node/controller runtime state on block breaks and repairs chunk-index drift.
- Added a serial SQLite worker so drawer reads and writes cannot reorder across two workers.
- Added bounded shutdown, queued/in-flight diagnostics, and protection against closing SQLite while its worker is still active.
- Merges duplicate stored-item rows transactionally and adds a unique `(ContainerID, ItemID)` index.
- Recovers item/container counters from the highest real database IDs when environment values are missing or stale.
- Uses UPSERT operations for atomic drawer additions and exact amount snapshots.
- Made drawer load state, caches, and pending save snapshots concurrency-safe.
- Added clone-and-commit transfers for menu slots, player inventory slots, cursors, held items, importers, grabbers, vacuums, and crafting interfaces.
- Hardened Control X against moving inventory-bearing blocks into networks.
- Lazily invalidates cached nodes whose physical Slimefun block no longer matches the recorded node type.
- Corrected Quantum Storage persistence ordering so withdrawals are committed before the storage block is synchronized.
- Bound crafting results to one exact nine-slot recipe, restored all reserved ingredients on cancellation/failure, and stopped multi-craft when any ingredient or output space is unavailable.
- Fixed the Smart Crafting Grid entity-limit comparison and the failed-fetch path that could return crafted output instead of reserved ingredients.

### Minecraft 1.21.11 functionality

- Defaults machine tickers to synchronized server-thread execution.
- Removed Bukkit asynchronous scheduling from menu registration, grid refresh, particles, keybind maintenance, debug viewers, and drawer autosave snapshots.
- Hardened blueprint encoding/decoding for Slimefun ItemStacks and malformed historical ItemStack payloads.
- Preserves old blueprint array keys and all known Networks namespace variants.
- Replaced skull `OfflinePlayer` comparison with local player-profile comparison to avoid Mojang profile lookups and rate limits.
- Retains modern potion metadata handling and blocks unsafe containers/bundles from network storage matching.

### Diagnostics

- Added `/networks doctor status|scan|repair confirm`.
- Scans loaded nodes, controllers, chunk indexes, drawer caches, database state, and compatibility information without force-loading chunks.
- Added a reflective Slimefun Legacy Addon Doctor bridge that does not hard-link on United or Gugu.

## 2.1.112-Legacy-Alpha1

### Slimefun Legacy

- Replaced the Gugu Slimefun core dependency with an exact local Slimefun Legacy JAR.
- Added CI that builds Slimefun Legacy and compiles Networks against the produced artifact.
- Preserved the `Networks` plugin identity, main class, package names, configuration keys, and 288 item IDs.
- Updated the Paper API baseline to 1.21.11.
- Enforced Java 21 bytecode.

### English edition

- Added a complete `en-US` runtime locale.
- Restored classic item names and descriptions using original Blob Builds wording where available.
- Added consistent English names and functional descriptions for Expansion-only content.
- Replaced remaining hard-coded Chinese player messages and console text.
- Removed Chinese-only Pinyin/OpenCC search and runtime library loading.
- Made English the default language.

### Safety and maintenance

- Disabled automatic JAR replacement.
- Removed the Guizhan updater dependency and hard requirement.
- Replaced GuizhanLib item/material display helpers with a Networks-owned English display-name bridge.
- Changed project and support links to the maintained fork.
- Replaced the Tencent Gradle mirror with the official Gradle distribution.
- Added compatibility, localization, placeholder, item-ID, and Java bytecode verification.
- Marked Folia unsupported until a dedicated scheduler and region-ownership audit is complete.

## 2.1.112-Legacy-Alpha1 compile compatibility follow-up

- Build Slimefun Legacy with Java 25, then switch the Networks compilation to Java 21.
- Update Lombok to 1.18.46 for current JDK compatibility.
- Replace inheritance from Paper's now-final `RecipeChoice.ExactChoice` with a composition-based `RecipeChoice` implementation.
- Add permanent verifier checks for the Java 21 compile lane and recipe-choice compatibility.
