# Changelog

## 1.0.49

### Database queue reliability and diagnostic performance
- Keeps accepted database work pending throughout worker handoff, execution, and callback completion so drain checks cannot report completion early.
- Makes pending-task checks constant-time, removing repeated scans of large database backlogs for the internal shutdown marker.
- Counts only work actually removed during cancellation while preserving the count owned by an executing worker.
- Adds controlled concurrency regressions for handoff, callback completion, cancellation, concurrent submissions, and rejected work.
- Preserves database ordering, stored contents, item IDs, recipes, machine throughput, and public queue APIs.

## 1.0.48

### Network Cargo Storage compatibility and Purpur menu safety
- Adds transactional virtual Cargo transport for Network Cargo Storage Units without exposing their GUI display slots as real inventory storage.
- Supports direct Slimefun Cargo insert/withdraw through Slimefun Legacy 4.1.69's virtual-storage transport hook, with filter, rollback, Content Lock, Void Excess, and per-type capacity behavior preserved.
- Bridges Networks Expansion Transfer/Line Transfer and whitelisted grabbers directly to drawer storage while keeping monitor-attached no-auto-assign behavior unchanged.
- Fixes Purpur/Paper 1.21.11 `SlimefunItemStack -> CraftItemStack` ClassCastException when a drawer renders stored Slimefun items or refreshes its Quantum Storage slot.
- Sanitizes only live menu preview stacks; stored item payloads, IDs, database contents, recipes, storage identities, quantities, routing priorities, and existing world data are unchanged.
- Revalidates the universal Java 21 JAR against finalized Slimefun Legacy 4.1.69 source plus current Slimefun United and Gugu compatibility targets.

## 1.0.47

### Grid selection correctness
- Includes the last supported sort mode when cycling forward: addon, reverse quantity, and quantity sorting are no longer skipped for their respective mode limits.
- Refreshes the derived grid view immediately when its filter or sorting choice changes, while retaining cached results for identical choices and ordinary paging.
- Avoids allocating an enum array on backward sorting wraps without changing backward results.
- Adds 17 permanent regression tests; preserves item IDs, stored quantities, recipes, machine rates, transfer priorities and quantum-storage category order.
- Retains test reports alongside the raw installable JAR build.

## 1.0.46

### Machine ticking and transport performance

- Reuses confirmed missing-source results and item requests within one multi-target push pass; limiter denials and zero-quantity requests never become network-wide missing-item results.
- Stops ordinary and specified-quantity grab scans when the existing input limiter activates, avoiding remaining inventory reads and stack clones. VOID mode retains its intentional discard behavior.
- Skips grab-slot discovery for the three push-only transport modes, without changing line traversal, cursor rotation, power charges, or configured tick intervals.
- Keeps line templates array-backed, allocates no template arrays for empty transfer machines, avoids cursor-index arrays for ordinary transfers, and avoids push-memo overhead for single-target transfers.
- Reduces controller discovery allocations, repeated chunk checks, and redundant registry updates; Doctor reports topology rebuild timing and push-memo counters.
- Keeps the public debug-subscription map authoritative, removes empty watcher entries, and skips feedback work when nobody is subscribed. This avoids stale subscriptions from a separate reverse index.
- Adds behavioral regression coverage for transfer limits, limiter recovery, partial progress, VOID mode, and pass-local source misses. Item IDs, recipes, storage formats, routing order, and machine throughput remain unchanged.

## 1.0.45

### Cross-core API hardening and deprecation cleanup
- Completes the current Slimefun Legacy deprecation pass and makes Legacy deprecation/removal warnings release-blocking with `-Werror`, while United/Gugu compatibility-only warnings remain non-blocking.
- Replaces deprecated Networks storage snapshots with the maintained copy/direct-view APIs, including Drawer Manager and Networks Drawer paths, while preserving stored item order and persistence behavior.
- Modernizes remaining ItemMeta display/lore paths to Adventure-backed components across manager screens, directional controls, item-flow views, drawer binding, blueprint migration, and shared icon/theme helpers.
- Replaces deprecated Research cost access with `getLevelCost()`, removes the unused `HIDE_ADDITIONAL_TOOLTIP` compatibility wrapper, and narrows broad deprecation suppressions to documented cross-core API boundaries.
- Keeps the public Bungee `ChatColor` Theme getter as an explicit compatibility boundary so existing addons are not broken by a maintenance release.
- Pins maintained JEG 2.1.67 by SHA-256 in CI, retries transient GitHub release-download failures, validates the JAR/API class before compilation, and still supports the canonical dependency path for local builds.
- Reuses the exact Slimefun Legacy core JAR that passed the compatibility matrix for final release packaging, eliminating moving-branch drift and the duplicate Legacy rebuild.
- Verifies the same universal Java 21 JAR against current Slimefun Legacy, Slimefun United, and Slimefun Gugu exact-core builds.
- Preserves existing item IDs, recipes, storage formats, network routing, transfer quantities, quantum ordering, and world data.

## 1.0.44

