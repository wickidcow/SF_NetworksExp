# Changelog

## 1.0.25

### Network Controller placement safety
- Fixed a controller duplication path when a player attempted to place a second Network Controller onto an already-controlled network.
- Controller/network merge conflicts are now rejected during the held item's right-click phase, before Slimefun creates persistent block data for the attempted placement.
- Retained a BlockPlaceEvent fallback for unusual placement paths, but that fallback now explicitly removes the just-created runtime/database block state before cancelling. This prevents a later placement from seeing a ghost Slimefun controller and dropping an extra controller item.
- Ordinary Network nodes still use the BlockPlaceEvent merge guard, now with explicit ghost-state cleanup when such a merge is rejected.

### Network Controller interaction
- Right-clicking a Network Controller with an empty hand now shows a compact status readout instead of doing nothing.
- The readout shows current node count/capacity and whether the controller is online or overburdened, then points players to the Network Monitor for the full machine list, Active/Inactive counts, and Refresh.
- Held Networks tools keep priority, so Network Probe/Crayon-style item interactions are not replaced by the controller status click.

## 1.0.24

### Network Monitor topology inspector
- The classic **Network Monitor** now doubles as a read-only Networks topology inspector while preserving its six storage-facing controls and adjacent-inventory behavior.
- The monitor lists every machine type present in the controller's current `NetworkRoot`, grouped by actual Slimefun item rather than only by broad node category.
- Hovering a machine icon shows **Total connected**, **Active**, and **Inactive** counts. Active means the node is loaded, resolved to its Slimefun item, and currently assigned to this root; inactive means the root snapshot contains it but the runtime block is not fully resolved.
- Added page controls for networks with more machine types than fit on one screen.
- Added a summary panel showing total nodes versus controller capacity, active/inactive totals, machine-type count, page number, and overburdened state.
- Added a **Refresh Network** button. Refresh marks the owning controller topology dirty, waits for controller rediscovery, then rebuilds the monitor list from the new root instead of merely repainting stale GUI data.
- The inspector never scans outward itself and never force-loads chunks; it reads the controller's authoritative runtime topology.
- Added `features.network-monitor-inspector.enabled` (default `true`) so servers can disable the inspector without changing the historical Network Monitor item or world data.

## 1.0.23

### Advanced Auto Crafter functionality
- Restored the original stacked-blueprint contract in the English guide text: Advanced Auto Crafters use the number of identical encoded blueprints in their blueprint slot as the requested batch size.
- A 64-blueprint stack can therefore execute up to 64 recipe crafts in one operation when the recipe output stacks to 64 and enough ingredients are available.
- Fixed large-output recipes so a stacked blueprint no longer fails merely because `recipe output × blueprint count` exceeds one stack. The crafter now clamps the batch to the remaining legal output-stack capacity (for example, a 4-item recipe with 64 blueprints performs 16 crafts and produces 64 items).
- Withholding variants use the same batching rule while continuing to keep one output stack locally available to Networks/Cargo.
- The Recipe Encoder control now documents Click = 1 blueprint and Shift-click = up to 64 identical encoded blueprints.
- Auto Crafters now use reason-aware idle retry delays: missing/broken blueprint states back off longer until player intervention, while transient states such as missing power, missing ingredients, output pressure, or a temporarily missing network retry on the shorter interval. Opening/clicking the crafter or inserting a blueprint through the Crafter Manager clears the runtime delay immediately.

### Line-transfer performance
- Extended the rotating target-work budget to the other line families visible in live profiling: normal Line Transfer, normal Line Transfer Grabber, and Advanced Line Transfer Grabber, including PLUS variants.
- Defaults are 16 targets/pass for normal Line Transfer/Grabber and 12 targets/pass for Advanced Line Transfer Grabber. The existing bidirectional Advanced Line Transfer remains at 8.
- `max-targets-per-tick: 0` still restores historical unlimited-per-pass behavior.

## 1.0.22

