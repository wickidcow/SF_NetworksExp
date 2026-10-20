# Networks Expansion completion checkpoint

Updated: 2026-10-20, DS-01 production implementation checkpoint. Read this file and fresh Git refs before additional work; do not restart completed investigations.

## Authority and non-negotiable boundaries

The owner explicitly authorized completing, testing, reviewing, merging, versioning and publishing Networks Expansion and updating SF_Addons after acceptance. This replaces the earlier blanket release hold, not the requirement to pass tests. Never merge failing code, bypass a safety-denied write, force-push, guess identities, silently delete stock, mutate live servers/worlds/configs, or publish unrelated Slimefun Legacy changes. Preserve recipes, item IDs/PDC, storage/migration formats, intentional input Void Excess, rates and tick budgets. GuGu/Legacy is the maintenance focus; retained United CI is historical compatibility evidence, not a new development target.

Use the existing integration PRs. Preserve concurrent commits and re-read branch heads before writes. An old green run does not accept a new head or another core/IE2 pin. Models, injected faults and MockBukkit are not native gameplay, Folia, forced-crash or live TPS proof.

## Current repository baseline and topology

- Networks master: `084515c96bdeb3aea433890249b6d36ec8de5d6b` at execution start.
- Existing Networks PR #104: `test/native-ie2-player-actions-20261018`; base #102 `integration/cursor-ie2-withdrawal-player-20261018` at `96f9123f64df315a969a2b499b91781c6cc370f1`, NOT master. Draft/open; no formal reviews at execution start.
- Starting #104 head `5cddfba0dd900bb045a5103e8f417158df25b5d1`: all eight jobs of universal run `39425065000` passed; publish skipped. Later commits below invalidate using that result for final acceptance.
- IE2 master: `cc6f568b72b63283cf72bcbd85bf0817cba22194` at execution start. Preserve its independent fixes during final integration.
- Existing IE2 PR #32: `integration/ie2-cross-direction-receipts-20261018` at `b96e3a0f09d56faeff135a1d3ac679776e1fbd24`, based on #30 `ec201bc77f4bf0611c7d8674e7b75d1656e313da`. Exact CI `39348655330` passed both validation jobs; publication skipped. Draft/open, no formal reviews.
- Networks #98 is ALREADY MERGED: `f934c3e3eb1545c6e2be45f4ce27afd7caac0d5d` on October 17; source head `6bf76a0ab8b41b3101f710ccd09829b25a51891a`. Do not recreate it.
- PR descriptions contain stale historical hashes. Current refs and exact-head workflow source take precedence.

## Implemented work to retain

- Merged #98: ingredient debit/output receipt crafting safety, metadata preservation, reentry rejection, no speculative refunds.
- #104 already includes #99 cursor safety, #100 actual Grid fixture, #101 IE2 output receipts, #102 combined tree and actual IE2 player fixture. #103 is an alternative input-only pairing, not another production fix to blindly merge.
- IE2 #32 combines #30 withdrawal reservations and #31 verified physical input receipts, cross-direction ownership, seeded-template integrity and template-clone cleanup. The old `cb0df3ef` failure was fixed on that branch. It also includes ticker output receipts; do not re-diagnose the old unchecked ticker as unchanged current source.
- Classic Pusher / More / Best already provide per-block Recipe-Aware toggles. OFF retains normal behavior, configured quantities and duplicates define a batch, target 1-64, accepted physical input routing, fair four-template budget. Line/point/Vanilla/Smart pushers are separate implementations, not implicitly covered.
- Retain concurrent Recipe-Aware control classification changes through `5cddfba0`: exact physical-count/lore agreement, foreign PDC protection and no repeated unchanged icon writes.
- Retain existing Drawer absolute amount/tombstone and FIFO publication ordering; do not replace it with earlier model-only changes.

## Execution log: actual repository changes and evidence