### Item Differenter API modernization
- Replaces deprecated fire-resistance checks with the current damage-resistance API and the fire damage-type tag.
- Replaces deprecated integer custom-model-data checks with full CustomModelDataComponent comparison across floats, flags, strings, and colors.
- Removes the unreachable pre-1.20.5 PotionData fallback and compares current PotionType data directly.
- Removes obsolete 1.20.5/1.21 version gates from ItemDifferenter because Networks' supported Minecraft floor is 1.21.11.
- Preserves Item Differenter result keys and Slimefun/PDC/enchantment/lore/meta comparison behavior.

## 1.0.43

### Line-transfer budget migration
- Corrects the shipped normal Line Transfer budgets so the 1.0.37 performance defaults actually take effect: normal bidirectional Line Transfer and PLUS process 8 targets per pass, while their Grabber variants process 12.
- Migrates existing stock `2.1.112-legacy-1.0` configs only when those four keys still contain the old bundled value of 16.
- Preserves administrator-tuned values that differ from the old stock value.
- Advances the config marker to `2.1.112-legacy-1.1` so the migration runs once.
- Keeps line cursor rotation, transfer quantities, tick cadence, power rules, and persistence behavior unchanged.

## 1.0.42

### Brewing inventory API cleanup
- Removes the obsolete pre-1.20.5 PotionData fallback from VanillaInventoryWrapper.
- Uses PotionMeta#getBasePotionType() directly, matching Networks' supported Minecraft floor of 1.21.11+.
- Removes the now-unused MinecraftVersion branch and PotionData import from this hot inventory wrapper.
- Adds a regression guard so the removal-marked potion API cannot return.

## 1.0.41

### Server-scale transport and grid cleanup
- Reuses the existing NetworkRoot transport-miss limiter to skip importer, exporter, grabber, Advanced Import, and Advanced Export work that cannot succeed while an accessor is already limited.
- Preserves the historical limiter threshold and recovery timing; no new cooldown, tick delay, slot order, quantity limit, or transfer rule is introduced.
- Stops long occupied-slot and template scans as soon as an attempted transfer activates the existing limiter.
- Preserves empty-import and empty-template feedback semantics.
- Removes Network Grid and Network Crafting Grid cache entries when their blocks are broken, while still invoking the shared NetworkObject break lifecycle.
- Adds source-contract guards for all five limiter short-circuits and both grid-cache cleanup paths.

## 1.0.40

### AutoCrafter ingredient metadata cache
- Reuses one resolved ItemStack metadata cache per aggregated blueprint ingredient across AutoCrafter ticks.
- Adds an allocation-light NetworkRoot availability probe that accepts an existing ItemStackCache and requested amount.
- Creates withdrawal ItemRequests from the same resolved cache while preserving the existing complete-recipe preflight, withdrawal order, rollback safety, power cost, and throughput.
- Keeps the current successful-craft optimization that reuses each ItemRequest for the withdrawal phase.
- Adds regression guards for the cached-template path across Legacy, United, and Gugu builds.

## 1.0.39

### Paper 26.3 API modernization
- Replaced all deprecated-for-removal `StorageCacheUtils.getSfItem(Location)` calls with `getSlimefunItem(Location)` across Networks, including integrations, listeners, admin tools, diagnostics, and network controls.
- Replaced the deprecated `BlockStorage.getInventory` break-handler lookup with the equivalent cached block-data menu path without force-loading data.
- Migrated manual-crafter and network power reads/removals to the long-capacity Slimefun energy API shared by Legacy, United, and Gugu.
- Modernized Network Monitor, directional controls, localization action bars, and selected ItemMeta display/lore paths to Adventure-backed APIs while preserving legacy color rendering.
- Added cross-core source-contract guards for the storage API migration and Adventure text bridge without adding a Gugu-specific test dependency.
- Preserved existing network routing, topology, item IDs, recipes, persistence formats, transfer quantities, and storage behavior.
- Left ItemDifferenter's version-sensitive custom-model-data, fire-resistant, and map component comparisons for a separate compatibility-focused pass.

## 1.0.38

### Bidirectional line-transfer traversal reuse
- Reuses the validated push traversal for the grab phase only when both phases run in the same tick and their line cursors are aligned.
- Keeps the shared target list strictly tick-local; no BlockMenu or inventory references are retained across ticks.
- Falls back to the existing independent scan whenever cadence, cursor position, transfer type, or line state does not qualify for safe reuse.
- Preserves push-then-grab ordering, target budgets, transfer quantities, power cost, cursor rotation, and vanilla/push-only/grab-only behavior.
- Adds a regression guard so the optimization cannot silently become a persistent cross-tick menu cache.

## 1.0.37

### Line-transfer performance
- Reduced the default work budget for normal Line Transfer from 16 targets per pass to 8, matching the proven Advanced Line Transfer budget.
- Reduced normal Line Transfer Grabber from 16 targets per pass to 12.
- Fixed budgeted line traversal so resumed passes jump directly to their saved cursor instead of re-scanning every already-processed block from the line origin.
- Preserved contiguous-line behavior by revalidating the full prefix again when the cursor wraps to the start of the next cycle.

