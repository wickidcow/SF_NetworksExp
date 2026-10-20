# Networks Expansion completion checkpoint

Updated: 2026-10-20. DS-01 production fix has passed exact-head project and native regression gates. DS-03 withdrawal preparation tests are the next test-first commit. Read this file and fresh Git refs before additional work; do not restart completed investigations.

## Authority and boundaries

The owner explicitly authorized completing, testing, reviewing, merging, versioning and publishing Networks Expansion and updating SF_Addons AFTER acceptance. This replaces the earlier blanket release hold, not the requirement to pass tests. Never merge failing code, bypass a safety-denied write, force-push, guess identities, silently delete stock, mutate live servers/worlds/configs, or publish unrelated Slimefun Legacy changes. Preserve recipes, IDs/PDC, storage/migration formats, intentional input Void Excess, transfer rates and tick budgets. GuGu/Legacy is the maintenance focus; retained United CI is historical compatibility evidence, not a new development target.

Use the existing integration PRs and preserve concurrent commits. A prior green run does not accept a new head or different core/IE2 pin. Models, injected faults and MockBukkit are not native gameplay, Folia, forced-crash or live TPS proof.

## Repository baseline and PR topology

- Networks master: `084515c96bdeb3aea433890249b6d36ec8de5d6b` at execution start.
- Existing Networks PR #104: `test/native-ie2-player-actions-20261018`; base #102 `integration/cursor-ie2-withdrawal-player-20261018` at `96f9123f64df315a969a2b499b91781c6cc370f1`, NOT master. Draft/open; no formal reviews at execution start.
- Networks #98 is ALREADY MERGED: `f934c3e3eb1545c6e2be45f4ce27afd7caac0d5d` on October 17; source head `6bf76a0ab8b41b3101f710ccd09829b25a51891a`. Do not recreate it.
- IE2 master: `cc6f568b72b63283cf72bcbd85bf0817cba22194` at execution start. Preserve its independent fixes during integration.
- Existing IE2 PR #32: `integration/ie2-cross-direction-receipts-20261018` at `b96e3a0f09d56faeff135a1d3ac679776e1fbd24`, base #30 `ec201bc77f4bf0611c7d8674e7b75d1656e313da`. Exact CI `39348655330` passed both validation jobs; publication skipped. Draft/open, no formal reviews.
- PR descriptions contain historical hashes. Current refs and exact workflow source take precedence. Latest accepted Networks checkpoint is `40a06739228c39eff68a7a4302ef95668909623b`; the new test commit is deliberately expected to reproduce preparation failures, not a release candidate.

## Implemented work to retain

- Merged #98: crafting ingredient debits, output receipts, metadata preservation, reentry rejection, no speculative refunds.
- #104 includes #99 cursor safety, #100 actual Grid fixture, #101 IE2 output receipts, #102 combined tree and actual IE2 player fixtures. #103 is an alternative input-only pairing, not an extra production fix to blindly merge.
- IE2 #32 combines #30 withdrawals and #31 verified input receipts, cross-direction ownership, seeded-template integrity and template-clone cleanup. The old `cb0df3ef` failure was fixed on that branch. Ticker output receipts are also implemented; do not re-diagnose the old unchecked ticker as current.
- Classic Pusher / More / Best already have per-block Recipe-Aware controls: OFF preserves normal behavior; template quantities/duplicates define a batch; target 1-64; accepted physical input routing; fair four-template budget. Line/point/Vanilla/Smart pushers are distinct and not implicitly covered.
- Retain concurrent GUI changes through `5cddfba0`: quantity/lore agreement, foreign PDC protection, no unchanged icon rewrites.
- Retain existing Drawer absolute updates/tombstones and FIFO publication ordering. DS-01 adds retained cancelled receipts and the post-worker shutdown checkpoint without changing that format.

## Actual execution history

