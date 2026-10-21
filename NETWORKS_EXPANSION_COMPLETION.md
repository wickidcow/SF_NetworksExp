# Networks Expansion completion checkpoint

Updated October 20, 2026 America/New_York (October 21 UTC in CI).

## Resume here — do not restart accepted work

**Production at `5a8dd3747684f585ba6552b12e96eaa30c5ae47d` now passes all eight universal gates and 513/513 project tests. Its separate paired native player run failed conservatively at the first Grid shift.** The two storage/restart JVMs passed; all 16 tagged items stayed in the player's inventory, zero in storage/cursor, no reported transfer exception. This is NOT evidence of a dupe, loss or a registration-path regression, and the native pair is NOT accepted at this head.

This commit changes ONLY the disposable NativeGridPlayerProbe and this checkpoint. It captures already-published root/cache/cooldown/miss state at LOWEST and MONITOR click priorities. It never invokes lazy barrel discovery, changes inventories, retries deposits, refreshes controllers, or relaxes the exact existing 16-item/crafting assertions. Production and all 513 tests remain unchanged.

**Exact next action:** inspect this head's native run and passive click-state records. Resolve the observed Grid refusal causally without fabricated barrels or weaker ledgers. In parallel, continue the already-prepared DS-02b registration/restart tests rather than restarting DS-01 or DS-03. Preserve concurrent work and safety-denied-write boundaries. Do not publish while final acceptance is incomplete.

## Authority and boundaries

Owner authorization covers completing, reviewing, merging, versioning and publishing Networks Expansion and updating SF_Addons AFTER acceptance. It supersedes the old blanket release hold, not testing/safety requirements. No failing merges, safety-denial bypass, force pushes, guessed identities, silent deletion, live server/world/config changes, or unrelated Slimefun Legacy publication. Preserve recipes, IDs/PDC, storage/migration formats, physical counts, intentional input Void Excess, rates/budgets. GuGu/Legacy is the maintenance focus; retained United CI is historical compatibility evidence.

Models, MockBukkit and injected faults are not native exploits, Folia, forced-crash or live TPS evidence. A passing earlier SHA does not accept a new source/core/IE2 combination. No master merge, version/tag/release or SF_Addons update has occurred in this workstream.

## Repository topology

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
| DS-02a | Registration preflight | 513/513 and eight universal gates pass; separate native pair refusal above unresolved. |
| DS-02b | Durable templates/orphan IDs | Existing additive intent-journal prototype and real restart/failure test candidates prepared; not integrated. Protect original bytes before acceptance, replay before loadItemMap/counters, refuse conflicting identities, reserve orphan IDs, preserve backups. |
| DS-03 | GUI remainder/ItemMover/Quantum receipts | Preparation accepted; other known receipt/restore candidates require real project/native tests. |
| IE-01 | Break/output/Void/zero/live PDC | Existing full-context candidate; full Gradle/native break/place/restart and partial-spawn recovery unresolved. |
| RA-01 | Foreign-slot initial handler | Existing narrow candidate; previous safety-denied write must not be bypassed. Native pickup/toggle/batch/reload required. |
| RA-02 | Recipe/OFF parity | Classic/More/Best implemented; native ratios/accepted slots/budget tests pending. Other families distinct. |
| PAIR-01 | Final pair/Dust Extractor | Keep exact ledgers; resolve conservative first-shift refusal, no invented barrels. |
| PLATFORM-01 | Paper/Purpur/Folia/performance | Paper176/194 and Purpur storage pass at5a8; paired Folia/live MSPT not established. Perf96 separate. |
| RELEASE-01 | Review/integrate/version/release/SF_Addons | Authorized only after acceptance. No unrelated Legacy publication. |

## Environment and evidence boundaries

GitHub reads/writes/Actions/artifacts work. Local Java21 works; direct clone/download DNS fails. Full builds execute in Actions. Earlier local model ZIPs are supplementary, not deployable releases. Permanent disk failure, nonstopping workers, arbitrary callbacks and forced-crash atomicity remain distinct requirements. Detailed earlier execution history remains in this file's Git history at5a8dd374 and predecessors.