### Quantum Storage upgrade migration
- Existing English language files now migrate the old stock Quantum Storage names to the corrected visible tier sequence 0 through 14.
- Historical internal Slimefun IDs, capacity progression, upgrade recipes, placed blocks, and saved Quantum Storage data remain unchanged.
- Custom administrator-edited Quantum Storage names are preserved; only exact legacy stock names are replaced.
- Added regression coverage for both the stock-name migration and custom-name preservation.

## 1.0.36

### Quantum Storage tier names
- Fixed the player-facing Quantum Storage tier numbers so the capacity progression displays as 0 through 14 in order.
- Preserved the historical internal Slimefun IDs used by existing worlds, placed blocks, recipes, and saved storage data.
- Added a regression test that locks the legacy-ID-to-visible-tier mapping so registration order cannot leak back into Guide, SlimeHUD, item, or block GUI names.

## 1.0.35

### Sleeping blueprint-less Auto Crafters
- Auto Crafters with no blueprint now take a minimal idle path instead of entering network/crafting preflight every Slimefun tick.
- Dormant crafters remain registered as network nodes so removing a blueprint cannot break topology or disconnect machines behind them.
- Normal Auto Crafters with a buffered output still get a final transfer opportunity; Withholding Auto Crafters retain their output as before.
- Active crafters keep the existing adaptive idle backoff, recipe batching, power usage, and ingredient safety behavior unchanged.
- Updated the compatibility source contract so CI guards the topology-preserving sleep path.

## 1.0.30

### Network Monitor auto-connect
- Removed storage-direction selection from the standard Network Monitor GUI.
- The standard Network Monitor now automatically exposes supported storage touching any of its six faces.
- Multiple supported storage blocks may be exposed by one Monitor when they touch different sides.
- Input-only and output-only Monitor variants remain directional so their one-way routing stays explicit.
- Existing manually assigned Quantum Storage behavior is unchanged; empty Quantum Storage still never auto-assigns an item type.
- The topology inspector keeps all 36 machine-list slots plus Refresh/summary/filter controls without directional selectors.

## 1.0.29

### Classic Networks storage routing
- Restored the original Networks withdrawal priority: **Network Cells → crafter outputs → Greedy storage → exposed deep storage**.
- Network Cells remain normal loose network storage; they are not forced into an overflow-only role.
- Grid deposits keep the original behavior: matching deep storage exposed by a Network Monitor can accept items before Cells.
- Network Pushers remain the active transfer path for moving configured items from the network into an adjacent Slimefun machine or assigned Quantum Storage.
- Quantum Storage remains manually assigned and never auto-binds itself from a Grid/network deposit.
- Added in-game Monitor/Pusher/Quantum Storage guidance and verification so classic routing cannot silently drift again.

## 1.0.28

### Manual Quantum Storage routing
- Keeps the correction from PR #32: empty/unassigned Network Quantum Storage is **not** auto-bound by Grid or network deposits. Players still explicitly assign its item type.
- Matching assigned Quantum Storage continues to receive items before Network Cells; if it fills, only the remaining amount can fall through to Cells.
- Fixed a live-cache edge case found during review: if a Quantum Storage was manually assigned after the root had already cached its storage identity, the cached identity could still contain the old null item template and reject deposits until a network rebuild.
- Network-backed Quantum Storage matching now checks the live `QuantumCache`, so a newly manually assigned storage starts receiving matching items immediately without requiring Refresh/rebuild.
- Unassigned Quantum Storage remains ignored by ordinary network routing.

## 1.0.27

### Network Monitor GUI direction fix
- Fixed the Network Monitor's six inherited directional selectors overwriting machine entries in the topology inspector.
- The entire upper four rows (36 slots) now belong exclusively to the machine overview/detail list.
- The Monitor keeps its original adjacent-storage behavior, but direction is configured through one **Storage Direction** hopper button in the bottom toolbar.
- Click the Storage Direction button to cycle North → East → South → West → Up → Down. Shift-click opens the currently selected adjacent target.
- The toolbar shows the current direction and adjacent target when loaded, while keeping unloaded neighboring chunks untouched.
- Other directional Networks machines keep their existing six-direction GUI unchanged.

## 1.0.26

### Network Monitor individual-node inspector
- Clicking a grouped machine type in the Network Monitor now opens a paginated list of every individual node in that group.
- Each node entry shows its world, exact block coordinates, Networks node type, Slimefun item ID when known, and current Active/Inactive state.
- Inactive entries now report the concrete reason when possible: unloaded chunk, missing runtime node, unassigned node, wrong root, missing Slimefun block data, pending removal, unresolved item, missing item ID, or ID mismatch.
- Detail pages list inactive nodes first so network-reading problems are immediately visible.
- Added a click-to-cycle **All / Active / Inactive** filter while viewing a machine type.
- Clicking an individual loaded node highlights that exact block with player-only particles for a configurable duration. Cross-world, unloaded, or very distant nodes fall back to exact coordinates instead of force-loading chunks.
- Added `features.network-monitor-inspector.highlight-connected-nodes` and `highlight-seconds` (default 10, clamped to 1-30 seconds).
- Refresh remains available from both overview and detail pages and still performs a real controller topology rediscovery before rebuilding the snapshot.

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
