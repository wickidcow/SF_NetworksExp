# Networks Expansion completion checkpoint

Updated: **2026-10-21 UTC**. This is the current release record. Earlier investigation queues are superseded by the evidence below; the [previous checkpoint remains in Git history](https://github.com/wickidcow/SF_NetworksExp/blob/175dcae24fd311bef0b3daf2bafb1a51af913e07/NETWORKS_EXPANSION_COMPLETION.md).

## Resume here

**Networks 1.0.60 and InfinityExpansion2 2.0.15 are merged, published, and verified. SF_Addons r140 is saved with the two official release JARs.** All known scoped item-conservation, IE2, recipe-control, and Paper/Purpur release blockers described in the earlier checkpoint are resolved. Folia runtime support remains an unmet platform requirement: neither addon declares it, and cross-region transactions have not been made safe. This release does not claim full Folia support.

Before new work, read this file and inspect the current Git branch, worktree, PR heads, and master. Continue from the released source below. Do not recreate PR #98, reapply incorporated patches, rerun accepted native suites without a concrete risk, or overwrite immutable releases.

**Exact next action for this release:** merge the documentation-only checkpoint through the existing CI gates. When this file is on master with those checks passing, no publication or ZIP-update action remains. Future Folia implementation must establish ownership and coordinated inventory writes on every affected region before enabling either plugin there; a descriptor flag alone is insufficient.

## Published artifacts

| Addon | Accepted source | Published tag / target | Integration |
|---|---|---|---|
| Networks 1.0.60 | `175dcae24fd311bef0b3daf2bafb1a51af913e07` | [v1.0.60](https://github.com/wickidcow/SF_NetworksExp/releases/tag/v1.0.60) -> `d41d362c8b71073dca41abc24f6ef6a5a8688fff` | [PR #104](https://github.com/wickidcow/SF_NetworksExp/pull/104), reviewed and merged normally |
| InfinityExpansion2 2.0.15 | `8d876da50973f8c295be61d30ded7a25e5628602`; production `19e4943a53c44589a6cfcf618150358f7bdf5a1c` | [v2.0.15](https://github.com/wickidcow/SF_InfinityExpansion2/releases/tag/v2.0.15) -> `168815bb99e5701d3680095a9eb9170d83a19740` | [PR #32](https://github.com/wickidcow/SF_InfinityExpansion2/pull/32) merged at `557a01ce09419461159e58272ed0f0e17512b3fc`; [publisher-only PR #34](https://github.com/wickidcow/SF_InfinityExpansion2/pull/34) merged at the release target |

Networks merge tree `3a3348663755c0536ff838ebf977cc4c7d00955a` is identical to the accepted candidate. IE2 PR #32 merge tree `25bd6adda38f48c43cd61d2afe5ceabd0cf95624` is identical to its accepted head; PR #34 changes only the artifact downloader in `.github/workflows/ci.yml`. Its production sources and resulting JAR are unchanged.

Both public releases are stable, non-draft releases. Their actual downloadable assets were fetched and compared byte-for-byte with the accepted test binaries, including CRC, embedded version, and Java 21 bytecode checks.

| Release asset | Bytes | SHA-256 | GitHub asset |
|---|---:|---|---:|
| `SF_Networks1.0.60.jar` | 1,681,029 | `e355ce958bd1e3d3c8ddd65d7f56dbd2b8d4c605ca6b0f1025a34e65c6a808b2` | 661228146 |
| `SF_InfinityExpansion2.0.15.jar` | 1,707,539 | `897bd4de70c9219217ecfb0f8cf68f0147b6c38758a239085db39718dd686eef` | 661246651 |

Networks release 419112723 was published at 03:24:55 UTC by [workflow 39537659539](https://github.com/wickidcow/SF_NetworksExp/actions/runs/39537659539), after every required gate passed. IE2 release 419121965 was published at 03:34:45 UTC by [workflow 39539123498](https://github.com/wickidcow/SF_InfinityExpansion2/actions/runs/39539123498), with compatibility, source safety, and publication all successful. Its explicit confirmation was `2.0.15`.

### SF_Addons r140

- File: **`SF_Addons_r140_1.21.11-26.3.zip`**, saved successfully as the replacement for the existing bundle.
- SHA-256: **`9254a5ef4ee568349ad26a520dbba79c93f0bc0e3c9d9c8b5f6aa589621ba9f1`**.
- Size: **24,584,646 bytes**; **45 JARs / 48 unique entries**.
- Base r139 SHA-256: `541708ca969bb6d6c7a0b11510e856b221c2dbd984e6523404a53ebdaa60ff1c`.
- Only Networks 1.0.56 and IE2 2.0.13 were replaced, by the official assets above. The other **43 JARs and their individual manifest entries are unchanged**.
- Outer ZIP and all 45 inner JAR CRC checks pass. Every manifest and `SHA256SUMS.txt` digest matches the actual bytes. The two old target JARs are absent.
- Manifest and compatibility notes record exact release commits, asset IDs, acceptance evidence, Java/API targets, and platform limitations. Historical provenance for the untouched addons is retained.
- No Slimefun core JAR, unrelated addon change, resource pack, or live-server data is included. No unrelated Slimefun Legacy change was published.

An independent read-only verification confirmed the complete bundle, both live release digests, all unrelated bytes, and the platform notes.

## Completed fixes and verification

| Area | Implemented behavior | Evidence |
|---|---|---|
| Inventory transfers | Live source, cursor, player-inventory and destination receipts determine the committed amount. Partial/rejected writes, write-then-throw outcomes, stale menus and reentry are handled without speculative refunds. Existing input/output/grabber/pusher/crafting guards are retained. | Full 665-test Networks suite; Grid/IE2 player and storage native workflows; earlier incident regressions retained. |
| Drawer menu and Quantum | Drawer ownership spans authoritative slot/cursor readback; exact item metadata, capacity, lock/void settings and 64-bit quantities are retained. Transfer writers receive independent snapshots so a setter cannot mutate its expected receipt. | Four new alias cases reproduced two quantity defects and two identity defects before the fix; 39 affected tests pass. Final actual-click/restart ledger is exactly 3,000,000,086. |
| Drawer persistence | Flushed exact-identity template journal precedes new-type registration. Rejected/cancelled amount writes remain pending and shutdown checkpoints them. Negative/overcapacity records and associated physical items are retained for recovery, not clamped or deleted. | Durable registration, pending deletion, enqueue ordering, shutdown and invalid-balance tests; final native restart with all 28 stopped storage-file digests matching. |
| ItemMover | Full identity and long balance are prepared together. Menu and main/off-hand writes are verified; stale events cannot reuse stock. Ambiguous writes retain exact original/prepared/offered evidence and block replay. | Unit receipt regressions; five actual native gestures; independent reload of healthy 17, Long.MAX_VALUE, exact pending bytes, empty stale mover, and Quantum total 101. Audit instrumentation disabled for acceptance. |
| IE2 storage | Input/output/withdrawal operations hold ownership through physical readback, including cross-direction callbacks and partial writes. Authorized zero-balance retyping uses the exact incoming identity. Invalid balances fail closed without guessing identity. | 158 passing IE2 tests, six source verifiers, coordinated storage and player native suites. |
| IE2 block break | Prepare exact cache/output identity before mutation; retain physical output with Void Excess; roll back captured entities before restoring source. RuntimeException/LinkageError use verified recovery, while fatal VM errors are rethrown. | Five native break-event cases plus one injected slot-clear LinkageError handler case; verified rollback/retry, real placement, and independent 11-item reload. |
| Infinity Dust Extractor | Retained the synchronized producer repair and validated ordinary growth, metadata, partial/VOID transfer and compensation behavior. No asynchronous producer workaround or guessed item identity was added. | 152 native production/line-transfer cases, 38 per tier, and all four registered synchronous ticker checks pass. |
| Recipe-aware pushers | Classic/More/Best have persistent per-pusher ON/OFF controls and targets of 1-64 batches. Supported recipes, fresh item-aware permissions, complete physical input counts and dynamic declarations govern top-up. OFF behavior is preserved. Foreign control-slot items remain recoverable and block unsafe breaking. | Unit regressions; 24 actual GUI clicks, three foreign-stack break refusals, 39 preserved typed/PDC items, actual smeltery ratios, OFF 64 parity, no excess refill on re-enable, and persisted batch-five settings. |
| Performance | Target classification is reused within a line pass; confirmed missing sources skip target work; covered capacity requests stop further scanning. Existing routing and amount budgets are retained. | Seven operation-count regressions. No server-wide TPS improvement percentage is claimed. |

See [Networks release notes](docs/releases/1.0.60.md) and [IE2 release notes](https://github.com/wickidcow/SF_InfinityExpansion2/blob/v2.0.15/docs/releases/2.0.15.md) for operator-facing behavior. The plugin remains `Networks` with all **289 registered IDs**, existing recipes and established persistent namespaces.

## Successful build and native gates

### Exact final candidates

- Networks strict clean build: **665 tests / 65 suites, zero failures, errors or skips**. Source contract, universal JAR, Java 21 bytecode (536 owned classes), no bundled Slimefun/optional APIs and no hard InfinityExpansion1 linkage all pass.
- Networks universal workflows [39537008996](https://github.com/wickidcow/SF_NetworksExp/actions/runs/39537008996) and [39537012642](https://github.com/wickidcow/SF_NetworksExp/actions/runs/39537012642): all eight gates pass on `175dcae2`.
- IE2: **158 tests, zero failures, errors or skips**, all six source verifiers, version/bytecode/JAR checks pass. Exact-head workflows [39536618288](https://github.com/wickidcow/SF_InfinityExpansion2/actions/runs/39536618288) and [39536611861](https://github.com/wickidcow/SF_InfinityExpansion2/actions/runs/39536611861) pass on `8d876da5`.
- Publisher-only IE2 fix `76b2c8c3844bc9d63c0e5e430eb7a54fa6961906`: workflows [39538766853](https://github.com/wickidcow/SF_InfinityExpansion2/actions/runs/39538766853) and [39538780758](https://github.com/wickidcow/SF_InfinityExpansion2/actions/runs/39538780758) pass before merge. Corrected publication then passes on `168815bb`.

### Native evidence bound to the released JARs

| Gate | Successful workflow | Result |
|---|---|---|
| Grid / IE2 player | [39537009159](https://github.com/wickidcow/SF_NetworksExp/actions/runs/39537009159) | Actual Grid/crafting and IE2 player operations, paired 24 round trips and 192-item restart ledger. |
| Drawer / Quantum | [39537009109](https://github.com/wickidcow/SF_NetworksExp/actions/runs/39537009109) | Four JVMs 4662/4773/4900/5034; 19 seed clicks + 2 reload clicks; exact total 3,000,000,086 = Drawer 13 + large Quantum 3,000,000,072 + small typed Quantum 0 + chosen item 1. |
| Recipe pushers | [39537009201](https://github.com/wickidcow/SF_NetworksExp/actions/runs/39537009201) | Four JVMs 4584/4713/4839/4989; exact 256 per ingredient in the smeltery ledger, persistent controls and templates, OFF-mode parity, physical foreign-stack protection. |
| ItemMover / Dust | [39537009022](https://github.com/wickidcow/SF_NetworksExp/actions/runs/39537009022) | ItemMover seed 4553 / verify 4681, audit OFF, Quantum 101; all 152 Dust cases and four synchronous tickers pass. |

The downloaded native evidence manifests record the tested Networks `e355ce95...` and IE2 `897bd4de...` JAR SHA-256 values, which match the published release assets:

| Suite | Actions artifact ID | Evidence ZIP SHA-256 |
|---|---:|---|
| Drawer | 12318361922 | `9a916efc6248dd8101db453413f5ea8858ea15f03e1a6b5b4ebbc10599efb3c6` |
| Recipe | 12318192465 | `6ee83a4090a1495cb30d9aa0e1e9268b34d69cf0dd6b90206a0f41f7c8b6718b` |
| ItemMover / Dust | 12318421633 | `1dd0c7828f37b5f002f47b550738708cf359009706395d9e520678a87ec5b55c` |

Local audit-OFF ItemMover acceptance also passes with Java 21, independent JVMs 42757/42920 and the unchanged accepted JAR. All runtime and trace files stayed in process-local temporary storage until both JVMs exited. The stopped seed database SHA-256 is `0214b2ab94bfed6b84cd8cdaf2c609a116cb619924ea08e0e2b4c3a113396af8`; no WAL/SHM remained. Local final IE2 break acceptance used JVMs 40003/40438 and the unchanged production `19e4943a` binary.

### Tested core and platform scope

The pinned GuGu Slimefun Legacy source is `1cb32d6e95b305998065b8ef6b936a56f679187d`. Hosted core JAR SHA-256 is `cec822e34f0d9b2f27b5e7b8190e4a2e9625e634144a219a30d7a3394a4ba783`; local core JAR SHA-256 is `251e9075bb6d1629790d845569fb90994e4ff1ed76408400ee733d4041bb9e3e`. These are separate build artifacts from the same recorded source, not interchangeable hash claims. Core XML contains 941 test entries with zero failures/errors and one existing optional external-database assumption skip. Neither addon suite skips tests.

| Platform / API | Established evidence | Boundary |
|---|---|---|
| Java 21 / Paper 1.21.11 API floor | Both published JARs are Java 21 compatible; strict baseline build. | Native Paper 26.3 runs use Java 25. |
| Paper 26.3 | Networks API compile at build 176 and native manager/Quantum/persistence checks at builds 176 and 194. IE2 separately compiles against 1.21.11, 26.2 and 26.3. | IE2 coordinated machine/player acceptance was on Purpur; do not claim every IE2 native case on Paper 26.3. |
| Purpur 1.21.11 build 2568 | Native manager, storage, player, pusher, Drawer, Quantum, ItemMover, Dust and restart fixtures. | No untested future Purpur release is certified. |
| Folia | **Unsupported by these addons.** Both descriptors omit Folia opt-in; cross-region transaction ownership remains unimplemented. | This is an addon limitation, not a claim that the pinned core lacks Folia support. |
| Leaf | No new runtime acceptance established by this refresh. | Do not infer it from Paper/Purpur success. |

GuGu Slimefun Legacy is the maintenance target. Existing auxiliary United/Gugu compatibility gates were retained and passed; no new development was directed at reviving abandoned United support.

## Failures retained and resolved

| Failed evidence | Concrete disposition |
|---|---|
| Four independent inventory-setter alias regressions: existing 35 pass / new four fail; source case duplicated 44, destination case lost 212, Quantum cases accepted modified owner metadata. | Three helper writers now receive independent plain snapshots. All 39 affected tests and the complete 665-test suite pass. |
| Earlier Drawer menu, Quantum, ItemMover capacity, invalid-balance and recipe-buffer/break regressions. | Each was implemented and retained as an automated regression. The previous checkpoint contains individual RED counts and accepted fixes; they are not pending work. |
| Local native SQLite restarts in the shared workspace reverted stopped seed state. | Retained as **FAIL_DIAGNOSTIC**. Prior core evidence traced `.rsync-tmp` restoration of deleted WAL/SHM/markers; current recipe evidence proves stale-WAL replay/truncation but does not establish the current actor. Acceptance used isolated process-local runtime storage and independent hosted CI without weakening assertions. |
| Initial IE2 publisher [39537784165](https://github.com/wickidcow/SF_InfinityExpansion2/actions/runs/39537784165). | Build/safety gates passed, publication failed before tag/release creation: download-artifact v6 did not preserve the raw JAR uploaded with `archive: false`. PR #34 changes only to v8 with `skip-decompress: true`. Corrected run 39539123498 preserves SHA `897bd4de...` and publishes successfully. |

Do not repeat the resolved workspace SQLite investigation or patch core storage speculatively. Its [existing core checkpoint](https://github.com/wickidcow/Slimefun-Legacy/blob/1cb32d6e95b305998065b8ef6b936a56f679187d/docs/release-candidates/4.1.72.md#storage-assessment-and-remaining-attribution) remains the attribution record. Do not force flushes, reseed a restart fixture, add audit reads as a hidden acceptance dependency, or remove conservation assertions.

## Reviews and existing PR disposition

Independent automated-agent reviews are recorded honestly as automated reviews, not human approvals: Networks review **5594544313**, IE2 review **5594513572**, publisher fix review **5594638779**. The final production PRs had no unresolved review threads. All merges used normal GitHub merge operations after the relevant checks passed; no failing test was bypassed.

| Repository | PRs | Final disposition |
|---|---|---|
| Networks | #98 | Already merged before this continuation; retained as the starting baseline. |
| Networks | #96, #99 | Merged; performance and cursor work incorporated, not recreated. |
| Networks | #104 | Final integration reviewed, marked ready and merged at `d41d362c`. |
| Networks | #100, #102 | Closed with explicit supersession notes; their heads are ancestors of the accepted release. |
| Networks | #101 | Closed with exact-integration evidence; its two production and two test blobs are included. |
| Networks | #103 | Closed as the superseded workflow-only alternate; final workflows use accepted coordinated IE2 source. |
| IE2 | #30 | Already merged and retained. |
| IE2 | #31 | Closed with exact integration evidence; input receipt helper/test imported and combined with existing ownership guards. |
| IE2 | #32, #34 | Both reviewed and merged; production repairs plus the narrowly scoped publisher correction. |

The five superseded PRs were closed without deleting branches or history. No unrelated Slimefun Legacy release or merge was performed for this delivery.

## Remaining issues and limits

- **Folia runtime support remains unmet**, as stated above. Cross-region inventory/world operations need a dedicated ownership and coordination implementation before either addon can safely opt in.
- Ambiguous external adapter writes preserve exact recovery evidence and hold further transfers; they do not authorize deleting contents, guessing identities, or issuing speculative refunds. Existing corrupted data still needs evidence-based reconciliation.
- Synchronous receipts, clean shutdown and independent-JVM restart tests do not establish arbitrary process-crash atomicity across the core database, inventory persistence and world item entities.
- Performance reductions are verified as operation counts; no live server-wide TPS percentage was measured.
- No known unresolved blocker remains for the published, tested Paper/Purpur release. The other 43 bundled addons were preserved, not rebuilt or newly certified.

## Files changed in the accepted integration

The inventory below records the Networks and coordinated IE2 integration files tracked for this acceptance. Earlier repairs remain in the preserved commit history and previous checkpoint. The final documentation follow-up updates this file and `LEGACY_COMPATIBILITY.md` only; it does not change the published production tree or release assets.

### Networks — 80 recorded integration files

- `.github/workflows/build.yml`
- `.github/workflows/compatibility.yml`
- `.github/workflows/native-drawer-player.yml`
- `.github/workflows/native-grid-player.yml`
- `.github/workflows/native-ie2-player.yml`
- `.github/workflows/native-item-mover.yml`
- `.github/workflows/native-recipe-pusher.yml`
- `.github/workflows/paired-ie2-storage.yml`
- `.gitignore`
- `CHANGELOG.md`
- `NETWORKS_EXPANSION_COMPLETION.md`
- `build.gradle.kts`
- `docs/dust-extractor-source-snapshot.md`
- `docs/native-item-mover-receipts.md`
- `docs/releases/1.0.60.md`
- `scripts/download_jeg.sh`
- `scripts/native_drawer_player_probe.py`
- `scripts/native_grid_player_probe.py`
- `scripts/native_ie2_player_probe.py`
- `scripts/native_item_mover_receipts.py`
- `scripts/native_recipe_pusher_probe.py`
- `scripts/prepare_infinity_dust_probe.py`
- `scripts/verify_legacy_compatibility.py`
- `src/main/java/com/balugaq/netex/api/data/StorageUnitData.java`
- `src/main/java/com/balugaq/netex/utils/LineOperationUtil.java`
- `src/main/java/com/ytdd9527/networksexpansion/implementation/machines/unit/DrawerItemMoverTransfer.java`
- `src/main/java/com/ytdd9527/networksexpansion/implementation/machines/unit/DrawerMenuTransfer.java`
- `src/main/java/com/ytdd9527/networksexpansion/implementation/machines/unit/DrawerQuantumDepositReceipt.java`
- `src/main/java/com/ytdd9527/networksexpansion/implementation/machines/unit/DrawerQuantumTransferMath.java`
- `src/main/java/com/ytdd9527/networksexpansion/implementation/machines/unit/DrawerQuantumWithdrawalReceipt.java`
- `src/main/java/com/ytdd9527/networksexpansion/implementation/machines/unit/NetworksDrawer.java`
- `src/main/java/com/ytdd9527/networksexpansion/implementation/tools/ItemMover.java`
- `src/main/java/com/ytdd9527/networksexpansion/implementation/tools/ItemMoverStorage.java`
- `src/main/java/com/ytdd9527/networksexpansion/utils/databases/DataSource.java`
- `src/main/java/com/ytdd9527/networksexpansion/utils/databases/DataStorage.java`
- `src/main/java/com/ytdd9527/networksexpansion/utils/databases/DatabaseBackupManager.java`
- `src/main/java/com/ytdd9527/networksexpansion/utils/databases/DrawerTemplateJournal.java`
- `src/main/java/io/github/sefiraat/networks/Networks.java`
- `src/main/java/io/github/sefiraat/networks/integrations/infinityexpansion2/IE2CacheTransferHold.java`
- `src/main/java/io/github/sefiraat/networks/integrations/infinityexpansion2/InfinityExpansion2Integration.java`
- `src/main/java/io/github/sefiraat/networks/network/barrel/InfinityExpansion2Barrel.java`
- `src/main/java/io/github/sefiraat/networks/network/barrel/InfinityExpansion2Withdrawal.java`
- `src/main/java/io/github/sefiraat/networks/slimefun/network/pusher/AbstractNetworkPusher.java`
- `src/main/java/io/github/sefiraat/networks/slimefun/network/pusher/RecipeAwareControlSlot.java`
- `src/main/java/io/github/sefiraat/networks/utils/NetworkTransferUtils.java`
- `src/test/java/com/balugaq/netex/api/data/DrawerInvalidBalanceTest.java`
- `src/test/java/com/balugaq/netex/api/data/DrawerWithdrawalPreparationTest.java`
- `src/test/java/com/balugaq/netex/utils/LineOperationUtilTest.java`
- `src/test/java/com/ytdd9527/networksexpansion/implementation/machines/unit/DrawerItemMoverReceiptTest.java`
- `src/test/java/com/ytdd9527/networksexpansion/implementation/machines/unit/DrawerMenuReceiptTest.java`
- `src/test/java/com/ytdd9527/networksexpansion/implementation/machines/unit/DrawerQuantumDepositReceiptTest.java`
- `src/test/java/com/ytdd9527/networksexpansion/implementation/machines/unit/DrawerQuantumQuickTransferSourceContractTest.java`
- `src/test/java/com/ytdd9527/networksexpansion/implementation/machines/unit/DrawerQuantumTransferMathTest.java`
- `src/test/java/com/ytdd9527/networksexpansion/implementation/tools/ItemMoverStorageTest.java`
- `src/test/java/com/ytdd9527/networksexpansion/utils/databases/DrawerAutosaveEnqueueOrderTest.java`
- `src/test/java/com/ytdd9527/networksexpansion/utils/databases/DrawerPendingDeletionConservationTest.java`
- `src/test/java/com/ytdd9527/networksexpansion/utils/databases/DrawerShutdownReceiptTest.java`
- `src/test/java/com/ytdd9527/networksexpansion/utils/databases/DrawerTemplateJournalTest.java`
- `src/test/java/com/ytdd9527/networksexpansion/utils/databases/DrawerTemplateRegistrationTest.java`
- `src/test/java/io/github/sefiraat/networks/integrations/infinityexpansion2/IE2CacheTransferHoldTest.java`
- `src/test/java/io/github/sefiraat/networks/network/barrel/InfinityExpansion2WithdrawalGuardTest.java`
- `src/test/java/io/github/sefiraat/networks/network/barrel/InfinityExpansion2WithdrawalReceiptTest.java`
- `src/test/java/io/github/sefiraat/networks/slimefun/network/pusher/RecipeAwareBreakGuardTest.java`
- `src/test/java/io/github/sefiraat/networks/slimefun/network/pusher/RecipeAwareControlSlotTest.java`
- `src/test/java/io/github/sefiraat/networks/slimefun/network/pusher/RecipeAwareFullInputBufferTest.java`
- `src/test/java/io/github/sefiraat/networks/slimefun/network/pusher/RecipeAwareInitialControlHandlerTest.java`
- `src/test/java/io/github/sefiraat/networks/slimefun/network/pusher/RecipeAwarePhysicalInputDeclarationTest.java`
- `src/test/java/io/github/sefiraat/networks/utils/NetworkCursorPrecommitConservationTest.java`
- `tests/runtime/InfinityDustTransferProbe.java`
- `tests/runtime/NativeDrawerPlayerProbe.java`
- `tests/runtime/NativeGridPlayerProbe.java`
- `tests/runtime/NativeIE2PlayerProbe.java`
- `tests/runtime/NativeItemMoverReceiptProbe.java`
- `tests/runtime/NativeRecipePusherProbe.java`
- `tests/runtime/NativeRecipeTransferProbe.java`
- `tests/runtime/native_drawer_player_client.cjs`
- `tests/runtime/native_grid_player_client.cjs`
- `tests/runtime/native_ie2_player_client.cjs`
- `tests/runtime/native_item_mover_client.cjs`
- `tests/runtime/native_recipe_pusher_client.cjs`

### InfinityExpansion2 — 29 recorded integration files

- `.github/workflows/ci.yml`
- `.github/workflows/paired-networks-storage.yml`
- `CHANGELOG_FORK.md`
- `build.gradle.kts`
- `docs/native-storage-break-receipts.md`
- `docs/releases/2.0.15.md`
- `scripts/native_ie2_break_probe.py`
- `scripts/verify_legacy_migration.py`
- `src/main/java/net/guizhanss/infinityexpansion2/implementation/items/storage/StorageBreakDropReceipt.java`
- `src/main/java/net/guizhanss/infinityexpansion2/implementation/items/storage/StorageInputReceipt.java`
- `src/main/java/net/guizhanss/infinityexpansion2/implementation/items/storage/StorageOutputReceipt.java`
- `src/main/kotlin/net/guizhanss/infinityexpansion2/implementation/items/storage/StorageCache.kt`
- `src/main/kotlin/net/guizhanss/infinityexpansion2/implementation/items/storage/StorageUnit.kt`
- `src/main/kotlin/net/guizhanss/infinityexpansion2/implementation/items/storage/StorageWithdrawal.kt`
- `src/test/java/net/guizhanss/infinityexpansion2/implementation/items/storage/StorageBreakDropReceiptTest.java`
- `src/test/java/net/guizhanss/infinityexpansion2/implementation/items/storage/StorageBreakSnapshotTest.java`
- `src/test/java/net/guizhanss/infinityexpansion2/implementation/items/storage/StorageCrossDirectionTest.java`
- `src/test/java/net/guizhanss/infinityexpansion2/implementation/items/storage/StorageInputIncidentBaselineTest.java`
- `src/test/java/net/guizhanss/infinityexpansion2/implementation/items/storage/StorageInputOrderingTest.java`
- `src/test/java/net/guizhanss/infinityexpansion2/implementation/items/storage/StorageInputReceiptTest.java`
- `src/test/java/net/guizhanss/infinityexpansion2/implementation/items/storage/StorageInvalidBalanceGuardTest.java`
- `src/test/java/net/guizhanss/infinityexpansion2/implementation/items/storage/StorageOutputReceiptTest.java`
- `src/test/java/net/guizhanss/infinityexpansion2/implementation/items/storage/StorageSeedTemplateIntegrityTest.java`
- `src/test/java/net/guizhanss/infinityexpansion2/implementation/items/storage/StorageTickerOutputOrderingTest.java`
- `src/test/java/net/guizhanss/infinityexpansion2/implementation/items/storage/StorageWithdrawalConservationTest.java`
- `src/test/java/net/guizhanss/infinityexpansion2/implementation/items/storage/StorageWithdrawalReceiptTest.java`
- `src/test/java/net/guizhanss/infinityexpansion2/implementation/items/storage/StorageWithdrawalTest.java`
- `tests/runtime/NativeIE2BreakProbe.java`
- `tests/runtime/native_ie2_break_client.cjs`

The exact publisher correction is limited to `.github/workflows/ci.yml`; accepted source, tests, build and immutability guards remain unchanged.
