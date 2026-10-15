# Dust extractor menu-source snapshot follow-up

## Report and evidence boundary

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

This remains draft development work, now synchronized to the distinct 1.0.56 candidate identity. Do not replace the existing v1.0.55 asset. Run fresh exact-head validation of this versioned candidate and obtain explicit merge/publication approval before release. No addon bundle or live-server change is part of this follow-up. Keep the affected connection paused pending an approved replacement; do not delete extractor or storage data.
