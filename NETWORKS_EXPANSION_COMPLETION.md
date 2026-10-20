# Networks Expansion completion checkpoint

Updated: 2026-10-20. Consult this file and fresh GitHub refs before every execution.

## Execution authority and scope

The owner explicitly authorized finishing, testing, reviewing, merging, versioning, publishing Networks Expansion, and updating the SF_Addons bundle after the acceptance requirements pass. This supersedes the earlier blanket release hold, but is NOT permission to merge failing code, skip tests, guess item identities, deploy to a live server, force-push, bypass a safety-denied write, or publish unrelated Slimefun Legacy changes. GuGu/Legacy is the maintenance focus. Existing United CI results are historical compatibility evidence, not a new development target.

Use the existing integration branch/PR; do not create another integration PR. Preserve concurrent commits. An earlier successful run does not validate a newer head, another core/IE2 pin, or an uncommitted patch. Models and injected faults are not evidence of a live exploit, Folia correctness, forced-crash durability, or live TPS.

## Verified starting state

| Repository / work | Ref | Evidence / status |
| --- | --- | --- |
| Networks master | `084515c96bdeb3aea433890249b6d36ec8de5d6b` | Read from GitHub at start of this execution. |
| Networks existing integration PR #104 | `test/native-ie2-player-actions-20261018` at `5cddfba0dd900bb045a5103e8f417158df25b5d1` | Draft, open, unmerged. Base is #102 `96f9123f64df315a969a2b499b91781c6cc370f1`, NOT master. No review submissions. |
| Networks exact-head universal CI | run `39425065000` | All eight validation jobs passed; publication skipped. Includes Legacy/Gugu/retained United, Java 21 JAR, Paper 26.3 API, native Paper 176/194 Quantum/storage checks. This does not validate the local repairs below. |
| Networks #98 crafting repair | merge `f934c3e3eb1545c6e2be45f4ce27afd7caac0d5d` | Already merged on 2026-10-17. Do not recreate it. Head was `6bf76a0ab8b41b3101f710ccd09829b25a51891a`. |
| IE2 master | `cc6f568b72b63283cf72bcbd85bf0817cba22194` | Read from GitHub; retain the independent master changes when preparing integration. |
| IE2 existing integration PR #32 | `integration/ie2-cross-direction-receipts-20261018` at `b96e3a0f09d56faeff135a1d3ac679776e1fbd24` | Draft, open, unmerged; based on #30 `ec201bc77f4bf0611c7d8674e7b75d1656e313da`. No review submissions. |
| IE2 exact-head CI | run `39348655330` | Successful compatibility and migration/storage validation; publication skipped. |

PR descriptions include historical head hashes. The current Git ref and exact-head run take precedence. The old IE2 `cb0df3ef` template-clone failure was fixed in the existing branch; do not reapply or bypass the previously denied patch for that issue.

## Implemented work to retain (not a completion claim)

- Merged #98 verifies crafting ingredient debits and output receipts; preserves metadata, blocks reentry, and avoids speculative refunds.
- Existing #104 includes #99 cursor conservation, #100 native Grid fixture, #101 IE2 output receipts, #102 integration, and real IE2 player fixture work. #103 is an alternative input-only pairing, not an extra production repair to blindly merge.
- Existing IE2 #32 combines #30 withdrawal reservations and #31 physical input receipts, cross-direction guards, seeded-template integrity, and template-clone ownership cleanup. It also contains verified ticker output receipt work.
- Classic Network Pusher, More, and Best already have per-block Recipe-Aware toggles. OFF remains normal behavior. Template quantities/duplicates define one recipe batch; batch target is 1-64; routing uses accepted physical inputs and a four-template work budget. Expansion line/point/Vanilla/Smart pushers are separate implementations and are not implicitly covered.
- #104 already reduces redundant Recipe-Aware icon writes and protects foreign items. Concurrent commits through `5cddfba0` add physical-quantity/lore agreement checks. Preserve them.
- Drawer absolute updates/tombstones and FIFO worker-before-journal queue ordering are already present in #104. Do not replace them with the old model-only approach.

## Acceptance queue: concrete unfinished work

