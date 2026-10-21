# Dust extractor menu-source snapshot follow-up

## Final coordinated 1.0.60 / 2.0.15 candidate

The October21 candidate pair, Networks `b5e48c5c8da4ddab93cabafca44349ee4ad26a2e` and IE2 `19e4943a53c44589a6cfcf618150358f7bdf5a1c`, passed **152 native Dust Extractor production/Networks line-transfer cases** on Purpur1.21.11/2568 with core `1cb32d6e95b305998065b8ef6b936a56f679187d`. The exact JAR hashes and final release acceptance are tracked in `NETWORKS_EXPANSION_COMPLETION.md`.

This reuses the established real extractor fixture. It retains the synchronized-ticker, exact modern model/PDC identity, replacement, empty-source, partial acceptance and compensation checks. Networks now intentionally permits a producer to add stock of the same exact identity during a transfer. The previous four amount-change abort cases are therefore replaced by16explicit ordinary/VOID growth ledgers, including decorated items. Each checks accepted+retained+intentionalVOID quantity against original+newly produced stock; VOID cannot consume the new producer stock.

The sink remains controlled and the probe runs in one JVM. This proves the stated actual producer/menu/line paths; it does not certify a live destination topology, arbitrary tick interleavings or restart durability. `scripts/prepare_infinity_dust_probe.py` regenerates the runner from the existing disposable-server harness; immutable source pins must be supplied through `NETWORKS_HEAD`, `IE2_HEAD` and `CORE_HEAD`.

The following published-pair evidence remains historical; it is not relabelled as a test of the new candidate.

## Confirmed producer-side trigger and published fix

A later live trace on Networks 1.0.56 reported the same guarded conflict at `world -5274,-26,-5510`: the network accepted 8 items, detected that the menu source changed, and removed all 8 accepted items as compensation. That is the intended 1.0.56 safety behavior: the conflict is rejected without authorizing the old destructive VOID cleanup.

The producer-side scheduling condition is now confirmed. InfinityExpansion2 2.0.13 shades GuizhanLib 2.5.0, whose `TickingMenuBlock.isSync()` default is `false`. IE2's Dust Extractor inherits `RandomHopperMachine -> HopperMachine -> AbstractTickingMachine -> TickingMenuBlock` and, before IE2 2.0.14, did not override that default. Its input/output transaction could therefore run asynchronously while Networks performed the synchronized source snapshot/deposit/commit sequence.

InfinityExpansion2 2.0.14 narrows the fix to `HopperMachine.isSync() = true`. This keeps Dust Extractor and the same short converter family synchronized without moving heavier Quarry/Growing-style machines onto the main ticker. Networks 1.0.57 retains the 1.0.56 exact-source snapshot and compensation guard as a safety net for other producers.

Published-pair validation run `38895397648` used the exact public JARs:

- Networks 1.0.57 SHA-256 `125a54605244f3ecaa2d873cca4dff7a78ff7772a445ad5d6f4c38f3603d57ab`.
- InfinityExpansion2 2.0.14 SHA-256 `4c3d5a19b751d13f769937d6e5faf18f67ddb40465db3d9759a1d8800c0cbdb9`.
- Slimefun Legacy 4.1.74 SHA-256 `72a524d7c0d6f75a9decbf06679515887273ae53673d3f5ee38f355bef952c96`.

On both Purpur 1.21.11 and Paper 26.3, the real registered Dust Extractor ticker reported synchronized and 140 real IE2 production/menu/Networks line-transfer cases passed. The negative control kept Networks 1.0.57 fixed and swapped only IE2 back to 2.0.13; both runtimes failed specifically with `DUST_EXTRACTOR_TICKER_ASYNC: IE_DUST_EXTRACTOR`.

This validation does not reproduce a live server's exact tick interleaving or storage topology. It does verify the concrete scheduler difference that allowed the race and the exact published pair that removes that condition.

## Earlier report and snapshot-defense boundary