1. `7c81b6e7de6716481e01d8f95772cb0f97368efb`: created this permanent checkpoint on #104.
2. `b7e42a31e796a98d1d52f16291c087921c57aec6`: added 12 `DrawerShutdownReceiptTest` cases and pinned test-only SQLite JDBC 3.50.3.0. No production change. The tests execute real DataStorage, QueryQueue, DrawerRecoveryJournal and DataSource against real temporary SQLite files. The Bukkit plugin host is mocked. Synthetic existing IDs deliberately do not test template registration or gameplay.
3. First run `39512094436`: production and tests compiled, but fixture initialization failed because Paper uses `Plugin.namespace()` rather than `getName()`. That first run was NOT clean baseline evidence.
4. `dac68eb35b9eb4789021b2ee1bf2cc2b7b98a08a`: fixed fixture initialization, following the existing NetworkRootDrawerDiscoveryTest bootstrap. Initializes constants before the worker starts, supplies namespace, does not relax any assertion.
5. Clean baseline RED, exact PR run `39512799540`, GuGu job `118937676149`: compilation succeeded; **485 tests executed, exactly five new DS-01 failures, no existing-test failures**. Failures: missing post-worker checkpoint; cancelled detached amount expected7/actual10; delete+reinsert expected4/actual10; final-delete expected absent/actual10; newer-pending overlay expected second key5/actual9. The other seven new cases passed, including normal save cleanup, rejection mergeback, journal failure, actual SQLite rollback/retry, manual final checkpoint, disabled-journal behavior and idle no-work.
6. THIS COMMIT applies the already-reviewed coordinated DS-01 production fix with no changes to the baseline assertions. DataStorage retains detached immutable save receipts until completion/rejection; cancelled tasks retain theirs. Shutdown checkpoint overlays newer pending amounts per key. Networks checkpoints again only after the worker stops and before closing SQLite. Original blobs: DataStorage `6560e2fbfd1814a082846314d69f75037c45acbc`, Networks `0463f648263bd757c5e2771d944f67b0d601444c`. Candidate blobs: DataStorage `2b3ad1f1adee2f19fe47c9cb0d4d67f1137b6d32`, Networks `d6f803344314531cd83f45213b85524e37eb1800`.

Local supplementary evidence: existing full-class adapter suite rerun this execution reproduced baseline failures and completed three passing candidate rounds (101 persistence assertions, 120 independent-JVM replay assertions per replay, 21 lifecycle assertions, plus lifecycle replay). Two single-half negative controls still failed; modified-binary provenance check refused the wrong JAR. The outer runner reached its tool timeout after assertions at environment-report collection, so do NOT describe it as an unqualified whole-run PASS. Separate Python/SQLite version read succeeded. These adapter results are supplementary, not the new project CI outcome.

## Acceptance queue

| ID | Task | State / exact next requirement |
| --- | --- | --- |
| DS-01 | Cancelled detached saves + post-worker shutdown checkpoint | Production implemented in this commit after clean project RED. **Run exact-head CI now; not yet accepted GREEN.** Add native Drawer shutdown/restart acceptance before final release. |
| DS-02 | Durable new item template and quantity registration; orphan ID reservation | Existing serialization-preflight/template-journal prototypes remain local. Integrate only after actual registration-failure tests; DS-01 does not restore missing identity bytes. |
| DS-03 | Drawer withdrawal preparation, GUI remainder recovery, ItemMover/Quantum source and destination receipts | Existing local candidates; next small implementation after DS-01 green is clone-before-debit through actual StorageUnitData tests. Preserve intentional ItemContainer canonicalization rather than blindly changing metadata provenance. |
| IE-01 | Break-output conservation including zero balance/Void Excess and live identity/PDC | Existing full-context two-file local candidate; needs full IE2 Gradle + native break/place/restart. Partial-spawn failure/restart remains unresolved; do not claim models reproduce live exploits. |
| RA-01 | First-viewer foreign-slot control handler registration | Current AbstractNetworkPusher returns before registering handler. Existing local patch/lifecycle tests. Prior safety denial must not be bypassed. Needs actual GUI pickup/toggle/batch/reload verification. |
| RA-02 | Recipe-aware machine insertion and OFF parity | Planner/policy tests implemented. Extend disposable actual machine fixture for ratios, accepted slots, metadata and four-template budget. Do not relax conservation. |
| PAIR-01 | Final exact Networks + IE2 + held core; Infinity Dust Extractor source transfer | Historical actual Grid/IE2 player and two-JVM tests passed on older pins. Read current workflow pins; resolve startup readiness by normal controller refresh after IE2 cache readiness, never fixture-injected barrels. |
| PLATFORM-01 | Applicable Paper/Purpur/Folia owner-thread and performance acceptance | Networks-only Paper 176/194 and Purpur1.21.11 evidence exists, not paired Folia/live MSPT proof. Keep perf #96 separate until safety acceptance. Document external unsupported cases accurately. |
| RELEASE-01 | Coherent PR review/integration, version, immutable builds, publish, SF_Addons | Authorized only after acceptance. No release/version/tag/bundle change yet. Do not publish unrelated Legacy work. |

## Exact next action

Check the exact workflow for this DS-01 commit. Fix any real compile/test failure immediately without weakening the regressions. Record successful complete project runs and artifact hashes here. While platform jobs run, prepare the existing clone-before-debit regression against actual StorageUnitData, then implement that narrow repair after reproducing RED. Do not start a fresh general investigation or another integration PR.

## Environment / blockers

- GitHub connector reads and writes work; all repository mutations use the existing branch and non-forced updates.
- Container Java21 is available, but direct dependency/source download DNS fails. Full dependency-resolved compilation is running in the existing GitHub Actions pipeline, not being substituted with stand-ins.
- Nonstopping database workers, permanent disk failure, identity-template durability and forced-crash atomicity are outside the bounded DS-01 fix and remain distinct safety requirements.