### Advanced Line Transfer performance
- Added a rotating per-pass target budget for the bidirectional `NTW_EXPANSION_ADVANCED_LINE_TRANSFER` family, defaulting to 8 targets per push pass and 8 per grab pass. Long 32/64-block lines now spread expensive target work across ticks while preserving contiguous-line stop semantics.
- Added `max-targets-per-tick` under the normal and PLUS Advanced Line Transfer config sections. Set it to `0` to restore the historical unlimited per-tick scan.
- Removed the redundant pre-scan before line and vanilla grab operations, so target transport slots are no longer enumerated twice before a grab.
- Reworked transport-slot sanitation to avoid stream/distinct allocation on the hot cargo path when presets already return valid unique slot lists.
- Preserved the existing duplicate-ticker coalescing, transfer modes, power charging, item safety/rollback behavior, and 3,456 maximum advanced transfer amount.

## 2.1.112-Legacy-1.0

### Universal compatibility and preserved world contract
- Promoted the maintained fork to the first 1.0 Legacy release.
- Kept Slimefun Legacy primary while requiring successful United and Gugu compile/test gates before artifact upload.
- Preserved the `Networks` plugin identity, guide organization, all 288 item IDs, recipes, namespaces, placed blocks, and `CargoStorageUnits.db` schema/path.
- Updated the flat artifact to `Networks-Legacy-2.1.112-1.0.jar`.

### Transaction and integration safety
- Added lock-free transfer audit counters for withdrawals, deposits, rollbacks, compensation, safety drops, and failures.
- Added compensating network withdrawals when a source inventory/menu/cursor commit fails after a deposit was accepted.
- Added Control X compensation when a cut block deposit succeeds but block removal fails or cannot be verified.
- Added a fail-soft internal storage adapter registry and moved Infinity Expansion 2 through that contract.
- Retained official and relocated/unofficial IE2 storage discovery, empty-unit deposits, slot-based withdrawals, matching, capacity reporting, and nested-unit rejection.
- Hotfix: changed IE2 discovery to lazy item-instance discovery so preview/unofficial builds are not disabled merely because their `StorageUnit` class cannot be loaded externally by name during Networks startup.
- Hotfix: made IE2 cache reflection optional with a read-only `stored_amount` persistence fallback; all writes still flow through IE2 menu slots.

### Drawer database durability
- Added bounded startup backups for `CargoStorageUnits.db` plus WAL/SHM sidecars.
- Added startup `PRAGMA quick_check` validation with fail-closed corruption handling.
- Replaced per-row delayed amount writes with one transactional absolute-value snapshot.
- Added durable atomic `CargoStorageUnits.recovery.tsv` write-ahead journaling with forced writes and idempotent replay.
- Added shutdown checkpointing for still-unsubmitted drawer amounts.
- Added database queue telemetry for scheduled, completed, failed, rejected, and cancelled tasks.

### Diagnostics and qualification
- Expanded Networks Doctor with storage-adapter, database-integrity, backup, recovery-journal, queue, and transfer-safety details.
- Added regression tests for the recovery journal, backup manager, queue lifecycle, and transfer audit.
- Retained controller circuit breakers, atomic node registration, bounded rotating Doctor scans, and chunk unload/reload cleanup.

## 2.1.112-Legacy-Alpha5

### Controller fault containment
- Added a configurable per-controller rebuild circuit breaker with exponential cooldowns.
- Removed failed partial runtime trees immediately and cleared failure state after successful recovery.
- Added compact failure diagnostics without retaining throwable instances.
- Added regression tests for thresholds, cooldown backoff, recovery, and bounded failure descriptions.

### Node and chunk lifecycle
- Replaced unconditional node registration with atomic same-type duplicate and node-type conflict handling.
- Clears assignments belonging to replaced roots without unregistering valid loaded blocks.
- Discards controller runtime trees before chunk registry unload and reruns controller first-tick initialization after reload.
- Added Doctor counters for controller failures, quarantines, circuit trips, duplicate registrations, and type conflicts.

### Compatibility and preservation
- Kept Slimefun Legacy primary with required United and Gugu build gates.
- Retained Alpha 4 Infinity Expansion 2 storage-unit support.
- Preserved all 288 item IDs, guide organization, recipes, database paths, plugin identity, and world records.
- Updated the flat GitHub artifact to `Networks-Legacy-2.1.112-Alpha5.jar`.

## 2.1.112-Legacy-Alpha4