| ID | Task | Current evidence | State |
| --- | --- | --- | --- |
| DS-01 | Retain detached Drawer autosave snapshots through queue cancellation; checkpoint after worker termination and before closing SQLite. | Existing full-class local candidate and SQLite/separate-JVM tests in `Networks_Drawer_FullClass_SQLite_Restart_TestFirst_2026-10-20.zip`. Prior report: baseline failures, candidate 101/101 persistence, 120/120 restart, 21/21 shutdown-method assertions. Those used test boundary adapters, not a full plugin. | FIRST implementation task: add real repository regressions, apply the existing two-file fix, compile/test in project CI. |
| DS-02 | Keep new item-template registration and amount recovery consistent through failure/restart; prevent orphan ItemID reuse. | Local serialization-preflight and template-identity journal prototypes only. Caller-thread serialization cost and durable-template integration unresolved. | Not integrated; do not claim solved by DS-01. |
| DS-03 | Drawer withdrawal and quick-transfer receipt/recovery correctness. | Local clone-before-debit, GUI remainder, ItemMover overflow/PDC, and Quantum debit proposals. Some earlier provenance ideas conflict with intentional ItemContainer canonicalization. | Review existing candidates against actual callers, port targeted tests, fix only confirmed production failures. |
| IE-01 | IE2 physical-output conservation on break, including Void Excess and zero stored balance; verify live identity and serialized PDC before mutation. | Existing two-file local candidate, exact-method tests and CI-bytecode baseline evidence. Post-spawn failure/cancellation/restart remains unresolved. | Full-project compile and native break/place/restart required; do not install the isolated candidate untested. |
| RA-01 | Register the Recipe-Aware control handler even if a foreign item occupies the historical slot on the first viewer tick. | Existing local patch and 15-check lifecycle harness. Current `AbstractNetworkPusher` still returns before handler registration. A prior write in this GUI area was safety-denied. | Do not bypass that denial. Needs native pickup/toggle/batch/reload/PDC tests and an allowed reviewed integration path. |
| RA-02 | Actual machine Recipe-Aware insertion acceptance (ratios, valid slots, OFF behavior, four-template budget). | Planner/policy tests and implemented controls exist; native direct controls/foreign recovery not yet accepted. | Extend disposable fixture, do not weaken conservation or hide missing barrels. |
| PAIR-01 | Final combined Networks/IE2/core pair, including actual Grid/IE2 player actions and Infinity Dust Extractor transfer source. | Historical four-JVM pairing passed. A prior intermittent Grid readiness failure retained all 16 items in player inventory with zero discovered barrels. | Verify current workflow pins and startup readiness causally; rerun final exact pair. |
| PLATFORM-01 | Paper/Purpur/Folia 26.3 owner-thread and performance acceptance. | Networks-only Paper 176/194 and Purpur 1.21.11 checks exist. They are not paired IE2 Folia or live MSPT proof. Performance #96 is separate. | Preserve rates/budgets; test applicable platforms, document unsupported/external blockers explicitly. |
| RELEASE-01 | Review coherent PR stack, integrate current masters without dropping independent fixes, bump versions, validate exact release artifacts, publish, update SF_Addons. | Owner now authorizes this sequence after acceptance. | Not started; no release while unresolved safety gates remain. Do not publish unrelated Legacy changes. |

## Changes and test log for this execution

- Added this durable checkpoint on the existing #104 branch. No production change in this commit.
- Fresh reads completed for both masters, #104/#32 metadata, discussions and reviews, #98 merged state, and exact-head universal/IE2 workflow status.
- Local execution environment has Java 21. Direct dependency/source downloads currently fail DNS resolution. GitHub connector reads work; project GitHub Actions will be used for full dependency-resolved validation where allowed.
- Existing local packages were found in the conversation runtime; resume those candidates rather than starting new investigations. Their historical test reports must not be presented as tests of a new commit.

## Exact next action

DS-01: read the existing full-class candidate patch, confirm its original file blob hashes against current #104, add focused JUnit regression tests for cancelled detached snapshots/newer-pending precedence, reproduce failure against unchanged production, then commit the coordinated DataStorage/Networks fix on this branch. Check the resulting exact-head CI and repair any compile/test failure. Update this checkpoint with the resulting commit hashes and actual run results before proceeding to DS-02 or the next acceptance item.