1. `7c81b6e7de6716481e01d8f95772cb0f97368efb`: permanent completion checkpoint established.
2. `b7e42a31e796a98d1d52f16291c087921c57aec6`: 12 production JDBC/SQLite shutdown tests plus test-only sqlite-jdbc3.50.3.0. First CI `39512094436` compiled, but fixture namespace initialization failed; NOT valid baseline evidence.
3. `dac68eb35b9eb4789021b2ee1bf2cc2b7b98a08a`: fixture bootstrap fixed (Paper `Plugin.namespace()` and plugin constants), no assertions relaxed.
4. Clean RED at `dac68eb3`, run `39512799540`, GuGu job `118937676149`: 485 tests, exactly five new DS-01 failures, existing tests passed. Missing final checkpoint and cancellation cases reproduced stale 10-item balance instead of 7, 4, absent, or a retained second key of 5.
5. **`40a06739228c39eff68a7a4302ef95668909623b`: DS-01 production fix.** DataStorage retains detached immutable save snapshots until completion/rejection; shutdown overlays newer pending amounts per key. Networks checkpoints after the worker stops and before closing SQLite. Original DataStorage blob `6560e2fbfd1814a082846314d69f75037c45acbc` -> `2b3ad1f1adee2f19fe47c9cb0d4d67f1137b6d32`; Networks blob `0463f648263bd757c5e2771d944f67b0d601444c` -> `d6f803344314531cd83f45213b85524e37eb1800`.
6. **DS-01 GREEN:** exact PR universal run `39513489094` and push universal run `39513484351` completed SUCCESS. All eight gates, including Legacy/GuGu/retained United, Java21 JAR/Purpur persistence, Paper26.3 API and native Paper176/194 checks. Publication skipped.
7. Downloaded report artifact `12307431485`, SHA256 `f0e8a1992dd3b9ef1968c52d1041ac728e213e23069f5aa0e688011a4c66abbe`: XML confirms **485 tests, zero failures/errors/skips**, including **12/12 DrawerShutdownReceiptTest**. These exercise real DataStorage, QueryQueue, DrawerRecoveryJournal, DataSource and SQLite; only Bukkit host boundaries are mocked. Existing synthetic IDs do not test template registration or live Drawer gameplay.
8. Downloaded canonical JAR artifact `12307561776`: **1,642,215 bytes**, SHA256 **`50aa02834355ea1605cca0d1d11b1f8af3beab1474bef1039fa9f95ca9e394a7`**, plugin version remains1.0.59. This is a development build, not a published release.
9. **Actual paired native run `39513484119` SUCCESS** at the same Networks SHA, IE2 `b96e3a0f09d56faeff135a1d3ac679776e1fbd24`, core `1cb32d6e95b305998065b8ef6b936a56f679187d`, Purpur1.21.11 build2568. Downloaded artifact `12307880141`, SHA256 `2b12f770f99a78e578d3f46dbbdd6c279afe0119bc5c2b2f5bd71c8c5cac5168`. Manifest matches the canonical Networks JAR hash. Distinct JVM PIDs6498/6610/6737/6892; 24 storage roundtrips with192 tagged items and restart, actual Grid7clicks/2shifts/crafts, actual IE2 deposit/single/all clicks with320/316 item ledgers, full/partial inventory, PDC and close/reopen. Incident holds are explicitly synthetic. No barrel overrides.
10. **`eef2618da32243882138af3e547ce426f4014b68`: DS-03 test-only preparation regression.** Adds 15 cases in `src/test/java/com/balugaq/netex/api/data/DrawerWithdrawalPreparationTest.java`. Executes the actual private withdrawal helper against real ItemContainer balances and Bukkit ItemStack/PDC; DataStorage writes are observed through Mockito. Covers clone/quantity exceptions, locked/unlocked partial/final withdrawals, metadata ownership, capacity cap, preparation reentry/drift and recovery after failure. Production remains the accepted DS-01 tree. This checkpoint update follows that test commit without altering production.

## Acceptance queue

| ID | Task | State / next requirement |
| --- | --- | --- |
| DS-01 | Cancelled saves and post-worker checkpoint | Implemented and accepted by exact project CI + paired regression. Dedicated native Drawer timeout/restart coverage still required for final storage acceptance; no forced-crash claim. |
| DS-02 | Durable new item-template/quantity registration and orphan ID reservation | Existing serialization-preflight/template journal prototypes remain local. Port actual registration-failure tests; DS-01 does not restore missing identity bytes. |
| DS-03 | Withdrawal preparation, GUI remainder, ItemMover/Quantum receipts | **Current step: run new actual-helper tests RED, then prepare result before debit with reentry/drift protection and run GREEN.** Preserve intentional ItemContainer canonicalization and requesting-template metadata contract. Other existing receipt candidates remain separate. |
| IE-01 | IE2 break-output conservation, zero balance/Void Excess, live identity/PDC | Existing full-context candidate needs full IE2 Gradle/native break-place-restart. Partial-spawn/restart failure unresolved. |
| RA-01 | First-viewer foreign-slot control handler registration | Current AbstractNetworkPusher returns before registering handler. Existing local patch/lifecycle tests; prior safety-denied write must not be bypassed. Actual GUI pickup/toggle/batch/reload acceptance still needed. |
| RA-02 | Recipe-aware machine insertion/OFF parity | Planner/policy code exists. Extend disposable native machine fixture for ratios, accepted inputs, metadata and four-template work budget. |
| PAIR-01 | Final exact Networks/IE2/core and Infinity Dust Extractor source | Current40a067 pair passes the existing actual client/storage fixture. Re-run every final changed pair. Missing-barrel readiness must use normal controller refresh, never fabricated barrels or relaxed ledgers. |
| PLATFORM-01 | Applicable Paper/Purpur/Folia owner-thread/performance acceptance | Paper176/194 and Purpur1.21.11 checks passed at40a067. Not paired IE2-on-Folia or live MSPT proof. Keep perf#96 separate until safety acceptance; document actual external blockers. |
| RELEASE-01 | Review/coherent integration, version, exact artifacts, publish/SF_Addons | Authorized only after acceptance. No master merge/version/tag/release/bundle/live deployment yet; no unrelated Legacy publication. |

## Exact next action

Check the exact-head workflow for the new DS-03 tests. Preserve the clean DS-01 evidence above. Confirm preparation failures against unchanged StorageUnitData blob `9661c9bc33804ee837612379b9d62379645cc773`, then apply the narrowly scoped result-before-debit fix with finally-released reentry protection and fresh source readback. Re-run the same tests unchanged; update this checkpoint with exact failing/passing runs and commit hashes. Continue the existing acceptance queue rather than opening another integration PR.

## Environment and limits

GitHub connector reads/writes and CI work. Local Java21 is available, but direct download DNS still fails. Full dependency-resolved builds run in existing GitHub Actions, not stand-ins. Local historical model/adaptor results are supplementary only. Permanent disk failure, nonstopping workers, missing template durability, unsafe external callbacks and forced-crash atomicity remain distinct requirements; successful DS-01 CI is not proof that all duplication paths are eliminated.
