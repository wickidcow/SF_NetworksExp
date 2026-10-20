# Networks Expansion completion checkpoint

Updated: 2026-10-20. DS-01 is verified GREEN; DS-03 withdrawal preparation reached clean project RED and is implemented in this commit, awaiting exact-head GREEN. Read this file and fresh Git refs before additional work; do not restart completed investigations.

## Authority and boundaries

The owner explicitly authorized completing, testing, reviewing, merging, versioning and publishing Networks Expansion and updating SF_Addons AFTER acceptance. This replaces the earlier blanket release hold, not the requirement to pass tests. Never merge failing code, bypass a safety-denied write, force-push, guess identities, silently delete stock, mutate live servers/worlds/configs, or publish unrelated Slimefun Legacy changes. Preserve recipes, IDs/PDC, storage/migration formats, intentional input Void Excess, rates and tick budgets. GuGu/Legacy is the maintenance focus; retained United CI is historical compatibility evidence, not a new development target.

Use existing integration PRs and preserve concurrent commits. A prior green run does not accept a new head or different core/IE2 pin. Models, injected faults and MockBukkit are not native gameplay, Folia, forced-crash or live TPS proof.

## Repository baseline and topology

- Networks master `084515c96bdeb3aea433890249b6d36ec8de5d6b` at execution start.
- Networks PR #104 branch `test/native-ie2-player-actions-20261018`, based on #102 `integration/cursor-ie2-withdrawal-player-20261018` at `96f9123f64df315a969a2b499b91781c6cc370f1`, NOT master. Draft/open; no formal reviews at execution start.
- Networks #98 ALREADY MERGED: `f934c3e3eb1545c6e2be45f4ce27afd7caac0d5d` on October17, source head `6bf76a0ab8b41b3101f710ccd09829b25a51891a`. Do not recreate it.
- IE2 master `cc6f568b72b63283cf72bcbd85bf0817cba22194`; preserve independent master fixes during integration.
- IE2 PR #32 branch `integration/ie2-cross-direction-receipts-20261018`, head `b96e3a0f09d56faeff135a1d3ac679776e1fbd24`, base #30 `ec201bc77f4bf0611c7d8674e7b75d1656e313da`. Exact CI39348655330 passed both validation jobs, publication skipped. Draft/open, no formal reviews.
- PR descriptions have historical hashes. Current refs and exact workflow source take precedence. Last accepted Networks checkpoint is40a06739 below; this new production commit needs its own acceptance.

## Implemented work to retain

- Merged #98: crafting ingredient debits, output receipts, metadata preservation, reentry rejection, no speculative refunds.
- #104 includes #99 cursor safety, #100 actual Grid fixture, #101 IE2 output receipts, #102 combined tree and actual IE2 player fixtures. #103 is an alternative input-only pairing, not another production fix to blindly merge.
- IE2 #32 combines #30 withdrawals and #31 physical input receipts, cross-direction ownership, seeded-template integrity and template-clone cleanup. Old cb0df3ef failure already fixed. Ticker output receipts are implemented; do not re-diagnose the old unchecked ticker as current.
- Classic Pusher/More/Best already have per-block Recipe-Aware controls: normal OFF behavior, template quantities/duplicates define a batch, target1-64, accepted physical inputs, fair four-template budget. Line/point/Vanilla/Smart pushers are distinct and not implicitly covered.
- Preserve concurrent GUI changes through5cddfba0: physical-count/lore agreement, foreign PDC protection and no unchanged icon rewrites.
- Preserve Drawer absolute updates/tombstones and FIFO ordering. DS-01 adds retained cancelled receipts and final shutdown checkpoint without changing the journal format.

## Actual execution history and evidence

