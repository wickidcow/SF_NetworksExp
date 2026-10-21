# Native ItemMover receipts and player hand verification

## Production contract

`ItemMoverStorage` prepares the complete item identity and amount before it mutates either endpoint. It preserves the existing `item_mover_item`, integer amount, and long amount keys. Conflicting amounts, wrong key types, negative balances, and positive balances without an exact template are refused without relabelling their contents. A zero-balance lore refresh removes the storage keys on the same metadata snapshot, so stale lore metadata cannot restore them.

The direct player handler binds a transfer to the actual main-hand or off-hand slot. It compares the original event item with the current hand, checks region ownership, reserves a transfer on the authoritative slot, writes that slot, and reads it back before calling the storage endpoint. A detached event object is insufficient. One player cannot reenter this handler while a transfer is in progress.

Snapshots use a plain stack with the live material, quantity, and complete metadata, followed by exact readback. They do not call a wrapper's clone or copy constructor, which can rebuild Slimefun model metadata. Cross-wrapper `ItemStack.equals` also fails on some native Paper paths; the storage helper compares material, quantity, and complete `ItemMeta` without that cast. The real Slimefun wrapper, a deliberately rebuilding wrapper, and modern model components are covered below.

A pending external transaction is represented by two additive PDC keys: `item_mover_pending_transfer` contains the existing item-array serializer's exact original, prepared, and offered snapshots; `item_mover_pending_direction` records the versioned direction. The complete record must be readable before an endpoint can change. Normal exact receipts settle once. Exceptions, malformed returned items, or unknown settlement writes leave the durable record blocked. Read-only `pendingReceipt` exposes the evidence; it does not infer an external outcome or refund an uncertain transfer. Existing items are never replaced with fresh registered templates.

## Automated build evidence

Production commit `18e1d9ad121f2f66e68c083a0d654b5138f4340d` builds against exact Slimefun core `1cb32d6e95b305998065b8ef6b936a56f679187d` with the Java 25 toolchain and Java 21 output. The full clean Gradle build passed **565 tests, zero failures, errors, or skips**, including **35 ItemMover regressions**. The source compatibility gate retained 289 item IDs; the Java 21 bytecode gate verified 530 Networks-owned classes; the universal JAR gate passed without bundled optional APIs or hard IE1 storage linkage. This candidate still carries development version 1.0.59; these checks do not publish a release.

The focused tests exercise real production preparation, serialization, metadata writes, and receipt logic under MockBukkit. Faults are injected only at actual setters, inventory endpoints, or external callbacks. The tests cover rejected writes, write-then-throw, stale hand slots, detached event snapshots, reentry, partial acceptance, invalid external receipts, integer/long migration, long overflow, malformed identities, rebuilding wrappers, and source debit failures. Native coverage supplements MockBukkit's known byte-array equality and model-component limitations. The final native run below uses the coordinated Networks 1.0.60 candidate, with these same ItemMover production files.

## Native fixture

`scripts/native_item_mover_receipts.py` runs a disposable loopback Purpur server and a separate restart JVM with:

| Input | Exact identity |
| --- | --- |
| Networks source | `b5e48c5c8da4ddab93cabafca44349ee4ad26a2e` (1.0.60 coordinated candidate) |
| Networks JAR SHA-256 | `0588ad1e19281293daee6dd419c51d92e5b9dc7923d707dd27157b14b9238ae3` |
| Slimefun core source | `1cb32d6e95b305998065b8ef6b936a56f679187d` |
| Slimefun core JAR SHA-256 | `251e9075bb6d1629790d845569fb90994e4ff1ed76408400ee733d4041bb9e3e` |
| Server | Purpur 1.21.11 build 2568 |
| Server SHA-256 | `ee199511bf0a970ee8459fe3a351703fec37293f00bc2de1c5cb746c7e4acc34` |
| Runtime | Temurin 21.0.12.1+1 |
| Actual client | Mineflayer 4.39.0 with retained dependency lock |

The fixture invokes the actual `ItemMover.depositItem`, lore, and transfer methods with native item metadata. It verifies exact 17-item deposit, positive unknown identity refusal, zero-balance PDC removal, rejected metadata writes, same-item reentry, a normal seven-item partial transfer, an explicitly injected partial destination exception, and the `Long.MAX_VALUE` boundary. Its rebuilding wrapper retains all native custom-model floats, strings, flags, colors, and typed owner PDC.

A connected Minecraft client then performs five real interactions against registered Quantum Storage:

1. Main-hand right click moves 37 items into the actual hand; storage retains 64.
2. Main-hand sneak/right click returns those 37; storage totals 101.
3. Off-hand right click moves 37 into the actual off-hand slot; storage retains 64.
4. Off-hand sneak/right click returns those 37; storage totals 101.
5. A real main-hand click triggers an explicit pre-handler stale-event injection. The fixture replaces the hand after the event captures it. The production handler preserves that replacement, leaves the detached original mover empty, and leaves all 101 stored items intact.

The 101-item starting storage is an explicit disposable seed. Its real ticker creates the 64-item physical output and 37-item reserve; the fixture waits for that actual state before permitting client interactions. Off-hand interactions use Mineflayer's native interaction packet shape with hand 1 because its `activateBlock` method selects hand 0. The fixture counts actual `PlayerInteractEvent` deliveries and reads real hand slots and native storage totals after every gesture.