### Infinity Expansion 2
- Added a reflection-backed adapter for every IE2 item implemented through its shared `StorageUnit` class.
- Added input and output routing through IE2's real menu transport slots.
- Added read-only cache discovery for the stored item, amount, and capacity.
- Added empty-unit first-item deposits while preventing nested IE2 storage units.
- Kept IE2 optional and fail-soft; no IE2 classes are bundled into the universal JAR.

### Diagnostics and compatibility
- Replaced ambiguous `inactive` integration results with `active`, `not-installed`, `detected`, `incompatible`, or `failed`, including detected plugin versions.
- Kept Slimefun Legacy primary while retaining required United and Gugu build gates.
- Preserved all 288 item IDs, guide organization, recipes, database paths, and world data.
- Updated the flat GitHub artifact to `Networks-Legacy-2.1.112-Alpha4.jar`.

## 2.1.112-Legacy-Alpha3

### Release-gated three-core compatibility

- Makes Slimefun Legacy, Slimefun United, and Slimefun Gugu required compile/test gates for the universal release.
- Keeps Slimefun Legacy as the primary compiler and release-blocking runtime target.
- Builds the final universal artifact only after all three exact-core jobs succeed.
- Records the exact Slimefun Legacy commit used to compile the release artifact.
- Adds universal-JAR validation for plugin identity, version, 288 preserved item IDs, and accidental bundled core/optional API classes.

### Runtime core detection

- Adds a Legacy Doctor marker fallback, United command-alias fingerprinting, and Gugu API marker detection.
- Gives explicit United/Gugu fingerprints precedence over the Legacy fallback marker to avoid future marker overlap.
- Adds regression tests for every supported family and unknown-core fail-closed behavior.

### Optional integration stability

- Adds missing `softdepend` declarations for SlimeHUDPlus, JustEnoughGuide, and LogiTech.
- Defers RoseStacker and LogiTech API initialization until the next server tick.
- Validates optional API ownership through each plugin's classloader.
- Disables only the failing optional integration after a runtime/linkage error instead of disabling Networks.
- Prefers WildStacker when both WildStacker and RoseStacker are installed, preventing two stack providers from handling the same item entity.
- Reports active/inactive optional integrations in Networks Doctor details.

### Lifecycle and scheduled-maintenance stability

- Adds staged startup failure reporting and safe cleanup after partial initialization.
- Resets optional integration, localization, Doctor cursor, shared ticker, and loaded runtime caches during disable.
- Makes localization caches concurrent and prevents duplicate language registration.
- Closes embedded language resources and only exposes a language after it loaded successfully.
- Replaces permanent first-tick block-location retention with a shared pending set that is cleared on shutdown and revalidated after chunk reload.
- Limits automatic Doctor repair to a configurable rotating node budget (`doctor.max-auto-scan-entries`, default `512`).
- Keeps manual `/networks doctor scan` and `repair confirm` as full loaded-state scans.

### Preserved behavior

- No guide category, item, recipe, machine, item-ID, namespace, database-path, plugin-name, or world-record migration.
- The organized guide behavior from Alpha 2 is intentionally unchanged.

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

### Runtime stability pack

- Added a defensive Slimefun transport-slot adapter that supports item-aware and legacy menu APIs, rejects invalid slots, and is used across Networks cargo/storage integrations.
- Corrected `NetworkRootReadyEvent` thread metadata and rejects stale controller records before rebuilding a root.
- Revalidates Network Remote bindings after deferred menu loading so broken, replaced, or unloaded grids cannot open stale menus.
- Hardened vanilla pushers for verified partial insertion and Crafter rejection, and vanilla grabbers for clone-verify-commit source removal.
- Made drawer and LogiTech linker runtime caches concurrency-safe and repairs invalid stored linker types/icons.
- Clears reverse storage-access history when nodes break or unload, preventing stale cargo endpoints from remaining hot-cached.

### Diagnostics

- Added `/networks doctor status|scan|repair confirm`.
- Scans loaded nodes, controllers, chunk indexes, drawer caches, database state, and compatibility information without force-loading chunks.
- Added a reflective Slimefun Legacy Addon Doctor bridge that does not hard-link on United or Gugu.
- Added `RUNTIME_STABILITY.md` with the preserved-data contract and an in-world validation matrix.

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