The reported source is an Infinity Dust Extractor. The supplied trace identifies Networks 1.0.55, Slimefun Legacy 4.1.74 and Purpur 1.21.11 build 2568, at world `world`, block `-1314,66,817`. It reports four items accepted and four removed as compensation. It does not identify the installed Infinity Expansion/IE2 version or the source stack's class and metadata.

PR #71 already fixes unconditional VOID source clearing after a rejected or failed deposit. This follow-up addresses a separate, plausible way the unchanged source can fail that check; it does not establish the actual server's trigger.

Inspected primary source: `wickidcow/Slimefun-Legacy`, tag `v4.1.74`, `api/items/SlimefunItemStack.java`. Its `clone()` reconstructs `SlimefunItemStack`, whose constructor writes item identity and invokes `CustomTextureService.setTexture`. That service replaces the first model-data float when the configured mapping is nonzero. Existing metadata differing from the mapping can therefore produce a non-identical clone without any concurrent machine tick. Paper's `ver/1.21.11` `ItemStack(ItemStack)` constructor also delegates to the input's `clone()`, so a copy constructor alone is not a fix.

## Change

- Validate a detached source snapshot before offering any items to the network.
- Keep the normal identity-preserving clone path. If the clone changes identity/count or aliases the original object, copy the existing metadata into a plain stack and accept it only if the strict source comparison succeeds.
- Never silently discard an unrepresentable component. A non-identical fallback or snapshot exception stops before network acceptance, with an explicit pre-deposit warning.
- Derive the menu offer and remainder from the verified snapshot rather than repeatedly cloning the live addon object.
- Retain the strict post-offer type, amount and metadata check, compensation, guarded source restoration and the existing VOID failure result. No ID-only comparison, ignored model data, disabled safeguards, recipe change or persistent-data migration.

## Regression coverage and test-model limitation

`NetworkMenuSourceSnapshotTest` adds 14 cases: a rebuilding-clone fixture, ordinary and VOID zero/partial/full acceptance, aliasing clone, real metadata replacement, partial destination failure, source dirty failure, and runtime/linkage snapshot failures. Its supported scalar model-data field, lore and STRING/LONG/BYTE_ARRAY persistent data are asserted explicitly at the offer and remainder boundaries. It exercises production menu entry points with mocked storage boundaries.

The first follow-up run, `38811911198` at head `28e163cd94557fbf3bcba61dbea62fc1377163bf`, compiled successfully but failed the model-only clone reproducer (250 tests completed, one failed in the primary job). Inspection of MockBukkit 4.110.0 `ItemMetaMock` showed that its copy constructor omits the modern CustomModelDataComponent, while equality considers the separate legacy scalar field. The initial fixture therefore did not faithfully represent native model data. The failing assertion is retained; the fixture now uses the supported scalar field. No production guard was weakened to accommodate the test framework.

`CrafterManagerDisplayProbe` separately invokes the actual snapshot and strict source-comparison methods on native server ItemStacks. It requires a model-only rebuilding clone to differ, verifies exact floats/strings/flags/colors and typed PDC preservation, checks detached copies, and rejects real amount, owner and model changes. It covers native stacks, rebuilding/aliasing fixtures and a real SlimefunItemStack. This runs inside the existing disposable Purpur 1.21.11 and Paper 26.3 probes, without adding a live-server mutation or a production dependency.

These fixtures do not load the reported extractor or certify every component/integration. Passing results must be verified against the resulting versioned PR head in CI; neither the old c252233 checks nor the failed 28e163c run certifies this revision.

## Release and operational boundary

Networks 1.0.57 and InfinityExpansion2 2.0.14 are published. For this specific Dust Extractor conflict, upgrade both JARs together during a clean restart; installing Networks 1.0.57 while leaving IE2 2.0.13 retains the asynchronous producer condition. Keep only one JAR per plugin and preserve world/plugin data. The source-change safeguard must remain enabled even after the producer fix.