The restart phase checks native chest persistence of the healthy 17-item balance, the long-maximum balance, the complete unresolved original/prepared/offered receipt, and the separately retained known external outcome. It confirms the unresolved transfer cannot replay and checks the original empty mover and 101-item Quantum ledger after reload.

**Native restart acceptance: BLOCKED.** The audit-enabled run of the exact 1.0.60 candidate above passed, but its result is insufficient after the controlled failure below. The seed JVM was PID 36813 and the independent verification JVM was PID 37231. Java fixture SHA-256 was `5f09e309cb83b41b24c26f03cb0e4982471fa5cc8185db4b38301ca749b9b0d1`; client SHA-256 was `a5f731246bbc330c29d7434edf2d321350cb7c671ce50fcd2c87dac5cb0e196a`. The read-only adapter audits at plugin enable, before chunk load, and after chunk load all reported the exact stored reserve 37 and inventory slots 4 and 7. They neither changed a record nor initialized a cache. The stopped seed database SHA-256 was `0214b2ab94bfed6b84cd8cdaf2c609a116cb619924ea08e0e2b4c3a113396af8`, with no WAL or SHM file present at the captured process boundary. The runner retained its stopped data before starting the verification JVM.

A control run used identical plugin/server JARs and omitted only the three diagnostic SQL read calls. All direct production cases, all five native hand interactions, and the ItemMover chest records passed, but the restart Quantum ledger failed: expected **101**, observed **0**. Its stopped seed database had the identical `0214b2ab94bfed6b84cd8cdaf2c609a116cb619924ea08e0e2b4c3a113396af8` hash and no WAL or SHM file at capture. Seed PID was 37934; the control Java fixture SHA-256 was `718fe515a7a7ef5af879530865f389dc3f883cc7ea7b3d9759d0eb535f6db5ec`. The failing run and its raw stopped data remain preserved. Diagnostic timing must not mask this startup failure. SQL audits are now disabled by default; the acceptance runner does not opt in. Developers can request them explicitly with JVM property `-Dnetworks.fixture.persistenceAudit=true` for diagnosis, but an audit-enabled pass cannot clear the default-run restart gate.

## Failures retained during development

The initial `34175f7a` native run exposed an unreadable Slimefun wrapper alias in the pending item-array record. The following `427c5eb1` run exposed Paper's cross-wrapper equality cast while verifying the standard copy. Both stopped before an unverified external mutation. The corrected implementation uses the existing exact live-metadata snapshot pattern and proves record decoding before endpoint mutation.

The first `18e1d9ad` run passed all five real client interactions and native production assertions, then failed the chest restart check. Read-only inspection of the saved Anvil chunk showed that only the later stale-event slot survived. The fixture had called `Chest.update` on an old block-state snapshot after writing its live inventory, overwriting the first four fixture slots. The corrected fixture omits that snapshot overwrite and adds immediate readback of all four actual chest slots. No production assertion or quantity requirement was relaxed.

Two subsequent runs of the isolated 1.0.59 development branch passed the complete ItemMover item-record restart checks but failed the additional Quantum block restart ledger. The retained stopped seed database and shutdown backup contained the correct 37-item reserve plus the 64-item output; the verification process later observed the initial template with no reserve/output. That startup reversion is not explained by the ItemMover changes, and the later passing candidate run is not claimed to repair it. The runner now preserves the exact stopped database files and hashes, and the fixture can optionally log read-only database state before and after chunk loading. This failure evidence remains part of the platform/integration review; no core change or database repair was made by this work.

## Reproduction and boundaries

Provide a fresh directory, the pinned raw JARs, a Java 21 runtime, and the locked client installation:

```sh
export CORE_HEAD=1cb32d6e95b305998065b8ef6b936a56f679187d
export NETWORKS_HEAD=b5e48c5c8da4ddab93cabafca44349ee4ad26a2e
python3 scripts/native_item_mover_receipts.py \
  /absolute/path/Slimefun.jar \
  /absolute/path/Networks.jar \
  /absolute/path/purpur-1.21.11-2568.jar \
  /absolute/path/new-disposable-directory \
  /absolute/path/client-modules-directory
```

The client directory must contain `package-lock.json` and the installed Mineflayer modules. `JAVA_HOME` selects the runtime. If the environment requires an HTTP proxy, configure the JVM proxy properties explicitly; an environment `HTTPS_PROXY` alone does not configure Java. The runner hashes the server, core, plugin, Java fixture, client, and lockfile into `inputs.json`, retains both console logs and client output, and requires different server process IDs for seed and restart.

The completed seed runs verify the stated native production and actual-hand paths on the listed Purpur build. The complete clean-restart gate remains blocked by the reproduced Quantum ledger failure; the audit-enabled pass does not clear it. The boundary balances and injected failures are explicit test state. It does not certify forced-crash atomicity, another server's actual tick interleaving, every optional barrel integration, Folia execution, or the final merged/version-bumped release JAR. The final release pair must run the applicable existing platform and integration gates.
