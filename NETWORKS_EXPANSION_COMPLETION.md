# Networks Expansion completion checkpoint

Updated October 20, 2026 America/New_York (October 21 UTC in CI).

## Current execution — resume here

**DS-02a production preflight reapplied against corrected regression tests; GREEN pending.** Corrected test-only baseline `5f6f69fa9477688ca0be1fb0354b571481be00b1` compiled in exact CI `39521322733`, GuGu job `118965578859`: **513 tests, exactly seven targeted registration failures, zero skips**. The 500 accepted tests and six healthy new controls, including explicit cloned-serializer fault reachability, passed. Failure tests demonstrate late serialization, 13 physical input items consumed into orphan balances, premature ID/map/task publication, retry failure and phantom identity after queue rejection. Worker timing can leave a serializer uncalled at the immediate assertion; independent harness control verifies clone propagation.

This commit reapplies the previously prepared DataSource blob `e2f76480502ecda0d9f94a34d4a8cddc8f137044` with test blob `50521f75b1e1b6e1566bd85d1d0c0b14b9f07626` UNCHANGED. Serialize a new template before ID allocation/publication, reuse its immutable original-format payload in the worker, and publish the template map only after queue submissions are accepted. Known types keep the existing no-serialization fast path. Synchronized registration, identity matching, quantities, schemas and rates are unchanged.

**Exact next action:** inspect this production commit's exact-head universal/native workflows; fix failures without weakening the 513-test suite. Download JUnit reports and native manifests before acceptance. Then continue DS-02b durable template/quantity registration and orphan IDs using the existing test/prototype work, not another general investigation. Preserve concurrent commits and safety-denied-write boundaries.

## Authority and boundaries

The owner authorized finishing, reviewing, merging, versioning and publishing Networks Expansion and updating SF_Addons AFTER acceptance. This supersedes the old blanket release hold, not test/safety requirements. No failing merges, safety-denial bypass, force pushes, inferred item identities, silent deletion, live server/world/config changes, or unrelated Slimefun Legacy publication. Preserve recipes, IDs/PDC, schemas/migration, quantities, intentional input Void Excess, rates and budgets. GuGu/Legacy is the maintenance focus; retained United CI remains historical compatibility evidence.

Use existing PRs and preserve concurrent changes. A previous green SHA does not accept a new SHA or different core/IE2 pin. MockBukkit, source models and injected faults are not native gameplay, Folia, forced-crash or live TPS proof. No master merge, release, version bump or bundle update has occurred in this workstream.

## Repository topology

- Networks master: `084515c96bdeb3aea433890249b6d36ec8de5d6b`.
- Existing draft #104: `test/native-ie2-player-actions-20261018`, based on #102 `integration/cursor-ie2-withdrawal-player-20261018` at `96f9123f64df315a969a2b499b91781c6cc370f1`, NOT master. No formal reviews at start of this run.
- #98 ALREADY MERGED October 17: merge `f934c3e3eb1545c6e2be45f4ce27afd7caac0d5d`, source `6bf76a0ab8b41b3101f710ccd09829b25a51891a`. Do not recreate.
- #104 retains #99 cursor, #100 native Grid, #101 IE2 output receipts and #102 combined work. #103 is an alternate input pairing, not a second fix to blindly merge. Perf #96 is separate.
- IE2 master: `cc6f568b72b63283cf72bcbd85bf0817cba22194`. Draft #32: `integration/ie2-cross-direction-receipts-20261018` at `b96e3a0f09d56faeff135a1d3ac679776e1fbd24`, base #30 `ec201bc77f4bf0611c7d8674e7b75d1656e313da`. CI `39348655330` passed both validation jobs; publication skipped.
- IE2 #32 already integrates #30/#31 ownership/input/withdrawal and clone cleanup. Ticker output receipts ARE implemented. Preserve independent master fixes.
- PR descriptions have obsolete historical pins. Current refs, workflow source and exact downloaded manifests take precedence.

## Accepted fixes and evidence

### DS-01: cancelled autosaves and final shutdown checkpoint

- Test-first commits `b7e42a31` then fixture correction `dac68eb3`. Clean RED `39512799540`: 485 tests, five new failures, no old regression.
- Production **`40a06739228c39eff68a7a4302ef95668909623b`** retains detached immutable save receipts, overlays newer per-key pending values, and checkpoints after worker termination before DB close. Existing journal format unchanged.
- Files: DataStorage blob `2b3ad1f1adee2f19fe47c9cb0d4d67f1137b6d32`; Networks blob `d6f803344314531cd83f45213b85524e37eb1800`.
- Universal `39513489094` / `39513484351`: all eight gates passed, publish skipped. XML artifact `12307431485`, SHA256 `f0e8a1992dd3b9ef1968c52d1041ac728e213e23069f5aa0e688011a4c66abbe`: 485 tests, zero failures/errors/skips, 12 shutdown tests. Real production JDBC/SQLite, mocked Bukkit host.
- Paired actual native `39513484119` passed; artifact `12307880141`, SHA256 `2b12f770f99a78e578d3f46dbbdd6c279afe0119bc5c2b2f5bd71c8c5cac5168`. Four JVMs, physical controller/monitors, 192 tagged items across restart, actual Grid and IE2 player ledgers. Synthetic incident hold explicitly distinguished.