1. `7c81b6e7de6716481e01d8f95772cb0f97368efb`: permanent completion checkpoint created.
2. `b7e42a31e796a98d1d52f16291c087921c57aec6`: 12 production JDBC/SQLite shutdown tests plus test-only sqlite-jdbc3.50.3.0. Initial CI39512094436 compiled but fixture namespace setup failed, NOT clean baseline evidence.
3. `dac68eb35b9eb4789021b2ee1bf2cc2b7b98a08a`: fixed fixture namespace/bootstrap without relaxing assertions. Clean RED run39512799540, GuGu job118937676149:485 tests, exactly5 new DS-01 failures; all existing tests passed. Cancelled snapshots resurrected or retained stale balances and final checkpoint was absent.
4. **`40a06739228c39eff68a7a4302ef95668909623b`: DS-01 production.** DataStorage retains detached immutable save snapshots until completion/rejection and overlays newer pending amounts per key. Networks checkpoints after worker termination and before SQLite close. DataStorage blob6560e2fb ->2b3ad1f1adee2f19fe47c9cb0d4d67f1137b6d32; Networks blob0463f648 ->d6f803344314531cd83f45213b85524e37eb1800.
5. **DS-01 GREEN:** exact PR universal39513489094 and push universal39513484351 SUCCESS, all8 gates including Legacy/GuGu/retainedUnited, Java21JAR/Purpur, Paper26.3API and nativePaper176/194. Publication skipped.
6. Downloaded XML artifact12307431485, SHA256 `f0e8a1992dd3b9ef1968c52d1041ac728e213e23069f5aa0e688011a4c66abbe`: **485 tests,0 failures/errors/skips**, including12/12DrawerShutdownReceiptTest. Real production DataStorage/QueryQueue/DrawerRecoveryJournal/DataSource and SQLite; mocked Bukkit host. Synthetic existing IDs do not prove new-template registration or gameplay.
7. Downloaded canonical JAR12307561776:1,642,215bytes, SHA256 **`50aa02834355ea1605cca0d1d11b1f8af3beab1474bef1039fa9f95ca9e394a7`**, plugin version1.0.59. Development build, not published release.
8. **Actual paired native39513484119 SUCCESS** at40a06739, IE2b96e3a0f, core `1cb32d6e95b305998065b8ef6b936a56f679187d`, Purpur1.21.11build2568. Downloaded artifact12307880141 SHA256 `2b12f770f99a78e578d3f46dbbdd6c279afe0119bc5c2b2f5bd71c8c5cac5168`. Manifest Networks JAR hash matches canonical. JVM PIDs6498/6610/6737/6892.24roundtrips/192taggeditems/restart, actualGrid7clicks2shifts/crafts, actualIE2buttons320/316ledgers/PDC/full/partial/reopen. Incident hold explicitly synthetic. No fabricated barrels.
9. `eef2618da32243882138af3e547ce426f4014b68` and checkpoint `e9f3e772cee14176b8cc839a96e888f258ba0682`:15 actual-helper withdrawal tests, production unchanged40a067. File `src/test/java/com/balugaq/netex/api/data/DrawerWithdrawalPreparationTest.java`. Real ItemContainer/ItemStack/PDC; persistence calls observed through Mockito.
10. **DS-03 clean RED at e9f3e772:** exact run39514785065; GuGu job118944221858 compiled source/tests, **500 tests with11 failures, all in the new suite**. All485existing tests and4 new healthy withdrawal tests passed. Four clone faults and four quantity-setting faults debit16 to13/0 prematurely and sometimes remove identity; failed retry loses3; recursive preparation reaches clone twice; changed-source callback returns an obsolete withdrawal. Legacy and retainedUnited lanes also failed this test commit. No fixture failures or assertion relaxation.
11. **THIS COMMIT implements DS-03 preparation safety**, unchanged test assertions. `StorageUnitData.java` blob `9661c9bc33804ee837612379b9d62379645cc773` -> **`e367dbed6a3ef68adb6616ad8e1f13ea35df1b5c`**. Result clone/quantity prepared before debit, per-drawer finally-released preparation guard, and canonical container/balance readback before debit. Requesting-template identity, locked zero-balance behavior, existing long-quantity deposit fix, schemas and rates preserved. Exact-head CI pending. This does not claim atomicity across arbitrary direct external ItemContainer mutations or later delivery/SQL failure.

## Acceptance queue

| ID | Task | State / next requirement |
| --- | --- | --- |
| DS-01 | Cancelled saves and post-worker checkpoint | Accepted by full project CI and paired regression. Dedicated native Drawer timeout/restart coverage remains final storage requirement; no forced-crash claim. |
| DS-02 | Durable item-template/quantity registration; orphan IDs | Existing local serialization-preflight/template-journal candidates. Port actual registration-failure tests; DS-01 does not recover missing identity bytes. |
| DS-03 | Withdrawal preparation, GUI remainder, ItemMover/Quantum receipts | Preparation fix implemented after500-test/11-failure RED; verify GREEN now. Preserve requesting-template/ItemContainer canonicalization contract. Other already-diagnosed receipt candidates remain separate. |
| IE-01 | Break-output/zero/Void Excess/live identity/PDC | Existing full-context candidate needs full IE2 Gradle and native break/place/restart; partial-spawn/restart still unresolved. |
| RA-01 | Foreign-slot first-viewer handler | Current AbstractNetworkPusher returns before handler registration. Existing local patch; prior safety-denied write must not be bypassed. Native pickup/toggle/batch/reload needed. |
| RA-02 | Recipe-aware insertion/OFF parity | Existing planner/policy implementation; extend disposable machine fixture for ratios, accepted inputs, metadata, four-template budget. |
| PAIR-01 | Final Networks/IE2/core + Infinity Dust Extractor |40a067pair passed current actual-client/storage fixture. Re-run final changed pair; missing-barrel readiness uses normal controller refresh, never fabricated storage or relaxed ledgers. |
| PLATFORM-01 | Paper/Purpur/Folia owner-thread/performance |Paper176/194/Purpur1.21.11 passed at40a067, not pairedFolia/liveMSPT. Perf#96 separate until safety acceptance. Document external blockers accurately. |
| RELEASE-01 | Review/integration/version/artifacts/publish/SF_Addons |Authorized after acceptance. No master merge/version/tag/release/bundle/live changes yet. No unrelated Legacy publication. |

## Exact next action

Verify this DS-03 production commit through unchanged500-test suite, universal gates and current native pairing. Fix actual failures, not tests. Record exact result/artifacts. During CI, prepare the already-diagnosed next actual regression (new-template registration or recipe-control lifecycle), not another broad investigation. Preserve all completed commits and continue the acceptance queue on existing PRs.

## Environment and limitations

GitHub reads/writes/CI work. LocalJava21 available; direct external download DNS fails. Full dependency-resolved compilation runs in GitHub Actions, not stand-ins. Local historical models are supplementary only. Permanent disk failure/nonstopping workers/missing template durability/unsafe callbacks/forced-crash atomicity remain distinct requirements; green DS-01 does not prove every duplication path eliminated.
