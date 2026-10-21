# Networks Expansion completion checkpoint

Updated October 20, 2026 America/New_York (October 21 UTC in CI).

## Resume here — do not restart accepted work


### Current execution checkpoint — final native acceptance and receipt alias fix

This is the final **665-test Networks1.0.60 candidate**, based on b5e48c5c8da4ddab93cabafca44349ee4ad26a2e, on PR104 to master. IE2 evidence head is **8d876da50973f8c295be61d30ded7a25e5628602**, version2.0.15, PR32 to master; production is unchanged from19e4943a. No master merge or publication yet.

**Do not repeat the local SQLite investigation.** The pinned core already documents the same startup file-restoration diagnostic in [its4.1.72 checkpoint](https://github.com/wickidcow/Slimefun-Legacy/blob/1cb32d6e95b305998065b8ef6b936a56f679187d/docs/release-candidates/4.1.72.md#storage-assessment-and-remaining-attribution), from PR362/363. Prior run03 traced matched `.rsync-tmp` moves restoring deleted WAL/SHM/clean-marker files. Current recipe evidence proves stale-WAL replay/truncation byte-for-byte, but does not identify the current actor. Current local failures remain FAIL_DIAGNOSTIC. For native local acceptance, keep **all mutable runtime and trace files in process-local `/tmp` until every server JVM exits**, then export the evidence. Independent GitHub CI is the other accepted environment. Do not add speculative core fixes, force database flushes, reseed restart state or remove assertions.

**Accepted evidence at b5e48c5c:**

Both universal workflows39535135880/39535130407 PASS all eight gates, including native Paper26.3 builds176/194 and Purpur persistence. The following are prior-head behavioral results; the three-helper repair above has its own final gates.

- Recipe workflow **39535130524**, job119009912281 PASS: four distinct JVMs6542/6652/6780/6929;192-item restart ledger; actual Classic/More/Best controls,3foreign-stack break refusals,24GUI clicks,39typed-PDC items, batch5/chat/reopen; real smeltery ratios,OFF64 and no refill when re-enabled; exact256-per-ingredient ledger and persisted settings/templates/player contents. Artifact12317400679 SHA256`8c47282e39e72f551b5135461730c01044acb26fbe6b06108dd1727716319a74`.
- Drawer workflow **39535130437**, job119009911833 PASS: four JVMs6773/6882/7007/7144;192 prerequisite ledger,19seedclicks+2reloadclicks, exact3,000,000,086 total with64-bitQuantum/PDC/lock/void/settings preserved. Artifact12317264138 SHA256`951a13500974bc67b3f77ee012a38155fc07ce449f5f8f4b5f4117df60198fc7`.
- Grid/IE2 player workflow **39535130411**, job119009911420 PASS.
- Both downloaded recipe/Drawer artifacts contain the exact canonical NetworksJAR SHA`0588ad1e19281293daee6dd419c51d92e5b9dc7923d707dd27157b14b9238ae3` and IE2JAR SHA`897bd4de70c9219217ecfb0f8cf68f0147b6c38758a239085db39718dd686eef`. CI coreJAR SHA`cec822e34f0d9b2f27b5e7b8190e4a2e9625e634144a219a30d7a3394a4ba783` differs from the local build but names the same1cb source; do not conflate those build artifacts. Networks661 andIE2158 tests have zero failures/errors/skips. Core941 entries have zero failures/errors and one existing optional real-database assumption skip.
- Reused Dust Extractor fixture passes **152real production/line-transfer cases** on the final b5/19e/local1cb pair: synchronized registered tickers, exact full metadata/PDC, partial/VOID transfers and compensation. The four obsolete producer-growth abort cases were replaced by16exact normal/VOID growth ledgers; metadata/replacement/empty source guards remain. Single-JVM acceptance does not claim restart or a real destination network (sink is controlled).
- Final IE2 six-break-case/player-placement/reload acceptance now PASS in isolated temporary storage, exported only after bothJVMs exited: seed40003/verify40438; exact11-item independent reload. Source19e/b5/local1cb and JARs unchanged. Evidence `build-tools/native-runtime/ie2-final-b5e48c5c-19e4943a-isolated`.

**Final review defect fixed and independently reviewed:** a custom inventory setter could mutate the same prepared ItemStack later used as its expected receipt. Clean RED:39tests, existing35pass,4newfailures (ItemMover source44duplication/destination212loss; two Quantum owner-PDC aliases). The three writer calls now pass independent plain snapshots; affected39tests pass. Root integrated the exact five-file repair from independently reviewed local7e92a2e2 and ran a full strict clean build: **665tests, zero failures/errors/skips**,65suites. Source289IDs, Java21bytecode536classes, universal JAR and ZIP CRC pass. Immutable1.0.60JAR SHA256 **e355ce958bd1e3d3c8ddd65d7f56dbd2b8d4c605ca6b0f1025a34e65c6a808b2**, byte-identical to the reviewer build. Final pusher review found no additional blocker. No broader investigation is required.

**Included reproducible fixtures:** native ItemMover runner/client/Java/doc, isolated GitHub ItemMover+Dust gate, reused Dust probe/preparer and phase-specific Drawer readiness/stopped-file evidence. SQL diagnostic SELECTs in ItemMover are opt-in and off by default; an audit-enabled pass is not allowed to hide the failed local no-audit control. The final gate runs without that instrumentation.

**Exact next action:** require this final Networks candidate's CI/native gates, including the new audit-off ItemMover and Dust workflow. Complete IE2 evidence-head CI/review and master integration; its production and accepted native JARs are unchanged. Then use the existing gated publishers for1.0.60/2.0.15 and update only the two applicable JARs in SF_Addons. Existing versioned release artifacts remain immutable.

### Active final integration — October 21 UTC

This commit is the combined Networks **1.0.60** candidate on PR **#104**, based on `1266e7a0f7461ba40af331ede42c288d848f49fc`. PR #104 now targets **master**. Coordinated IE2 **2.0.15** is committed at `19e4943a53c44589a6cfcf618150358f7bdf5a1c` in PR **#32**, also targeting master. Both remain draft until the final native acceptance gates pass. Neither release has been published and the SF_Addons ZIP has not been replaced.

**Completed in this Networks batch:**

- `StorageUnitData` and `DrawerInvalidBalanceTest`: invalid negative or overcapacity deposits retain the original record and the complete physical input, including with Void Excess. Negative unlocked withdrawals retain the canonical record. Clean RED deposit cases:12invalid failures+4valid controls; withdrawal cases:2unlocked failures+2locked controls. All20new cases now pass.
- `AbstractNetworkPusher`, two new test classes and native fixtures: count full physical inputs before recipe top-up while retaining dynamic insertion permissions. Native1266 CI39532219093 and actual local smeltery actions reproduced OFF-to-ON refill. Eight new buffer regressions pass. A real break-before-pickup action reproduced foreign control-slot loss; nine guard cases pass. The existing59pusher cases pass.
- `DrawerItemMoverTransfer`, the actual `NetworksDrawer` hookup and17real-handler regressions: baseline16cases had14failures and2healthy controls. Authoritative slot writes/readbacks now precede opposite-endpoint changes. Snapshot reads, rejected writes, write-then-throw, reentry, detached menus, long limits, first assignment and full drain pass. A reached live metadata-read callback reproduced a capacity-change race after the initial check (16pass/1fail); the final prewrite capacity recheck fixes it. Safe preparation does not invoke wrapper metadata writers. Full item data comparisons avoid cross-wrapper equality casts.
- `ItemMover`, new `ItemMoverStorage` and35helper regressions from agent production18e1d9ad: prepare full identity and long amount together, validate malformed/conflicting balances, reserve exact serialized transfer evidence, verify actual menu/hand writes and retain unresolved receipts. Native rejected candidates34175/427c5eb exposed wrapper serialization/equality failures; final plain material+amount+full live metadata snapshots address them. Do not reuse rejected candidates.
- Native Drawer fixture/workflow/client/runner added. Version, changelog, release notes, active workflows and source verifier updated to1.0.60. Native workflows pin IE2final19e4943a and Legacycore1cb32d6e. All existing build/release gates remain enabled.

**Verified combined build:** strict Gradle `clean build` **661tests, zero failures/errors/skips**,65suites; source compatibility289item IDs; Java21bytecode536owned classes; universal JAR and ZIP CRC pass. Local candidate `SF_Networks1.0.60.jar` SHA256 **0588ad1e19281293daee6dd419c51d92e5b9dc7923d707dd27157b14b9238ae3**. CoreJAR SHA256`251e9075bb6d1629790d845569fb90994e4ff1ed76408400ee733d4041bb9e3e`. This local build is not a published release artifact.

**IE2final19e4943a:** preserves mastercc6f568 fixes as an additional parent, fixes authorized zero-balance cache reseeding with exact incoming identity, and routes RuntimeException/LinkageError break failures through verified recovery while rethrowing fatal VM errors.158tests pass; all six source verifiers and version/bytecode/JAR checks pass. Both CI runs **39534149819** and **39534145743** pass. Immutable local2.0.15JAR SHA256`897bd4de70c9219217ecfb0f8cf68f0147b6c38758a239085db39718dd686eef`. Native seed passes six physical break cases including injected LinkageError rollback and real player placement. A separate native pair at `ie2-final/build/native-linkage-green-19e4943a-snapshots` now passes all six seed cases, real placement and an11-item independent reload with the unchanged fixture. The earlier reversion failure remains unexplained, so it is retained as an unresolved acceptance issue rather than dismissed by retry.

**Native evidence already accepted at1266:** universal39532225425/39532219143 PASS; native Grid/IE2 player39532219149 PASS. Drawer fixture passed four independent JVMs20478/20769/21046/21507: prerequisite192ledger,19actual seed clicks+2reload clicks, exact3,000,000,086total, partial5/full0withdrawals, inventory64/cursor1inputs, Quantum184debit/256credit, small13full drain and identity/flags/settings after restart. These are earlier-source results; final661-test pair must pass separately.

**Current native blockers, not concealed:** ItemMover final18e1d9ad seed passes all five actual main/off-hand gestures, stale-hand replacement denial, modern model data, pending exact-byte receipts and healthy/long/PDC recovery checks. A fixture Chest.update call overwrote newly written slots; that fixture fault is corrected. The stopped seed Quantum database contains37stored+64physical=101, but the second JVM sees a reverted row. IE2 seed shutdown backup likewise contains the correct placed11-item record and no broken rows; verify sees the original six rows instead. Both agents are checking the specific fixture/startup persistence lifecycle. No quantity assertion has been removed, no native restart is marked PASS and no core change has been published.

**Independent review:** Drawer/Quantum/menu/ItemMover receipts reviewed; the two concrete IE2 review findings are fixed/tested in19e4943a. Both PRs have no existing review submissions or inline threads as of this checkpoint. Final exact-head review still required before merging.

**SF_Addons base:** `SF_Addons_r139_1.21.11-26.3.zip` is CRC-valid,45JARs/48entries, SHA256`541708ca969bb6d6c7a0b11510e856b221c2dbd984e6523404a53ebdaa60ff1c`. It contains Networks1.0.56/IE2.0.13. Replace only the two applicable verified release JARs and precise manifest/checksum/compatibility metadata; preserve the other43JARs byte-for-byte. No unrelated core is bundled or published.

**Exact next action:** push this661-test candidate on the existing PR104 branch; run final canonical recipe/Drawer acceptance withIE219e4943a, resolve the causal native restart blocker and commit the reproducible ItemMover fixture. Require final CI/native acceptance and exact-head review, then merge master, publish Networks1.0.60/IE22.0.15 through existing gated workflows, verify the actual release assets, and update SF_Addons. Folia remains explicitly unsupported for cross-region transactions; no descriptor flag or forced-crash claim is introduced.


### Latest implementation checkpoint: GUI, Quantum and recipe fallback

**Canonical clean strict build: 572 tests, zero failures/errors/skips.** Legacy source contracts, Java 21 bytecode (531 owned classes), universal JAR (289 IDs) pass. Versions remain unchanged pending final acceptance.

- **DS-03 GUI:** actual installed handler onfd59173d ran8tests: healthy control passed,7failures reproduced full/rejected/partial-inventory loss, snapshot cursor/inventory duplicate credits and stale-menu access. All8 now pass, plus4controls for reentry, intentional input Void Excess, new-type registration and refused source writes. New `DrawerMenuTransfer` uses authoritative slot/cursor writes/readbacks. `StorageUnitData` holds canonical Drawer/container ownership until exact source debit/destination credit is observed; no refund passes through void/lock policy. New handler rejects detached menus/replaced storage.
- **DS-03 Quantum:** FROM baseline5tests:1control passed,4failures duplicated44 or lost37/44. TO baseline4tests:1control passed,3failures lost212. New `DrawerQuantumDepositReceipt` and `DrawerQuantumWithdrawalReceipt` use guarded live menu/PDC receipts. All18actual-handler cases pass: snapshot reads, rejected writes, write-then-throw, preparation callbacks, reentry, stale menus, zero/full drain, first assignment andLong.MAX_VALUE. Full drain preserves original unrelated container PDC, template, capacity and flags. The source-order assertion now checks the verified helper sequence.
- **RA-02:** native834df8dd/IE2tree74680f3e proved actual GUI pickup/toggles/chat5/reopen and exact per-round ledgers, then real ElectricSmeltery buffered1CARBON when5was requested. Five unit declarations reproduced1failure/4healthy controls. `AbstractNetworkPusher.getRecipeInputSlots` now uses the same live target's public `InventoryBlock.getInputSlots` when its generic declaration is empty. Dynamic item-aware insertion permissions are unchanged. All5new cases and fullsuite pass; native ratio/OFF/re-enable/restart is next.
- Native recipe client now sends the ordinary close-window action after the real chat prompt; pinned ChatUtils does not close the inventory automatically. No server assertions weakened.
- Compatibility check initially caught the removed old empty-Quantum cleaning expression. New GUI/Quantum receipts now detach prepared items with the established `ItemStackUtil.getCleanItem` before CraftInventory writes, preserving exact PDC. The verifier checks all three new helpers and retained display cleanup. Full572-test build rerun and all static/bytecode/JAR gates pass after this correction.

**Remote/native evidence:** Networksfd59173d native workflow **39530959605**, job **118996473117**, passed storage192restart, actual Grid/crafting and IE2 player actions againstIE2`274a1833`. The real-monitor readiness correction is verified. Recipe workflow39530959650failed the old client assumption; corrected client is included here. IE2`274a1833488fba8dbd8e3bbc44b95e59d49c09b7` passed both CI workflows **39530756144**/**39530752142**. Separate local native IE2 break acceptance now PASS: five real Player.breakBlock ledgers, full+Void physical output, foreign PDC, cancelled second spawn rollback/retry, callback reentry denial, actual client placement and independent-JVM reload. SeedPID14864/verifyPID15153; exact core1cb/Networks834/IE2localfa4 treeequalremote274a. Fixture files are ready to commit; no forced-crash claim.

**Exact next action:** run this canonical572-test candidate through native recipe acceptance; complete ItemMover PDC/slot receipts and repair the existing silent overcapacity clamp. Then native Drawer acceptance, final reviews/master integration/versioning/release/SF_Addons. Existing documented Folia runtime remains unclaimed because multi-chunk networks are not region-safe; do not force an unsafe descriptor flag. Applicable Paper/Purpur26.3 gates remain mandatory. Runtime ambiguous-write holds do not establish forced-crash recovery.

### Previous integration checkpoint: performance, readiness and IE2 break receipts

**Latest verified production:** Networks `834df8dd424a6749e5f1723f63cfdabca936831f` passed both universal workflows **39529931281** and **39529926947**. All 530 tests and eight platform/compatibility gates passed. Its native workflow **39529927009**, job **118993225786**, passed the 192-item two-JVM storage/restart ledger but reproduced the first Grid shift refusal. At the actual click, the published root had **zero monitors**, `cachedInputs=unbuilt` before and `0` after; all 16 items stayed with the player. Later discovery found the two real monitors/one deduplicated IE2 storage. This establishes a fixture-readiness race: the old condition accepted the Grid node before its physical monitors registered.

**Current Networks integration batch:**

- Reuses existing performance PR **#96**, source `323e7d1a7c641668ea960e7ed55b4e2198710361`, as an additional parent. Applies only its original two-file patch, preserving subsequent incident guards. Multi-template pushes classify each target once, skip target work for confirmed source misses, and stop capacity/count scans once the request is covered. Seven new test invocations verify unchanged routing/withdrawal budgets and reduced calls; these are operation-count results, not a live TPS claim.
- Native Grid readiness now requires both actual physical monitors, loaded authoritative records/menus, and node ownership by the published root before connecting the client. It does not call lazy barrel discovery, retry a player deposit, or weaken any conservation/crafting assertion.
- Adds the native Recipe-Aware fixture/workflow: actual Classic/More/Best GUI actions, foreign PDC stack pickup, toggle/batch persistence, actual smeltery ingredient ratios, OFF behavior and independent-JVM reload. Native acceptance is in progress; fixture inclusion is not a PASS claim.
- Initial Recipe-Aware control regression fixture now explicitly initializes its own plugin config constants; assertions and production behavior are unchanged.
- Both native workflows pin the coordinated IE2 repair `274a1833488fba8dbd8e3bbc44b95e59d49c09b7` and core `1cb32d6e95b305998065b8ef6b936a56f679187d`.

**Verified locally for this integration batch:** full Gradle `clean build`, strict production deprecation gate; **537 tests, zero failures/errors/skips**; Legacy source compatibility, Java 21 bytecode (526 owned classes), universal JAR (289 item IDs). Tested core JAR SHA256 `251e9075bb6d1629790d845569fb90994e4ff1ed76408400ee733d4041bb9e3e`.

**Coordinated IE2 progress:** PR **#32** now at `274a1833488fba8dbd8e3bbc44b95e59d49c09b7`, source tree `74680f3ec78546d2cffa1c72e2c8d1618a91da45` exactly equal to locally tested commit `fa4b7adf9539511b13f919b29de1c5bb9222d14b`. Five files: `StorageCache.kt`, `StorageUnit.kt`, new `StorageBreakDropReceipt.java` and two test classes. Break preparation uses live exact identity, preserves physical output regardless of input Void Excess, verifies shell PDC before mutation, holds source ownership through exact world-entity receipts, and rolls back captured entities before source restoration. Ambiguous rollback remains held without speculative refunds. Full local build **150 tests, zero failures/errors/skips**, including 19 new regressions and 2,500 seeded ledgers. Immutable candidate JAR SHA256 `ef25cc5022734988b8aef77ebb316764f570300a2e2608bb0239b98abd8d01f4`. Native break/place/restart and CI are still required; forced-crash atomicity is not claimed.

**Exact next actionable development task:** implement and test DS-03 actual drawer GUI remainder/slot receipts while parallel work finishes Quantum source readback and ItemMover PDC receipts. Inspect newly triggered native Recipe-Aware and Grid jobs; fix concrete failures immediately. Local native startup currently needs IE2 Libby dependency provisioning; no product failure is inferred from its missing Kotlin runtime. No merge, version bump, release or SF_Addons update yet.

### DS-02b and initial Recipe-Aware control accepted locally and in universal CI

Test-only `0287ff137cd5f7ec77badb0e1be77faebced1293`, GuGu job **118990879584** in workflow **39529184106**, compiled and ran **523 tests with exactly the 10 new failures**; all original 513 passed. Repair `834df8dd` passed **530 tests, zero failures/errors/skips**, then both universal CI runs above.

- DS-02b: `DataSource`, new `DrawerTemplateJournal`, and `DatabaseBackupManager` preserve fsynced exact-byte identity receipts before accepting new types; replay before maps/counters; verify committed bytes and refuse conflicts; reserve orphan item/container IDs; include recovery files in backups. Existing serialization, schema, amount journal and known-type fast path remain intact. Seven production-journal tests join eight real SQLite/worker regressions.
- RA-01: `AbstractNetworkPusher` always installs the existing foreign-item recovery handler, including the first menu update; no replacement or deletion of the foreign stack. Initial/stale-menu regressions pass.
- Build helper `scripts/download_jeg.sh` consumes the complete ZIP entry listing during class verification, avoiding SIGPIPE exit 141 under `pipefail`; pinned digest and ZIP/class checks remain enforced.
- Earlier `fc0582bff93128c60dd5fa9f36562c5c04332f75` native run **39523127735** passed four independent JVMs, the 192-item restart ledger, actual Grid/crafting clicks and IE2 player buttons. Its repeat was cancelled by the next branch push. The later click-time evidence above now explains the intermittent readiness failure.

### Previous checkpoint details (preserved evidence)

**Production at `5a8dd3747684f585ba6552b12e96eaa30c5ae47d` now passes all eight universal gates and 513/513 project tests. Its separate paired native player run failed conservatively at the first Grid shift.** The two storage/restart JVMs passed; all 16 tagged items stayed in the player's inventory, zero in storage/cursor, no reported transfer exception. This is NOT evidence of a dupe, loss or a registration-path regression, and the native pair is NOT accepted at this head.

This commit changes ONLY the disposable NativeGridPlayerProbe and this checkpoint. It captures already-published root/cache/cooldown/miss state at LOWEST and MONITOR click priorities. It never invokes lazy barrel discovery, changes inventories, retries deposits, refreshes controllers, or relaxes the exact existing 16-item/crafting assertions. Production and all 513 tests remain unchanged.

**Exact next action:** inspect this head's native run and passive click-state records. Resolve the observed Grid refusal causally without fabricated barrels or weaker ledgers. In parallel, continue the already-prepared DS-02b registration/restart tests rather than restarting DS-01 or DS-03. Preserve concurrent work and safety-denied-write boundaries. Do not publish while final acceptance is incomplete.

## Authority and boundaries

Owner authorization covers completing, reviewing, merging, versioning and publishing Networks Expansion and updating SF_Addons AFTER acceptance. It supersedes the old blanket release hold, not testing/safety requirements. No failing merges, safety-denial bypass, force pushes, guessed identities, silent deletion, live server/world/config changes, or unrelated Slimefun Legacy publication. Preserve recipes, IDs/PDC, storage/migration formats, physical counts, intentional input Void Excess, rates/budgets. GuGu/Legacy is the maintenance focus; retained United CI is historical compatibility evidence.

Models, MockBukkit and injected faults are not native exploits, Folia, forced-crash or live TPS evidence. A passing earlier SHA does not accept a new source/core/IE2 combination. No master merge, version/tag/release or SF_Addons update has occurred in this workstream.

## Repository topology at initial inspection (historical; active heads above)

- Networks master `084515c96bdeb3aea433890249b6d36ec8de5d6b`.
- Existing draft #104 branch `test/native-ie2-player-actions-20261018`, stacked on #102 `integration/cursor-ie2-withdrawal-player-20261018` at `96f9123f64df315a969a2b499b91781c6cc370f1`, NOT master. No formal reviews at run start.
- #98 ALREADY MERGED October 17: merge `f934c3e3eb1545c6e2be45f4ce27afd7caac0d5d`, source `6bf76a0ab8b41b3101f710ccd09829b25a51891a`. Do not recreate.
- #104 retains #99 cursor, #100 native Grid, #101 IE2 output, #102 integration. #103 is an alternate input pairing; perf #96 separate.
- IE2 master `cc6f568b72b63283cf72bcbd85bf0817cba22194`; preserve independent fixes.
- IE2 draft #32 `integration/ie2-cross-direction-receipts-20261018`, head `b96e3a0f09d56faeff135a1d3ac679776e1fbd24`, base #30 `ec201bc77f4bf0611c7d8674e7b75d1656e313da`. CI `39348655330` passed both validations, publish skipped. #30/#31 integration, ownership/clone cleanup and ticker output receipts already exist.
- PR descriptions contain obsolete pins. Current refs and exact workflow/manifests take precedence.

## Completed production changes and evidence

### DS-01 — cancelled saves and post-worker checkpoint

Production `40a06739228c39eff68a7a4302ef95668909623b`: retain detached immutable save receipts until completion/rejection, overlay newer per-key pending amounts, checkpoint after worker termination before DB close. Existing journal format unchanged. Files: DataStorage blob `2b3ad1f1adee2f19fe47c9cb0d4d67f1137b6d32`; Networks blob `d6f803344314531cd83f45213b85524e37eb1800`.

Clean RED `39512799540`: 485 tests, five targeted failures. GREEN `39513489094` / `39513484351`: all eight gates. XML artifact `12307431485`, SHA256 `f0e8a1992dd3b9ef1968c52d1041ac728e213e23069f5aa0e688011a4c66abbe`: 485/485, 12 shutdown regressions. Production JDBC/SQLite, mocked host. Native `39513484119` passed; artifact `12307880141`, SHA256 `2b12f770f99a78e578d3f46dbbdd6c279afe0119bc5c2b2f5bd71c8c5cac5168`, four JVMs/192-item restart/real Grid and IE2 actions. Does not prove new-template durability or forced-crash atomicity.

### DS-03 — withdrawal preparation

Production `877e3fcac1c2f78ea7a2f1c4cc68d57cb8f8f9fb`: prepare clone/quantity before debit, finally-released same-drawer preparation ownership, canonical-container/balance readback before debit. StorageUnitData blob `e367dbed6a3ef68adb6616ad8e1f13ea35df1b5c`. Requesting-template/Slimefun canonicalization, lock-zero and long-deposit changes preserved.

Clean RED `39514785065`: 500 tests, 11 new failures. GREEN `39515547044` / `39515538733`: eight gates; XML artifact `12306818461`, SHA256 `6e7d710f63c928c71fc04d061b0f7919edefd79f72970b0ee46ed63fedeacd89`: 500/500, 15 preparation and 12 shutdown tests. Native `39515538641` passed; downloaded artifact `12306769572`, SHA256 `72acc2800f6f3a6a5c7b6f5745abbc72308444355a7ba74ac5068e97df0a8708`. Correct manifest: Networks877e3fca/IE2b96e3a0f/core1cb32d6e95b305998065b8ef6b936a56f679187d, Purpur1.21.11/2568; PIDs4630/4740/4864/5025, Networks JAR SHA256 `520be90624b0769a7dfc1640c1f27dfa9500baa8d742bcdd3902ecf5ba7c4eb3`. Historical checkpoint PID/JAR transcription errors were corrected; source/PASS was accurate.

### DS-02a — item-template serialization preflight

Production **`5a8dd3747684f585ba6552b12e96eaa30c5ae47d`**, DataSource blob `e2f76480502ecda0d9f94a34d4a8cddc8f137044`: serialize exact historical payload before allocating/publishing a new ID, reuse immutable bytes on worker, publish map only after queue submissions are accepted. Known identity returns before serialization. Synchronized matching, formats, quantities and rates unchanged.

- Test `DrawerTemplateRegistrationTest.java`, blob `50521f75b1e1b6e1566bd85d1d0c0b14b9f07626`: 13 actual registration/Bukkit serialization/SQLite tests. Only unrelated component matching is isolated because MockBukkit lacks matchesWithoutData. Registration/serializer/JDBC are not mocked. Actual physical-deposit failure keeps all 13 input items; PDC/lore/identity/readback/retry/queue rejection/fast path covered.
- Initial `25d41f61` / `32c06ab9` fixture attempts included unsupported comparison controls. `dbf1025f` ran512/7 but the test subclass serializer was discarded by Paper clone. That initial clean serializer-fault claim is RETRACTED; do not cite it as valid reproduction.
- `95840eaf` candidate exposed the injection problem. `5f6f69fa` forward-restored only unaccepted DS-02a production, explicitly retained the fault override across clones, strengthened callback-reached assertions and added a harness control. Corrected clean RED `39521322733`, GuGu job118965578859: **513 tests, seven targeted failures, zero skips**, 500 accepted plus six healthy controls pass.
- `5a8dd374` reapplies the same production candidate with corrected tests unchanged. **Universal39521805317 SUCCESS, all eight gates**, including Legacy/GuGu/retained United, Java21 canonical JAR/Purpur, Paper26.3 API and native Paper176/194. Publication skipped.
- Downloaded XML artifact **12312045405**, SHA256 **1970aa4e6478c94d91e5a5f013b1e51df89f14942330c3575df9077fb31f4144**: **513 tests, zero failures/errors/skips**, 13 registration, 15 withdrawal and 12 shutdown cases.
- Separate native **39521800649 FAILED** at Grid first shift after successful192-item seed/restart. Downloaded artifact12311990636, verified SHA256 `bd01057ab10ff50405542db00cd56169a77f80a146953c6ef863894b82d282de`. At failure: attempts1/offered16/committed0/failures0, inventory16/storage0/cursor0. Five ticks later inputBarrels1/IE2barrels1, targetinputempty, unheld, template matches, acceptsTaggedtrue. That delayed snapshot cannot establish click-time state. This commit adds passive click-time snapshots only; no retry/ledger change.
- This repair does NOT restore failed/cancelled SQL identity writes. First-new-type serialization now runs on registering thread; existing types do not repeat it. Practical performance/native acceptance remains separate.

## Remaining acceptance queue

| ID | Task | State / next requirement |
| --- | --- | --- |
| DS-01 | Dedicated native Drawer timeout/restart | Full project and paired regressions pass; dedicated fault/crash evidence still distinct. |
| DS-02a | Registration preflight | Accepted in 530/537-test builds and universal gates. Native Grid readiness race identified and fixture corrected; exact-pair rerun pending. |
| DS-02b | Durable templates/orphan IDs | Implemented834df8dd; real SQLite/worker/journal tests and universal CI pass. Dedicated crash acceptance is distinct. |
| DS-03 | GUI remainder/ItemMover/Quantum receipts | GUI12/Quantum18 real-handler cases pass in full572-test build. ItemMover, malformed-balance clamp and native Drawer acceptance remain. |
| IE-01 | Break/output/Void/zero/live PDC | Implemented274a1833;150tests/both CI pass. Five native breakcases, actual placement andindependent restart pass with834pair; fixturecommit/finalpair pending. |
| RA-01 | Foreign-slot initial handler | Implemented834df8dd; initial/stale-menu tests pass. Native pickup/toggle/batch/reload running. |
| RA-02 | Recipe/OFF parity | Real smeltery one-vs-five defect reproduced; fallback passes5new tests/fullsuite. Canonical native ratio/OFF/re-enable/restart pending. |
| PAIR-01 | Final pair/Dust Extractor | Keep exact ledgers. First-shift readiness race causally identified; rerun corrected fixture against274a1833. |
| PLATFORM-01 | Paper/Purpur/Folia/performance | Paper176/194 and Purpur storage pass at5a8; paired Folia/live MSPT not established. Perf96 integrated with seven operation-count tests; final native acceptance remains. |
| RELEASE-01 | Review/integrate/version/release/SF_Addons | Authorized only after acceptance. No unrelated Legacy publication. |

## Environment and evidence boundaries

GitHub reads/writes/Actions/artifacts work. Approved network execution, exact full local checkouts and JDK25/JDK21 builds work. Use isolated Gradle user homes for concurrent builds and immutable validated artifacts. Full builds also execute in Actions. Earlier local model ZIPs are supplementary, not deployable releases. Permanent disk failure, nonstopping workers, arbitrary callbacks and forced-crash atomicity remain distinct requirements. Detailed earlier execution history remains in this file's Git history at5a8dd374 and predecessors.