### DS-03: withdrawal preparation (GUI/other receipts still outstanding)

- Test-first `eef2618d` / checkpoint `e9f3e772`; clean RED `39514785065`: 500 tests, 11 new failures, 485 old plus four healthy new tests passed.
- Production **`877e3fcac1c2f78ea7a2f1c4cc68d57cb8f8f9fb`** prepares clone/quantity before debit, adds finally-released same-drawer preparation ownership, and validates canonical container/balance before debit.
- File StorageUnitData blob `e367dbed6a3ef68adb6616ad8e1f13ea35df1b5c`. Requesting-template identity/Slimefun canonicalization, lock-zero and long-quantity deposit changes preserved.
- Universal **`39515547044` / `39515538733`** all eight gates passed, publication skipped. XML artifact `12306818461`, SHA256 `6e7d710f63c928c71fc04d061b0f7919edefd79f72970b0ee46ed63fedeacd89`: **500/500, zero failures/errors/skips**, 15 preparation and 12 shutdown tests.
- Exact native **`39515538641`** passed; downloaded artifact `12306769572`, ZIP SHA256 `72acc2800f6f3a6a5c7b6f5745abbc72308444355a7ba74ac5068e97df0a8708`. Re-read the archive manifest: Networks `877e3fca`, IE2 `b96e3a0f`, core `1cb32d6e95b305998065b8ef6b936a56f679187d`, Purpur 1.21.11 build 2568. PIDs **4630/4740/4864/5025**. Networks JAR SHA256 **`520be90624b0769a7dfc1640c1f27dfa9500baa8d742bcdd3902ecf5ba7c4eb3`**. These replace transcription errors in an earlier checkpoint's PID/JAR fields; source pins and PASS were correct. Original 192-item restart and actual Grid/IE2 320/316 ledgers preserved.

## DS-02a development history and scope

- `25d41f61` added 12 actual DataSource/SQLite/Bukkit serialization tests; `9ad88f64` checkpointed DS-03 acceptance.
- `32c06ab9` switched comparison configuration; `39519612547` had seven failures but two skipped controls because MockBukkit lacks `matchesWithoutData`. Not accepted as clean RED.
- `dbf1025f` isolates only the unrelated two-stack matcher by materializing real metadata; no registration, serializer or JDBC stubs. It ran 512 tests with seven failures/no skips in `39520191771`, but later evidence showed the injected serializer was not reached across the production clone. Its initial clean serializer-fault claim is RETRACTED; queue rejection remained valid.
- `95840eaf` implemented the prepared preflight. CI `39520806970` compiled but six fault tests still failed: Paper clone delegated to its underlying stack, discarding the test subclass.
- `5f6f69fa` forward-restored only the unaccepted DataSource change and repaired clone propagation, strengthened callback-reached assertions, and added a harness control. CI `39521322733` now establishes the corrected 513/7/0-skip baseline above. All accepted DS-01/DS-03 changes remain intact.
- This commit reapplies exactly the prepared production blob with those 13 corrected tests unchanged. It remains unaccepted until exact-head GREEN.
- Preflight alone does not recover cancelled/failed SQL identity writes. Known types must never incur repeat serialization; first-unique-type work moves to the registering thread and needs practical performance validation.

## Remaining acceptance queue

| ID | Requirement | State / next acceptance |
| --- | --- | --- |
| DS-01 | Native Drawer timeout/restart | Project and paired regressions pass; dedicated native Drawer fault test and forced-crash evidence remain separate. |
| DS-02a | Serializer preflight | Corrected baseline RED established; production reapplied, require exact-head GREEN. |
| DS-02b | Durable templates and orphan IDs | Existing prototype/journal candidates not integrated. New-template SQL cancellation/failure and missing identities remain. |
| DS-03 | GUI remainder, ItemMover and Quantum receipts | Preparation accepted; remaining known receipt/restore candidates need actual project/native tests. |
| IE-01 | Break/output/Void/zero/live PDC | Existing full-context candidate; full Gradle/native break/place/restart and partial-spawn recovery unresolved. |
| RA-01 | Foreign-slot initial handler | Existing narrow candidate but prior safety-denied write must not be bypassed. Native pickup/toggle/batch/reload still required. |
| RA-02 | Recipe insertion/OFF parity | Classic/More/Best already implemented; validate native ratios/accepted inputs/budgets. Other pusher families distinct. |
| PAIR-01 | Final pair and Infinity Dust Extractor | 877e3fca actual native passes; re-run final changed pair, never fabricate barrels or relax ledgers. |
| PLATFORM-01 | Paper/Purpur/Folia/performance | Native Paper 176/194 and Purpur passed at accepted SHA; paired Folia and live MSPT not proven. Perf #96 separate. |
| RELEASE-01 | Review/integrate/version/release/SF_Addons | Owner-authorized only after acceptance; unrelated Legacy publication excluded. |

## Environment

GitHub reads/writes/Actions/artifacts work. Local Java21 works; direct clone/download DNS fails, so full dependency-resolved builds execute in Actions. Historical isolated model packages are supplementary. Permanent disk failure, nonstopping workers, forced-crash atomicity and arbitrary unsafe callbacks are not solved by individual passing tests.
